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
  const isGoogle = (config.baseUrl && config.baseUrl.includes('googleapis')) ||
    Boolean(process.env.GEMINI_EMBEDDING_API_KEY) ||
    (config.model && config.model.includes('gemini'));

  const isOpenAi = !isGoogle && Boolean(config.apiKey || process.env.AI_API_KEY);

  const apiKey = config.apiKey || (isGoogle ? process.env.GEMINI_EMBEDDING_API_KEY : (process.env.AI_API_KEY || ''));
  const baseUrl = (
    config.baseUrl ||
    (isGoogle
      ? (process.env.GEMINI_EMBEDDING_BASE_URL || 'https://generativelanguage.googleapis.com/v1beta')
      : (process.env.AI_BASE_URL || 'https://api.hcnsec.cn/v1'))
  ).replace(/\/$/, '');
  const model = config.model || (isGoogle ? (process.env.GEMINI_EMBEDDING_MODEL || DEFAULT_MODEL) : (process.env.EMBEDDING_MODEL || 'Qwen3-Embedding-8B'));
  const dims = Number(config.dims || (isGoogle ? (process.env.GEMINI_EMBEDDING_DIMS || DEFAULT_DIMS) : 2048));

  function assertKey() {
    if (!apiKey) throw new Error('Embeddings not configured (set GEMINI_EMBEDDING_API_KEY or AI_API_KEY)');
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
   * Embed 1..N texts. Supports both OpenAI /v1/embeddings and Gemini batchEmbedContents.
   */
  async function embedTexts(texts, { taskType = 'RETRIEVAL_DOCUMENT', signal } = {}) {
    if (!Array.isArray(texts) || texts.length === 0) return [];
    assertKey();

    if (isOpenAi) {
      const res = await fetch(`${baseUrl}/embeddings`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${apiKey}`,
        },
        body: JSON.stringify({
          model,
          input: texts,
        }),
        signal,
      });
      if (!res.ok) {
        const text = await res.text().catch(() => '');
        throw new Error(`Embedding API error ${res.status}: ${String(text).slice(0, 200)}`);
      }
      const json = await res.json();
      return (json.data || []).map((d) => d.embedding);
    }
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
