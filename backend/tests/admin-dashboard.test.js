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

describe('Admin Fleet Dashboard & Metrics API', () => {
  it('rejects unauthenticated access to /api/admin/auth with invalid passkey', async () => {
    const res = await request({
      path: '/api/admin/auth',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { passkey: 'wrong-passkey' });

    assert.equal(res.status, 401);
    assert.equal(res.json.ok, false);
  });

  it('authenticates valid passkey and returns HMAC session token', async () => {
    const validKey = process.env.ADMIN_KEY || process.env.DASHBOARD_PASSKEY || 'SchedMateAdmin2026!';
    const res = await request({
      path: '/api/admin/auth',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { passkey: validKey });

    assert.equal(res.status, 200);
    assert.equal(res.json.ok, true);
    assert.ok(res.json.token);
    assert.ok(res.json.expiresAt > Date.now());
  });

  it('rejects unauthenticated access to /api/admin/metrics', async () => {
    const res = await request({
      path: '/api/admin/metrics',
      method: 'GET'
    });

    assert.equal(res.status, 401);
    assert.equal(res.json.ok, false);
  });

  it('allows access to /api/admin/metrics with x-admin-key header', async () => {
    const validKey = process.env.ADMIN_KEY || process.env.DASHBOARD_PASSKEY || 'SchedMateAdmin2026!';
    const res = await request({
      path: '/api/admin/metrics',
      method: 'GET',
      headers: { 'x-admin-key': validKey }
    });

    assert.equal(res.status, 200);
    assert.equal(res.json.ok, true);
    assert.ok(res.json.system);
    assert.ok(res.json.ai);
    assert.ok(res.json.policy);
    assert.ok(res.json.usage);
    const expectedVersion = require('../../version.json').versionName;
    assert.equal(res.json.version.versionName, expectedVersion);
  });

  it('serves admin dashboard HTML at /admin', async () => {
    const res = await request({
      path: '/admin',
      method: 'GET'
    });

    assert.equal(res.status, 200);
    assert.ok(res.body.includes('SchedMate Fleet Command'));
    assert.ok(res.body.includes('Master Passkey'));
  });
});
