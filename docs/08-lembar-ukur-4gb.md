# Lembar ukur HP 4 GB / 64 GB (R1)

Tujuan: membuktikan klaim "RAM 4 GB cukup" dengan angka dari HP sungguhan, bukan emulator.
Selama tabel di bawah masih kosong, semua angka performa di dokumen lain berasal dari **emulator**
dan harus disebut begitu.

## Cara mengisi

```bash
export JAVA_HOME=/opt/jdk17 PATH=/opt/jdk17/bin:/opt/gradle/bin:$PATH ANDROID_HOME=$HOME/android-sdk
./gradlew :app:installDebug :app:installDebugAndroidTest
scripts/ukur-hp.sh
```

Skrip akan mencetak satu baris siap tempel. Ulangi **tiga kali** (dingin, hangat, panas) lalu ambil
nilai tengahnya.

## Tabel hasil

| Tanggal | HP | Android | RAM dilaporkan | PSS diam | PSS puncak | Build template | Uji OK | Suhu |
|---|---|---|---|---|---|---|---|---|
| _(belum diukur)_ | | | | | | | | |

## Yang dianggap lulus

| Ukuran | Batas | Alasan |
|---|---|---|
| PSS menganggur | ≤ 100 MB | harus muat bersama WhatsApp + browser di HP 4 GB |
| PSS puncak saat build | ≤ 400 MB | di atas itu OOM killer mulai menutup aplikasi lain |
| Build template pertama | ≤ 5 s | di emulator 1,4–1,9 s; HP kelas bawah wajar lebih lambat |
| 10 build berturut-turut | tanpa ANR | pernah ada ANR pada impor `.zip`, jadi ini diuji terpisah |
| Suhu baterai setelah 3 build | ≤ 43 °C | di atas itu HP mulai menurunkan frekuensi |

## Cara membaca kalau gagal

- **PSS puncak > 400 MB** → kemungkinan build paralel; matikan "mode hemat" bila aktif, lalu ulangi.
- **ANR saat build** → ambil `adb shell dumpsys anr` dan simpan jejaknya; jangan menganggap HP-nya salah.
- **Build > 8 s** → ukur lagi setelah HP dingin; kalau tetap, catat model HP-nya sebagai batas nyata.

## Catatan

"RAM 4 GB" pada brosur biasanya terbaca **3,6–3,9 GB** oleh sistem. Ambang mode hemat KodeNusa
memakai angka yang dilaporkan sistem (4,2 GB), lihat `MEMORY.md` 26-11-19.
