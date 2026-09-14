# Widget V2 merge note (fix/widget-fast-load → master)

## What V2 is
Commit `68d1a4a` (branch `fix/widget-fast-load`, NOT on master) rewrites the
home-screen widget from Glance (`StudentAiWidget.kt`, `WidgetUi.kt`, …) to
classic RemoteViews (`widget/lifecycle|render|store|update/*`, `worker/*`).

## Why a full merge is wrong
`fix/widget-fast-load` predates master HEAD `981e335`. A full
`git merge --no-commit origin/fix/widget-fast-load` produces 15 conflicting
files OUTSIDE the widget (AiModelRouter, Enums, backend/server.js,
WebSearchService, JeviChatInput, ViewModels, SourcePickerRow, render.yaml…)
whose master versions are NEWER (Zen Flash, orange theme, citation gating).
Confirmed 2026-09-14 in worktree `edukasyon.merge-v2`.

## Correct vehicle: widget-only port (done, uncommitted, in edukasyon.merge-v2)
Ported paths from `68d1a4a`, deletions included:
- `widget/` — deleted 11 Glance files, added `lifecycle|render|store|update/`
  + WidgetConfig, WidgetSnapshotBuilder, WidgetRealtimeObserver,
  WidgetToggleTracker, WidgetTapDiagnostics, TaskToggleLocks
- `res/layout/` — deleted `widget_loading.xml`, `widget_preview.xml`;
  added `widget_v2_*.xml`
- `res/xml/` — `studentai_widget_info_2x2.xml` → `schedmate_widget_info.xml`;
  deleted `studentai_widget_info_2x3.xml` (V2 ships ONE provider, no 2x3)
- `res/values/strings.xml` — added `widget_v2_label/description`
  (master-untouched since merge-base, safe)
- `AndroidManifest.xml` — branch version (diff vs master is widget-only +
  RECEIVE_BOOT_COMPLETED)
- Callers rewired to V2 APIs (branch versions): StudentAiApplication,
  SyncWorker, HolidaySyncWorker, RepositoryImpls + new `worker/` files.
  Re-applied master's `preferences.ensureThemeOrangeMigrated()` (the one
  master-side line since merge-base).
- Stale-reference grep for all deleted Glance classes/layouts: ZERO hits.

## Pinned-widget orphan risk (user-visible)
- Provider authority changes (`studentai_widget_info_2x2` → `schedmate_widget_info`,
  2x3 provider removed). Existing pinned V1 widgets bind to receivers that no
  longer exist → they go stale/frozen after update, Android does NOT migrate them.
- Options: (a) accept + prompt user to remove old widgets and pin "SchedMate
  Tasks" fresh (simplest, recommended); (b) keep a shim Glance receiver so old
  widgets keep rendering (keeps Glance dep + dead code); (c) MY_PACKAGE_REPLACED
  sweep that deletes stale IDs + posts a re-pin prompt (code, still needs user).
- V2's own `WidgetV2BootReceiver` handles BOOT_COMPLETED / MY_PACKAGE_REPLACED /
  TIME_SET / TIMEZONE_CHANGED for the NEW provider only.

## Status
- Worktree `C:/Users/HP/AndroidStudioProjects/edukasyon.merge-v2` holds the
  uncommitted port. `assembleDebug` → **BUILD SUCCESSFUL** (2026-09-14).
- Extra ported pieces beyond widget/ (all branch→worktree, master-untouched
  since merge-base except where noted):
  - `data/local/dao/Daos.kt`: +`getRecentlyCompleted`, +`getByIds`,
    +`clampFutureCompletedAt`, +`clampFutureUpdatedAt` (V2 needs all four;
    `countUpcoming` skipped — nothing references it)
  - `MainActivity.kt`: `WidgetActions.*` → `WidgetRenderer.*` keys
    (WidgetActions died with the Glance file)
  - `StudentAiApplication.kt`: branch V2 wiring + re-applied master's
    `ensureThemeOrangeMigrated()` (the one master-side line since merge-base)
  - `SyncWorker` / `HolidaySyncWorker` / `RepositoryImpls`: branch V2
    `WidgetUpdateManager.refreshAllAsync` callsites
  - `WidgetSetupCard.kt`, `res/values/strings.xml`, `AndroidManifest.xml`:
    branch versions wholesale (widget-scoped diffs only)
- Do NOT commit to master until: user picks re-pin vs shim + Search-Sources
  UI lane decision + device verification (Huawei currently offline).
