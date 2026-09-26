/**
 * Page Notes Service — image→structured markdown + quiz map-reduce + validators + provenance.
 *
 * Components:
 * 1. handlePageNotes: vision call to NIM 11b-vision, returns structured markdown
 * 2. handleQuizFromPageNotes: map-reduce+merge quiz from page notes
 * 3. Validators: SHA-256 hash dedup, redundancy check, Levenshtein dedup
 * 4. Provenance: per-item {page, chunkIndex} where available
 */

const crypto = require('crypto');
const { PAGE_NOTES_SYSTEM_PROMPT, PAGE_NOTES_USER_MESSAGE } = require('../prompts/page-notes-system-prompt');

// ── Constants ────────────────────────────────────────────────────────────────

const PAGE_NOTES_MAX_IMAGE_BYTES = 10 * 1024 * 1024; // 10MB
const QUIZ_CHUNK_MAX_CHARS = 3000;
const QUIZ_MIN_COVERAGE_CHUNKS = 3;
const QUIZ_QUESTIONS_PER_CHUNK = 3;
const QUIZ_MAX_QUESTIONS = 30;
const QUIZ_CONCURRENCY = 3;
const LEVENSHTEIN_DEDUP_THRESHOLD = 3;

// ── Page Notes Handler ───────────────────────────────────────────────────────

/**
 * POST /api/ai/page-notes handler.
 * @param {object} opts
 * @param {object} opts.body - { imageBase64: string }
 * @param {object} opts.provider - AI provider from gateway
 * @param {number} opts.maxTokens
 * @param {AbortSignal} opts.signal
 * @returns {Promise<{markdown: string}>}
 */
async function handlePageNotes({ body, provider: ai, maxTokens, signal }) {
  const { imageBase64 } = body;
  if (!imageBase64 || typeof imageBase64 !== 'string') {
    const err = new Error('imageBase64 is required and must be a string');
    err.statusCode = 400;
    err.code = 'MISSING_IMAGE';
    throw err;
  }

  // Validate image size (base64 length * 3/4 ≈ raw bytes)
  const approxBytes = Math.ceil((imageBase64.length * 3) / 4);
  if (approxBytes > PAGE_NOTES_MAX_IMAGE_BYTES) {
    const err = new Error(`Image exceeds maximum size of ${PAGE_NOTES_MAX_IMAGE_BYTES / 1024 / 1024}MB`);
    err.statusCode = 400;
    err.code = 'IMAGE_TOO_LARGE';
    throw err;
  }

  // Use resolveScanProvider NIM lane — model meta/llama-3.2-11b-vision-instruct
  const scan = await resolveScanProviderForPageNotes();
  const visionAi = scan ? scan.provider : ai;
  const model = scan ? scan.model : (ai.resolveVisionModel ? ai.resolveVisionModel(body.model) : body.model);

  // Detect image MIME
  const mimeType = detectImageMimeFromBase64(imageBase64);

  const messages = [
    { role: 'system', content: PAGE_NOTES_SYSTEM_PROMPT },
    {
      role: 'user',
      content: [
        { type: 'text', text: PAGE_NOTES_USER_MESSAGE },
        {
          type: 'image_url',
          image_url: {
            url: `data:${mimeType};base64,${imageBase64}`,
            detail: 'auto',
          },
        },
      ],
    },
  ];

  const markdown = await visionAi.chatCompletionText(messages, {
    temperature: 0.1,
    maxTokens: Math.min(maxTokens, 4096),
    model,
    // When the dedicated scan provider (NIM) is active, pin the request to
    // exactly that model. Without this, chatCompletion's vision fallback
    // chain prepends Gemini/Orca lanes (GEMINI_API_KEY falls back to the
    // embedding key, which 401s) and each page burns dead lanes first —
    // ~25-90s instead of the direct NIM ~5s. Mirrors handleScheduleAnalysis's
    // wireModelOverride usage.
    ...(scan ? { wireModelOverride: model } : {}),
    isVision: true,
    signal,
  });

  if (!markdown || !markdown.trim()) {
    const err = new Error('AI returned empty page notes');
    err.statusCode = 502;
    err.code = 'EMPTY_VISION_RESULT';
    throw err;
  }

  return { markdown: markdown.trim() };
}

