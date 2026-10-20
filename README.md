# Android Launcher

A home-screen replacement for Android, written in Kotlin with Jetpack Compose and
Material 3.

The app registers itself as a `CATEGORY_HOME` activity, so once it is set as the
default home app it takes over the home screen.

## Get it

[**Download the latest APK**](https://github.com/suriya-ca/android-launcher/releases/latest)
from GitHub Releases — the current release is **v0.2.2**.

Install it, then make it the home app: **Settings → Apps → Default apps → Home
app**, or use the launcher's own settings.

## Features

**Home screen**
- App grid with an editable number of rows per page
- Multiple pages, with add / remove and a main home page
- A dock
- Drag and drop that lands where you let go — every cell of every page, and the
  dock, for apps and widgets alike
- Widget hosting, including resize and move
- An edit mode that keeps the pages swipeable, with add-page outlines at either
  end and wallpaper, widgets and settings along the bottom

**App drawer**
- Pages or list layout
- Configurable grid columns, icon size, and app-name labels
- A to Z or Z to A sort order
- Optional search bar

**Widget page**

Reached from the home screen's edit mode, not by swiping: the drawer swipes up
to nothing. It lists every widget the system offers, with search. Long-press one
to carry it onto the home screen — the page gets out of the way the moment the
widget is lifted — or tap it to place it.

**Appearance**
- Light, dark, or system theme
- Icon shapes: system, circle, squircle, rounded square, square, teardrop
- Third-party icon pack support
- Dynamic color from the wallpaper

**Other**
- Hide apps without uninstalling them
- Swipe down on the home screen to open the notification shade
- Set the app as the default home screen from settings

## Permissions and privacy

The launcher deliberately avoids broad, invasive permissions:

- **No `QUERY_ALL_PACKAGES`.** Apps and icon packs are made visible through the
  `<queries>` element in the manifest instead. Apps that do not expose a launcher
  activity are never seen.
- **`REQUEST_DELETE_PACKAGES`** is used only to hand an uninstall request to the
  system package installer. The launcher does not uninstall anything itself.
- **The accessibility service is single-purpose.** It exists solely to pull the
  notification shade down when you swipe on the home screen, and it reads no
  window content. It is off by default and enabled from settings.
- **The settings activity is not exported**, so it is reachable only from the
  launcher and the launcher never lists itself in its own app drawer.

Settings are stored locally with Jetpack DataStore. Nothing leaves the device.

## Requirements

- JDK 17
- Android SDK 37
- An Android 13 (API 33) or newer device or emulator

## Building

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Testing

The home screen's cell arithmetic — which cell a place on the screen belongs to,
on any page — is plain Kotlin and is covered by plain JUnit tests, so it runs on
the JVM without a device:

```sh
./gradlew test
```

## Project layout

```
app/src/main/java/com/sdev/launcher/
├── LauncherApp.kt          Application class
├── MainActivity.kt         The home activity
├── data/                   Apps, icon caching, icon packs, settings, home slots
├── service/                The notification-shade accessibility service
└── ui/                     Compose screens, widgets, drag and drop, theming
    └── settings/           The launcher's settings UI
app/src/test/               JVM unit tests
```

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 |
| Build | Android Gradle Plugin 9.4.1 |
| SDK | compile/target 37, min 33 |
| Storage | DataStore Preferences |
