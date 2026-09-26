/**
 * Quiz generation tests.
 * Verifies system prompt usage, question count calibration, difficulty target,
 * and QuizValidator contract adherence.
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const { handleQuiz } = require('../server');
const { QuizValidator } = require('../validation/AiResponseValidator');
const { QUIZ_SYSTEM_PROMPT } = require('../prompts/quiz-system-prompt');
const { createAiProvider } = require('../ai/AiProvider');

function providerReturning(replies) {
  const calls = [];
  const ai = createAiProvider({ apiKey: 'test-key', baseUrl: 'http://127.0.0.1:9' });
  let n = 0;
  ai.chatCompletionText = async (messages, options) => {
    calls.push({ messages, options });
    const reply = Array.isArray(replies) ? replies[Math.min(n, replies.length - 1)] : replies;
    n += 1;
    return reply;
  };
  return { ai, calls };
}

function quizJson(count = 5) {
  return JSON.stringify({
    title: 'Biology Practice Quiz',
    questions: Array.from({ length: count }, (_, i) => ({
      type: 'MULTIPLE_CHOICE',
      question: `What is the primary function of organelle ${i + 1}?`,
      options: ['ATP production', 'Protein synthesis', 'Lipid transport', 'DNA replication'],
      correctAnswer: 'ATP production',
    })),
  });
}

describe('handleQuiz — system prompt and question options', () => {
  it('uses QUIZ_SYSTEM_PROMPT and defaults to 5 questions', async () => {
    const text = 'Mitochondria generate ATP. Ribosomes synthesize proteins.';
    const { ai, calls } = providerReturning(quizJson(5));
    const result = await handleQuiz({ body: { text }, provider: ai, maxTokens: 2048 });

    assert.equal(calls.length, 1);
    assert.equal(calls[0].messages[0].content, QUIZ_SYSTEM_PROMPT);
    assert.ok(calls[0].messages[1].content.includes('5 questions'));
    assert.equal(result.questions.length, 5);

    const validator = new QuizValidator();
    const validated = validator.validate(result);
    assert.equal(validated.valid, true);
  });

  it('respects requested count and difficulty target', async () => {
    const text = 'Quantum mechanics notes: wave-particle duality, uncertainty principle.';
    const { ai, calls } = providerReturning(quizJson(10));
    const result = await handleQuiz({
      body: { text, count: 10, difficulty: 'HARD' },
      provider: ai,
      maxTokens: 2048,
    });

    assert.equal(calls.length, 1);
    assert.ok(calls[0].messages[1].content.includes('10 questions'));
    assert.ok(calls[0].messages[1].content.includes('HARD'));
    assert.equal(result.questions.length, 10);
  });

  it('randomizes multiple-choice options so the correct answer is not stuck in position A', async () => {
    const text = 'Cell biology: mitochondria produce ATP energy.';
    const { ai } = providerReturning(quizJson(20));
    const result = await handleQuiz({ body: { text, count: 20 }, provider: ai, maxTokens: 4096 });

    const correctIndices = result.questions.map((q) => q.options.indexOf(q.correctAnswer));
    assert.ok(correctIndices.every((idx) => idx >= 0), 'Every question must retain its correct answer');
    const allAtZero = correctIndices.every((idx) => idx === 0);
    assert.equal(allAtZero, false, 'Correct answer must be randomized across options and not always at index 0');
  });
});
