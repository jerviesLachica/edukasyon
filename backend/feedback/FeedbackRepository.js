const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

class FeedbackRepository {
  constructor(options = {}) {
    this.maxItems = options.maxItems || 300;
    this.storagePath = options.storagePath || (process.env.VERCEL ? path.join('/tmp', 'feedback.json') : path.join(__dirname, '../data/feedback.json'));
    this.items = [];
    this.rateLimits = new Map(); // identifier -> [timestamps]

    this._ensureDir();
    this._load();
  }

  _ensureDir() {
    try {
      const dir = path.dirname(this.storagePath);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
    } catch (_) {}
  }

  _load() {
    try {
      if (fs.existsSync(this.storagePath)) {
        const raw = fs.readFileSync(this.storagePath, 'utf8');
        this.items = JSON.parse(raw);
        if (!Array.isArray(this.items)) this.items = [];
      }
    } catch (_) {
      this.items = [];
    }
  }

  _save() {
    try {
      this._ensureDir();
      fs.writeFileSync(this.storagePath, JSON.stringify(this.items, null, 2), 'utf8');
    } catch (_) {}
  }

  checkRateLimit(identifier, maxPerWindow = 3, windowMs = 10 * 60 * 1000) {
    if (!identifier) return true;
    const now = Date.now();
    const history = (this.rateLimits.get(identifier) || []).filter(t => now - t < windowMs);
    if (history.length >= maxPerWindow) {
      return false;
    }
    history.push(now);
    this.rateLimits.set(identifier, history);
    return true;
  }

  add({ category, title, description, device = {}, contact = '', honeypot = '' }) {
    // Anti-bot check: if honeypot has content, reject as spam
    if (honeypot && String(honeypot).trim().length > 0) {
      return { ok: false, spam: true, error: 'Honeypot validation failed' };
    }

    const cleanCategory = ['bug', 'feature', 'general'].includes(category) ? category : 'general';
    const cleanTitle = String(title || '').trim().slice(0, 140);
    const cleanDesc = String(description || '').trim().slice(0, 3000);

    if (cleanTitle.length < 3) {
      return { ok: false, error: 'Title must be at least 3 characters' };
    }
    if (cleanDesc.length < 10) {
      return { ok: false, error: 'Description must be at least 10 characters' };
    }

    const item = {
      id: crypto.randomUUID(),
      timestamp: Date.now(),
      category: cleanCategory,
      title: cleanTitle,
      description: cleanDesc,
      contact: contact ? String(contact).trim().slice(0, 100) : null,
      device: {
        appVersion: String(device.appVersion || 'Unknown').slice(0, 30),
        buildNumber: String(device.buildNumber || '').slice(0, 20),
        osVersion: String(device.osVersion || '').slice(0, 30),
        deviceModel: String(device.deviceModel || '').slice(0, 60),
      },
      status: 'new' // new, reviewed, resolved, dismissed
    };

    this.items.unshift(item);
    if (this.items.length > this.maxItems) {
      this.items = this.items.slice(0, this.maxItems);
    }

    this._save();
    return { ok: true, item };
  }

  getAll(filterCategory = null) {
    if (filterCategory) {
      return this.items.filter(i => i.category === filterCategory);
    }
    return this.items;
  }

  getById(id) {
    return this.items.find(i => i.id === id) || null;
  }

  updateStatus(id, newStatus) {
    const validStatuses = ['new', 'reviewed', 'resolved', 'dismissed'];
    if (!validStatuses.includes(newStatus)) return null;

    const item = this.getById(id);
    if (!item) return null;

    item.status = newStatus;
    item.updatedAt = Date.now();
    this._save();
    return item;
  }

  delete(id) {
    const initialLen = this.items.length;
    this.items = this.items.filter(i => i.id !== id);
    if (this.items.length !== initialLen) {
      this._save();
      return true;
    }
    return false;
  }
}

module.exports = { FeedbackRepository };
