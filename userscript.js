// ==UserScript==
// @name         PT Local Music Player (Draggable)
// @namespace    http://tampermonkey.net/
// @version      1.8.1
// @description  Mini local player with Native ID3 Parser, Lyrics, & UI (Hotfix)
// @author       deitzu
// @match        https://pony.town/*
// @grant        none
// ==/UserScript==
(function() {
    'use strict';
    
    const svgPlay = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>`;
    const svgPause = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;
    const svgPrev = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>`;
    const svgNext = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>`;
    const svgShuffle = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/></svg>`;
    const svgRepeat = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"/></svg>`;
    const svgRepeat1 = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"/></svg>`;
    const svgVol = `<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>`;
    const svgGear = `<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.49-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/></svg>`;

    const DB_NAME = 'PT_MusicPlayer_DB';
    const STORE_NAME = 'playlist';
    let userSettings = JSON.parse(localStorage.getItem('pt_mp_settings')) || { lrcMode: 1, lrcStyle: 0, lrcBot: 20, lrcSize: 16, autoFetch: true };

    function openDB() {
        return new Promise((resolve, reject) => {
            const req = indexedDB.open(DB_NAME, 1);
            req.onupgradeneeded = () => req.result.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
            req.onsuccess = () => resolve(req.result);
            req.onerror = () => reject(req.error);
        });
    }

    // Native ID3v2 Parser Racikan Sendiri
    function parseNativeID3(file) {
        return new Promise((resolve) => {
            const defName = file.name.replace(/\.[^/.]+$/, "");
            let meta = { title: defName, artist: "", album: "" };
            const reader = new FileReader();
            // Baca 128KB pertama doang biar memori HP lu kaga meledak
            const slice = file.slice(0, Math.min(128 * 1024, file.size));

            reader.onload = (e) => {
                try {
                    const buffer = e.target.result;
                    const view = new DataView(buffer);
                    
                    // Cek ada header "ID3" kaga
                    if (view.byteLength < 10 || view.getUint8(0) !== 0x49 || view.getUint8(1) !== 0x44 || view.getUint8(2) !== 0x33) {
                        return resolve(meta);
                    }

                    const version = view.getUint8(3);
                    if (version !== 3 && version !== 4) return resolve(meta); // Cuma support v2.3 / v2.4

                    const tagSize = (view.getUint8(6) << 21) | (view.getUint8(7) << 14) | (view.getUint8(8) << 7) | view.getUint8(9);
                    let offset = 10;

                    const readString = (buf, start, len, enc) => {
                        try {
                            const arr = new Uint8Array(buf, start, len);
                            let decoder;
                            if (enc === 0 || enc === 3) decoder = new TextDecoder(enc === 3 ? 'utf-8' : 'iso-8859-1');
                            else decoder = new TextDecoder('utf-16');
                            return decoder.decode(arr).replace(/\0/g, '').trim();
                        } catch(err) { return ""; }
                    };

                    while (offset < tagSize && offset < buffer.byteLength - 10) {
                        const frameId = String.fromCharCode(view.getUint8(offset), view.getUint8(offset+1), view.getUint8(offset+2), view.getUint8(offset+3));
                        if (!/[A-Z0-9]{4}/.test(frameId)) break; // Ketemu padding, kelar.

                        let frameSize = version === 3 ? view.getUint32(offset + 4) : 
                                       (view.getUint8(offset+4) << 21) | (view.getUint8(offset+5) << 14) | (view.getUint8(offset+6) << 7) | view.getUint8(offset+7);
                        
                        const frameOffset = offset + 10;
                        if (frameSize > 0 && frameOffset + frameSize <= buffer.byteLength) {
                            const encoding = view.getUint8(frameOffset);
                            const text = readString(buffer, frameOffset + 1, frameSize - 1, encoding);
                            
                            if (frameId === 'TIT2' && text) meta.title = text;
                            if (frameId === 'TPE1' && text) meta.artist = text;
                            if (frameId === 'TALB' && text) meta.album = text;
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
        const itemsToSave = [];
        
        // 1. Parse semua metadata dulu di luar transaksi
        for (let i = 0; i < files.length; i++) { 
            titleEl.innerText = `Memuat ${i+1}/${files.length}...`;
            let meta = await parseNativeID3(files[i]);
            itemsToSave.push({ 
                name: meta.title, 
                artist: meta.artist, 
                album: meta.album, 
                blob: files[i], 
                lyrics: "" 
            }); 
        }
        
        // 2. Pas data udah kumpul mateng, baru buka transaksi & push instan sekaligus
        const db = await openDB();
        const tx = db.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        
        itemsToSave.forEach(item => store.add(item));
        
        return new Promise(res => tx.oncomplete = res);
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
        const db = await openDB();
        const tx = db.transaction(STORE_NAME, 'readwrite');
        tx.objectStore(STORE_NAME).delete(id);
        return new Promise(res => tx.oncomplete = res);
    }
    
    async function updateTrack(track) {
        const db = await openDB();
        const tx = db.transaction(STORE_NAME, 'readwrite');
        tx.objectStore(STORE_NAME).put(track);
        return new Promise(res => tx.oncomplete = res);
    }

    async function clearAll() {
        const db = await openDB();
        const tx = db.transaction(STORE_NAME, 'readwrite');
        tx.objectStore(STORE_NAME).clear();
        return new Promise(res => tx.oncomplete = res);
    }

    let playlist = [], currentIndex = -1, isDragging = false, isSeeking = false;
    let isShuffle = false, repeatMode = 0; 
    let parsedLyrics = [], currentLyricLine = -1;
    
    const audio = new Audio();
    let audioCtx, gainNode;

    function initWebAudio() {
        if (audioCtx) return;
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        const source = audioCtx.createMediaElementSource(audio);
        gainNode = audioCtx.createGain();
        source.connect(gainNode).connect(audioCtx.destination);
        gainNode.gain.value = qs('#pt-vol').value;
    }

    const container = document.createElement('div');
    container.id = 'pt-mp-container';
    container.innerHTML = `
        <style>
            #pt-mp-container { position: fixed; top: 15px; right: 15px; z-index: 999999; background: rgba(20,20,20,0.85); color: #fff; padding: 10px; border-radius: 8px; font-family: sans-serif; font-size: 12px; width: 220px; box-shadow: 0 4px 10px rgba(0,0,0,0.5); backdrop-filter: blur(5px); border: 1px solid #333; user-select: none; -webkit-user-select: none; transition: opacity 0.3s, width 0.2s, padding 0.2s; }
            #pt-mp-container.idle { opacity: 0.3; }
            #pt-mp-container.minimized { width: auto; padding: 5px 10px; }
            #pt-mp-container.minimized #pt-mp-body, #pt-mp-container.minimized #pt-settings-panel { display: none !important; }
            #pt-mp-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #444; padding-bottom: 5px; margin-bottom: 5px; }
            #pt-mp-container.minimized #pt-mp-header { border-bottom: none; padding-bottom: 0; margin-bottom: 0; }
            #pt-mp-title { font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #ffb74d; cursor: grab; flex-grow: 1; max-width: 150px; }
            #pt-mp-title:active { cursor: grabbing; }
            .pt-head-btns { display: flex; gap: 5px; }
            .pt-head-btn { background: none; border: none; color: #aaa; cursor: pointer; font-weight: bold; font-size: 14px; padding: 0; display:flex; align-items:center; justify-content:center;}
            #pt-settings-panel { display: none; background: #222; border: 1px solid #444; padding: 8px; border-radius: 6px; margin-bottom: 8px; font-size: 11px; }
            .pt-set-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }
            .pt-set-row select, .pt-set-row input[type="range"] { width: 90px; background: #333; color: #fff; border: 1px solid #555; border-radius:3px; outline:none;}
            .pt-mp-controls, .pt-mp-controls-2 { display: flex; gap: 4px; margin-bottom: 8px; align-items: center;}
            .pt-mp-controls button, .pt-btn { display: flex; align-items: center; justify-content: center; background: #333; color: #fff; border: 1px solid #555; padding: 6px; border-radius: 4px; cursor: pointer; font-size: 12px; flex: 1; text-align: center; }
            .pt-mp-controls button:active, .pt-btn:active { background: #555; }
            .btn-active { background: #ffb74d !important; color: #000 !important; }
            .btn-active-2 { background: #81c784 !important; color: #000 !important; }
            #pt-seek-container { display: flex; flex-direction: column; margin-bottom: 8px; }
            #pt-time { font-size: 10px; text-align: right; color: #bbb; margin-bottom: 2px; }
            input[type="range"] { -webkit-appearance: none; width: 100%; height: 6px; background: #555; border-radius: 3px; outline: none; }
            input[type="range"]::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 12px; height: 12px; border-radius: 50%; background: #ffb74d; cursor: pointer; }
            #pt-vol-container { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; border-top: 1px dashed #444; padding-top: 8px; }
            #pt-vol-icon { color: #aaa; display: flex; align-items: center; }
            #pt-mp-list { max-height: 120px; overflow-y: auto; padding-top: 4px; display: block; border-top: 1px solid #333;}
            .pt-mp-item { display: flex; justify-content: space-between; align-items: center; padding: 5px 0; border-bottom: 1px solid #222;}
            .pt-mp-item.active { color: #81c784; font-weight: bold; }
            .pt-mp-item-info { display: flex; flex-direction: column; cursor: pointer; overflow: hidden; max-width: 130px; flex-grow:1;}
            .pt-mp-item-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .pt-mp-item-artist { font-size: 9px; color: #aaa; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .pt-mp-acts { display: flex; gap: 4px; }
            .pt-mp-lrc-btn, .pt-mp-del { background:#333; border:1px solid #555; color: #ddd; border-radius:3px; cursor: pointer; font-size: 10px; padding: 2px 4px;}
            .pt-mp-del { color: #e57373; font-weight: bold;}
            #pt-embedded-lrc { display: none; text-align: center; font-style: italic; color: #ffb74d; font-size: 11px; padding: 4px; border-bottom: 1px dashed #444; margin-bottom: 5px; min-height: 15px;}
            input[type="file"] { display: none; }
        </style>
        <div id="pt-mp-header">
            <div id="pt-mp-title">Mini Player</div>
            <div class="pt-head-btns">
                <button id="pt-set-btn" class="pt-head-btn">${svgGear}</button>
                <button id="pt-min-btn" class="pt-head-btn">_</button>
            </div>
        </div>
        <div id="pt-settings-panel">
            <div class="pt-set-row">
                <span>Lrc Mode:</span>
                <select id="pt-set-mode"><option value="0">Off</option><option value="1">Overlay</option><option value="2">Embedded</option></select>
            </div>
            <div class="pt-set-row">
                <span>Lrc Style:</span>
                <select id="pt-set-style"><option value="0">YouTube</option><option value="1">Glow</option><option value="2">Glass</option></select>
            </div>
            <div class="pt-set-row">
                <span>Bottom Position:</span>
                <input type="range" id="pt-set-bot" min="5" max="50" value="20">
            </div>
            <div class="pt-set-row">
                <span>Font Size:</span>
                <input type="range" id="pt-set-size" min="12" max="24" value="16">
            </div>
            <div class="pt-set-row" style="margin-top:8px;">
                <label style="display:flex; align-items:center; gap:5px;"><input type="checkbox" id="pt-set-fetch"> Auto-Fetch API</label>
                <button id="pt-clear-all" class="pt-btn" style="background:#b71c1c; padding:2px 5px; flex:none;">Clear All</button>
            </div>
        </div>
        <div id="pt-mp-body">
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
            <div id="pt-mp-list"></div>
        </div>
        <input type="file" id="pt-lrc-file" accept=".lrc" style="display:none;">
    `;
    document.body.appendChild(container);

    const overlay = document.createElement('div');
    overlay.id = 'pt-lyric-overlay';
    overlay.innerHTML = `<style>
        #pt-lyric-overlay { position: fixed; left: 50%; transform: translateX(-50%); text-align: center; pointer-events: none; z-index: 999998; transition: bottom 0.2s; white-space: pre-wrap; font-family: sans-serif; font-weight:bold;}
        .lyric-yt { background: rgba(0,0,0,0.6); padding: 4px 12px; border-radius: 6px; color: #fff; text-shadow: none; }
        .lyric-glow { background: transparent; color: #fff; text-shadow: 2px 2px 3px #000, -2px -2px 3px #000, 2px -2px 3px #000, -2px 2px 3px #000; }
        .lyric-glass { background: rgba(255,255,255,0.1); backdrop-filter: blur(6px); padding: 4px 12px; border-radius: 6px; border: 1px solid rgba(255,255,255,0.2); color: #fff; text-shadow: 1px 1px 2px #000;}
    </style><div id="pt-lyric-text"></div>`;
    document.body.appendChild(overlay);

    const qs = sel => container.querySelector(sel);
    const titleEl = qs('#pt-mp-title'), playBtn = qs('#pt-play'), prevBtn = qs('#pt-prev'), nextBtn = qs('#pt-next');
    const listEl = qs('#pt-mp-list'), fileInput = qs('#pt-file'), minBtn = qs('#pt-min-btn'), setBtn = qs('#pt-set-btn');
    const seekEl = qs('#pt-seek'), timeEl = qs('#pt-time'), shuffleBtn = qs('#pt-shuffle'), repeatBtn = qs('#pt-repeat');
    const toggleListBtn = qs('#pt-toggle-list'), volEl = qs('#pt-vol'), setPanel = qs('#pt-settings-panel');
    const lrcFileIn = qs('#pt-lrc-file'), embLrcEl = qs('#pt-embedded-lrc'), ovLrcEl = overlay.querySelector('#pt-lyric-text');

    // Load Settings
    qs('#pt-set-mode').value = userSettings.lrcMode; qs('#pt-set-style').value = userSettings.lrcStyle;
    qs('#pt-set-bot').value = userSettings.lrcBot; qs('#pt-set-size').value = userSettings.lrcSize;
    qs('#pt-set-fetch').checked = userSettings.autoFetch;
    
    function applySettings() {
        userSettings = {
            lrcMode: parseInt(qs('#pt-set-mode').value),
            lrcStyle: parseInt(qs('#pt-set-style').value),
            lrcBot: parseInt(qs('#pt-set-bot').value),
            lrcSize: parseInt(qs('#pt-set-size').value),
            autoFetch: qs('#pt-set-fetch').checked
        };
        localStorage.setItem('pt_mp_settings', JSON.stringify(userSettings));
        
        overlay.style.display = userSettings.lrcMode === 1 ? 'block' : 'none';
        embLrcEl.style.display = userSettings.lrcMode === 2 ? 'block' : 'none';
        overlay.style.bottom = userSettings.lrcBot + '%';
        ovLrcEl.style.fontSize = userSettings.lrcSize + 'px';
        
        ovLrcEl.className = '';
        if (userSettings.lrcStyle === 0) ovLrcEl.classList.add('lyric-yt');
        if (userSettings.lrcStyle === 1) ovLrcEl.classList.add('lyric-glow');
        if (userSettings.lrcStyle === 2) ovLrcEl.classList.add('lyric-glass');
    }
    qs('#pt-settings-panel').addEventListener('change', applySettings);
    applySettings();

    // Restore Pos
    const savedPos = localStorage.getItem('pt_mp_pos');
    if (savedPos) {
        try {
            const pos = JSON.parse(savedPos);
            container.style.right = 'unset'; container.style.left = pos.left; container.style.top = pos.top;
        } catch(e) {}
    }

    ['keydown', 'keyup', 'keypress', 'mousedown', 'wheel'].forEach(evt => {
        container.addEventListener(evt, e => e.stopPropagation());
        overlay.addEventListener(evt, e => e.stopPropagation());
    });
    container.addEventListener('touchstart', e => { if (e.target.tagName !== 'INPUT' && e.target.tagName !== 'SELECT') e.stopPropagation(); }, {passive: true});

    let idleTimer;
    const resetIdle = () => {
        container.classList.remove('idle');
        clearTimeout(idleTimer);
        if (!isDragging) idleTimer = setTimeout(() => container.classList.add('idle'), 3500);
    };
    ['touchstart', 'touchmove', 'mousemove', 'mousedown', 'wheel'].forEach(e => container.addEventListener(e, resetIdle));
    resetIdle();

    let initialX, initialY, startX, startY;
    function dragStart(e) {
        if (e.type === "touchstart") { initialX = e.touches[0].clientX; initialY = e.touches[0].clientY; } 
        else { initialX = e.clientX; initialY = e.clientY; }
        const rect = container.getBoundingClientRect();
        container.style.right = 'unset'; container.style.left = rect.left + 'px'; container.style.top = rect.top + 'px';
        startX = container.offsetLeft; startY = container.offsetTop; isDragging = true; resetIdle();
    }
    function drag(e) {
        if (!isDragging) return; e.preventDefault();
        let curX = (e.type === "touchmove") ? e.touches[0].clientX : e.clientX;
        let curY = (e.type === "touchmove") ? e.touches[0].clientY : e.clientY;
        container.style.left = (startX + (curX - initialX)) + "px"; container.style.top = (startY + (curY - initialY)) + "px";
    }
    function dragEnd() { 
        isDragging = false; resetIdle(); 
        localStorage.setItem('pt_mp_pos', JSON.stringify({left: container.style.left, top: container.style.top}));
    }
    
    titleEl.addEventListener("touchstart", dragStart, { passive: false }); titleEl.addEventListener("mousedown", dragStart);
    document.addEventListener("touchmove", drag, { passive: false }); document.addEventListener("mousemove", drag);
    document.addEventListener("touchend", dragEnd); document.addEventListener("mouseup", dragEnd);

    let isMinimized = false, isListOpen = true, isSettingsOpen = false;
    minBtn.onclick = () => {
        isMinimized = !isMinimized;
        container.classList.toggle('minimized', isMinimized);
        titleEl.innerText = isMinimized ? '🎵 ' + (playlist[currentIndex]?.name || 'Player') : (playlist[currentIndex]?.name || 'Mini Player');
    };
    setBtn.onclick = () => {
        isSettingsOpen = !isSettingsOpen;
        setPanel.style.display = isSettingsOpen ? 'block' : 'none';
    };
    toggleListBtn.onclick = () => {
        isListOpen = !isListOpen;
        listEl.style.display = isListOpen ? 'block' : 'none';
        toggleListBtn.innerText = isListOpen ? '▼ List' : '▲ List';
    };
    qs('#pt-clear-all').onclick = async () => {
        if(confirm("Hapus semua lagu?")) {
            await clearAll(); audio.pause(); currentIndex = -1; playlist = [];
            titleEl.innerText = 'Mini Player'; timeEl.innerText="00:00 / 00:00"; seekEl.value=0;
            displayLyric(""); refreshUI();
        }
    };

    function parseLRC(text) {
        parsedLyrics = [];
        if(!text) return;
        const lines = text.split('\n');
        lines.forEach(line => {
            const match = line.match(/\[(\d+):(\d+\.\d+)\](.*)/);
            if (match) parsedLyrics.push({ time: parseInt(match[1]) * 60 + parseFloat(match[2]), text: match[3].trim() });
        });
    }

    function displayLyric(text) {
        embLrcEl.innerText = text;
        ovLrcEl.innerText = text;
    }

    const formatTime = (sec) => {
        if (isNaN(sec)) return "00:00";
        let m = Math.floor(sec / 60), s = Math.floor(sec % 60);
        return (m < 10 ? '0'+m : m) + ':' + (s < 10 ? '0'+s : s);
    };

    audio.addEventListener('timeupdate', () => {
        if (!isSeeking) seekEl.value = audio.currentTime;
        seekEl.max = audio.duration || 0;
        timeEl.innerText = `${formatTime(audio.currentTime)} / ${formatTime(audio.duration)}`;
        
        if (parsedLyrics.length > 0 && userSettings.lrcMode !== 0) {
            let activeText = "";
            for(let i=0; i<parsedLyrics.length; i++) {
                if(audio.currentTime >= parsedLyrics[i].time) activeText = parsedLyrics[i].text;
                else break;
            }
            displayLyric(activeText);
        }
    });
    
    seekEl.oninput = () => isSeeking = true;
    seekEl.onchange = () => { audio.currentTime = seekEl.value; isSeeking = false; };
    volEl.oninput = () => { if (gainNode) gainNode.gain.value = volEl.value; else audio.volume = volEl.value; };

    function updateMediaSession(track) {
        if ('mediaSession' in navigator) {
            navigator.mediaSession.metadata = new MediaMetadata({ 
                title: track.name, 
                artist: track.artist || 'PT Player',
                album: track.album || ''
            });
            navigator.mediaSession.setActionHandler('play', () => playBtn.click());
            navigator.mediaSession.setActionHandler('pause', () => playBtn.click());
            navigator.mediaSession.setActionHandler('previoustrack', () => prevBtn.click());
            navigator.mediaSession.setActionHandler('nexttrack', () => nextBtn.click());
        }
    }

    async function fetchLyrics(track) {
        if (track.lyrics) return parseLRC(track.lyrics);
        if (!userSettings.autoFetch || !track.artist || !track.name) return parseLRC("");
        displayLyric("Mencari lirik online...");
        try {
            const res = await fetch(`https://lrclib.net/api/get?artist_name=${encodeURIComponent(track.artist)}&track_name=${encodeURIComponent(track.name)}`);
            const data = await res.json();
            if (data.syncedLyrics) {
                track.lyrics = data.syncedLyrics;
                await updateTrack(track);
                parseLRC(track.lyrics);
            } else { displayLyric(""); parseLRC(""); }
        } catch(e) { displayLyric(""); parseLRC(""); }
    }

    async function playTrack(index) {
        if (!playlist.length || index < 0 || index >= playlist.length) return;
        initWebAudio();
        if (audioCtx.state === 'suspended') audioCtx.resume();
        
        currentIndex = index; const track = playlist[currentIndex];
        if (audio.src) URL.revokeObjectURL(audio.src);
        audio.src = URL.createObjectURL(track.blob);
        audio.play().catch(e => console.error(e));
        
        playBtn.innerHTML = svgPause; 
        titleEl.innerText = track.artist ? `${track.artist} - ${track.name}` : track.name; 
        updateMediaSession(track);
        displayLyric("");
        parseLRC("");
        await fetchLyrics(track);
        renderList();
    }

    let targetUploadTrackId = null;
    lrcFileIn.onchange = (e) => {
        const file = e.target.files[0];
        if (!file || !targetUploadTrackId) return;
        const reader = new FileReader();
        reader.onload = async (ev) => {
            let trk = playlist.find(t => t.id === targetUploadTrackId);
            if(trk) {
                trk.lyrics = ev.target.result;
                await updateTrack(trk);
                if(currentIndex > -1 && playlist[currentIndex].id === targetUploadTrackId) parseLRC(trk.lyrics);
                alert("Lirik berhasil disimpan!");
                refreshUI();
            }
        };
        reader.readAsText(file);
        lrcFileIn.value = '';
    };

    async function refreshUI() { playlist = await loadPlaylist(); renderList(); }
    function renderList() {
        listEl.innerHTML = '';
        if (!playlist.length) return listEl.innerHTML = '<div style="text-align:center;color:#777;padding:10px;">Kosong. Klik + Add</div>';
        playlist.forEach((track, idx) => {
            const item = document.createElement('div');
            item.className = `pt-mp-item ${idx === currentIndex ? 'active' : ''}`;
            
            const info = document.createElement('div');
            info.className = 'pt-mp-item-info';
            // Pamerin fitur album juga di list kalo ada
            info.innerHTML = `<span class="pt-mp-item-name">${track.name}</span>
                              <span class="pt-mp-item-artist">${track.artist || 'Unknown'}${track.album ? ' • ' + track.album : ''} ${track.lyrics ? '✓LRC' : ''}</span>`;
            info.onclick = () => playTrack(idx);
            
            const acts = document.createElement('div'); acts.className = 'pt-mp-acts';
            const lrcBtn = document.createElement('button'); lrcBtn.className = 'pt-mp-lrc-btn'; lrcBtn.innerText = '+LRC';
            lrcBtn.onclick = (e) => { e.stopPropagation(); targetUploadTrackId = track.id; lrcFileIn.click(); };
            
            const delBtn = document.createElement('button'); delBtn.className = 'pt-mp-del'; delBtn.innerText = '✕';
            delBtn.onclick = async (e) => {
                e.stopPropagation(); await deleteTrack(track.id);
                if (idx === currentIndex) { audio.pause(); playBtn.innerHTML = svgPlay; titleEl.innerText = 'Mini Player'; currentIndex = -1; timeEl.innerText="00:00 / 00:00"; seekEl.value=0; displayLyric(""); } 
                else if (idx < currentIndex) currentIndex--;
                refreshUI();
            };
            
            acts.append(lrcBtn, delBtn); item.append(info, acts); listEl.appendChild(item);
        });
    }

    playBtn.onclick = () => {
        if (!playlist.length) return;
        initWebAudio(); if (audioCtx.state === 'suspended') audioCtx.resume();
        if (audio.paused) { currentIndex === -1 ? playTrack(0) : audio.play(); playBtn.innerHTML = svgPause; } 
        else { audio.pause(); playBtn.innerHTML = svgPlay; }
    };
    nextBtn.onclick = () => {
        if (!playlist.length) return;
        if (isShuffle) playTrack(Math.floor(Math.random() * playlist.length));
        else playTrack(currentIndex < playlist.length - 1 ? currentIndex + 1 : 0);
    };
    prevBtn.onclick = () => { if (playlist.length) playTrack(currentIndex > 0 ? currentIndex - 1 : playlist.length - 1); };
    
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
        if (isShuffle) { playTrack(Math.floor(Math.random() * playlist.length)); return; }
        if (currentIndex < playlist.length - 1) playTrack(currentIndex + 1);
        else if (repeatMode === 1) playTrack(0);
        else playBtn.innerHTML = svgPlay;
    };

    fileInput.onchange = async (e) => {
        if (e.target.files.length) { 
            titleEl.innerText = "Loading..."; 
            await saveFiles(e.target.files); 
            fileInput.value = ''; 
            titleEl.innerText = "Mini Player";
            refreshUI(); 
        }
    };
    refreshUI();
})();