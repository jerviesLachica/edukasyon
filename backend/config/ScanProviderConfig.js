/**
 * Runtime scan-provider configuration.
 *
 * Lets operators activate the dedicated schedule-scanning vision provider
 * (e.g. NVIDIA NIM) by writing ONE Firestore document instead of touching
 * Render env vars:
 *
 *   Collection: runtimeConfig
 *   Document:   scanProvider
 *   Fields:     { apiKey: string, baseUrl?: string, model?: string }
 *
 * Security: firestore.rules deny ALL client access outside /users/{uid},
 * so this document is readable only via the Admin SDK (this backend).
 * The value is cached briefly to avoid per-scan reads.
 */
const CACHE_TTL_MS = 60_000;

let cachedAt = 0;
let cachedValue = null;

function getAdmin() {
  try {
    const admin = require('firebase-admin');
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) return null;
    if (!admin.apps.length) {
      let serviceAccount;
      try {
        serviceAccount = JSON.parse(
          raw.trim().startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8')
        );
      } catch (err) {
        console.warn('[scan-config] FIREBASE_SERVICE_ACCOUNT is not valid JSON/base64');
        return null;
      }
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    }
    return admin;
  } catch (err) {
    console.warn('[scan-config] firebase-admin unavailable:', String(err.message || err));
    return null;
  }
}

/**
 * Returns { apiKey, baseUrl?, model? } from Firestore when present, else null.
 * Never throws — configuration problems must degrade to the default provider.
 */
async function getRemoteScanProvider() {
  const now = Date.now();
  if (cachedValue !== null && now - cachedAt < CACHE_TTL_MS) return cachedValue;

  let result = null;
  const admin = getAdmin();
  if (admin) {
    try {
      const snap = await admin.firestore().doc('runtimeConfig/scanProvider').get();
      if (snap.exists) {
        const data = snap.data() || {};
        if (typeof data.apiKey === 'string' && data.apiKey.trim()) {
          result = {
            apiKey: data.apiKey.trim(),
            baseUrl: typeof data.baseUrl === 'string' ? data.baseUrl.trim() : undefined,
            model: typeof data.model === 'string' ? data.model.trim() : undefined,
          };
        }
      }
    } catch (err) {
      console.warn('[scan-config] Firestore read failed, using defaults:', String(err.message || err));
    }
  }

  cachedAt = now;
  cachedValue = result;
  return result;
}

module.exports = { getRemoteScanProvider };
