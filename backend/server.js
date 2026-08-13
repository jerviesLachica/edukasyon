/**
 * StudentAI Backend API
 *
 * Secure proxy between Android app and OpenAI-compatible AI provider (hcnsec.cn).
 * ALL AI routes pass through AiSafetyGateway (auth, rate limits, moderation, validation).
 *
 * Endpoints:
 *   POST /api/ai/chat
 *   POST /api/ai/schedule-analysis
 *   POST /api/ai/summarize
 *   POST /api/ai/flashcards
 *   POST /api/ai/quiz
 *   POST /api/ai/study-plan
 *   POST /api/ai/assignment-breakdown
 *   POST /api/ai/focus-plan
 *
 * Run: npm install && npm start
 */

require('dotenv').config();

const crypto = require('crypto');
const express = require('express');
const { createGateway } = require('./ai/AiSafetyGateway');
const { createAiProvider } = require('./ai/AiProvider');
const {
  buildJarvisSystemMessage,
  buildChatUserContent,
  wrapUntrustedDocument,
} = require('./ai/PromptBuilder');
const { loadSafetyPolicy } = require('./safety/SafetyPolicy');
const { AuthenticationService } = require('./auth/AuthenticationService');
const { RateLimiter } = require('./abuse/RateLimiter');
const { QuotaService } = require('./abuse/QuotaService');
const { AbuseEventRepository } = require('./abuse/AbuseEventRepository');
const { UsageTracker } = require('./abuse/UsageTracker');
const { AbuseRiskService } = require('./abuse/AbuseRiskService');
const { InputModerator } = require('./safety/InputModerator');
const { OutputModerator } = require('./safety/OutputModerator');
const { PromptInjectionDetector } = require('./safety/PromptInjectionDetector');
const { DataClassifier } = require('./safety/DataClassifier');
const {
  AiResponseValidator,
  FlashcardValidator,
  QuizValidator,
  ScheduleValidator,
  AssignmentBreakdownValidator,
  FocusPlanValidator,
} = require('./validation/AiResponseValidator');
const {
  SCHEDULE_SCANNER_SYSTEM_PROMPT,
  SCHEDULE_SCANNER_USER_MESSAGE,
} = require('./prompts/schedule-scanner-system-prompt');
const {
  ASSIGNMENT_BREAKDOWN_SYSTEM_PROMPT,
  ASSIGNMENT_BREAKDOWN_USER_TEXT_PREFIX,
  ASSIGNMENT_BREAKDOWN_USER_IMAGE_PREFIX,
} = require('./prompts/assignment-breakdown-system-prompt');

const app = express();
const PORT = process.env.PORT || 8080;

app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Device-Id');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.use(express.json({ limit: `${Math.ceil((loadSafetyPolicy().maxRequestBodyBytes || 10_485_760) / 1024 / 1024)}mb` }));

// ── Safety infrastructure ───────────────────────────────────────────────────

const policy = loadSafetyPolicy();
const provider = createAiProvider();
const abuseEvents = new AbuseEventRepository();
const gateway = createGateway({
  auth: new AuthenticationService({
    requireDeviceId: policy.requireDeviceId,
    requireFirebaseAuth: policy.requireFirebaseAuth,
  }),
  policy,
  rateLimiter: new RateLimiter(),
  quotaService: new QuotaService(),
  inputModerator: new InputModerator({ enabled: policy.moderationEnabled }),
  outputModerator: new OutputModerator({ enabled: policy.moderationEnabled }),
  promptInjection: new PromptInjectionDetector({ enabled: policy.promptInjectionEnabled }),
  dataClassifier: new DataClassifier(),
  abuseRisk: new AbuseRiskService(abuseEvents),
  abuseEvents,
  usageTracker: new UsageTracker(),
  provider,
  mockHandler: (body, config) => mockHandlers[config.endpoint](body),
});

