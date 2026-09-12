/**
 * Server-side web search integration.
 *
 * Round-robin providers:
 *   1. Tavily API (POST https://api.tavily.com/search)
 *   2. LangSearch API (POST https://api.langsearch.com/v1/web-search)
 *   3. DuckDuckGo HTML fallback (free, no key)
 *
 * Two modes:
 *   - Explicit `/search` command (parseWebSearchCommand): user-requested, full MAX_RESULTS.
 *   - Automatic research (searchAuto): every chat message triggers a lightweight
 *     query derived from the user's message, capped at AUTO_MAX_RESULTS.
 * Results are returned as untrusted reference material for the AI provider.
 */

const MAX_RESULTS = 5;
const AUTO_MAX_RESULTS = 5;
const MAX_RESULT_CHARS = 1_200;

function parseWebSearchCommand(message) {
  const text = String(message || '').trim();
  const match = text.match(/^\/search(?:\s+(.+))?$/is);
  if (!match) return { requested: false, query: text };
  const query = (match[1] || '').trim();
  if (!query) throw new Error('WEB_SEARCH_QUERY_REQUIRED');
  return { requested: true, query };
}

// ── DuckDuckGo free fallback ──────────────────────────────────────────────────
async function searchFreeFallback(query, maxResults, fetchImpl = fetch) {
  try {
    const url = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`;
    const res = await fetchImpl(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      },
    });
    if (!res.ok) return [];
    const html = await res.text();
    const results = [];
    const regex = /<a class="result__url" href="([^"]+)"[^>]*>.*?<\/a>.*?<a class="result__snippet"[^>]*>(.*?)<\/a>/gs;
    let m;
    while ((m = regex.exec(html)) !== null && results.length < maxResults) {
      const href = m[1].trim();
      const snippet = m[2].replace(/<[^>]+>/g, '').trim();
      if (href && !href.includes('duckduckgo.com')) {
        results.push({
          title: snippet.slice(0, 40) + '...',
          url: href.startsWith('http') ? href : `https://${href}`,
          content: snippet,
        });
      }
    }
    return results;
  } catch {
    return [];
  }
}

// ── Tavily provider ───────────────────────────────────────────────────────────
async function searchTavily(query, apiKey, maxResults, signal, fetchImpl = fetch) {
  const response = await fetchImpl('https://api.tavily.com/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      api_key: apiKey,
      query,
      search_depth: 'basic',
      max_results: maxResults,
      include_answer: false,
      include_raw_content: false,
    }),
    signal,
  });
  if (!response.ok) throw new Error(`Tavily search failed (${response.status})`);
  const data = await response.json();
  return Array.isArray(data.results) ? data.results.slice(0, maxResults) : [];
}

// ── LangSearch provider ───────────────────────────────────────────────────────
async function searchLangSearch(query, apiKey, maxResults, signal, fetchImpl = fetch) {
  const response = await fetchImpl('https://api.langsearch.com/v1/web-search', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      query,
      count: Math.min(maxResults, 10),
      summary: false,
    }),
    signal,
  });
  if (!response.ok) throw new Error(`LangSearch failed (${response.status})`);
  const data = await response.json();
  // LangSearch wraps results in data.webPages.value[]
  const pages = data?.data?.webPages?.value || data?.webPages?.value || [];
  return pages.slice(0, maxResults).map(page => ({
    title: page.name || 'Untitled',
    url: page.url || '',
    content: page.snippet || page.summary || '',
  }));
}

// ── Round-robin orchestrator ──────────────────────────────────────────────────
function createWebSearchService({
  tavilyKey = process.env.TAVILY_API_KEY,
  langSearchKey = process.env.LANGSEARCH_API_KEY,
  fetchImpl = fetch,
} = {}) {
  // Build ordered provider list: configured providers first, then free fallback
  const providers = [];
  if (tavilyKey) providers.push({ name: 'tavily', fn: (q, n, s) => searchTavily(q, tavilyKey, n, s, fetchImpl) });
  if (langSearchKey) providers.push({ name: 'langsearch', fn: (q, n, s) => searchLangSearch(q, langSearchKey, n, s, fetchImpl) });
  providers.push({ name: 'ddg', fn: (q, n, s) => searchFreeFallback(q, n, fetchImpl) });

  const isConfigured = providers.length > 0; // always true (DDG fallback)

  let providerIndex = 0;

  async function searchWithRoundRobin(query, maxResults, signal) {
    const startIdx = providerIndex;
    for (let i = 0; i < providers.length; i++) {
      const idx = (startIdx + i) % providers.length;
      const provider = providers[idx];
      try {
        const results = await provider.fn(query, maxResults, signal);
        if (results.length > 0) {
          // Rotate to next provider for next call
          providerIndex = (idx + 1) % providers.length;
          return results;
        }
      } catch {
        // Provider failed, try next
        continue;
      }
    }
    return [];
  }

  async function search(query, signal) {
    return searchWithRoundRobin(query, MAX_RESULTS, signal);
  }

  async function searchAuto(query, signal) {
    return searchWithRoundRobin(query, AUTO_MAX_RESULTS, signal);
  }

  function formatForPrompt(results) {
    if (!results.length) return '[No web search results were returned.]';
    return results.map((result, index) => {
      const title = String(result.title || 'Untitled source').trim();
      const url = String(result.url || '').trim();
      const content = String(result.content || '').trim().slice(0, MAX_RESULT_CHARS);
      return `[${index + 1}] ${title}\nURL: ${url}\n${content}`;
    }).join('\n\n');
  }

  return { isConfigured, search, searchAuto, formatForPrompt };
}

module.exports = { createWebSearchService, parseWebSearchCommand };
