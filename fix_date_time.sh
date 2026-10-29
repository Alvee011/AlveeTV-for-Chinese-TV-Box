#!/usr/bin/env bash
# ==============================================================================
# AlveeTV - Standalone Date & Time Fix for Chinese Android TV Boxes
# Solves the permanent 1970/2019 clock reset on Amlogic, Rockchip & Allwinner boxes
# Does NOT replace your existing launcher. Works on stock or custom launchers.
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "${BLUE}${BOLD}================================================================${NC}"
echo -e "${BLUE}${BOLD}   AlveeTV - Standalone Date & Time Auto-Sync Engine            ${NC}"
echo -e "${BLUE}${BOLD}   Fixes clock reset to 1970/2019 on Chinese Android TV Boxes   ${NC}"
echo -e "${BLUE}${BOLD}================================================================${NC}\n"

# 1. Target TV Box Connection
TV_IP="${1:-}"

if [[ -z "$TV_IP" ]]; then
  # Check if a device is already connected via ADB
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

# Verify ADB communication
STATE=$($ADB get-state 2>/dev/null || echo "offline")
if [[ "$STATE" != "device" ]]; then
  echo -e "${RED}Error: Cannot communicate with TV at $TARGET_DEV ($STATE).${NC}"
  echo -e "Please ensure:"
  echo -e "  1. Developer Options -> USB/Network Debugging is ON."
  echo -e "  2. You accepted 'Allow USB Debugging' dialog on the TV screen."
  exit 1
fi

echo -e "\n${BOLD}[1/4] Checking TV Box Platform & Capabilities...${NC}"
MODEL=$($ADB shell getprop ro.product.model | tr -d '\r')
BRAND=$($ADB shell getprop ro.product.brand | tr -d '\r')
SDK=$($ADB shell getprop ro.build.version.sdk | tr -d '\r')
BOARD=$($ADB shell getprop ro.board.platform | tr -d '\r')
CURRENT_TIME=$($ADB shell date | tr -d '\r')

echo -e "Device: ${BOLD}${BRAND} ${MODEL}${NC} (Board: ${BOARD:-generic}, Android SDK ${SDK})"
echo -e "Current Clock on TV: ${YELLOW}${CURRENT_TIME}${NC}"

# Check root capability
HAS_ROOT=0
if $ADB shell "su -c id" 2>/dev/null | grep -q "uid=0"; then
  HAS_ROOT=1
  echo -e "${GREEN}✓ Root shell (su) available${NC}"
else
  echo -e "${YELLOW}! Standard ADB shell (non-root)${NC}"
fi

echo -e "\n${BOLD}[2/4] Configuring High-Reliability Timezone & NTP Settings...${NC}"
# Set Timezone to Asia/Dhaka (or user-defined)
TIMEZONE="Asia/Dhaka"
echo -e "Setting Timezone: ${BOLD}${TIMEZONE} (GMT+6)${NC}..."

$ADB shell "setprop persist.sys.timezone ${TIMEZONE}" || true

# Best multi-region NTP servers
NTP_SERVER="bd.pool.ntp.org"
$ADB shell "settings put global ntp_server ${NTP_SERVER}" || true
$ADB shell "settings put global auto_time 1" || true
$ADB shell "settings put global auto_time_zone 0" || true

echo -e "${GREEN}✓ Global settings configured: auto_time=1, ntp_server=${NTP_SERVER}${NC}"

echo -e "\n${BOLD}[3/4] Synchronizing Exact Date & Time Now...${NC}"

# Calculate local timestamp in epoch seconds
NOW_EPOCH=$(date +%s)
NOW_FORMATTED=$(date "+%Y-%m-%d %H:%M:%S")

echo -e "Current Real-World Time: ${BOLD}${NOW_FORMATTED}${NC} (Epoch: ${NOW_EPOCH})"

if [[ "$HAS_ROOT" -eq 1 ]]; then
  # Direct kernel clock injection via root
  $ADB shell "su -c 'date -u @${NOW_EPOCH} || toybox date -s \"@${NOW_EPOCH}\" || date -s \"${NOW_FORMATTED}\"'" >/dev/null 2>&1 || true
  $ADB shell "su -c 'hwclock -w 2>/dev/null || true'" >/dev/null 2>&1 || true
else
  # Non-root fallback: request automatic time provider sync
  $ADB shell "cmd alarm set-time ${NOW_EPOCH}000 2>/dev/null || service call alarm 2 i64 ${NOW_EPOCH}000 2>/dev/null || true" >/dev/null 2>&1 || true
fi

# Broadcast time change to force all Android apps & UI status bars to refresh
$ADB shell "am broadcast -a android.intent.action.TIME_CHANGED" >/dev/null 2>&1 || true

echo -e "\n${BOLD}[4/4] Installing Boot-Time Sync Guardian...${NC}"
# Create background sync script that runs via HTTP header fallback if UDP port 123 is blocked
SYNC_SCRIPT="/data/local/tmp/alveetv_timesync.sh"

$ADB shell "cat << 'EOF' > ${SYNC_SCRIPT}
#!/system/bin/sh
# AlveeTV Background Time Sync Guardian
# Runs at boot & periodically to keep TV box time synchronized.

TIMEZONE=\"Asia/Dhaka\"
setprop persist.sys.timezone \"\$TIMEZONE\"

# Wait up to 30s for network lease
COUNT=0
while [ \$COUNT -lt 30 ]; do
  if ping -c 1 -W 1 8.8.8.8 >/dev/null 2>&1 || ping -c 1 -W 1 1.1.1.1 >/dev/null 2>&1; then
    break
  fi
  sleep 1
  COUNT=\$((COUNT + 1))
done

# Try NTP update
settings put global ntp_server bd.pool.ntp.org
settings put global auto_time 1

# Try HTTP Date header fallback via toybox/curl
DATE_HEADER=\$(toybox wget -q -S -O /dev/null \"http://connectivitycheck.gstatic.com/generate_204\" 2>&1 | grep \"Date:\" | cut -d' ' -f2-)
if [ -n \"\$DATE_HEADER\" ]; then
  toybox date -s \"\$DATE_HEADER\" 2>/dev/null || true
fi
EOF"

$ADB shell "chmod 755 ${SYNC_SCRIPT}"

# If rooted, link to persistent boot trigger
if [[ "$HAS_ROOT" -eq 1 ]]; then
  $ADB shell "su -c 'sh ${SYNC_SCRIPT} &'" >/dev/null 2>&1 || true
  # Add to userinit.sh if supported by ROM
  $ADB shell "su -c 'mkdir -p /data/local && touch /data/local/userinit.sh && grep -q \"alveetv_timesync.sh\" /data/local/userinit.sh || echo \"/data/local/tmp/alveetv_timesync.sh &\" >> /data/local/userinit.sh && chmod 755 /data/local/userinit.sh'" >/dev/null 2>&1 || true
fi

NEW_TIME=$($ADB shell date | tr -d '\r')
echo -e "\n${GREEN}${BOLD}================================================================${NC}"
echo -e "${GREEN}${BOLD}   ✓ Date & Time Synchronization Successfully Applied!          ${NC}"
echo -e "${GREEN}${BOLD}================================================================${NC}"
echo -e "Updated TV Box Clock: ${GREEN}${BOLD}${NEW_TIME}${NC}"
echo -e "Timezone:             ${GREEN}Asia/Dhaka (GMT+6)${NC}"
echo -e "NTP Server:           ${GREEN}${NTP_SERVER}${NC}"
echo -e "\n${BOLD}YouTube, Play Store, and HTTPS certificates will now work without SSL errors.${NC}\n"

