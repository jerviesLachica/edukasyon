#!/usr/bin/env node
/**
 * Ops tool: write or clear the runtime schedule-scanner provider config.
 *
 *   node scripts/set-scan-provider-config.js <serviceAccount.json> <apiKey> [baseUrl] [model]
 *   node scripts/set-scan-provider-config.js <serviceAccount.json> --clear
 *
 * The key lives in Firestore (runtimeConfig/scanProvider) — readable only by
 * the Admin SDK per firestore.rules, never committed to git.
 */
const fs = require('fs');
const admin = require('firebase-admin');

const [, , saPath, apiKey, baseUrl, model] = process.argv;
if (!saPath || !apiKey) {
  console.error('usage: node scripts/set-scan-provider-config.js <serviceAccount.json> <apiKey|--clear> [baseUrl] [model]');
  process.exit(1);
}

const serviceAccount = JSON.parse(fs.readFileSync(saPath, 'utf8'));
if (!admin.apps.length) {
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
}
const doc = admin.firestore().doc('runtimeConfig/scanProvider');

(async () => {
  if (apiKey === '--clear') {
    await doc.delete();
    console.log('[ok] runtimeConfig/scanProvider cleared');
  } else {
    await doc.set({
      apiKey,
      ...(baseUrl ? { baseUrl } : {}),
      ...(model ? { model } : {}),
      updatedAt: new Date().toISOString(),
    });
    console.log('[ok] runtimeConfig/scanProvider written', { baseUrl, model });
  }
  process.exit(0);
})().catch((err) => {
  console.error('[fail]', err.message);
  process.exit(1);
});
