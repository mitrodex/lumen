@echo off
rem Baut Lumen, installiert es auf dem Fire TV und stellt alles wieder her, was eine
rem Neuinstallation zuruecksetzt (Home-Dienst, Benachrichtigungs-Zugriff, Vorkompilieren).
rem
rem   tv-install.bat 192.168.1.50          (IP des Fire TV, Port 5555 wird ergaenzt)
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
    echo Bitte die IP des Fire TV angeben: tv-install.bat 192.168.1.50
    exit /b 1
)
echo %FIRE_TV% | find ":" >nul || set FIRE_TV=%FIRE_TV%:5555
if "%ADB%"=="" set ADB=adb

call gradlew.bat assembleRelease -q || goto :error

"%ADB%" connect %FIRE_TV% >nul
set S="%ADB%" -s %FIRE_TV% shell
"%ADB%" -s %FIRE_TV% install -r app\build\outputs\apk\release\app-release.apk || goto :error
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
%S% cmd package compile -m speed -f de.tb.lumen
%S% am start -n de.tb.lumen/.MainActivity >nul
echo Lumen ist installiert.
exit /b 0

:error
echo Fehler - siehe Ausgabe oben.
exit /b 1
