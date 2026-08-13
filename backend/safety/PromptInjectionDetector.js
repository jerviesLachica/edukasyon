/**
 * Detects prompt injection attempts in untrusted user/document content.
 */

const INJECTION_PATTERNS = [
  { pattern: /<\s*\/?\s*(system|assistant|instruction|prompt)\s*>/i, weight: 8 },
  { pattern: /\[\s*(system|inst|SYS)\s*\]/i, weight: 7 },
  { pattern: /```\s*(system|prompt|instructions)/i, weight: 6 },
  { pattern: /\b(ignore|disregard|forget)\s+((all|any)\s+)?(previous|prior|above|earlier)\s+(instructions|prompts|rules)\b/i, weight: 7 },
  { pattern: /\b(you are now|from now on you are|act as|pretend to be)\s+(an?\s+)?(unrestricted|admin|root|jailbreak|dan)\b/i, weight: 7 },
  { pattern: /\b(override|bypass|disable|turn off)\s+(safety|moderation|filters|guardrails)\b/i, weight: 7 },
  { pattern: /\b(developer mode|debug mode|maintenance mode|admin mode)\b/i, weight: 5 },
  { pattern: /\b(do not follow|stop following|ignore)\s+(the )?(system|developer|openai|jarvis)\b/i, weight: 6 },
  { pattern: /\bnew instructions?:\s*you are\b/i, weight: 7 },
  { pattern: /\bfrom now on,?\s+you (are|will|must|should)\b/i, weight: 5 },
  { pattern: /\bpretend (the )?(above|previous|system) (rules|instructions) (don't|do not) (exist|apply)\b/i, weight: 8 },
  { pattern: /\b(base64|decode|execute|run)\s+(this|the following)\s+(command|code|script|payload)\b/i, weight: 6 },
];

class PromptInjectionDetector {
  constructor(options = {}) {
    this.enabled = options.enabled !== false;
    this.blockThreshold = options.blockThreshold ?? 6;
  }

  /**
   * @returns {{ detected: boolean, score: number, signals: string[] }}
   */
  analyze(text) {
    if (!this.enabled || !text) return { detected: false, score: 0, signals: [] };

    const input = String(text);
    let score = 0;
    const signals = [];

    for (const { pattern, weight } of INJECTION_PATTERNS) {
      if (pattern.test(input)) {
        score += weight;
        signals.push(pattern.source.slice(0, 40));
      }
    }

    // Multiple newline-separated "instruction-like" blocks
    const instructionLines = input.split('\n').filter((l) =>
      /^(system|assistant|user|instruction|rule|note):\s/i.test(l.trim())
    );
    if (instructionLines.length >= 2) {
      score += 5;
      signals.push('multi_role_headers');
    }

    return {
      detected: score >= this.blockThreshold,
      score,
      signals,
    };
  }
}

module.exports = { PromptInjectionDetector, INJECTION_PATTERNS };
