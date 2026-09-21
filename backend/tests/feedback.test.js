const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const http = require('http');
const app = require('../server');

function request(options, body = null) {
  return new Promise((resolve, reject) => {
    const server = http.createServer(app);
    server.listen(0, () => {
      const port = server.address().port;
      const reqOpts = {
        hostname: '127.0.0.1',
        port,
        path: options.path,
        method: options.method || 'GET',
        headers: options.headers || {}
      };

      const req = http.request(reqOpts, (res) => {
        let raw = '';
        res.on('data', chunk => { raw += chunk; });
        res.on('end', () => {
          server.close();
          let json = null;
          try { json = JSON.parse(raw); } catch (e) {}
          resolve({ status: res.statusCode, headers: res.headers, body: raw, json });
        });
      });

      req.on('error', (err) => {
        server.close();
        reject(err);
      });

      if (body) {
        req.write(typeof body === 'string' ? body : JSON.stringify(body));
      }
      req.end();
    });
  });
}

describe('User Feedback & Bug Reporting API', () => {
  const adminKey = process.env.ADMIN_KEY || process.env.DASHBOARD_PASSKEY || 'SchedMateAdmin2026!';

  it('rejects submissions with missing or too-short title/description', async () => {
    const res = await request({
      path: '/api/feedback',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-device-id': 'device-test-val' }
    }, {
      category: 'bug',
      title: 'hi', // too short
      description: 'short'
    });

    assert.equal(res.status, 400);
    assert.equal(res.json.ok, false);
  });

  it('detects and filters bot honeypot submissions', async () => {
    const res = await request({
      path: '/api/feedback',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-device-id': 'bot-trap-1' }
    }, {
      category: 'bug',
      title: 'Valid Looking Title',
      description: 'Valid looking description with enough characters',
      hp: 'bot_filled_input' // Honeypot trap!
    });

    assert.equal(res.status, 200);
    assert.equal(res.json.ok, true);
    assert.equal(res.json.id, 'filtered');
  });

  it('successfully creates valid feature suggestions and bug reports with device metadata', async () => {
    const res = await request({
      path: '/api/feedback',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-device-id': 'user-phone-101' }
    }, {
      category: 'bug',
      title: 'Audio alarm plays default sound instead of custom tone',
      description: 'When I choose an external MP3 file and restart the device, the alarm reverts to the stock beep tone.',
      device: {
        appVersion: '2.1.3',
        buildNumber: '14',
        osVersion: 'Android 14',
        deviceModel: 'Samsung Galaxy S23'
      }
    });

    assert.equal(res.status, 200);
    assert.equal(res.json.ok, true);
    assert.ok(res.json.id);

    // Retrieve via admin API
    const adminRes = await request({
      path: '/api/admin/feedback',
      method: 'GET',
      headers: { 'x-admin-key': adminKey }
    });

    assert.equal(adminRes.status, 200);
    assert.ok(Array.isArray(adminRes.json.feedback));
    const created = adminRes.json.feedback.find(f => f.id === res.json.id);
    assert.ok(created);
    assert.equal(created.category, 'bug');
    assert.equal(created.device.deviceModel, 'Samsung Galaxy S23');
    assert.equal(created.status, 'new');

    // Update status to resolved
    const patchRes = await request({
      path: `/api/admin/feedback/${created.id}`,
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'x-admin-key': adminKey }
    }, { status: 'resolved' });

    assert.equal(patchRes.status, 200);
    assert.equal(patchRes.json.item.status, 'resolved');
  });

  it('enforces rate limiting on rapid repeated submissions', async () => {
    const testDeviceId = 'spam-bot-device-999';
    for (let i = 0; i < 3; i++) {
      await request({
        path: '/api/feedback',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-device-id': testDeviceId }
      }, {
        category: 'general',
        title: `Spam attempt ${i}`,
        description: 'Repeated test submission that should hit the rate limiter.'
      });
    }

    // 4th request must be rate limited with 429
    const limitedRes = await request({
      path: '/api/feedback',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-device-id': testDeviceId }
    }, {
      category: 'general',
      title: 'Spam attempt 4',
      description: 'This submission exceeds rate limits.'
    });

    assert.equal(limitedRes.status, 429);
    assert.equal(limitedRes.json.ok, false);
  });
});
