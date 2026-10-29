#!/usr/bin/env bash
# ==============================================================================
# AlveeTV - Read-Only Preflight & Device Compatibility Inspector
# Inspects device hardware, verifies compatibility, backs up original settings,
# and prints a summary of proposed changes WITHOUT making any modifications.
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

echo -e "${BLUE}${BOLD}================================================================${NC}"
echo -e "${BLUE}${BOLD}   AlveeTV Read-Only Preflight & Compatibility Inspector        ${NC}"
echo -e "${BLUE}${BOLD}   (Checks hardware, backs up settings, changes nothing)        ${NC}"
echo -e "${BLUE}${BOLD}================================================================${NC}\n"

TV_IP="${1:-}"

if [[ -z "$TV_IP" ]]; then
  CONNECTED_DEV=$(adb devices | grep -v "List" | grep "device$" | head -n 1 | awk '{print $1}' || true)
  if [[ -n "$CONNECTED_DEV" ]]; then
    echo -e "${GREEN}✓ Found connected ADB device: ${BOLD}${CONNECTED_DEV}${NC}"
    TARGET_DEV="$CONNECTED_DEV"
  else
    echo -e "${YELLOW}Enter TV Box IP Address (found in TV Settings -> Network/About):${NC}"
    read -rp "TV IP Address [e.g. 192.168.0.54]: " TV_IP
    if [[ -z "$TV_IP" ]]; then
      echo -e "${RED}Error: TV IP address is required.${NC}"
      exit 1
    fi
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

# Verify communication
STATE=$($ADB get-state 2>/dev/null || echo "offline")
if [[ "$STATE" != "device" ]]; then
  echo -e "${RED}Error: Cannot communicate with device at $TARGET_DEV ($STATE).${NC}"
  echo -e "Please ensure Developer Options & ADB debugging are enabled on the TV."
  exit 1
fi

echo -e "\n${BOLD}[1/4] Inspecting Hardware & Android Platform...${NC}"

MODEL=$($ADB shell getprop ro.product.model | tr -d '\r')
BRAND=$($ADB shell getprop ro.product.brand | tr -d '\r')
MANUFACTURER=$($ADB shell getprop ro.product.manufacturer | tr -d '\r')
SDK=$($ADB shell getprop ro.build.version.sdk | tr -d '\r')
ANDROID_VER=$($ADB shell getprop ro.build.version.release | tr -d '\r')
CHIP=$($ADB shell getprop ro.board.platform | tr -d '\r')
HARDWARE=$($ADB shell getprop ro.hardware | tr -d '\r')
SCREEN_RES=$($ADB shell wm size 2>/dev/null | grep "Physical size" | cut -d':' -f2 | tr -d ' \r' || echo "Unknown")
CURRENT_DATE=$($ADB shell date | tr -d '\r')

echo -e "• Device:            ${BOLD}${BRAND} ${MODEL}${NC} (${MANUFACTURER})"
echo -e "• SoC / Chipset:     ${BOLD}${CHIP:-$HARDWARE}${NC}"
echo -e "• Android Version:   ${BOLD}Android ${ANDROID_VER}${NC} (API Level: ${SDK})"
echo -e "• Display Size:      ${BOLD}${SCREEN_RES}${NC}"
echo -e "• Current TV Clock:  ${YELLOW}${CURRENT_DATE}${NC}"

# Detect Root
HAS_ROOT=0
if $ADB shell "su -c id" 2>/dev/null | grep -q "uid=0"; then
  HAS_ROOT=1
  echo -e "• Root Privilege:    ${GREEN}Yes (su binary available)${NC}"
else
  echo -e "• Root Privilege:    ${YELLOW}Standard user (No root / unrooted)${NC}"
fi

# Detect Current Stock Launcher
STOCK_LAUNCHER=$($ADB shell "cmd package resolve-activity -c android.intent.category.HOME 2>/dev/null" | grep -o 'name=[^ ]*' | cut -d'=' -f2 | cut -d'/' -f1 || true)
if [[ -z "$STOCK_LAUNCHER" ]]; then
  STOCK_LAUNCHER=$($ADB shell "pm query-activities -c android.intent.category.HOME 2>/dev/null" | grep -o 'packageName=[^ ]*' | head -n 1 | cut -d'=' -f2 || echo "Unknown")
