/**
 * AlveeTV Web Remote & Hardware H.264 Live Screen Client
 * Zero-dependency, ultra low-latency WebCodecs stream + input dispatcher.
 */

// State
const PORT = 8080;
let tvIp = localStorage.getItem('alveetv_ip') || 
  (location.hostname && location.hostname !== 'localhost' && !location.hostname.includes('vercel.app') 
    ? location.hostname 
    : '192.168.0.54');

let hapticEnabled = localStorage.getItem('alveetv_haptic') !== 'false';
let isConnected = false;
let pingTimer = null;
let currentMode = 'dpad';

// Streaming State
let isStreaming = false;
let streamAbortController = null;
let videoDecoder = null;
let spsData = null;
let ppsData = null;
let frameCount = 0;
let fpsTimer = null;
let timestampIndex = 0;

// Elements
const statusBadge = document.getElementById('statusBadge');
const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');
const latencyBadge = document.getElementById('latencyBadge');
const tvIpInput = document.getElementById('tvIpInput');
const hapticToggle = document.getElementById('hapticToggle');
const settingsModal = document.getElementById('settingsModal');
const scanStatus = document.getElementById('scanStatus');
const btnScan = document.getElementById('btnScan');
const remoteView = document.getElementById('remoteView');
const trackpadView = document.getElementById('trackpadView');
const tabDpad = document.getElementById('tabDpad');
const tabTrackpad = document.getElementById('tabTrackpad');
const touchpad = document.getElementById('touchpad');
const scrollStrip = document.getElementById('scrollStrip');
const liveCanvas = document.getElementById('liveCanvas');
const btnLiveScreen = document.getElementById('btnLiveScreen');
const screenSection = document.getElementById('screenSection');
const streamPlaceholder = document.getElementById('streamPlaceholder');
const tvPowerLed = document.getElementById('tvPowerLed');
const fpsBadge = document.getElementById('fpsBadge');
const tapRipple = document.getElementById('tapRipple');
const httpsWarning = document.getElementById('httpsWarning');
const localTvLink = document.getElementById('localTvLink');
const modalDirectLink = document.getElementById('modalDirectLink');

// Initialize
window.addEventListener('DOMContentLoaded', () => {
  if (tvIpInput) tvIpInput.value = tvIp;
  if (hapticToggle) hapticToggle.checked = hapticEnabled;

  updateDirectLinks();
  checkHttpsEnvironment();
  initTouchpad();
  initScrollStrip();
  initDesktopShortcuts();
  checkConnection();

  // On mobile devices, hide screen monitor by default until user taps "Live Screen"
  if (window.innerWidth < 960 && screenSection) {
    screenSection.classList.add('collapsed');
  }

  // Periodic health check
  pingTimer = setInterval(checkConnection, 5000);

  // Register service worker for PWA support
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
});

// Update TV Direct Link URLs
function updateDirectLinks() {
  const directUrl = `http://${tvIp}:${PORT}`;
  if (localTvLink) localTvLink.href = directUrl;
  if (modalDirectLink) {
    modalDirectLink.href = directUrl;
    modalDirectLink.textContent = directUrl;
  }
}

// Detect Mixed Content issue on HTTPS / Vercel
function checkHttpsEnvironment() {
  if (window.location.protocol === 'https:') {
    if (httpsWarning) httpsWarning.classList.remove('hidden');
  }
}

function dismissBanner() {
  if (httpsWarning) httpsWarning.classList.add('hidden');
}

// Vibration feedback
function vibrate(ms = 15) {
  if (hapticEnabled && 'vibrate' in navigator) {
    try { navigator.vibrate(ms); } catch (e) {}
  }
}

// Connection check with latency measurement
async function checkConnection() {
  const url = `http://${tvIp}:${PORT}/ping`;
  const t0 = performance.now();
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 2000);

  try {
    const res = await fetch(url, {
      method: 'GET',
      mode: 'cors',
      signal: controller.signal
    });
    clearTimeout(timeoutId);
    if (res.ok) {
      const latency = Math.round(performance.now() - t0);
      setConnectedState(true, latency);
      return true;
    }
    throw new Error('Not OK');
  } catch (err) {
    clearTimeout(timeoutId);
    setConnectedState(false);
    return false;
  }
}

