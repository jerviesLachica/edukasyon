/**
 * Schedule scanner prompt constants — compact version optimized for speed.
 * System prompt kept under 500 chars to minimize input tokens and latency.
 */

const SCHEDULE_SCANNER_SYSTEM_PROMPT = `Respond DIRECTLY with valid JSON only. Extract classes from schedule image.
Schema: {"classes":[{"subject":"CS101","teacher":"Prof. Santos","room":"301","day":"MONDAY","startTime":"08:00","endTime":"09:00"}],"uncertainFields":[]}
Days: M=Mon T=Tue W=Wed Th=Thu F=Fri S=Sat U=Sun. MW=Mon+Wed, MWF=Mon+Wed+Fri, TTh=Tue+Thu, TF=Tue+Fri. R=Thu only if legend confirms.
Image is source of truth. Never invent data. Times: 24h HH:MM. If only start visible, add 1hr. Expand multi-day codes. Never invent.`;

const SCHEDULE_SCANNER_USER_MESSAGE = 'Scan this schedule image and extract all class meetings into JSON format with classes array and uncertainFields.';

module.exports = { SCHEDULE_SCANNER_SYSTEM_PROMPT, SCHEDULE_SCANNER_USER_MESSAGE };
