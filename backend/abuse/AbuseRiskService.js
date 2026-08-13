/**
 * Lightweight risk scoring from recent abuse signals.
 */

class AbuseRiskService {
  constructor(abuseEvents) {
    this.abuseEvents = abuseEvents;
  }

  /**
   * @returns {{ score: number, level: 'low'|'medium'|'high', factors: string[] }}
   */
  assess(identity, endpoint) {
    const recent = this.abuseEvents.recent(200);
    const subject = identity.logSubject;
    const factors = [];
    let score = 0;

    const subjectEvents = recent.filter((e) => e.logSubject === subject);
    const moderationBlocks = subjectEvents.filter((e) => e.type === 'input_blocked' || e.type === 'injection_detected');
    const rateLimits = subjectEvents.filter((e) => e.type === 'rate_limited');

    if (moderationBlocks.length >= 3) {
      score += 40;
      factors.push('repeated_moderation_blocks');
    }
    if (rateLimits.length >= 5) {
      score += 25;
      factors.push('rate_limit_hits');
    }
    if (subjectEvents.filter((e) => e.endpoint === endpoint).length >= 10) {
      score += 15;
      factors.push('endpoint_burst');
    }
    if (identity.authMethod === 'ip_fallback') {
      score += 10;
      factors.push('anonymous_ip_only');
    }

    const level = score >= 50 ? 'high' : score >= 25 ? 'medium' : 'low';
    return { score, level, factors };
  }

  shouldBlockHighRisk(risk) {
    return risk.level === 'high';
  }
}

module.exports = { AbuseRiskService };
