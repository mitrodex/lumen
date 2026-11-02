# Lumen ✨

**A glass-style home screen for Amazon Fire TV – written 100 % by AI, steered by people with ideas.**

[![CI](../../actions/workflows/ci.yml/badge.svg)](../../actions/workflows/ci.yml)
![vibe-coded](https://img.shields.io/badge/code-100%25%20vibe--coded-blueviolet)
![license](https://img.shields.io/badge/license-MIT-green)

<!-- TODO: add a screenshot (docs/screenshot.png) – use a demo calendar, not your private one -->

Lumen replaces the ad-filled Amazon home screen with a calm, translucent "frosted glass" design:
your favorite apps, a clock, weather and your week – and a way to jump straight back into the
episode you were watching.

## Why this repo is special

This is an experiment: **every line of code is written by AI assistants** (mostly
[Claude Code](https://claude.com/claude-code)). Humans bring the ideas, test on their TVs and decide
what goes in. You don't need to know how to program to shape it – if you can describe what you want
on your TV, you can contribute. → [How to contribute](CONTRIBUTING.md)

## What it can do

- 🏠 **Takes over the Home button** – press Home and you land in Lumen, not on Amazon's ad wall
  (without root; uses an accessibility service, see [how](docs/fire-os-notes.md#replacing-the-home-screen))
- 🧊 **Glass design** with blurred wallpaper, animated backgrounds, light/dark tint by time of day
- ▶️ **Continue watching** – hold OK on Netflix, Prime Video, Disney+ or Crunchyroll to jump right
  back into what you watched, with episode number and image
- 📅 **Week calendar** from iCloud (or any ICS link) – browse weeks with the remote
- 🌤 **Weather**, 🎵 **AirPlay music** view, ⚙️ **control center** (WiFi, Bluetooth, restart)
- 🌙 **Screensaver** with clock and your next appointments

Tested on a **Fire TV Stick (3rd gen), Fire OS 7.7**. Other Fire TV devices probably work – tell us!

## Install

You need a computer with [ADB](https://developer.android.com/tools/releases/platform-tools)
and your Fire TV in the same network.

1. On the Fire TV: **Settings → My Fire TV → About** → click the device name 7 times. Then
   **Developer options → ADB debugging: ON**. Note the IP address (**About → Network**).
2. Download the latest `lumen-*.apk` from [Releases](../../releases).
3. Install and switch everything on (allow the connection on the TV when asked):

   ```bash
   ./tv-install.sh 192.168.1.50 lumen-v0.1.0.apk
   ```

   On Windows, from source: `tv-install.bat 192.168.1.50`.

Press Home – welcome to Lumen. To go back to Amazon's home screen, uninstall Lumen
(`adb uninstall de.tb.lumen`).

### Optional: calendar and episode images

Both are off until you set them – no account needed:

```bash
adb shell am broadcast -a de.tb.lumen.CONFIG -n de.tb.lumen/.ConfigReceiver \
  --es calendar_urls "webcal://…icloud.com/published/…,webcal://…" \
  --es tmdb_key "your-tmdb-api-key"
```

- **Calendar:** iPhone → Calendar → Calendars → (i) → *Public Calendar* → share link. Several links
  separated by commas. Other ICS links (e.g. Google Calendar's "secret address in iCal format")
  should work too – only iCloud is tested so far.
- **TMDB key** (episode numbers and images): free at [themoviedb.org](https://www.themoviedb.org/settings/api).
- An empty value (`""`) removes a setting.

## Build from source

JDK 17+ and the Android SDK. Then `./gradlew assembleRelease` or directly `./tv-install.sh <ip>`.
Private defaults can also go into `local.properties` (never committed):

```properties
tmdb.key=…
calendar.urls=webcal://…,webcal://…
```

## Good to know

- Lumen uses unofficial ways to read what's playing (media sessions, Fire TV recommendations) and
  to find Disney+ links ([JustWatch](https://www.justwatch.com)). Apps can change and break this any time.
- Not affiliated with Amazon, Apple, Netflix, Disney or anyone else. App logos are loaded from the
  apps installed on your device.
- Hard-won Fire OS knowledge: [docs/fire-os-notes.md](docs/fire-os-notes.md).
  Removing Amazon bloat: [docs/debloat.md](docs/debloat.md) (German).

## License

[MIT](LICENSE)