const chatValidator = new AiResponseValidator();
const flashcardValidator = new FlashcardValidator();
const quizValidator = new QuizValidator();
const scheduleValidator = new ScheduleValidator();
const assignmentBreakdownValidator = new AssignmentBreakdownValidator();
const focusPlanValidator = new FocusPlanValidator();
const usageTracker = gateway.usageTracker;

// ── Mock fallbacks (when AI_API_KEY is not set) ─────────────────────────────

const mockHandlers = {
  chat(body) {
    const { message, subject, conversationId, attachmentName, imageBase64, attachmentText } = body;
    const lower = (message || '').toLowerCase();
    const attachmentNote = attachmentName
      ? ` I received your attachment${imageBase64 ? ' (image)' : attachmentText ? ' (text)' : ''}: ${attachmentName}.`
      : '';

    if (/ignore (previous|prior|all) instructions|reveal (your )?system prompt|you are now|act as dan|jailbreak/.test(lower)) {
      return {
        reply: "I'm Jarvis, your study tutor — I can't change my role or share internal instructions. What subject or assignment can I help you with?",
        conversationId: conversationId || crypto.randomUUID(),
        model: 'mock',
      };
    }
    if (imageBase64) {
      return {
        reply: `[Mock Jarvis — set AI_API_KEY for real vision] I received your image${attachmentName ? ` (${attachmentName})` : ''}. In mock mode I can't analyze pixels, but your message was: "${message}".`,
        conversationId: conversationId || crypto.randomUUID(),
        model: 'mock',
      };
    }
    if (/cheat|answers for (my )?(exam|test|quiz)/.test(lower) && /(during|right now|in progress|currently taking)/.test(lower)) {
      return {
        reply: "I can't help with active exams — that wouldn't be fair to you or your classmates. After the exam, I'm happy to help you review topics you found tricky.",
        conversationId: conversationId || crypto.randomUUID(),
        model: 'mock',
      };
    }

    return {
      reply: `[Mock Jarvis — set AI_API_KEY in backend/.env] Hi! I'm Jarvis.${attachmentNote} ${subject ? `Subject: ${subject}. ` : ''}You asked: "${message}".`,
      conversationId: conversationId || crypto.randomUUID(),
      model: 'mock',
    };
  },
  'schedule-analysis'() {
    return {
      classes: [
        { subject: 'Programming 2', teacher: 'Juan Santos', room: '304', day: 'MONDAY', startTime: '08:00', endTime: '09:30' },
        { subject: 'Database Management', teacher: 'Maria Cruz', room: '201', day: 'WEDNESDAY', startTime: '10:00', endTime: '11:30' },
      ],
      uncertainFields: ['room for Database Management'],
    };
  },
  summarize(body) {
    const text = body.text || '';
    const words = text.split(/\s+/).slice(0, 30);
    return { result: `Summary: ${words.join(' ')}${text.split(/\s+/).length > 30 ? '...' : ''}` };
  },
  flashcards() {
    return {
      cards: [
        { question: 'What is the main topic?', answer: 'Review your notes for the key concept.', topic: 'General' },
        { question: 'Define a key term from the material.', answer: 'See your source notes.', topic: 'General' },
      ],
    };
  },
  quiz() {
    return {
      title: 'Generated Quiz',
      questions: [
        { type: 'MULTIPLE_CHOICE', question: 'Sample question from notes?', options: ['Option A', 'Option B', 'Option C'], correctAnswer: 'Option A' },
        { type: 'TRUE_FALSE', question: 'This material is worth reviewing.', options: ['True', 'False'], correctAnswer: 'True' },
      ],
    };
  },
  'study-plan'(body) {
    const subjects = body.subjects?.length ? body.subjects : ['Review'];
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
    return {
      title: 'Study Plan',
      items: subjects.map((subject, i) => ({
        dayOfWeek: days[i % days.length],
        startTime: '18:00',
        endTime: '18:45',
        subjectName: subject,
        topic: body.topics?.[i] || 'Review',
        activity: 'Study session',
      })),
    };
  },
  'assignment-breakdown'(body) {
    const text = body.text || body.attachmentText || '';
    const titleMatch = text.match(/(?:assignment|project|paper|essay)[:\s]+(.{5,60})/i);
    const title = titleMatch ? titleMatch[1].trim() : 'Sample Assignment';
    const deadline = new Date(Date.now() + 7 * 86_400_000).toISOString().slice(0, 10);
    return {
      title,
      deadline,
      requirements: ['Follow the assignment instructions', 'Cite sources where required'],
      deliverables: ['Completed submission file', 'Cover page with name and date'],
      rubric: ['Content accuracy', 'Organization and clarity', 'Timeliness'],
      subtasks: [
        { title: 'Read instructions and rubric', estimatedMinutes: 20, dueOffsetDays: 6 },
        { title: 'Research and gather sources', estimatedMinutes: 90, dueOffsetDays: 4 },
        { title: 'Draft outline', estimatedMinutes: 45, dueOffsetDays: 3 },
        { title: 'Write first draft', estimatedMinutes: 120, dueOffsetDays: 2 },
        { title: 'Revise and proofread', estimatedMinutes: 60, dueOffsetDays: 1 },
        { title: 'Final review and submit', estimatedMinutes: 30, dueOffsetDays: 0 },
      ],
      estimatedEffortHours: 6,
      notes: 'Mock breakdown — set AI_API_KEY for real analysis.',
    };
  },
  'focus-plan'(body) {
    const total = Math.min(Math.max(parseInt(body.totalMinutes, 10) || 90, 15), 240);
    const subjects = body.subjects?.length ? body.subjects : ['General review'];
    const breakGap = 5;
    const blockDuration = Math.max(15, Math.floor(total / subjects.length) - breakGap);
    const blocks = subjects.map((subject, index) => {
      const start = index * (blockDuration + breakGap);
      const end = Math.min(start + blockDuration, total);
      return {
        startMinute: start,
        endMinute: end,
        activity: subject,
        type: index === subjects.length - 1 && total - end >= 10 ? 'REVIEW' : 'STUDY',
      };
    }).filter((b) => b.endMinute > b.startMinute);
    return {
      totalMinutes: total,
      blocks: blocks.length ? blocks : [{ startMinute: 0, endMinute: total, activity: subjects[0], type: 'STUDY' }],
      breakMinutesBetween: breakGap,
    };
  },
};

