# Project Plan

Implement GitHub Releases Ecosystem for KuchuPuchi:

Part 1: In-App Auto-Updater & Post-Update Auto-Start
- Create `UpdateManager.kt` executed when FloatingService starts.
- Requests `https://api.github.com/repos/{GITHUB_OWNER}/kuchupuchi/releases/latest` with `Accept: application/vnd.github.v3+json`.
- Compares `tag_name` integer against `BuildConfig.VERSION_CODE`.
- Downloads matching flavor APK asset (e.g. `kuchu-release.apk` or `puchi-release.apk`).
- Silent APK installation using `PackageInstaller.Session` (Android 12+) with fallback to FileProvider `ACTION_VIEW` intent.
- Manifest permissions: `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `INTERNET`.
- BroadcastReceiver (`PackageReplacedReceiver.kt`) listening to `android.intent.action.MY_PACKAGE_REPLACED` to auto-restart `FloatingService` after app update.

Part 2: Custom Gradle Deployment Task (`deployToGitHub`)
- Custom Gradle task in `app/build.gradle.kts`.
- Increments `versionCode` in `app/build.gradle.kts`.
- Depends on `assembleRelease`.
- Reads `GITHUB_TOKEN`, `GITHUB_OWNER`, `GITHUB_REPO` from `local.properties`.
- Creates GitHub release via `HttpURLConnection` POST request (`https://api.github.com/repos/{owner}/{repo}/releases`).
- Uploads `kuchu-release.apk` and `puchi-release.apk` assets to the release.
- Logs build progress.

## Project Brief

# Project Brief: KuchuPuchi GitHub Releases Ecosystem

## Features
- **GitHub Release Auto-Updater**: Queries the GitHub REST API on `FloatingService` startup to check for newer releases and downloads matching flavor APK assets (`kuchu-release.apk` or `puchi-release.apk`).
- **Silent & Fallback APK Installation**: Performs seamless unattended updates using Android 12+ `PackageInstaller.Session` API with fallback to `ACTION_VIEW` FileProvider intents for legacy support.
- **Post-Update Auto-Restart**: Utilizes `PackageReplacedReceiver` listening for `MY_PACKAGE_REPLACED` broadcasts to automatically restart `FloatingService` immediately following an update.
- **Automated Deployment Gradle Task (`deployToGitHub`)**: Custom Gradle task that auto-increments `versionCode`, executes `assembleRelease`, creates GitHub Releases via REST API, and uploads flavor APK assets.

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Navigation & Adaptive Strategy**: Jetpack Navigation 3 (state-driven), Compose Material Adaptive library
- **Asynchronous & Core Utilities**: Kotlin Coroutines, Android Service (`FloatingService`), BroadcastReceiver (`PackageReplacedReceiver`)
- **Package & System Management**: Android `PackageInstaller` API, FileProvider (`REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`)
- **Build System & Automation**: Gradle Kotlin DSL (`build.gradle.kts`), GitHub REST API (via `HttpURLConnection`)

## Implementation Steps
**Total Duration:** 57m 39s

### Task_1_Setup_Flavors_Dependencies: Configure Gradle for 'kuchu' and 'puchi' product flavors. Add Firebase Realtime Database and necessary dependencies. Set up string resources and constants for flavor-specific paths.
- **Status:** COMPLETED
- **Updates:** Configured 'kuchu' and 'puchi' product flavors in build.gradle.kts, added Firebase DB dependencies, set up FlavorConfig, initialized Firebase in MainApplication, added permissions in AndroidManifest.xml, verified build for both kuchuDebug and puchiDebug.
- **Acceptance Criteria:**
  - Gradle syncs successfully with two build variants (kuchuDebug, puchiDebug)
  - Firebase dependencies are added
- **Duration:** 15m 50s

