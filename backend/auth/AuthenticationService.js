/**
 * Resolves caller identity for rate limits, quotas, and abuse tracking.
 *
 * Identity sources, strongest first:
 *   1. Firebase ID token (Authorization: Bearer <token>) — cryptographically
 *      verified via firebase-admin. The identity key is `fb:<uid>` where uid
 *      comes from the *verified* token, never from the raw token string.
 *   2. X-Device-Id header — `dev:<deviceId>`.
 *   3. Client IP — `ip:<addr>` (last-resort fallback).
 *
 * Security invariant: a bearer token is only ever trusted after
 * admin.auth().verifyIdToken() succeeds. A token that is present but fails
 * verification is rejected (401) — it is NEVER silently downgraded to a
 * device/IP identity, because that downgrade would let an attacker present a
 * junk token and still be served. Unverified tokens never produce an `fb:` id.
 */

const crypto = require('crypto');

const DEVICE_ID_PATTERN = /^[a-zA-Z0-9._-]{8,128}$/;

// Verification outcomes for a presented bearer token.
const VERIFIED = 'verified'; // token cryptographically valid; decoded.uid present
const INVALID = 'invalid'; // verifier ran and rejected the token (bad/expired/revoked)
const UNAVAILABLE = 'unavailable'; // verification could not be performed (misconfig/dependency)

class AuthenticationService {
  /**
   * @param {object} [options]
   * @param {boolean} [options.requireDeviceId=false]
   * @param {boolean} [options.requireFirebaseAuth=false]
   * @param {boolean} [options.checkRevoked] Pass checkRevoked=true to verifyIdToken (extra lookup per call).
   *   Defaults to the SAFETY_FIREBASE_CHECK_REVOKED env flag.
   * @param {(token: string) => Promise<object|null>} [options.verifyIdToken] Custom verifier (mainly for tests).
   *   Resolves to the decoded token on success, or null/throws on failure. When provided, verification is
   *   always considered "available" (never UNAVAILABLE).
   */
  constructor(options = {}) {
    this.requireDeviceId = options.requireDeviceId ?? false;
    this.requireFirebaseAuth = options.requireFirebaseAuth ?? false;
    this.checkRevoked =
      options.checkRevoked ?? String(process.env.SAFETY_FIREBASE_CHECK_REVOKED) === 'true';
    this.customVerify = typeof options.verifyIdToken === 'function' ? options.verifyIdToken : null;

    // Lazy firebase-admin state (only used by the built-in verifier).
    this._admin = undefined; // undefined = not yet resolved, null = unavailable
  }

  /**
   * @param {import('express').Request} req
   * @returns {Promise<{ ok: true, identity: AuthIdentity } | { ok: false, status: number, code: string, message: string }>}
   */
  async authenticate(req) {
    const clientIp = this.extractClientIp(req);
    const deviceId = this.extractDeviceId(req);
    const bearerToken = this.extractBearerToken(req);

    let verifiedToken = null;

    if (bearerToken) {
      const outcome = await this.verifyBearer(bearerToken);

      if (outcome.status === VERIFIED) {
        verifiedToken = outcome.decoded;
      } else if (outcome.status === INVALID) {
        // A presented token that fails verification is a hard rejection.
        // Downgrading to device/IP here would re-open the bypass.
        return {
          ok: false,
          status: 401,
          code: 'AUTH_TOKEN_INVALID',
          message: 'Authentication token is invalid or expired. Please sign in again.',
        };
      } else {
        // UNAVAILABLE: verification could not be performed.
        if (this.requireFirebaseAuth) {
          // Auth is mandatory but we cannot verify — fail closed, do not guess.
          return {
            ok: false,
            status: 503,
            code: 'AUTH_VERIFICATION_UNAVAILABLE',
            message: 'Authentication is temporarily unavailable. Please try again later.',
          };
        }
        // Auth optional: ignore the unverifiable token and fall back to device/IP.
        // We never mint an fb: identity from an unverified token.
      }
    }

    if (this.requireDeviceId && !deviceId) {
      return {
        ok: false,
        status: 401,
        code: 'AUTH_DEVICE_ID_REQUIRED',
        message: 'Device identification is required for AI requests.',
      };
    }

    if (this.requireFirebaseAuth && !verifiedToken) {
      return {
        ok: false,
        status: 401,
        code: 'AUTH_TOKEN_REQUIRED',
        message: 'Authentication is required for AI requests.',
      };
    }

    const uid = verifiedToken ? verifiedToken.uid : null;
    const userId = uid ? `fb:${uid}` : deviceId ? `dev:${deviceId}` : `ip:${clientIp}`;
    const authMethod = uid ? 'firebase' : deviceId ? 'device_id' : 'ip_fallback';

    return {
      ok: true,
      identity: {
        userId,
        uid,
        deviceId: deviceId || null,
        clientIp,
        authMethod,
        hasBearerToken: Boolean(bearerToken),
        tokenVerified: Boolean(verifiedToken),
        // Privacy-preserving hash for logs (never log raw device IDs / uids in production logs)
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

  /**
   * Verify a bearer token. Returns a 3-state outcome so callers can distinguish
   * "rejected" (must 401) from "could not verify" (misconfiguration).
   * Never throws.
   * @returns {Promise<{status: 'verified', decoded: object} | {status: 'invalid'} | {status: 'unavailable'}>}
   */
  async verifyBearer(token) {
    // Custom injected verifier is always considered available.
    if (this.customVerify) {
      try {
        const decoded = await this.customVerify(token);
        if (decoded && typeof decoded.uid === 'string' && decoded.uid.length > 0) {
          return { status: VERIFIED, decoded };
        }
        return { status: INVALID };
      } catch (_err) {
        return { status: INVALID };
      }
    }

    const admin = this.getAdmin();
    if (!admin) {
      return { status: UNAVAILABLE };
    }

    try {
      const decoded = await admin.auth().verifyIdToken(token, this.checkRevoked);
      if (decoded && typeof decoded.uid === 'string' && decoded.uid.length > 0) {
        return { status: VERIFIED, decoded };
      }
      return { status: INVALID };
    } catch (_err) {
      // Bad signature, expired, revoked, malformed — all are hard rejections.
      return { status: INVALID };
    }
  }

  /**
   * Lazily initialize firebase-admin from FIREBASE_SERVICE_ACCOUNT (raw JSON or
   * base64), mirroring the pattern used by ScanProviderConfig / UpdateBroadcastService.
   * Returns the admin instance, or null when verification cannot be performed.
   * Result is cached (including the null "unavailable" result).
   */
  getAdmin() {
    if (this._admin !== undefined) return this._admin;

    this._admin = null; // default to unavailable unless init fully succeeds
    try {
      const admin = require('firebase-admin');
      if (admin.apps.length) {
        this._admin = admin;
        return this._admin;
      }

      const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
      if (!raw) {
        console.warn('[auth] FIREBASE_SERVICE_ACCOUNT not set — Firebase token verification disabled');
        return this._admin;
      }

      let serviceAccount;
      try {
        serviceAccount = JSON.parse(
          raw.trim().startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8')
        );
      } catch (_err) {
        console.warn('[auth] FIREBASE_SERVICE_ACCOUNT is not valid JSON/base64 — token verification disabled');
        return this._admin;
      }

      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
      console.log('[auth] firebase-admin initialized for ID token verification');
      this._admin = admin;
    } catch (err) {
      console.warn('[auth] firebase-admin unavailable:', String(err.message || err));
      this._admin = null;
    }
    return this._admin;
  }
}

module.exports = { AuthenticationService, DEVICE_ID_PATTERN };
