/**
 * Output moderation — strip secrets, block obvious harmful content in responses.
 */

const SECRET_PATTERNS = [
  /\bsk-[a-zA-Z0-9]{20,}\b/g,
  /\bAI_API_KEY\s*=\s*\S+/gi,
  /Bearer\s+[a-zA-Z0-9._-]{20,}/gi,
  /\b(api[_-]?key|secret[_-]?key|password)\s*[:=]\s*\S+/gi,
];

const OUTPUT_HARMFUL = [
  /\b(here('s| is) how to (make|build|synthesize) (a )?(bomb|explosive|poison))\b/i,
  /\b(step-by-step|detailed instructions).{0,40}(weapon|explosive|illegal drug)\b/i,
];

class OutputModerator {
  constructor(options = {}) {
    this.enabled = options.enabled !== false;
  }

  /**
   * @returns {{ text: string, blocked: boolean, redactions: number, reason?: string }}
   */
  moderate(text) {
    if (!this.enabled || !text) return { text: text || '', blocked: false, redactions: 0 };

    let output = String(text);
    let redactions = 0;

    for (const pattern of SECRET_PATTERNS) {
      const before = output;
      output = output.replace(pattern, '[REDACTED]');
      if (output !== before) redactions += 1;
    }

    for (const pattern of OUTPUT_HARMFUL) {
      if (pattern.test(output)) {
        return {
          text: '',
          blocked: true,
          redactions,
          reason: 'The AI response was blocked for safety. Please ask a study-related question.',
        };
      }
    }

    return { text: output, blocked: false, redactions };
  }
}

module.exports = { OutputModerator };
