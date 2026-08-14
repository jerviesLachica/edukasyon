/**
 * Server-side Tavily web search integration for explicit `/search` chat requests.
 * Results are returned as untrusted reference material for the AI provider.
 */

const MAX_RESULTS = 5;
const MAX_RESULT_CHARS = 1_200;

function parseWebSearchCommand(message) {
  const text = String(message || '').trim();
  const match = text.match(/^\/search(?:\s+(.+))?$/is);
  if (!match) return { requested: false, query: text };
  const query = (match[1] || '').trim();
  if (!query) throw new Error('WEB_SEARCH_QUERY_REQUIRED');
  return { requested: true, query };
}

function createWebSearchService({ apiKey = process.env.TAVILY_API_KEY, fetchImpl = fetch } = {}) {
  const isConfigured = Boolean(apiKey);

  async function search(query, signal) {
    const response = await fetchImpl('https://api.tavily.com/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        api_key: apiKey,
        query,
        search_depth: 'basic',
        max_results: MAX_RESULTS,
        include_answer: false,
        include_raw_content: false,
      }),
      signal,
    });
    if (!response.ok) throw new Error(`Web search failed (${response.status})`);
    const data = await response.json();
    return Array.isArray(data.results) ? data.results.slice(0, MAX_RESULTS) : [];
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

  return { isConfigured, search, formatForPrompt };
}

module.exports = { createWebSearchService, parseWebSearchCommand };
