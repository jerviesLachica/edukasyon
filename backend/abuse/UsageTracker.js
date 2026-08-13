/**
 * Aggregates AI usage metrics for cost monitoring (no content stored).
 */

class UsageTracker {
  constructor() {
    /** @type {Map<string, { count: number, tokensEstimate: number, lastAt: string }>} */
    this.byEndpoint = new Map();
    /** @type {Map<string, number>} */
    this.byIdentity = new Map();
  }

  record({ endpoint, identity, outputChars = 0, inputChars = 0, model = null }) {
    const ep = this.byEndpoint.get(endpoint) || { count: 0, tokensEstimate: 0, lastAt: null };
    ep.count += 1;
    ep.tokensEstimate += Math.ceil((inputChars + outputChars) / 4);
    ep.lastAt = new Date().toISOString();
    this.byEndpoint.set(endpoint, ep);

    const idKey = identity?.logSubject || 'unknown';
    this.byIdentity.set(idKey, (this.byIdentity.get(idKey) || 0) + 1);

    return {
      endpoint,
      model,
      estimatedTokens: Math.ceil((inputChars + outputChars) / 4),
    };
  }

  snapshot() {
    return {
      endpoints: Object.fromEntries(this.byEndpoint),
      uniqueIdentities: this.byIdentity.size,
      totalRequests: [...this.byEndpoint.values()].reduce((s, v) => s + v.count, 0),
    };
  }
}

module.exports = { UsageTracker };
