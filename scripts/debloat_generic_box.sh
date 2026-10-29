#!/usr/bin/env bash
# ==============================================================================
# AlveeTV - Generic Chinese TV Box Safe Debloater
# Safely disables known adware, tracking, and redundant background apps on
# Amlogic, Rockchip, and Allwinner TV boxes without root or /system modifications.
# ==============================================================================
set -euo pipefail

TV_IP="${1:-}"
if [[ -n "$TV_IP" ]]; then
  adb connect "${TV_IP}:5555" || true
fi

ADB="adb"

echo "==> Scanning installed packages on TV Box..."

# Known generic adware, telemetry, and junk bloat packages on generic TV boxes
JUNK_PACKAGES=(
  "com.droidlogic.otaupgrade"       # Unverified Chinese OTA update pusher
  "com.droidlogic.appinstall"       # Preloader / adware installer
  "com.android.browser"             # Vulnerable outdated AOSP browser
  "com.android.email"               # Outdated AOSP email
  "com.android.music"               # Outdated AOSP media player
  "com.android.calendar"            # Outdated calendar daemon
  "com.mstar.tv.service"            # Background telemetry
  "com.quicktv.live"                # Adware live channel app
  "com.shouji.cleanmaster"          # Fake ram cleaner malware
)

for pkg in "${JUNK_PACKAGES[@]}"; do
  if $ADB shell "pm list packages | grep -q $pkg"; then
    echo "Disabling bloat package: $pkg"
    $ADB shell "pm disable-user --user 0 $pkg 2>/dev/null || pm disable $pkg 2>/dev/null || true"
  fi
done

echo "==> Safe debloat check complete."

