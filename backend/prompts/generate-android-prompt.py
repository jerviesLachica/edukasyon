#!/usr/bin/env python3
"""Generate Android ScheduleScannerSystemPrompt.kt from backend prompt files."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROMPTS = Path(__file__).resolve().parent

base = (PROMPTS / "schedule-scanner-prompt.txt").read_text(encoding="utf-8").strip()
contract = (PROMPTS / "android-output-contract.txt").read_text(encoding="utf-8")
full = base + contract

out = ROOT / "androidApp/src/main/kotlin/com/edukasyon/studentai/core/ai/ScheduleScannerSystemPrompt.kt"
header = '''package com.edukasyon.studentai.core.ai

/**
 * Server-controlled schedule scanner system prompt (mirrors backend/prompts/).
 *
 * The Android app sends images to POST /api/ai/schedule-analysis; the backend applies this
 * prompt — it is NOT sent from the client. Kept here for reference, tests, and parity checks.
 *
 * Final JSON output must match [ScheduleAnalysisResponseDto] in AiApi.kt:
 * `{ "classes": [...], "uncertainFields": [...] }` with one row per day per class.
 */
object ScheduleScannerSystemPrompt {
    const val SCHEDULE_SCANNER_SYSTEM_PROMPT: String = """
'''

footer = '''
"""

    const val SCHEDULE_SCANNER_USER_MESSAGE: String =
        "Analyze the attached class schedule image. Extract every class meeting visible in the image. " +
        "Apply all interpretation rules from your system instructions. " +
        "Return ONLY the final JSON object described in section 76 (classes + uncertainFields) — no markdown fences, no commentary."
}
'''

out.write_text(header + full + footer, encoding="utf-8")
print(f"Wrote {out} ({len(full)} chars)")
