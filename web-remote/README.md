# AlveeTV Web Remote Control

A modern, fast, zero-dependency Progressive Web App (PWA) remote control crafted specifically for your **AlveeTV Box** (H96 MAX X3). It faithfully matches the physical remote control layout, with a purple D-pad, responsive touchpad mouse mode, keyboard text input, and quick app launchers.

---

## Features

- **Exact Physical Remote Layout:**
  - Red **Power** button
  - **Menu** and **Home** buttons
  - **Purple D-Pad ring** with Up, Down, Left, Right, and Center **OK** button
  - Wide black pill **Back** button
  - **Volume Down (-)** and **Volume Up (+)** buttons
  - Purple **Mouse Mode** toggle
- **Interactive Mouse Trackpad:**
  - Real-time drag navigation on screen
  - Tap for Left Click
  - Right Click (acts as Android Menu button)
  - Vertical scroll strip for scrolling web pages & lists
- **Direct Keyboard Typing:**
  - Type text on your phone and press "Send" to instantly inject text into any TV search or login box without tediously using on-screen keyboards.
- **Quick App Launchers:**
  - One-tap launch for YouTube, SmartTube, Brave, TV APP, MX Player, and Settings.
- **Dynamic IP Auto-Discovery:**
  - If your router reboots or changes the TV's IP address, tap the connection pill or Settings cog and hit **"Auto-Detect TV on Network"**. It scans the local subnet in seconds and reconnects automatically.
- **Zero TV Changes to `/system`:**
  - The TV server is built directly into AlveeTV home app on port `8080`.
  - Can be turned ON or OFF at any time in **AlveeTV Settings -> Web Remote Control**.

---

## How to Host on Vercel

You can host this entire remote on Vercel for free in under 2 minutes:

### Option A: Using Vercel Web Dashboard (Easiest)
1. Push this folder or your repository to GitHub.
2. Go to [vercel.com](https://vercel.com) and log in.
3. Click **"Add New Project"** and import your repository.
4. Set **Root Directory** to `AlveeTV Remote`.
5. Click **Deploy**.
6. Vercel will give you a live URL like `https://alveetv-remote.vercel.app`.

### Option B: Using Vercel CLI
From your terminal:
```sh
cd "AlveeTV Remote"
npx vercel
```
Follow the terminal prompts (choose default settings), and your deployment will be instantly live.

---

## How to Install as an App on your Phone

### On iPhone (iOS Safari):
1. Open your Vercel URL (e.g. `https://alveetv-remote.vercel.app`) in **Safari**.
2. Tap the **Share** button (box with an upward arrow at the bottom of Safari).
3. Scroll down and tap **"Add to Home Screen"**.
4. Tap **Add** in the top right corner.
5. The **AlveeTV Remote** app icon will appear on your iPhone home screen! Opening it launches in full-screen standalone mode with no browser bars.

### On Android (Chrome):
1. Open the URL in **Chrome**.
2. Tap the 3 dots menu in the top right.
3. Tap **"Install app"** or **"Add to Home screen"**.

---

## How It Connects to Your TV

1. Ensure your phone and TV box are on the same Wi-Fi / home network.
2. Open the remote app. It will attempt to connect to `192.168.0.54` on port `8080`.
3. When connected, the top dot turns **green** and shows the connection latency.
4. If your TV box has a different IP:
   - Tap the connection pill at the top.
   - Enter your TV's current IP address and tap **Save**, OR
   - Tap **"Auto-Detect TV on Network"** to automatically find it.

---

## Turning Web Remote On / Off on the TV

You can toggle the remote server directly on your TV:
1. On the AlveeTV home screen, select **Options** (or **Settings**).
2. Look for **"Web remote control"**.
3. Toggle between **‹ On (192.168.0.54:8080) ›** and **‹ Off ›**.

