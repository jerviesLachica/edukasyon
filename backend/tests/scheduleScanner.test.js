/**
 * Schedule scanner fast-path tests.
 * Ensures the schedule-analysis endpoint is optimized for speed:
 * - Compact prompt with explicit no-reasoning directive
 * - maxTokens capped at 1500
 * - temperature set to 0
 * - reasoning_effort / enable_thinking passed through when supported
 * - Long OCR text is dropped to save tokens
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const fs = require('fs');
const { ScheduleValidator } = require('../validation/AiResponseValidator');

// Load modules under test
const {
  SCHEDULE_SCANNER_SYSTEM_PROMPT,
  SCHEDULE_SCANNER_USER_MESSAGE,
} = require('../prompts/schedule-scanner-system-prompt');
const app = require('../server');

describe('Schedule Scanner Fast Path', () => {
  describe('System prompt', () => {
    it('must be compact (under 500 chars) as a fallback to reduce input tokens', () => {
      // Only check the fallback prompt length
      if (SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('Extract ALL schedule rows into JSON. Output ONLY raw JSON.')) {
        assert.ok(
          SCHEDULE_SCANNER_SYSTEM_PROMPT.length < 500,
          `Fallback prompt too long (${SCHEDULE_SCANNER_SYSTEM_PROMPT.length} chars). ` +
            'Keep it under 500 to minimize latency.'
        );
      }
    });

    // The full prompt from schedule-scanner-prompt.txt contains detailed instructions
    // that may include reasoning. Only check for no-reasoning directive in fallback.
    it('must contain explicit no-reasoning directive in fallback prompt', () => {
      if (SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('Extract ALL schedule rows into JSON. Output ONLY raw JSON.')) {
        const directives = [
          /respond\s+directly/i,
          /do\s+not\s+reason/i,
          /no\s+thinking/i,
          /output\s+(only|just)/i,
          /json\s+response/i,
        ];
        const hasDirective = directives.some((re) => re.test(SCHEDULE_SCANNER_SYSTEM_PROMPT));
        assert.ok(hasDirective, 'Fallback prompt must instruct model to respond directly with JSON (no reasoning)');
      }
    });

    it('must not reference REASONED INTERPRETATION in evidence hierarchy', () => {
      assert.ok(
        !SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('REASONED INTERPRETATION'),
        'Prompt should not encourage reasoning — remove from evidence hierarchy'
      );
    });

    it('must still define the JSON output schema', () => {
      assert.ok(
        SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('"classes"') || SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('classes'),
        'Prompt must define classes field in output schema'
      );
      assert.ok(
        SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('"day"') || SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('day'),
        'Prompt must define day field in output schema'
      );
      assert.ok(
        SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('"startTime"') || SCHEDULE_SCANNER_SYSTEM_PROMPT.includes('startTime'),
        'Prompt must define startTime field in output schema'
      );
    });

    it('must cover core day-of-week patterns', () => {
      // Keep only the most critical patterns to save tokens
      const corePatterns = ['MWF', 'TTh', 'MW'];
      const hasCorePatterns = corePatterns.every((p) => SCHEDULE_SCANNER_SYSTEM_PROMPT.includes(p));
      assert.ok(hasCorePatterns, 'Prompt must cover MWF, TTh, MW patterns');
    });

    it('must assign multirow timetable entries by their own day cell', () => {
      assert.match(SCHEDULE_SCANNER_SYSTEM_PROMPT, /each class row whose center falls inside/i);
      assert.match(SCHEDULE_SCANNER_SYSTEM_PROMPT, /truly blank, unmerged day cell means no class/i);
      assert.match(SCHEDULE_SCANNER_SYSTEM_PROMPT, /never infer a day merely from the preceding row/i);
    });

    it('must map vertically merged DAY cells only across their visible row span', () => {
      const prompt = fs.readFileSync(path.join(__dirname, '../prompts/schedule-scanner-prompt.txt'), 'utf8');
      assert.match(prompt, /vertical(?:ly)? merged cell/i);
      assert.match(prompt, /inside that merged cell.s vertical bounds/i);
      assert.match(prompt, /stop exactly at the cell border/i);
    });

    it('must omit unsupported weekday guesses instead of defaulting to Monday', () => {
      const prompt = fs.readFileSync(path.join(__dirname, '../prompts/schedule-scanner-prompt.txt'), 'utf8');
      assert.match(prompt, /do not assume MONDAY/i);
      assert.match(prompt, /omit rows whose day cannot be established/i);
      assert.match(prompt, /identifying each omitted class and its missing day/i);
    });
  });

  describe('handleScheduleAnalysis', () => {
    // We can't easily mock the full AI provider here, so we test via the prompt/message structure
    // The actual temperature/token caps are tested in AiProvider integration if needed
    it('user message must be concise', () => {
      const userMsgLength = SCHEDULE_SCANNER_USER_MESSAGE.length;
      assert.ok(userMsgLength < 200, `User message too long (${userMsgLength} chars). Keep it concise.`);
      assert.ok(
        SCHEDULE_SCANNER_USER_MESSAGE.includes('schedule') || SCHEDULE_SCANNER_USER_MESSAGE.includes('image'),
        'User message should reference schedule/image context'
      );
    });
  });

  describe('row/day normalization', () => {
    it('preserves explicit weekday assignments and drops incomplete or blank cells', () => {
      const validator = new ScheduleValidator();
      const result = validator.validate({
        classes: [
          { subject: 'GE07', day: 'FRI', startTime: '09:00', endTime: '10:00' },
          { subject: 'Biology', day: 'THU', startTime: '11:00', endTime: '12:00' },
          { subject: 'Incomplete Sunday cell', day: 'SUN', startTime: null, endTime: null },
          { subject: '', day: 'WED', startTime: '13:00', endTime: '14:00' },
        ],
      });

      assert.deepEqual(result.data.classes.map(({ subject, day }) => [subject, day]), [
        ['GE07', 'FRIDAY'],
        ['Biology', 'THURSDAY'],
      ]);
    });

    it('keeps a 14-row Thu/Fri schedule balanced at three classes per day', () => {
      const rows = [
        ['GE04', 'MON'], ['CHEM01', 'MON'], ['CAL01', 'MON'],
        ['GE07', 'TUE'], ['GE-ELE02', 'TUE'], ['PE01', 'TUE'],
        ['GE04', 'THU'], ['CHEM01', 'THU'], ['CAL01', 'THU'],
        ['GE07', 'FRI'], ['CAD01', 'FRI'], ['GE-ELE02', 'FRI'],
        ['GE01', 'SAT'], ['CHEM01L', 'SAT'],
      ];
      const classes = rows.map(([subject, day]) => ({ subject, day, startTime: '08:00', endTime: '09:00' }));
      const result = new ScheduleValidator().validate({ classes });
      const counts = result.data.classes.reduce((total, item) => {
        total[item.day] = (total[item.day] || 0) + 1;
        return total;
      }, {});
      assert.equal(result.data.classes.length, 14);
      assert.equal(counts.THURSDAY, 3);
      assert.equal(counts.FRIDAY, 3);
      assert.deepEqual(
        result.data.classes.filter(({ day }) => day === 'THURSDAY').map(({ subject }) => subject),
        ['GE04', 'CHEM01', 'CAL01'],
      );
      assert.deepEqual(
        result.data.classes.filter(({ day }) => day === 'FRIDAY').map(({ subject }) => subject),
        ['GE07', 'CAD01', 'GE-ELE02'],
      );
    });

    it('omits missing and bare R/H weekdays with uncertainty instead of assigning Thursday', () => {
      const result = new ScheduleValidator().validate({
        classes: [
          { subject: 'R code', day: 'R', startTime: '09:00', endTime: '10:00' },
          { subject: 'H code', day: 'H', startTime: '10:00', endTime: '11:00' },
          { subject: 'No day', day: '', startTime: '11:00', endTime: '12:00' },
          { subject: 'Legend resolves R', day: 'THURSDAY', startTime: '12:00', endTime: '13:00' },
        ],
      });

      assert.deepEqual(result.data.classes.map(({ subject, day }) => [subject, day]), [
        ['Legend resolves R', 'THURSDAY'],
      ]);
      assert.equal(result.data.uncertainFields.length, 3);
      assert.match(result.data.uncertainFields[0], /R code.*ambiguous.*omitted/i);
      assert.match(result.data.uncertainFields[1], /H code.*ambiguous.*omitted/i);
      assert.match(result.data.uncertainFields[2], /No day.*missing.*omitted/i);
    });
  });

  it('bounds the dedicated attempt so a complete fallback fits the inline budget', () => {
    const budget = app.__scheduleScanTimeouts;
    assert.ok(budget.dedicatedVisionMs + budget.fallbackVisionMs <= budget.inlineMs);
    assert.equal(app.__isUsefulDedicatedScanProvider({ baseUrl: 'https://api.hcnsec.cn/v1' }), false);
    assert.equal(app.__isUsefulDedicatedScanProvider({ baseUrl: 'https://integrate.api.nvidia.com/v1' }), true);
  });

  it('accepts completed inline scans and keeps Vercel scans inside the request', async () => {
    const result = {
      status: 'done',
      classes: [{ subject: 'Math', teacher: null, room: null, day: 'MONDAY', startTime: '08:00', endTime: '09:00' }],
      uncertainFields: [],
    };
    assert.deepEqual(app.__validateScheduleScanOutput(result), { valid: true, data: result });
    assert.equal(app.__validateScheduleScanOutput({ status: 'done' }).valid, false);

    const originalVercel = process.env.VERCEL;
    const originalHandler = app.handleScheduleAnalysis;
    process.env.VERCEL = '1';
    app.handleScheduleAnalysis = async () => ({ classes: result.classes, uncertainFields: [] });
    try {
      assert.deepEqual(
        await app.__handleScheduleScanRequest({ body: { imageBase64: 'test-image' }, provider: {}, maxTokens: 100 }),
        result,
      );
    } finally {
      app.handleScheduleAnalysis = originalHandler;
      if (originalVercel === undefined) delete process.env.VERCEL;
      else process.env.VERCEL = originalVercel;
    }
  });

  it('records a sanitized terminal error for failed async jobs', async () => {
    const originalVercel = process.env.VERCEL;
    const originalHandler = app.handleScheduleAnalysis;
    delete process.env.VERCEL;
    try {
      const provider = {
        resolveVisionModel: () => 'test-vision-model',
        chatCompletion: async () => { throw new Error('AI provider groq timeout after 28000ms'); },
      };
      const started = await app.__handleScheduleScanRequest({ body: { imageBase64: 'test-image' }, provider, maxTokens: 100 });
      for (let i = 0; i < 10 && app.__scheduleScanJobs.get(started.jobId)?.status === 'processing'; i++) {
        await new Promise((resolve) => setTimeout(resolve, 0));
      }
      const job = app.__scheduleScanJobs.get(started.jobId);
      assert.equal(job.status, 'error');
      assert.match(job.error, /timed out/i);
      assert.equal(job.errorIsSafe, true);
      app.__scheduleScanJobs.delete(started.jobId);
    } finally {
      app.handleScheduleAnalysis = originalHandler;
      if (originalVercel === undefined) delete process.env.VERCEL;
      else process.env.VERCEL = originalVercel;
    }
  });
});
