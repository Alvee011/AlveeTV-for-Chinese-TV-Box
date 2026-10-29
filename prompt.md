# AlveeTV AI Agent & User Execution Prompts

This file contains production-grade, safety-first prompts designed for AI coding assistants (such as Claude Code, Cursor, Gemini CLI, ChatGPT) or human developers. 

Every prompt enforces **read-only preflight inspection**, **device compatibility validation**, **dry-run preview**, **settings backup**, and **explicit user approval** before any changes are made to the TV.

---

## 🛡️ Safety & Reliability Principles

1. **Never write to `/system`:** dm-verity protects the system partition on modern Android TV boxes; any writes to `/system` risk bricking the TV.
2. **Preserve the stock launcher:** Stock launchers are never uninstalled or disabled without explicit user request.
3. **Always back up settings first:** Timezone, NTP, auto-time, and launcher state are backed up to `backup/` before applying modifications.
4. **Summary & Approval Gate:** Always present a clear summary of proposed changes and ask for confirmation before modifying persistent device settings.

---

## 🚀 Option 1: Safe Full AlveeTV Suite Installation Prompt

> **Copy and paste this prompt to your AI assistant when you want the complete launcher, web remote, and system optimization:**

```markdown
I want to install the AlveeTV system on my Android TV box (Amlogic, Rockchip, or Allwinner) using the AlveeTV-for-Chinese-TV-Box repository.

### My Device Details:
- TV Box IP Address: <YOUR_TV_IP_HERE> (Port: 5555)
- Desired Timezone: Asia/Dhaka (GMT+6)

### Your Step-by-Step Instructions:

#### Phase 1: Connect & Read-Only Preflight Check
1. Connect over ADB: `adb connect <YOUR_TV_IP_HERE>:5555`
2. Verify ADB connection: `adb devices`
3. Execute the read-only preflight and compatibility check:
   ```bash
   ./preflight.sh <YOUR_TV_IP_HERE>
   ```
4. Check and report:
   - Device model, brand, chipset, and Android version (must be Android 7.1+ / API 25+).
   - Current clock time on the TV (confirm whether date is reset to 1970/2019).
   - Root availability status (`su`).
   - Active stock launcher package name.
   - Confirm that original settings were backed up to `backup/settings_backup_*.conf`.

#### Phase 2: Dry-Run Mode & Summary of Proposed Changes
5. Run the installer in dry-run mode to preview planned actions without changing anything:
   ```bash
   ./install_full_alveetv.sh --dry-run <YOUR_TV_IP_HERE>
   ```
6. Present a clear table to me summarizing the proposed changes:
   - Package to install: `bin/alveetv-home.apk` (`tv.alveetv.home`, ~84 KB)
   - Stock launcher status: PRESERVED (Will NOT be uninstalled or disabled)
   - Web Remote: Port 8080 (Keys, click-to-touch, and hardware H.264 screen stream)
   - Date & Time: Set to real-world time via UDP NTP (`bd.pool.ntp.org`) + HTTP header fallback
   - Persistent settings: `auto_time=1`, `persist.sys.timezone=Asia/Dhaka`
7. **STOP AND WAIT FOR MY EXPLICIT APPROVAL** before executing Phase 3.

#### Phase 3: Execution (Only After My Approval)
8. Once I approve, execute the installer:
   ```bash
   ./install_full_alveetv.sh <YOUR_TV_IP_HERE>
   ```

#### Phase 4: Verification
9. Verify the deployment:
   - AlveeTV Home (`tv.alveetv.home`) is installed and running.
   - TV box clock matches real-world time (`adb shell date`).
   - Web Remote server responds: `curl -s http://<YOUR_TV_IP_HERE>:8080/ping`
10. Print the Web Remote address (`http://<YOUR_TV_IP_HERE>:8080`) and provide the rollback command (`./restore.sh <YOUR_TV_IP_HERE>`).
```

---

## ⏰ Option 2: Safe Date & Time Fix ONLY Prompt

> **Copy and paste this prompt when you want to keep your existing launcher (e.g., stock Android TV launcher) and ONLY fix the clock resetting to 1970/2019:**

```markdown
My generic Chinese Android TV box lacks an onboard RTC battery and resets its date and time to 1970 (or 2019) every time power is disconnected, which breaks YouTube, Google Play Store, and HTTPS SSL certificates.

I want to fix the Date & Time issue ONLY, without replacing or disabling my existing stock launcher.

### My Device Details:
- TV Box IP Address: <YOUR_TV_IP_HERE>
- Timezone: Asia/Dhaka (GMT+6)

### Your Step-by-Step Instructions:

#### Phase 1: Read-Only Preflight & Backup
1. Connect over ADB: `adb connect <YOUR_TV_IP_HERE>:5555`
2. Check current clock: `adb shell date`
3. Back up current global time settings:
   - `adb shell settings get global auto_time`
   - `adb shell settings get global ntp_server`
   - `adb shell getprop persist.sys.timezone`

#### Phase 2: Propose Changes & Ask for Confirmation
4. Summarize the changes to be applied:
   - Set NTP server to `bd.pool.ntp.org` (with fallbacks `pool.ntp.org`, `time.google.com`).
   - Enforce timezone: `Asia/Dhaka` (GMT+6).
   - Inject current atomic real-world time into the kernel clock.
   - Install lightweight background sync guardian (`/data/local/tmp/alveetv_timesync.sh`) with HTTP header fallback if UDP port 123 is blocked by the ISP.
   - Zero changes to installed apps or launchers.
5. Ask for my confirmation before proceeding.

#### Phase 3: Execution & Verification
6. Once confirmed, execute the standalone Date & Time sync script:
   ```bash
   ./fix_date_time.sh <YOUR_TV_IP_HERE>
   ```
7. Verify by running `adb shell date` and confirm the clock matches current real-world time.
```

---

## 🛠️ Option 3: Manual Command Reference (For Humans)

### 1. Read-Only Preflight & Settings Backup:
```bash
cd AlveeTV-for-Chinese-TV-Box
./preflight.sh 192.168.0.54
```

### 2. Dry-Run Installation Preview:
```bash
./install_full_alveetv.sh --dry-run 192.168.0.54
```

### 3. Full Installation (After Reviewing Preflight & Dry-Run):
```bash
./install_full_alveetv.sh 192.168.0.54
```

### 4. Date & Time Fix Only (Keeps Stock Launcher):
```bash
./fix_date_time.sh 192.168.0.54
```

### 5. Safe Rollback / Restore:
```bash
./restore.sh 192.168.0.54
```
