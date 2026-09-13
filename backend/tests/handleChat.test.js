/**
 * handleChat citation integration: local + web numbering and DTOs.
 * Run: npm test
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const { handleChat, isLearningTopic } = require('../server');

const WEB = {
  title: 'Photosynthesis — Biology Textbook',
  url: 'https://example.com/photosynthesis',
  content: 'Photosynthesis converts light energy into chemical energy.',
};

function stubProvider(reply) {
  return {
    resolveChatModel: () => 'auto',
    requestHasVisionContent: () => false,
    chatCompletion: async () => ({ reply, reasoning: null, model: 'auto' }),
  };
}

function stubSearch() {
  return {
    isConfigured: true,
    searchAuto: async () => [WEB],
    search: async () => [WEB],
  };
}

function baseBody(overrides = {}) {
  return {
    message: 'Explain photosynthesis simply',
    sources: [{ id: 's1', label: 'Biology Chapter 4', text: 'Chloroplasts absorb light.' }],
    ...overrides,
  };
}

describe('handleChat citations', () => {
  it('numbers local [1] then web [2] with prefixed IDs and web metadata', async () => {
    const result = await handleChat({
      body: baseBody(),
      provider: stubProvider('Light reactions [1] happen first; see the overview [2].'),
      webSearch: stubSearch(),
      maxTokens: 512,
    });
    assert.ok(result.reply.includes('[1]'));
    assert.deepEqual(result.citedChunkIds[0], 'local:s1');
    assert.match(result.citedChunkIds[1], /^web:[0-9a-f]{8}:0$/);
    assert.equal(result.citedWebResults.length, 1);
    assert.equal(result.citedWebResults[0].url, WEB.url);
    assert.equal(result.citedWebResults[0].title, WEB.title);
  });

  it('explicit /search results take priority in web numbering', async () => {
    const explicit = { title: 'Explicit Hit', url: 'https://example.com/explicit', content: 'x' };
    const result = await handleChat({
      body: baseBody({ message: '/search quantum dots' }),
      provider: stubProvider('Per [2], quantum dots matter.'),
      webSearch: {
        isConfigured: true,
        searchAuto: async () => { throw new Error('should not auto-search on /search'); },
        search: async () => [explicit],
      },
      maxTokens: 512,
    });
    assert.equal(result.citedWebResults.length, 1);
    assert.equal(result.citedWebResults[0].url, explicit.url);
  });

  it('pads local-only citations with unused web results up to available sources', async () => {
    const result = await handleChat({
      body: baseBody(),
      provider: stubProvider('Only the chapter matters [1].'),
      webSearch: stubSearch(),
      maxTokens: 512,
    });
    // 1 local + 1 web available: padding fills the unused web result.
    assert.deepEqual(result.citedChunkIds.length, 2);
    assert.deepEqual(result.citedChunkIds[0], 'local:s1');
    assert.match(result.citedChunkIds[1], /^web:[0-9a-f]{8}:0$/);
    assert.equal(result.citedWebResults.length, 1);
    assert.equal(result.citedWebResults[0].url, WEB.url);
  });

  it('topic message + reply with zero markers still returns 5 citedChunkIds/citedWebResults', async () => {
    const webs = [0, 1, 2, 3, 4].map((i) => ({
      title: `Web source ${i + 1}`,
      url: `https://example.com/topic-${i}`,
      content: `Supporting fact ${i + 1} about photosynthesis.`,
    }));
    const result = await handleChat({
      body: baseBody({ message: 'Explain photosynthesis simply' }),
      provider: stubProvider('Photosynthesis is how plants turn light into food.'),
      webSearch: {
        isConfigured: true,
        searchAuto: async () => webs,
        search: async () => webs,
      },
      maxTokens: 512,
    });
    assert.equal(result.citedChunkIds.length, 5);
    assert.equal(result.citedWebResults.length, 5);
    assert.deepEqual(
      result.citedWebResults.map((r) => r.url),
      webs.map((w) => w.url)
    );
  });

  it('pads with unused local sources when web results run out', async () => {
    const result = await handleChat({
      body: baseBody({
        message: 'Explain photosynthesis simply',
        sources: [
          { id: 's1', label: 'Biology Chapter 4', text: 'Chloroplasts absorb light.' },
          { id: 's2', label: 'Biology Chapter 5', text: 'The Calvin cycle fixes carbon.' },
          { id: 's3', label: 'Biology Chapter 6', text: 'Stomata regulate gas exchange.' },
        ],
      }),
      provider: stubProvider('A claim with no markers at all.'),
      webSearch: {
        isConfigured: true,
        searchAuto: async () => [WEB],
        search: async () => [WEB],
      },
      maxTokens: 512,
    });
    // 3 local + 1 web = 4 total: web first, then locals, exhausted at 4.
    assert.equal(result.citedChunkIds.length, 4);
    assert.match(result.citedChunkIds[0], /^web:[0-9a-f]{8}:0$/);
    assert.deepEqual(result.citedChunkIds.slice(1), ['local:s1', 'local:s2', 'local:s3']);
    assert.equal(result.citedWebResults.length, 1);
  });
});

describe('isLearningTopic', () => {
  it('returns false for short messages, greetings, and commands', () => {
    for (const msg of [
      'hi',
      'hii!',
      'hello',
      'hey',
      'thanks',
      'thank you',
      'ok',
      'okay',
      'yes',
      'no',
      'bye',
      'bye!',
      'short msg',
      'What is it',
      '/search quantum dots',
      '/summarize my notes please',
    ]) {
      assert.equal(isLearningTopic(msg), false, `expected false for: ${JSON.stringify(msg)}`);
    }
  });

  it('returns true for genuine learning topics', () => {
    for (const msg of [
      'Explain photosynthesis simply',
      'What is the capital of France?',
      'How do I solve quadratic equations step by step?',
      'quantum dots explained',
      'thank you, now explain mitosis in detail',
    ]) {
      assert.equal(isLearningTopic(msg), true, `expected true for: ${JSON.stringify(msg)}`);
    }
  });
});
