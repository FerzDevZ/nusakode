#!/usr/bin/env bash
# Bukti kunci tanda tangan pengguna (docs/14 2.1, ADR-041) — tahap yang tidak bisa dilakukan
# di dalam instrumentation: shell UiAutomator tidak menafsirkan redireksi `>`, jadi pembaruan
# `pm install -r` diorkestrasi dari host.
#
# Prasyarat: satu perangkat/emulator terhubung, aplikasi debug + androidTest terpasang.
# Tahapan:
#   1. Uji perangkat membangun dua APK (versionCode 1 dan 2) dengan kunci pengguna.
#   2. Host menarik kedua APK lewat run-as, memasang v1, lalu memperbaruinya dengan v2.
#   3. `pm install -r` harus Success — bukti signature match lintas build.
set -euo pipefail

ADB=${ADB:-adb}
PAKET_APP="id.kodenusa.app"
PAKET_UJI="id.kodenusa.uji.kunci"
JALUR_V1="files/uji-kunci-v1.apk"
JALUR_V2="files/projek/uji-kunci/build/tier0/app-debug.apk"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "== 1. Bangun dua APK dengan kunci pengguna di perangkat =="
$ADB shell am instrument -w \
  -e class id.kodenusa.app.KunciPenggunaDeviceTest \
  "$PAKET_APP.test/androidx.test.runner.AndroidJUnitRunner" | tail -1

echo "== 2. Tarik kedua APK ke host =="
$ADB exec-out run-as "$PAKET_APP" cat "$JALUR_V1" > "$TMP/v1.apk"
$ADB exec-out run-as "$PAKET_APP" cat "$JALUR_V2" > "$TMP/v2.apk"
ls -l "$TMP/v1.apk" "$TMP/v2.apk"

echo "== 3. Pasang v1 lalu perbarui dengan v2 =="
$ADB shell pm uninstall "$PAKET_UJI" >/dev/null 2>&1 || true
$ADB push "$TMP/v1.apk" /data/local/tmp/kunci-v1.apk >/dev/null
$ADB push "$TMP/v2.apk" /data/local/tmp/kunci-v2.apk >/dev/null
KELUAR1=$($ADB shell pm install /data/local/tmp/kunci-v1.apk)
echo "pm install v1: $KELUAR1"
echo "$KELUAR1" | grep -q "Success"
KELUAR2=$($ADB shell pm install -r /data/local/tmp/kunci-v2.apk)
echo "pm install -r v2: $KELUAR2"
echo "$KELUAR2" | grep -q "Success"

echo "== 4. Beres-beres =="
$ADB shell pm uninstall "$PAKET_UJI" >/dev/null
$ADB shell rm -f /data/local/tmp/kunci-v1.apk /data/local/tmp/kunci-v2.apk >/dev/null
$ADB shell run-as "$PAKET_APP" rm -rf files/kunci-ttd files/projek/uji-kunci files/uji-kunci-v1.apk shared_prefs/kunci-ttd.xml

echo "HASIL KUNCI TTD: pembaruan pm install -r Success — signature match terbukti."