function setConnectedState(online, latency = 0) {
  isConnected = online;
  if (online) {
    statusDot.className = 'status-dot connected';
    statusText.textContent = tvIp;
    latencyBadge.textContent = `${latency}ms`;
    if (tvPowerLed && isStreaming) tvPowerLed.className = 'power-led active';
  } else {
    statusDot.className = 'status-dot disconnected';
    statusText.textContent = 'Disconnected';
    latencyBadge.textContent = '';
    if (tvPowerLed) tvPowerLed.className = 'power-led';
  }
}

// Low-latency Key Sender
async function sendKey(code) {
  vibrate(18);
  const url = `http://${tvIp}:${PORT}/key?code=${encodeURIComponent(code)}`;

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 1200);
    const res = await fetch(url, {
      method: 'POST',
      mode: 'cors',
      signal: controller.signal
    });
    clearTimeout(timeout);
    if (res.ok && !isConnected) setConnectedState(true);
  } catch (e) {
    try { fetch(url, { method: 'GET', mode: 'no-cors' }); } catch (err) {}
  }
}

/* ========================================================================== */
/* HARDWARE H.264 LIVE STREAM ENGINE (JMuxer MSE + WebCodecs)                  */
/* ========================================================================== */

// Ensure ManagedMediaSource is available as MediaSource on iOS 17.1+
if (typeof window.MediaSource === 'undefined' && typeof window.ManagedMediaSource !== 'undefined') {
  window.MediaSource = window.ManagedMediaSource;
}

let jmuxer = null;
let latencyInterval = null;

async function toggleLiveScreen() {
  if (isStreaming) {
    stopStream();
  } else {
    startStream();
  }
}

async function startStream() {
  if (isStreaming) return;
  isStreaming = true;

  const liveVideo = document.getElementById('liveVideo');
  const liveCanvas = document.getElementById('liveCanvas');

  if (screenSection) screenSection.classList.remove('collapsed');
  if (btnLiveScreen) btnLiveScreen.classList.add('active');
  if (streamPlaceholder) streamPlaceholder.classList.add('hidden');
  if (tvPowerLed) tvPowerLed.className = 'power-led active';

  frameCount = 0;
  if (fpsTimer) clearInterval(fpsTimer);
  fpsTimer = setInterval(() => {
    if (fpsBadge) fpsBadge.textContent = `${frameCount} FPS`;
    frameCount = 0;
  }, 1000);

  const streamUrl = `http://${tvIp}:${PORT}/stream.h264`;
  streamAbortController = new AbortController();

  // Primary Engine: JMuxer (MediaSource) - works in all browsers on HTTP LAN IP without security blocks
  const hasMSE = (typeof window.MediaSource !== 'undefined');
  if (hasMSE && typeof JMuxer !== 'undefined') {
    if (liveVideo) {
      liveVideo.style.display = 'block';
      if (window.ManagedMediaSource) {
        liveVideo.disableRemotePlayback = true;
      }
    }
    if (liveCanvas) liveCanvas.style.display = 'none';

    try {
      if (jmuxer) {
        try { jmuxer.destroy(); } catch (e) {}
        jmuxer = null;
      }

      jmuxer = new JMuxer({
        node: 'liveVideo',
        mode: 'video',
        flushingTime: 0,
        fps: 30,
        clearBuffer: true,
        debug: false,
        onError: function(data) {
          console.warn('JMuxer warning:', data);
        }
      });

      // Frame presentation tracking for real FPS
      if (liveVideo && 'requestVideoFrameCallback' in liveVideo) {
        const onFrame = () => {
          if (!isStreaming) return;
          frameCount++;
          liveVideo.requestVideoFrameCallback(onFrame);
        };
        liveVideo.requestVideoFrameCallback(onFrame);
      } else if (liveVideo) {
        liveVideo.ontimeupdate = () => { frameCount++; };
      }

      // Latency control: keep video locked to live edge with sub-100ms latency
      if (latencyInterval) clearInterval(latencyInterval);
      latencyInterval = setInterval(() => {
        if (!isStreaming || !liveVideo) {
          clearInterval(latencyInterval);
          return;
        }
        if (liveVideo.buffered && liveVideo.buffered.length > 0) {
          const end = liveVideo.buffered.end(liveVideo.buffered.length - 1);
          if (end - liveVideo.currentTime > 0.35) {
            liveVideo.currentTime = end - 0.05;
          }
        }
      }, 500);

      const response = await fetch(streamUrl, {
        signal: streamAbortController.signal,
        mode: 'cors'
      });

      if (!response.body) throw new Error('No readable stream body');
      const reader = response.body.getReader();

      while (isStreaming) {
        const { value, done } = await reader.read();
        if (done) break;
        if (jmuxer && value && value.length > 0) {
          jmuxer.feed({ video: value });
        }
      }
      return;
    } catch (err) {
      if (err.name !== 'AbortError') {
        console.warn('JMuxer stream stopped:', err);
      }
      stopStream();
      return;
    }
  }
}

