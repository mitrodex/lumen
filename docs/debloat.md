# Debloat-Kandidaten – Fire TV Stick 3. Gen (AFTSSS, Fire OS 7.7.1.7)

Nur deaktivieren mit `pm disable-user --user 0 <paket>`, rückgängig mit `pm enable <paket>`.
Eigene Liste: `adb shell pm list packages -e`.

## A – Sicher weg (Werbung, Telemetrie, Promo, Shopping, Tutorials)

| Paket | Zweck |
|---|---|
| com.amazon.kso.blackbird | „Special Offers“ / Werbe-Auslieferung |
| com.amazon.ftvads.deeplinking | Werbe-Deeplinks auf der Startseite |
| com.amazon.hybridadidservice | Werbe-ID |
| com.amazon.tv.acr | Automatic Content Recognition (Tracking, was du schaust) |
| com.amazon.sneakpeek | Autoplay-Vorschauen auf der Startseite |
| com.amazon.shoptv.client | Shopping |
| com.amazon.shoptv.firetv.client | Shopping |
| com.amazon.tv.easyupgrade | „Neues Gerät kaufen“-Promo |
| com.amazon.gamehub | Spiele-Hub-Promo |
| com.amazon.minitv.android.app | Amazon miniTV (Indien) |
| com.amazon.device.metrics | Telemetrie |
| com.amazon.tv.fw.metrics | Telemetrie |
| com.amazon.wirelessmetrics.service | WLAN-Telemetrie |
| com.amazon.perfcollection | Performance-Telemetrie |
| com.amazon.neo.minerva.logcat | Log-Upload |
| com.amazon.device.crashmanager | Crash-Report-Upload |
| com.amazon.device.logmanager | Log-Upload |
| com.amazon.dp.logger | Logging |
| com.amazon.logan | Logging |
| com.amazon.storm.lightning.tutorial | Tutorial |
| com.amazon.tmm.tutorial | Tutorial |
| com.amazon.tv.releasenotes | „Was ist neu“-Hinweise |

## B – Optional (deine Entscheidung)

| Paket | Zweck | Nebenwirkung |
|---|---|---|
| com.amazon.device.software.ota | System-Updates | keine Fire-OS-Updates mehr (auch keine Sicherheitsupdates) – verhindert aber, dass Amazon die Launcher-Tricks per Update kaputtmacht |
| com.amazon.device.software.ota.override | System-Updates | wie oben |
| com.amazon.tv.forcedotaupdater.v2 | Erzwungene Updates | wie oben |
| com.amazon.tahoe | Amazon Kids | nur weg, wenn keine Kinderprofile |
| com.amazon.bueller.photos | Amazon-Photos-Bildschirmschoner | Bildschirmschoner zeigt nur noch Standardbilder |
| com.amazon.bueller.music | Amazon Music-Integration | nur weg, wenn du Amazon Music nicht nutzt |
| com.amazon.media.recommendations | Empfehlungs-Zeilen | Startseite des Amazon-Launchers wird leerer (egal mit eigenem Launcher) |
| com.amazon.tv.livetv | Live-TV-Tab | nur relevant, wenn du Live-TV-Kanäle in Apps nutzt |
| com.amazon.firetv.youtube | YouTube | nur weg, wenn du YouTube nicht nutzt |
| Alexa-Pakete (`com.amazon.alexa*`, `com.amazon.tv.alexa*`, `com.amazon.whad`, `com.amazon.wha*`) | Sprachsteuerung | Mikrofon-Taste geht nicht mehr – erstmal drin lassen |

## C – Finger weg (Auswahl)

`com.amazon.tv.launcher`, `com.amazon.tv.settings.*`, `com.amazon.device.settings`, `com.amazon.tv.quicksettings.ui`,
`com.amazon.tv.ime` (Tastatur), `com.amazon.fireinputdevices`, `com.amazon.device.bluetooth*`, `com.android.bluetooth`,
`com.android.systemui`, `com.android.settings`, alle `com.android.providers.*`, `com.amazon.webview.chromium`,
`com.amazon.venezia` (Appstore), `com.amazon.dcp` / `com.amazon.identity.*` (Konto), `com.amazon.firehomestarter`,
`com.amazon.tv.intentsupport`, `com.amazon.tv.keypolicymanager`, `com.amazon.tifobserver`, `com.amazon.tv.oobe`,
`com.amazon.ftv.profilepicker`, `com.amazon.connectivity*`, `com.amazon.whisper*`, `com.amazon.tcomm*`,
`android`, `android.*`, `amazon.*`, `com.amazon.client.metrics.api` (Bibliothek – andere Apps hängen dran).


## Gelernt (getestet auf Fire OS 7.7.1.7)

- `com.amazon.imp` NICHT abschalten – sonst bleiben Fire-Einstellungsseiten (Netzwerk …) schwarz.
- `com.amazon.vizzini` (Alexa) wird von Fire OS nach einem Neustart wieder aktiviert.
- Geschützt (lassen sich nicht abschalten): `diode`, `adep`, `ftvads.deeplinking`, `device.metrics`, `crashmanager`, `logmanager`.
