# CLAUDE.md – context for AI assistants

You are working on **Lumen**, a glass-style home screen for Amazon Fire TV, written in Kotlin with
Jetpack Compose. **This repository is vibe-coded on purpose:** humans describe ideas in plain
language, AI assistants write the code. Many contributors cannot read Kotlin. That makes *you*
responsible for code quality, tests and honest explanations.

## Ground rules

1. **Explain in plain words.** Contributors may not be developers. In PR descriptions and replies,
   say what changed for the person in front of the TV first, technical details second.
2. **Keep CI green.** Run `./gradlew testDebugUnitTest lintDebug assembleRelease` before you call
   anything done. Never weaken or delete a test to make it pass; fix the code or explain why the
   test was wrong.
3. **Test what you can.** Put new logic that doesn't need Android (parsing, matching, date math) in
   pure Kotlin functions and add unit tests in `app/src/test/java/de/tb/lumen/`. See
   `TitleParsing.kt` + `TitleParsingTest.kt` and `Ics.kt` + `IcsTest.kt` for the pattern.
4. **Be honest about the device.** You can't press buttons on a real Fire TV from CI. If a change
   needs a check on the device (focus, Home key, animations), say so clearly in the PR, with the
   exact steps a human should try.
5. **No secrets, no personal data.** API keys, calendar links, IP addresses and screenshots with
   private content never go into the repo. Private values live in `local.properties` (git-ignored)
   or are set at runtime via ADB (`Config.kt`).
6. **Read [docs/fire-os-notes.md](docs/fire-os-notes.md) before touching** the Home button, key
   handling, system settings, permissions or "continue watching". Many obvious approaches are proven
   dead ends there. Add what you learn.
7. **Small, focused changes.** One idea per PR. Don't reformat or rename unrelated code.
8. **Never make the device unusable.** Don't disable Amazon's launcher, settings, system updates,
   YouTube or Live TV, and never automate navigation near "Factory reset".

## Commands

```bash
./gradlew testDebugUnitTest          # unit tests (fast, no device)
./gradlew lintDebug                  # lint; existing issues are frozen in app/lint-baseline.xml
./gradlew assembleRelease            # APK → app/build/outputs/apk/release/app-release.apk
./tv-install.sh <fire-tv-ip>         # build + install + restore services (Windows: tv-install.bat)
adb logcat -d | grep ' Lumen +:'     # Lumen's log lines on the device
```

Requirements: JDK 17+, Android SDK (path in `local.properties` as `sdk.dir=…` or `ANDROID_HOME`).
The Fire TV needs ADB debugging enabled (Settings → My Fire TV → Developer options).

## Code style

- Kotlin, Compose, no DI framework, no extra libraries unless really needed (the stick is slow:
  1 GB RAM, every dependency costs).
- **Code comments are in German** (the original author's language). Keep writing them in German so
  files stay consistent; README/docs/PRs are in English.
- Short `object`s for services (`Calendar`, `Weather`, `NowPlaying`), state in `LauncherModel`,
  UI in `ui/`. Match the surrounding code: naming, comment density, idioms.
- Network calls only on background threads (`Executors`/`Dispatchers.IO`), always with timeouts
  and `runCatching` – a missing network must never crash the home screen.

## Map of the code (`app/src/main/java/de/tb/lumen/`)

| Area | Files |
|---|---|
| App start, key handling, screens | `MainActivity.kt`, `ui/HomeScreen.kt`, `ui/AppsScreen.kt`, `ui/SettingsScreen.kt`, `ui/TopBar.kt` |
| State | `LauncherModel.kt` (process singleton), `AppRepository.kt`, `Appearance.kt` (prefs) |
| Glass look | `ui/Glass.kt` (glass modifier, tiles, shimmer), `ui/Blur.kt`, `ui/Wallpaper.kt`, `ui/AnimatedWallpaper.kt`, `BannerTint.kt` |
| Home button takeover | `HomeWatcherService.kt` (accessibility), `HomeKeyLog.kt` (log watcher), `ui/HomeCover.kt`, `LumenVoice.kt` (assistant trick), `SystemSettings.kt` (self-heal), `BootReceiver.kt` |
| Continue watching | `MediaWatcher.kt` (media sessions + notifications), `NowPlaying.kt` (deep links), `History.kt`, `Recommendations.kt` (Netflix), `PrimeVideo.kt`, `DisneyPlus.kt`, `Episodes.kt` (TMDB), `TitleParsing.kt`, `ui/HistoryPopup.kt` |
| Calendar | `Calendar.kt` (fetch + cache), `Ics.kt` (parser), `ui/WeekCalendar.kt` |
| Screensaver | `LumenDream.kt` |
| Control center | `ui/ControlCenter.kt`, `WifiController.kt`, `BluetoothController.kt`, `Weather.kt`, `ui/WeatherUi.kt` |
| AirPlay | `AirPlayKeeper.kt`, `AirPlayMusic.kt`, `ui/MusicWidget.kt` |
| Private config | `Config.kt` (runtime values via ADB, fallback `BuildConfig` from `local.properties`) |

## Remote control and focus

Everything is driven by a D-pad: Up/Down/Left/Right, OK (center), Back, Home, Menu. Focus handling
uses `FocusRequester` and `onPreviewKeyEvent`. Parents see preview key events **before** children –
if a child (e.g. the calendar) handles Up/Down itself, the parent must let it through. Long press is
detected via `nativeKeyEvent.repeatCount > 0` and ~700 ms; the release after a long press must be
swallowed, otherwise it "clicks". Test focus paths on a real device.

## Feature flags worth knowing

- `NowPlaying.ENABLED = false` – the big "continue watching" widget on the home screen is
  switched off (it was unreliable); its code stays on purpose. `NowPlaying.CAPTURE = true` keeps
  recording for the long-press popup.
- Deep links per app: `NowPlaying.DEEP_LINKS`. Popup entries only appear when a deep link exists.
