/**
 * Flashcard pipeline: single whole-document call for material ≤ 14k chars
 * (real system prompt, raised output floor), chunked parallel fallback above
 * it, and FlashcardValidator dedup of twin cards.
 * Run: npm test
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const { handleFlashcards } = require('../server');
const { FlashcardValidator } = require('../validation/AiResponseValidator');
const {
  FLASHCARDS_SYSTEM_PROMPT,
  FLASHCARDS_JSON_SHAPE,
} = require('../prompts/flashcards-system-prompt');
const { createAiProvider } = require('../ai/AiProvider');

/** Real provider (extractJson/resolveTextModel fidelity) with the HTTP leg stubbed. */
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

function cardsJson(count, start = 1) {
  return JSON.stringify({
    cards: Array.from({ length: count }, (_, i) => ({
      question: `What defines concept ${start + i}?`,
      answer: `Concept ${start + i} is defined in the notes.`,
      topic: `Topic-${Math.ceil((start + i) / 5)}`,
    })),
  });
}

const ROUTE_MAX_TOKENS = 2048; // safety/SafetyPolicy.js flashcards.maxOutputTokens

describe('handleFlashcards — single whole-document call', () => {
  it('makes exactly one provider call for short material with the full text', async () => {
    const text = 'Cell biology notes. Mitochondria produce ATP via oxidative phosphorylation. ' +
      'The nucleus stores DNA. Ribosomes translate mRNA into proteins.';
    const { ai, calls } = providerReturning(cardsJson(10));
    const result = await handleFlashcards({ body: { text }, provider: ai, maxTokens: ROUTE_MAX_TOKENS });

    assert.equal(calls.length, 1, 'expected a single whole-document call');
    assert.ok(calls[0].messages[1].content.includes('oxidative phosphorylation'),
      'the user message must carry the whole document, not a section');
    assert.equal(result.cards.length, 10);
    // Client contract unchanged: {cards:[{question,answer,topic?}]}
    assert.deepEqual(Object.keys(result.cards[0]).sort(), ['answer', 'question', 'topic']);
  });

  it('uses the dedicated flashcards system prompt (not the one-liner)', async () => {
    const { ai, calls } = providerReturning(cardsJson(3));
    await handleFlashcards({ body: { text: 'Photosynthesis converts light into chemical energy.' }, provider: ai, maxTokens: ROUTE_MAX_TOKENS });
    assert.equal(calls[0].messages[0].content, FLASHCARDS_SYSTEM_PROMPT);
    assert.match(FLASHCARDS_SYSTEM_PROMPT, /atomic/i);
    assert.match(FLASHCARDS_SYSTEM_PROMPT, /duplicate/i);
  });

  it('raises output budget to >= 3000 tokens only on the single-call branch', async () => {
    const { ai, calls } = providerReturning(cardsJson(5));
    await handleFlashcards({ body: { text: 'Short notes about the Krebs cycle.' }, provider: ai, maxTokens: ROUTE_MAX_TOKENS });
    assert.ok(calls[0].options.maxTokens >= 3000,
      `whole-doc pass emits 10-15 JSON cards; ${ROUTE_MAX_TOKENS} truncates mid-JSON`);
  });

  it('parses fenced ```json replies via extractJson', async () => {
    const fenced = '```json\n' + cardsJson(4) + '\n```';
    const { ai } = providerReturning(fenced);
    const result = await handleFlashcards({ body: { text: 'Some notes on plate tectonics.' }, provider: ai, maxTokens: ROUTE_MAX_TOKENS });
    assert.equal(result.cards.length, 4);
  });

  it('caps results at FLASHCARDS_MAX_CARDS (60)', async () => {
    const { ai } = providerReturning(cardsJson(70));
    const result = await handleFlashcards({ body: { text: 'A long list of vocabulary terms.' }, provider: ai, maxTokens: ROUTE_MAX_TOKENS });
    assert.equal(result.cards.length, 60);
  });
});

describe('handleFlashcards — chunked fallback above the single-call threshold', () => {
  it('splits material over 14k chars into multiple calls and keeps the route token budget', async () => {
    const text = 'paragraph content here. '.repeat(1000); // 22_000 chars, single run-on paragraph → hard-split at 10k
    assert.ok(text.length > 14_000);
    const { ai, calls } = providerReturning([cardsJson(3, 1), cardsJson(3, 11), cardsJson(3, 21)]);
    const result = await handleFlashcards({ body: { text }, provider: ai, maxTokens: ROUTE_MAX_TOKENS });

    assert.ok(calls.length >= 2, `expected chunked calls, got ${calls.length}`);
    for (const c of calls) {
      assert.equal(c.options.maxTokens, ROUTE_MAX_TOKENS, 'fallback path must not inflate tokens');
      assert.equal(c.messages[0].content, FLASHCARDS_SYSTEM_PROMPT, 'real prompt on the fallback too');
      assert.match(c.messages[1].content, /Part \d+ of \d+/);
    }
    assert.equal(result.cards.length, calls.length * 3);
  });
});

describe('FlashcardValidator — dedup and shape', () => {
  const v = new FlashcardValidator();

  it('drops twin questions differing only in case/whitespace, keeps first occurrence', () => {
    const out = v.validate({
      cards: [
        { question: 'What is 2+2?', answer: '4', topic: 'Math' },
        { question: '  what is 2+2? ', answer: '4 (duplicate pass)' },
        { question: 'CAPITAL OF PERU?', answer: 'Lima' },
        { question: 'capital of peru?', answer: 'Buenos Aires (twin)' },
      ],
    });
    assert.equal(out.valid, true);
    assert.equal(out.data.cards.length, 2);
    assert.equal(out.data.cards[0].answer, '4');
    assert.equal(out.data.cards[1].answer, 'Lima');
  });

  it('keeps distinct cards and filters incomplete ones', () => {
    const out = v.validate({
      cards: [
        { question: 'Q one?', answer: 'A one' },
        { question: '   ', answer: 'blank question' },
        { question: 'No answer?', answer: '' },
        null,
        { question: 'Q two?', answer: 'A two', topic: 'X' },
      ],
    });
    assert.equal(out.valid, true);
    assert.deepEqual(out.data.cards.map((c) => c.question), ['Q one?', 'Q two?']);
  });

  it('rejects missing cards array and an all-invalid card set', () => {
    assert.equal(v.validate({}).valid, false);
    assert.equal(v.validate({ cards: [{ question: 'x', answer: '  ' }] }).valid, false);
  });

  it('JSON shape constant matches the client contract', () => {
    const parsed = JSON.parse(FLASHCARDS_JSON_SHAPE);
    assert.ok(Array.isArray(parsed.cards));
    assert.deepEqual(Object.keys(parsed.cards[0]).sort(), ['answer', 'question', 'topic']);
  });
});
