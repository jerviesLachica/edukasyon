/**
 * Tests for the Gemini embedding client (JEVI source citations).
 * Uses mocked fetch; verifies batch shape, task types, dimensionality,
 * error surfacing, and the no-key guard.
 */

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { createEmbeddingClient } = require('../ai/Embeddings');

describe('Embeddings client', () => {
  let realFetch;
  let calls;

  beforeEach(() => {
    realFetch = globalThis.fetch;
    calls = [];
  });

  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  function client() {
    return createEmbeddingClient({
      apiKey: 'test-key',
      baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
      model: 'gemini-embedding-001',
      dims: 768,
    });
  }

  it('single text uses :embedContent with RETRIEVAL_DOCUMENT by default', async () => {
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return { ok: true, status: 200, json: async () => ({ embedding: { values: [0.1, 0.2] } }) };
    };
    const [vec] = await client().embedDocuments(['hello']);
    assert.deepStrictEqual(vec, [0.1, 0.2]);
    assert.ok(String(calls[0].url).includes(':embedContent'), 'uses single endpoint');
    assert.ok(!String(calls[0].url).includes('batch'), 'not the batch endpoint');
    assert.strictEqual(calls[0].body.taskType, 'RETRIEVAL_DOCUMENT');
    assert.strictEqual(calls[0].body.outputDimensionality, 768);
  });

  it('multiple texts use :batchEmbedContents in ONE http call', async () => {
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return {
        ok: true,
        status: 200,
        json: async () => ({ embeddings: [{ values: [1] }, { values: [2] }, { values: [3] }] }),
      };
    };
    const vecs = await client().embedDocuments(['a', 'b', 'c']);
    assert.strictEqual(calls.length, 1, 'one http call for the batch');
    assert.ok(String(calls[0].url).includes(':batchEmbedContents'));
    assert.strictEqual(calls[0].body.requests.length, 3);
    assert.deepStrictEqual(vecs, [[1], [2], [3]]);
  });

  it('embedQuery uses RETRIEVAL_QUERY task type', async () => {
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return { ok: true, status: 200, json: async () => ({ embedding: { values: [9] } }) };
    };
    const vec = await client().embedQuery('what is photosynthesis?');
    assert.deepStrictEqual(vec, [9]);
    assert.strictEqual(calls[0].body.taskType, 'RETRIEVAL_QUERY');
  });

  it('throws a clear error without an API key', async () => {
    const noKey = createEmbeddingClient({ apiKey: '' });
    delete process.env.GEMINI_EMBEDDING_API_KEY;
    assert.strictEqual(noKey.hasKey, false);
    await assert.rejects(() => noKey.embedQuery('hi'), /GEMINI_EMBEDDING_API_KEY/);
  });

  it('surfaces upstream errors with status', async () => {
    globalThis.fetch = async () => ({ ok: false, status: 429, text: async () => 'quota' });
    await assert.rejects(() => client().embedQuery('hi'), /429/);
  });

  it('rejects mismatched batch responses', async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ embeddings: [{ values: [1] }] }),
    });
    await assert.rejects(() => client().embedDocuments(['a', 'b']), /mismatched/);
  });
});