### Task_2_Permissions_Onboarding: Update AndroidManifest.xml with SYSTEM_ALERT_WINDOW, VIBRATE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS. Implement Compose MainActivity to request permissions and start the Foreground Service.
- **Status:** COMPLETED
- **Updates:** Implemented permissions flow in MainActivity.kt using Jetpack Compose for Overlay permission (Settings.canDrawOverlays) and POST_NOTIFICATIONS. Updated AndroidManifest.xml with specialUse foreground service attributes and created stub FloatingService. Verified build.
- **Acceptance Criteria:**
  - App requests required permissions on startup
  - Service starts successfully after granting permissions
- **Duration:** 2m 30s

### Task_3_Foreground_Service_Floating_UI: Implement the Foreground Service with a persistent notification. Create the floating overlay button using WindowManager, adding touch listeners for ACTION_DOWN and ACTION_UP.
- **Status:** COMPLETED
- **Updates:** Created ic_sun.xml, ic_moon.xml, ic_app_icon.xml vector drawables. Updated launcher icons. Configured FloatingService with persistent notification, WindowManager TYPE_APPLICATION_OVERLAY, touch listener handling touch slop drag and touch state updates, and proper cleanup. Verified build.
- **Acceptance Criteria:**
  - Foreground service runs without crashing
  - Floating button is visible on screen
  - Touch events (down/up) are accurately captured
- **Duration:** 2m 51s

### Task_4_Firebase_and_Vibration_Integration: Integrate Firebase Realtime Database using flavor-specific paths. Implement keepSynced(true) and onDisconnect().setValue(false). Bind touch events to Firebase writes. Implement continuous vibration reacting to the partner's node.
- **Status:** COMPLETED
- **Updates:** Integrated Firebase Realtime Database with keepSynced(true), onDisconnect().setValue(false), touch state updates, partner node listener, continuous vibration using VibrationEffect waveform repeating pattern and VibratorManager/Vibrator, and lifecycle cleanup. Added unit tests for FlavorConfig. Verified builds and tests.
- **Acceptance Criteria:**
  - Pressing button writes true to correct Firebase node, releasing writes false
  - Partner node changing to true triggers continuous vibration
  - Network disconnect handles state correctly via onDisconnect
- **Duration:** 2m 51s

### Task_5_Run_and_Verify: Instruct critic_agent to verify application stability, confirm alignment with user requirements, check the floating overlay functionality, Firebase connection, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic agent completed build and code quality verification. Unit tests passed, Gradle sync passed, all permissions, service setup, flavor icons, Firebase Realtime DB sync, onDisconnect logic, and continuous vibration engine verified. No issues found.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - Firebase writes and reads function properly across flavors
- **Duration:** 1m 1s

### Task_6_Touch_Gestures_Volume_Intensity_and_Pattern_Dashboard: Implement 1-finger touch signal and 2-finger drag floating overlay behavior with opacity states (20% idle, 50% active). Update MainActivity dashboard with service toggle switch and pattern selector. Sync STREAM_MUSIC volume level and vibration pattern via Firebase for dynamic volume-scaled vibration.
- **Status:** COMPLETED
- **Updates:** Implemented 1-finger touch signal sending and 2-finger drag floating overlay repositioning with opacity transitions (20% idle, 50% active). Implemented media volume (STREAM_MUSIC) sensing and dynamic amplitude scaling. Supported continuous constant vibration and custom patterns (PULSE, HEARTBEAT, WAVE, RHYTHM). Transformed MainActivity into a full Material 3 Companion Dashboard with service start/stop toggle switch, pattern selector chips, and permission status checks. Verified unit tests and builds.
- **Acceptance Criteria:**
  - 1-finger touch sends vibration signal, 2-finger touch drags floating button
  - Floating button opacity is 0.2f idle and 0.5f active
  - MainActivity dashboard toggles service state and selects vibration patterns
  - Sender media volume and vibration pattern are synced via Firebase and control receiver vibration engine
- **Duration:** 8m 16s

### Task_7_Run_and_Verify: Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, check floating touch gestures, opacity, volume intensity scaling, pattern selector, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic agent completed final code quality and build verification. Unit tests passed. Verified 1-finger touch vs 2-finger drag floating overlay behavior, 20%/50% opacity transitions, sender STREAM_MUSIC media volume reading, dynamic amplitude continuous vibration engine with pattern selector (CONSTANT, PULSE, HEARTBEAT, WAVE, RHYTHM), and MainActivity Compose dashboard with service switch toggle.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** 1m 3s

