/**
 * Shared Firebase service-account credential loader.
 *
 * Consumers: AuthenticationService (ID-token verification), ScanProviderConfig
 * (runtimeConfig/scanProvider via Firestore), UpdateBroadcastService (FCM).
 *
 * Supported sources, first match wins:
 *   1. FIREBASE_SERVICE_ACCOUNT env var — raw JSON or base64-encoded JSON.
 *   2. FIREBASE_SERVICE_ACCOUNT_FILE env var — path to the key file (Render
 *      "Secret File" mounts work well, e.g. /etc/secrets/service-account.json).
 *   3. GOOGLE_APPLICATION_CREDENTIALS env var — standard Google/Firebase path.
 *
 * Returns the parsed service-account object, or null with ONE actionable
 * warning (callers decide whether that disables their feature).
 */
const fs = require('fs');

let warned = false;

function warnOnce() {
  if (warned) return;
  warned = true;
  console.warn(
    '[firebase] FIREBASE_SERVICE_ACCOUNT is not configured — features that need it are disabled ' +
    '(ID-token verification, runtimeConfig/scanProvider overrides, FCM broadcast). ' +
    'Fix: set FIREBASE_SERVICE_ACCOUNT to the service-account key JSON (raw or base64), ' +
    'or attach the key as a Render Secret File and set FIREBASE_SERVICE_ACCOUNT_FILE to its path.'
  );
}

function parseServiceAccountJson(raw) {
  try {
    const parsed = JSON.parse(raw.trim().startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8'));
    if (!parsed || typeof parsed !== 'object' || !parsed.private_key || !parsed.client_email) {
      console.warn('[firebase] Service-account JSON is missing private_key/client_email — check the key file.');
      return null;
    }
    return parsed;
  } catch (_err) {
    return null;
  }
}

function loadServiceAccount() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (raw && raw.trim()) {
    const parsed = parseServiceAccountJson(raw);
    if (parsed) return parsed;
    console.warn('[firebase] FIREBASE_SERVICE_ACCOUNT is set but not valid JSON/base64 — trying file sources.');
  }

  const filePath = process.env.FIREBASE_SERVICE_ACCOUNT_FILE || process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (filePath && filePath.trim()) {
    try {
      const parsed = parseServiceAccountJson(fs.readFileSync(filePath.trim(), 'utf8'));
      if (parsed) return parsed;
      console.warn(`[firebase] Credential file at ${filePath} is not valid service-account JSON.`);
    } catch (_err) {
      console.warn(`[firebase] Could not read credential file at ${filePath}.`);
    }
  }

  warnOnce();
  return null;
}

module.exports = { loadServiceAccount };
