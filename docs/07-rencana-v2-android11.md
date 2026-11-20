# Rencana v2 — Android 11+ & HP minimum 4 GB / 64 GB

> Dokumen ini menjawab tiga hal yang paling sering ditanya: **kenapa KodeNusa tidak muncul di
> "data" HP**, **izin apa yang sebenarnya dibutuhkan** (karena AI sekarang bisa melihat gambar),
> dan **fitur apa yang layak dibangun berikutnya** kalau patokannya HP 4 GB / 64 GB.

## 1. "NusaKode tidak ada di data" — penjelasan

| Pertanyaan | Jawaban |
|---|---|
| Nama paket aplikasi | **`id.kodenusa.app`** (bukan `nusakode`). Nama repo GitHub memang `nusakode`, nama aplikasi di layar HP **KodeNusa**. |
| Di mana datanya | `/data/data/id.kodenusa.app/` → di dalamnya `files/projek/` (projek pengguna), `files/keys/` (keystore debug), `shared_prefs/ai.xml`, `shared_prefs/git.xml`. |
| Kenapa tidak terlihat | Sejak **Android 11** folder `/data/data` **ditutup total** untuk aplikasi lain dan untuk pengelola berkas, bahkan lewat kabel USB. Yang bisa dilihat hanya `Android/data/` — dan folder itu **baru dibuat** kalau aplikasi menulis berkas ke penyimpanan luar. KodeNusa sekarang **tidak** menulis ke luar, jadi `Android/data/id.kodenusa.app` memang tidak ada. Ini bukan bug, ini scoped storage. |
| Kalau aplikasi tidak terpasang | Setelah `connectedAndroidTest`, Gradle **mencopot** aplikasi dari emulator. Jadi `pm list packages | grep kodenusa` kosong sesudah uji, dan itu normal. |

Konsekuensi desain yang disengaja: data pengguna tinggal di dalam sandbox aplikasi, sehingga
**tidak bisa diintip pengelola berkas atau aplikasi lain**, dan ikut terhapus kalau aplikasi
di-uninstall. Karena itu ada **Ekspor zip** (SAF) — satu-satunya jalur keluar data yang resmi.

## 2. Izin: apa yang benar-benar diminta

Prinsipnya: **minta paling sedikit yang cukup**, dan izin besar hanya lewat tombol yang dijelaskan.

| Kebutuhan | Izin | Kapan diminta | Catatan |
|---|---|---|---|
| Unduh/serve AI (BYOK) | `INTERNET` | otomatis | tanpa kunci AI tidak ada lalu lintas jaringan sama sekali |
| Pasang APK hasil build | `REQUEST_INSTALL_PACKAGES` | saat menekan Pasang | sistem tetap menampilkan dialog setuju |
| Pilih foto untuk AI (Android 13+) | **tidak ada** | — | **Photo Picker** sistem, tanpa dialog izin |
| Pilih foto (Android 11–12 / ROM lama) | `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (maxSdk 32) | hanya bila Photo Picker tidak ada | deklarasi sudah ada, permintaan runtime menyusul |
| "Folder di mana saja" (opsional) | **tidak diminta** | — | `MANAGE_EXTERNAL_STORAGE` **dihapus di 0.8.2**: izin ini membuat Android 13+ menandai app "Setelan terbatas" dan Play Protect menghalangi pemasangan dari luar Play Store. SAF sudah cukup |
| Ambil foto langsung | **tidak ada** | — | memakai aplikasi kamera lewat intent `ACTION_IMAGE_CAPTURE` + FileProvider |

**Tidak dideklarasikan, disengaja:** `CAMERA` (tidak perlu kalau memakai intent kamera), kontak,
lokasi, mikrofon, `QUERY_ALL_PACKAGES`.

> Pelajaran nyata: 0.8.1 sempat mendeklarasikan `MANAGE_EXTERNAL_STORAGE` dan pemasangan di HP
> pengguna gagal. Izin itu **dihapus di 0.8.2**; kemampuan folder-di-luar-sandbox tetap ada lewat
> SAF (persisted tree) yang tidak butuh izin apa pun.

## 3. AI bisa melihat gambar (dasar sudah jalan)

Alur yang dipakai, semuanya **tanpa server perantara**:

```
Photo Picker / galeri → GambarUntukAi.dataUrl()
   → dikecilkan ke sisi ≤ 1024 px (pangkat dua, dua tahap decode)
   → JPEG mutu 80 → turun bertahap sampai ≤ 700 KB
   → data:image/jpeg;base64,… → ChatMessage(gambar = [...])
   → OpenAiCompatClient kirim content array: [{type:text},{type:image_url}]
