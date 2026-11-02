@echo off
rem Live-Ansicht des Fire TV per scrcpy (Steuerung: Pfeiltasten, Enter, Esc = Zurueck).
rem Nutzt dasselbe adb wie tv-install.bat, damit sich zwei adb-Versionen nicht gegenseitig beenden.
rem Einstellungen (FIRE_TV, ADB, SCRCPY) wie bei tv-install.bat in tv.local.bat.
setlocal
cd /d "%~dp0"
if exist tv.local.bat call tv.local.bat
if not "%~1"=="" set FIRE_TV=%~1
if "%FIRE_TV%"=="" (
    echo Bitte die IP des Fire TV angeben: tv-live.bat 192.168.1.50
    exit /b 1
)
echo %FIRE_TV% | find ":" >nul || set FIRE_TV=%FIRE_TV%:5555
if "%ADB%"=="" set ADB=adb
if "%SCRCPY%"=="" set SCRCPY=scrcpy
"%ADB%" connect %FIRE_TV% >nul
start "" "%SCRCPY%" -s %FIRE_TV% --max-size 1280 --max-fps 30 --no-audio --stay-awake --window-title "Fire TV"