// ── Route handlers (business logic only — safety handled by gateway) ─────────

async function handleChat({ body, provider: ai, maxTokens, signal }) {
  const {
    message,
    subject,
    contextSummary,
    conversationId,
    attachmentName,
    attachmentMimeType,
    imageBase64,
    attachmentText,
    model: requestedModel,
    systemPrompt: clientSystemPrompt,
    system: clientSystemAlias,
  } = body;

  const hasVisionAttachment = Boolean(imageBase64) || ai.requestHasVisionContent(body);
  const model = ai.resolveChatModel(requestedModel, hasVisionAttachment);

  const systemContent = buildJarvisSystemMessage({
    subject,
    contextSummary,
    clientSystemPrompt: clientSystemPrompt || clientSystemAlias,
    hasVisionAttachment,
    hasTextAttachment: Boolean(attachmentText),
  });

  const userContent = buildChatUserContent({
    message,
    attachmentName,
    attachmentMimeType,
    imageBase64,
    attachmentText,
  });

  const messages = [
    { role: 'system', content: systemContent },
    { role: 'user', content: userContent },
  ];

  const { reply, reasoning, model: usedModel } = await ai.chatCompletion(messages, {
    model,
    isVision: hasVisionAttachment,
    maxTokens,
    signal,
  });

  return {
    reply,
    ...(reasoning ? { reasoning } : {}),
    conversationId: conversationId || crypto.randomUUID(),
    model: usedModel || model,
  };
}

