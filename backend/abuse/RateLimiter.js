/**
 * In-memory sliding-window rate limiter (per identity + endpoint + IP).
 * Upgrade path: replace store with Redis/Valkey for multi-instance Render deploys.
 */

class RateLimiter {
  constructor() {
    /** @type {Map<string, number[]>} */
    this.windows = new Map();
    this.cleanupInterval = setInterval(() => this.cleanup(), 5 * 60 * 1000);
    if (this.cleanupInterval.unref) this.cleanupInterval.unref();
  }

  /**
   * @returns {{ allowed: boolean, retryAfterMs?: number, remaining?: number }}
   */
  check(key, { limit, windowMs = 60_000 }) {
    const now = Date.now();
    const windowStart = now - windowMs;
    const hits = (this.windows.get(key) || []).filter((t) => t > windowStart);

    if (hits.length >= limit) {
      const oldest = hits[0];
      const retryAfterMs = Math.max(0, oldest + windowMs - now);
      this.windows.set(key, hits);
      return { allowed: false, retryAfterMs, remaining: 0 };
    }

    hits.push(now);
    this.windows.set(key, hits);
    return { allowed: true, remaining: limit - hits.length };
  }

  checkEndpoint(identity, endpoint, policy) {
    const limit = policy.rateLimitPerMin ?? 5;
    const burst = policy.burstPerMin ?? limit;
    const userKey = `user:${identity.userId}:${endpoint}`;
    const ipKey = `ip:${identity.clientIp}:${endpoint}`;

    const userResult = this.check(userKey, { limit: burst, windowMs: 60_000 });
    if (!userResult.allowed) {
      return { allowed: false, retryAfterMs: userResult.retryAfterMs, scope: 'user' };
    }

    // IP-level backstop (3x user limit) to catch distributed abuse on shared devices
    const ipLimit = Math.max(burst * 3, 10);
    const ipResult = this.check(ipKey, { limit: ipLimit, windowMs: 60_000 });
    if (!ipResult.allowed) {
      return { allowed: false, retryAfterMs: ipResult.retryAfterMs, scope: 'ip' };
    }

    return { allowed: true, remaining: userResult.remaining };
  }

  cleanup() {
    const cutoff = Date.now() - 2 * 60 * 60 * 1000;
    for (const [key, hits] of this.windows.entries()) {
      const fresh = hits.filter((t) => t > cutoff);
      if (fresh.length === 0) this.windows.delete(key);
      else this.windows.set(key, fresh);
    }
  }

  destroy() {
    if (this.cleanupInterval) clearInterval(this.cleanupInterval);
    this.windows.clear();
  }
}

module.exports = { RateLimiter };
