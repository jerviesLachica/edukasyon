# Plan — Widget config preview fix + photo background + tablet/phone adaptation

Date: 2026-09-14 · Lane: ulw-plan (reviewed plan, no implementation until accepted)
Evidence: screenshot image_03f5d7.png + direct source reads (WidgetConfigActivity.kt, WidgetRenderer.kt, WidgetConfigStore.kt, WidgetUpdateManager.kt, schedmate_widget_info.xml, widget_v2_root.xml, widget_v2_shell_bg.xml, ui/adaptive/*).

## Bug findings (observed in code)

### B1 — Picked photo silently vanishes (nothing is added as background)
`WidgetConfigActivity.copyAndScalePhoto()` (line ~360) calls
`contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` inside the try.
`ActivityResultContracts.GetContent()` (ACTION_GET_CONTENT) does **not** grant persistable
permission → this throws → the outer `catch (_: Exception) { null }` swallows it → `photoPath`
is never set → "Pick photo" stays "Pick photo", no preview, nothing saved, no crash.
This is why "the image doesnt add it as background".

### B2 — Preview card cannot render the saved photo even when a path exists
`WidgetPreviewCard` loads `Uri.parse(photoPath)` where photoPath is an absolute file path
(`/data/...`). A schemeless Uri is not loadable by Coil 2.7 `rememberAsyncImagePainter` →
error painter → blank. Must use `File(path)` (or `Uri.fromFile`).

### B3 — Preview geometry ≠ real widget geometry
- Preview: `fillMaxWidth().height(180.dp)`, **square-corner Box**, flat `color1` fill.
- Real widget: ~2x2 cells (minWidth/minHeight 110dp in `schedmate_widget_info.xml`),
  backgrounds are generated bitmaps rounded to **20dp** (`widget_v2_shell_bg.xml` and
  `WidgetBackgroundGenerator` "Round corners (20dp…)" step), and the launcher clips to its
  own squircle too (the pinned widget in the screenshot shows pronounced rounding).
Fix: preview becomes an approximately-square card (1:1 for SMALL_2X2, 2:3 for tall) capped in
width, with `RoundedCornerShape(20.dp)`, clipped photo + pattern layers, same header
proportions. This is the "same size and same border radius" ask.

### B4 — Photo never scales to the real rendered cell
`WidgetRenderer.paintBackground` hardcodes 160x160 / 160x240 budgets and the generator caps at
160px long edge. On tablets/expanded 2x2+ cells the centerCrop ImageView stretches a tiny
bitmap. Acceptable today; improvement = derive budget from `getAppWidgetOptions`
min/max width/height dp instead of constants (photo + preset both benefit).

### B5 — Tablet/phone adaptation gaps
- Manifest main activity already `resizeableActivity="true"` + full `configChanges`. Good.
- `AdaptiveScaffold` (bottom bar on Compact, NavigationRail on Medium/Expanded) is wired at
  the root. Good.
- Missing on the 4 screens with no `ui.adaptive` import:
  `AiConversationHistoryScreen`, `AuthGateScreen`, `ChangelogScreen`, `ScheduleScannerScreen`
  → they stretch edge-to-edge on tablets.
- `WidgetConfigActivity`'s Compose UI is a raw full-width Column → unusable on tablets.
- EMUI (Huawei) home screens honor the provider fields; no `maxResizeWidth/Height` declared,
  so pin-to-tablet grid sizing can undershoot. Declare max resize dims + keep `targetCell 2x2`.

## Options considered
- **A (chosen): targeted fixes** — permission-free copy via plain `openInputStream` (transient
  grant is enough because we copy the bytes into app-private storage immediately), Coil
  `File` model, exact-geometry preview composable, adaptive containers on the 4 stragglers +
  config screen, provider max-resize attrs, options-derived bg budget.
- B: switch picker to `OpenDocument` + real persistable permission — rejected: we already copy
  to private storage, persistable grants add failure modes with Huawei file managers for zero
  benefit.
- C: render preview with the actual RemoteViews (inflate + draw to bitmap) — rejected for now:
  heavier, lifecycle risk inside the configure activity; the hand-built composable matches
  1:1 once B3 is fixed. Can be a follow-up if pixel-exactness is demanded.

## Changes (file → what)
1. `widget/lifecycle/WidgetConfigActivity.kt`
   - `copyAndScalePhoto`: remove `takePersistableUriPermission` entirely; stream-copy from the
     transient grant; delete older `widget_bg_*.png` files when replacing (storage hygiene).
   - `WidgetPreviewCard`: aspect-correct card — width min(fillMaxWidth, 320.dp) centered,
     1:1 (or 2:3 when tall design), `clip(RoundedCornerShape(20.dp))`, preset pattern drawn via
     the same `WidgetBackgroundGenerator` bitmap (AsImagePainter(File)) instead of flat color,
     photo layer via `Image(painter = rememberAsyncImagePainter(File(path)))`, same paddings/
     text sizes ratio.
   - "Use design instead" currently only clears the in-memory path — keep the file copy but
     set photoPath null on save (already), plus delete the orphan file.
   - Wrap screen content in adaptive max-width container (360.dp centered on Medium+).
2. `widget/render/WidgetRenderer.kt` — `paintBackground`: budget from `getAppWidgetOptions`
   (min/max dp, capped 512) instead of 160/240 constants; keep 160 fallback.
3. `widget/WidgetBackgroundGenerator.kt` — accept the derived px budget (signature already
   takes w/h — verify cache key uses it; no behavior change on phones).
4. `res/xml/schedmate_widget_info.xml` — add `android:maxResizeWidth="240dp"`,
   `android:maxResizeHeight="360dp"` (and keep min 110 / targetCell 2x2).
5. Adaptive pass (small, mechanical): `AiConversationHistoryScreen`, `AuthGateScreen`,
   `ChangelogScreen`, `ScheduleScannerScreen` → `AdaptiveContentContainer` /
   `rememberAdaptiveHorizontalPadding` (pattern already used by sibling screens).

## Risks
- RemoteViews Binder limit: options-derived budget must stay capped (≤512px) — enforced.
- Removing takePersistableUriPermission is safe only because bytes are copied at pick time —
  verified copy happens before save/finish.
- Coil File model + cache: photo replaced → new timestamped filename → no stale cache. OK.
- Preview-vs-real mismatch can only be *approximate* (launcher clips too) — acceptance says
  "same shape/size/radius family", not pixel-identical.
- EMUI 2x2 cell metrics vary; budget derivation must fall back cleanly (runCatching already).

## Acceptance criteria
- AC1: Pick photo → button flips to "Photo selected — change" **on the first try**, photo is
  visible inside the preview card, and after Add the pinned widget shows the photo background.
- AC2: "Use design instead" → preview and widget revert to the preset pattern; the photo file
  is deleted.
- AC3: Preview card is square (2x2 case), corner radius 20dp, width-capped (~widget scale),
  centered; visually matches the pinned widget in size/rounding on the phone home screen.
- AC4: Reopening config for an existing widget restores type/design/photo (store roundtrip)
  with photo still rendering in the preview.
- AC5: After app kill + home-screen refresh (or reboot) the photo background still renders
  (file in app-private storage, no URI dependency).
- AC6: On a sw600dp+ device/emulator: the 4 previously-stretched screens use adaptive
  containers; the widget config screen is centered max-width; nav rail still present.
- AC7: `:androidApp:assembleDebug` green; backend suite unchanged 71/71 (no backend touch).

## Verification commands
- `./gradlew :androidApp:assembleDebug --no-daemon` → BUILD SUCCESSFUL
- `cd backend && npm test` → 71 pass (no regression)
- `grep -n takePersistableUriPermission -r androidApp/src` → **zero hits**
- `grep -n "RoundedCornerShape(20.dp)" widget/lifecycle/WidgetConfigActivity.kt` → ≥1
- `grep -n "maxResizeWidth" androidApp/src/main/res/xml/schedmate_widget_info.xml` → hit
- Device (Huawei 2GRYD24331004259, pending connection): pin widget, pick photo, verify
  AC1/AC3; force-stop app, update widget, verify AC5.

## Explicit non-goals
- No backend changes. No RemoteViews-in-preview (option C). No new design presets.
- No migration of existing widget configs (v2 store format unchanged).

## Lane shape (for later ultrawork if approved)
Single owner recommended: files 1–3 are one invariant (photo pipeline), 4–5 are mechanical but
small; splitting buys nothing and risks ViewModels-adjacent conflicts. One lane, ordered.
