#!/usr/bin/env bash
# ==============================================================================
# AlveeTV - Full System Installer for Chinese Android TV Boxes
# Installs AlveeTV Home Launcher, Web Remote, and Date & Time Auto-Sync Engine.
# Features: Read-only preflight check, automatic backup, dry-run mode, and
# safe stock launcher preservation.
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

DRY_RUN=0
AUTO_YES=0
TV_IP=""

# Parse flags
for arg in "$@"; do
  case "$arg" in
    --dry-run)
      DRY_RUN=1
      ;;
    -y|--yes)
      AUTO_YES=1
      ;;
    -*)
      echo "Unknown flag: $arg"
      exit 1
      ;;
    *)
      if [[ -z "$TV_IP" ]]; then
        TV_IP="$arg"
      fi
      ;;
  esac
done

echo -e "${BLUE}${BOLD}================================================================${NC}"
echo -e "${BLUE}${BOLD}   AlveeTV Complete Suite Installer for Chinese TV Boxes        ${NC}"
echo -e "${BLUE}${BOLD}   (H96 Max, X96, Tanix, Mecool, Amlogic, Rockchip, Allwinner)  ${NC}"
echo -e "${BLUE}${BOLD}================================================================${NC}\n"

# 1. Target TV Connection
if [[ -z "$TV_IP" ]]; then
  CONNECTED_DEV=$(adb devices | grep -v "List" | grep "device$" | head -n 1 | awk '{print $1}' || true)
  if [[ -n "$CONNECTED_DEV" ]]; then
    echo -e "${GREEN}✓ Found connected ADB device: ${BOLD}${CONNECTED_DEV}${NC}"
    TARGET_DEV="$CONNECTED_DEV"
  else
    echo -e "${YELLOW}Enter your TV Box IP Address (found in TV Settings -> Network/About):${NC}"
    read -rp "TV IP Address [e.g. 192.168.0.54]: " TV_IP
    if [[ -z "$TV_IP" ]]; then
      echo -e "${RED}Error: TV IP address is required.${NC}"
      exit 1
    fi
    echo -e "Connecting to ${TV_IP}:5555..."
    adb connect "${TV_IP}:5555" || true
    TARGET_DEV="${TV_IP}:5555"
  fi
else
  if [[ "$TV_IP" != *":"* ]]; then
    adb connect "${TV_IP}:5555" || true
    TARGET_DEV="${TV_IP}:5555"
  else
    adb connect "$TV_IP" || true
    TARGET_DEV="$TV_IP"
  fi
fi

ADB="adb -s $TARGET_DEV"

STATE=$($ADB get-state 2>/dev/null || echo "offline")
if [[ "$STATE" != "device" ]]; then
  echo -e "${RED}Error: Cannot communicate with TV at $TARGET_DEV ($STATE).${NC}"
  echo -e "Please ensure Developer Options and ADB Debugging are enabled on your TV."
  exit 1
fi

DEVICE_IP=$(echo "$TARGET_DEV" | cut -d':' -f1)

# Step 1: Run Read-Only Preflight & Backup
echo -e "${BOLD}==> Running Read-Only Preflight Inspection & Settings Backup...${NC}"
"$ROOT_DIR/preflight.sh" "$TARGET_DEV"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo -e "\n${YELLOW}${BOLD}================================================================${NC}"
  echo -e "${YELLOW}${BOLD}   [DRY-RUN MODE ACTIVE] No changes have been made to the TV.   ${NC}"
  echo -e "${YELLOW}${BOLD}================================================================${NC}"
  echo -e "The installer would execute the following actions:"
  echo -e "  1. Install ${BOLD}bin/alveetv-home.apk${NC} (Package: tv.alveetv.home)"
  echo -e "  2. Grant runtime permissions (WRITE_SETTINGS, SET_TIME, etc.)"
  echo -e "  3. Deploy Web Remote assets to ${BOLD}/sdcard/Android/media/tv.alveetv.home/remote/${NC}"
  echo -e "  4. Synchronize Date & Time via NTP (${BOLD}bd.pool.ntp.org${NC}) and HTTP header fallback"
  echo -e "  5. Set Timezone to ${BOLD}Asia/Dhaka (GMT+6)${NC}"
  echo -e "  6. ${GREEN}Preserve the stock launcher${NC} (zero packages disabled)"
  echo -e "\nTo proceed with the actual installation, re-run without --dry-run:"
  echo -e "  ${BOLD}./install_full_alveetv.sh ${TARGET_DEV}${NC}\n"
  exit 0
fi

