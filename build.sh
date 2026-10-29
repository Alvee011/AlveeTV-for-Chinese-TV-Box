#!/usr/bin/env bash
# ==============================================================================
# AlveeTV Launcher - Standalone Build Script
# Builds bin/alveetv-home.apk from bare tools: no Gradle required.
# ==============================================================================
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

source launcher/version.env
MIN_SDK=${MIN_SDK:-25}
TARGET_SDK=28

echo "==> Building AlveeTV Home v${VERSION_NAME} (code ${VERSION_CODE})..."

# Detect Java
if [[ -z ${JAVA_HOME:-} || ! -x ${JAVA_HOME:-}/bin/java ]]; then
  for j in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
           /usr/lib/jvm/java-17-openjdk \
           /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
           "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [[ -n $j && -x $j/bin/java ]]; then JAVA_HOME=$j; break; fi
  done
fi
export JAVA_HOME
JAVA="${JAVA_HOME}/bin/java"

# Locate Android Tools (SDK or bundled /tmp tools)
AAPT2=""
FRAMEWORK_RES=""
ANDROID_JAR=""
ECJ=""
R8=""
SIGNER=""

if command -v aapt2 >/dev/null 2>&1; then
  AAPT2="$(command -v aapt2)"
elif [[ -x /tmp/aapt2 ]]; then
  AAPT2="/tmp/aapt2"
fi

if [[ -f /tmp/android.jar ]]; then
  ANDROID_JAR="/tmp/android.jar"
fi

if [[ -f /tmp/framework-res.apk ]]; then
  FRAMEWORK_RES="/tmp/framework-res.apk"
else
  FRAMEWORK_RES="$ANDROID_JAR"
fi

if [[ -f /tmp/ecj.jar ]]; then
  ECJ="/tmp/ecj.jar"
fi

if [[ -f /tmp/r8-java8.jar ]]; then
  R8="/tmp/r8-java8.jar"
elif [[ -f /tmp/r8.jar ]]; then
  R8="/tmp/r8.jar"
fi

if [[ -f /tmp/uber-apk-signer.jar ]]; then
  SIGNER="/tmp/uber-apk-signer.jar"
fi

# Fallback: if standard SDK is present
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z $SDK ]]; then
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
    if [[ -f $d/platforms/android-$TARGET_SDK/android.jar ]]; then SDK=$d; break; fi
  done
fi

if [[ -n $SDK && -f "$SDK/platforms/android-$TARGET_SDK/android.jar" ]]; then
  BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)
  if [[ -n $BT && -x "$BT/aapt2" ]]; then
    AAPT2="$BT/aapt2"
    ANDROID_JAR="$SDK/platforms/android-$TARGET_SDK/android.jar"
    FRAMEWORK_RES="$ANDROID_JAR"
  fi
fi

if [[ -z $AAPT2 || -z $ANDROID_JAR ]]; then
  echo "error: aapt2 or android.jar missing. Please set ANDROID_HOME or install build-tools." >&2
  exit 1
fi

WORK=$(mktemp -d "${TMPDIR:-/tmp}/alveetv-build.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" "$WORK/dex" "$ROOT_DIR/bin"

# 1. Compile Resources
echo "==> Compiling resources with aapt2..."
"$AAPT2" compile --dir launcher/res -o "$WORK/res.zip"

# 2. Link AndroidManifest & Resources
echo "==> Linking APK package..."
"$AAPT2" link -I "$FRAMEWORK_RES" --manifest launcher/AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -o "$WORK/base.apk"

# 3. Compile Java Source Code
echo "==> Compiling Java sources (Java 8 target)..."
SOURCES=()
while IFS= read -r f; do SOURCES+=("$f"); done < <(find launcher/src -name '*.java')

if [[ -n $ECJ && -f $ECJ ]]; then
  "$JAVA" -jar "$ECJ" -8 -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
else
  "$JAVA_HOME/bin/javac" --release 8 -Xlint:none -encoding UTF-8 \
    -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
fi

# 4. Dex bytecode to Dalvik executable (D8)
echo "==> Translating classes to classes.dex (D8)..."
CLASS_FILES=()
while IFS= read -r f; do CLASS_FILES+=("$f"); done < <(find "$WORK/classes" -name '*.class')

if [[ -n $R8 && -f $R8 ]]; then
  "$JAVA" -cp "$R8" com.android.tools.r8.D8 --min-api "$MIN_SDK" --lib "$ANDROID_JAR" \
    --output "$WORK/dex" "${CLASS_FILES[@]}"
else
  "$SDK/build-tools/"*/d8 --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" \
    --output "$WORK/dex" "${CLASS_FILES[@]}"
fi

(cd "$WORK/dex" && zip -q -X "$WORK/base.apk" classes.dex)

# 5. Sign & Zipalign APK
echo "==> Signing & aligning APK..."
if [[ -n $SIGNER && -f $SIGNER ]]; then
  "$JAVA" -jar "$SIGNER" -a "$WORK/base.apk" -o "$WORK/signed" >/dev/null 2>&1
  cp "$WORK/signed/"*-aligned-debugSigned.apk "$ROOT_DIR/bin/alveetv-home.apk"
else
  KEYSTORE=${ALVEETV_KEYSTORE:-$ROOT_DIR/launcher/release.keystore}
  KEYPASS=${ALVEETV_KEYPASS:-alveetvhome}
  if [[ ! -f $KEYSTORE ]]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias alveetv -keyalg RSA -keysize 2048 \
      -validity 10000 -storepass "$KEYPASS" -keypass "$KEYPASS" -dname "CN=AlveeTV Home" >/dev/null
  fi
  "$BT/zipalign" -f -p 4 "$WORK/base.apk" "$WORK/aligned.apk"
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
    --out "$ROOT_DIR/bin/alveetv-home.apk" "$WORK/aligned.apk"
fi

APK_SIZE=$(wc -c < "$ROOT_DIR/bin/alveetv-home.apk" | tr -d ' ')
echo "==> Success! Built: bin/alveetv-home.apk ($((APK_SIZE / 1024)) KB)"
