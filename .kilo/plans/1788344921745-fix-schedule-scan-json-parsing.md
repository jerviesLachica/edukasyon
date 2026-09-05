# Plan: Fix Schedule Scan JSON Pipeline + Install MCPs (Render / GitHub / Firebase)

**Repo:** `C:/Users/HP/AndroidStudioProjects/edukasyon` (branch `master`, backend on Render `https://studentai-backend-ha0z.onrender.com`)
**Issue:** `step-3.7-flash` (via `https://api.hcnsec.cn/v1`) returns JSON in `reasoning`/`reasoning_content` with `content=""` when `response_format: {type:"json_object"}` is set. Result: `extractJson` throws `AI returned no parsable JSON` and scan fails. Test image: `C:/Users/HP/AppData/Roaming/Hermes/composer-images/composer_2026-09-02_06-51-53-125_fb8a13.png`. Expected: `HTTP 200 {"classes":[...],"uncertainFields":[]}`

---

## 0. Goals & Success Criteria

- **SC-1:** `POST /api/ai/schedule-analysis` with the test image returns HTTP 200 with valid `ScheduleValidator` payload, regardless of whether model puts JSON in `content` or `reasoning` (including prose+fenced JSON).
- **SC-2:** No regression on other AI endpoints (`/chat`, `/summarize`, `/flashcards`, `/quiz`, `/study-plan`, `/assignment-breakdown`, `/focus-plan`) -> `npm test` green.
- **SC-3:** Render / GitHub / Firebase MCPs installed and verified (tools callable from Kilo).
- **SC-4:** Deploy via `push to master` -> Render auto-redeploy verified via `/health` (`schedule-analysis.lastAt` or build timestamp updates within 5 min).

---

## 1. Context & Current State (DISCOVER summary)

- **Backend:** `backend/server.js:359-417` `handleScheduleAnalysis` now uses `ai.chatCompletion()` (keeps `reply`+`reasoning`) with `responseFormat: json_object, reasoning: 'none', temperature:0`. Fallback logic tries `ai.extractJson(completion.reply)` then `ai.extractJson(completion.reasoning)`.
- **Provider:** `backend/ai/AiProvider.js:187-208` `parseChatCompletionResult` already merges `providerReasoning` (`reasoning_content`/`reasoning`/`thinking`) + embedded reasoning tags, and falls back `reply = reasoning` when `reply` empty. `extractJson (259-294)` handles fenced ```json blocks and prose-prefix `{`...`}` via depth scanning.
- **Prompt:** `backend/prompts/schedule-scanner-system-prompt.js:6` current system prompt is compact (500 chars) but not strict enough. Still allows markdown/prose. Example: `Respond DIRECTLY with valid JSON only...` — model ignores under `reasoning` channel.
- **Still broken:** Vision model emits prose + fenced JSON in `reasoning` channel. Current `extractJson` + fallback handles it partially but not robustly for prose interleaved before/after JSON.
- **Infra:** `render.yaml` defines `studentai-backend` (rootDir `backend`, build `npm install`, start `npm start`, health `/health`) and static site. `.github/workflows/deploy.yml` is a no-op (Render webhook deploy). `firebase.json` hosts `download-site`.

---

## 2. Phase 1 — MCP Installation (Render, GitHub, Firebase)

> Kilo MCP config lives in `.kilo/kilo.jsonc` (or global `C:\Users\HP\.config\kilo\kilo.jsonc`). This repo currently has minimal `kilo.jsonc` (`{"$schema":"...","snapshot":false}`) — add `mcpServers` key. Requires restart of Kilo after write.

### 1.1 GitHub MCP
- **Package:** `@modelcontextprotocol/server-github` (official)
- **Prereq:** GitHub PAT with `repo`, `read:org` scopes. Generate at `https://github.com/settings/tokens/new`.
- **Config snippet:**
```jsonc
"github": {
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-github"],
  "env": { "GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_PAT}" }
}
```
- **Verify:** Tool `mcp_github_list_repos` / `mcp_github_create_issue` appears.

### 1.2 Firebase MCP
- **Package:** `firebase-mcp` (or `@gannonh/firebase-mcp` / `mcp-remote https://mcp.firebase.google.com/mcp`). For this repo (`edukasyon-studentai` per `.firebaserc`), use `firebase-tools` auth.
- **Prereq:** `firebase login` already done; ensure `FIREBASE_TOKEN` or service account.
- **Config snippet (option A - stdio):**
```jsonc
"firebase": {
  "command": "npx",
  "args": ["-y", "firebase-mcp"],
  "env": { "FIREBASE_TOKEN": "${FIREBASE_TOKEN}" }
}
```
- Alternative if using hosted MCP: `{ "command": "npx", "args": ["-y", "mcp-remote", "https://mcp.firebase.google.com/mcp"] }`
- **Verify:** `mcp_firebase_list_projects` shows `studentai-download` hosting target.