// ── Scan Provider Resolver ───────────────────────────────────────────────────

/**
 * Resolve the NIM scan provider for page notes.
 * Uses SCAN_AI_API_KEY or falls back to the scanProviderConfig remote.
 * Default NIM model: meta/llama-3.2-11b-vision-instruct
 */
const PAGE_NOTES_NIM_MODEL = 'meta/llama-3.2-11b-vision-instruct';
const PAGE_NOTES_NIM_BASE_URL = 'https://integrate.api.nvidia.com/v1';

async function resolveScanProviderForPageNotes() {
  // Check env for NIM config
  if (process.env.SCAN_AI_API_KEY || process.env.SCAN_AI_BASE_URL) {
    const { createAiProvider } = require('./AiProvider');
    return {
      provider: createAiProvider({
        baseUrl: process.env.SCAN_AI_BASE_URL || PAGE_NOTES_NIM_BASE_URL,
        apiKey: process.env.SCAN_AI_API_KEY || process.env.AI_API_KEY,
      }),
      model: process.env.SCAN_VISION_MODEL || PAGE_NOTES_NIM_MODEL,
    };
  }
  // Try remote scan provider config
  try {
    const scanProviderConfig = require('../config/ScanProviderConfig');
    const remote = await scanProviderConfig.getRemoteScanProvider();
    if (remote && remote.apiKey) {
      const { createAiProvider } = require('./AiProvider');
      return {
        provider: createAiProvider({
          baseUrl: remote.baseUrl || PAGE_NOTES_NIM_BASE_URL,
          apiKey: remote.apiKey,
        }),
        model: remote.model || PAGE_NOTES_NIM_MODEL,
      };
    }
  } catch (_) { /* ScanProviderConfig not available */ }
  return null;
}

// ── Quiz Map-Reduce + Merge ──────────────────────────────────────────────────

/**
 * Split page notes markdown into chunks at heading boundaries, ≤QUIZ_CHUNK_MAX_CHARS each.
 * @param {string} markdown
 * @returns {Array<{text: string, heading: string}>}
 */
