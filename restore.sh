#!/usr/bin/env bash
# ==============================================================================
# AlveeTV - Safe Restore / Uninstaller Script
# Restores original stock launcher and removes AlveeTV components cleanly.
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "${BLUE}${BOLD}================================================================${NC}"
echo -e "${BLUE}${BOLD}   AlveeTV Safe Restore & Rollback                              ${NC}"
echo -e "${BLUE}${BOLD}================================================================${NC}\n"

TV_IP="${1:-}"

if [[ -z "$TV_IP" ]]; then
  CONNECTED_DEV=$(adb devices | grep -v "List" | grep "device$" | head -n 1 | awk '{print $1}' || true)
  if [[ -n "$CONNECTED_DEV" ]]; then
    TARGET_DEV="$CONNECTED_DEV"
  else
    read -rp "Enter TV IP Address: " TV_IP
    adb connect "${TV_IP}:5555" || true
    TARGET_DEV="${TV_IP}:5555"
  fi
else
  if [[ "$TV_IP" != *":"* ]]; then
    adb connect "${TV_IP}:5555" || true
    TARGET_DEV="${TV_IP}:5555"
  else
    TARGET_DEV="$TV_IP"
  fi
fi

ADB="adb -s $TARGET_DEV"

echo -e "Target Device: ${BOLD}${TARGET_DEV}${NC}"

read -rp "Do you want to completely uninstall AlveeTV Home? [y/N]: " CONFIRM
if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then
  echo -e "\n1. Restoring stock home launcher..."
  # Re-enable any disabled launchers
  for pkg in com.android.launcher3 com.google.android.tvlauncher com.droidlogic.launcher; do
    $ADB shell "pm enable $pkg 2>/dev/null || true"
  done

  echo -e "2. Uninstalling AlveeTV Home (tv.alveetv.home)..."
  $ADB uninstall tv.alveetv.home || true

  echo -e "3. Cleaning temporary files..."
  $ADB shell "rm -rf /sdcard/Android/media/tv.alveetv.home 2>/dev/null || true"
  $ADB shell "rm -f /data/local/tmp/alveetv_timesync.sh 2>/dev/null || true"

  echo -e "\n${GREEN}✓ AlveeTV Home removed. Stock launcher restored.${NC}"
else
  echo -e "\nRestoring stock launcher without uninstalling AlveeTV..."
  # Start stock home
  $ADB shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" || true
  echo -e "${GREEN}✓ Done.${NC}"
fi

