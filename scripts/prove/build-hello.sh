#!/usr/bin/env bash
# Membuktikan pipeline Tier 0 menghasilkan APK yang benar-benar valid dan bisa diverifikasi.
# Yang diuji di sini adalah kode Kotlin yang sama yang dipakai di HP (Tier0BuildEngine),
# bukan skrip tiruan.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Skrip ini harus bisa dijalankan tanpa menyiapkan environment lebih dulu.
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS="$ROOT/.tools"
PROJECT="$ROOT/examples/hello"
ECJ_VERSION="3.46.100"
ECJ_URL="https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/${ECJ_VERSION}/ecj-${ECJ_VERSION}.jar"

mkdir -p "$TOOLS"

if [ ! -f "$TOOLS/ecj.jar" ]; then
    echo "==> Mengunduh ECJ $ECJ_VERSION"
    curl -sSL -o "$TOOLS/ecj.jar" "$ECJ_URL"
fi

if [ ! -f "$TOOLS/debug.keystore" ]; then
    echo "==> Membuat keystore debug"
    keytool -genkeypair \
        -keystore "$TOOLS/debug.keystore" \
        -storepass android -keypass android \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

BIN="$ROOT/build/hostcli/build/install/kodenusa-tier0/bin/kodenusa-tier0"
if [ ! -x "$BIN" ]; then
    echo "==> Membangun CLI Tier 0"
    "$ROOT/gradlew" -q --console=plain :build:hostcli:installDist
fi

rm -rf "$PROJECT/build"

echo "==> Build dingin"
START=$(date +%s)
"$BIN" "$PROJECT" --sdk "$SDK" --tools "$TOOLS"
COLD=$(( $(date +%s) - START ))

APK="$PROJECT/build/tier0/app-debug.apk"
[ -f "$APK" ] || { echo "GAGAL: APK tidak terbentuk"; exit 1; }

BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
echo "==> Verifikasi tanda tangan"
"$BUILD_TOOLS/apksigner" verify --verbose "$APK" | sed 's/^/    /'

echo "==> Isi APK"
unzip -l "$APK" | sed 's/^/    /'

echo "==> Build inkremental (harus lebih cepat)"
START=$(date +%s)
"$BIN" "$PROJECT" --sdk "$SDK" --tools "$TOOLS" >/dev/null
WARM=$(( $(date +%s) - START ))

echo
echo "HASIL PROVE"
echo "  APK        : $APK"
echo "  Ukuran     : $(stat -c%s "$APK") byte"
echo "  Build dingin: ${COLD}s"
echo "  Build kedua : ${WARM}s"
echo "  Tanda tangan: valid (apksigner verify lolos)"
