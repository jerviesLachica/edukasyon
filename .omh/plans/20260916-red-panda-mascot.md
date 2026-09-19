# Red Panda Mascot — Animated Character for SchedMate

## Goal
Add the SchedMate red panda mascot as an animated character in the app, matching the style guide / logo. The mascot lives on the JEVI empty state and can be extended to other surfaces (home screen, loading states).

## Non-Goals
- No true 3D model viewer (filament/Sceneform) — minSdk 24, no native 3D runtime, APK size impact too high
- No mascot voice / dialogue system
- No animation authoring inside the app

## Evidence
- **Reference image:** `image_b4b99c.png` — 1254x1254 style guide with main mascot pose, 12 emotes, 10 action poses, 5 idle animation frames
- **Logo:** `ic_launcher_foreground.png` — 432x432 RGBA, dominant orange `(236,125,48)` = `#EC7D30`, teal `#80C0A0`, charcoal `#202020`
- **App theme:** Material 3, `Theme.Edukasyon` parent `Material.Light.NoActionBar`
- **Main tabs:** Home, Schedule, Planner, JEVI, Profile
- **Empty states:**
  - JEVI tab: `JeviScreens.kt:378` — `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)` with `EmptyState()`
  - Home tab: `HomeScreen.kt:164` — `DashboardEmptyState()`
- **No mascot/character code** exists in the codebase
- **No Lottie, Rive, or Filament** dependencies in `gradle/libs.versions.toml`
- `minSdk = 24`, `compileSdk = 34`

## Options

### Option A: Animated video overlay on logo image (SELECTED)
- Generate a looping MP4/WebP of the mascot's idle animation using AI video tools (Flux AI / Rewind.ai)
- Display via Compose `MediaPlayer` or `ImageDecoder` with the static logo PNG as fallback
- Add Compose animation overlays on top (tap bounce, speech bubble, ear twitch via `Modifier` transforms)
- **Pros:** Brand-exact, fast to produce, small APK impact (~20-50KB for short clip), no new dependencies
- **Cons:** Fixed animation (can't change motion at runtime), can't recolor parts dynamically, video player adds Media3 dependency

### Option B: Pure Compose vector mascot (REJECTED earlier)
- Draw red panda using Compose `Canvas` + vector paths
- Animate with `InfiniteTransition`, `animate*AsState`
- **Pros:** Dynamic recoloring, tiny size, interactive parts
- **Cons:** Cannot match the polished logo artwork — rough SVG paths look amateur vs. original

### Option C: Lottie animation (REJECTED)
- Commission/create Lottie JSON animation
- **Pros:** Rich animations, designer-friendly
- **Cons:** New dependency (~500KB), needs Lottie file sourced/created, overkill for one mascot

## Implementation Plan

### Phase 1: Asset Generation
- Use Flux AI (fluxai.pro) or Rewind.ai to generate a 3-5 second idle loop from `ic_launcher_foreground.png`
- Prompt: "Animate this red panda mascot over a calendar planner — gentle body bob, ear twitches, tail wag, eye blinks, pencil wiggle. Flat vector cartoon style, solid cream background (#F5E6D3), seamless loop."
- Output: 512x512 or 1024x1024 MP4/WebP, <500KB
- Save to `androidApp/src/main/res/raw/red_panda_idle.mp4` (or `.webp`)
- Fallback: keep `ic_launcher_foreground.png` as static image

### Phase 2: Compose Mascot Component
- Create `RedPandaMascot.kt` in `ui/components/`
- Display generated video with `androidx.media3:media3-exoplayer` + `PlayerView` (media3 is already used for audio in `AudioOverviewManager.kt`)
- If video fails to load, fall back to `Image painter` with static PNG
- Size: 160dp x 160dp (empty state), 120dp (compact)
- Add tap interaction: `clickable` modifier → bounce animation (scale 1.0 → 1.12 → 1.0)
- Specify `contentDescription` for accessibility

### Phase 3: Placement
- **JEVI empty state:** Replace static `EmptyState()` at `JeviScreens.kt:378` with `RedPandaMascot` + title/subtitle text
- **Home empty state (optional):** Add small mascot above `DashboardEmptyState()` at `HomeScreen.kt:164`
- Respect `LocalAccessibilityManager` — if reduce-motion is enabled, show static PNG without overlays

### Phase 4: Polish
- Add haptic feedback (`LocalHapticFeedback`, `HapticFeedbackType.LongPress`) on tap
- Add subtle breathing scale animation via `animateFloatAsState` overlay (independent of video)
- Seasonal variant hook (future): swap video asset during graduation/exam seasons

## Acceptance Criteria
- [ ] Mascot videoplays on JEVI empty state without crashing
- [ ] Video loops seamlessly (3-5 second cycle)
- [ ] Tap triggers bounce + haptic feedback
- [ ] Static PNG fallback works if video fails to load
- [ ] APK size increase < 100KB (video asset + media3 dependency already present)
- [ ] Build passes: `./gradlew :androidApp:assembleDebug`
- [ ] Mascot respects reduced-motion accessibility (shows static image)
- [ ] User reports the mascot "looks like the logo" when testing on device

## Verification Commands
```bash
cd C:/Users/HP/AndroidStudioProjects/edukasyon
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:testDebugUnitTest
```

## Risks
- **Video codec support:** H.264 MP4 is safe for minSdk 24. Avoid HEVC.
- **APK size:** 500KB video is fine; monitor with `analyzeDebugApkSize`
- **Media3 dependency:** Already present via `AudioOverviewManager.kt` — no new transitive deps
- **Video quality:** AI-generated video may not perfectly match logo. Mitigate with prompt iteration (generate 3-5 versions, pick best)

## Asset Sources
- Static logo: `androidApp/src/main/res/drawable-nodpi/ic_launcher_foreground.png`
- Style guide reference: user-shared `image_b4b99c.png` (not committed to repo)
- Video: generated via Flux AI / Rewind.ai (no CC required)

## Handoff
After plan acceptance, implement via `ultrawork` single-owner lane (one developer, one feature, bounded scope). video asset must be provided by user or generated before Phase 2 begins.
