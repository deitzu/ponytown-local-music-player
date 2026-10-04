# Native Android

This directory contains the standalone native Android rebuild of PT Local Music Player. It is independent from the original Pony Town userscript in the repository.

## Status

The native app now covers the main local-player workflow and the floating-player workflow. It is still an early native implementation, so CI debug APKs are the primary distribution build.

Targeted Android versions:

- Minimum: Android 8.0 (API 26)
- Project compile/target SDK: 37
- Java: 17
- Kotlin: 2.4.20
- Android Gradle Plugin: 9.3.0
- Jetpack Compose
- Media3 / ExoPlayer: 1.11.1

## Features

### Local music

- Scan device audio through MediaStore.
- Import one or multiple audio files with the Android document picker.
- Read title, artist, album, genre, and duration metadata from imported files.
- Persist imported tracks, edited metadata, tags, lyrics, and lyric offsets locally.
- Search by title, artist, album, or tag.
- Filter the library by tags or untagged tracks.
- Play, pause, previous, next, seek, volume, shuffle, and repeat.
- Background playback through Media3 `MediaSessionService`.
- Android media/session integration for the main player service.

### Lyrics and LRC

- Parse timestamped `.lrc` lyrics.
- Automatic lyric lookup through LRCLIB.
- Manual LRCLIB search and candidate selection.
- Import a local `.lrc` file for a track.
- Keep synced lyrics attached to the track instead of storing them only in runtime state.
- Optional romanized and translated lyric enrichment for non-Latin lyrics.
- LRC display modes: Off, Overlay, or Embedded.
- LRC presentation styles: YouTube, Glow, or Glass.
- Adjustable lyric position and font size.
- Per-track quick offset adjustment in 0.5 second steps, clamped to ±30 seconds.
- Optional automatic lyric fetching/selection.

### Floating player

The floating player is implemented as a dedicated foreground service and is intentionally kept separate from the normal activity UI.

It provides:

- Draggable 220dp floating control panel.
- Saved floating position.
- Play/pause, previous, next, seek, volume, shuffle, and repeat controls.
- Current track title and artist.
- Optional FFT/audio visualizer.
- Collapsible track list with tag filters.
- Add audio files directly from the floating UI.
- Search or upload LRC files directly from the floating UI.
- Edit tags or delete saved tracks.
- Minimized floating mode.
- Expand button to reopen the full app.
- Idle fade with configurable delay and opacity.
- Separate lyric subtitle overlay outside the control panel.
- Subtitle positioning similar to the original userscript layout.
- Four floating themes: Amber, Emerald, Cyan, and Violet.

## Permissions

The app asks for permissions only for features that need them.

| Permission | Android 12 and below | Android 13+ | Purpose |
| --- | --- | --- | --- |
| Music storage | `READ_EXTERNAL_STORAGE` | `READ_MEDIA_AUDIO` | Scan local music |
| Notifications | Not required by the app | `POST_NOTIFICATIONS` | Media/playback notification |
| Microphone | `RECORD_AUDIO` | `RECORD_AUDIO` | Optional audio visualizer |
| Overlay | Android Settings | Android Settings | Floating player |

The floating player also runs as a foreground service. On Android 14+ it uses the `specialUse` foreground-service type for the user-enabled floating controls and lyric overlay.

If overlay access is not granted, the floating service stops instead of trying to create a window without permission.

## Data and persistence

The app stores player metadata and settings in `SharedPreferences` using JSON.

Persisted data includes:

- Imported track entries
- Tags
- Lyrics, romanized lyrics, and translations
- LRC offsets
- Player settings
- Audio session ID
- Floating window position

Clearing the saved library removes the app's saved metadata and imported entries. It does not delete the original audio files from the device.

## Architecture

The native app is split into a few small responsibilities:

- `MainActivity` - normal Compose UI and permission flow.
- `MainViewModel` - playback state, library state, lyrics state, and settings.
- `PlayerService` - Media3/ExoPlayer background playback and media session.
- `FloatingPlayerService` - floating controls, lyric overlay, and floating-specific actions.
- `FloatingFilePickerActivity` - document-picker bridge used by the floating service.
- `MusicLibrary` - MediaStore scanning and imported-file metadata extraction.
- `LyricsRepository` - LRCLIB lookup/search and lyric enrichment.
- `LrcParser` - timestamp parsing and active-line selection.
- `AppStore` - local persistence for tracks, settings, and floating position.
- `PTMusicApplication` - persistent crash-report handling.

The floating Compose views use their own lifecycle/saved-state owner because they are created directly by a service rather than an activity.

## Crash reports

Uncaught crashes are written to:

`Android/data/dev.deitzu.ptmusic/files/logs/last_crash.txt`

If the external files directory is unavailable, the fallback is:

`files/logs/last_crash.txt`

The report includes the app version, package, device/Android information, thread, and full stack trace.

## Build

The repository is designed to build remotely through GitHub Actions, so the Android SDK does not need to be installed on the development phone.

Local Gradle command used by CI:

```bash
gradle -p android :app:assembleDebug --stacktrace
```

The workflow:

- Uses JDK 17.
- Uses Gradle 9.5.0.
- Installs Android SDK 37 and build-tools 37.0.0.
- Builds `app-debug.apk`.
- Uploads the APK as the `pt-local-music-player-debug` Actions artifact.
- Caches a stable debug signing key so consecutive CI builds can update an existing debug install while the cache remains available.

## Installing updates

The native app uses the stable package ID:

`dev.deitzu.ptmusic`

A newer APK can update the existing installation when it has the same application ID, is signed with the same key, and has a higher `versionCode`.

The CI debug-signing cache is intended to make this work for repeated development builds. If that cache is lost and a new debug key is generated, Android may require uninstalling the previous debug build before installing the new one.

## Relationship to the original userscript

The original browser userscript remains untouched and is not required by the native app.

The native implementation is a separate Android client intended to reproduce the useful local-player workflow without relying on Pony Town's webpage runtime.