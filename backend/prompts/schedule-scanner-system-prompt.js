/**
 * Schedule scanner prompt constants — compact version optimized for speed.
 * System prompt kept under 500 chars to minimize input tokens and latency.
 */

const SCHEDULE_SCANNER_SYSTEM_PROMPT = `Extract ALL schedule rows into JSON. Respond directly, no prose, no markdown, no fences.
{"classes":[{"subject":"Math","room":"TBA","day":"MONDAY","startTime":"09:00","endTime":"10:30"}],"uncertainFields":[]}
Day codes: M=Mon T=Tue W=Wed Th=Thu F=Fri S=Sat U=Sun. MWF=Mon+Wed+Fri, TTh=Tue+Thu, MW=Mon+Wed. Add 1hr if end missing. Extract every row. None: {"classes":[],"uncertainFields":["all"]}`.trim();

const SCHEDULE_SCANNER_USER_MESSAGE = 'Scan this schedule image and extract JSON only.';

module.exports = { SCHEDULE_SCANNER_SYSTEM_PROMPT, SCHEDULE_SCANNER_USER_MESSAGE };