### 1.3 Render MCP
- **Package:** `@render/mcp` (community) or `mcp-remote https://mcp.render.com/mcp`. Render MCP requires `RENDER_API_KEY` (create at `https://dashboard.render.com/account/api-keys`).
- **Config snippet:**
```jsonc
"render": {
  "command": "npx",
  "args": ["-y", "@render/mcp-server"],
  "env": { "RENDER_API_KEY": "${RENDER_API_KEY}" }
}
```
- If package not found, use remote: `npx -y mcp-remote https://mcp.render.com/mcp --header "Authorization: Bearer ${RENDER_API_KEY}"`
- **Verify:** `mcp_render_list_services` shows `studentai-backend`.

### 1.4 Unified `.kilo/kilo.jsonc` example
```jsonc
{
  "$schema": "https://app.kilo.ai/config.json",
  "snapshot": false,
  "mcpServers": {
    "github": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-github"], "env": { "GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_PAT}" } },
    "firebase": { "command": "npx", "args": ["-y", "firebase-mcp"] },
    "render": { "command": "npx", "args": ["-y", "mcp-remote", "https://mcp.render.com/mcp"], "env": { "RENDER_API_KEY": "${RENDER_API_KEY}" } }
  }
}
```
- **Steps:** Edit `.kilo/kilo.jsonc`, set env vars in system, restart Kilo, run `Kilo: Show MCP Servers` to confirm 3 green.

---

## 3. Phase 2 — Schedule Scanner Resilience Fix

### Task 2.1 — Harden System Prompt (`backend/prompts/schedule-scanner-system-prompt.js`) [SMALL]
- **File:** `backend/prompts/schedule-scanner-system-prompt.js:6`
- **Problem:** Compact prompt does not explicitly forbid markdown fences, prose, or reasoning-channel leakage. `step-3.7-flash` with `reasoning: 'none'` still emits prose+JSON in reasoning.
- **Change:**
```js
const SCHEDULE_SCANNER_SYSTEM_PROMPT = `You are a schedule extractor. Output ONLY raw JSON, no markdown, no fences, no prose, no explanation.
Schema: {"classes":[{"subject":"CS101","teacher":"Prof. Santos","room":"301","day":"MONDAY","startTime":"HH:MM","endTime":"HH:MM"}],"uncertainFields":[]}
Rules: Days MONDAY-SUNDAY. M=Mon T=Tue W=Wed Th=Thu F=Fri S=Sat U=Sun. Expand MW->Mon+Wed, MWF->Mon+Wed+Fri, TTh->Tue+Thu, TF->Tue+Fri. R=Thu only if legend confirms. 24h HH:MM. If end missing, add 1hr. Image is source of truth. Never invent. If unreadable, return {"classes":[],"uncertainFields":["all"]}.`;
const SCHEDULE_SCANNER_USER_MESSAGE = 'Extract JSON only. No markdown.';
```
- **Constraint:** Keep < 600 chars (token budget) but add explicit `no markdown fences` and fallback empty case. Do NOT allow client override (already ignored in server.js:365).
- **Acceptance:** Prompt forces `content` JSON when tested via `hcnsec.cn` playground; reasoning fallback still works.

### Task 2.2 — Strengthen `extractJson` for prose-in-reasoning (`backend/ai/AiProvider.js:259-294`) [MEDIUM]
- **Current:** Handles single fenced block, then tries first `{` to last `}` with depth tracking, but fails if multiple JSON objects or prose interleaved, and does not strip leading reasoning prose robustly.
- **Fix (apply in `extractJson`):**
  1. Strip all markdown fences first (global): `text.replace(/```(?:json)?\s*([\s\S]*?)```/gi, '$1')`.
  2. Trim, then attempt direct `JSON.parse`.
  3. Fallback: find the largest valid JSON object containing `"classes"` key — scan for all `{...}` candidates via balanced brace parser (respect strings/escapes) and try `JSON.parse` each candidate, prefer one with `classes` array. This handles `prose { "classes": [...] } prose`.
  4. Keep throwing `AI returned no parsable JSON` only if no candidate parses.
- **Code sketch:**
```js
function extractJson(text) {
  if (typeof text !== 'string') throw new Error('extractJson received non-string input');
  let raw = text.trim();
  // Unwrap all fences, keep inner content
  raw = raw.replace(/```(?:json)?\s*([\s\S]*?)```/gi, (_, inner) => inner.trim());
  raw = raw.trim();
  try { return JSON.parse(raw); } catch {}
  // Balanced-brace scan for candidates containing "classes"
  const candidates = []; let depth=0, inStr=false, esc=false, start=-1;
  for (let i=0;i<raw.length;i++){ ... } // collect balanced objects
  for (const cand of candidates.sort((a,b)=>b.length-a.length)) { try { const p=JSON.parse(cand); if(p && Array.isArray(p.classes)) return p; } catch{} }
  // last resort: first { to last } slice
  const s=raw.indexOf('{'), e=raw.lastIndexOf('}'); if(s>=0 && e>s) try{return JSON.parse(raw.slice(s,e+1));}catch{}
  throw new Error('AI returned no parsable JSON');
}
```
- **Add unit tests** in `backend/tests/scheduleScanner.test.js` for: (a) pure JSON, (b) fenced JSON, (c) prose+fenced JSON, (d) prose+raw JSON, (e) reasoning empty content case.

