/**
 * Schedule scanner prompt constants.
 *
 * The system prompt is loaded from schedule-scanner-prompt.txt, which contains
 * the full training prompt used in v1.1.0 (170 lines). This was compacted in
 * an earlier commit to under 500 chars, but the compacted version lost critical
 * day-of-week parsing rules and multi-day expansion instructions, causing the
 * model to extract only one class. Restoring the original comprehensive prompt
 * fixes schedule extraction.
 */

const fs = require('fs');
const path = require('path');

// Load the full system prompt from disk so the prompt file can be edited
// independently without touching this JS wrapper.
const PROMPT_PATH = path.join(__dirname, 'schedule-scanner-prompt.txt');
let SCHEDULE_SCANNER_SYSTEM_PROMPT;
try {
  SCHEDULE_SCANNER_SYSTEM_PROMPT = fs.readFileSync(PROMPT_PATH, 'utf8').trim();
} catch (e) {
  // Fallback to compact prompt if the file is missing (e.g. during tests)
  SCHEDULE_SCANNER_SYSTEM_PROMPT = `Extract ALL schedule rows into JSON. Output ONLY raw JSON.\n{"classes":[{"subject":"Math","room":"TBA","day":"MONDAY","startTime":"09:00","endTime":"10:30"}],"uncertainFields":[]}\nMWF=Mon+Wed+Fri, TTh=Tue+Thu, MW=Mon+Wed. End missing? +1hr. None: {"classes":[],"uncertainFields":["all"]}`;
}

const SCHEDULE_SCANNER_USER_MESSAGE = 'Scan this schedule image and extract JSON only.';

module.exports = { SCHEDULE_SCANNER_SYSTEM_PROMPT, SCHEDULE_SCANNER_USER_MESSAGE };
