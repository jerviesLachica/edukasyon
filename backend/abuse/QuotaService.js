/**
 * Daily/hourly usage quotas per identity (global + per-endpoint).
 * In-memory v1 — document Redis upgrade for horizontal scaling.
 */

class QuotaService {
  constructor() {
    /** @type {Map<string, { hourly: number[], daily: number[] }>} */
    this.usage = new Map();
  }

  _bucketKey(identity, endpoint, period) {
    return `${period}:${identity.userId}:${endpoint || 'global'}`;
  }

  _getHits(key) {
    return this.usage.get(key) || { hourly: [], daily: [] };
  }

  _recordHit(key) {
    const now = Date.now();
    const bucket = this._getHits(key);
    bucket.hourly = bucket.hourly.filter((t) => t > now - 3_600_000);
    bucket.daily = bucket.daily.filter((t) => t > now - 86_400_000);
    bucket.hourly.push(now);
    bucket.daily.push(now);
    this.usage.set(key, bucket);
    return bucket;
  }

  /**
   * @returns {{ allowed: boolean, code?: string, retryAfterMs?: number, scope?: string }}
   */
  checkAndConsume(identity, endpoint, policy, globalPolicy) {
    const now = Date.now();
    const hourlyLimit = policy.hourlyQuota ?? globalPolicy.globalHourlyQuota;
    const dailyLimit = policy.dailyQuota ?? globalPolicy.globalDailyQuota;

    const endpointHourlyKey = this._bucketKey(identity, endpoint, 'hourly');
    const endpointDailyKey = this._bucketKey(identity, endpoint, 'daily');
    const globalHourlyKey = this._bucketKey(identity, null, 'hourly');
    const globalDailyKey = this._bucketKey(identity, null, 'daily');

    const checks = [
      { key: endpointHourlyKey, limit: hourlyLimit, scope: 'endpoint_hourly' },
      { key: endpointDailyKey, limit: dailyLimit, scope: 'endpoint_daily' },
      { key: globalHourlyKey, limit: globalPolicy.globalHourlyQuota, scope: 'global_hourly' },
      { key: globalDailyKey, limit: globalPolicy.globalDailyQuota, scope: 'global_daily' },
    ];

    for (const { key, limit, scope } of checks) {
      const bucket = this._getHits(key);
      const hourlyHits = bucket.hourly.filter((t) => t > now - 3_600_000);
      const dailyHits = bucket.daily.filter((t) => t > now - 86_400_000);
      const relevant = key.startsWith('hourly:') ? hourlyHits : dailyHits;
      if (relevant.length >= limit) {
        const oldest = relevant[0] || now;
        const windowMs = key.startsWith('hourly:') ? 3_600_000 : 86_400_000;
        return {
          allowed: false,
          code: 'QUOTA_EXCEEDED',
          retryAfterMs: Math.max(0, oldest + windowMs - now),
          scope,
        };
      }
    }

    // All checks passed — record usage
    for (const { key } of checks) {
      this._recordHit(key);
    }

    return { allowed: true };
  }
}

module.exports = { QuotaService };
