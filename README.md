# PT Local Music Player

A feature-rich, self-contained music player userscript for [Pony Town](https://pony.town/). Built as a Tampermonkey script with zero external dependencies.

## Features

### 🎵 Core Playback
- **Persistent playlist** — Tracks stored in IndexedDB (survives browser restarts)
- **Native ID3v2 parser** — Reads title, artist, album from MP3 headers (v2.3/v2.4) without external libs
- **Web Audio API** — Precise volume control via `GainNode`
- **Media Session API** — Hardware media keys, lock screen controls, Bluetooth headset support

### 📝 Lyrics Support
- **LRC parsing** — Timestamped lyrics (`[mm:ss.xx]`) with real-time sync
- **Three display modes**: Overlay (floating), Embedded (in-player), Off
- **Three visual styles**: YouTube-style, Glow outline, Glassmorphism
- **Auto-fetch** — Queries [lrclib.net](https://lrclib.net) for missing synced lyrics
- **Manual .lrc upload** — Attach lyrics per-track via the playlist

### 🎮 Playback Controls
- Play / Pause / Previous / Next
- **Shuffle** mode
- **Repeat modes**: Off → All → One (three-state toggle)
- Seek bar with time display
- Volume slider

### 🖥️ UI/UX
- **Draggable window** — Click & drag the title bar to reposition
- **Minimize mode** — Collapses to compact bar showing current track
- **Settings panel** — Configure lyrics, appearance, auto-fetch
- **Idle fade** — Auto-dims after 3.5s of inactivity
- **Persistent position** — Remembers window location via localStorage
- **Input blocking** — Prevents Pony Town keybinds from interfering

## Installation

1. Install [Tampermonkey](https://www.tampermonkey.net/) (or Violentmonkey/Greasemonkey)
2. Click **[Install Script](https://raw.githubusercontent.com/<your-repo>/main/userscript.js)** or copy `userscript.js` into a new userscript
3. Visit [pony.town](https://pony.town/) — the player appears in the top-right

## Usage

| Action | How |
|--------|-----|
| Add music | Click **+ Add** → select one or more audio files (MP3, OGG, etc.) |
| Play track | Click a track in the list, or use Prev/Next/Play buttons |
| Attach lyrics | Click **+LRC** on a track → select `.lrc` file |
| Move player | Drag the title bar ("Mini Player" / track name) |
| Minimize | Click `_` button |
| Settings | Click ⚙️ gear icon |
| Clear all | Settings → **Clear All** (irreversible) |

## Configuration

All settings persist in `localStorage` under key `pt_mp_settings`:

| Setting | Options | Default |
|---------|---------|---------|
| **Lrc Mode** | Off / Overlay / Embedded | Overlay |
| **Lrc Style** | YouTube / Glow / Glass | YouTube |
| **Bottom Position** | 5–50% (overlay vertical offset) | 20% |
| **Font Size** | 12–24px | 16px |
| **Auto-Fetch API** | On / Off | On |

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
- Reads only first **128 KB** of file (covers most ID3v2 tags)
- Supports **ID3v2.3** (ISO-8859-1/UTF-16) and **v2.4** (UTF-8)
- Frames parsed: `TIT2` (title), `TPE1` (artist), `TALB` (album)
- Falls back to filename if no tags found

### Browser APIs Used
- `indexedDB` — persistent storage
- `FileReader` + `DataView` — binary ID3 parsing
- `Audio` + `AudioContext`/`webkitAudioContext` — playback & volume
- `navigator.mediaSession` — system media controls
- `fetch` — lrclib.net lyrics API
- `localStorage` — settings & window position

## File Structure
```
userscript.js   # Single-file userscript (~558 lines)
README.md       # This file
```

## License

MIT — free to use, modify, distribute.

## Credits

- **Author**: deitzu
- **Lyrics API**: [lrclib.net](https://lrclib.net)
- **Icons**: Inline SVGs (public domain / MIT)