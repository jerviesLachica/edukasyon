/**
 * Safe client-facing error messages — never leak secrets, stack traces, or keys.
 */

const SECRET_SUBSTRINGS = ['sk-', 'AI_API_KEY', 'Bearer ', 'api.hcnsec', 'Authorization'];

function containsSecret(text) {
  const s = String(text || '');
  return SECRET_SUBSTRINGS.some((sub) => s.includes(sub));
}

function sanitizeErrorForClient(err) {
  const raw = String(err?.message || err || 'Unknown error');

  if (/AI provider not configured/i.test(raw)) {
    return { status: 503, code: 'AI_NOT_CONFIGURED', message: 'AI service is not configured.' };
  }
  if (/WEB_SEARCH_NOT_CONFIGURED/i.test(raw)) {
    return {
      status: 503,
      code: 'WEB_SEARCH_NOT_CONFIGURED',
      message: 'Web search is not configured. Ask the app administrator to set TAVILY_API_KEY.',
    };
  }
  if (/WEB_SEARCH_QUERY_REQUIRED/i.test(raw)) {
    return {
      status: 400,
      code: 'WEB_SEARCH_QUERY_REQUIRED',
      message: 'Add a search query after /search.',
    };
  }
  if (/abort/i.test(raw) || /timeout/i.test(raw)) {
    return { status: 504, code: 'REQUEST_TIMEOUT', message: 'AI request timed out. Please try again.' };
  }
  if (/429|rate limit/i.test(raw)) {
    return { status: 503, code: 'PROVIDER_BUSY', message: 'AI service is busy. Try again in a moment.' };
  }
  if (/502|503|504|NO_UPSTREAM/i.test(raw)) {
    return { status: 502, code: 'PROVIDER_ERROR', message: 'AI provider is temporarily unavailable.' };
  }
  if (/Output blocked|Invalid AI output|blocked for safety/i.test(raw)) {
    return { status: 422, code: 'OUTPUT_BLOCKED', message: 'The AI response could not be delivered safely.' };
  }
  if (/Could not extract assignment details from the image/i.test(raw)) {
    return { status: 422, code: 'INVALID_AI_OUTPUT', message: raw };
  }
  if (/Missing assignment|No valid subtasks|Invalid assignment breakdown/i.test(raw)) {
    return {
      status: 422,
      code: 'INVALID_AI_OUTPUT',
      message: 'Could not parse assignment details. Try a clearer photo or paste the text.',
    };
  }
  if (/JSON|parse/i.test(raw)) {
    return { status: 422, code: 'INVALID_AI_OUTPUT', message: 'AI returned an invalid response. Please try again.' };
  }

  // Never expose raw provider errors or secrets
  if (containsSecret(raw) || /AI API error/i.test(raw)) {
    return { status: 502, code: 'PROVIDER_ERROR', message: 'AI provider request failed.' };
  }

  return { status: 502, code: 'AI_ERROR', message: 'AI request failed. Please try again.' };
}

module.exports = { sanitizeErrorForClient, containsSecret };