fi
echo -e "• Active Launcher:   ${CYAN}${STOCK_LAUNCHER:-com.android.launcher3}${NC}"

# Compatibility Evaluation
echo -e "\n${BOLD}[2/4] Evaluating Device Compatibility...${NC}"
COMPATIBLE=1
WARN_MSG=""

if [[ "$SDK" -lt 25 ]]; then
  COMPATIBLE=0
  WARN_MSG="Android version is older than Android 7.1 (API 25). AlveeTV requires Android 7.1+."
elif [[ "$SDK" -gt 33 ]]; then
  WARN_MSG="Android version is Android 14+; some root shell injection commands might have restricted SELinux policies."
fi

if [[ "$COMPATIBLE" -eq 1 ]]; then
  echo -e "${GREEN}✓ Device is COMPATIBLE with AlveeTV Home & Web Remote.${NC}"
else
  echo -e "${RED}✗ Incompatible device: ${WARN_MSG}${NC}"
  exit 1
fi

# [3/4] Backup Original Settings
echo -e "\n${BOLD}[3/4] Backing Up Original System Settings...${NC}"
mkdir -p "$ROOT_DIR/backup"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")
SAFE_DEV_NAME=$(echo "${BRAND}_${MODEL}" | tr -cd 'A-Za-z0-9_-')
BACKUP_FILE="$ROOT_DIR/backup/settings_backup_${SAFE_DEV_NAME}_${TIMESTAMP}.conf"

ORIG_AUTO_TIME=$($ADB shell settings get global auto_time 2>/dev/null | tr -d '\r' || echo "1")
ORIG_AUTO_TZ=$($ADB shell settings get global auto_time_zone 2>/dev/null | tr -d '\r' || echo "0")
ORIG_NTP=$($ADB shell settings get global ntp_server 2>/dev/null | tr -d '\r' || echo "null")
ORIG_TZ=$($ADB shell getprop persist.sys.timezone 2>/dev/null | tr -d '\r' || echo "UTC")

cat << EOF > "$BACKUP_FILE"
# AlveeTV Pre-Install Settings Backup
# Created: $(date)
# Device: ${BRAND} ${MODEL} (${TARGET_DEV})
auto_time=${ORIG_AUTO_TIME}
auto_time_zone=${ORIG_AUTO_TZ}
ntp_server=${ORIG_NTP}
persist.sys.timezone=${ORIG_TZ}
stock_launcher=${STOCK_LAUNCHER}
EOF

echo -e "${GREEN}✓ Original settings backed up to:${NC} ${BOLD}${BACKUP_FILE}${NC}"

# [4/4] Summary of Proposed Changes
echo -e "\n${BOLD}[4/4] Summary of Proposed Changes (Dry-Run Preview):${NC}"
echo -e "┌──────────────────────────────┬─────────────────────────────────────────────────┐"
echo -e "│ ${BOLD}Component${NC}                    │ ${BOLD}Action to be taken${NC}                              │"
echo -e "├──────────────────────────────┼─────────────────────────────────────────────────┤"
echo -e "│ AlveeTV Launcher             │ Install tv.alveetv.home (84 KB) alongside stock │"
echo -e "│ Stock Launcher               │ ${GREEN}PRESERVED${NC} (Will NOT be uninstalled/disabled)   │"
echo -e "│ Web Remote Server            │ Runs on port 8080 (Keys, Tap, HW H.264 stream)  │"
echo -e "│ Date & Time Auto-Sync        │ Fix 1970/2019 reset via UDP NTP + HTTP fallback │"
echo -e "│ Timezone Setting             │ Asia/Dhaka (GMT+6)                              │"
echo -e "│ NTP Server                   │ bd.pool.ntp.org (fallback pool.ntp.org)         │"
echo -e "│ System Partition (/system)   │ ${GREEN}READ-ONLY${NC} (Zero writes, dm-verity safe)      │"
echo -e "└──────────────────────────────┴─────────────────────────────────────────────────┘"

echo -e "\n${CYAN}Preflight check completed successfully.${NC}"
echo -e "To proceed with installation after reviewing the proposed changes, run:"
echo -e "  ${BOLD}./install_full_alveetv.sh ${TARGET_DEV}${NC}\n"