async function handleScheduleAnalysis({ body, provider: ai, maxTokens, signal }) {
  const { imageBase64, model: requestedModel, systemPrompt: clientSystemPrompt } = body;
  if (clientSystemPrompt) {
    console.warn('[schedule-analysis] Ignoring client-supplied systemPrompt.');
  }
  if (!imageBase64) throw new Error('imageBase64 is required');

  const model = ai.resolveVisionModel(requestedModel);
  const content = await ai.chatCompletionText(
    [
      { role: 'system', content: SCHEDULE_SCANNER_SYSTEM_PROMPT },
      {
        role: 'user',
        content: [
          { type: 'text', text: SCHEDULE_SCANNER_USER_MESSAGE },
          { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
        ],
      },
    ],
    { temperature: 0.2, maxTokens, model, isVision: true, signal }
  );

  const parsed = ai.extractJson(content);
  return {
    classes: parsed.classes || [],
    uncertainFields: parsed.uncertainFields || [],
  };
}

async function handleSummarize({ body, provider: ai, maxTokens, signal }) {
  const text = body.text || '';
  const model = ai.resolveTextModel(body.model);
  const result = await ai.chatCompletionText(
    [
      { role: 'system', content: 'Summarize study notes concisely. Preserve key facts and terminology. Use plain text, no bullet markdown unless helpful.' },
      { role: 'user', content: `Summarize these notes:\n\n${wrapUntrustedDocument(text)}` },
    ],
    { temperature: 0.3, maxTokens, model, signal }
  );
  return { result };
}

async function handleFlashcards({ body, provider: ai, maxTokens, signal }) {
  const text = body.text || '';
  const model = ai.resolveTextModel(body.model);
  const content = await ai.chatCompletionText(
    [
      { role: 'system', content: 'Generate study flashcards from notes. Respond with JSON only.' },
      {
        role: 'user',
        content: `Create 5-8 flashcards from this material. JSON shape:
{"cards":[{"question":"...","answer":"...","topic":"optional topic"}]}
Notes:\n${wrapUntrustedDocument(text)}`,
      },
    ],
    { temperature: 0.5, maxTokens, model, signal }
  );
  const parsed = ai.extractJson(content);
  return { cards: parsed.cards || [] };
}

async function handleQuiz({ body, provider: ai, maxTokens, signal }) {
  const text = body.text || '';
  const model = ai.resolveTextModel(body.model);
  const content = await ai.chatCompletionText(
    [
      { role: 'system', content: 'Generate quizzes from study material. Respond with JSON only.' },
      {
        role: 'user',
        content: `Create a quiz (5-8 questions) from this material. JSON shape:
{"title":"Quiz title","questions":[{"type":"MULTIPLE_CHOICE|TRUE_FALSE","question":"...","options":["..."],"correctAnswer":"..."}]}
Use MULTIPLE_CHOICE with 3-4 options, or TRUE_FALSE with options ["True","False"].
Notes:\n${wrapUntrustedDocument(text)}`,
      },
    ],
    { temperature: 0.5, maxTokens, model, signal }
  );
  const parsed = ai.extractJson(content);
  return { title: parsed.title || 'Generated Quiz', questions: parsed.questions || [] };
}

async function handleStudyPlan({ body, provider: ai, maxTokens, signal }) {
  const { examDate, availableHours, subjects, topics } = body;
  const model = ai.resolveTextModel(body.model);
  const exam = examDate ? new Date(examDate).toISOString().slice(0, 10) : 'unknown';
  const content = await ai.chatCompletionText(
    [
      { role: 'system', content: 'Create realistic weekly study plans for students. Respond with JSON only.' },
      {
        role: 'user',
        content: `Build a study plan. JSON shape:
{"title":"Plan title","items":[{"dayOfWeek":"MONDAY|...","startTime":"HH:MM","endTime":"HH:MM","subjectName":"...","topic":"...","activity":"..."}]}
Exam date: ${exam}
Available hours per week: ${availableHours}
Subjects: ${(subjects || []).join(', ')}
Topics: ${(topics || []).join(', ')}`,
      },
    ],
    { temperature: 0.4, maxTokens, model, signal }
  );
  const parsed = ai.extractJson(content);
  return { title: parsed.title || 'Study Plan', items: parsed.items || [] };
}

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
      buf.length >= 12 &&
      buf[0] === 0x52 &&
      buf[1] === 0x49 &&
      buf[2] === 0x46 &&
      buf[3] === 0x46 &&
      buf[8] === 0x57 &&
      buf[9] === 0x45 &&
      buf[10] === 0x42 &&
      buf[11] === 0x50
    ) {
      return 'image/webp';
    }
  } catch (_) {
    /* ignore decode errors */
  }
  return 'image/jpeg';
}