function stopStream() {
  isStreaming = false;
  if (streamAbortController) {
    try { streamAbortController.abort(); } catch (e) {}
    streamAbortController = null;
  }
  if (jmuxer) {
    try { jmuxer.destroy(); } catch (e) {}
    jmuxer = null;
  }
  if (videoDecoder) {
    try { videoDecoder.close(); } catch (e) {}
    videoDecoder = null;
  }
  if (fpsTimer) {
    clearInterval(fpsTimer);
    fpsTimer = null;
  }
  if (latencyInterval) {
    clearInterval(latencyInterval);
    latencyInterval = null;
  }
  if (fpsBadge) fpsBadge.textContent = '0 FPS';
  if (btnLiveScreen) btnLiveScreen.classList.remove('active');
  if (streamPlaceholder) streamPlaceholder.classList.remove('hidden');
  if (tvPowerLed) tvPowerLed.className = 'power-led';

  const liveVideo = document.getElementById('liveVideo');
  if (liveVideo) {
    try {
      liveVideo.pause();
      liveVideo.removeAttribute('src');
      liveVideo.load();
    } catch (e) {}
  }
}

function restartStream() {
  stopStream();
  setTimeout(startStream, 300);
}

// Tap directly on TV screen canvas / video
function handleScreenTap(e) {
  const container = document.getElementById('canvasContainer') || document.getElementById('liveVideo');
  const rect = container.getBoundingClientRect();
  const clickX = e.clientX - rect.left;
  const clickY = e.clientY - rect.top;

  // TV physical screen resolution is 1920x1080
  const tvX = Math.round((clickX / rect.width) * 1920);
  const tvY = Math.round((clickY / rect.height) * 1080);

  // Animate ripple feedback
  if (tapRipple) {
    tapRipple.style.left = `${clickX}px`;
    tapRipple.style.top = `${clickY}px`;
    tapRipple.classList.remove('animate');
    void tapRipple.offsetWidth; // trigger reflow
    tapRipple.classList.add('animate');
  }

  vibrate(12);
  const url = `http://${tvIp}:${PORT}/tap?x=${tvX}&y=${tvY}`;
  fetch(url, { method: 'POST', mode: 'no-cors' }).catch(() => {});
}

function toggleFullscreen() {
  const container = document.getElementById('canvasContainer');
  if (!document.fullscreenElement) {
    if (container.requestFullscreen) container.requestFullscreen();
    else if (container.webkitRequestFullscreen) container.webkitRequestFullscreen();
  } else {
    if (document.exitFullscreen) document.exitFullscreen();
  }
}

/* ========================================================================== */
/* DESKTOP KEYBOARD SHORTCUTS                                                  */
/* ========================================================================== */

function initDesktopShortcuts() {
  window.addEventListener('keydown', (e) => {
    // Ignore keystrokes when typing inside text inputs
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
    if (!settingsModal.classList.contains('hidden')) return;

    switch (e.key) {
      case 'ArrowUp':
        e.preventDefault();
        sendKey('UP');
        break;
      case 'ArrowDown':
        e.preventDefault();
        sendKey('DOWN');
        break;
      case 'ArrowLeft':
        e.preventDefault();
        sendKey('LEFT');
        break;
      case 'ArrowRight':
        e.preventDefault();
        sendKey('RIGHT');
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        sendKey('OK');
        break;
      case 'Escape':
      case 'Backspace':
        e.preventDefault();
        sendKey('BACK');
        break;
      case 'h':
      case 'H':
        e.preventDefault();
        sendKey('HOME');
        break;
      case 'm':
      case 'M':
        e.preventDefault();
        sendKey('MENU');
        break;
      case '+':
      case '=':
        e.preventDefault();
        sendKey('VOL_UP');
        break;
      case '-':
      case '_':
        e.preventDefault();
        sendKey('VOL_DOWN');
        break;
    }
  });
}

