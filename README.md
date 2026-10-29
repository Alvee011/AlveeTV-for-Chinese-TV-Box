# AlveeTV for Chinese TV Boxes

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%207%20--%2012-brightgreen.svg)](#hardware-compatibility)
[![APK Size](https://img.shields.io/badge/APK%20Size-84%20KB-success.svg)](#architecture)
[![RAM Usage](https://img.shields.io/badge/RAM%20Usage-%3C%2025%20MB-orange.svg)](#performance)

An ultra-lightweight, high-performance, ad-free launcher suite engineered specifically for generic **Chinese Android TV Boxes** (x96, Amlogic, Rockchip, Allwinner).

Includes a built-in **multi-tier Date & Time auto-sync engine**, **embedded Web Remote with sub-100ms hardware H.264 live screen streaming**, and **USB/wireless mouse sensitivity & button remapping**.

---

## 📺 Why AlveeTV Exists

Generic Chinese Android TV boxes (such as **H96 Max**, **X96 Max**, **TX3**, **Tanix**, **Mecool**, **Beelink**) provide powerful hardware at a low cost, but suffer from critical software and hardware design flaws:

1. **The 1970/2019 Date & Time Reset Flaw:**
   - To cut manufacturing costs, Chinese TV boxes **completely omit the backup battery and Real-Time Clock (RTC) chip**.
   - When power is switched off or disconnected, the internal hardware clock resets to epoch 0 (`1970-01-01`) or firmware build date (`2019-10-18`).
   - Android's native NTP service fails because local ISPs or routers frequently block or throttle **UDP port 123**, or Wi-Fi takes 5 seconds to connect after boot (causing Android's time service to time out and back off for hours).
   - **Result:** Modern HTTPS SSL certificates fail TLS verification, breaking **YouTube**, **Google Play Store**, **SmartTube**, and all browsers with "No Connection" or "SSL Certificate Expired" errors.
2. **Heavy, Ad-Littered Stock Launchers:**
   - Stock firmware comes bloated with intrusive Chinese banner ads, background telemetry, slow UI navigation, and redundant background daemons.
3. **No Mobile Remote Compatibility:**
   - Third-party mobile remote apps (like "Remote for Android TV") rely on Google's proprietary Google TV framework (`com.google.android.tv.remote.service`) which is completely missing on generic AOSP TV boxes.

**AlveeTV permanently solves every single one of these problems.**

---

## ✨ Key Features

```
┌────────────────────────────────────────────────────────────────────────┐
│                              AlveeTV Suite                             │
├──────────────────┬──────────────────────┬──────────────────────────────┤
│ ⏰ TimeSync      │ 📱 Web Remote & Live │ 🖱️ Mouse Management          │
│ • UDP NTP multi- │ • Port 8080 server   │ • Pointer speed slider       │
│   pool sync      │ • Hardware H.264 live│ • 7-Button remapping         │
│ • HTTP Date head-│   screen stream (MSE)│ • Real-time USB mouse status │
│   er fallback    │ • Direct touch/tap   │ • Top bar status indicators  │
│ • Auto-runs on   │ • Zero-install PWA   │ • Wi-Fi & Ethernet detection │
│   boot & resume  │ • Subnet auto-scan   │ • Balanced 5-app top row     │
└──────────────────┴──────────────────────┴──────────────────────────────┘
```

### 1. Multi-Tier Date & Time Auto-Sync Engine
- **Primary Tier (UDP NTP):** Queries multi-region NTP servers (`bd.pool.ntp.org`, `pool.ntp.org`, `asia.pool.ntp.org`, `time.google.com`, `time.cloudflare.com`) over low-level UDP sockets.
- **Secondary Tier (HTTP Header Fallback):** If UDP port 123 is blocked by your ISP or firewall, it performs a lightweight HTTP `HEAD` request to Google / Cloudflare (`http://connectivitycheck.gstatic.com/generate_204`) and extracts the atomic `Date:` response header.
- **Reactive Triggers:** Automatically re-synchronizes on system boot (`BOOT_COMPLETED`), on network connectivity change, and every time the home screen resumes.
- **Available as Standalone:** You can install *just* the date & time fix on your TV box without changing your existing launcher!

### 2. Embedded Web Remote & Hardware H.264 Live Screen
- **Zero-Dependency On-TV Server:** Runs directly inside the launcher on port **8080**.
- **Hardware-Accelerated Screen Streaming:** Streams hardware H.264 video directly from the Amlogic/Rockchip VPU at 30 FPS using less than 4% CPU and 25 MB RAM.
- **Universal Compatibility (JMuxer MSE):** Unlike WebCodecs which is blocked on plain HTTP LAN IPs (`isSecureContext === false`), AlveeTV uses a lightweight Media Source Extensions engine that streams natively in Chrome, Edge, Safari, Firefox, and iOS 17.1+ without any security warnings.
- **Interactive Click-to-Touch:** Click or tap anywhere on the live video stream from your phone or PC to touch your TV screen directly.
- **Complete Remote Chassis:** Virtual D-Pad, Volume +/-, Menu, Home, Back, Power, and dedicated Mouse Trackpad.

### 3. Mouse Settings & Top Bar Integrations
- Auto-detects 2.4GHz wireless and USB wired mice.
- Dynamic top bar icons for network state (Green = Online, Red = Local only, Grey = Disconnected, LAN = Ethernet).
- Interactive mouse settings dialog with pointer speed slider and 7-button hardware remapper (e.g. remap Right Click to Android Menu or Back).

### 4. Ultra-Fast, Beautiful Leanback Launcher
- Symmetrical 5-app top row layout (`YouTube` | `SmartTube` | `Brave` | `TV APP` | `Settings`).
- All Apps drawer (`AllAppsActivity`) with filter tabs: *Not on Home*, *All Apps*, and *Hidden Apps*.
- Fast app launch with zero latency.
- Wallpaper configuration and adaptive background tinting.

---

## 🚀 Quick Start & Installation

### Option A: Install Full AlveeTV Suite (Recommended)

1. Turn on **Developer Options** and enable **ADB Debugging** on your TV box.
2. Connect your TV box to your Wi-Fi network and note its IP address (e.g., `192.168.0.54`).
3. Clone this repository and run the automated installer:

```bash
git clone https://github.com/Alvee011/AlveeTV-for-Chinese-TV-Box.git
cd AlveeTV-for-Chinese-TV-Box
chmod +x preflight.sh install_full_alveetv.sh build.sh fix_date_time.sh restore.sh

# 1. Run read-only preflight check & backup existing settings
./preflight.sh 192.168.0.54

# 2. Preview changes with zero modifications (dry-run mode)
./install_full_alveetv.sh --dry-run 192.168.0.54

# 3. Apply full installation (after reviewing proposed changes)
./install_full_alveetv.sh 192.168.0.54
```

The script will automatically:
- Back up original system settings to `backup/settings_backup_*.conf`.
- Install `alveetv-home.apk` (`tv.alveetv.home`) alongside your stock launcher.
- **Preserve your stock launcher** (nothing is force-disabled or uninstalled).
- Grant necessary time and system settings permissions.
- Deploy Web Remote assets to `/sdcard/Android/media/tv.alveetv.home/remote/`.
- Synchronize the TV box date and time.
- Launch AlveeTV Home.

---

### Option B: Fix Date & Time ONLY (Keep Existing Launcher)

If you love your current launcher and **only** want to fix the clock resetting to 1970/2019:

```bash
cd AlveeTV-for-Chinese-TV-Box
chmod +x fix_date_time.sh

# Run Date & Time fix
./fix_date_time.sh 192.168.0.54
```

This script:
1. Sets Android's network time properties (`auto_time=1`, `ntp_server=bd.pool.ntp.org`).
2. Syncs the kernel clock immediately to real-world time.
3. Installs a background guardian script (`/data/local/tmp/alveetv_timesync.sh`) with HTTP header fallback.
4. Broadcasts `android.intent.action.TIME_CHANGED` to eliminate SSL certificate errors across all apps.

---

## 📱 How to Use the Web Remote

Once AlveeTV is installed, open any web browser on your phone, tablet, or laptop connected to the same Wi-Fi network:

```
http://<YOUR_TV_IP>:8080/
```
*(Example: `http://192.168.0.54:8080`)*

- **Live Screen Monitor:** Displays your TV screen live at 30 FPS. Click or tap directly on the stream to interact with apps.
- **D-Pad & Navigation:** Navigate menus, adjust volume, or toggle sleep.
- **Trackpad Mode:** Switch to mouse trackpad to navigate non-TV apps like mobile Chrome or file managers.
- **Direct Text Typing:** Type queries on your smartphone keyboard and send them straight to search fields on your TV.

---

## 📂 Repository Structure

```
AlveeTV-for-Chinese-TV-Box/
├── README.md                      # Complete project documentation
├── LICENSE                        # MIT License
├── prompt.md                      # Ready-to-copy prompts for AI agents & users
├── preflight.sh                   # Read-only inspection & settings backup script
├── build.sh                       # Bare-SDK build script (no Gradle needed)
├── install_full_alveetv.sh        # Full system installer (with --dry-run support)
├── fix_date_time.sh               # Standalone Date & Time fix (preserves stock launcher)
├── restore.sh                     # Safe restore & rollback script
├── backup/                        # Pre-install system settings backups (.conf)
├── bin/
│   └── alveetv-home.apk           # Precompiled, signed, production-ready APK
├── launcher/
│   ├── AndroidManifest.xml        # Package tv.alveetv.home
│   ├── version.env                # Version configuration
│   ├── res/                       # Drawables, layouts, styles, strings
│   └── src/tv/alveetv/home/       # Full Java source code:
│       ├── Home.java              # Main home screen activity & grid layout
│       ├── TimeSync.java          # Multi-host UDP NTP + HTTP fallback sync
│       ├── RemoteServer.java      # Port 8080 HTTP server & H.264 streaming
│       ├── Options.java           # Options menu & QR contact card
│       ├── MouseDialog.java       # Mouse sensitivity & button remapping
│       ├── MouseDrawable.java     # USB mouse status bar icon
│       ├── WifiDrawable.java      # Dynamic Wi-Fi & Ethernet icon
│       ├── AllAppsActivity.java   # Custom app drawer
│       ├── BootReceiver.java      # System boot initialization
│       └── ...                    # UI utilities, catalog, config
├── web-remote/                    # Web Remote client (served on-device)
│   ├── index.html                 # Responsive desktop & mobile dashboard
│   ├── style.css                  # Modern glassmorphism UI styles
│   ├── remote.js                  # Input dispatcher & stream client
│   ├── jmuxer.min.js              # Hardware H.264 MSE video player
│   └── vercel.json                # Vercel deployment configuration
└── scripts/
    └── debloat_generic_box.sh     # Optional generic Chinese bloatware cleaner
```

---

## ⚙️ Building From Source

AlveeTV requires **no Gradle**, **no external dependencies**, and builds in less than 3 seconds using bare Android SDK tools:

```bash
./build.sh
```

The script compiles Java 8 bytecode, processes resources with `aapt2`, dexes with `d8`, zip-aligns, and signs the APK with `apksigner` into `bin/alveetv-home.apk`.

---

## 📺 Hardware Compatibility

Tested and verified on:

| SoC Family | Example Devices | Android Versions | Status |
|---|---|---|---|
| **Amlogic** | H96 MAX X3, X96 Max+, TX3, Tanix TX3, Beelink GT-King | Android 7, 8, 9, 10, 11, 12 | ✅ Fully Verified |
| **Rockchip** | RK3328, RK3399, RK3566, H96 Max V56 | Android 8, 9, 10, 11, 12 | ✅ Supported |
| **Allwinner** | H6, H616, Tanix TX6s | Android 7, 8, 9, 10 | ✅ Supported |

---

## 🛡️ Safe Rollback & Restore

AlveeTV makes **zero writes to `/system`** and never modifies read-only partitions (dm-verity is always preserved).

To revert to your stock launcher:
```bash
./restore.sh <YOUR_TV_IP>
```

---

## 👤 Author & Credits

Developed with ❤️ by **Faruque Azam Alvee**  
- **Bio Link / Contact:** [https://alvee.bio.link/](https://alvee.bio.link/)  
- **GitHub:** [@Alvee011](https://github.com/Alvee011)