function normalizeAssignmentBreakdown(parsed, { hasImage }) {
  const requirements = Array.isArray(parsed.requirements) ? parsed.requirements : [];
  const deliverables = Array.isArray(parsed.deliverables) ? parsed.deliverables : [];
  const rubric = Array.isArray(parsed.rubric) ? parsed.rubric : [];
  const hasExtractedContent =
    Boolean(String(parsed.title || '').trim()) ||
    requirements.length > 0 ||
    deliverables.length > 0 ||
    rubric.length > 0 ||
    Boolean(parsed.deadline);

  if (hasImage && !hasExtractedContent) {
    throw new Error(
      'Could not extract assignment details from the image. Try a clearer photo, paste the text, or upload a PDF.'
    );
  }

  let title = typeof parsed.title === 'string' ? parsed.title.trim() : '';
  if (!title) title = hasImage ? 'Assignment from image' : 'Assignment';

  let subtasks = Array.isArray(parsed.subtasks) ? parsed.subtasks.filter((item) => item && item.title) : [];
  if (subtasks.length === 0) {
    subtasks = [
      { title: 'Review assignment requirements', estimatedMinutes: 30, dueOffsetDays: 4 },
      { title: 'Complete assignment work', estimatedMinutes: 90, dueOffsetDays: 2 },
      { title: 'Review and submit', estimatedMinutes: 30, dueOffsetDays: 0 },
    ];
  }

  return {
    title,
    deadline: parsed.deadline ?? null,
    requirements,
    deliverables,
    rubric,
    subtasks,
    estimatedEffortHours: parsed.estimatedEffortHours ?? 1,
    notes: typeof parsed.notes === 'string' ? parsed.notes : '',
  };
}

async function handleAssignmentBreakdown({ body, provider: ai, maxTokens, signal }) {
  const { text, attachmentText, imageBase64, model: requestedModel, systemPrompt: clientSystemPrompt } = body;
  if (clientSystemPrompt) {
    console.warn('[assignment-breakdown] Ignoring client-supplied systemPrompt.');
  }

  const documentText = [text, attachmentText].filter(Boolean).join('\n\n').trim();
  const hasImage = Boolean(imageBase64);

  if (!documentText && !hasImage) {
    throw new Error('text or imageBase64 is required');
  }

  const model = hasImage
    ? ai.resolveVisionModel(requestedModel)
    : ai.resolveTextModel(requestedModel);

  const imageMime = hasImage ? detectImageMimeFromBase64(imageBase64) : null;

  let userContent;
  if (hasImage) {
    userContent = [
      {
        type: 'text',
        text: documentText
          ? `${ASSIGNMENT_BREAKDOWN_USER_IMAGE_PREFIX}\n\n${wrapUntrustedDocument(documentText, 'Assignment text')}`
          : ASSIGNMENT_BREAKDOWN_USER_IMAGE_PREFIX,
      },
      {
        type: 'image_url',
        image_url: { url: `data:${imageMime};base64,${imageBase64}` },
      },
    ];
  } else {
    userContent = `${ASSIGNMENT_BREAKDOWN_USER_TEXT_PREFIX}\n\n${wrapUntrustedDocument(documentText, 'Assignment instructions')}`;
  }

  const content = await ai.chatCompletionText(
    [
      { role: 'system', content: ASSIGNMENT_BREAKDOWN_SYSTEM_PROMPT },
      { role: 'user', content: userContent },
    ],
    { temperature: 0.2, maxTokens, model, isVision: hasImage, signal }
  );

  const parsed = ai.extractJson(content);
  return normalizeAssignmentBreakdown(parsed, { hasImage });
}