/* ========================================================================== */
/* MOUSE TOUCHPAD & GESTURES                                                   */
/* ========================================================================== */

let lastTouchX = null;
let lastTouchY = null;
let touchStartTime = 0;
let touchMoved = false;
let pendingDx = 0;
let pendingDy = 0;
let mouseSendTimeout = null;

function initTouchpad() {
  if (!touchpad) return;

  touchpad.addEventListener('pointerdown', (e) => {
    touchpad.setPointerCapture(e.pointerId);
    touchpad.classList.add('active');
    lastTouchX = e.clientX;
    lastTouchY = e.clientY;
    touchStartTime = Date.now();
    touchMoved = false;
  });

  touchpad.addEventListener('pointermove', (e) => {
    if (lastTouchX === null || lastTouchY === null) return;

    const dx = Math.round((e.clientX - lastTouchX) * 1.6);
    const dy = Math.round((e.clientY - lastTouchY) * 1.6);

    lastTouchX = e.clientX;
    lastTouchY = e.clientY;

    if (Math.abs(dx) > 0 || Math.abs(dy) > 0) {
      touchMoved = true;
      pendingDx += dx;
      pendingDy += dy;

      if (!mouseSendTimeout) {
        mouseSendTimeout = setTimeout(flushMouseMove, 20);
      }
    }
  });

  const handlePointerEnd = () => {
    touchpad.classList.remove('active');
    if (lastTouchX !== null) {
      const duration = Date.now() - touchStartTime;
      if (!touchMoved && duration < 280) {
        sendMouseClick('left');
      }
    }
    lastTouchX = null;
    lastTouchY = null;
    flushMouseMove();
  };

  touchpad.addEventListener('pointerup', handlePointerEnd);
  touchpad.addEventListener('pointercancel', handlePointerEnd);
}

function flushMouseMove() {
  if (mouseSendTimeout) {
    clearTimeout(mouseSendTimeout);
    mouseSendTimeout = null;
  }
  if (pendingDx === 0 && pendingDy === 0) return;

  const dx = pendingDx;
  const dy = pendingDy;
  pendingDx = 0;
  pendingDy = 0;

  const url = `http://${tvIp}:${PORT}/mouse?dx=${dx}&dy=${dy}`;
  fetch(url, { method: 'POST', mode: 'cors' }).catch(() => {});
}

// Scroll strip gestures
let lastScrollY = null;
function initScrollStrip() {
  if (!scrollStrip) return;

  scrollStrip.addEventListener('pointerdown', (e) => {
    scrollStrip.setPointerCapture(e.pointerId);
    scrollStrip.classList.add('active');
    lastScrollY = e.clientY;
    vibrate(10);
  });

  scrollStrip.addEventListener('pointermove', (e) => {
    if (lastScrollY === null) return;
    const dy = e.clientY - lastScrollY;
    if (Math.abs(dy) > 12) {
      const scrollStep = dy > 0 ? -1 : 1;
      sendScroll(scrollStep);
      lastScrollY = e.clientY;
      vibrate(8);
    }
  });

  const handleEnd = () => {
    scrollStrip.classList.remove('active');
    lastScrollY = null;
  };
  scrollStrip.addEventListener('pointerup', handleEnd);
  scrollStrip.addEventListener('pointercancel', handleEnd);
}

function sendScroll(step) {
  const url = `http://${tvIp}:${PORT}/mouse?scroll=${step}`;
  fetch(url, { method: 'POST', mode: 'cors' }).catch(() => {});
}

async function sendMouseClick(type = 'left') {
  vibrate(22);
  const url = `http://${tvIp}:${PORT}/mouse?click=${type}`;
  fetch(url, { method: 'POST', mode: 'cors' }).catch(() => {});
}

/* ========================================================================== */
/* TEXT INJECTION & APP LAUNCHERS                                              */
/* ========================================================================== */

