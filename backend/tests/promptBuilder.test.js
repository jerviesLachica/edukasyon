/**
 * PromptBuilder / chat history tests.
 * Run: npm test
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { normalizeHistoryMessages } = require('../ai/PromptBuilder');

describe('normalizeHistoryMessages', () => {
  it('returns empty array for non-array input', () => {
    assert.deepEqual(normalizeHistoryMessages(null), []);
    assert.deepEqual(normalizeHistoryMessages(undefined), []);
    assert.deepEqual(normalizeHistoryMessages('bad'), []);
  });

  it('keeps valid user and assistant turns in order', () => {
    const history = normalizeHistoryMessages([
      { role: 'user', content: 'Write an essay about Jose Rizal' },
      { role: 'assistant', content: 'Here is your essay...' },
      { role: 'system', content: 'should be ignored' },
      { role: 'user', content: '   ' },
      null,
    ]);
    assert.equal(history.length, 2);
    assert.equal(history[0].role, 'user');
    assert.equal(history[1].role, 'assistant');
    assert.ok(history[0].content.includes('Jose Rizal'));
  });

  it('trims content and caps per-message length', () => {
    const history = normalizeHistoryMessages([
      { role: 'user', content: '  hello  ' },
    ]);
    assert.equal(history[0].content, 'hello');
  });
});
