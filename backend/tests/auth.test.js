/**
 * AuthenticationService tests — focused on the Firebase ID token verification
 * security fix (CRITICAL: forged/unverified bearer tokens must never grant an
 * authenticated identity).
 *
 * Run: npm test
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const { AuthenticationService } = require('../auth/AuthenticationService');
const { AiSafetyGateway } = require('../ai/AiSafetyGateway');
const { loadSafetyPolicy } = require('../safety/SafetyPolicy');
const { RateLimiter } = require('../abuse/RateLimiter');
const { InputModerator } = require('../safety/InputModerator');
const { PromptInjectionDetector } = require('../safety/PromptInjectionDetector');

const JUNK_TOKEN = 'a'.repeat(40); // passes the >=20 length gate, but is not a real token

function reqWith({ authorization, deviceId, ip = '203.0.113.7' } = {}) {
  const headers = {};
  if (authorization) headers.authorization = authorization;
  if (deviceId) headers['x-device-id'] = deviceId;
  return { headers, socket: { remoteAddress: ip } };
}

/** Build a service whose built-in verifier is forced UNAVAILABLE (no service account). */
function unverifiableService(opts = {}) {
  const svc = new AuthenticationService(opts);
  svc._admin = null; // deterministically force getAdmin() -> null (UNAVAILABLE)
  return svc;
}

describe('AuthenticationService — Firebase token verification', () => {
  it('CRITICAL regression: a junk bearer token does NOT produce an fb: identity', async () => {
    // requireFirebaseAuth is off (default posture). A forged token must be
    // ignored and the caller downgraded to device identity — never trusted.
    const svc = unverifiableService({ requireFirebaseAuth: false });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}`, deviceId: 'device-abc123' }));

    assert.equal(result.ok, true);
    assert.equal(result.identity.authMethod, 'device_id');
    assert.equal(result.identity.uid, null);
    assert.equal(result.identity.tokenVerified, false);
    assert.equal(result.identity.userId, 'dev:device-abc123');
    assert.ok(!result.identity.userId.startsWith('fb:'), 'must not mint an fb: identity from an unverified token');
  });

  it('CRITICAL regression: two different junk tokens cannot mint two distinct identities', async () => {
    // The old code hashed the raw token -> unlimited unique per-user quota keys.
    const svc = unverifiableService({ requireFirebaseAuth: false });
    const a = await svc.authenticate(reqWith({ authorization: 'Bearer ' + 'x'.repeat(40), deviceId: 'same-device-1' }));
    const b = await svc.authenticate(reqWith({ authorization: 'Bearer ' + 'y'.repeat(40), deviceId: 'same-device-1' }));

    assert.equal(a.identity.userId, b.identity.userId, 'quota key must be stable, not token-derived');
    assert.equal(a.identity.userId, 'dev:same-device-1');
  });

  it('accepts a validly verified token and keys identity on the verified uid', async () => {
    const svc = new AuthenticationService({
      verifyIdToken: async () => ({ uid: 'firebase-user-777' }),
    });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}`, deviceId: 'device-abc123' }));

    assert.equal(result.ok, true);
    assert.equal(result.identity.authMethod, 'firebase');
    assert.equal(result.identity.uid, 'firebase-user-777');
    assert.equal(result.identity.userId, 'fb:firebase-user-777');
    assert.equal(result.identity.tokenVerified, true);
  });

  it('identity is uid-derived, not token-derived (stable across token rotation)', async () => {
    const svc = new AuthenticationService({ verifyIdToken: async () => ({ uid: 'stable-uid' }) });
    const a = await svc.authenticate(reqWith({ authorization: 'Bearer ' + '1'.repeat(40) }));
    const b = await svc.authenticate(reqWith({ authorization: 'Bearer ' + '2'.repeat(40) }));
    assert.equal(a.identity.userId, b.identity.userId);
    assert.equal(a.identity.userId, 'fb:stable-uid');
  });

  it('rejects a presented-but-invalid token with 401 (never downgrades)', async () => {
    // Verifier is available and rejects the token — this is a hard 401 even
    // though requireFirebaseAuth is false, because presenting a bad token is an
    // explicit (false) identity claim.
    const svc = new AuthenticationService({
      requireFirebaseAuth: false,
      verifyIdToken: async () => null, // rejected
    });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}`, deviceId: 'device-abc123' }));

    assert.equal(result.ok, false);
    assert.equal(result.status, 401);
    assert.equal(result.code, 'AUTH_TOKEN_INVALID');
  });

  it('rejects a token whose verifier throws (bad signature / expired)', async () => {
    const svc = new AuthenticationService({
      verifyIdToken: async () => {
        throw new Error('Firebase ID token has expired');
      },
    });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}` }));
    assert.equal(result.ok, false);
    assert.equal(result.status, 401);
    assert.equal(result.code, 'AUTH_TOKEN_INVALID');
  });

  it('treats a verified token with no uid as invalid', async () => {
    const svc = new AuthenticationService({ verifyIdToken: async () => ({ email: 'x@y.z' }) });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}` }));
    assert.equal(result.ok, false);
    assert.equal(result.code, 'AUTH_TOKEN_INVALID');
  });

  it('requireFirebaseAuth: rejects requests with no token (401 AUTH_TOKEN_REQUIRED)', async () => {
    const svc = new AuthenticationService({ requireFirebaseAuth: true });
    const result = await svc.authenticate(reqWith({ deviceId: 'device-abc123' }));
    assert.equal(result.ok, false);
    assert.equal(result.status, 401);
    assert.equal(result.code, 'AUTH_TOKEN_REQUIRED');
  });

  it('requireFirebaseAuth: fails closed with 503 when verification is unavailable', async () => {
    const svc = unverifiableService({ requireFirebaseAuth: true });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}` }));
    assert.equal(result.ok, false);
    assert.equal(result.status, 503);
    assert.equal(result.code, 'AUTH_VERIFICATION_UNAVAILABLE');
  });

  it('requireFirebaseAuth: accepts a validly verified token', async () => {
    const svc = new AuthenticationService({
      requireFirebaseAuth: true,
      verifyIdToken: async () => ({ uid: 'ok-user' }),
    });
    const result = await svc.authenticate(reqWith({ authorization: `Bearer ${JUNK_TOKEN}` }));
    assert.equal(result.ok, true);
    assert.equal(result.identity.userId, 'fb:ok-user');
  });

  it('falls back to device then IP when no token is present', async () => {
    const svc = new AuthenticationService();
    const dev = await svc.authenticate(reqWith({ deviceId: 'device-abc123' }));
    assert.equal(dev.identity.authMethod, 'device_id');
    assert.equal(dev.identity.userId, 'dev:device-abc123');

    const ip = await svc.authenticate(reqWith({ ip: '198.51.100.9' }));
    assert.equal(ip.identity.authMethod, 'ip_fallback');
    assert.equal(ip.identity.userId, 'ip:198.51.100.9');
  });

  it('requireDeviceId: rejects when device id is missing', async () => {
    const svc = new AuthenticationService({ requireDeviceId: true });
    const result = await svc.authenticate(reqWith({ ip: '198.51.100.9' }));
    assert.equal(result.ok, false);
    assert.equal(result.code, 'AUTH_DEVICE_ID_REQUIRED');
  });
});

