# Code Quality Control Report - edukasyon Project

## Summary

This report details the code quality issues identified during a thorough review of the edukasyon codebase as of 2026-08-27. **Update 2026-10-16**: every item below has been re-audited and resolved — see per-item statuses. Android + backend builds are green.

## Build Status

### Backend
- **Status**: ✅ PASSING
- **Tests**: 196/196 tests passing (full suite)
- **Last run**: 2026-10-16

### Android App
- **Status**: ✅ PASSING (2026-10-16) — `:androidApp:testDebugUnitTest` (164 tests, 0 failures) and `:androidApp:assembleDebug` both green. Former blocking errors below are resolved.

## Detailed Issues

### Critical Build Errors (Blocking Compilation)

#### 1. DocumentScanLauncher.kt - Lambda Return Issue
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/core/mlkit/DocumentScanLauncher.kt`
**Line**: 69
**Issue**: ✅ FIXED (2026-10-16) — incorrect use of labeled return in lambda; restructured with no early return
**Current Code**:
```kotlin
if (activity == null) {
    onError("Document scanner requires an Activity context")
    return@rememberDocumentScanLauncher
}
```
**Problem**: The lambda `return { ... }` has type `() -> Unit`, but `return@rememberDocumentScanLauncher` attempts to return from the outer function which has type `() -> Unit`. This creates a type mismatch.
**Fix**: Use `return@let` (the implicit name for the lambda) or restructure to avoid early return.

#### 2. ExamEditDialog.kt - Missing Icons Reference
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/ui/components/ExamEditDialog.kt`
**Lines**: 183, 183
**Issue**: ✅ FIXED (2026-10-16) — unresolved `ExpandLess`/`ExpandMore`; qualified as `Icons.Outlined.*`
**Current Code**:
```kotlin
Icon(
    if (showMoreDetails) ExpandLess else ExpandMore,
    contentDescription = null,
)
```
**Problem**: Missing `Icons.Outlined.` qualifier
**Fix**: Change to `Icons.Outlined.ExpandLess` and `Icons.Outlined.ExpandMore`

### Outstanding Technical Debt Items (From Previous Audit)

Based on session history in `.remember/today-2026-08-27.md`, the following 8 issues were identified but may not be fully resolved:

#### 3. Firebase Token Not Sent in Interceptor
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/core/network/AiSafetyHeadersInterceptor.kt`
**Status**: ✅ FIXED (verified 2026-10-16) — Firebase ID token attached to outgoing AI requests
**Issue**: Authentication token not being attached to outgoing AI requests
**Reference**: Memory entry from 20:55 on 2026-08-27

#### 4. DocumentScanLauncher Activity Cast Crash
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/core/mlkit/DocumentScanLauncher.kt`
**Status**: ✅ FIXED (verified 2026-10-16) — safe tailrec `findComponentActivity`, no Activity cast
**Issue**: Activity cast crash when finding component activity
**Reference**: Memory entry from 20:55 on 2026-08-27

#### 5. GradeCalculator NaN on maxScore=0
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/core/util/Utils.kt`
**Status**: ✅ FIXED (verified 2026-10-16) — div-by-zero/NaN guard in grade calculation
**Issue**: Division by zero when calculating grades
**Reference**: Memory entry from 20:55 on 2026-08-27

#### 6. Mappers Enum ValueOf Crashes
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/data/mapper/Mappers.kt`
**Status**: ✅ FIXED (verified 2026-10-16) — safe enum fallback via `entries.find {} ?: default`
**Issue**: Unsafe enum valueOf() calls causing crashes
**Reference**: Memory entry from 20:55 on 2026-08-27

#### 7. SubtaskEntity/NoteTagEntity Missing deletedAt Tombstone
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/data/local/entity/Entities.kt`
**Status**: ✅ FIXED (2026-10-16) — tombstone columns present; `MIGRATION_13_14` adds them to existing `subtasks`/`note_tags` tables (DB v14, PRAGMA-guarded)
**Issue**: Missing tombstone columns for soft delete implementation
**Reference**: Memory entry from 21:24 on 2026-08-27

#### 8. NoteDao REPLACE Cascade-Deletes Tags
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/data/local/dao/Daos.kt`
**Status**: ✅ FIXED (2026-10-16) — `NoteDao.insert` + `NoteTagDao.insert` switched from `REPLACE` to `@Upsert` (no cascade wipe of tags/subtasks)
**Issue**: REPLACE strategy causing unintended cascade deletions
**Reference**: Memory entry from 21:24 on 2026-08-27

#### 9. Sign-out Doesn't Clear Database
**File**: Multiple locations (likely Authentication/Firebase services)
**Status**: ✅ BY DESIGN (audited 2026-10-16) — offline-first app intentionally keeps local data on sign-out ("local data kept, cloud sync paused"); not a defect
**Issue**: User data not cleared on sign-out
**Reference**: Memory entry from 21:24 on 2026-08-27

#### 10. ReminderScheduler Late Firing
**File**: `androidApp/src/main/kotlin/com/edukasyon/studentai/core/notifications/ReminderScheduler.kt`
**Status**: ✅ FIXED (2026-10-16) — `ReminderScheduler` now uses `AlarmManager` exact alarms with `ReminderAlarmReceiver` (manifest-registered); WorkManager `setInitialDelay` no longer used for one-shot reminders
**Issue**: Late firing via setInitialDelay
**Reference**: Memory entry from 20:24 on 2026-08-27

## Backend Issues

### Security & Auth
- **AuthenticationService.js**: ✅ FIXED (per memory - critical auth bypass fixed with verifyIdToken)
- **AiSafetyGateway.js**: Awaits async authentication - tests passing

### Performance Optimizations
- **Schedule Scanner**: Prompt optimized (~170 lines → ~120 chars), tokens capped at 1500, reasoning disabled
- **Results**: 40/40 backend tests passing, Android assembleDebug build succeeded (when fixed)

## Recommendations

### Immediate Fixes (Blocking)
1. ~~Fix DocumentScanLauncher.kt lambda return issue~~ ✅ RESOLVED (2026-10-16)
2. ~~Fix ExamEditDialog.kt Icons references~~ ✅ RESOLVED (2026-10-16)

### Technical Debt Resolution
All 8 audit items resolved (verified 2026-10-16):
- ✅ Firebase token attachment in AiSafetyHeadersInterceptor
- ✅ Safe tailrec `findComponentActivity` (no Activity cast) in DocumentScanLauncher
- ✅ Div-by-zero/NaN guard in GradeCalculator (Utils.kt)
- ✅ Safe enum fallback (`entries.find {} ?: default`) in Mappers.kt
- ✅ `subtasks`/`note_tags` tombstone columns via `MIGRATION_13_14` (DB v14, PRAGMA-guarded)
- ✅ NoteDao/NoteTagDao `REPLACE` → `@Upsert` (no cascade deletes)
- ✅ Sign-out data retention confirmed by-design (offline-first)
- ✅ ReminderScheduler exact alarms (`AlarmManager` + `ReminderAlarmReceiver`)

### Preventive Measures
1. Add pre-commit hook to run lint/checks
2. Enable stricter compiler warnings in build.gradle.kts
3. Add unit tests for edge cases (division by zero, null enum values)
4. Consider implementing a technical debt tracking system

## Conclusion

All build-blocking issues and all 8 technical debt items are resolved as of 2026-10-16. Backend: 196/196 tests passing. Android: `:androidApp:testDebugUnitTest` (164 tests, 0 failures) and `:androidApp:assembleDebug` both green.

**Next Steps**: No outstanding audit items. Keep the test suites running with each change.