/**
 * Layered input moderation: structural rules + pattern scoring (not keyword-only).
 */

const EDUCATIONAL_ALLOWLIST = [
  /\b(cyber\s*security|cybersecurity|information security)\b/i,
  /\b(photosynthesis|calculus|algebra|physics|chemistry|biology)\b/i,
  /\b(homework|assignment|exam|quiz|study|lecture|notes)\b/i,
];

const HARMFUL_PATTERNS = [
  { pattern: /\b(how to|instructions for|recipe for)\s+(make|build|synthesize)\s+(a\s+)?(bomb|explosive|weapon|poison|meth)\b/i, weight: 10, category: 'violence' },
  { pattern: /\b(kill|murder|harm|hurt)\s+(myself|yourself|someone|people|children)\b/i, weight: 8, category: 'self_harm' },
  { pattern: /\b(child|minor|underage)\s+(porn|sexual|nude|explicit)\b/i, weight: 10, category: 'csam' },
  { pattern: /\b(credit card|ssn|social security|password|otp|api[_\s]?key)\s*(number|code)?\s*(is|:|=)/i, weight: 7, category: 'pii_exfil' },
  { pattern: /\b(cheat|answers)\s+(on|for|during)\s+(my\s+)?(live|active|current|ongoing|in[- ]progress)\s+(exam|test|quiz)\b/i, weight: 6, category: 'academic_integrity' },
  { pattern: /\b(cheat|answers)\s+(on|for|during)\s+(my\s+)?(exam|test|quiz)\b/i, weight: 4, category: 'academic_integrity' },
  { pattern: /\b(ignore|disregard|forget)\s+(all\s+)?(previous|prior|above|system)\s+(instructions|prompts|rules)\b/i, weight: 5, category: 'injection' },
  { pattern: /\b(you are now|act as|pretend to be|roleplay as)\s+(dan|jailbreak|unrestricted|admin|root)\b/i, weight: 6, category: 'injection' },
  { pattern: /\b(reveal|show|print|output)\s+(your\s+)?(system\s+)?(prompt|instructions|rules)\b/i, weight: 5, category: 'injection' },
];

const SPAM_PATTERNS = [
  { pattern: /(.)\1{20,}/, weight: 4, category: 'spam' },
  { pattern: /\b(buy now|click here|free money|crypto pump)\b/i, weight: 3, category: 'spam' },
];

class InputModerator {
  constructor(options = {}) {
    this.enabled = options.enabled !== false;
    this.blockThreshold = options.blockThreshold ?? 6;
  }

  /**
   * @returns {{ allowed: boolean, score: number, categories: string[], reason?: string }}
   */
  moderate(text, context = {}) {
    if (!this.enabled) return { allowed: true, score: 0, categories: [] };

    const input = String(text || '').trim();
    if (!input) return { allowed: true, score: 0, categories: [] };

    let score = 0;
    const categories = new Set();

    for (const { pattern, weight, category } of [...HARMFUL_PATTERNS, ...SPAM_PATTERNS]) {
      if (pattern.test(input)) {
        score += weight;
        categories.add(category);
      }
    }

    // Educational context reduces false positives (not when integrity violations detected)
    const isEducational = EDUCATIONAL_ALLOWLIST.some((p) => p.test(input));
    const educationalDiscount =
      isEducational && !categories.has('academic_integrity') && !categories.has('violence') ? 2 : 0;

    // Excessive length without educational markers
    if (input.length > 8000 && !isEducational) {
      score += 2;
      categories.add('oversized_input');
    }

    score = Math.max(0, score - educationalDiscount);

    if (score >= this.blockThreshold) {
      return {
        allowed: false,
        score,
        categories: [...categories],
        reason: 'Your message was blocked by our safety filters. Please rephrase and keep questions study-related.',
      };
    }

    return { allowed: true, score, categories: [...categories] };
  }
}

module.exports = { InputModerator, HARMFUL_PATTERNS };