### Task 2.3 — Ensure `handleScheduleAnalysis` uses both channels robustly (`backend/server.js:387-417`) [SMALL]
- **Current:** Tries `reply` then `reasoning`. Good but could also concatenate.
- **Improve:** If `reply` parses but yields empty `classes` while `reasoning` contains valid data, prefer non-empty. Implement helper `tryParse` and:
```js
let parsed = null; let lastErr;
for (const src of [completion.reply, completion.reasoning].filter(Boolean)) {
  try { const p = ai.extractJson(src); if (p && Array.isArray(p.classes) && p.classes.length) { parsed = p; break; } if (!parsed) parsed = p; } catch(e){ lastErr=e; }
}
if (!parsed) throw lastErr || new Error('AI returned no parsable JSON');
```
- Also log `completion.reply.slice(0,120)` and `completion.reasoning.slice(0,120)` on failure for debugging (no PII).

---

## 4. Phase 3 — Testing & Deployment

### 3.1 Local Verification (before push)
- Run `cd backend && npm test` — must pass `tests/scheduleScanner.test.js` + others.
- Manual test with test image:
```powershell
$img = [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:/Users/HP/AppData/Roaming/Hermes/composer-images/composer_2026-09-02_06-51-53-125_fb8a13.png"))
$body = @{ imageBase64=$img } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/ai/schedule-analysis" -Method Post -Body $body -ContentType "application/json" -Headers @{"X-Device-Id"="test-device"}
# Expect 200 with classes array
```
- Also test via Render staging if local AI key not set (mock mode returns mock classes — confirm fallback).

### 3.2 Deploy
- Commit: `git add backend/prompts/schedule-scanner-system-prompt.js backend/ai/AiProvider.js backend/server.js backend/tests/scheduleScanner.test.js`
- `git commit -m "fix(scan): force JSON-only prompt + harden extractJson for reasoning-channel output"`
- `git push origin master`
- Render auto-deploy ~2-5 min (verify in Render dashboard or via MCP `mcp_render_get_deploy_status`).

### 3.3 Post-deploy Verification
- `curl -s https://studentai-backend-ha0z.onrender.com/health | jq` — check `aiConfigured`, `visionModel`, timestamp.
- Live scan test:
```powershell
Invoke-RestMethod -Uri "https://studentai-backend-ha0z.onrender.com/api/ai/schedule-analysis" -Method Post -Body $body -ContentType "application/json" -Headers @{"X-Device-Id"="test-device"}
```
Expect `200 {"classes":[...],"uncertainFields":[]}`. If 500, inspect Render logs `mcp_render_get_logs --service studentai-backend --tail 100`.

### 3.4 Firebase/GitHub Verification (bonus)
- GitHub MCP: `gh release` not needed for backend fix, but verify `mcp_github_list_commits`.
- Firebase MCP: no hosting deploy needed for this fix (backend only) — verify `download-site` still serves `version.json`.

---

## 5. Risks & Mitigations

- **Prompt too strict -> model still emits reasoning:** Mitigated by `extractJson` hardening + dual-channel parse — prompt is defense-in-depth, not sole fix.
- **Token limit:** Keep system prompt < 650 chars to avoid vision cost blowup.
- **Provider rejects `response_format`:** `AiProvider.js:240` already retries without it — keep.
- **MCP package name drift:** If `npx` fails, fallback to `mcp-remote` URL; document in plan.

---

## 6. Implementation Order

1. Phase 1: Install & verify 3 MCPs (parallel, no code dependency)
2. Task 2.1: Prompt fix (1 file)
3. Task 2.2: `extractJson` hardening + tests (1 file + test file)
4. Task 2.3: `handleScheduleAnalysis` dual-parse (1 file)
5. Phase 3: Local test -> push -> Render verify

---

## 7. Files In Scope

- `backend/prompts/schedule-scanner-system-prompt.js`
- `backend/ai/AiProvider.js`
- `backend/server.js`
- `backend/tests/scheduleScanner.test.js`
- `.kilo/kilo.jsonc` (MCP config)

Out of scope: `androidApp/` (no client change needed; error surfacing already shipped).

---

## 8. References

- Troubleshooting handoff: `DEPLOY_SCAN_FIX_PROMPT.md` (prior v1.2.4), prior fix prompt for reasoning fallback.
- Test image path above, Render URL `https://studentai-backend-ha0z.onrender.com`, test model `step-3.7-flash` via `hcnsec.cn`.