# Step 2: Require Explicit User Approval Before Modifying Anything
if [[ "$AUTO_YES" -eq 0 ]]; then
  echo -e "\n${YELLOW}${BOLD}Safety Confirmation:${NC}"
  echo -e "• Original settings are backed up."
  echo -e "• The stock launcher will be ${GREEN}preserved${NC}."
  echo -e "• Zero modifications will be made to /system partition."
  read -rp "Proceed with installation on ${TARGET_DEV}? [y/N]: " CONFIRM_INSTALL
  if [[ ! "$CONFIRM_INSTALL" =~ ^[Yy]$ ]]; then
    echo -e "${RED}Installation cancelled by user.${NC}"
    exit 0
  fi
fi

# Check APK
APK_PATH="$ROOT_DIR/bin/alveetv-home.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo -e "${YELLOW}Prebuilt APK not found. Compiling from source...${NC}"
  "$ROOT_DIR/build.sh"
fi

echo -e "\n${BOLD}[1/6] Installing AlveeTV Launcher APK...${NC}"
$ADB install -r -d -g "$APK_PATH"
echo -e "${GREEN}✓ AlveeTV Home installed successfully${NC}"

echo -e "\n${BOLD}[2/6] Granting System Permissions...${NC}"
$ADB shell "appops set tv.alveetv.home WRITE_SETTINGS allow" || true
$ADB shell "appops set tv.alveetv.home SYSTEM_ALERT_WINDOW allow" || true
$ADB shell "pm grant tv.alveetv.home android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true"
$ADB shell "pm grant tv.alveetv.home android.permission.SET_TIME 2>/dev/null || true"
$ADB shell "pm grant tv.alveetv.home android.permission.SET_TIME_ZONE 2>/dev/null || true"
$ADB shell "pm grant tv.alveetv.home android.permission.SET_POINTER_SPEED 2>/dev/null || true"
echo -e "${GREEN}✓ Permissions granted${NC}"

echo -e "\n${BOLD}[3/6] Deploying Web Remote Client to TV...${NC}"
REMOTE_SRC="$ROOT_DIR/web-remote"
$ADB shell "mkdir -p /sdcard/Android/media/tv.alveetv.home/remote" || true
$ADB push "$REMOTE_SRC/"* /sdcard/Android/media/tv.alveetv.home/remote/ >/dev/null

# If rooted, copy to app internal storage for direct offline serving
if $ADB shell "su -c id" 2>/dev/null | grep -q "uid=0"; then
  $ADB shell "su -c 'mkdir -p /data/data/tv.alveetv.home/files/remote && cp -r /sdcard/Android/media/tv.alveetv.home/remote/* /data/data/tv.alveetv.home/files/remote/ && chmod -R 755 /data/data/tv.alveetv.home/files/remote/'" >/dev/null 2>&1 || true
fi
echo -e "${GREEN}✓ Web Remote assets deployed${NC}"

echo -e "\n${BOLD}[4/6] Initializing Default Home Configuration...${NC}"
$ADB shell "mkdir -p /sdcard/Android/media/tv.alveetv.home" || true
$ADB shell "cat << 'EOF' > /sdcard/Android/media/tv.alveetv.home/home.conf
tiles=large
layout=grid
order=custom
memory=off
apps.show=com.google.android.youtube,org.smarttube.stable,com.brave.browser,tv.famelack.tvapp,com.android.settings
apps.order=com.google.android.youtube,org.smarttube.stable,com.brave.browser,tv.famelack.tvapp,com.android.settings
EOF"
echo -e "${GREEN}✓ Default grid layout (5-card balanced first row) written${NC}"

echo -e "\n${BOLD}[5/6] Synchronizing Date & Time...${NC}"
"$ROOT_DIR/fix_date_time.sh" "$TARGET_DEV"

echo -e "\n${BOLD}[6/6] Launching AlveeTV Home...${NC}"
$ADB shell "am start -n tv.alveetv.home/.Home" >/dev/null 2>&1 || true

echo -e "\n${GREEN}${BOLD}================================================================${NC}"
echo -e "${GREEN}${BOLD}   ✓ AlveeTV Installation Complete!                             ${NC}"
echo -e "${GREEN}${BOLD}================================================================${NC}"
echo -e "• TV Launcher:    ${BOLD}AlveeTV Home (Active)${NC}"
echo -e "• Stock Launcher: ${GREEN}Preserved & accessible at any time${NC}"
echo -e "• Web Remote:     ${BOLD}http://${DEVICE_IP}:8080${NC} (Open in any phone/browser)"
echo -e "• Date & Time:    ${GREEN}Synchronized (Asia/Dhaka GMT+6)${NC}"
echo -e "• Remote Options: Press Menu button -> Options to customize wallpapers or remote toggle."
echo -e "\n${YELLOW}To restore original settings or switch back to stock launcher, run:${NC}"
echo -e "  ${BOLD}./restore.sh ${TARGET_DEV}${NC}\n"
