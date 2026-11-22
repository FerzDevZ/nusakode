# 09 · Rencana v2 — Android 11+ & HP Minimum 4 GB / 64 GB

> Dokumen ini menjawab tiga hal yang paling sering ditanya: **kenapa KodeNusa tidak muncul di "data" HP**, **izin apa yang sebenarnya dibutuhkan**, dan **fitur apa yang layak dibangun berikutnya** kalau patokannya HP 4 GB / 64 GB.

**Status dokumen:** terakhir diselaraskan dengan rilis **v0.20.0**. Sebagian besar daftar P0–P3 di bagian 5 sudah tuntas; statusnya ditandai langsung di daftar.

---

## 1. "NusaKode tidak ada di data" — penjelasan

| Pertanyaan | Jawaban |
|---|---|
| Nama paket aplikasi | **`id.kodenusa.app`** (bukan `nusakode`). Nama repo GitHub memang `nusakode`, nama aplikasi di layar HP **KodeNusa**. |
| Di mana datanya | `/data/data/id.kodenusa.app/` → di dalamnya `files/projek/` (projek pengguna), `files/keys/` (keystore debug), `shared_prefs/ai.xml`, `shared_prefs/git.xml`. |
| Kenapa tidak terlihat | Sejak **Android 11** folder `/data/data` **ditutup total** untuk aplikasi lain dan untuk pengelola berkas, bahkan lewat kabel USB. Yang bisa dilihat hanya `Android/data/` — dan folder itu **baru dibuat** kalau aplikasi menulis berkas ke penyimpanan luar. KodeNusa **tidak** menulis ke luar, jadi `Android/data/id.kodenusa.app` memang tidak ada. Ini bukan bug, ini scoped storage. |
| Kalau aplikasi tidak terpasang | Setelah `connectedAndroidTest`, Gradle **mencopot** aplikasi dari emulator. Jadi `pm list packages | grep kodenusa` kosong sesudah uji, dan itu normal. |

Konsekuensi desain yang disengaja: data pengguna tinggal di dalam sandbox aplikasi, sehingga **tidak bisa diintip pengelola berkas atau aplikasi lain**, dan ikut terhapus kalau aplikasi di-uninstall. Karena itu ada **Ekspor zip** (SAF) — satu-satunya jalur keluar data yang resmi.

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

**Tidak dideklarasikan, disengaja:** `CAMERA` (tidak perlu kalau memakai intent kamera), kontak, lokasi, mikrofon, `QUERY_ALL_PACKAGES`.

> Pelajaran nyata: 0.8.1 sempat mendeklarasikan `MANAGE_EXTERNAL_STORAGE` dan pemasangan di HP pengguna gagal. Izin itu **dihapus di 0.8.2**; kemampuan folder-di-luar-sandbox tetap ada lewat SAF (persisted tree) yang tidak butuh izin apa pun.

## 3. AI bisa melihat gambar (sudah jalan)

Alur yang dipakai, semuanya **tanpa server perantara**:

```
Photo Picker / galeri / kamera → GambarUntukAi.dataUrl()
   → dikecilkan ke sisi ≤ 1024 px (pangkat dua, dua tahap decode)
   → JPEG mutu 80 → turun bertahap sampai ≤ 700 KB
   → data:image/jpeg;base64,… → ChatMessage(gambar = [...])
   → OpenAiCompatClient kirim content array: [{type:text},{type:image_url}]
```

Batas yang mengikat di HP 4/64: foto 2400×1600 (≈ 4 MB) berakhir **≤ 700 KB** dan ≤ 1024 px — diuji di HP (`GambarAiDeviceTest`). Tanpa pengecilan, satu foto kamera membuat permintaan 5–6 MB: kuota pengguna habis dan model menolak.

Yang sudah tuntas dari backlog lama: tombol **Ambil foto** (v0.9.4), dan pemberitahuan jelas bila model **tidak mendukung gambar** (400 yang menyebut `image_url` diterjemahkan jadi pesan manusiawi + saran model). Yang masih terbuka: tangkapan layar Build otomatis dilampirkan saat "Perbaiki galat build".

## 4. Anggaran HP 4 GB / 64 GB (angka terukur)

| Komponen | Ukuran terukur | Catatan |
|---|---|---|
| **APK rilis (dibagikan sekarang)** | **35,2 MB** | R8 + shrink + arm64/v7a, ditandatangani kunci perangkat/pengguna |
| APK debug (untuk emulator) | ±46 MB | + dex tanpa R8 + aapt2 x86/x86_64 |
| `android.jar` | 26,4 MB | **tidak bisa dipangkas** tanpa memutus kompilasi Java; bahan wajib Tier 0 (ADR-028) |
| libaapt2 | 1,7–2,1 MB per ABI | rilis hanya arm64 + v7a |
| Penyimpanan projek | 5–30 MB per projek | sudah ada tombol **Bersihkan** di layar Build |
| RAM idle | **66 MB PSS** (API 35, RAM 3 GB) | target < 120 MB — **terpenuhi** |
| RAM saat build | belum diukur di HP asli | langkah R1 |

