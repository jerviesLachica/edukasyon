# Widget Task Display & Checkbox Fix Plan

## Goal
Fix the widget so tasks appear immediately after creation (without requiring checkbox toggle), and ensure the widget checkbox works like Google Calendar's inline checkboxes.

## Root Cause Analysis

### Bug: Tasks don't appear until checkbox toggle
The widget reads from a **snapshot cache** (`WidgetSnapshotCache`). When `notifyDataChanged()` is called after task creation, it enqueues a WorkManager job but **does NOT invalidate the cache**. The next `provideGlance()` call reads the **stale cached snapshot** (which has empty tasks) instead of loading fresh data from Room DB.

When the user toggles the checkbox, `ToggleTaskActionCallback` writes to Room DB first, then calls `notifyDataChanged()`. The WorkManager job then runs `loadSnapshotFresh()` which reads the updated Room DB and writes a fresh snapshot to cache. The subsequent `refreshAll()` triggers `provideGlance()` which now reads the fresh cache.

**The fix:** Re-enable cache invalidation in `notifyDataChanged()`, and ensure `provideGlance()` eagerly loads from Room DB when cache is null (which we already implemented). This way:
1. Task created → `notifyDataChanged()` invalidates cache
2. Widget re-renders → cache is null → eagerly loads from Room DB → renders real data immediately

### Bug: Skeleton shows on first widget placement
Already fixed in previous commit (`4b7ee82`) — `provideGlance()` now eagerly calls `loadSnapshotFresh()` when cache is null.

## Plan

### Step 1: Re-enable cache invalidation in `notifyDataChanged()`
**File:** `WidgetUpdater.kt`
**Change:** Add `WidgetSnapshotCache.invalidate(context)` back to `notifyDataChanged()`

This ensures that when any data change triggers a widget refresh, the next `provideGlance()` reads fresh data from Room DB instead of stale cache.

**Why this is safe now:** We already have eager DB loading in `provideGlance()` when cache is null, so the widget will show real data immediately instead of skeleton.

### Step 2: Verify `provideGlance()` eager loading handles the cache-miss correctly
**File:** `StudentAiWidget.kt`
**Status:** Already implemented in commit `4b7ee82`. The code already:
- Checks if cache exists
- If cache is null, calls `loadSnapshotFresh()` eagerly
- Falls back to skeleton only if DB is truly empty or an exception occurs

**No changes needed** — just verify the flow works end-to-end.

### Step 3: Add diagnostic logs to trace the full lifecycle
**Files:** `WidgetUpdater.kt`, `WidgetRefreshWorker.kt`, `StudentAiWidget.kt`
**Status:** Already implemented in commit `4b7ee82`. Logs include:
- `WIDGET_INIT` — widget initialization started
- `WIDGET_LOCAL_SNAPSHOT_READ` — cache hit or miss
- `WIDGET_FIRST_RENDER` — initial render with data counts
- `WIDGET_BACKGROUND_SYNC_START` — WorkManager job started
- `WIDGET_BACKGROUND_SYNC_COMPLETE` — WorkManager job finished
- `WIDGET_REFRESH` — `refreshAll()` called
- `WIDGET_REFRESH_REASON` — why the refresh was triggered

### Step 4: Build and verify
**Command:** `./gradlew.bat :androidApp:assembleDebug`
**Verification:** Install APK, add widget to home screen, verify tasks appear immediately without any interaction.

## Acceptance Criteria

1. ✅ Widget shows tasks immediately when first placed on home screen
2. ✅ Widget shows tasks immediately after creating a new task in the app
3. ✅ Widget checkbox toggles task completion status
4. ✅ Widget checkbox state reflects in the app's planner screen
5. ✅ No skeleton/loading state visible after initial DB read completes
6. ✅ Background sync still runs for Firebase data
7. ✅ All diagnostic logs are present and traceable via `adb logcat -s WidgetLifecycle`

## Risks

- **Cache invalidation + eager reload:** If the Room DB read is slow (>500ms), the widget might briefly show skeleton. Mitigation: Room queries are typically <50ms on local DB.
- **WorkManager race condition:** If `notifyDataChanged()` is called rapidly (e.g., multiple task edits), `ExistingWorkPolicy.REPLACE` ensures only one job runs at a time. No issue expected.
