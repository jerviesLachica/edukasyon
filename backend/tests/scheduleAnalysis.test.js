const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const app = require('../server');

const body = { imageBase64: 'data:image/png;base64,aGVsbG8=' };
const validClasses = {
  classes: [{ subject: 'Math', day: 'MONDAY', startTime: '09:00', endTime: '10:00' }],
  uncertainFields: [],
};

function fakeProvider(responses, model = 'test-model') {
  const calls = [];
  return {
    calls,
    provider: {
      resolveVisionModel: () => model,
      extractJson: (text) => JSON.parse(text),
      chatCompletion: async (_messages, options) => {
        calls.push(options);
        const response = responses.shift();
        if (response instanceof Error) throw response;
        return response;
      },
    },
  };
}

describe('handleScheduleAnalysis', () => {
  it('uses valid reply JSON when reasoning contains an empty class list', async () => {
    const dedicated = fakeProvider([{
      reasoning: '{"classes":[]}',
      reply: JSON.stringify(validClasses),
      model: 'dedicated',
    }], 'dedicated-model');
    const fallback = fakeProvider([]);

    const result = await app.handleScheduleAnalysis({
      body,
      provider: fallback.provider,
      scanProviderOverride: { provider: dedicated.provider, model: 'dedicated-model' },
      maxTokens: 1000,
    });

    assert.deepEqual(result.classes.map(({ subject }) => subject), ['Math']);
    assert.equal(fallback.calls.length, 0);
  });

  it('uses the main provider and its vision model after empty dedicated output', async () => {
    const dedicated = fakeProvider([{ reply: '{"classes":[]}', reasoning: null }], 'dedicated-model');
    const fallback = fakeProvider([{
      reply: JSON.stringify(validClasses),
      reasoning: null,
      model: 'main-vision',
    }], 'main-vision');

    const result = await app.handleScheduleAnalysis({
      body,
      provider: fallback.provider,
      scanProviderOverride: { provider: dedicated.provider, model: 'dedicated-model' },
      maxTokens: 1000,
    });

    assert.deepEqual(result.classes.map(({ subject }) => subject), ['Math']);
    assert.equal(dedicated.calls.length, 1);
    assert.equal(dedicated.calls[0].wireModelOverride, 'dedicated-model');
    assert.equal(fallback.calls.length, 1);
    assert.equal(fallback.calls[0].model, 'main-vision');
    assert.equal(fallback.calls[0].wireModelOverride, undefined);
  });

  it('uses the main provider after the dedicated provider throws', async () => {
    const dedicated = fakeProvider([new Error('dedicated provider unavailable')], 'dedicated-model');
    const fallback = fakeProvider([{
      reply: JSON.stringify(validClasses),
      reasoning: null,
      model: 'main-vision',
    }], 'main-vision');

    const result = await app.handleScheduleAnalysis({
      body,
      provider: fallback.provider,
      scanProviderOverride: { provider: dedicated.provider, model: 'dedicated-model' },
      maxTokens: 1000,
    });

    assert.deepEqual(result.classes.map(({ subject }) => subject), ['Math']);
    assert.equal(dedicated.calls.length, 1);
    assert.equal(fallback.calls.length, 1);
    assert.equal(fallback.calls[0].model, 'main-vision');
  });

  it('returns an empty class list when neither provider can read the image', async () => {
    const dedicated = fakeProvider([{ reply: '{"classes":[]}', reasoning: null }]);
    const fallback = fakeProvider([{ reply: '{"classes":[]}', reasoning: null }]);

    const result = await app.handleScheduleAnalysis({
      body,
      provider: fallback.provider,
      scanProviderOverride: { provider: dedicated.provider, model: 'dedicated-model' },
      maxTokens: 1000,
    });

    assert.deepEqual(result.classes, []);
  });
});