### Task_8_Visual_Assets_1Finger_Drag_Opacity_Slider_SOS_Vibration: Add bird vector assets (ic_yellow_bird, ic_blue_bird, ic_app_icon). Update FloatingService for 1-finger touch & drag, idle opacity slider support (default 0.5f, 1.0f active pressed opacity). Update vibration engine for continuous max intensity (amplitude 255) with HEARTBEAT pattern. Implement automated permission onboarding dialog and Emergency SOS audio alert with 10s cooldown.
- **Status:** COMPLETED
- **Updates:** Added ic_yellow_bird.xml, ic_blue_bird.xml, ic_app_icon.xml vector drawables. Updated FlavorConfig and launcher icon. Rolled back 2-finger drag to 1-finger touch and drag in FloatingService. Added idle opacity slider in MainActivity dashboard (default 0.5f, active 1.0f). Updated vibration engine for maximum intensity (amplitude 255) with default HEARTBEAT pattern. Added first-launch automated permission onboarding AlertDialog. Added Emergency SOS feature with 10s cooldown timer and ToneGenerator max-volume fast-paced Morse code S.O.S tone audio alert on partner device. Verified builds and unit tests.
- **Acceptance Criteria:**
  - ic_yellow_bird, ic_blue_bird, ic_app_icon vector assets created and integrated
  - 1-finger drag and press overlay button behavior working smoothly
  - Dashboard idle opacity slider controls floating overlay opacity with default 50% and 100% active pressed opacity
  - Continuous max-intensity vibration (amplitude 255) defaults to HEARTBEAT pattern
  - First launch displays automated permission onboarding popup dialog
  - Emergency SOS button triggers fast-paced Morse code S.O.S tone at max volume on partner device with 10s cooldown
- **Duration:** 4m 29s

### Task_9_Run_and_Verify: Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, check visual assets, 1-finger touch & drag, opacity slider, max vibration, permission onboarding, SOS audio alert, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic agent completed final code quality and build verification. Unit tests passed, Gradle build passed. Verified bird visual assets (ic_yellow_bird, ic_blue_bird, ic_app_icon), 1-finger touch & drag overlay handling, 50% idle / 100% active opacity slider, maximum intensity vibration (amplitude 255) with default HEARTBEAT pattern, automated first-launch permission onboarding dialog, and Emergency SOS feature with 10s cooldown timer and ToneGenerator max-volume fast-paced Morse S.O.S tone audio alert.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** 1m 9s

### Task_10_Max_Haptics_WakeLocks_Presence_Themes_Ping: Implement maximum intensity vibration with double-thump HEARTBEAT waveform, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS prompt, PARTIAL_WAKE_LOCK for lock screen execution, pulsating floating button animation during vibration, Discord-style partner presence state updates via RTDB and screen state broadcasts, app dashboard color themes, and 5-sec cooldown high-pitched Ping audio button.
- **Status:** COMPLETED
- **Updates:** Implemented maximum intensity vibration with double-thump HEARTBEAT waveform (amplitude 255). Added REQUEST_IGNORE_BATTERY_OPTIMIZATIONS prompt card and PARTIAL_WAKE_LOCK for lock screen haptics. Added floating overlay scale & opacity pulse animation during vibration. Added Discord-style partner presence tracking (Active green, Away yellow, Offline red/desaturated) via screen state BroadcastReceiver and Firebase RTDB presence heartbeat. Implemented 5 dashboard color themes (Default, Midnight, Romantic Rose, Ocean Breeze, Sunset Gold). Implemented Ping audio button with 5-second cooldown playing a 3000Hz decaying sine wave with echo effect on partner device. Verified builds and tests.
- **Acceptance Criteria:**
  - Vibration pattern uses max intensity amplitude 255 double thumps without volume scaling
  - App requests REQUEST_IGNORE_BATTERY_OPTIMIZATIONS exemption and acquires PARTIAL_WAKE_LOCK for lock screen haptics
  - Floating overlay button dynamically pulsates scale and opacity while vibration is active
  - Discord-style partner presence (Active 🟢, Away 🟡, Offline 🔴) tracks screen lock/unlock and RTDB heartbeat
  - App dashboard supports themes (Default, Midnight Dark, Romantic Rose, Ocean Breeze, Sunset Gold)
  - Ping button with 5-second cooldown plays high-pitched echoed 'ting' sound on partner device
