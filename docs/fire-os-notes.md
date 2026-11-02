# Fire OS notes – what works, what doesn't, and why

Hard-won knowledge from building Lumen on a **Fire TV Stick (3rd gen, AFTSSS), Fire OS 7.7.1.7, Android 9 / API 28**.
Most of these are dead ends that cost hours. Read this before touching the Home button, key handling,
system settings or the "continue watching" logic. If you find out something new, add it here.

## Replacing the home screen

- **Becoming the real launcher is impossible.** `com.amazon.tv.launcher` is protected: neither
  `pm disable-user` on the package nor `pm disable` on `HomeActivity_vNext` works, and
  `cmd package set-home-activity` is overruled by Amazon's priority 950. Behind it sits
  `com.amazon.firehomestarter` (priority 1). Don't retry.
- **No `category.HOME` in the manifest.** Fire OS (FireTVKeyPolicyManager) force-stops any app with a
  HOME intent filter when Home is pressed – and force-stop also removes the accessibility service
  from `enabled_accessibility_services`.
- **The Home key never reaches an accessibility service.** Neither `KEYCODE_HOME` nor the Alexa key
  (code 319, hard-wired to `com.amazon.vizzini`) arrive in `onKeyEvent`. D-pad keys do.
  `adb shell input keyevent` doesn't reach the key filter at all – test with the real remote.
- **How Lumen catches Home anyway:** it reads the system log (`READ_LOGS` via `pm grant`), tag
  `ContextImpl:W`, line containing `handleShortPressOnHome`. It arrives ~0.3 s before Amazon draws,
  so Lumen covers the screen immediately (`HomeCover`, `HomeKeyLog`).
- **The 5-second rule:** after Home, Android blocks activity starts from the background for ~5 s
  (app-switch protection). Fix: Lumen registers as an (empty) voice assistant
  (`voice_interaction_service` = `LumenVoiceService`) – the assistant is exempt. Lumen now shows
  ~0.4 s after Home. The cover stays for the fraction of a second Amazon is visible, and for the
  ~2.5 s rebuild when Fire OS killed Lumen's activity to free memory.
- The launcher model is a process singleton so a recreated activity has apps and banners instantly.
- The 2025 system-user exploit (Pro-me3us) is patched since Fire OS 7.7.0.6.

## Settings pages

- Fire settings pages guarded by the signature permission `LAUNCHER_SETTINGS` are locked
  (e.g. Display & Sounds). Open: `NetworkActivity`, `ControllersAndBluetooth`
  (action `com.amazon.device.settings.action.CONTROLLERS`), `Settings.ACTION_SETTINGS`.
- `Settings.ACTION_SETTINGS` lands at `CTSDummySettingsHandler` (does nothing) for third-party apps.
  Real settings: explicitly `com.amazon.tv.launcher/.ui.SettingsActivity` – but that only opens a tab
  of Amazon's HomeActivity, hence the "settings session" in `HomeWatcherService`.
- Home inside Amazon's settings tab produces no accessibility event, and the
  `CLOSE_SYSTEM_DIALOGS(reason=homekey)` broadcast needs a signature permission. Workaround: poll
  `rootInActiveWindow` during the session for marker texts ("Mein Fire TV" / "Einstellungen durchsuchen").
- Reboot (`REBOOT`) is signature-only. Amazon's settings lists ignore `ACTION_CLICK` and
  `dispatchGesture` taps – only a real OK press works. Lumen opens "My Fire TV", moves focus to
  "Restart" via `ACTION_FOCUS` and shows a toast "press OK". **"Factory reset" is right below it –
  never navigate there blindly.**

## Permissions granted via ADB

`tv-install.*` grants `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `ACCESS_FINE/COARSE_LOCATION` (WiFi scan
results need location on Android 9). They survive `install -r`. With `WRITE_SECURE_SETTINGS`,
`SystemSettings.selfHeal` re-enables the home service itself (after update, boot, onResume).
After every reinstall the accessibility service still has to be re-enabled – use `tv-install`.

## Continue watching

- **Netflix** reports no media-session metadata. But it posts Fire TV *recommendations* as
  notifications: `category=recommendation`, group `continueWatching`, text "Series, Episode",
  `contentIntent` jumps straight into the content, image via `android.backgroundImageUri`
  (a content provider Lumen can read). Large icons are stripped ("reduced images").
- **Crunchyroll**: `DISPLAY_TITLE` = episode, `TITLE` = series, `MEDIA_ID` works with
  `crunchyroll://episode/<MEDIA_ID>`. No artwork → TMDB.
- **Prime Video** (`com.amazon.firebat`): only `MEDIA_ID` (`amzn1.dv.gti…`). Title and image come from
  the first 64 KB of `https://www.primevideo.com/-/de/detail/<gti>`. Deep link
  `https://app.primevideo.com/detail?gti=<gti>` opens the detail page with "Resume".
- **Disney+**: no `MEDIA_ID`, no recommendations, Watch Next is empty. Subtitle is "S8:F9 Title".
  Lumen looks the series up on JustWatch (unofficial GraphQL API) to get
  `https://www.disneyplus.com/browse/entity-<uuid>`. On a cold start Disney+ shows the profile
  picker first.
- **YouTube** (`com.amazon.firetv.youtube`) only reports title/channel once a video really plays,
  and keeps reporting "playing" long after it's in the background – stale sessions are ignored.
- The Netflix player UI is a Gibbon surface without accessibility nodes; screenshots are blocked.

## Other

- Elevation shadows shine through translucent glass → shadows only on opaque tiles.
- **Own screensaver:** `settings put secure screensaver_components de.tb.lumen/de.tb.lumen.LumenDream`
  (original: `com.amazon.ftv.screensaver/.app.services.ScreensaverService`). There is no
  Somnambulator; to test, lower `screen_off_timeout` + `contextual_screen_off_timeout` briefly and
  set them back to 300000. `service call dreams 1` needs `WRITE_DREAM_STATE` – not possible.
- **iCloud calendars** (published webcal links) sometimes take minutes until new events appear.
- **AirPlay** (`io.github.jqssun.airplay`): its service doesn't start after boot by itself. Lumen
  (`AirPlayKeeper`) wakes it with an explicit `QUICKBOOT_POWERON` to its boot receiver and checks
  port 7000 every 2 min. End of mirroring = log line "AirPlayService: Sender disconnected" → Lumen
  presses Back. Apple devices connect every few minutes for milliseconds, so the app's
  `launch_on_connect` is off and Lumen opens it on "mirror running: 1". Audio-only
  ("Audio format … screen=false") opens Lumen's music view instead.
- App logs on Fire OS: grep for `' Lumen +:'` (the tag is padded).
- R8 can remove a `SharedPreferences` listener field (the listener is held weakly) → `@field:Keep`.
- Debloating: see [debloat.md](debloat.md).