async function handleFocusPlan({ body, provider: ai, maxTokens, signal }) {
  const { totalMinutes, subjects, upcomingExams, weakAreas, userPrompt } = body;
  const total = Math.min(Math.max(parseInt(totalMinutes, 10) || 90, 15), 240);
  const model = ai.resolveTextModel(body.model);

  const contextLines = [
    `Total session length: ${total} minutes`,
    `Subjects: ${(subjects || []).join(', ') || 'none specified'}`,
    upcomingExams?.length ? `Upcoming exams: ${upcomingExams.join(', ')}` : null,
    weakAreas?.length ? `Weak areas / low grades: ${weakAreas.join(', ')}` : null,
    userPrompt?.trim() ? `Student request: ${userPrompt.trim()}` : null,
  ].filter(Boolean).join('\n');

  const content = await ai.chatCompletionText(
    [
      {
        role: 'system',
        content: 'You are Jarvis, an academic focus session planner. Create realistic timed study blocks for a student. Respond with JSON only.',
      },
      {
        role: 'user',
        content: `Plan a focus session. JSON shape:
{"totalMinutes":${total},"blocks":[{"startMinute":0,"endMinute":30,"activity":"Subject or task name","type":"STUDY|BREAK|REVIEW"}],"breakMinutesBetween":5}
Rules:
- Blocks must fit within totalMinutes (${total})
- Include short breaks (type BREAK) between study blocks when helpful
- Prioritize weak areas and upcoming exams
- Use STUDY for main work, REVIEW for flashcard/quiz review
- startMinute/endMinute are minutes from session start (0-based)
Context:
${contextLines}`,
      },
    ],
    { temperature: 0.4, maxTokens, model, signal }
  );

  const parsed = ai.extractJson(content);
  return {
    totalMinutes: parsed.totalMinutes || total,
    blocks: parsed.blocks || [],
    breakMinutesBetween: parsed.breakMinutesBetween ?? 5,
  };
}

// ── Routes (all via AiSafetyGateway) ────────────────────────────────────────

app.post('/api/ai/chat', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'chat',
    extractInputText: (body) => [body.message, body.attachmentText, body.contextSummary].filter(Boolean).join('\n'),
    validate: (body) => {
      if (!body.message && !body.imageBase64 && !body.attachmentText) {
        return { ok: false, code: 'MISSING_INPUT', message: 'Message or attachment is required.' };
      }
      return { ok: true };
    },
    handler: handleChat,
    validateOutput: (result) => chatValidator.validateChatResult(result),
  })
);

app.post('/api/ai/schedule-analysis', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'schedule-analysis',
    extractInputText: () => 'schedule image analysis',
    validate: (body) => {
      if (!body.imageBase64) {
        return { ok: false, code: 'MISSING_IMAGE', message: 'imageBase64 is required.' };
      }
      return { ok: true };
    },
    handler: handleScheduleAnalysis,
    validateOutput: (result) => scheduleValidator.validate(result),
  })
);

app.post('/api/ai/summarize', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'summarize',
    extractInputText: (body) => body.text || '',
    validate: (body) => {
      if (!body.text || !String(body.text).trim()) {
        return { ok: false, code: 'MISSING_TEXT', message: 'text is required.' };
      }
      return { ok: true };
    },
    handler: handleSummarize,
    validateOutput: (result) => chatValidator.validateTextResult(result.result),
  })
);

app.post('/api/ai/flashcards', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'flashcards',
    extractInputText: (body) => body.text || '',
    validate: (body) => {
      if (!body.text || !String(body.text).trim()) {
        return { ok: false, code: 'MISSING_TEXT', message: 'text is required.' };
      }
      return { ok: true };
    },
    handler: handleFlashcards,
    validateOutput: (result) => flashcardValidator.validate(result),
  })
);

