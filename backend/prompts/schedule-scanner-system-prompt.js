/**
 * Schedule scanner prompt constants — compact version optimized for speed.
 * System prompt kept under 500 chars to minimize input tokens and latency.
 */

const SCHEDULE_SCANNER_SYSTEM_PROMPT = `Extract schedule to JSON ONLY. No prose, no markdown, no fences, no explanations.

Output must be a single JSON object with exactly these keys: "classes" (array) and "uncertainFields" (array). Nothing else before, after, or within.

Schema:
{"classes":[{"subject":"CS101","teacher":"Prof. Santos","room":"301","day":"MONDAY","startTime":"HH:MM","endTime":"HH:MM"}],"uncertainFields":[]}

Strict rules:
- Output ONLY the JSON object — no surrounding text of any kind
- Do NOT use code fences of any kind
- Do NOT add any introductory or concluding text
- Do NOT add any commentary, notes, or descriptions
- Do NOT use markdown formatting of any kind
- The JSON must be on its own line, with no leading or trailing spaces beyond what is needed for valid JSON
- If no schedule elements are detected, return exactly: {"classes":[],"uncertainFields":["all"]}

Image is truth — extract only what is actually visible. Never invent or hallucinate fields.

Example good output:
{"classes":[{"subject":"Math","teacher":"Prof.","room":"303","day":"MONDAY","startTime":"10:30","endTime":"11:30"}],"uncertainFields":[]}

Example bad output (will cause scan failure):
Here is the schedule: [fenced code block with JSON inside] Thoughts: ...
`.trim();

const SCHEDULE_SCANNER_USER_MESSAGE = 'Scan this schedule image and extract JSON only.';

module.exports = { SCHEDULE_SCANNER_SYSTEM_PROMPT, SCHEDULE_SCANNER_USER_MESSAGE };