async function handleSendText(e) {
  e.preventDefault();
  const input = document.getElementById('keyboardInput');
  if (!input) return;
  const text = input.value.trim();
  if (!text) return;

  vibrate(20);
  const url = `http://${tvIp}:${PORT}/text?text=${encodeURIComponent(text)}`;
  try {
    const res = await fetch(url, { method: 'POST', mode: 'cors' });
    if (res.ok) input.value = '';
  } catch (err) {}
}

async function launchApp(pkg) {
  vibrate(25);
  const url = `http://${tvIp}:${PORT}/launch?pkg=${encodeURIComponent(pkg)}`;
  fetch(url, { method: 'POST', mode: 'cors' }).catch(() => {});
}

function setMode(mode) {
  currentMode = mode;
  vibrate(12);
  if (mode === 'dpad') {
    tabDpad.classList.add('active');
    tabTrackpad.classList.remove('active');
    remoteView.classList.remove('hidden');
    trackpadView.classList.add('hidden');
  } else {
    tabDpad.classList.remove('active');
    tabTrackpad.classList.add('active');
    remoteView.classList.add('hidden');
    trackpadView.classList.remove('hidden');
  }
}

/* ========================================================================== */
/* SETTINGS & AUTO-DISCOVERY                                                  */
/* ========================================================================== */

function openSettingsModal() {
  vibrate(10);
  if (tvIpInput) tvIpInput.value = tvIp;
  updateDirectLinks();
  scanStatus.textContent = '';
  settingsModal.classList.remove('hidden');
}

function closeSettingsModal() {
  settingsModal.classList.add('hidden');
}

function closeModalOnOverlay(e) {
  if (e.target === settingsModal) closeSettingsModal();
}

function saveTvIp() {
  const val = tvIpInput.value.trim();
  if (val) {
    tvIp = val;
    localStorage.setItem('alveetv_ip', tvIp);
    updateDirectLinks();
    vibrate(20);
    closeSettingsModal();
    checkConnection();
    if (isStreaming) restartStream();
  }
}

function toggleHaptics(e) {
  hapticEnabled = e.target.checked;
  localStorage.setItem('alveetv_haptic', hapticEnabled ? 'true' : 'false');
  if (hapticEnabled) vibrate(30);
}

// Subnet Auto-Scanner
let isScanning = false;
async function startSubnetScan() {
  if (isScanning) return;
  isScanning = true;
  btnScan.disabled = true;
  statusDot.className = 'status-dot scanning';
  statusText.textContent = 'Scanning...';
  scanStatus.textContent = 'Searching network for AlveeTV...';

  const ipParts = tvIp.split('.');
  const baseSubnet = (ipParts.length === 4) ? `${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.` : '192.168.0.';

  let foundIp = null;
  const batchSize = 25;

  for (let start = 1; start <= 254; start += batchSize) {
    scanStatus.textContent = `Scanning ${baseSubnet}${start} - ${baseSubnet}${Math.min(start + batchSize - 1, 254)}...`;
    const batch = [];

    for (let i = start; i < start + batchSize && i <= 254; i++) {
      batch.push(probeIp(`${baseSubnet}${i}`));
    }

    const results = await Promise.all(batch);
    const hit = results.find(r => r !== null);
    if (hit) {
      foundIp = hit;
      break;
    }
  }

  isScanning = false;
  btnScan.disabled = false;

  if (foundIp) {
    tvIp = foundIp;
    localStorage.setItem('alveetv_ip', tvIp);
    if (tvIpInput) tvIpInput.value = tvIp;
    updateDirectLinks();
    scanStatus.textContent = `Found AlveeTV at ${foundIp}!`;
    vibrate(50);
    setTimeout(() => {
      closeSettingsModal();
      checkConnection();
      if (isStreaming) restartStream();
    }, 1200);
  } else {
    scanStatus.textContent = 'TV not found. Ensure TV & phone are on the same Wi-Fi.';
    setConnectedState(false);
  }
}

async function probeIp(ip) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 1200);

  try {
    const res = await fetch(`http://${ip}:${PORT}/ping`, {
      method: 'GET',
      mode: 'cors',
      signal: controller.signal
    });
    clearTimeout(timeoutId);
    if (res.ok) {
      const data = await res.json();
      if (data && data.app === 'AlveeTV') return ip;
    }
  } catch (e) {
    clearTimeout(timeoutId);
  }
  return null;
}
