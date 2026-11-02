@echo off
rem Installiert Lumen auf dem Fire TV und schaltet alles ein, was es braucht (Home-Dienst,
rem Benachrichtigungs-Zugriff, Bildschirmschoner, ...). Nach jedem Update erneut ausfuehren.
rem Installs Lumen on a Fire TV and switches on everything it needs. Run again after updates.
rem
rem   tv-install.bat 192.168.1.50             neuestes Release (im Repo: eigener Build)
rem   tv-install.bat 192.168.1.50 lumen.apk   eine bestimmte APK
rem
rem Eigene Pfade/IP dauerhaft in tv.local.bat (wird nicht eingecheckt), z. B.:
rem   set FIRE_TV=192.168.1.50:5555
rem   set ADB=C:\platform-tools\adb.exe
rem   set JAVA_HOME=C:\Program Files\Java\jdk-17
setlocal
cd /d "%~dp0"
if exist tv.local.bat call tv.local.bat
if not "%~1"=="" set FIRE_TV=%~1
if "%FIRE_TV%"=="" (
    echo Usage: tv-install.bat ^<fire-tv-ip^> [apk]
    exit /b 1
)
echo %FIRE_TV% | find ":" >nul || set FIRE_TV=%FIRE_TV%:5555
if "%ADB%"=="" set ADB=adb
set APK=%~2

if not "%APK%"=="" goto :install
if exist gradlew.bat (
    echo Building Lumen ...
    call gradlew.bat assembleRelease -q || goto :error
    set APK=app\build\outputs\apk\release\app-release.apk
) else (
    echo Downloading the latest Lumen release ...
    set APK=lumen.apk
    curl.exe -fL -o lumen.apk https://github.com/mitrodex/lumen/releases/latest/download/lumen.apk || goto :error
)

:install
echo Connecting to %FIRE_TV% - if the TV asks "Allow USB debugging?", choose Allow.
"%ADB%" connect %FIRE_TV% >nul
"%ADB%" -s %FIRE_TV% wait-for-device
set S="%ADB%" -s %FIRE_TV% shell
"%ADB%" -s %FIRE_TV% install -r "%APK%" || goto :error
rem Eigene Einstellungen + Selbstheilung, WLAN-Suche (Standort ist unter Android 9 Pflicht).
%S% pm grant de.tb.lumen android.permission.WRITE_SECURE_SETTINGS
%S% pm grant de.tb.lumen android.permission.ACCESS_FINE_LOCATION
%S% pm grant de.tb.lumen android.permission.ACCESS_COARSE_LOCATION
rem Home-Druck im System-Log erkennen (Abdeckung liegt, bevor Amazon zeichnet).
%S% pm grant de.tb.lumen android.permission.READ_LOGS
%S% settings put secure enabled_accessibility_services de.tb.lumen/de.tb.lumen.HomeWatcherService
%S% settings put secure accessibility_enabled 1
%S% cmd notification allow_listener de.tb.lumen/de.tb.lumen.MediaWatcher
%S% settings put secure screensaver_components de.tb.lumen/de.tb.lumen.LumenDream
rem Leerer Sprachassistent: hebt die 5-s-Sperre nach Home auf.
%S% settings put secure voice_interaction_service de.tb.lumen/de.tb.lumen.LumenVoiceService
%S% settings put secure voice_recognition_service de.tb.lumen/de.tb.lumen.LumenRecognitionService
%S% cmd package compile -m speed -f de.tb.lumen >nul
%S% am start -n de.tb.lumen/.MainActivity >nul
echo Lumen is installed. Press Home on your remote.
exit /b 0

:error
echo Something went wrong - see the output above.
exit /b 1
