/**
 * PageNotesService unit tests.
 * Tests pure functions: splitPageNotesChunks, dedupeQuizQuestions, isRedundantQuestion,
 * hasMinimumCoverage, isValidQuizItem, sha256Hash, levenshteinDistance, batchDedupe.
 * No live AI calls — fixture transcripts for chunking tests.
 * Run: node --test tests/pageNotes.test.js
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const {
  splitPageNotesChunks,
  dedupeQuizQuestions,
  isRedundantQuestion,
  hasMinimumCoverage,
  isValidQuizItem,
  batchDedupe,
  sha256Hash,
  levenshteinDistance,
  QUIZ_CHUNK_MAX_CHARS,
  QUIZ_MIN_COVERAGE_CHUNKS,
  LEVENSHTEIN_DEDUP_THRESHOLD,
  PAGE_NOTES_MAX_IMAGE_BYTES,
} = require('../ai/PageNotesService');

// ── Levenshtein Distance ──────────────────────────────────────────────────────

describe('levenshteinDistance', () => {
  it('identical strings → 0', () => {
    assert.equal(levenshteinDistance('abc', 'abc'), 0);
  });

  it('empty vs non-empty → length of non-empty', () => {
    assert.equal(levenshteinDistance('', 'abc'), 3);
    assert.equal(levenshteinDistance('xyz', ''), 3);
  });

  it('single char difference → 1', () => {
    assert.equal(levenshteinDistance('abc', 'abd'), 1);
  });

  it('completely different → max length', () => {
    assert.equal(levenshteinDistance('abc', 'xyz'), 3);
  });

  it('insertion', () => {
    assert.equal(levenshteinDistance('cat', 'cats'), 1);
  });

  it('deletion', () => {
    assert.equal(levenshteinDistance('cats', 'cat'), 1);
  });

  it('within threshold', () => {
    const d = levenshteinDistance('what is the capital of france', 'what is the captial of france');
    assert.ok(d <= LEVENSHTEIN_DEDUP_THRESHOLD, `expected ≤ ${LEVENSHTEIN_DEDUP_THRESHOLD}, got ${d}`);
  });
});

// ── SHA-256 Hash ──────────────────────────────────────────────────────────────

describe('sha256Hash', () => {
  it('returns 64-char hex string', () => {
    const h = sha256Hash('hello world');
    assert.equal(h.length, 64);
    assert.match(h, /^[0-9a-f]{64}$/);
  });

  it('deterministic', () => {
    assert.equal(sha256Hash('test'), sha256Hash('test'));
  });

  it('different inputs → different hashes', () => {
    assert.notEqual(sha256Hash('a'), sha256Hash('b'));
  });
});

// ── splitPageNotesChunks ──────────────────────────────────────────────────────

describe('splitPageNotesChunks', () => {
  const MATH_NOTES = `# Calculus I — Lecture 7

## The Chain Rule

Theorem: If h(x) = f(g(x)), then h'(x) = f'(g(x)) * g'(x).
${'This is additional filler content about the chain rule and its applications in calculus. '.repeat(40)}

## Applications

The chain rule is used in implicit differentiation and related rates problems.
${'Applications include related rates, implicit differentiation, and higher-order derivatives. '.repeat(40)}

### Related Rates

A ladder slides down a wall. At what rate is the angle changing?
${'Related rates problems involve multiple changing quantities connected by an equation. '.repeat(40)}`;

  it('returns empty array for empty/null input', () => {
    assert.deepEqual(splitPageNotesChunks(''), []);
    assert.deepEqual(splitPageNotesChunks(null), []);
  });

  it('splits on heading boundaries', () => {
    const chunks = splitPageNotesChunks(MATH_NOTES);
    assert.ok(chunks.length >= 2, `expected ≥2 chunks, got ${chunks.length}`);
  });

  it('each chunk ≤ QUIZ_CHUNK_MAX_CHARS', () => {
    const chunks = splitPageNotesChunks(MATH_NOTES);
    for (const chunk of chunks) {
      assert.ok(
        chunk.text.length <= QUIZ_CHUNK_MAX_CHARS + 100, // allow some heading overhead
        `chunk "${chunk.heading}" is ${chunk.text.length} chars, expected ≤ ${QUIZ_CHUNK_MAX_CHARS}`
      );
    }
  });

  it('preserves headings in chunk metadata', () => {
    const chunks = splitPageNotesChunks(MATH_NOTES);
    assert.ok(chunks.some((c) => c.heading === 'The Chain Rule'), 'missing heading "The Chain Rule"');
  });

  it('handles very large content (no heading split needed)', () => {
    const bigContent = 'A'.repeat(QUIZ_CHUNK_MAX_CHARS * 5);
    const chunks = splitPageNotesChunks(bigContent);
    assert.ok(chunks.length >= 5, `expected ≥5 chunks for large content, got ${chunks.length}`);
    for (const chunk of chunks) {
      assert.ok(chunk.text.length <= QUIZ_CHUNK_MAX_CHARS);
    }
  });

  it('handles content with no headings', () => {
    const noHeadings = 'Just plain text without any markdown headings at all.\n'.repeat(20);
    const chunks = splitPageNotesChunks(noHeadings);
    assert.ok(chunks.length >= 1, 'should produce at least one chunk');
  });
});

// ── dedupeQuizQuestions ───────────────────────────────────────────────────────

describe('dedupeQuizQuestions', () => {
  it('removes exact duplicates (same question text)', () => {
    const qs = [
      { question: 'What is 2+2?', options: ['3', '4', '5'], correctAnswer: '4' },
      { question: 'What is 2+2?', options: ['3', '4', '5'], correctAnswer: '4' },
    ];
    const result = dedupeQuizQuestions(qs);
    assert.equal(result.length, 1);
  });

  it('removes near-duplicates (levenshtein ≤ threshold)', () => {
    const qs = [
      { question: 'What is the capital of France?', options: ['Paris', 'Lyon', 'Marseille'], correctAnswer: 'Paris' },
      { question: 'What is the captial of France?', options: ['Paris', 'Lyon', 'Marseille'], correctAnswer: 'Paris' },
    ];
    const result = dedupeQuizQuestions(qs);
    assert.equal(result.length, 1);
  });

  it('keeps distinct questions', () => {
    const qs = [
      { question: 'What is the capital of France?', options: ['Paris', 'Lyon', 'Marseille'], correctAnswer: 'Paris' },
      { question: 'What is the largest planet in the solar system?', options: ['Earth', 'Jupiter', 'Mars'], correctAnswer: 'Jupiter' },
      { question: 'What are mitochondria?', options: ['Cell wall', 'Powerhouse of cell', 'Nucleus'], correctAnswer: 'Powerhouse of cell' },
    ];
    const result = dedupeQuizQuestions(qs);
    assert.equal(result.length, 3);
  });

  it('skips null/malformed entries', () => {
    const qs = [
      null,
      { question: '', options: [], correctAnswer: '' },
      { question: 'Valid question?', options: ['Yes', 'No'], correctAnswer: 'Yes' },
    ];
    const result = dedupeQuizQuestions(qs);
    assert.equal(result.length, 1);
  });
});

// ── isRedundantQuestion ───────────────────────────────────────────────────────

describe('isRedundantQuestion', () => {
  it('detects answer-in-question leakage', () => {
    assert.ok(isRedundantQuestion({
      question: 'What is the largest planet in the solar system called jupiter?',
      options: ['Jupiter', 'Saturn'],
      correctAnswer: 'Jupiter',
    }));
  });

  it('does not flag leakage when answer is short (≤5 chars)', () => {
    assert.ok(!isRedundantQuestion({
      question: 'What is 2+2?',
      options: ['3', '4'],
      correctAnswer: '4',
    }));
  });

  it('no leakage when answer not in question', () => {
    assert.ok(!isRedundantQuestion({
      question: 'What is the largest planet?',
      options: ['Earth', 'Jupiter', 'Mars'],
      correctAnswer: 'Jupiter',
    }));
  });

  it('handles null input gracefully', () => {
    assert.ok(!isRedundantQuestion(null));
    assert.ok(!isRedundantQuestion({}));
  });
});

// ── hasMinimumCoverage ────────────────────────────────────────────────────────

describe('hasMinimumCoverage', () => {
  it('returns true when ≥ QUIZ_MIN_COVERAGE_CHUNKS distinct page-chunk pairs', () => {
    const qs = [
      { page: 1, chunkIndex: 0 },
      { page: 1, chunkIndex: 1 },
      { page: 2, chunkIndex: 0 },
    ];
    assert.ok(hasMinimumCoverage(qs));
  });

  it('returns false when fewer distinct page-chunk pairs', () => {
    const qs = [
      { page: 1, chunkIndex: 0 },
      { page: 1, chunkIndex: 0 },  // duplicate
    ];
    assert.ok(!hasMinimumCoverage(qs));
  });

  it('returns false for empty array', () => {
    assert.ok(!hasMinimumCoverage([]));
  });
});

// ── isValidQuizItem ───────────────────────────────────────────────────────────

describe('isValidQuizItem', () => {
  it('valid MC question', () => {
    assert.ok(isValidQuizItem({
      type: 'MULTIPLE_CHOICE',
      question: 'What is 2+2?',
      options: ['3', '4', '5'],
      correctAnswer: '4',
    }));
  });

  it('valid TF question', () => {
    assert.ok(isValidQuizItem({
      type: 'TRUE_FALSE',
      question: 'The earth is round.',
      options: ['True', 'False'],
      correctAnswer: 'True',
    }));
  });

  it('rejects missing question', () => {
    assert.ok(!isValidQuizItem({ options: ['A'], correctAnswer: 'A' }));
  });

  it('rejects correctAnswer not in options', () => {
    assert.ok(!isValidQuizItem({
      question: 'Q?',
      options: ['A', 'B'],
      correctAnswer: 'C',
    }));
  });

  it('rejects < 2 options', () => {
    assert.ok(!isValidQuizItem({
      question: 'Q?',
      options: ['A'],
      correctAnswer: 'A',
    }));
  });

  it('rejects null', () => {
    assert.ok(!isValidQuizItem(null));
  });
});

// ── batchDedupe ───────────────────────────────────────────────────────────────

describe('batchDedupe', () => {
  it('removes exact hash duplicates', () => {
    const items = [
      { id: 1, text: 'hello' },
      { id: 2, text: 'hello' },
      { id: 3, text: 'world' },
    ];
    const { unique, duplicatesRemoved } = batchDedupe(items, (i) => i.text);
    assert.equal(unique.length, 2);
    assert.equal(duplicatesRemoved, 1);
  });

  it('case-insensitive dedup', () => {
    const items = [
      { id: 1, text: 'Hello' },
      { id: 2, text: 'hello' },
    ];
    const { unique, duplicatesRemoved } = batchDedupe(items, (i) => i.text);
    assert.equal(unique.length, 1);
    assert.equal(duplicatesRemoved, 1);
  });

  it('skips empty text', () => {
    const items = [
      { id: 1, text: '' },
      { id: 2, text: 'valid' },
    ];
    const { unique } = batchDedupe(items, (i) => i.text);
    assert.equal(unique.length, 1);
  });
});

// ── Integration: chunk → dedupe → validate round-trip ─────────────────────────

describe('chunk → dedupe → validate round-trip', () => {
  const SECTIONS = [];
  for (let i = 1; i <= 6; i++) SECTIONS.push(`## Section ${String.fromCharCode(64 + i)}\n${'A'.repeat(3200)}`);
  const LONG_NOTES = `# Lecture 1\n\n${SECTIONS.join('\n\n')}`;

  it('produces valid chunks from long notes', () => {
    const chunks = splitPageNotesChunks(LONG_NOTES);
    assert.ok(chunks.length >= 3, `expected ≥3 chunks, got ${chunks.length}`);
    for (const c of chunks) {
      assert.ok(c.text.length > 0);
      assert.ok(typeof c.heading === 'string');
    }
  });

  it('all chunks within size limit', () => {
    const chunks = splitPageNotesChunks(LONG_NOTES);
    for (const c of chunks) {
      assert.ok(
        c.text.length <= QUIZ_CHUNK_MAX_CHARS + 100,
        `chunk "${c.heading}" is ${c.text.length} chars`
      );
    }
  });
});
