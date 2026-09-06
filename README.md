# PT Local Music Player

A feature-rich, self-contained music player userscript for Pony Town (pony.town). Built as a Tampermonkey script with zero external dependencies.

## Features

### Core Playback
- Persistent playlist — Tracks stored in IndexedDB (survives browser restarts)
- Native ID3v2 parser — Reads title, artist, album from MP3 headers (v2.3/v2.4) without external libraries
- Web Audio API — Precise volume control via GainNode
- Media Session API — Hardware media keys, lock screen controls, Bluetooth headset support

### Lyrics Support
- LRC parsing — Timestamped lyrics ([mm:ss.xx]) with real-time synchronization
- Three display modes: Overlay (floating), Embedded (in-player), Off
- Three visual styles: YouTube-style, Glow outline, Glassmorphism
- Auto-fetch — Queries lrclib.net for missing synced lyrics
- Manual .lrc upload — Attach lyrics per-track via the playlist

### Playback Controls
- Play / Pause / Previous / Next
- Shuffle mode
- Repeat modes: Off → All → One (three-state toggle)
- Seek bar with time display
- Volume slider

### UI/UX
- Draggable window — Click and drag the title bar to reposition
- Minimize mode — Collapses to compact bar showing current track
- Settings panel — Configure lyrics, appearance, auto-fetch
- Idle fade — Auto-dims after 3.5s of inactivity
- Persistent position — Remembers window location via localStorage
- Input blocking — Prevents Pony Town keybinds from interfering

## Installation

1. Install Tampermonkey (or Violentmonkey/Greasemonkey)
2. Click "Install Script" or copy userscript.js into a new userscript
3. Visit pony.town — the player appears in the top-right

## Usage

| Action | How |
|--------|-----|
| Add music | Click + Add → select one or more audio files (MP3, OGG, etc.) |
| Play track | Click a track in the list, or use Prev/Next/Play buttons |
| Attach lyrics | Click +LRC on a track → select .lrc file |
| Move player | Drag the title bar ("Mini Player" / track name) |
| Minimize | Click _ button |
| Settings | Click gear icon |
| Clear all | Settings → Clear All (irreversible) |

## Configuration

All settings persist in localStorage under key pt_mp_settings:

| Setting | Options | Default |
|---------|---------|---------|
| Lrc Mode | Off / Overlay / Embedded | Overlay |
| Lrc Style | YouTube / Glow / Glass | YouTube |
| Bottom Position | 5–50% (overlay vertical offset) | 20% |
| Font Size | 12–24px | 16px |
| Auto-Fetch API | On / Off | On |

## Changelog

### v1.9.3 — Toast Notification Overhaul
**Release Date:** Sep 6, 2026

#### New Features
- **Toast Notifications (v2)**: Completely redesigned toast with track title, artist, duration, and progress bar
- **Progress bar animation**: Sliding bar that depletes over the 3-second toast lifetime
- **CSS slide-in/out**: Smooth animation using `cubic-bezier(0.18, 0.89, 0.32, 1.28)`
- **Toast setting toggle**: Enable/disable toast notifications via settings panel
- **Metadata-aware triggering**: Toast fires on `audio.onloadedmetadata` for accurate duration display

#### Improvements
- Hoisted `formatTime()` function to top-level for reuse in toast display
- Removed inline comments for cleaner code

#### Removed
- Temporary Gemini patch files (`gemini-code-*.js`)

---

### v1.9.2 — Gemini Patch Integration
**Release Date:** Sep 5, 2026

#### New Features
- **Audio Visualizer**: Canvas-based frequency bars using Web Audio API analyser
- **Themes**: Four color schemes (Amber, Emerald, Cyan, Violet) via CSS custom properties
- **Marquee scrolling**: Long track titles scroll automatically (6s loop)
- **Quick LRC Offset panel**: Overlay +/- buttons for instant lyric timing adjustment
- **Idle fade settings**: Configurable idle timeout (2-10s) and opacity (0.1-1.0)
- **Scrollable settings panel**: max-height: 250px with vertical overflow

#### Bug Fixes
- **Lyric background auto-hide**: Empty lyric containers hidden when no lyrics shown
- **Offset direction fixed**: parseLRC() uses +offset (was -offset) for correct delay

---

### v1.9.1 — Lyric Offset Feature
**Release Date:** Sep 5, 2026

#### New Features
- **Per-track lyric offset**: lrcOffset field stored in IndexedDB per-track
- **DB Version 2**: Migration with onupgradeneeded handler
- **Numeric +/- input** in settings for lyric timing adjustment
- Offset persists per-track across sessions

---

### v1.8.1 — Initial Release
**Release Date:** Sep 4, 2026

#### Features
- Persistent playlist with IndexedDB
- Native ID3v2 parser (v2.3/v2.4)
- LRC lyrics with real-time sync
- Overlay and embedded display modes
- Auto-fetch lyrics from lrclib.net
- Play/Pause/Prev/Next with shuffle/repeat
- Web Audio API volume control
- Media Session integration
- Draggable window with position persistence
- Settings panel with localStorage configuration

## Technical Details

### Storage Schema (IndexedDB)
```
DB: PT_MusicPlayer_DB
Store: playlist (autoIncrement id)
Record: {
  id: number,
  name: string,      // ID3 TIT2 or filename
  artist: string,    // ID3 TPE1
  album: string,     // ID3 TALB
  blob: Blob,        // original audio file
  lyrics: string     // LRC text (optional)
}
```

### ID3 Parser Limitations
- Reads only first 128 KB of file (covers most ID3v2 tags)
- Supports ID3v2.3 (ISO-8859-1/UTF-16) and v2.4 (UTF-8)
- Frames parsed: TIT2 (title), TPE1 (artist), TALB (album)
- Falls back to filename if no tags found

### Browser APIs Used
- indexedDB — persistent storage
- FileReader + DataView — binary ID3 parsing
- Audio + AudioContext/webkitAudioContext — playback and volume
- navigator.mediaSession — system media controls
- fetch — lrclib.net lyrics API
- localStorage — settings and window position

### Icons
All icons are inline SVGs defined directly in the script (public domain / MIT), no external icon library required.

## File Structure
```
userscript.js   # Single-file userscript (~558 lines)
README.md       # This file
```

## License

MIT — free to use, modify, distribute.

## Credits

- Author: deitzu
- Lyrics API: lrclib.net
- Icons: Inline SVGs (public domain / MIT)