#!/usr/bin/env bash
# Menyiapkan toolchain yang bisa dipakai DI DALAM Android.
#
# Bedanya dengan toolchain host:
#   - aapt2 harus biner bionic (linkage Android), bukan biner glibc Linux.
#   - JVM tidak ada, jadi jar ECJ/d8/apksigner di-dex lebih dulu dan dijalankan lewat
#     /system/bin/app_process (lihat AppProcessLauncher).
#   - zipalign tidak dibutuhkan lagi; perataan dikerjakan ZipAligner di dalam proses.
#
# Hasil: app/src/main/assets/toolchain/{shared,arm64-v8a,armeabi-v7a,x86_64}
set -euo pipefail

AKAR="$(cd "$(dirname "$0")/../.." && pwd)"
SDK="${ANDROID_HOME:-/home/boxd/android-sdk}"
JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
JAVA="$JAVA_HOME/bin/java"
JAR="$JAVA_HOME/bin/jar"
ASSETS="$AKAR/app/src/main/assets/toolchain"
CACHE="$AKAR/.tools/android-toolchain"
mkdir -p "$CACHE" "$ASSETS/shared"
# Jar sumber disimpan terpisah dari hasil dex. Kalau keduanya memakai path sama, dex kedua
# membaca jar yang sudah kehilangan .class dan resource-nya (ini yang dulu membuat ECJ mati).
SRC="$CACHE/sumber"
OUT="$CACHE/dex"
mkdir -p "$SRC" "$OUT"


BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
# Tier 0 dipin ke API 34: aapt2 2.19 (satu-satunya biner bionic yang stabil kita pakai) menolak
# android.jar API 35 dengan "illegal map type 'string'". Lihat ADR-015.
PLATFORM="$SDK/platforms/android-34"
[ -d "$PLATFORM" ] || PLATFORM="$(ls -d "$SDK"/platforms/android-*/ | sort -V | tail -1)"
D8_JAR="$BT/lib/d8.jar"
# apksigner 0.9+ (build-tools 30+) memanggil java.io.File.toPath() saat menandatangani, yang baru
# ada di API 26. apksigner 0.8 dari build-tools 28 masih murni java.io. Lihat ADR-018.
APKSIGNER_JAR="$(ls "$SDK"/build-tools/*/lib/apksigner.jar 2>/dev/null | sort -V | head -1)"
APKSIGNER_JAR="$SDK/build-tools/28.0.3/lib/apksigner.jar"
[ -f "$APKSIGNER_JAR" ] || { echo "Butuh build-tools 28.0.3 untuk apksigner 0.8." >&2; exit 2; }
# dx hanya ada di build-tools <= 30; d8 modern memanggil java.nio.file (API 26+) sehingga tidak
# bisa dipakai untuk build di Android 7. Lihat ADR-017.
DX_JAR="$(ls "$SDK"/build-tools/*/lib/dx.jar 2>/dev/null | sort -V | tail -1)"
[ -n "$DX_JAR" ] || { echo "Butuh build-tools yang masih memuat lib/dx.jar (mis. 28.0.3)." >&2; exit 2; }
ANDROID_JAR="$PLATFORM/android.jar"
JARJAR_URL="https://repo1.maven.org/maven2/com/googlecode/jarjar/jarjar/1.3/jarjar-1.3.jar"
JARJAR="$SRC/jarjar.jar"
[ -f "$JARJAR" ] || { mkdir -p "$SRC"; curl -sSfL "$JARJAR_URL" -o "$JARJAR"; }

# ECJ 3.25 adalah versi terakhir yang masih berjalan di Android 7: versi 3.26+ memanggil
# java.io.File.toPath() dan java.nio.file.Path, yang baru ada di API 26. Lihat ADR-016.
ECJ_URL="https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.25.0/ecj-3.25.0.jar"
ECJ="$SRC/ecj.jar"
[ -f "$ECJ" ] || curl -sSfL "$ECJ_URL" -o "$ECJ"

