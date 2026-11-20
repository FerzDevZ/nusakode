#!/usr/bin/env bash
# Membangun APK KodeNusa lalu melayaninya lewat jaringan Tailscale, supaya bisa diunduh
# langsung dari HP tanpa kabel dan tanpa server web terpisah.
#
# Pakai:
#   bash scripts/serve-apk.sh            # bangun ulang APK, taruh di dist/, layani
#   bash scripts/serve-apk.sh --tanpa-build   # layani berkas yang sudah ada
#   PORT=9000 bash scripts/serve-apk.sh
#   HOST=<ip-tailscale-lain> bash scripts/serve-apk.sh
set -euo pipefail

AKAR="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

PORT="${PORT:-8080}"
HOST="${HOST:-$(tailscale ip -4 2>/dev/null | head -1)}"
DIST="$AKAR/dist"
VERSI="$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$AKAR/app/build.gradle.kts" | head -1)"
APK="$DIST/KodeNusa-${VERSI}-debug.apk"

if [[ ! -f "$HOST" ]] && [[ -z "$HOST" ]]; then
  echo "Tailscale tidak aktif dan HOST tidak diisi. Jalankan 'tailscale up' dulu." >&2
  exit 2
fi

if [[ "${1:-}" != "--tanpa-build" ]]; then
  echo "==> Merakit APK"
  ( cd "$AKAR" && ./gradlew --console=plain -q :app:assembleDebug )
  mkdir -p "$DIST/contoh"
  cp "$AKAR/app/build/outputs/apk/debug/app-debug.apk" "$APK"
fi

if [[ ! -f "$APK" ]]; then
  echo "APK belum ada di $APK. Jalankan tanpa --tanpa-build." >&2
  exit 3
fi

UKURAN="$(stat -c%s "$APK")"
SIDIK="$(sha256sum "$APK" | cut -c1-16)"
# Halaman unduhan selalu menyebut ukuran dan sidik jari berkas yang benar-benar dilayani.
sed -i -E "s/[0-9]+,[0-9]+ MB · minSdk 24/$(awk "BEGIN{printf \"%.1f\", $UKURAN/1048576}" | tr '.' ',') MB · minSdk 24/; s/<code>[0-9a-f]{16}…<\/code>/<code>${SIDIK}…<\/code>/" "$DIST/index.html"

# Server lama dihentikan supaya port dan berkas yang dilayani selalu yang terbaru.
pkill -f "http.server $PORT" 2>/dev/null || true
sleep 0.4
( cd "$DIST" && setsid nohup python3 -m http.server "$PORT" --bind "$HOST" > /tmp/kodenusa-http.log 2>&1 & )
sleep 1

KODE="$(curl -s -o /dev/null -w '%{http_code}' "http://$HOST:$PORT/")"
if [[ "$KODE" != "200" ]]; then
  echo "Server tidak merespons (HTTP $KODE). Cek /tmp/kodenusa-http.log" >&2
  exit 4
fi

NAMA="$(tailscale status --json 2>/dev/null | python3 -c 'import json,sys;print(json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))' 2>/dev/null || echo "-")"
echo
echo "  Buka di HP (Tailscale harus aktif):"
echo "    http://$HOST:$PORT/"
echo "    http://$NAMA:$PORT/     (kalau MagicDNS aktif)"
echo
echo "  Berkas : $APK"
echo "  Ukuran : $UKURAN byte"
echo "  SHA-256: $SIDIK…"
echo "  Hentikan: pkill -f 'http.server $PORT'"
