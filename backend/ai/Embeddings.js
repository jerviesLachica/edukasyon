/**
 * Gemini embedding client for JEVI source citations.
 *
 * Retrieval lives behind this client: documents are embedded once with
 * RETRIEVAL_DOCUMENT, queries with RETRIEVAL_QUERY, and cosine similarity
 * (computed by the caller, e.g. on-device over Room vectors) ranks chunks.
 *
 * Config is env-driven; no key = explicit throw (callers decide fallback).
 * Free tier (AI Studio, no card): ~1,500 req/day. Batch to stay under it.
 */

const DEFAULT_MODEL = 'gemini-embedding-001';
const DEFAULT_DIMS = 768;

function createEmbeddingClient(config = {}) {
  const apiKey = config.apiKey || process.env.GEMINI_EMBEDDING_API_KEY || '';
  const baseUrl = (
    config.baseUrl ||
    process.env.GEMINI_EMBEDDING_BASE_URL ||
    'https://generativelanguage.googleapis.com/v1beta'
  ).replace(/\/$/, '');
  const model = config.model || process.env.GEMINI_EMBEDDING_MODEL || DEFAULT_MODEL;
  const dims = Number(config.dims || process.env.GEMINI_EMBEDDING_DIMS || DEFAULT_DIMS);

  function assertKey() {
    if (!apiKey) throw new Error('Embeddings not configured (set GEMINI_EMBEDDING_API_KEY)');
  }

  async function post(path, body, signal) {
    assertKey();
    const res = await fetch(`${baseUrl}${path}?key=${encodeURIComponent(apiKey)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new Error(`Embedding API error ${res.status}: ${String(text).slice(0, 200)}`);
    }
    return res.json();
  }

  function singleBody(text, taskType) {
    const body = { model: `models/${model}`, content: { parts: [{ text }] } };
    if (taskType) body.taskType = taskType;
    if (dims) body.outputDimensionality = dims;
    return body;
  }

  async function embedOne(text, taskType, signal) {
    const data = await post(`/models/${model}:embedContent`, singleBody(text, taskType), signal);
    const values = data?.embedding?.values;
    if (!Array.isArray(values) || !values.length) throw new Error('Embedding API returned no values');
    return values;
  }

  /**
   * Embed 1..N texts. Single text uses :embedContent; N use :batchEmbedContents
   * (one HTTP call, one quota hit per request object).
   */
  async function embedTexts(texts, { taskType = 'RETRIEVAL_DOCUMENT', signal } = {}) {
    if (!Array.isArray(texts) || texts.length === 0) return [];
    assertKey();
    if (texts.length === 1) return [await embedOne(texts[0], taskType, signal)];
    const data = await post(
      `/models/${model}:batchEmbedContents`,
      {
        requests: texts.map((text) => ({
          model: `models/${model}`,
          content: { parts: [{ text }] },
          ...(taskType ? { taskType } : {}),
          ...(dims ? { outputDimensionality: dims } : {}),
        })),
      },
      signal,
    );
    const out = data?.embeddings;
    if (!Array.isArray(out) || out.length !== texts.length) {
      throw new Error('Embedding API returned mismatched batch values');
    }
    return out.map((e, i) => {
      if (!Array.isArray(e?.values) || !e.values.length) {
        throw new Error(`Embedding API returned no values for batch index ${i}`);
      }
      return e.values;
    });
  }

  const embedDocuments = (texts, opts = {}) =>
    embedTexts(texts, { ...opts, taskType: 'RETRIEVAL_DOCUMENT' });

  async function embedQuery(text, opts = {}) {
    const [vec] = await embedTexts([text], { ...opts, taskType: 'RETRIEVAL_QUERY' });
    return vec;
  }

  return {
    hasKey: Boolean(apiKey),
    model,
    dims,
    embedTexts,
    embedDocuments,
    embedQuery,
  };
}

module.exports = { createEmbeddingClient, DEFAULT_MODEL, DEFAULT_DIMS };
