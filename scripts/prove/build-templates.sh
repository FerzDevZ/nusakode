#!/usr/bin/env bash
# Membuktikan bahwa ketiga template bawaan bisa dirakit menjadi APK bertanda tangan
# dengan pipeline Tier 0 yang sama seperti yang dipakai di HP.
set -euo pipefail

AKAR="$(cd "$(dirname "$0")/../.." && pwd)"
# Skrip ini harus bisa dijalankan tanpa menyiapkan environment lebih dulu.
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
TEMPLATE="$AKAR/app/src/main/assets/templates"
KERJA="${TMPDIR:-/tmp}/kodenusa-template"
CLI="$AKAR/build/hostcli/build/install/kodenusa-tier0/bin/kodenusa-tier0"
TOOLS="$AKAR/.tools"

if [[ ! -x "$CLI" ]]; then
  echo "CLI belum dibangun. Jalankan: ./gradlew :build:hostcli:installDist"
  exit 1
fi

mkdir -p "$TOOLS"
if [[ ! -f "$TOOLS/ecj.jar" ]]; then
  echo "==> Mengunduh ECJ"
  curl -sSfL "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.46.100/ecj-3.46.100.jar" -o "$TOOLS/ecj.jar"
fi
if [[ ! -f "$TOOLS/debug.keystore" ]]; then
  echo "==> Membuat keystore debug"
  keytool -genkeypair -keystore "$TOOLS/debug.keystore" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" > /dev/null 2>&1
fi

rm -rf "$KERJA"
mkdir -p "$KERJA"

python3 - "$TEMPLATE" "$KERJA" <<'PY'
import json, pathlib, sys
templates = pathlib.Path(sys.argv[1])
kerja = pathlib.Path(sys.argv[2])
index = json.loads((templates / "index.json").read_text())
for entry in index["templates"]:
    app_id = f"id.kodenusa.{entry['id'].replace('-', '')}"
    target = kerja / entry["id"]
    for relative in entry["files"]:
        out = target / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        text = (templates / entry["id"] / relative).read_text()
        out.write_text(text.replace("__APP_NAME__", entry["title"]).replace("__APP_ID__", app_id))
    print(f"siap: {entry['id']} -> {app_id}")
PY

lulus=0
gagal=0
for projek in "$KERJA"/*/; do
  nama="$(basename "$projek")"
  echo
  echo "=== $nama"
  if "$CLI" "$projek" --sdk "$SDK" --tools "$TOOLS" > "$KERJA/$nama.log" 2>&1; then
    apk="$projek/build/tier0/app-debug.apk"
    if "$SDK/build-tools/35.0.0/apksigner" verify "$apk" > /dev/null 2>&1; then
      echo "  APK sah: $(stat -c%s "$apk") byte"
      lulus=$((lulus + 1))
    else
      echo "  APK ada tetapi tanda tangan tidak sah"
      gagal=$((gagal + 1))
    fi
  else
    echo "  build gagal:"
    tail -8 "$KERJA/$nama.log" | sed 's/^/    /'
    gagal=$((gagal + 1))
  fi
done

echo
echo "HASIL TEMPLATE: $lulus lulus, $gagal gagal"
[[ "$lulus" -eq 3 && "$gagal" -eq 0 ]]
