// ==UserScript==
// @name         PT Local Music Player (Draggable)
// @namespace    http://tampermonkey.net/
// @version      2.5.0
// @description  Themeable local player with synchronized lyrics, searchable library and metadata tools
// @author       deitzu
// @match        https://pony.town/*
// @grant        none
// ==/UserScript==
(function() {
    'use strict';

    // SVG Icons
    const svgPlay = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>`;
    const svgPause = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;
    const svgPrev = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>`;
    const svgNext = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>`;
    const svgShuffle = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/></svg>`;
    const svgRepeat = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"/></svg>`;
    const svgRepeat1 = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4-4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"/></svg>`;
    const svgVol = `<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>`;
    const svgGear = `<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.49-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/></svg>`;
    const svgTag = `<svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor"><path d="M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.41l9 9c.36.36.86.58 1.41.58s1.05-.22 1.41-.59l7-7c.36-.36.59-.86.59-1.41s-.23-1.06-.59-1.41zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z"/></svg>`;

    const DB_NAME = 'PT_MusicPlayer_DB';
    const STORE_NAME = 'playlist';
    const DB_VERSION = 2;
    const THEMES = [
        { name: 'Tokyo Night', bg: 'rgba(28,28,37,0.90)', card: '#23232e', border: '#454653', text: '#dad7e2', subtext: '#a7a4b5', accent: '#9eacc9' },
        { name: 'Espresso', bg: 'rgba(33,26,30,0.85)', card: '#2b2124', border: '#4a3b40', text: '#d4c5b9', subtext: '#b49c8c', accent: '#c88d75' },
        { name: 'Dracula', bg: 'rgba(34,31,39,0.90)', card: '#29242f', border: '#4a414f', text: '#e3dce6', subtext: '#b7aabf', accent: '#b9a1c8' },
        { name: 'Nord', bg: 'rgba(35,41,45,0.90)', card: '#2d353a', border: '#4a565b', text: '#dde4e0', subtext: '#aab8b2', accent: '#a1bdaf' },
        { name: 'Catppuccin Mocha', bg: 'rgba(31,29,38,0.90)', card: '#25232d', border: '#48414f', text: '#e2dde8', subtext: '#b8aab8', accent: '#c4a0a7' },
        { name: 'Mosswood', bg: 'rgba(34,37,31,0.90)', card: '#2d332b', border: '#505948', text: '#e4e3d8', subtext: '#b4b6a5', accent: '#adb99c' },
        { name: 'Oxblood', bg: 'rgba(36,29,32,0.90)', card: '#34252a', border: '#583941', text: '#e6dadd', subtext: '#c6aeb2', accent: '#c08d91' }
    ];

    let userSettings = JSON.parse(localStorage.getItem('pt_mp_settings')) || {
        lrcMode: 1, lrcStyle: 0, lrcBot: 20, lrcSize: 16, lrcBgOpacity: 0.6, autoFetch: true, autoSelectLrc: true,
        theme: 0, visMode: true, qOffset: true, idleSec: 3.5, idleOp: 0.3, toastNotif: true, autoTag: true,
        rsize: 13, showOri: true, showRom: true, showTrs: true
    };
    if (userSettings.autoSelectLrc === undefined) userSettings.autoSelectLrc = true;
    if (userSettings.rsize === undefined) userSettings.rsize = 13;
    if (userSettings.showOri === undefined) userSettings.showOri = true;
    if (userSettings.showRom === undefined) userSettings.showRom = true;
    if (userSettings.showTrs === undefined) userSettings.showTrs = true;
    if (userSettings.lrcBgOpacity === undefined) userSettings.lrcBgOpacity = 0.6;

    let activeFilters = [];
    let searchQuery = '';

    function openDB() {
        return new Promise((resolve, reject) => {
            const req = indexedDB.open(DB_NAME, DB_VERSION);
            req.onupgradeneeded = (e) => {
                const db = e.target.result;
                if (!db.objectStoreNames.contains(STORE_NAME)) db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
            };
            req.onsuccess = () => resolve(req.result);
            req.onerror = () => reject(req.error);
        });
    }

    function parseNativeID3(file) {
        return new Promise((resolve) => {
            const defName = file.name.replace(/\.[^/.]+$/, "");
            let meta = { title: defName, artist: "", album: "", genre: "" };
            const reader = new FileReader();
            const slice = file.slice(0, Math.min(128 * 1024, file.size));

            reader.onload = (e) => {
                try {
                    const buffer = e.target.result;
                    const view = new DataView(buffer);
                    if (view.byteLength < 10 || view.getUint8(0) !== 0x49 || view.getUint8(1) !== 0x44 || view.getUint8(2) !== 0x33) return resolve(meta);

                    const version = view.getUint8(3);
                    if (version !== 3 && version !== 4) return resolve(meta);

                    const tagSize = (view.getUint8(6) << 21) | (view.getUint8(7) << 14) | (view.getUint8(8) << 7) | view.getUint8(9);
                    let offset = 10;

                    const readString = (buf, start, len, enc) => {
                        try {
                            const arr = new Uint8Array(buf, start, len);
                            let decoder = (enc === 0 || enc === 3) ? new TextDecoder(enc === 3 ? 'utf-8' : 'iso-8859-1') : new TextDecoder('utf-16');
                            return decoder.decode(arr).replace(/\0/g, '').trim();
                        } catch(err) { return ""; }
                    };

                    while (offset < tagSize && offset < buffer.byteLength - 10) {
                        const frameId = String.fromCharCode(view.getUint8(offset), view.getUint8(offset+1), view.getUint8(offset+2), view.getUint8(offset+3));
                        if (!/[A-Z0-9]{4}/.test(frameId)) break;

                        let frameSize = version === 3 ? view.getUint32(offset + 4) :
                                       (view.getUint8(offset+4) << 21) | (view.getUint8(offset+5) << 14) | (view.getUint8(offset+6) << 7) | view.getUint8(offset+7);
                        const frameOffset = offset + 10;
                        if (frameSize > 0 && frameOffset + frameSize <= buffer.byteLength) {
                            const text = readString(buffer, frameOffset + 1, frameSize - 1, view.getUint8(frameOffset));
                            if (frameId === 'TIT2' && text) meta.title = text;
                            if (frameId === 'TPE1' && text) meta.artist = text;
                            if (frameId === 'TALB' && text) meta.album = text;
                            if (frameId === 'TCON' && text) meta.genre = text;
                        }
                        offset += 10 + frameSize;
                    }
                } catch(e) { console.warn("ID3 Parse Failed", e); }
                resolve(meta);
            };
            reader.onerror = () => resolve(meta);
            reader.readAsArrayBuffer(slice);
        });
    }

    async function saveFiles(files) {
        const fileArray = Array.from(files || []);
        if (!fileArray.length) return { added: 0, skipped: 0 };
        const existing = await loadPlaylist();
        const knownKeys = new Set(existing.map(track => track.sourceKey).filter(Boolean));
        const legacyTracks = existing.filter(track => !track.sourceKey);
        const itemsToSave = [];
        let skipped = 0;
        importProgressWrap.style.display = 'flex';
        importProgress.max = fileArray.length;
        importProgress.value = 0;

        for (let i = 0; i < fileArray.length; i++) {
            const file = fileArray[i];
            qs('#pt-mp-header-title').innerText = 'Importing ' + (i + 1) + '/' + fileArray.length;
            importStatusEl.textContent = 'Reading metadata: ' + file.name;
            const meta = await parseNativeID3(file);
            const sourceKey = [String(file.name).toLowerCase(), file.size, file.lastModified || 0].join('::');
            if (knownKeys.has(sourceKey)) {
                skipped++; importProgress.value = i + 1;
                importStatusEl.textContent = 'Skipped duplicate: ' + file.name;
                continue;
            }
            const normalize = value => String(value || '').trim().toLocaleLowerCase();
            const legacyDuplicate = legacyTracks.find(track =>
                normalize(track.name) === normalize(meta.title) &&
                normalize(track.artist) === normalize(meta.artist) &&
                normalize(track.album) === normalize(meta.album) &&
                track.blob && Number(track.blob.size) === Number(file.size)
            );
            if (legacyDuplicate && !confirm('A similar track may already exist: "' + (meta.title || file.name) + '". Import anyway?')) {
                skipped++; importProgress.value = i + 1;
                importStatusEl.textContent = "Skipped: " + (meta.title || file.name);
                continue;
            }
            knownKeys.add(sourceKey);
            const defaultTags = (userSettings.autoTag && meta.genre) ? meta.genre.split(',').map(tag => tag.trim()).filter(Boolean) : [];
            itemsToSave.push({
                name: meta.title, artist: meta.artist, album: meta.album, blob: file,
                lyrics: '', romanizedLyrics: '', translatedLyrics: '', lrcOffset: 0, tags: defaultTags,
                sourceKey, sourceName: file.name, sourceSize: file.size, sourceLastModified: file.lastModified || 0
            });
            importProgress.value = i + 1;
            importStatusEl.textContent = (i + 1) + '/' + fileArray.length + ' processed · ' + itemsToSave.length + ' new · ' + skipped + ' skipped';
        }
        if (itemsToSave.length) {
            const db = await openDB();
            const tx = db.transaction(STORE_NAME, 'readwrite');
            itemsToSave.forEach(item => tx.objectStore(STORE_NAME).add(item));
            await new Promise((resolve, reject) => {
                tx.oncomplete = resolve;
                tx.onerror = () => reject(tx.error || new Error('Failed to save to the database'));
                tx.onabort = () => reject(tx.error || new Error('Import was cancelled'));
            });
            db.close();
        }
        importProgress.value = fileArray.length;
        importStatusEl.textContent = 'Import complete: ' + itemsToSave.length + ' added, ' + skipped + ' duplicates skipped';
        return { added: itemsToSave.length, skipped };
    }

    async function loadPlaylist() {
        const db = await openDB();
        return new Promise(resolve => {
            const tx = db.transaction(STORE_NAME, 'readonly');
            const req = tx.objectStore(STORE_NAME).getAll();
            req.onsuccess = () => resolve(req.result);
        });
    }

    async function deleteTrack(id) {
        const db = await openDB(); const tx = db.transaction(STORE_NAME, 'readwrite');
        tx.objectStore(STORE_NAME).delete(id); return new Promise(res => tx.oncomplete = res);
    }

    async function updateTrack(track) {
        const db = await openDB(); const tx = db.transaction(STORE_NAME, 'readwrite');
        tx.objectStore(STORE_NAME).put(track); return new Promise(res => tx.oncomplete = res);
    }

    async function clearAll() {
        const db = await openDB(); const tx = db.transaction(STORE_NAME, 'readwrite');
        tx.objectStore(STORE_NAME).clear(); return new Promise(res => tx.oncomplete = res);
    }

    function formatTime(sec) {
        if (isNaN(sec)) return "00:00";
        let m = Math.floor(sec / 60), s = Math.floor(sec % 60);
        return (m < 10 ? '0'+m : m) + ':' + (s < 10 ? '0'+s : s);
    }

    let playlist = [], currentIndex = -1, isDragging = false, isSeeking = false, idleTimer;
    let isShuffle = false, repeatMode = 0, parsedLyrics = [];

    const audio = new Audio();
    let audioCtx, gainNode, analyser, dataArray, visCtx, visId;

    const toast = document.createElement('div');
    toast.id = 'pt-toast';
    document.body.appendChild(toast);
    let toastTimer;
    function showToast(track, dur) {
        const head = document.createElement('div');
        head.id = 'toast-head';
        head.textContent = '🎵 NOW PLAYING • ' + formatTime(dur);
        const title = document.createElement('div');
        title.id = 'toast-title';
        title.textContent = track.name || 'Untitled';
        const artist = document.createElement('div');
        artist.id = 'toast-artist';
        artist.textContent = track.artist || 'Unknown';
        const progress = document.createElement('div');
        progress.id = 'toast-progress';
        toast.replaceChildren(head, title, artist, progress);
        toast.classList.remove('show');
        void toast.offsetWidth;
        toast.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => toast.classList.remove('show'), 3000);
    }
    function initWebAudio() {
        if (audioCtx) return;
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        const source = audioCtx.createMediaElementSource(audio);
        analyser = audioCtx.createAnalyser();
        analyser.fftSize = 64;
        gainNode = audioCtx.createGain();
        source.connect(analyser).connect(gainNode).connect(audioCtx.destination);
        gainNode.gain.value = qs('#pt-vol').value;
        dataArray = new Uint8Array(analyser.frequencyBinCount);
        visCtx = qs('#pt-vis-canvas').getContext('2d');
    }

    function drawVis() {
        if(!userSettings.visMode || isMinimized || audio.paused) return;
        visId = requestAnimationFrame(drawVis);
        analyser.getByteFrequencyData(dataArray);
        visCtx.clearRect(0, 0, 200, 40);
        visCtx.fillStyle = THEMES[userSettings.theme].accent;
        for(let i = 0; i < 16; i++) {
            let v = dataArray[i + 2] / 255.0;
            let h = v * 40;
            visCtx.fillRect(i * 12 + 6, 40 - h, 8, h);
        }
    }

    const ROMANIZATION_LANGS = new Set([
        'ar', 'am', 'bn', 'be', 'gu', 'hi', 'ja', 'kn',
        'my', 'ru', 'sr', 'ta', 'te', 'uk', 'ko', 'zh-CN'
    ]);

    function detectRomanizationLanguage(text) {
        if (!text || !/[^\x00-\x7F]/.test(text)) return null;

        // Japanese: kana makes the distinction from Chinese reliable enough for this client.
        if (/[\u3040-\u30FF\u31F0-\u31FF]/.test(text)) return 'ja';
        if (/[\uAC00-\uD7AF]/.test(text)) return 'ko';

        // CJK without kana is treated as Simplified Chinese on a best-effort basis.
        if (/[\u3400-\u4DBF\u4E00-\u9FFF]/.test(text)) return 'zh-CN';

        // Cyrillic languages with distinctive characters first.
        if (/[ЇїЄєІіҐґ]/.test(text)) return 'uk';
        if (/[Ўў]/.test(text)) return 'be';
        if (/[ЈјЉљЊњЏџЂђЋћ]/.test(text)) return 'sr';
        if (/[А-Яа-яЁё]/.test(text)) return 'ru';

        if (/[\u0900-\u097F]/.test(text)) return 'hi';
        if (/[\u0980-\u09FF]/.test(text)) return 'bn';
        if (/[\u0A80-\u0AFF]/.test(text)) return 'gu';
        if (/[\u0C80-\u0CFF]/.test(text)) return 'kn';
        if (/[\u0B80-\u0BFF]/.test(text)) return 'ta';
        if (/[\u0C00-\u0C7F]/.test(text)) return 'te';
        if (/[\u1000-\u109F]/.test(text)) return 'my';
        if (/[\u1200-\u137F]/.test(text)) return 'am';
        if (/[\u0600-\u06FF\u0750-\u077F]/.test(text)) return 'ar';

        return null;
    }

    function hasUsableRomanization(originalText, romanizedText) {
        if (!romanizedText || !romanizedText.trim()) return false;

        const original = originalText.trim().normalize('NFKC');
        const romanized = romanizedText.trim().normalize('NFKC');

        // Never accept the original script as a successful romanization.
        if (romanized === original) return false;
        if (!/[A-Za-z]/.test(romanized)) return false;

        return true;
    }

    function extractRomanizedSentence(data, originalText) {
        // Preferred modern response shape when dj=1 is enabled:
        // { sentences: [{ src_translit: "..." }, ...] }
        if (data && Array.isArray(data.sentences)) {
            const result = data.sentences
                .map(sentence => typeof sentence.src_translit === 'string' ? sentence.src_translit : '')
                .filter(Boolean)
                .join(' ')
                .trim();

            if (hasUsableRomanization(originalText, result)) return result;
        }

        // Legacy array response: transliteration is commonly in segment[2].
        if (Array.isArray(data) && Array.isArray(data[0])) {
            const candidates = [];

            data[0].forEach(segment => {
                if (!Array.isArray(segment)) return;
                if (typeof segment[2] === 'string' && segment[2].trim()) {
                    candidates.push(segment[2].trim());
                }
                if (typeof segment[3] === 'string' && segment[3].trim()) {
                    candidates.push(segment[3].trim());
                }
            });

            // Prefer a candidate that actually differs from the source.
            for (const candidate of candidates) {
                if (hasUsableRomanization(originalText, candidate)) return candidate;
            }
        }

        return '';
    }

    async function romanizeLine(text, language) {
        const params = new URLSearchParams();
        params.set('client', 'gtx');
        params.set('sl', language);
        params.set('tl', 'en');
        params.set('hl', 'en');
        params.set('dj', '1');
        params.set('source', 'bubble');
        params.set('ie', 'UTF-8');
        params.set('oe', 'UTF-8');
        params.append('dt', 't');
        params.append('dt', 'rm');
        params.set('q', text);

        const response = await fetch(
            'https://translate.googleapis.com/translate_a/single?' + params.toString()
        );

        if (!response.ok) throw new Error('Romanization HTTP ' + response.status);

        const data = await response.json();

        return {
            romanized: extractRomanizedSentence(data, text),
            translated: Array.isArray(data?.sentences)
                ? data.sentences
                    .map(sentence => sentence.trans || '')
                    .filter(Boolean)
                    .join(' ')
                    .trim()
                : ''
        };
    }

    function parseTimestampedLyrics(text) {
        return text.split(/\r?\n/).map(line => {
            const match = line.match(/^(\[\d+:\d+(?:\.\d+)?\])(.*)$/);
            return match ? { tag: match[1], text: match[2].trim() } : null;
        });
    }

    async function processRomanization(track) {
        if (!track || !track.lyrics) return;

        const sourceLanguage = detectRomanizationLanguage(track.lyrics);
        if (!sourceLanguage || !ROMANIZATION_LANGS.has(sourceLanguage)) return;

        const sourceLines = parseTimestampedLyrics(track.lyrics);
        const sourceTextLines = sourceLines.filter(Boolean).map(line => line.text);
        if (!sourceTextLines.some(text => text)) return;

        // Re-process stale caches from older builds, including "romaji == original".
        const cachedLines = parseTimestampedLyrics(track.romanizedLyrics || '')
            .filter(Boolean)
            .map(line => line.text);

        const cacheLooksUsable =
            cachedLines.length === sourceTextLines.length &&
            sourceTextLines.some((text, i) => hasUsableRomanization(text, cachedLines[i]));

        if (cacheLooksUsable && track.translatedLyrics) return;

        displayLyric("Translating and romanizing...");

        const romanizedByIndex = new Array(sourceLines.length).fill('');
        const translatedByIndex = new Array(sourceLines.length).fill('');
        let cursor = 0;

        async function worker() {
            while (true) {
                const index = cursor++;
                if (index >= sourceLines.length) return;

                const line = sourceLines[index];
                if (!line || !line.text) continue;

                // Latin-only fragments need no romanization and should stay unchanged.
                if (!/[^\x00-\x7F]/.test(line.text)) {
                    romanizedByIndex[index] = line.text;
                    translatedByIndex[index] = line.text;
                    continue;
                }

                try {
                    const result = await romanizeLine(line.text, sourceLanguage);

                    romanizedByIndex[index] = hasUsableRomanization(line.text, result.romanized)
                        ? result.romanized
                        : '';

                    translatedByIndex[index] = result.translated || '';
                } catch (err) {
                    console.warn('[PT Player] Romanization failed:', err);
                }

                // This endpoint is unofficial. Keep the request rate modest.
                await new Promise(resolve => setTimeout(resolve, 120));
            }
        }

        await Promise.all([worker(), worker()]);

        track.romanizedLyrics = sourceLines.map((line, i) => {
            if (!line) return '';
            return line.tag + ' ' + (romanizedByIndex[i] || line.text);
        }).join('\n');

        track.translatedLyrics = sourceLines.map((line, i) => {
            if (!line) return '';
            return line.tag + ' ' + (translatedByIndex[i] || line.text);
        }).join('\n');

        await updateTrack(track);
    }

    const container = document.createElement('div');
    container.id = 'pt-mp-container';
    container.innerHTML = `
        <style>
            :root {
                --pt-th: #ffb74d;
                --pt-bg: rgba(20,20,20,0.85);
                --pt-card: #222;
                --pt-border: #444;
                --pt-text: #fff;
                --pt-subtext: #aaa;
                --pt-rsize: 13px;
            }
            #pt-toast { position: fixed; top: 20px; right: -300px; background: var(--pt-bg); color: var(--pt-text); padding: 10px 14px 12px; border-radius: 4px; font-family: sans-serif; z-index: 9999999; transition: right 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28); pointer-events: none; border-left: 4px solid var(--pt-th); box-shadow: 0 4px 12px rgba(0,0,0,0.6); width: 220px; box-sizing: border-box; }
            #pt-toast.show { right: 20px; }
            #toast-head { font-size: 9px; color: var(--pt-th); font-weight: bold; margin-bottom: 4px; letter-spacing: 0.5px;}
            #toast-title { font-size: 13px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 2px;}
            #toast-artist { font-size: 10px; color: var(--pt-subtext); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            #toast-progress { position: absolute; bottom: 0; left: 0; height: 2px; background: var(--pt-th); width: 100%; transform-origin: left; }
            #pt-toast.show #toast-progress { animation: toastBar 3s linear forwards; }
            @keyframes toastBar { 0% { transform: scaleX(1); } 100% { transform: scaleX(0); } }

            #pt-mp-container { position: fixed; top: 15px; right: 15px; z-index: 999999; background: var(--pt-bg); color: var(--pt-text); padding: 10px; border-radius: 8px; font-family: sans-serif; font-size: 12px; width: min(220px, calc(100vw - 24px)); max-width: calc(100vw - 24px); box-sizing: border-box; box-shadow: 0 4px 10px rgba(0,0,0,0.5); backdrop-filter: blur(5px); border: 1px solid var(--pt-border); user-select: none; -webkit-user-select: none; transition: opacity 0.3s, width 0.2s, padding 0.2s; }
            #pt-mp-container.minimized { width: auto; padding: 5px 10px; }
            #pt-mp-container.minimized #pt-mp-body, #pt-mp-container.minimized #pt-settings-panel { display: none !important; }
            #pt-mp-header { display: flex; justify-content: space-between; align-items: center; cursor: grab; padding-bottom: 5px; margin-bottom: 5px; border-bottom: 1px solid var(--pt-border); }
            #pt-mp-container.minimized #pt-mp-header { border-bottom: none; padding-bottom: 0; margin-bottom: 0; }
            #pt-mp-header:active { cursor: grabbing; }
            #pt-mp-header-title { font-weight: bold; color: var(--pt-th); flex-grow: 1; pointer-events: none;}
            .pt-head-btns { display: flex; gap: 12px; }
            .pt-head-btn { background: none; border: none; color: var(--pt-subtext); cursor: pointer; font-weight: bold; font-size: 14px; padding: 2px; display:flex; align-items:center; justify-content:center;}

            #pt-track-info-wrap { position: relative; height: 45px; margin-bottom: 8px; display: flex; flex-direction: column; justify-content: center; align-items: center; overflow: hidden; background: rgba(0,0,0,0.3); border-radius: 6px;}
            #pt-vis-canvas { position: absolute; bottom: 0; left: 0; width: 100%; height: 100%; opacity: 0.3; pointer-events: none; }
            .pt-scroll-box { width: 190px; overflow: hidden; white-space: nowrap; text-align: center; position: relative; z-index: 2;}
            #pt-track-title { display: inline-block; font-weight: bold; font-size: 13px; color: var(--pt-text); }
            #pt-track-artist { font-size: 10px; color: var(--pt-th); margin-top: 2px; position: relative; z-index: 2;}
            @keyframes marquee { 0% { transform: translateX(50%); } 100% { transform: translateX(-100%); } }
            .is-marquee { animation: marquee 6s linear infinite; padding-left: 100%;}

            #pt-settings-panel { display: none; background: var(--pt-card); border: 1px solid var(--pt-border); padding: 8px; border-radius: 6px; margin-bottom: 8px; font-size: 11px; max-height: 250px; overflow-y:auto;}
            .pt-set-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }
            .pt-set-row select, .pt-set-row input[type="range"] { width: 85px; background: var(--pt-bg); color: var(--pt-text); border: 1px solid var(--pt-border); border-radius:3px; outline:none;}
            .pt-mp-controls, .pt-mp-controls-2 { display: flex; gap: 4px; margin-bottom: 8px; align-items: center;}
            .pt-mp-controls button, .pt-btn { margin: 0; display: flex; align-items: center; justify-content: center; background: var(--pt-card); color: var(--pt-text); border: none; border-bottom: 1px solid var(--pt-border); border-radius: 4px; cursor: pointer; font-size: 12px; flex: 1 1 0; text-align: center; height: 26px; box-sizing: border-box; }
            .pt-mp-controls button:active, .pt-btn:active { filter: brightness(1.2); }
            .btn-active { background: var(--pt-th) !important; color: var(--pt-bg) !important; border-color: var(--pt-th) !important;}

            #pt-seek-container { display: flex; flex-direction: column; margin-bottom: 8px; }
            #pt-time { font-size: 10px; text-align: right; color: var(--pt-subtext); margin-bottom: 2px; }
            input[type="range"] { -webkit-appearance: none; width: 100%; height: 6px; background: var(--pt-border); border-radius: 3px; outline: none; }
            input[type="range"]::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 12px; height: 12px; border-radius: 50%; background: var(--pt-th); cursor: pointer; }
            #pt-vol-container { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; border-top: 1px dashed var(--pt-border); padding-top: 8px; }
            #pt-vol-icon { color: var(--pt-subtext); display: flex; align-items: center; }

            #pt-tag-filter-container { display:flex; padding: 4px 0 0 0; border-top: 1px solid var(--pt-border); gap: 4px; align-items: center; margin-top: 4px; }
            #pt-tag-filter { flex-grow:1; background:var(--pt-card); color:var(--pt-text); border:1px solid var(--pt-border); border-radius:3px; outline:none; font-size:11px; padding:2px; cursor: pointer; }
            #pt-active-filters { display: block; padding: 4px 0 4px 0; border-bottom: 1px solid var(--pt-border); min-height: 18px;}
            .pt-chip { background: var(--pt-th); color: var(--pt-bg); padding: 2px 6px; border-radius: 8px; font-size: 9px; cursor: pointer; display: inline-block; margin: 2px 2px 2px 0; font-weight: bold;}

            #pt-mp-list { max-height: min(180px, 28vh); overflow-y: auto; padding-top: 4px; display: block; }
            #pt-search-row { display:flex; gap:6px; align-items:center; margin:5px 0 3px; }
            #pt-search { width:100%; min-width:0; box-sizing:border-box; background:var(--pt-card); color:var(--pt-text); border:1px solid var(--pt-border); border-radius:4px; padding:6px; font:inherit; outline:none; }
            #pt-search:focus { border-color:var(--pt-th); }
            #pt-search-count { flex-shrink:0; color:var(--pt-subtext); font-size:10px; white-space:nowrap; }
            #pt-import-progress-wrap { display:none; align-items:center; gap:6px; padding:4px 0; color:var(--pt-subtext); font-size:10px; }
            #pt-import-progress { width:48px; height:8px; accent-color:var(--pt-th); }
            #pt-import-status { min-width:0; overflow-wrap:anywhere; }
            .pt-mp-item-info { min-width:0; max-width:none; }
            .pt-mp-acts { flex-shrink:0; gap:3px; }
            .pt-mp-edit { background:var(--pt-card); color:var(--pt-text); border:0; border-bottom:1px solid var(--pt-border); border-radius:3px; cursor:pointer; font-size:13px; min-width:22px; padding:2px 4px; }
            .pt-mp-edit:hover { color:var(--pt-th); }
            #pt-meta-modal { display:none; position:fixed; inset:0; z-index:1000001; }
            #pt-meta-modal.open { display:block; }
            .pt-meta-backdrop { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; box-sizing:border-box; padding:16px; background:rgba(0,0,0,.72); }
            .pt-meta-dialog { width:min(320px,100%); box-sizing:border-box; padding:14px; border:1px solid var(--pt-border); border-radius:8px; background:var(--pt-card); color:var(--pt-text); box-shadow:0 8px 28px rgba(0,0,0,.65); font:12px sans-serif; }
            .pt-meta-dialog h3 { margin:0 0 12px; color:var(--pt-th); font-size:14px; }
            .pt-meta-dialog label { display:block; margin:8px 0; color:var(--pt-subtext); }
            .pt-meta-dialog input { display:block; width:100%; box-sizing:border-box; margin-top:4px; padding:7px; background:var(--pt-bg); color:var(--pt-text); border:1px solid var(--pt-border); border-radius:4px; font:inherit; }
            .pt-meta-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:14px; }
            .pt-meta-actions button { min-height:32px; padding:5px 12px; color:var(--pt-text); background:var(--pt-bg); border:1px solid var(--pt-border); border-radius:4px; }
            .pt-meta-actions button[type=submit] { color:var(--pt-bg); background:var(--pt-th); border-color:var(--pt-th); }
            @media (max-width:480px) {
                #pt-mp-container { width:min(300px, calc(100vw - 16px)); max-width:calc(100vw - 16px); }
                .pt-mp-controls button, .pt-btn, .pt-head-btn { min-height:32px; }
                #pt-mp-list { max-height:min(190px, 30vh); }
                #pt-lyric-overlay { max-width:94vw; }
            }
            .pt-mp-item { display: flex; justify-content: space-between; align-items: center; padding: 5px 0; border-bottom: 1px solid var(--pt-border);}
            .pt-mp-item.active { color: var(--pt-th); font-weight: bold; }
            .pt-mp-item-info { display: flex; flex-direction: column; cursor: pointer; overflow: hidden; max-width: 120px; flex-grow:1;}
            .pt-mp-item-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .pt-mp-item-artist { font-size: 9px; color: var(--pt-subtext); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .pt-mp-acts { display: flex; gap: 4px; }
            .pt-mp-lrc-btn, .pt-mp-del { background:var(--pt-card); border: none; border-bottom: 1px solid var(--pt-border); color: var(--pt-text); border-radius:3px; cursor: pointer; font-size: 10px; padding: 2px 4px; display:flex; align-items:center; justify-content:center;}
            .pt-mp-del { color: #e57373; font-weight: bold;}
            #pt-embedded-lrc { display: none; text-align: center; color: var(--pt-th); font-size: 11px; padding: 4px; border-bottom: 1px dashed var(--pt-border); margin-bottom: 5px; min-height: 15px;}
            input[type="file"] { display: none; }
        </style>
        <div id="pt-mp-header">
            <div id="pt-mp-header-title">🎵 PT Player</div>
            <div class="pt-head-btns">
                <button id="pt-set-btn" class="pt-head-btn">${svgGear}</button>
                <button id="pt-min-btn" class="pt-head-btn">_</button>
            </div>
        </div>
        <div id="pt-settings-panel">
            <div class="pt-set-row"><span>Theme:</span><select id="pt-set-th">${THEMES.map((t,i) => `<option value="${i}">${t.name}</option>`).join('')}</select></div>
            <div class="pt-set-row"><span>Lyrics Mode:</span><select id="pt-set-mode"><option value="0">Off</option><option value="1">Overlay</option><option value="2">Embedded</option></select></div>
            <div class="pt-set-row"><span>Lyrics Style:</span><select id="pt-set-style"><option value="0">YouTube</option><option value="1">Glow</option><option value="2">Glass</option></select></div>

            <div class="pt-set-row" style="justify-content: flex-start; gap: 8px; flex-wrap: wrap; border-top:1px dashed var(--pt-border); padding-top:6px; margin-top:6px;">
                <label style="display:flex; align-items:center; gap:4px;"><input type="checkbox" id="pt-set-showori"> Original</label>
                <label style="display:flex; align-items:center; gap:4px;"><input type="checkbox" id="pt-set-showrom"> Romaji</label>
                <label style="display:flex; align-items:center; gap:4px;"><input type="checkbox" id="pt-set-showtrs"> Translation</label>
            </div>

            <div class="pt-set-row" style="margin-top:4px;"><span>Subtitle Size:</span><input type="range" id="pt-set-rsize" min="10" max="20" value="13"></div>
            <div class="pt-set-row"><span>Lyrics Y Position:</span><input type="range" id="pt-set-bot" min="5" max="50" value="20"></div>
            <div class="pt-set-row"><span>Font Size:</span><input type="range" id="pt-set-size" min="12" max="24" value="16"></div>
            <div class="pt-set-row"><span>Subtitle BG:</span><input type="range" id="pt-set-bgop" min="0" max="1" step="0.05" value="0.6"></div>
            <div class="pt-set-row"><span>Idle Fade (s):</span><input type="range" id="pt-set-idlesec" min="2" max="10" step="0.5" value="3.5"></div>
            <div class="pt-set-row"><span>Idle Opacity:</span><input type="range" id="pt-set-idleop" min="0.1" max="1" step="0.1" value="0.3"></div>
            <div class="pt-set-row" style="margin-top:8px;">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-fetch"> Auto-Fetch API</label>
            </div>
            <div class="pt-set-row">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-autoselect"> Auto-Select (1st Match)</label>
            </div>
            <div class="pt-set-row">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-autotag"> Auto-Tag (ID3)</label>
            </div>
            <div class="pt-set-row">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-vis"> Audio Visualizer</label>
            </div>
            <div class="pt-set-row">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-qoff"> Quick LRC Offset</label>
            </div>
            <div class="pt-set-row">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-toast"> Toast Notification</label>
            </div>
            <div class="pt-set-row" style="border-top:1px dashed var(--pt-border); padding-top:8px; margin-top:8px;">
                <button id="pt-clear-all" class="pt-btn" style="background:#b71c1c; margin: 0 auto; width: 100%;">Danger: Clear All Tracks</button>
            </div>
        </div>
        <div id="pt-mp-body">
            <div id="pt-track-info-wrap">
                <canvas id="pt-vis-canvas" width="200" height="40"></canvas>
                <div class="pt-scroll-box"><span id="pt-track-title">-</span></div>
                <div id="pt-track-artist">-</div>
            </div>
            <div id="pt-embedded-lrc"></div>
            <div id="pt-seek-container">
                <div id="pt-time">00:00 / 00:00</div>
                <input type="range" id="pt-seek" min="0" value="0" step="1">
            </div>
            <div id="pt-vol-container">
                <span id="pt-vol-icon">${svgVol}</span>
                <input type="range" id="pt-vol" min="0" max="1" step="0.05" value="1">
            </div>
            <div class="pt-mp-controls">
                <button id="pt-shuffle" title="Shuffle">${svgShuffle}</button>
                <button id="pt-prev" title="Previous">${svgPrev}</button>
                <button id="pt-play" title="Play">${svgPlay}</button>
                <button id="pt-next" title="Next">${svgNext}</button>
                <button id="pt-repeat" title="Repeat">${svgRepeat}</button>
            </div>
            <div class="pt-mp-controls-2">
                <label class="pt-btn">+ Add<input type="file" id="pt-file" accept="audio/*" multiple></label>
                <button id="pt-toggle-list" class="pt-btn">▼ List</button>
            </div>
            <div id="pt-tag-filter-container">
                <span style="font-size:10px; color:var(--pt-subtext);">Filter:</span>
                <select id="pt-tag-filter"><option value="" disabled selected>+ Add Tag</option></select>
                <button id="pt-clear-filters" class="pt-btn" style="width: auto; padding: 0 6px; height: 20px; font-size:10px;">Clear</button>
            </div>
            <div id="pt-active-filters"></div>
            <div id="pt-search-row">
                <input type="search" id="pt-search" placeholder="Search title, artist, album, tags..." aria-label="Search playlist">
                <span id="pt-search-count">0 / 0</span>
            </div>
            <div id="pt-import-progress-wrap" aria-live="polite">
                <progress id="pt-import-progress" max="100" value="0"></progress>
                <span id="pt-import-status"></span>
            </div>
            <div id="pt-mp-list"></div>
        </div>
        <input type="file" id="pt-lrc-file" accept=".lrc" style="display:none;">
    `;
    document.body.appendChild(container);

    const overlay = document.createElement('div');
    overlay.id = 'pt-lyric-overlay';
    overlay.innerHTML = `<style>
        #pt-lyric-overlay { position: fixed; left: 50%; transform: translateX(-50%); text-align: center; pointer-events: none; z-index: 999998; transition: bottom 0.2s, opacity .22s; white-space: pre-wrap; font-family: sans-serif; display: flex; flex-direction: column; align-items: center; gap: 4px; max-width:94vw; box-sizing:border-box; }
        #pt-lyric-text { max-width:min(90vw,900px); width:max-content; box-sizing:border-box; overflow-wrap:anywhere; white-space:pre-wrap; opacity:1; transform:translateY(0); transition:opacity .22s ease, transform .22s ease; }
        .lyric-yt { background: rgba(0,0,0,var(--pt-lrc-bg,0.6)); padding: 3px 10px; border-radius: 3px; color: #fff; text-shadow: none; }
        .lyric-glow { background: transparent; color: #fff; text-shadow: 2px 2px 3px #000, -2px -2px 3px #000, 2px -2px 3px #000, -2px 2px 3px #000; }
        .lyric-glass { background: rgba(255,255,255,0.1); backdrop-filter: blur(6px); padding: 6px 14px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.2); color: #fff; text-shadow: 1px 1px 2px #000;}
        #pt-qoff-panel { pointer-events: auto; display: none; background: rgba(0,0,0,0.5); padding: 2px 6px; border-radius: 12px; font-size: 10px; color: #fff; border: 1px solid #555; align-items: center; gap: 6px; backdrop-filter: blur(2px);}
        .pt-qoff-btn { background: none; border: none; color: #fff; font-weight: bold; cursor: pointer; padding: 2px 6px; }
        .pt-qoff-btn:active { color: var(--pt-th); }
        .pt-lrc-ori { font-weight: bold; }
        .pt-lrc-rm { font-weight: normal; font-size: var(--pt-rsize); opacity: 0.85; margin-top: 2px; }
        .pt-lrc-tr { font-weight: normal; font-size: var(--pt-rsize); opacity: 0.85; margin-top: 2px; font-style: italic; }
    </style>
    <div id="pt-lyric-text"></div>
    <div id="pt-qoff-panel"><button id="qoff-dec" class="pt-qoff-btn">-</button><span id="qoff-val">0.0s</span><button id="qoff-inc" class="pt-qoff-btn">+</button></div>`;
    document.body.appendChild(overlay);

    const metaModal = document.createElement('div');
    metaModal.id = 'pt-meta-modal';
    metaModal.innerHTML = "<div class='pt-meta-backdrop' id='pt-meta-backdrop'><form class='pt-meta-dialog' id='pt-meta-form' role='dialog' aria-modal='true' aria-labelledby='pt-meta-heading'><h3 id='pt-meta-heading'>Edit track metadata</h3><label>Title<input id='pt-meta-name' maxlength='240' required></label><label>Artist<input id='pt-meta-artist' maxlength='240'></label><label>Album<input id='pt-meta-album' maxlength='240'></label><label>Tags (comma separated)<input id='pt-meta-tags' maxlength='500'></label><div class='pt-meta-actions'><button type='button' id='pt-meta-cancel'>Cancel</button><button type='submit'>Save</button></div></form></div>";
    document.body.appendChild(metaModal);

    const qs = sel => container.querySelector(sel);
    const headerEl = qs('#pt-mp-header'), titleEl = qs('#pt-track-title'), artistEl = qs('#pt-track-artist');
    const playBtn = qs('#pt-play'), prevBtn = qs('#pt-prev'), nextBtn = qs('#pt-next'), fileInput = qs('#pt-file');
    const listEl = qs('#pt-mp-list'), minBtn = qs('#pt-min-btn'), setBtn = qs('#pt-set-btn');
    const seekEl = qs('#pt-seek'), timeEl = qs('#pt-time'), shuffleBtn = qs('#pt-shuffle'), repeatBtn = qs('#pt-repeat');
    const toggleListBtn = qs('#pt-toggle-list'), volEl = qs('#pt-vol'), setPanel = qs('#pt-settings-panel');
    const lrcFileIn = qs('#pt-lrc-file'), embLrcEl = qs('#pt-embedded-lrc'), ovLrcEl = overlay.querySelector('#pt-lyric-text');
    const qoffPanel = overlay.querySelector('#pt-qoff-panel'), qoffVal = overlay.querySelector('#qoff-val');
    const tagFilterEl = qs('#pt-tag-filter'), tagFilterContainer = qs('#pt-tag-filter-container'), activeFiltersEl = qs('#pt-active-filters');
    const searchRowEl = qs('#pt-search-row'), searchEl = qs('#pt-search'), searchCountEl = qs('#pt-search-count');
    const importProgressWrap = qs('#pt-import-progress-wrap'), importProgress = qs('#pt-import-progress'), importStatusEl = qs('#pt-import-status');
    const metaFormEl = metaModal.querySelector('#pt-meta-form'), metaNameEl = metaModal.querySelector('#pt-meta-name');
    const metaArtistEl = metaModal.querySelector('#pt-meta-artist'), metaAlbumEl = metaModal.querySelector('#pt-meta-album'), metaTagsEl = metaModal.querySelector('#pt-meta-tags');
    let editingTrackId = null;
    function closeMetadataEditor() { metaModal.classList.remove('open'); editingTrackId = null; }
    function openMetadataEditor(track) {
        editingTrackId = track.id;
        metaNameEl.value = track.name || '';
        metaArtistEl.value = track.artist || '';
        metaAlbumEl.value = track.album || '';
        metaTagsEl.value = (track.tags || (track.tag ? [track.tag] : [])).join(', ');
        metaModal.classList.add('open');
        metaNameEl.focus();
    }
    metaModal.addEventListener('click', e => { if (e.target.id === 'pt-meta-backdrop') closeMetadataEditor(); });
    metaModal.querySelector('#pt-meta-cancel').onclick = closeMetadataEditor;
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && metaModal.classList.contains('open')) closeMetadataEditor(); });
    metaFormEl.addEventListener('submit', async e => {
        e.preventDefault();
        const track = playlist.find(item => item.id === editingTrackId);
        if (!track) { closeMetadataEditor(); return; }
        track.name = metaNameEl.value.trim() || 'Untitled';
        track.artist = metaArtistEl.value.trim();
        track.album = metaAlbumEl.value.trim();
        track.tags = metaTagsEl.value.split(',').map(tag => tag.trim()).filter(Boolean);
        track.tag = null;
        await updateTrack(track);
        if (currentIndex >= 0 && playlist[currentIndex] && playlist[currentIndex].id === track.id) {
            titleEl.innerText = track.name;
            artistEl.innerText = track.artist || 'Unknown Artist';
            checkMarquee();
            updateMediaSession(track);
        }
        closeMetadataEditor();
        await refreshUI();
    });

    function resetIdle() {
        container.style.opacity = 1; clearTimeout(idleTimer);
        if (!isDragging) idleTimer = setTimeout(() => container.style.opacity = userSettings.idleOp, userSettings.idleSec * 1000);
    }

    qs('#pt-set-mode').value = userSettings.lrcMode; qs('#pt-set-style').value = userSettings.lrcStyle;
    qs('#pt-set-bot').value = userSettings.lrcBot; qs('#pt-set-size').value = userSettings.lrcSize;
    qs('#pt-set-bgop').value = userSettings.lrcBgOpacity ?? 0.6;
    qs('#pt-set-fetch').checked = userSettings.autoFetch; qs('#pt-set-autoselect').checked = userSettings.autoSelectLrc;
    qs('#pt-set-th').value = userSettings.theme; qs('#pt-set-vis').checked = userSettings.visMode;
    qs('#pt-set-qoff').checked = userSettings.qOffset; qs('#pt-set-idlesec').value = userSettings.idleSec;
    qs('#pt-set-idleop').value = userSettings.idleOp; qs('#pt-set-toast').checked = userSettings.toastNotif;
    qs('#pt-set-autotag').checked = userSettings.autoTag;
    qs('#pt-set-rsize').value = userSettings.rsize;
    qs('#pt-set-showori').checked = userSettings.showOri;
    qs('#pt-set-showrom').checked = userSettings.showRom;
    qs('#pt-set-showtrs').checked = userSettings.showTrs;

    function applySettings() {
        userSettings = {
            lrcMode: parseInt(qs('#pt-set-mode').value), lrcStyle: parseInt(qs('#pt-set-style').value),
            lrcBot: parseInt(qs('#pt-set-bot').value), lrcSize: parseInt(qs('#pt-set-size').value),
            lrcBgOpacity: parseFloat(qs('#pt-set-bgop').value),
            autoFetch: qs('#pt-set-fetch').checked, autoSelectLrc: qs('#pt-set-autoselect').checked,
            theme: parseInt(qs('#pt-set-th').value), visMode: qs('#pt-set-vis').checked, qOffset: qs('#pt-set-qoff').checked,
            idleSec: parseFloat(qs('#pt-set-idlesec').value), idleOp: parseFloat(qs('#pt-set-idleop').value),
            toastNotif: qs('#pt-set-toast').checked, autoTag: qs('#pt-set-autotag').checked,
            rsize: parseInt(qs('#pt-set-rsize').value),
            showOri: qs('#pt-set-showori').checked,
            showRom: qs('#pt-set-showrom').checked,
            showTrs: qs('#pt-set-showtrs').checked
        };
        localStorage.setItem('pt_mp_settings', JSON.stringify(userSettings));

        const th = THEMES[userSettings.theme];
        document.documentElement.style.setProperty('--pt-th', th.accent);
        document.documentElement.style.setProperty('--pt-bg', th.bg);
        document.documentElement.style.setProperty('--pt-card', th.card);
        document.documentElement.style.setProperty('--pt-border', th.border);
        document.documentElement.style.setProperty('--pt-text', th.text);
        document.documentElement.style.setProperty('--pt-subtext', th.subtext);
        document.documentElement.style.setProperty('--pt-rsize', userSettings.rsize + 'px');
        document.documentElement.style.setProperty('--pt-lrc-bg', String(userSettings.lrcBgOpacity ?? 0.6));

        overlay.style.display = userSettings.lrcMode === 1 ? 'flex' : 'none';
        embLrcEl.style.display = userSettings.lrcMode === 2 ? 'block' : 'none';
        overlay.style.bottom = userSettings.lrcBot + '%';
        ovLrcEl.style.fontSize = userSettings.lrcSize + 'px';

        ovLrcEl.className = '';
        if (userSettings.lrcStyle === 0) ovLrcEl.classList.add('lyric-yt');
        if (userSettings.lrcStyle === 1) ovLrcEl.classList.add('lyric-glow');
        if (userSettings.lrcStyle === 2) ovLrcEl.classList.add('lyric-glass');

        updateQuickOffsetUI();
        if (userSettings.visMode && !audio.paused && !isMinimized) drawVis();
        if (!userSettings.visMode && visCtx) visCtx.clearRect(0,0,200,40);
        resetIdle();
    }
    qs('#pt-settings-panel').addEventListener('change', applySettings);
    applySettings();

    const savedPos = localStorage.getItem('pt_mp_pos');
    if (savedPos) {
        try { const pos = JSON.parse(savedPos); container.style.right = 'unset'; container.style.left = pos.left; container.style.top = pos.top; } catch(e) {}
    }
    function clampSavedPosition() {
        if (!container.style.left || !container.style.top) return;
        const rect = container.getBoundingClientRect();
        container.style.left = Math.max(0, Math.min(Math.max(0, window.innerWidth - rect.width), parseFloat(container.style.left) || 0)) + 'px';
        container.style.top = Math.max(0, Math.min(Math.max(0, window.innerHeight - rect.height), parseFloat(container.style.top) || 0)) + 'px';
        localStorage.setItem('pt_mp_pos', JSON.stringify({ left: container.style.left, top: container.style.top }));
    }
    window.addEventListener('resize', clampSavedPosition);
    clampSavedPosition();

    ['touchstart', 'touchmove', 'mousemove', 'mousedown', 'wheel', 'keydown', 'keyup'].forEach(evt => {
        container.addEventListener(evt, e => {
            if (isDragging && (evt === 'touchmove' || evt === 'mousemove')) return;
            e.stopPropagation();
        });
        overlay.addEventListener(evt, e => e.stopPropagation());
    });

    ['touchstart', 'touchmove', 'mousemove', 'mousedown', 'wheel'].forEach(e => container.addEventListener(e, resetIdle));
    resetIdle();

    let initialX, initialY, startX, startY;
    function dragStart(e) {
        if (e.target.tagName === 'BUTTON' || e.target.tagName === 'SELECT' || e.target.tagName === 'INPUT' || e.target.tagName === 'LABEL') return;
        if (e.type === "touchstart") { initialX = e.touches[0].clientX; initialY = e.touches[0].clientY; } else { initialX = e.clientX; initialY = e.clientY; }
        const rect = container.getBoundingClientRect();
        container.style.right = 'unset'; container.style.left = rect.left + 'px'; container.style.top = rect.top + 'px';
        startX = container.offsetLeft; startY = container.offsetTop; isDragging = true; resetIdle();
    }
    function drag(e) {
        if (!isDragging) return; e.preventDefault();
        let curX = (e.type === "touchmove") ? e.touches[0].clientX : e.clientX;
        let curY = (e.type === "touchmove") ? e.touches[0].clientY : e.clientY;
        const rect = container.getBoundingClientRect();
        const maxX = Math.max(0, window.innerWidth - rect.width);
        const maxY = Math.max(0, window.innerHeight - rect.height);
        container.style.left = Math.max(0, Math.min(maxX, startX + (curX - initialX))) + 'px';
        container.style.top = Math.max(0, Math.min(maxY, startY + (curY - initialY))) + 'px';
    }
    function dragEnd() {
        if(!isDragging) return; isDragging = false; resetIdle();
        localStorage.setItem('pt_mp_pos', JSON.stringify({left: container.style.left, top: container.style.top}));
    }

    headerEl.addEventListener("touchstart", dragStart, { passive: false }); headerEl.addEventListener("mousedown", dragStart);
    document.addEventListener("touchmove", drag, { passive: false }); document.addEventListener("mousemove", drag);
    document.addEventListener("touchend", dragEnd); document.addEventListener("mouseup", dragEnd);

    let isMinimized = false, isListOpen = true, isSettingsOpen = false;
    minBtn.onclick = () => {
        isMinimized = !isMinimized;
        container.classList.toggle('minimized', isMinimized);
        qs('#pt-mp-header-title').innerText = isMinimized ? '🎵 ' + (playlist[currentIndex]?.name || 'Player') : '🎵 PT Player';
        if(isMinimized && visId) cancelAnimationFrame(visId);
        else if(!isMinimized && !audio.paused && userSettings.visMode) drawVis();
    };
    setBtn.onclick = () => { isSettingsOpen = !isSettingsOpen; setPanel.style.display = isSettingsOpen ? 'block' : 'none'; };
    toggleListBtn.onclick = () => {
        isListOpen = !isListOpen;
        listEl.style.display = isListOpen ? 'block' : 'none';
        tagFilterContainer.style.display = isListOpen ? 'flex' : 'none';
        activeFiltersEl.style.display = isListOpen ? 'block' : 'none';
        searchRowEl.style.display = isListOpen ? 'flex' : 'none';
        importProgressWrap.style.display = (isListOpen && importStatusEl.textContent) ? 'flex' : 'none';
        toggleListBtn.innerText = isListOpen ? '▼ List' : '▲ List';
    };
    qs('#pt-clear-all').onclick = async () => {
        if(confirm("Delete all tracks from the database? This cannot be undone.")) {
            await clearAll(); audio.pause(); currentIndex = -1; playlist = [];
            titleEl.innerText = '-'; artistEl.innerText = '-'; timeEl.innerText="00:00 / 00:00"; seekEl.value=0;
            displayLyric(""); titleEl.classList.remove('is-marquee'); refreshUI();
        }
    };

    function parseLRC(track) {
        parsedLyrics = []; if(!track || !track.lyrics) return;
        const oriLines = track.lyrics.split('\n');
        const rmLines = track.romanizedLyrics ? track.romanizedLyrics.split('\n') : [];
        const trLines = track.translatedLyrics ? track.translatedLyrics.split('\n') : [];

        let rmArray = [], trArray = [];

        rmLines.forEach(line => {
            const match = line.match(/^\[\d+:\d+(?:\.\d+)?\](.*)/);
            if (match) rmArray.push(match[1].trim());
        });

        trLines.forEach(line => {
            const match = line.match(/^\[\d+:\d+(?:\.\d+)?\](.*)/);
            if (match) trArray.push(match[1].trim());
        });

        let lineIdx = 0;
        oriLines.forEach(line => {
            const match = line.match(/^\[(\d+):(\d+(?:\.\d+)?)\](.*)/);
            if (match) {
                let time = parseInt(match[1]) * 60 + parseFloat(match[2]);
                parsedLyrics.push({
                    time: time + (track.lrcOffset || 0),
                    text: match[3].trim(),
                    romaji: rmArray[lineIdx] || "",
                    translation: trArray[lineIdx] || ""
                });
                lineIdx++;
            }
        });
        parsedLyrics.sort((a,b) => a.time - b.time);
    }

    function displayLyric(obj) {
        if (typeof obj === 'string') obj = { text: obj, romaji: "", translation: "" };

        if (!obj || !obj.text) {
            embLrcEl.style.display = 'none';
            embLrcEl.innerHTML = "";
            ovLrcEl.style.opacity = '0';
            ovLrcEl.style.transform = 'translateY(5px)';
            if (userSettings.lrcMode === 1) {
                setTimeout(() => {
                    if (!ovLrcEl.textContent.trim() && userSettings.lrcMode === 1) {
                        ovLrcEl.style.display = 'none';
                        ovLrcEl.innerHTML = '';
                    }
                }, 240);
            } else {
                ovLrcEl.style.display = 'none';
                ovLrcEl.innerHTML = '';
            }
            return;
        }

        let safeText = obj.text.replace(/</g, "&lt;");
        let safeRm = (obj.romaji || "").replace(/</g, "&lt;");
        let safeTr = (obj.translation || "").replace(/</g, "&lt;");

        let html = "";

        if (userSettings.showOri) html += `<div class="pt-lrc-ori">${safeText}</div>`;
        if (userSettings.showRom && safeRm) html += `<div class="pt-lrc-rm">${safeRm}</div>`;
        if (userSettings.showTrs && safeTr) html += `<div class="pt-lrc-tr">${safeTr}</div>`;

        if (html === "") {
            embLrcEl.style.display = 'none';
            ovLrcEl.style.opacity = '0';
            ovLrcEl.style.transform = 'translateY(5px)';
            ovLrcEl.style.display = 'none';
            return;
        }

        if (userSettings.lrcMode === 2) {
            embLrcEl.style.display = 'block';
            embLrcEl.innerHTML = html;
        } else { embLrcEl.style.display = 'none'; }

        if (userSettings.lrcMode === 1) {
            ovLrcEl.style.display = 'block';
            ovLrcEl.innerHTML = html;
            ovLrcEl.style.opacity = '1';
            ovLrcEl.style.transform = 'translateY(0)';
        } else { ovLrcEl.style.display = 'none'; }
    }

    audio.addEventListener('timeupdate', () => {
        if (!isSeeking) seekEl.value = audio.currentTime;
        seekEl.max = audio.duration || 0;
        timeEl.innerText = `${formatTime(audio.currentTime)} / ${formatTime(audio.duration)}`;

        if (parsedLyrics.length > 0 && userSettings.lrcMode !== 0) {
            let activeObj = null;
            for(let i=0; i<parsedLyrics.length; i++) {
                if(audio.currentTime >= parsedLyrics[i].time) activeObj = parsedLyrics[i]; else break;
            }
            displayLyric(activeObj || "");
        } else { displayLyric(""); }
    });

    seekEl.oninput = () => isSeeking = true;
    seekEl.onchange = () => { audio.currentTime = seekEl.value; isSeeking = false; };
    volEl.oninput = () => { if (gainNode) gainNode.gain.value = volEl.value; else audio.volume = volEl.value; };

    audio.onplay = () => { playBtn.innerHTML = svgPause; if(userSettings.visMode && !isMinimized) drawVis(); };
    audio.onpause = () => { playBtn.innerHTML = svgPlay; cancelAnimationFrame(visId); };

    function checkMarquee() {
        titleEl.classList.remove('is-marquee');
        if(titleEl.scrollWidth > 185) titleEl.classList.add('is-marquee');
    }

    function updateMediaSession(track) {
        if ('mediaSession' in navigator) {
            navigator.mediaSession.metadata = new MediaMetadata({ title: track.name, artist: track.artist || 'PT Player', album: track.album || '' });
            navigator.mediaSession.setActionHandler('play', () => playBtn.click());
            navigator.mediaSession.setActionHandler('pause', () => playBtn.click());
            navigator.mediaSession.setActionHandler('previoustrack', () => prevBtn.click());
            navigator.mediaSession.setActionHandler('nexttrack', () => nextBtn.click());
        }
    }

    async function fetchLyrics(track) {
        if (track.lyrics) {
            await processRomanization(track);
            return parseLRC(track);
        }
        if (!userSettings.autoFetch || !track.artist || !track.name) return parseLRC(track);
        displayLyric("Searching for lyrics online...");
        try {
            if (userSettings.autoSelectLrc) {
                const res = await fetch(`https://lrclib.net/api/get?artist_name=${encodeURIComponent(track.artist)}&track_name=${encodeURIComponent(track.name)}`);
                const data = await res.json();
                if (data.syncedLyrics) {
                    track.lyrics = data.syncedLyrics; await updateTrack(track);
                    await processRomanization(track);
                    parseLRC(track);
                    return;
                }
            } else {
                const res = await fetch(`https://lrclib.net/api/search?q=${encodeURIComponent(track.artist + ' ' + track.name)}`);
                const results = await res.json();
                const synced = results.filter(r => r.syncedLyrics);
                if (synced.length > 0) {
                    let promptText = "Synced lyrics were found automatically. Select a version:\n";
                    synced.slice(0, 5).forEach((r, i) => {
                        let dur = Math.floor(r.duration / 60) + ":" + String(Math.floor(r.duration % 60)).padStart(2, '0');
                        promptText += `${i+1}. [${dur}] ${r.trackName} (${r.albumName || 'Single'})\n`;
                    });
                    let pick = prompt(promptText, "1");
                    if (pick !== null && !isNaN(parseInt(pick)) && synced[parseInt(pick)-1]) {
                        track.lyrics = synced[parseInt(pick)-1].syncedLyrics; await updateTrack(track);
                        await processRomanization(track);
                        parseLRC(track);
                        return;
                    }
                }
            }
            displayLyric(""); parseLRC(track);
        } catch(e) { displayLyric(""); parseLRC(track); }
    }

    async function playTrack(index) {
        if (!playlist.length || index < 0 || index >= playlist.length) return;
        initWebAudio(); if (audioCtx.state === 'suspended') audioCtx.resume();

        currentIndex = index; const track = playlist[currentIndex];
        if (audio.src) URL.revokeObjectURL(audio.src);
        audio.src = URL.createObjectURL(track.blob);

        audio.onloadedmetadata = () => {
            if(userSettings.toastNotif) showToast(track, audio.duration);
        };

        audio.play().catch(e => console.error(e));

        titleEl.innerText = track.name; artistEl.innerText = track.artist || "Unknown Artist";
        checkMarquee();

        updateMediaSession(track); displayLyric(""); parseLRC(track);
        await fetchLyrics(track); updateQuickOffsetUI(); renderList();
    }

    function updateQuickOffsetUI() {
        const track = playlist[currentIndex];
        if (currentIndex < 0 || !track || !track.lyrics || userSettings.lrcMode !== 1 || !userSettings.qOffset) {
            qoffPanel.style.display = 'none'; return;
        }
        qoffPanel.style.display = 'flex';
        let val = track.lrcOffset || 0;
        qoffVal.innerText = (val > 0 ? '+' : '') + val.toFixed(1) + 's';
    }

    function modifyOffset(delta) {
        const track = playlist[currentIndex];
        if (!track) return;
        track.lrcOffset = Math.max(-30, Math.min(30, (track.lrcOffset || 0) + delta));
        updateTrack(track); parseLRC(track); updateQuickOffsetUI();
    }
    overlay.querySelector('#qoff-dec').onclick = () => modifyOffset(-0.5);
    overlay.querySelector('#qoff-inc').onclick = () => modifyOffset(0.5);

    let targetUploadTrackId = null;
    lrcFileIn.onchange = (e) => {
        const file = e.target.files[0]; if (!file || !targetUploadTrackId) return;
        const reader = new FileReader();
        reader.onload = async (ev) => {
            let trk = playlist.find(t => t.id === targetUploadTrackId);
            if(trk) {
                trk.lyrics = ev.target.result;
                trk.romanizedLyrics = "";
                trk.translatedLyrics = "";
                await updateTrack(trk);
                await processRomanization(trk);
                if(currentIndex > -1 && playlist[currentIndex].id === targetUploadTrackId) parseLRC(trk);
                alert("Lyrics saved successfully!"); refreshUI();
            }
        };
        reader.readAsText(file); lrcFileIn.value = '';
    };

    tagFilterEl.onchange = (e) => {
        const val = e.target.value;
        if(val && !activeFilters.includes(val)) {
            if(val === 'Untagged') activeFilters = ['Untagged'];
            else {
                activeFilters = activeFilters.filter(x => x !== 'Untagged');
                activeFilters.push(val);
            }
            renderList();
        }
        tagFilterEl.value = "";
    };

    qs('#pt-clear-filters').onclick = () => { activeFilters = []; renderList(); };
    searchEl.addEventListener('input', () => {
        searchQuery = searchEl.value.trim().toLocaleLowerCase();
        renderList();
    });

    async function refreshUI() { playlist = await loadPlaylist(); renderList(); updateQuickOffsetUI(); }

    function matchesTrack(track) {
        const tags = track.tags || (track.tag ? [track.tag] : []);
        if (activeFilters.includes('Untagged')) {
            if (tags.length > 0) return false;
        } else if (activeFilters.length > 0 && !activeFilters.every(filter => tags.includes(filter))) {
            return false;
        }
        if (!searchQuery) return true;
        const searchable = [track.name, track.artist, track.album].concat(tags).join(' ').toLocaleLowerCase();
        return searchable.includes(searchQuery);
    }

    function getFilteredPool() {
        const pool = [];
        playlist.forEach((track, index) => { if (matchesTrack(track)) pool.push(index); });
        return pool;
    }

    function renderList() {
        const allTags = new Set();
        playlist.forEach(t => {
            const tags = t.tags || (t.tag ? [t.tag] : []);
            tags.forEach(tg => allTags.add(tg));
        });

        tagFilterEl.innerHTML = `<option value="" disabled selected>+ Add Tag</option><option value="Untagged">Untagged</option>`;
        Array.from(allTags).sort().forEach(tag => {
            if(!activeFilters.includes(tag)) {
                const opt = document.createElement('option');
                opt.value = tag; opt.innerText = tag;
                tagFilterEl.appendChild(opt);
            }
        });

        activeFiltersEl.innerHTML = '';
        if(activeFilters.length === 0) {
            activeFiltersEl.innerHTML = `<span style="font-size:10px; color:var(--pt-subtext); margin-left:4px;">All Tracks</span>`;
        } else {
            activeFilters.forEach(f => {
                const chip = document.createElement('span');
                chip.className = 'pt-chip'; chip.innerText = f + ' ✕';
                chip.onclick = () => { activeFilters = activeFilters.filter(x => x !== f); renderList(); };
                activeFiltersEl.appendChild(chip);
            });
        }

        listEl.innerHTML = '';
        let visibleCount = 0;
        searchCountEl.textContent = getFilteredPool().length + ' / ' + playlist.length;

        playlist.forEach((track, idx) => {
            const tags = track.tags || (track.tag ? [track.tag] : []);

            if (!matchesTrack(track)) return;

            visibleCount++;
            const item = document.createElement('div');
            item.className = `pt-mp-item ${idx === currentIndex ? 'active' : ''}`;

            let tagDisplay = "";
            if (tags.length > 0) {
                if (tags.length <= 2) tagDisplay = ` • [${tags.join(', ')}]`;
                else tagDisplay = ` • [${tags[0]}, ${tags[1]}, +${tags.length - 2}]`;
            }

            const info = document.createElement('div');
            info.className = 'pt-mp-item-info';
            const nameEl = document.createElement('span');
            nameEl.className = 'pt-mp-item-name';
            nameEl.textContent = track.name || 'Untitled';
            const artistLineEl = document.createElement('span');
            artistLineEl.className = 'pt-mp-item-artist';
            artistLineEl.textContent = (track.artist || 'Unknown') + tagDisplay + (track.lyrics ? ' ✓' : '');
            info.append(nameEl, artistLineEl);
            info.title = [track.name, track.artist, track.album].filter(Boolean).join(' — ');
            info.onclick = () => playTrack(idx);
            const acts = document.createElement('div'); acts.className = 'pt-mp-acts';

            const editBtn = document.createElement('button');
            editBtn.className = 'pt-mp-edit';
            editBtn.innerText = '✎';
            editBtn.title = 'Edit metadata';
            editBtn.setAttribute('aria-label', 'Edit metadata for ' + (track.name || 'track'));
            editBtn.onclick = e => { e.stopPropagation(); openMetadataEditor(track); };

            const tagBtn = document.createElement('button'); tagBtn.className = 'pt-mp-lrc-btn'; tagBtn.innerHTML = svgTag;
            tagBtn.onclick = async (e) => {
                e.stopPropagation();
                const newTag = prompt("Edit Tags (comma separated):", tags.join(", "));
                if (newTag !== null) {
                    track.tags = newTag.split(',').map(t => t.trim()).filter(t => t);
                    track.tag = null;
                    await updateTrack(track);
                    refreshUI();
                }
            };

            const lrcBtn = document.createElement('button'); lrcBtn.className = 'pt-mp-lrc-btn'; lrcBtn.innerText = '+LRC';
            lrcBtn.onclick = async (e) => {
                e.stopPropagation();
                let action = prompt(`Search Lyrics: ${track.name}\n\nEnter a query (leave empty and press OK to upload an .lrc file):`, `${track.artist} ${track.name}`);
                if (action === null) return;
                if (action.trim() === "") {
                    targetUploadTrackId = track.id; lrcFileIn.click();
                } else {
                    displayLyric("Searching for lyrics...");
                    try {
                        let res = await fetch(`https://lrclib.net/api/search?q=${encodeURIComponent(action.trim())}`);
                        let results = await res.json();
                        let synced = results.filter(r => r.syncedLyrics);
                        if(synced.length === 0) { alert("No synced lyrics were found."); displayLyric(""); return; }

                        let promptText = "Select a version (enter a number):\n";
                        synced.slice(0, 8).forEach((r, i) => {
                            let dur = Math.floor(r.duration / 60) + ":" + String(Math.floor(r.duration % 60)).padStart(2, '0');
                            promptText += `${i+1}. [${dur}] ${r.trackName} - ${r.artistName} (${r.albumName || 'Single'})\n`;
                        });
                        let pick = prompt(promptText, "1");
                        if (pick !== null && !isNaN(parseInt(pick)) && synced[parseInt(pick)-1]) {
                            track.lyrics = synced[parseInt(pick)-1].syncedLyrics;
                            track.romanizedLyrics = "";
                            track.translatedLyrics = "";
                            await updateTrack(track);
                            await processRomanization(track);
                            if (idx === currentIndex) parseLRC(track);
                            alert("Lyrics updated successfully!");
                            refreshUI();
                        } else { displayLyric(""); }
                    } catch(err) { alert("Failed to search for lyrics."); displayLyric(""); }
                }
            };

            const delBtn = document.createElement('button'); delBtn.className = 'pt-mp-del'; delBtn.innerText = '✕';
            delBtn.onclick = async (e) => {
                e.stopPropagation();
                if (!confirm('Delete "' + (track.name || 'Untitled') + '" from the playlist?')) return;
                await deleteTrack(track.id);
                if (idx === currentIndex) { audio.pause(); titleEl.innerText = '-'; artistEl.innerText = '-'; currentIndex = -1; timeEl.innerText="00:00 / 00:00"; seekEl.value=0; displayLyric(""); checkMarquee(); }
                else if (idx < currentIndex) currentIndex--;
                refreshUI();
            };

            acts.append(editBtn, tagBtn, lrcBtn, delBtn); item.append(info, acts); listEl.appendChild(item);
        });
        if (visibleCount === 0) {
            const empty = document.createElement('div');
            empty.style.cssText = 'text-align:center;color:var(--pt-subtext);padding:12px 6px;line-height:1.4;';
            empty.textContent = playlist.length ? 'No tracks match the current search or filters.' : 'Playlist is empty. Click + Add to import tracks.';
            listEl.appendChild(empty);
        }
    }

    playBtn.onclick = () => {
        if (!playlist.length) return;
        initWebAudio(); if (audioCtx.state === 'suspended') audioCtx.resume();
        if (audio.paused) { currentIndex === -1 ? playTrack(getFilteredPool()[0] ?? -1) : audio.play(); }
        else { audio.pause(); }
    };
    nextBtn.onclick = () => {
        if (!playlist.length) return;
        const pool = getFilteredPool();
        if(!pool.length) return;

        if (isShuffle) {
            playTrack(pool[Math.floor(Math.random() * pool.length)]);
        } else {
            let poolIdx = pool.indexOf(currentIndex);
            if(poolIdx === -1) playTrack(pool[0]);
            else playTrack(poolIdx < pool.length - 1 ? pool[poolIdx + 1] : pool[0]);
        }
    };
    prevBtn.onclick = () => {
        if (!playlist.length) return;
        const pool = getFilteredPool();
        if(!pool.length) return;

        let poolIdx = pool.indexOf(currentIndex);
        if(poolIdx === -1) playTrack(pool[0]);
        else playTrack(poolIdx > 0 ? pool[poolIdx - 1] : pool[pool.length - 1]);
    };

    shuffleBtn.onclick = () => { isShuffle = !isShuffle; shuffleBtn.classList.toggle('btn-active', isShuffle); };
    repeatBtn.onclick = () => {
        repeatMode = (repeatMode + 1) % 3;
        repeatBtn.innerHTML = repeatMode === 2 ? svgRepeat1 : svgRepeat;
        repeatBtn.classList.remove('btn-active', 'btn-active-2');
        if (repeatMode === 1) repeatBtn.classList.add('btn-active');
        if (repeatMode === 2) repeatBtn.classList.add('btn-active-2');
    };

    audio.onended = () => {
        if (repeatMode === 2) { playTrack(currentIndex); return; }
        const pool = getFilteredPool();
        if (!pool.length) return;

        if (isShuffle) { playTrack(pool[Math.floor(Math.random() * pool.length)]); return; }

        let poolIdx = pool.indexOf(currentIndex);
        if (poolIdx !== -1 && poolIdx < pool.length - 1) playTrack(pool[poolIdx + 1]);
        else if (repeatMode === 1) playTrack(pool[0]);
    };

    fileInput.onchange = async (e) => {
        if (!e.target.files.length) return;
        try {
            await saveFiles(e.target.files);
            await refreshUI();
            qs('#pt-mp-header-title').innerText = '🎵 PT Player';
        } catch (error) {
            console.error('PT Music Player import failed', error);
            importProgressWrap.style.display = 'flex';
            importStatusEl.textContent = 'Import failed: ' + (error && error.message ? error.message : 'unknown error');
            qs('#pt-mp-header-title').innerText = '🎵 PT Player';
            alert('Audio import failed. Check the browser console for details.');
        } finally {
            fileInput.value = '';
        }
    };
    refreshUI();
})();