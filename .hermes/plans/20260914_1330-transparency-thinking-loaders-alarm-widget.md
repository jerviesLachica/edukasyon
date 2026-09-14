# Plan: Transparency + Thinking + Loaders + Alarm Sound + Widget Preview

Scope: `C:/Users/HP/AndroidStudioProjects/edukasyon` (package `com.edukasyon.studentai`). No code changed in this step.

## 1. Observed facts
- Bottom bar: `AdaptiveScaffold` (Compact) hosts `StudentAiBottomBar` → `PillTabBar`. `HorizontalPillTabBar` wraps content in `Surface(color = MaterialTheme.colorScheme.background)` (PillTabBar.kt:168-172) + inner pill `colors.container` (white / surfaceContainerHigh). The outer Surface is the black band in pic 1, not the pill itself.
- Jevi thinking: `JeviReasoningSection` (GizmoCompanionBar.kt:377, `initiallyExpanded=false`, default closed) + `GizmoChatBubble` already renders it above the answer when `msg.reasoning` non-blank. Loading path (`AiScreen.kt:380-384`) shows only `JeviThinkingIndicator()` (static "Thinking" `GeneratingLoader` Compact) with no reasoning content. So the collapsible exists for completed answers; streaming/live thinking is the gap.
- Loaders: `GeneratingLoader` (Full/Compact) is the app-wide loader (`LoadingScreen`, `AiLoadingIndicator`, `JeviThinkingIndicator`, Focus/Assignment screens) + stray `CircularProgressIndicator` in JeviScreens/SourcePicker/Auth/Focus. Uiverse CSS for curly-goose-54 / rare-pug-90 is JS-gated — `web_extract` returned metadata only, no CSS. Evidence gap: exact keyframes must be read in a browser at implementation time; plan assumes MIT-licensed re-implementation in Compose, not copied CSS.
- Alarm: `ReminderScheduler` (WorkManager) → `ReminderWorker` → `NotificationHelper.showReminder`. Channels created once in `NotificationHelper.createChannels()` with fixed `DEFAULT_ALARM_ALERT_URI`; sound toggle only (`notificationSoundEnabled`). No per-reminder sound, no picker, no custom MP3 path.
- Widget V2 (current, do-not-break): `WidgetConfig` (size/displayType/accent/designPreset/3 colors) → `WidgetConfigStore` (pipe-encoded `a|b|c|d|e|f|g`, commit-on-write, memory mirror; disk is truth) → `WidgetUpdateManager.refresh()` → RemoteViews `WidgetRenderer`. Config UI: `WidgetConfigActivity.V2ConfigScreen` (type + design chips, save → refresh → RESULT_OK + 12s rebind safeguard). 5 presets in `WidgetModels.kt` (MINIMAL light, 4 dark).

## 2. Plan by workstream (selected option + rejected)
1. **Transparent bottom bar** — Make outer `Surface` + Scaffold bottom-bar slot transparent; keep inner pill opaque (with slight elevation/shadow). Rejected: making the whole pill transparent (hurts contrast over StarfieldScaffold) and edge-to-edge transparent Scaffold everywhere (touches rail/tablet layouts).
2. **Collapsible thinking, default closed** — Reuse `JeviReasoningSection(initiallyExpanded=false)`. Change `JeviThinkingIndicator(reasoning: String?)` to show live reasoning inside the same collapsed component while streaming, then the completed `reasoning` continues in `GizmoChatBubble`. Rejected: new separate "thinking card" (duplicates existing component) and default-open (user explicitly wants closed).
3. **App-wide loader swap** — One new `StudentAiLoader` composable (Uiverse-inspired: book/page-flip motif from curly-goose-54 as primary, rare-pug-90 circular variant as fallback), tinted from `MaterialTheme.colorScheme.primary` (= user-chosen color), with reduced-motion fallback. Replace `GeneratingLoader` call sites + the 6 stray `CircularProgressIndicator` usages. Rejected: WebView/CSS embed (heavy, offline-first violation) and per-screen bespoke spinners (fragmentation).
4. **Custom alarm sound (MP3 + presets)** — `UserPreferences`: `alarmSoundUri: String?` + `alarmSoundName`. Settings row: preset list (System default + 3-4 bundled `res/raw`) + "Pick MP3" (`ActivityResultContracts.GetContent("audio/*")`, persist URI permission). Playback: if sound enabled and URI set → delete/recreate versioned `NotificationChannel` with that sound (channels are immutable after creation) or use `MediaPlayer` full-screen alarm path for exact MP3; fallback to default on missing file. Pass sound key through `ReminderWorkerKeys` only if per-reminder override is wanted (default: global). Rejected: per-channel-per-reminder matrix (channel explosion) and storing MP3 bytes in prefs/DB (copy URI, don't duplicate).
5. **Widget live preview + custom background photo** — Extend `V2ConfigScreen`: preview card rendering the selected preset's colors/type with sample tasks (Compose-only, since real widget is RemoteViews), plus "Custom photo" picker (persist `content://` URI + permission; downscale to ≤512px, store under app files, reference path in config). Extend `WidgetConfig` with `backgroundImagePath: String?` (+ store encode v2 with backward-compat decode), render via `RemoteViews ImageView` background in `WidgetRenderer`; fallback to preset gradient when image missing. No changes to `WidgetUpdateManager.refresh()` ordering or `TaskToggleLocks`/tap handling. Rejected: Glide-in-RemoteViews / adaptive live preview of the real AppWidget (launcher-owned, not renderable in-config) and replacing the store format wholesale (breaks existing pinned widgets).

## 3. Risks
- Transparency over scrolling content reduces pill legibility → keep pill opaque, verify in dark/light + Starfield on.
- Channel immutability: changing sound without versioned channel silently does nothing → must version/delete-recreate channels on sound change.
- Custom MP3 URI permission loss / file deleted → always fallback to default sound, never crash the worker.
- Widget `RemoteViews` can't do arbitrary Compose: photo must be a downscaled Bitmap in an ImageView; oversized images = binder failure → cap size, catch exceptions, fallback to preset.
- Store format change breaks existing widgets → v2 decode must accept old 7-field strings.

## 4. Acceptance criteria
- [ ] Bottom bar outer band transparent on Home/Schedule/Planner/Jevi/Profile; pill itself still readable in light + dark.
- [ ] Jevi streaming shows collapsed "Reasoning" that expands on tap; completed answer keeps reasoning collapsed by default.
- [ ] Every loading state uses the new themed loader in user primary color; no `GeneratingLoader`/bare spinner remains on the touched screens; reduced-motion shows static frame.
- [ ] User can pick an MP3 and hear it on the next scheduled reminder; preset selection also works; missing-file falls back to default.
- [ ] Config screen shows preset preview before Add; custom photo appears as widget background; existing pinned widgets keep working (toggle taps, midnight refresh, reboot).

## 5. Verification
- `./gradlew.bat :androidApp:assembleDebug`
- `cd backend && npm test` (untouched, sanity)
- Manual: bottom-bar over scroll; Jevi ask → expand reasoning; airplane-mode loader tint; schedule test reminder with MP3; pin widget → preview matches → reboot → still renders.

## 6. Handoff
No implementation until user says go. Suggested follow-on: single-owner build (one executor, no commit/push per standing convention), then manual QA report. Uiverse keyframes must be visually confirmed in-browser at build time (evidence gap above).
Note: prior HUD todo "Ultrawork: deck-local tutor" (deck thread + migration + opt-out) is unrelated to this plan and stays open under its own owner; this plan's todo replaces the panel for this session only.
