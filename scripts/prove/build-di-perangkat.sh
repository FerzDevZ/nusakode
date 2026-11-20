#!/usr/bin/env bash
# Membuktikan Tier 0 berjalan DI DALAM Android (bukan hanya di JVM host).
#
# Yang dijalankan: 13 uji instrumentasi di perangkat/emulator, termasuk membangun tiga template
# bawaan sampai menghasilkan APK bertanda tangan v2 yang lolos `apksigner verify`.
#
# Pakai:
#   bash scripts/prove/build-di-perangkat.sh                 # emulator/perangkat yang sedang aktif
#   ADB=/path/ke/adb bash scripts/prove/build-di-perangkat.sh
set -euo pipefail

AKAR="$(cd "$(dirname "$0")/../.." && pwd)"
ADB="${ADB:-${ANDROID_HOME:-$HOME/android-sdk}/platform-tools/adb}"
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "Tidak ada perangkat/emulator yang terhubung. Jalankan emulator dulu." >&2
  exit 2
fi

SDK="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
echo "==> Perangkat: $MODEL · API $SDK · $ABI"

cd "$AKAR"
./gradlew --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=id.kodenusa.app.OnDeviceBuildTest

echo
echo "==> Ringkasan build yang terjadi di perangkat (logcat tag KodeNusaBuild)"
"$ADB" logcat -d -s KodeNusaBuild | grep -E "Durasi build" | tail -5 || true
