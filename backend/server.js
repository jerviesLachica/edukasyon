/**
 * StudentAI Backend API
 *
 * Secure proxy between Android app and OpenAI-compatible AI providers.
 * API keys MUST be stored in backend/.env (never in source or the Android app).
 *
 * Endpoints:
 *   POST /api/ai/chat
 *   POST /api/ai/schedule-analysis
 *   POST /api/ai/summarize
 *   POST /api/ai/flashcards
 *   POST /api/ai/quiz
 *   POST /api/ai/study-plan
 *
 * Run: npm install && npm start
 */

require('dotenv').config();

const crypto = require('crypto');
const express = require('express');

const app = express();

app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.use(express.json({ limit: '10mb' }));

const PORT = process.env.PORT || 8080;
const AI_API_KEY = process.env.AI_API_KEY;
const AI_MODEL = process.env.AI_MODEL || 'auto';
const AI_BASE_URL = (process.env.AI_BASE_URL || 'https://api.hcnsec.cn/v1').replace(/\/$/, '');

const hasAiKey = Boolean(AI_API_KEY);

// ── OpenAI-compatible client ────────────────────────────────────────────────

async function chatCompletion(messages, { temperature = 0.7, maxTokens = 2048 } = {}) {
  const res = await fetch(`${AI_BASE_URL}/chat/completions`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${AI_API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: AI_MODEL,
      messages,
      temperature,
      max_tokens: maxTokens,
    }),
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`AI API error ${res.status}: ${body.slice(0, 300)}`);
  }

  const data = await res.json();
  const content = data.choices?.[0]?.message?.content;
  if (!content) throw new Error('AI API returned empty response');
  return content.trim();
}

function extractJson(text) {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const raw = fenced ? fenced[1].trim() : text.trim();
  return JSON.parse(raw);
}

async function callAiOrMock(hasKeyFn, aiFn, mockFn, res) {
  try {
    if (!hasKeyFn()) {
      return res.json(mockFn());
    }
    const result = await aiFn();
    res.json(result);
  } catch (err) {
    console.error(err.message || err);
    res.status(502).json({ error: 'AI provider request failed', detail: err.message });
  }
}

// ── Mock fallbacks (used when AI_API_KEY is not set) ────────────────────────

const mock = {
  chat(body) {
    const { message, subject, conversationId } = body;
    return {
      reply: `[Mock AI] Configure AI_API_KEY in backend/.env for real responses. Subject: ${subject || 'none'}. You asked: ${message}`,
      conversationId: conversationId || crypto.randomUUID(),
    };
  },
  scheduleAnalysis() {
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
  studyPlan(body) {
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
};

// ── Routes ──────────────────────────────────────────────────────────────────

app.post('/api/ai/chat', (req, res) => {
  callAiOrMock(
    () => hasAiKey,
    async () => {
      const { message, subject, contextSummary, conversationId } = req.body;
      const systemParts = [
        'You are StudentAI, a helpful academic tutor for students.',
        'Explain concepts clearly, encourage learning, and stay concise.',
      ];
      if (subject) systemParts.push(`Current subject: ${subject}.`);
      if (contextSummary) systemParts.push(`Student context: ${contextSummary}`);

      const reply = await chatCompletion([
        { role: 'system', content: systemParts.join(' ') },
        { role: 'user', content: message },
      ]);

      return { reply, conversationId: conversationId || crypto.randomUUID() };
    },
    () => mock.chat(req.body),
    res
  );
});

app.post('/api/ai/schedule-analysis', (req, res) => {
  callAiOrMock(
    () => hasAiKey,
    async () => {
      const { imageBase64 } = req.body;
      const prompt = `Analyze this class schedule image. Extract every class as JSON with this exact shape:
{"classes":[{"subject":"...","teacher":"... or null","room":"... or null","day":"MONDAY|TUESDAY|...","startTime":"HH:MM","endTime":"HH:MM"}],"uncertainFields":["field names you could not read clearly"]}
Return ONLY valid JSON, no markdown.`;

      const content = await chatCompletion(
        [
          { role: 'system', content: 'You extract structured schedule data from images. Respond with JSON only.' },
          {
            role: 'user',
            content: [
              { type: 'text', text: prompt },
              { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
            ],
          },
        ],
        { temperature: 0.2, maxTokens: 4096 }
      );

      const parsed = extractJson(content);
      return {
        classes: parsed.classes || [],
        uncertainFields: parsed.uncertainFields || [],
      };
    },
    () => mock.scheduleAnalysis(),
    res
  );
});

app.post('/api/ai/summarize', (req, res) => {
  callAiOrMock(
    () => hasAiKey,
    async () => {
      const text = req.body.text || '';
      const result = await chatCompletion([
        { role: 'system', content: 'Summarize study notes concisely. Preserve key facts and terminology. Use plain text, no bullet markdown unless helpful.' },
        { role: 'user', content: `Summarize these notes:\n\n${text}` },
      ], { temperature: 0.3 });
      return { result };
    },
    () => mock.summarize(req.body),
    res
  );
});

app.post('/api/ai/flashcards', (req, res) => {
  callAiOrMock(
    () => hasAiKey,
    async () => {
      const text = req.body.text || '';
      const content = await chatCompletion([
        { role: 'system', content: 'Generate study flashcards from notes. Respond with JSON only.' },
        {
          role: 'user',
          content: `Create 5-8 flashcards from this material. JSON shape:
{"cards":[{"question":"...","answer":"...","topic":"optional topic"}]}
Notes:\n${text}`,
        },
      ], { temperature: 0.5 });
      const parsed = extractJson(content);
      return { cards: parsed.cards || [] };
    },
    () => mock.flashcards(),
    res
  );
});

app.post('/api/ai/quiz', (req, res) => {
  callAiOrMock(
    () => hasAiKey,
    async () => {
      const text = req.body.text || '';
      const content = await chatCompletion([
        { role: 'system', content: 'Generate quizzes from study material. Respond with JSON only.' },
        {
          role: 'user',
          content: `Create a quiz (5-8 questions) from this material. JSON shape:
{"title":"Quiz title","questions":[{"type":"MULTIPLE_CHOICE|TRUE_FALSE","question":"...","options":["..."],"correctAnswer":"..."}]}
Use MULTIPLE_CHOICE with 3-4 options, or TRUE_FALSE with options ["True","False"].
Notes:\n${text}`,
        },
      ], { temperature: 0.5 });
      const parsed = extractJson(content);
      return { title: parsed.title || 'Generated Quiz', questions: parsed.questions || [] };
    },
    () => mock.quiz(),
    res
  );
});

app.post('/api/ai/study-plan', (req, res) => {
  callAiOrMock(
    () => hasAiKey,
    async () => {
      const { examDate, availableHours, subjects, topics } = req.body;
      const exam = examDate ? new Date(examDate).toISOString().slice(0, 10) : 'unknown';
      const content = await chatCompletion([
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
      ], { temperature: 0.4 });
      const parsed = extractJson(content);
      return { title: parsed.title || 'Study Plan', items: parsed.items || [] };
    },
    () => mock.studyPlan(req.body),
    res
  );
});

app.get('/health', (_, res) =>
  res.json({ status: 'ok', aiConfigured: hasAiKey, model: AI_MODEL })
);

app.listen(PORT, '0.0.0.0', () => {
  console.log(`StudentAI backend listening on :${PORT}`);
  console.log(hasAiKey ? `AI provider: ${AI_BASE_URL} (model: ${AI_MODEL})` : 'AI provider: mock mode (set AI_API_KEY in backend/.env)');
});