Kesimpulan jujur: **ukuran APK bukan hambatan pasang** di HP 64 GB, tetapi **penting untuk kuota unduh** dan kesan "aplikasi berat".

## 5. Daftar P0–P3: status kini

### P0 — "siap HP kentang"

| # | Butir | Status |
|---|---|---|
| 1 | Rilis arm-only + minify, ditandatangani | ✅ |
| 2 | Mode Hemat otomatis (RAM ≤ 4,2 GB): log dipangkas, sorotan dimatikan untuk berkas besar, undo dibatasi, satu build pada satu waktu | ✅ |
| 3 | Bersihkan hasil build dengan tampilan ukuran | ✅ |
| 4 | Ukur di HP asli (R1–R3) | ⚠️ menunggu HP fisik |

### P1 — AI yang benar-benar berguna

| # | Butir | Status |
|---|---|---|
| 5 | Ambil foto + lampirkan ke percakapan | ✅ v0.9.4 |
| 6 | Beri tahu model tanpa dukungan gambar | ✅ |
| 7 | Evaluasi 10 tugas (≥ 7/10, median ≤ 2 menit) | ✅ 9/10 dua putaran nyata ([docs/07](07-eval-agen.md)) |
| 8 | Agen membaca galat build sendiri + batas putaran jujur | ✅ `AgenBuild` (putaran 3/5, estimasi karakter) |

### P2 — projek & penyimpanan

| # | Butir | Status |
|---|---|---|
| 9 | Folder projek di luar sandbox (SAF persisted tree) | ⛔ belum |
| 10 | Cadangan otomatis harian | 🟡 manual "Cadangkan semua" ✅; otomatis belum |
| 11 | Buka projek dari GitHub (klon) + buat repo | ✅ v0.13 |

### P3 — situs & distribusi

| # | Butir | Status |
|---|---|---|
| 12 | PWA otomatis untuk projek `web-murni` | ✅ |
| 13 | Server lokal Wi-Fi | ❌ **ditolak** ADR-030 |
| 14 | Pratinjau situs dalam app | ✅ |

### Yang sengaja TIDAK dikerjakan

- **Tier 2 (proot + JDK + Gradle)**: butuh ≥ 2 GB RAM bebas; di HP 4 GB ini bunuh diri.
- **Model AI di perangkat**: 400–900 MB, 2–8 token/detik → tidak realistis (ADR-023).

## 6. Kalau aplikasi "tidak bisa dipasang" di Android 11+ / 15

Diuji langsung di emulator **Android 15 (API 35)**: `adb install` → **Success**, aplikasi terbuka, dan uji perangkat penuh lulus (termasuk build Tier 0 di perangkat). Jadi kalau di HP Anda gagal, penyebabnya hampir selalu salah satu dari ini:

| Gejala | Penyebab | Solusi |
|---|---|---|
| "Aplikasi tidak diinstal" tanpa alasan | **Sumber tidak dikenal** diblokir per-aplikasi (Chrome/Files) | Setelan → Aplikasi → Chrome → "Instal aplikasi tidak dikenal" → Izinkan, lalu pasang ulang |
| "Aplikasi berbahaya diblokir Play Protect" | APK di luar Play Store + izin sensitif | KodeNusa sudah membuang `MANAGE_EXTERNAL_STORAGE`; kalau masih muncul: Play Store → profil → Play Protect → setelan → nonaktifkan pemindaian sementara |
| "Aplikasi tidak diinstal" setelah versi lama | Tanda tangan berbeda (APK dari build lain) | Copot versi lama dulu, atau pasang APK dari **satu sumber** (GitHub Releases) |
| "Aplikasi tidak diinstal" di HP 32-bit | APK memuat arm64 + v7a; HP 32-bit tetap jalan lewat v7a | pastikan tidak memakai APK modif |
| Terpasang tapi tidak muncul di launcher | Ikon tidak terbaca oleh launcher tertentu | cari "KodeNusa" di daftar aplikasi; laporkan merek launcher-nya |
| Berkas hasil unduhan berekstensi `.bin`/`.zip` | Browser mengubah nama | ganti nama jadi `KodeNusa-x.y.z.apk` |

## 7. Emulator untuk verifikasi

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
emulator -avd kodenusa35 -no-window -no-audio -no-snapshot -memory 3072 -cores 2 -gpu swiftshader_indirect
# gerbang wajib sebelum rilis
./gradlew :app:connectedDebugAndroidTest
adb install -r dist/KodeNusa-X.Y.Z.apk   # harus "Success"
```
