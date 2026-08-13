/**
 * Classifies potentially sensitive data in user input (for logging decisions, not blocking alone).
 */

const PII_PATTERNS = [
  { type: 'email', pattern: /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g },
  { type: 'phone', pattern: /\b(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b/g },
  { type: 'credit_card', pattern: /\b(?:\d{4}[-\s]?){3}\d{4}\b/g },
];

class DataClassifier {
  /**
   * @returns {{ hasPii: boolean, types: string[], count: number }}
   */
  classify(text) {
    const input = String(text || '');
    const types = [];
    let count = 0;

    for (const { type, pattern } of PII_PATTERNS) {
      const matches = input.match(pattern);
      if (matches && matches.length > 0) {
        types.push(type);
        count += matches.length;
      }
    }

    return { hasPii: types.length > 0, types, count };
  }

  /** Returns a safe preview for logs (truncated, PII masked). */
  safePreview(text, maxLen = 80) {
    let preview = String(text || '').slice(0, maxLen);
    for (const { pattern } of PII_PATTERNS) {
      preview = preview.replace(pattern, '[PII]');
    }
    return preview;
  }
}

module.exports = { DataClassifier };
