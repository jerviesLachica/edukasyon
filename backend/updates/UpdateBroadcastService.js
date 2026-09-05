/**
 * Update Broadcast Service
 *
 * Sends FCM topic broadcasts announcing new app versions. Every install of the
 * Android app subscribes to the "app_updates" topic, so a single send reaches
 * all users.
 *
 * Configuration (Render env vars):
 *   FIREBASE_SERVICE_ACCOUNT — service-account key JSON from
 *     Firebase Console → Project settings → Service accounts.
 *     Accepts raw JSON or base64-encoded JSON.
 *
 * Used by POST /internal/broadcast-update (guarded by ADMIN_API_KEY).
 */

let messagingCache = null;

function getMessaging() {
  if (messagingCache) return messagingCache;
  let admin;
  try {
    admin = require('firebase-admin');
  } catch (err) {
    const error = new Error('firebase-admin dependency missing — run: npm install firebase-admin');
    error.status = 500;
    throw error;
  }

  if (admin.apps.length) {
    messagingCache = admin.messaging();
    return messagingCache;
  }

  const serviceAccount = require('../config/FirebaseCredential').loadServiceAccount();
  if (!serviceAccount) {
    const error = new Error('FIREBASE_SERVICE_ACCOUNT is not configured (env JSON/base64 or key file path)');
    error.status = 500;
    throw error;
  }

  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  console.log('[update-broadcast] firebase-admin initialized');
  messagingCache = admin.messaging();
  return messagingCache;
}

/**
 * Broadcasts an app-update data message to all installs via the topic.
 * The Android app renders its own notification with an "Install update" action.
 */
async function broadcastUpdate({
  versionCode,
  versionName,
  apkUrl,
  releaseNotes = '',
  mandatoryUpdate = false,
} = {}) {
  if (!versionCode || !versionName || !apkUrl) {
    const error = new Error('versionCode, versionName and apkUrl are required');
    error.status = 400;
    throw error;
  }
  // Sanity check that the versionCode is numeric.
  if (!/^\d+$/.test(String(versionCode))) {
    const error = new Error('versionCode must be an integer');
    error.status = 400;
    throw error;
  }

  const messaging = getMessaging();
  const message = {
    data: {
      type: 'app_update',
      versionCode: String(versionCode),
      versionName: String(versionName),
      apkUrl: String(apkUrl),
      releaseNotes: String(releaseNotes).slice(0, 400),
      mandatoryUpdate: mandatoryUpdate ? 'true' : 'false',
    },
    topic: 'app_updates',
  };

  const messageId = await messaging.send(message);
  console.log(`[update-broadcast] sent v${versionName} (${versionCode}) → topic "app_updates" [${messageId}]`);
  return { messageId };
}

module.exports = { broadcastUpdate };
