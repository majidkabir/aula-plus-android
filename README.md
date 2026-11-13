# Aula+ for Android

An unofficial, faster and nicer Android client for [Aula](https://www.aula.dk) — the Danish school and daycare platform. Sister app of [aula-plus](https://github.com/majidkabir/aula-plus) (iOS).

Kotlin · Jetpack Compose · Material 3 · minSdk 26

## Features

- **Today** – greeting hero, per-child day card (status, check-in/out, pick-up, timeline), important dates
- **Messages** – threads with filters, search, stars, delete, chat view, reply, new message with recipient search
- **Calendar** – week strip with event density, day timeline, event details, RSVP, add to phone calendar
- **Week plan** – Meebook/MinUddannelse week plans with a chart, school tasks with progress ring
- **Presence** – check in/out, mark sick, pick-up plan per day, holiday registration and holiday requests
- **More** – posts with comments, gallery, notifications, contacts, secure documents, common files
- Offline-first cache (stale-while-revalidate), background refresh notifications, fingerprint lock, home-screen widget
- 6 themes incl. Material You, light/dark, Danish/English

## Install

The latest debug APK is built by CI on every push to `main`:

1. On your phone, open
   `https://github.com/majidkabir/aula-plus-android/raw/screenshots/aula-plus-debug.apk`
2. Allow "Install unknown apps" for your browser when asked, then install.
3. Open Aula+ and log in with MitID.

## Try it on a laptop (emulator)

```sh
adb install -r aula-plus-debug.apk
adb shell am start -n com.majidkabir.aulaplus/.MainActivity --ez demo true   # sample data, no MitID
```

## Build

```sh
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest          # unit tests
./gradlew recordPaparazziDebug       # screenshots → app/src/test/snapshots/images
```

Screenshots of every screen (light, dark, themes, large text) are on the `screenshots` branch.

## How it works

Aula has no public API, but its own apps use a JSON API at `https://www.aula.dk/api/v{N}/`. Aula+ logs in through the official MitID page (OIDC + PKCE) in a WebView and keeps the tokens in encrypted storage. Your data only travels between your phone and Aula.

Not affiliated with Aula or KOMBIT.
