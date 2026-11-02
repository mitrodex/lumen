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

For now you need a computer (Windows, Mac or Linux) in the same WiFi as your Fire TV.
A setup without a computer is planned.

**1. Prepare the Fire TV** (once)

- **Settings → My Fire TV → About** → click on the device name **7 times** until it says
  "You are now a developer".
- Back to **My Fire TV → Developer options** → **ADB debugging: ON**.
- **About → Network**: note the **IP address** (something like `192.168.1.50`).

**2. Prepare the computer** (once)

- Download Google's [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
  and unzip them. On Mac you can also run `brew install android-platform-tools`.
- From the [latest release](../../releases/latest) download **`tv-install.bat`** (Windows) or
  **`tv-install.sh`** (Mac/Linux) and put it into the unzipped `platform-tools` folder.

**3. Install**

Open a terminal in the `platform-tools` folder and run the script with your TV's IP address.
It downloads Lumen, installs it and switches everything on:

```bash
# Windows
tv-install.bat 192.168.1.50

# Mac / Linux
ADB=./adb sh tv-install.sh 192.168.1.50
```

The TV asks **"Allow USB debugging?"** the first time – choose **Always allow from this computer**
and **OK**. Then press **Home** on your remote: welcome to Lumen.

**Updates:** run the same command again – your favorites and settings stay.

**Uninstall:** `adb uninstall de.tb.lumen` (or Settings → Applications → Lumen → Uninstall).
Amazon's home screen is back immediately.
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
