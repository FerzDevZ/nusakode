#!/usr/bin/env bash
# Mengukur KodeNusa di HP sungguhan (rencana R1) — hasilnya diisi ke docs/08-lembar-ukur-4gb.md.
#
# Pakai:
#   1. Pasang APK debug + APK uji:  ./gradlew :app:installDebug :app:installDebugAndroidTest
#   2. Hubungkan HP (USB debugging), lalu:  scripts/ukur-hp.sh
#
# Skrip ini tidak mengubah apa pun di HP selain membuka aplikasi dan menjalankan satu uji build.
set -uo pipefail

ADB="${ADB:-/home/boxd/android-sdk/platform-tools/adb}"
PAKET="id.kodenusa.app"
DATA="$(date +%Y-%m-%d-%H%M)"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "HP tidak terdeteksi. Jalankan 'adb devices' dan izinkan USB debugging." >&2
  exit 1
fi

model=$("$ADB" shell getprop ro.product.model | tr -d '\r')
sdk=$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')
ram_kb=$("$ADB" shell head -1 /proc/meminfo | awk '{print $2}' | tr -d '\r')
ram_gb=$(awk -v kb="${ram_kb:-0}" 'BEGIN {printf "%.1f", kb/1024/1024}')
echo "HP        : $model"
echo "Android   : API $sdk"
echo "RAM total : ${ram_gb} GB (angka yang dilaporkan sistem, bukan brosur)"
echo

# --- 1. RAM saat menganggur -------------------------------------------------
"$ADB" shell am force-stop "$PAKET" >/dev/null 2>&1
sleep 1
"$ADB" shell am start -n "$PAKET/.MainActivity" >/dev/null 2>&1
sleep 6
pss_diam=$("$ADB" shell dumpsys meminfo "$PAKET" | awk '/TOTAL PSS:/ {print $3; exit}' | tr -d '\r')
echo "PSS menganggur : ${pss_diam:-?} KB"

# --- 2. Satu build template sambil memantau puncak RAM ----------------------
puncak=0
mulai=$(date +%s%N)
"$ADB" shell am instrument -w -e class "$PAKET.OnDeviceBuildTest" "$PAKET.test/androidx.test.runner.AndroidJUnitRunner" \
  > "/tmp/ukur-uji-$DATA.log" 2>&1 &
uji_pid=$!
while kill -0 "$uji_pid" 2>/dev/null; do
  nilai=$("$ADB" shell dumpsys meminfo "$PAKET" 2>/dev/null | awk '/TOTAL PSS:/ {print $3; exit}' | tr -d '\r')
  if [[ -n "${nilai:-}" && "$nilai" -gt "$puncak" ]]; then puncak=$nilai; fi
  sleep 1
done
wait "$uji_pid" 2>/dev/null
selesai=$(date +%s%N)
detik=$(awk -v a="$mulai" -v b="$selesai" 'BEGIN {printf "%.1f", (b-a)/1000000000}')
lulus=$(grep -c "OK (" "/tmp/ukur-uji-$DATA.log" || true)
echo "Build template : ${detik} s (uji selesai: ${lulus} baris OK)"
echo "PSS puncak     : ${puncak} KB"

# --- 3. Suhu baterai -------------------------------------------------------
suhu=$("$ADB" shell dumpsys battery | awk -F': ' '/temperature/ {print $2; exit}' | tr -d '\r')
if [[ -n "${suhu:-}" ]]; then
  echo "Suhu baterai   : $(awk -v t="$suhu" 'BEGIN {printf "%.1f", t/10}') °C"
fi

cat <<BARIS

Baris untuk docs/08-lembar-ukur-4gb.md:
| $DATA | $model | API $sdk | ${ram_gb} GB | ${pss_diam:-?} KB | ${puncak:-?} KB | ${detik} s | ${lulus:-0} | ${suhu:-?} |
BARIS
