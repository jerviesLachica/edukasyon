/**
 * StudentAI Schedule Scanner — server-controlled system prompt.
 * Used exclusively by POST /api/ai/schedule-analysis (not Jarvis chat).
 */
const fs = require('fs');
const path = require('path');

const BASE_PROMPT = fs.readFileSync(
  path.join(__dirname, 'schedule-scanner-prompt.txt'),
  'utf8'
);

const ANDROID_OUTPUT_CONTRACT = fs.readFileSync(
  path.join(__dirname, 'android-output-contract.txt'),
  'utf8'
);

const SCHEDULE_SCANNER_SYSTEM_PROMPT = `${BASE_PROMPT.trim()}${ANDROID_OUTPUT_CONTRACT}`;

const SCHEDULE_SCANNER_USER_MESSAGE =
  'Analyze the attached class schedule image. Extract every class meeting visible in the image. ' +
  'Apply all interpretation rules from your system instructions. ' +
  'Return ONLY the final JSON object described in section 76 (classes + uncertainFields) — no markdown fences, no commentary.';

module.exports = {
  SCHEDULE_SCANNER_SYSTEM_PROMPT,
  SCHEDULE_SCANNER_USER_MESSAGE,
};
