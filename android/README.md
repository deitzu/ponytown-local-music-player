# Native Android rebuild

This directory contains the standalone native Android rebuild of PT Local Music Player.

Current foundation:

- Kotlin 2.4.20
- Android Gradle Plugin 9.3.0
- Jetpack Compose
- Media3 1.11.1
- MediaSession background playback
- MediaStore local-library scan
- title/artist/album search
- LRC parser foundation
- Android 8.0+ minimum
- persistent uncaught-exception crash logging

Crash reports are written to the app's external files directory at:
`Android/data/dev.deitzu.ptmusic/files/logs/last_crash.txt`

If the external files directory is unavailable, the app falls back to its private internal `files/logs/last_crash.txt` path.

The original Pony Town userscript is kept separate and is not required by the native app.

Builds are handled in GitHub Actions so the Android SDK does not need to be installed on the development phone.