function splitPageNotesChunks(markdown) {
  if (!markdown || typeof markdown !== 'string') return [];

  // Split on heading lines (## or ### level preferred, then # as fallback)
  const lines = markdown.split('\n');
  const sections = [];
  let currentHeading = '';
  let currentLines = [];

  for (const line of lines) {
    const headingMatch = line.match(/^(#{1,3})\s+(.+)/);
    if (headingMatch) {
      // Flush previous section
      if (currentLines.length > 0 || currentHeading) {
        sections.push({
          heading: currentHeading,
          text: currentLines.join('\n').trim(),
        });
      }
      currentHeading = headingMatch[2].trim();
      currentLines = [line];
    } else {
      currentLines.push(line);
    }
  }
  // Flush last section
  if (currentLines.length > 0) {
    sections.push({
      heading: currentHeading,
      text: currentLines.join('\n').trim(),
    });
  }

  // Merge small sections to stay ≤ QUIZ_CHUNK_MAX_CHARS
  const chunks = [];
  let currentChunk = '';
  let currentChunkHeading = '';

  for (const section of sections) {
    if (!section.text) continue;

    if (
      currentChunk.length + section.text.length + 2 > QUIZ_CHUNK_MAX_CHARS &&
      currentChunk.length > 0
    ) {
      chunks.push({
        heading: currentChunkHeading,
        text: currentChunk.trim(),
      });
      currentChunk = '';
      currentChunkHeading = '';
    }

    if (section.text.length > QUIZ_CHUNK_MAX_CHARS) {
      // Hard split oversized sections
      if (currentChunk) {
        chunks.push({ heading: currentChunkHeading, text: currentChunk.trim() });
        currentChunk = '';
        currentChunkHeading = '';
      }
      for (let i = 0; i < section.text.length; i += QUIZ_CHUNK_MAX_CHARS) {
        chunks.push({
          heading: section.heading,
          text: section.text.slice(i, i + QUIZ_CHUNK_MAX_CHARS),
        });
      }
    } else {
      if (!currentChunk) currentChunkHeading = section.heading;
      currentChunk = currentChunk ? `${currentChunk}\n\n${section.text}` : section.text;
    }
  }

  if (currentChunk.trim()) {
    chunks.push({ heading: currentChunkHeading, text: currentChunk.trim() });
  }

  return chunks;
}

/**
 * Generate quiz questions from a single chunk.
 * @param {object} ai - AI provider
 * @param {string} chunkText - text content of the chunk
 * @param {number} chunkIndex - index of the chunk (for provenance)
 * @param {number} pageNumber - page number (for provenance)
 * @param {number} maxTokens
 * @param {AbortSignal} signal
 * @returns {Promise<Array>} parsed questions array
 */
async function runQuizChunk(ai, chunkText, chunkIndex, pageNumber, maxTokens, signal) {
  const content = await ai.chatCompletionText(
    [
      { role: 'system', content: 'Generate quiz questions from study material. Respond with JSON only.' },
      {
        role: 'user',
        content: `Create ${QUIZ_QUESTIONS_PER_CHUNK} quiz questions from this section. JSON shape:
{"questions":[{"type":"MULTIPLE_CHOICE|TRUE_FALSE","question":"...","options":["..."],"correctAnswer":"..."}]}
Use MULTIPLE_CHOICE with 3-4 options, or TRUE_FALSE with options ["True","False"].
IMPORTANT: Each option and the correctAnswer must exactly match one of the provided options.
Section notes:\n${chunkText}`,
      },
    ],
    { temperature: 0.5, maxTokens, model: ai.resolveTextModel ? ai.resolveTextModel() : undefined, signal }
  );

  const parsed = ai.extractJson(content);
  const questions = Array.isArray(parsed.questions) ? parsed.questions : [];

  // Add provenance to each question
  return questions.map((q) => ({
    ...q,
    page: pageNumber,
    chunkIndex,
  }));
}

/**
 * Deduplicate quiz questions using SHA-256 hash + Levenshtein distance.
 * @param {Array} questions
 * @returns {Array} deduplicated questions
 */
function dedupeQuizQuestions(questions) {
  const seenHashes = new Set();
  const unique = [];

  for (const q of questions) {
    if (!q || !q.question) continue;

    // SHA-256 hash of question text for exact dedup
    const hash = crypto.createHash('sha256').update(q.question.trim().toLowerCase()).digest('hex');
    if (seenHashes.has(hash)) continue;

    // Levenshtein near-duplicate check against existing questions
    let isNearDupe = false;
    for (const existing of unique) {
      if (levenshteinDistance(q.question.trim().toLowerCase(), existing.question.trim().toLowerCase()) <= LEVENSHTEIN_DEDUP_THRESHOLD) {
        isNearDupe = true;
        break;
      }
    }
    if (isNearDupe) continue;

    seenHashes.add(hash);
    unique.push(q);
  }

  return unique;
}

/**
 * Check redundancy: a question is redundant if its correctAnswer appears
 * verbatim in a large portion of the question text (question-answer leakage).
 * @param {object} q
 * @returns {boolean} true if redundant
 */
function isRedundantQuestion(q) {
  if (!q || !q.question || !q.correctAnswer) return false;
  const questionLower = q.question.toLowerCase();
  const answerLower = q.correctAnswer.toLowerCase();
  // If answer is short (≤5 chars) it's likely to overlap by coincidence
  if (answerLower.length <= 5) return false;
  return questionLower.includes(answerLower);
}

/**
 * Coverage check: verify quiz touches at least QUIZ_MIN_COVERAGE_CHUNKS distinct chunks.
 * @param {Array} questions - questions with provenance
 * @returns {boolean}
 */
function hasMinimumCoverage(questions) {
  const chunkSet = new Set(questions.map((q) => `${q.page}-${q.chunkIndex}`));
  return chunkSet.size >= QUIZ_MIN_COVERAGE_CHUNKS;
}

/**
 * POST /api/ai/quiz-from-page-notes handler.
 * Processes page-notes markdown with map-reduce+merge + dedupe + coverage.
 * @param {object} opts
 * @param {object} opts.body - { text: string (page notes markdown), title?: string }
 * @param {object} opts.provider - AI provider from gateway
 * @param {number} opts.maxTokens
 * @param {AbortSignal} opts.signal
 * @returns {Promise<{title: string, questions: Array}>}
 */
async function handleQuizFromPageNotes({ body, provider: ai, maxTokens, signal }) {
  const text = body.text || '';
  const title = body.title || 'Generated Quiz';

  // Split page notes into chunks at heading boundaries
  const chunks = splitPageNotesChunks(text);

  if (chunks.length === 0) {
    const err = new Error('No content found in page notes');
    err.statusCode = 400;
    err.code = 'EMPTY_CONTENT';
    throw err;
  }

  // Map: generate questions from each chunk in parallel batches
  const allQuestions = [];
  for (let i = 0; i < chunks.length; i += QUIZ_CONCURRENCY) {
    const batch = chunks.slice(i, i + QUIZ_CONCURRENCY);
    const results = await Promise.all(
      batch.map((chunk, offset) =>
        runQuizChunk(ai, chunk.text, i + offset, body.page || 1, maxTokens, signal)
      )
    );
    for (const questions of results) allQuestions.push(...questions);
  }

  // Reduce: filter out malformed / redundant / leakage questions
  const validQuestions = allQuestions.filter((q) => {
    if (!q || !q.question || !Array.isArray(q.options) || q.options.length < 2) return false;
    if (!q.correctAnswer || !q.options.includes(q.correctAnswer)) return false;
    if (isRedundantQuestion(q)) return false;
    const type = String(q.type || '').toUpperCase();
    if (type.includes('TRUE') || type.includes('FALSE')) return q.options.length >= 2;
    const validOptions = q.options.filter((o) => typeof o === 'string' && o.trim());
    return validOptions.length >= 2;
  });

  // Dedupe: SHA-256 + Levenshtein
  const deduped = dedupeQuizQuestions(validQuestions);

  // Merge: enforce coverage requirement
  // If we don't have enough coverage, try to pull in more questions from underrepresented chunks
  let result = deduped;
  if (!hasMinimumCoverage(result) && chunks.length >= QUIZ_MIN_COVERAGE_CHUNKS) {
    // Find chunks not yet represented
    const representedChunks = new Set(result.map((q) => `${q.page}-${q.chunkIndex}`));
    const missingChunks = chunks
      .map((c, i) => ({ ...c, index: i }))
      .filter((c) => !representedChunks.has(`${body.page || 1}-${c.index}`));

    // Generate additional questions from missing chunks
    for (const chunk of missingChunks.slice(0, 3)) {
      try {
        const extra = await runQuizChunk(ai, chunk.text, chunk.index, body.page || 1, maxTokens, signal);
        result.push(...extra.filter((q) => {
          if (!q || !q.question || !Array.isArray(q.options) || q.options.length < 2) return false;
          if (!q.correctAnswer || !q.options.includes(q.correctAnswer)) return false;
          return true;
        }));
      } catch (_) { /* continue on individual chunk failure */ }
    }
    result = dedupeQuizQuestions(result);
  }

  // Cap total questions
  return {
    title,
    questions: result.slice(0, QUIZ_MAX_QUESTIONS),
  };
}

// ── Validators ───────────────────────────────────────────────────────────────

/**
 * SHA-256 hash of content for exact dedup.
 * @param {string} content
 * @returns {string} hex hash
 */
function sha256Hash(content) {
  return crypto.createHash('sha256').update(content).digest('hex');
}

/**
 * Validate a quiz item — checks required fields and format.
 * @param {object} q
 * @returns {boolean}
 */
function isValidQuizItem(q) {
  if (!q || typeof q !== 'object') return false;
  if (!q.question || typeof q.question !== 'string' || !q.question.trim()) return false;
  if (!Array.isArray(q.options) || q.options.length < 2) return false;
  if (!q.correctAnswer || typeof q.correctAnswer !== 'string') return false;
  if (!q.options.includes(q.correctAnswer)) return false;
  return true;
}

/**
 * Batch dedup + redundancy check for an array of items (cards or quizzes).
 * @param {Array} items
 * @param {function} getText - extract text from item for hashing
 * @returns {{unique: Array, duplicatesRemoved: number}}
 */
function batchDedupe(items, getText) {
  const seenHashes = new Set();
  const unique = [];
  let duplicatesRemoved = 0;

  for (const item of items) {
    const text = getText(item);
    if (!text) continue;

    const hash = sha256Hash(text.trim().toLowerCase());
    if (seenHashes.has(hash)) {
      duplicatesRemoved++;
      continue;
    }
    seenHashes.add(hash);
    unique.push(item);
  }

  return { unique, duplicatesRemoved };
}

// ── Levenshtein Distance ─────────────────────────────────────────────────────

/**
 * Compute Levenshtein distance between two strings.
 * @param {string} a
 * @param {string} b
 * @returns {number}
 */
function levenshteinDistance(a, b) {
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;

  const matrix = Array.from({ length: a.length + 1 }, (_, i) =>
    Array.from({ length: b.length + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0))
  );

  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      matrix[i][j] = Math.min(
        matrix[i - 1][j] + 1,
        matrix[i][j - 1] + 1,
        matrix[i - 1][j - 1] + cost
      );
    }
  }

  return matrix[a.length][b.length];
}