- **Duration:** 6m 2s

### Task_11_Run_and_Verify: Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, test max haptics, wake locks, overlay pulsation, presence state updates, dashboard themes, ping audio alert, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic agent completed final quality and code verification. Unit tests passed. Verified maximum intensity double-thump Heartbeat vibration pattern, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS prompt card and PARTIAL_WAKE_LOCK lock screen haptics, pulsating floating overlay scale & alpha animation during vibration, Discord-style partner presence state updates (Active green, Away yellow, Offline red/desaturated) via screen broadcasts and RTDB presence heartbeat, 5 app dashboard themes (Default, Midnight, Romantic Rose, Ocean Breeze, Sunset Gold), and 5s cooldown Ping audio button playing a high-pitched 3000Hz echoed 'ting' sound.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** 1m 14s

### Task_12_SineWave_BirdVectors_PingSound_Footer: Implement smooth sine wave animation oscillating opacity, scale, and padding for floating overlay button during vibration. Create presence-reactive bird vector drawables (Yellow for Kuchu, Blue for Puchi) for Active (awake happy singing), Away (sleepy zZz), and Offline (dead X_X) states and integrate into profile cards and floating button. Update Ping sound to single-note hotel reception desk bell 'ting' with ~2s exponential decay reverb. Add MainActivity footer text 'Made with love by Mahi 💖✨ • Inspired by my girlfriend Samia 👩‍❤️‍👨🏽🌸'.
- **Status:** COMPLETED
- **Updates:** Implemented presence-reactive bird vectors for Kuchu (Yellow) and Puchi (Blue) across Active (happy/singing), Away (sleepy), and Offline (dead) states. Updated FlavorConfig to resolve flavor-specific self and partner icons. Implemented smooth sine wave animation (oscillating scale, opacity, and padding) for floating overlay button during vibration in FloatingService.kt. Updated floating button icon to change dynamically based on partner presence status. Synthesized single-note hotel desk bell 'ting' (2400Hz + 4800Hz) audio with ~2.0s exponential decay reverb. Updated MainActivity dashboard with self and partner cards and added footer text 'Made with love by Mahi 💖✨ • Inspired by my girlfriend Samia 👩‍❤️‍👨🏽🌸'. Verified builds and unit tests.
- **Acceptance Criteria:**
  - Sine wave animation oscillates scale, opacity, and padding of floating button during vibration
  - Presence-reactive bird vectors (Active 🟢, Away 🟡, Offline 🔴) created for both Yellow and Blue birds and reflected in profile cards and floating control
  - Ping sound synthesizes hotel reception desk bell 'ting' with ~2 second exponential decay reverb
  - MainActivity interface includes required footer text at bottom
  - build pass
- **Duration:** 8m 54s

### Task_13_Run_and_Verify: Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, check sine wave floating overlay animation, presence-reactive bird vector drawables, hotel reception desk bell ping sound, MainActivity footer, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic agent completed final quality and code verification. Unit tests passed. Verified presence-reactive bird vector drawables (Active happy singing, Away sleepy zZz, Offline dead X_X) for both Kuchu (Yellow Bird) and Puchi (Blue Bird), flavor-specific presence cards, dynamic floating overlay button icon updates, sine wave animation (oscillating scale and opacity) during vibration, hotel desk bell 'ting' audio with ~2.0s exponential decay reverb, and MainActivity footer text 'Made with love by Mahi 💖✨ • Inspired by my girlfriend Samia 👩‍❤️‍👨🏽🌸'.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
- **Duration:** 1m 29s