```

Batas yang mengikat di HP 4/64: foto 2400×1600 (≈ 4 MB) berakhir **≤ 700 KB** dan ≤ 1024 px —
diuji di HP (`GambarAiDeviceTest`, 2 uji). Tanpa pengecilan, satu foto kamera membuat permintaan
5–6 MB: kuota pengguna habis dan model menolak.

Yang masih kurang (masuk backlog P1): tombol **Ambil foto** (intent kamera), **tangkapan layar
Build** otomatis dilampirkan saat "Perbaiki galat build", dan pemberitahuan jelas bila model yang
dipilih **tidak mendukung gambar** (sebagian model murah menolak `image_url`).

## 4. Anggaran HP 4 GB / 64 GB (angka nyata dari rilis 0.9.0)

| Komponen | Ukuran terukur | Catatan |
|---|---|---|
| **APK rilis (dibagikan sekarang)** | **32,2 MB (30,7 MiB)** | R8 + shrink + arm-only, ditandatangani kunci debug |
| APK debug (untuk emulator) | 45,9 MB | + dex tanpa R8 + aapt2 x86/x86_64 |
| dex | 11,9 MB debug → ±6 MB rilis | dipangkas R8 |
| `android.jar` | 26,4 MB | **tidak bisa dipangkas** tanpa memutus kompilasi Java; bahan wajib Tier 0 |
| libaapt2 | 1,7–2,1 MB per ABI | rilis hanya arm64 + v7a |
| Penyimpanan projek | 5–30 MB per projek | sudah ada tombol **Bersihkan** di layar Build |
| RAM idle | **66 MB PSS** (API 35, RAM 3 GB) | target < 120 MB — **terpenuhi** |
| RAM saat build | belum diukur di HP asli | langkah R1 |

Kesimpulan jujur: **ukuran APK bukan hambatan pasang** di HP 64 GB (45 MB ≈ 0,1% ruang), tetapi
**penting untuk kuota unduh** dan kesan "aplikasi berat". Prioritas ukuran: rilis arm-only +
minify → **±30 MB**.

## 5. Fitur yang layak dibangun berikutnya (urut nilai untuk 4/64)

### P0 — wajib sebelum disebut "siap HP kentang"
1. **Rilis arm-only + minify, ditandatangani** (±30 MB) — `splits.abi` + `ndk.abiFilters` + keystore rilis.
2. **Mode Hemat otomatis** (`ActivityManager.isLowRamDevice` atau RAM ≤ 3 GB): log build 2.000 → 600 baris, sorotan sintaks dimatikan untuk berkas > 200 KB, undo dibatasi 50 langkah, satu proses build pada satu waktu.
3. **Bersihkan hasil build**: tombol yang menampilkan "build/ 18,4 MB" lalu menghapusnya (satu projek atau semua).
4. **Ukur di HP asli**: waktu build template, PSS idle, PSS puncak, suhu 10 build berturut-turut (R1–R3).

### P1 — AI yang benar-benar berguna
5. **Ambil foto** + lampirkan tangkapan galat build otomatis ke percakapan.
6. **Beri tahu model tanpa dukungan gambar** (deteksi penolakan `image_url`, sarankan model lain).
7. **Evaluasi 10 tugas** (`docs/07-eval-agen.md`): ≥ 7/10 tanpa disentuh, median ≤ 2 menit.
8. **Agen membaca galat build sendiri** setelah 3 putaran → tampilkan "coba lagi dengan saran ini".

### P2 — projek & penyimpanan
9. **Folder projek di luar sandbox** (SAF persisted tree) supaya projek tidak hilang saat uninstall.
10. **Cadangkan otomatis** harian ke zip di `cacheDir` (maks 2 salinan, dibersihkan sendiri).
11. **Buka projek dari GitHub** (clone) dan **buat repo** dari app (G3–G5).

### P3 — situs & distribusi
12. **PWA otomatis** (`manifest.json` + service worker) untuk projek `web-murni`.
13. **Server lokal Wi-Fi** (`com.sun.net.httpserver`) supaya keluarga bisa membuka situs dari HP.
14. **Pratinjau situs dalam app** — sadar biaya: WebView +40 MB RAM, jadi **opsional** dan hanya di mode normal.

### Yang sengaja TIDAK dikerjakan
- **Tier 2 (proot + JDK + Gradle)**: butuh ≥ 2 GB RAM bebas; di HP 4 GB ini bunuh diri.
- **Model AI di perangkat**: 400–900 MB, 2–8 token/detik → tidak realistis.
- **Python**: hanya mungkin lewat Tier 2, jadi tidak dijanjikan di UI.

## 6. Definisi selesai untuk "fokus 4/64"

Rilis berikutnya baru boleh disebut "optimal untuk HP 4/64" kalau semua ini terpenuhi dan
angkanya ditempel di `docs/FITUR.md`:

```bash
# 1. ukuran
ls -l app/build/outputs/apk/release/*.apk      # ≤ 30 MB
# 2. memori & waktu (HP asli)
adb shell dumpsys meminfo id.kodenusa.app | grep TOTAL PSS   # idle ≤ 120 MB
# 3. build template di HP asli
#    halo-dunia ≤ 20 s, kasir-warung ≤ 30 s, 10 build berturut tanpa ANR
# 4. penyimpanan
#    setelah 3 build: build/ ≤ 30 MB per projek, tombol bersih-bersih bekerja
```

## 7. Kalau aplikasi "tidak bisa dipasang" di Android 11+ / 15

Diuji langsung di emulator **Android 15 (API 35)**: APK 0.8.2 `adb install` → **Success**, aplikasi
terbuka, dan **48 uji perangkat lulus** (termasuk build Tier 0 di perangkat). Jadi kalau di HP Anda
gagal, penyebabnya hampir selalu salah satu dari ini:

| Gejala | Penyebab | Solusi |
|---|---|---|
| "Aplikasi tidak diinstal" tanpa alasan | **Sumber tidak dikenal** diblokir per-aplikasi (Chrome/Files) | Setelan → Aplikasi → Chrome → "Instal aplikasi tidak dikenal" → Izinkan, lalu pasang ulang |
| "Aplikasi berbahaya diblokir Play Protect" | APK di luar Play Store + izin sensitif | 0.8.2 sudah membuang `MANAGE_EXTERNAL_STORAGE`; kalau masih muncul: Play Store → profil → Play Protect → setelan → nonaktifkan pemindaian sementara |
| "Aplikasi tidak diinstal" setelah versi lama | Tanda tangan berbeda (APK dari build lain) | Copot versi lama dulu, atau pasang APK dari **satu sumber** (GitHub Releases) |
| "Aplikasi tidak diinstal" di HP 32-bit | APK memuat arm64 + v7a; HP 32-bit tetap jalan lewat v7a | pastikan tidak memakai APK modif |
| Terpasang tapi tidak muncul di launcher | Ikon tidak terbaca oleh launcher tertentu | cari "KodeNusa" di daftar aplikasi; laporkan merek launcher-nya |
| Berkas hasil unduhan berekstensi `.bin`/`.zip` | Browser mengubah nama | ganti nama jadi `KodeNusa-0.8.2-debug.apk` |

Angka nyata dari emulator Android 15 (RAM 3 GB, setara HP 4/64):

| Ukuran | Hasil |
|---|---|
| Pemasangan | `Success` (tanpa peringatan izin) |
| PSS saat idle | **66 MB** (target < 120 MB) |
| Waktu uji perangkat penuh | 48 uji, 2 menit, 0 gagal |

## 8. Emulator untuk verifikasi (dipakai proyek ini)

| AVD | API | Kegunaan |
|---|---|---|
| `kodenusa24` | 24 (Android 7.0) | batas bawah `minSdk` |
| `kodenusa34` | 34 (Android 14) | perilaku scoped storage modern |
| **`kodenusa35`** | **35 (Android 15)** | **wajib** untuk memastikan pemasangan & build tetap jalan di HP baru |

```bash
# siapkan sekali
sdkmanager "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n kodenusa35 -k "system-images;android-35;google_apis;x86_64" -d pixel_5
# jalankan (RAM 3 GB = HP 4/64)
sg kvm -c "$ANDROID_HOME/emulator/emulator -avd kodenusa35 -no-window -no-audio -no-snapshot \
  -memory 3072 -cores 2 -gpu swiftshader_indirect"
# gerbang wajib sebelum rilis
./gradlew :app:connectedDebugAndroidTest
adb install -r dist/KodeNusa-X.Y.Z-debug.apk   # harus "Success"
```
