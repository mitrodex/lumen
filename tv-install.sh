#!/usr/bin/env sh
# Installs Lumen on a Fire TV and switches on everything it needs (home button service,
# notification access, screensaver, …). Run it again after every update.
#
#   ./tv-install.sh 192.168.1.50              latest release (or your own build, inside the repo)
#   ./tv-install.sh 192.168.1.50 lumen.apk    a specific APK
#
# Needs adb (Android platform-tools). Optional environment: ADB=/path/to/adb
set -e
cd "$(dirname "$0")"

TV="${1:-$FIRE_TV}"
[ -n "$TV" ] || { echo "Usage: $0 <fire-tv-ip> [apk]"; exit 1; }
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
ADB="${ADB:-adb}"
APK="$2"
command -v "$ADB" >/dev/null 2>&1 || { echo "adb not found – install Android platform-tools first."; exit 1; }

if [ -z "$APK" ]; then
  if [ -x ./gradlew ]; then
    echo "Building Lumen …"
    ./gradlew assembleRelease -q
    APK=app/build/outputs/apk/release/app-release.apk
  else
    echo "Downloading the latest Lumen release …"
    APK=lumen.apk
    curl -fL -o "$APK" https://github.com/mitrodex/lumen/releases/latest/download/lumen.apk
  fi
fi

echo "Connecting to $TV – if the TV asks \"Allow USB debugging?\", choose Allow."
"$ADB" connect "$TV" >/dev/null
"$ADB" -s "$TV" wait-for-device
"$ADB" -s "$TV" install -r "$APK"
s() { "$ADB" -s "$TV" shell "$@"; }
for p in WRITE_SECURE_SETTINGS ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_LOGS; do
  s pm grant de.tb.lumen "android.permission.$p"
done
s settings put secure enabled_accessibility_services de.tb.lumen/de.tb.lumen.HomeWatcherService
s settings put secure accessibility_enabled 1
s cmd notification allow_listener de.tb.lumen/de.tb.lumen.MediaWatcher
s settings put secure screensaver_components de.tb.lumen/de.tb.lumen.LumenDream
s settings put secure voice_interaction_service de.tb.lumen/de.tb.lumen.LumenVoiceService
s settings put secure voice_recognition_service de.tb.lumen/de.tb.lumen.LumenRecognitionService
s cmd package compile -m speed -f de.tb.lumen >/dev/null
s am start -n de.tb.lumen/.MainActivity >/dev/null
echo "Lumen is installed. Press Home on your remote."