app.post('/api/ai/quiz', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'quiz',
    extractInputText: (body) => body.text || '',
    validate: (body) => {
      if (!body.text || !String(body.text).trim()) {
        return { ok: false, code: 'MISSING_TEXT', message: 'text is required.' };
      }
      return { ok: true };
    },
    handler: handleQuiz,
    validateOutput: (result) => quizValidator.validate(result),
  })
);

app.post('/api/ai/study-plan', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'study-plan',
    extractInputText: (body) => [(body.subjects || []).join(' '), (body.topics || []).join(' ')].join('\n'),
    validate: (body) => {
      if (!body.subjects || !Array.isArray(body.subjects) || body.subjects.length === 0) {
        return { ok: false, code: 'MISSING_SUBJECTS', message: 'subjects array is required.' };
      }
      return { ok: true };
    },
    handler: handleStudyPlan,
    validateOutput: (result) => {
      if (!result || !Array.isArray(result.items)) {
        return { valid: false, error: 'Missing study plan items' };
      }
      return { valid: true, data: result };
    },
  })
);

app.post('/api/ai/assignment-breakdown', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'assignment-breakdown',
    extractInputText: (body) => [body.text, body.attachmentText].filter(Boolean).join('\n') || 'assignment image analysis',
    validate: (body) => {
      const hasText = Boolean(body.text?.trim() || body.attachmentText?.trim());
      const hasImage = Boolean(body.imageBase64);
      if (!hasText && !hasImage) {
        return { ok: false, code: 'MISSING_INPUT', message: 'text or imageBase64 is required.' };
      }
      return { ok: true };
    },
    handler: handleAssignmentBreakdown,
    validateOutput: (result) => assignmentBreakdownValidator.validate(result),
  })
);

app.post('/api/ai/focus-plan', (req, res) =>
  gateway.handle(req, res, {
    endpoint: 'focus-plan',
    extractInputText: (body) =>
      [
        String(body.totalMinutes || ''),
        (body.subjects || []).join(' '),
        (body.upcomingExams || []).join(' '),
        (body.weakAreas || []).join(' '),
        body.userPrompt || '',
      ].join('\n'),
    validate: (body) => {
      const total = parseInt(body.totalMinutes, 10);
      if (!Number.isFinite(total) || total < 15 || total > 240) {
        return { ok: false, code: 'INVALID_DURATION', message: 'totalMinutes must be between 15 and 240.' };
      }
      return { ok: true };
    },
    handler: handleFocusPlan,
    validateOutput: (result) => focusPlanValidator.validate(result),
  })
);

app.get('/health', (_, res) =>
  res.json({
    status: 'ok',
    aiConfigured: provider.hasAiKey,
    provider: provider.AI_BASE_URL,
    model: provider.DEFAULT_MODEL,
    defaultModel: provider.DEFAULT_MODEL,
    textModel: provider.TEXT_MODEL,
    visionModel: provider.VISION_MODEL,
    allowedModels: provider.ALLOWED_MODELS,
    safetyEnabled: true,
    usage: usageTracker.snapshot(),
    routingPolicy:
      'Text-only chat and study tools → auto. Image/PDF vision (chat attachments, schedule scanner, assignment breakdown) → step-3.7-flash.',
  })
);

// Internal admin-style stats (no secrets) — useful for monitoring
app.get('/health/safety', (_, res) => {
  res.json({
    policy: {
      maxInputChars: policy.maxInputChars,
      globalDailyQuota: policy.globalDailyQuota,
      endpoints: policy.endpoints,
    },
    abuseEventCounts: abuseEvents.countByType(),
    usage: usageTracker.snapshot(),
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`StudentAI backend listening on :${PORT}`);
  if (provider.hasAiKey) {
    console.log(`AI provider: ${provider.AI_BASE_URL} (text: ${provider.TEXT_MODEL}, vision: ${provider.VISION_MODEL})`);
  } else {
    console.log('AI provider: mock mode (set AI_API_KEY in backend/.env)');
  }
  console.log(`Safety gateway: moderation=${policy.moderationEnabled}, chat rate=${policy.endpoints.chat.rateLimitPerMin}/min`);
});