// ── Image MIME Detection ─────────────────────────────────────────────────────

function detectImageMimeFromBase64(imageBase64) {
  if (!imageBase64 || typeof imageBase64 !== 'string') return 'image/jpeg';
  try {
    const buf = Buffer.from(imageBase64.slice(0, 24), 'base64');
    if (buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xd8) return 'image/jpeg';
    if (buf.length >= 4 && buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) {
      return 'image/png';
    }
    if (buf.length >= 4 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) return 'image/gif';
    if (
      buf.length >= 12 && buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46 &&
      buf[8] === 0x57 && buf[9] === 0x45 && buf[10] === 0x42 && buf[11] === 0x50
    ) {
      return 'image/webp';
    }
  } catch (_) { /* ignore decode errors */ }
  return 'image/jpeg';
}

// ── Exports ──────────────────────────────────────────────────────────────────

module.exports = {
  // Handlers
  handlePageNotes,
  handleQuizFromPageNotes,

  // Splitting & chunking
  splitPageNotesChunks,

  // Validators
  dedupeQuizQuestions,
  isRedundantQuestion,
  hasMinimumCoverage,
  isValidQuizItem,
  batchDedupe,
  sha256Hash,
  levenshteinDistance,

  // Constants (for testing)
  QUIZ_CHUNK_MAX_CHARS,
  QUIZ_MIN_COVERAGE_CHUNKS,
  QUIZ_QUESTIONS_PER_CHUNK,
  QUIZ_MAX_QUESTIONS,
  LEVENSHTEIN_DEDUP_THRESHOLD,
  PAGE_NOTES_MAX_IMAGE_BYTES,
};
