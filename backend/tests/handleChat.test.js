/**
 * handleChat citation integration: local + web numbering and DTOs.
 * Run: npm test
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const { handleChat } = require('../server');

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

  it('omits citedWebResults entries when reply cites local only', async () => {
    const result = await handleChat({
      body: baseBody(),
      provider: stubProvider('Only the chapter matters [1].'),
      webSearch: stubSearch(),
      maxTokens: 512,
    });
    assert.deepEqual(result.citedChunkIds, ['local:s1']);
    assert.deepEqual(result.citedWebResults, []);
  });
});
