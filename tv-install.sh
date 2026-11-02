#!/usr/bin/env sh
# Builds Lumen (or takes a downloaded APK), installs it on the Fire TV and restores everything a
# reinstall resets (home service, notification access, precompilation).
#
#   ./tv-install.sh 192.168.1.50                 build from source and install
#   ./tv-install.sh 192.168.1.50 lumen-v0.2.apk  install a downloaded release
#
# Optional environment: ADB=/path/to/adb
set -e
cd "$(dirname "$0")"

TV="${1:-$FIRE_TV}"
[ -n "$TV" ] || { echo "Usage: $0 <fire-tv-ip> [apk]"; exit 1; }
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
ADB="${ADB:-adb}"
APK="$2"

if [ -z "$APK" ]; then
  ./gradlew assembleRelease -q
  APK=app/build/outputs/apk/release/app-release.apk
fi

"$ADB" connect "$TV" >/dev/null
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
echo "Lumen is installed."
