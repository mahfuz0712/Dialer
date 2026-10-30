# Dialer

Sci-fi HUD styled phone dialer for Android 5.0 (API 21) to the latest version. Same design language and
architecture as the Calculator app (Jetpack Compose, cyan/amber HUD palette, phone + tablet layouts).

- Package: `com.mux.phone.dialer` - Version: `1.0.0`

## Screens
| Tab  | What it does |
|------|--------------|
| DIAL | Keypad, key tones, smart-dial (number + T9 name) suggestions, long-press `0` = `+`, save number |
| LOG  | Call log with ALL / MISSED / INCOMING / OUTGOING filters; call, SMS, put on keypad, save, delete |
| CONT | Contacts screen: search, A-Z, filter by **PHONE / SIM / each connected Google account / OTHER**, detail + edit |
| REC  | Recorded calls: play, share, call back, delete |
| SYS  | System info, default-phone-app status, permissions, contact / dialer / recording settings, updates, author |

## Call recording
- Auto record: off / all calls / saved numbers / unknown numbers, or tap **REC** on the call screen.
- Saved as `.m4a` in `Android/data/com.mux.phone.dialer/files/Recordings/` (no storage permission needed).
- Android 10+ does not let normal apps capture the other side of a call; usually only the microphone
  (and the other person when on speaker) is recorded. Use SYS > Audio source / Speaker while recording.

## Default phone app
Android 6.0+: SYS > DEFAULT PHONE APP > SET AS DEFAULT (uses the Role API on Android 10+).
Then Dialer shows the HUD incoming/outgoing call screen (`InCallService`). On Android 5.x the system shows
the call screen; Dialer still dials and records through a phone-state receiver.

## Build
Open in Android Studio, or `gradle assembleDebug` (Gradle 8.9, JDK 17). CI: `.github/workflows/build.yml`.
Release updates are read from GitHub releases of `mahfuz0712/Dialer` (edit `AppInfo.kt`).
