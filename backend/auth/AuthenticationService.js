/**
 * Resolves caller identity for rate limits, quotas, and abuse tracking.
 *
 * v1: X-Device-Id header + client IP (+ optional Firebase Bearer token passthrough).
 * Full Firebase ID token verification requires firebase-admin (documented gap).
 */

const crypto = require('crypto');

const DEVICE_ID_PATTERN = /^[a-zA-Z0-9._-]{8,128}$/;

class AuthenticationService {
  constructor(options = {}) {
    this.requireDeviceId = options.requireDeviceId ?? false;
    this.requireFirebaseAuth = options.requireFirebaseAuth ?? false;
  }

  /**
   * @returns {{ ok: true, identity: AuthIdentity } | { ok: false, status: number, code: string, message: string }}
   */
  authenticate(req) {
    const clientIp = this.extractClientIp(req);
    const deviceId = this.extractDeviceId(req);
    const bearerToken = this.extractBearerToken(req);

    if (this.requireDeviceId && !deviceId) {
      return {
        ok: false,
        status: 401,
        code: 'AUTH_DEVICE_ID_REQUIRED',
        message: 'Device identification is required for AI requests.',
      };
    }

    if (this.requireFirebaseAuth && !bearerToken) {
      return {
        ok: false,
        status: 401,
        code: 'AUTH_TOKEN_REQUIRED',
        message: 'Authentication is required for AI requests.',
      };
    }

    const userId = this.resolveUserId({ deviceId, bearerToken, clientIp });
    const authMethod = bearerToken ? 'bearer_passthrough' : deviceId ? 'device_id' : 'ip_fallback';

    return {
      ok: true,
      identity: {
        userId,
        deviceId: deviceId || null,
        clientIp,
        authMethod,
        hasBearerToken: Boolean(bearerToken),
        // Privacy-preserving hash for logs (never log raw device IDs in production logs)
        logSubject: crypto.createHash('sha256').update(userId).digest('hex').slice(0, 16),
      },
    };
  }

  extractClientIp(req) {
    const forwarded = req.headers['x-forwarded-for'];
    if (typeof forwarded === 'string' && forwarded.length > 0) {
      return forwarded.split(',')[0].trim();
    }
    return req.socket?.remoteAddress || req.ip || 'unknown';
  }

  extractDeviceId(req) {
    const header = req.headers['x-device-id'];
    if (typeof header !== 'string') return null;
    const trimmed = header.trim();
    return DEVICE_ID_PATTERN.test(trimmed) ? trimmed : null;
  }

  extractBearerToken(req) {
    const auth = req.headers.authorization;
    if (typeof auth !== 'string' || !auth.startsWith('Bearer ')) return null;
    const token = auth.slice(7).trim();
    return token.length >= 20 ? token : null;
  }

  resolveUserId({ deviceId, bearerToken, clientIp }) {
    if (bearerToken) {
      // v1: hash token for stable per-user key without storing token
      return `fb:${crypto.createHash('sha256').update(bearerToken).digest('hex').slice(0, 32)}`;
    }
    if (deviceId) return `dev:${deviceId}`;
    return `ip:${clientIp}`;
  }
}

module.exports = { AuthenticationService, DEVICE_ID_PATTERN };