# dx direlokasi dari com.android.dx/com.android.dex: ART menaruh com.android.dex di bootclasspath
# sehingga kelas dexer bundel tidak pernah dipakai dan dx gagal dengan NoSuchFieldError.
DX_RELOKASI="$SRC/dxr.jar"
if [ ! -f "$DX_RELOKASI" ] || [ "$DX_JAR" -nt "$DX_RELOKASI" ]; then
  printf 'rule com.android.dex.** id.kodenusa.dxlib.@1\nrule com.android.dx.** id.kodenusa.dxtool.@1\n' \
    > "$SRC/jarjar.rules"
  "$JAVA" -jar "$JARJAR" process "$SRC/jarjar.rules" "$DX_JAR" "$DX_RELOKASI"
fi

# Salinan kelas (bukan dex) dipakai host: CLI host dan perangkat jadi memakai dexer yang sama.
cp "$DX_RELOKASI" "$CACHE/dxr.jar"

dex() { # dex <jar-keluar> <jar-masuk>...
  local keluar="$1"; shift
  local kerja isi masuk
  kerja="$(mktemp -d)"
  isi="$(mktemp -d)"
  "$JAVA" -Xmx1g -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api 24 --lib "$ANDROID_JAR" --output "$kerja" "$@"
  # Resource non-kelas HARUS ikut. ECJ memuat bundle pesan lewat ResourceBundle; kalau
  # messages.properties hilang, setiap pemanggilan Main melempar MissingResourceException dan
  # RuntimeInit membunuh proses (exit 137 tanpa pesan) — bukan galat yang mudah dilacak.
  for masuk in "$@"; do ( cd "$isi" && "$JAR" xf "$masuk" ); done
  find "$isi" -name '*.class' -delete
  find "$isi/META-INF" -maxdepth 1 -type f \
    \( -name '*.SF' -o -name '*.RSA' -o -name '*.DSA' -o -name '*.EC' \) -delete 2>/dev/null || true
  cp "$kerja"/classes*.dex "$isi"/
  rm -rf "$kerja"
  ( cd "$isi" && "$JAR" cfM "$keluar" . )
  rm -rf "$isi"
  echo "  $(basename "$keluar") $(stat -c%s "$keluar") byte"
}

echo "==> Men-dex tool untuk ART"
# javax.lang.model tidak ada di Android; stub ini ditanam di ecj.jar agar ECJ bisa membaca
# bootclasspath tanpa ClassNotFoundException.
STUB_CLASSES="$CACHE/stub-classes"
STUB_JAR="$CACHE/stub.jar"
rm -rf "$STUB_CLASSES"
mkdir -p "$STUB_CLASSES"
"$JAVA_HOME/bin/javac" --release 8 -d "$STUB_CLASSES" \
  "$AKAR/scripts/toolchain/stub/javax/lang/model/SourceVersion.java"
( cd "$STUB_CLASSES" && "$JAR" cf "$STUB_JAR" . )

dex "$OUT/ecj.jar" "$ECJ" "$STUB_JAR"
dex "$OUT/dx.jar" "$DX_RELOKASI"
dex "$OUT/apksigner.jar" "$APKSIGNER_JAR"

echo "==> Menyalin aset bersama"
cp "$OUT/ecj.jar" "$OUT/dx.jar" "$OUT/apksigner.jar" "$ASSETS/shared/"
cp "$ANDROID_JAR" "$ASSETS/shared/android.jar"
# Keystore TIDAK dibundel: aplikasi membuat kuncinya sendiri di perangkat (DebugKeystore.kt)
# supaya tidak ada kunci yang sama di semua pemasangan. CLI host juga membuatnya saat pertama jalan.
rm -f "$ASSETS/shared/debug.keystore"

echo "==> Menulis toolchain.json"
python3 - "$ASSETS/shared" <<'PY'
import hashlib, json, os, sys
root = sys.argv[1]
files = []
for name in sorted(os.listdir(root)):
    if name == "toolchain.json" or not os.path.isfile(os.path.join(root, name)):
        continue
    data = open(os.path.join(root, name), "rb").read()
    files.append({
        "name": name,
        "sha256": hashlib.sha256(data).hexdigest(),
        "executable": name in {"aapt2", "zipalign"},
    })
open(os.path.join(root, "toolchain.json"), "w").write(json.dumps({"files": files}, indent=2) + "\n")
print("  entri:", ", ".join(f["name"] for f in files))
PY

echo "==> Ukuran aset"
du -sh "$ASSETS"/* | sed 's/^/  /'