describe('AiSafetyGateway — awaits async authentication', () => {
  it('does not treat the authenticate() promise as a truthy result (await regression)', async () => {
    // If the gateway forgot to await authenticate(), authResult would be a
    // Promise, authResult.ok would be undefined, and the request would 401.
    const policy = loadSafetyPolicy();
    const gateway = new AiSafetyGateway({
      auth: new AuthenticationService({ verifyIdToken: async () => ({ uid: 'gw-user' }) }),
      policy,
      rateLimiter: new RateLimiter(),
      quotaService: { checkAndConsume: () => ({ allowed: true }) },
      inputModerator: new InputModerator(),
      outputModerator: { moderate: (t) => ({ text: t, blocked: false, redactions: 0 }) },
      promptInjection: new PromptInjectionDetector(),
      dataClassifier: { classify: () => ({ hasPii: false, types: [], count: 0 }) },
      abuseRisk: { assess: () => ({ score: 0, level: 'low', factors: [] }), shouldBlockHighRisk: () => false },
      abuseEvents: { record: () => {} },
      usageTracker: { record: () => {} },
      // No API key -> mock mode, so we exercise auth + validation without a real provider.
      provider: { hasAiKey: false },
      mockHandler: () => ({ reply: 'mock-ok' }),
    });

    const req = {
      body: { message: 'hello' },
      headers: { authorization: `Bearer ${JUNK_TOKEN}` },
      socket: { remoteAddress: '203.0.113.7' },
    };
    let statusCode = 200;
    let body = null;
    const res = {
      status(code) {
        statusCode = code;
        return this;
      },
      json(data) {
        body = data;
        return this;
      },
    };

    await gateway.handle(req, res, {
      endpoint: 'chat',
      extractInputText: (b) => b.message || '',
    });

    assert.equal(statusCode, 200);
    assert.deepEqual(body, { reply: 'mock-ok' });
  });
});
