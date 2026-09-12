# Plan: Widget initial population + checkbox toggle fix

Status: ACCEPTED (2026-09-08)
Scope: `androidApp/.../widget/` only. No Room/DataStore/Firebase schema changes, no backend changes, no new presets, no artificial delays.

## Goals
1. Add widget -> stable base design renders immediately, latest cached data populates immediately, background sync runs separately. No app open, no task tap, no delay.
2. Checkbox tap toggles exactly that task and refreshes the widget. Tapping anywhere else opens the app.
3. One centralized update coordinator (`WidgetUpdater.refresh(reason)`) used by ALL events: first creation, onUpdate, restoration, task create/edit/delete/check/uncheck, schedule create/edit/delete, remote sync, time-boundary refresh.

## Non-goals
- No rewrite of Room/DataStore/Firebase setup; no DAO filter-semantics change without separate approval.
- No new design presets or UI redesign.
- No `DataSnapshot` leaking into UI layer; no third-party data transmission.

## Assumptions (visible, affect the plan)
- `taskDao.getUpcoming()` excludes COMPLETED/ARCHIVED by design; toggled-complete tasks disappearing from the list is expected query behavior, not the bug.
- Glance routes `ActionCallback` internally; the `<receiver>` manifest entry for `ToggleTaskActionCallback` is dead weight.
- RemoteViews fires one PendingIntent per tap region; nested clickables collide in favor of the outer one.

## PATH A vs PATH B (traced, current code)
- PATH A (widget added): `provideGlance` (StudentAiWidget.kt:46) -> cache-miss -> `loadSnapshotFresh` (writes cache) -> `provideContent` renders -> post-render block (lines 97-111) writes cache AGAIN then `return`s early; `notifyDataChanged` fires only when snapshot is fully empty. If DB read raced empty/skeleton, the widget parks on skeleton with no guaranteed second update (WorkManager timing is not immediate). Post-write invalidate-then-refresh also races the configure screen's own `updateAppWidget`.
- PATH B (checkbox tap): DB write first (`ToggleTaskActionCallback.kt:52`), then `notifyDataChanged` -> cache invalidate + WorkManager fresh-load + immediate `refreshAll` (WidgetUpdater.kt:74-92) -> `provideGlance` cache-miss -> eager fresh read hits the just-written DB -> renders populated. The DB-write-before-read ordering is why PATH B works.

## Root-cause hypotheses (ranked)
1. **Parent clickable swallows checkbox taps.** `WidgetRoot` Box carries `.clickable(openAction)` (WidgetUi.kt:53-57) wrapping the checkbox Box's own `.clickable(toggleTaskComplete)` (WidgetUi.kt:147-155) plus a second `.clickable(openAction)` on the title Column (lines 172-175). In RemoteViews the outer PendingIntent wins -> checkbox tap opens the app instead of toggling. Matches prior gotcha on taps bubbling to WidgetRoot.
2. **Bogus manifest receiver.** `ToggleTaskActionCallback` is an `ActionCallback`, not a `BroadcastReceiver`; the `<receiver>` entry (AndroidManifest.xml:114-121) cannot dispatch it and should be removed.
3. **Initial-population race.** Write-cache-then-`return` with conditional-only sync (StudentAiWidget.kt:95-111) plus `notifyDataChanged`'s invalidate + dual refresh paths (GlobalScope + WorkManager, WidgetUpdater.kt:74-92) = skeleton can stick with no deterministic follow-up render.
4. **Data-dependent appearance.** Device cache showed `tasks=0, schedule=3` at one point: with zero pending tasks there is no checkbox to tap. Verify with a pending task present; do not "fix" by altering DAO semantics.

## Changes (executor handoff scope)
1. Logging: `WIDGET_INIT_START / _DATA_READ / _RENDER / _COMPLETE` + `WIDGET_REFRESH_REASON={INITIAL_CREATION,TASK_CHANGED,SCHEDULE_CHANGED,REMOTE_SYNC,TIME_BOUNDARY}` on every coordinator call.
2. Coordinator: add `WidgetUpdater.refresh(reason)` (single path: fresh-load into cache for affected ids, then one update); route first-creation, onUpdate/restoration, task/schedule/board mutations, remote sync, time-boundary through it. Remove GlobalScope fire-and-forget; keep one immediate path + one debounced worker path without double invalidate.
3. `provideGlance` restructure: render cached-or-skeleton immediately; on cache-miss/empty/stale, enqueue background `refresh(INITIAL_CREATION)` AFTER first paint; never invalidate-after-write in the same pass.
4. Checkbox routing: scope `openAction` off the widget root (apply only to non-interactive header/row areas); checkbox Box keeps the sole `toggleTaskComplete(taskId)` clickable with a >=24dp target; remove manifest `<receiver>` for the callback; add callback entry/exit + task-found/not-found logs.
5. Toggle callback keeps DB-write-then-`refresh(TASK_CHANGED)` ordering (the PATH B property that works).

## Acceptance criteria
- Add widget -> base design paints -> cached schedule/tasks populate with zero interaction, zero app open, zero artificial delay.
- Tap checkbox -> only that task flips, widget updates, app does NOT open; log shows `WIDGET_REFRESH_REASON=TASK_CHANGED`.
- Tap header/row body -> app opens (deep link intact).
- Every listed event routes through `WidgetUpdater.refresh(reason)`; logs show the matching reason tag.
- `assembleDebug` clean; no new lint/verification errors.

## Verification shape (smallest proof)
- On-device via adb on 2GRYD24331004259: clear logcat, add widget, expect `INIT_START -> DATA_READ -> RENDER -> COMPLETE -> REFRESH_REASON=INITIAL_CREATION -> populated render`.
- Create one pending task, tap its checkbox: expect `TASK_CHANGED`, no MainActivity launch, state flips; tap again flips back.
- Tap row body: expect app opens.
- Screenshot before/after for the design-render check.

## Rejected options
- Arbitrary post-add delay: banned by the request; masks the race instead of fixing ordering.
- Second parallel refresh implementation: request explicitly says reuse the working toggle-path mechanism.
- Silently widening `getUpcoming` to include COMPLETED: changes product semantics; needs separate decision.
- Full widget/repo rewrite: violates reuse-existing-conventions constraint.

## Handoff
- Executor: single prepared coding change in `widget/` (~4 files + manifest cleanup). Recommended follow-on: direct executor handoff after explicit go-ahead.
