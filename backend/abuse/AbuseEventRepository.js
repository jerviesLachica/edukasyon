/**
 * Privacy-preserving abuse event log (in-memory ring buffer).
 * Never stores full user messages — only metadata and hashed subjects.
 */

const MAX_EVENTS = 5000;

class AbuseEventRepository {
  constructor(options = {}) {
    this.maxEvents = options.maxEvents ?? MAX_EVENTS;
    /** @type {AbuseEvent[]} */
    this.events = [];
  }

  /**
   * @param {Omit<AbuseEvent, 'id' | 'timestamp'>} event
   */
  record(event) {
    const entry = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      timestamp: new Date().toISOString(),
      ...event,
    };
    this.events.push(entry);
    if (this.events.length > this.maxEvents) {
      this.events.splice(0, this.events.length - this.maxEvents);
    }
    return entry;
  }

  recent(limit = 100) {
    return this.events.slice(-limit);
  }

  countByType(sinceMs = 86_400_000) {
    const cutoff = Date.now() - sinceMs;
    const counts = {};
    for (const e of this.events) {
      if (new Date(e.timestamp).getTime() < cutoff) continue;
      counts[e.type] = (counts[e.type] || 0) + 1;
    }
    return counts;
  }
}

module.exports = { AbuseEventRepository };
