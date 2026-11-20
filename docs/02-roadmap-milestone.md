# Roadmap dan riwayat rilis

Versi terakhir: **0.10.0** (`versionCode 22`), dirilis 20 November 2026.

## Fase

| Fase | Tujuan | Status |
|---|---|---|
| **F0 — Bukti** | Membuktikan APK bisa dibangun di dalam HP | ✅ selesai (v0.1.0) |
| **F1 — Bisa dipakai** | Projek, berkas, simpan, build berulang | ✅ selesai (v0.4.0) |
| **F2 — Bisa dibagikan** | Situs, Git, GitHub, unggah APK | ✅ selesai (v0.9.5) |
| **F3 — Bisa diandalkan** | Mode hemat, rahasia tersandi, palet, uji perangkat 77 | ✅ selesai (v0.9.6) |
| **F3b — Jujur di layar** | Menghapus klaim yang tidak ditepati kode + dokumentasi ditulis ulang | ✅ selesai (v0.9.7) |
| **F4 — Bisa dipercaya orang lain** | Klon dari GitHub, laporan crash, angka HP nyata | ⏳ berikutnya |
| **F5 — Ekosistem** | Kotlin, projek Gradle, Tier 1/2 | ❌ belum, keputusan ditunda |

## Riwayat rilis

| Versi | Isi utama |
|---|---|
| **0.1.0** | Build APK pertama yang berhasil **di dalam HP** (API 24). 9 akar masalah toolchain diselesaikan |
| 0.2.0–0.2.1 | Pipeline stabil, 3 template, `DiagnosticParser` + `ErrorCatalog` |
| **0.3.0** | W^X di Android 10+ diselesaikan (`nativeLibraryDir` + dex tulis); `ZipAligner` |
| **0.4.0** | Projek sebagai folder, SAF impor/ekspor, arsip zip, ADR-022 (tanpa izin penyimpanan) |
| **0.5.0** | AI BYOK: klien OpenAI-compatible, prompt berbatas Tier 0, ADR-023 |
| **0.6.0** | Pindah/salin berkas, agen build, Git lewat UI |
| **0.7.0** | Situs murni (ADR-025), panel terbit, layar Git (JGit + GitHub) |
| **0.8.0** | Papan projek agen, gerbang build ke AI, layar Agen |
| 0.8.1 | Vision: gambar ikut terkirim, diperkecil dulu |
| **0.8.2** | Perbaikan instalasi di Android 13+: `MANAGE_EXTERNAL_STORAGE` dibuang |
| **0.9.0** | Mode hemat RAM, pembersih `build/`, batas log, rilis arm-only terbukti minified |
| 0.9.1 | Rantai galat kompilasi, `maxLines` editor, wizard `Jenis`, cari se-projek |
| 0.9.2 | Ambil berkas dari HP, bagikan berkas, kabar aksi di panel |
| 0.9.3 | Rahasia tersandi Keystore, PWA, eval agen, lembar ukur 4 GB, ADR-028 |
| 0.9.4 | Foto ke AI, ganti semua se-projek, impor zip tanpa ANR, alasan tombol mati |
| 0.9.5 | Pratinjau situs di dalam aplikasi, bagikan APK lewat GitHub Release |
| 0.9.6 | Palet perintah, progres unggah bertahap, catatan jujur soal Python |
| **0.9.7** | Janji "build di server" dihapus dari UI; seluruh dokumentasi ditulis ulang agar cocok dengan kenyataan |
| **0.9.8** | Laporan galat offline + tombol bagikan (ADR-032); klon repo GitHub dari layar projek; buat repo baru dari layar Git; catatan jujur untuk berkas `.kt` |
| **0.9.9** | Perapatan tampilan (ADR-033): tombol sekunder berwujud, kolom nama wizard selalu terlihat di bilah bawah, kelompok pilihan diberi judul, Git menyuruh siapkan repo dulu |
| **0.10.0** | Python bisa dijalankan (ADR-034): MicroPython v1.25 dibawa sebagai libmicropython.so, tombol Jalankan + konsol keluaran di editor, batas 20 detik |

Artefak 0.10.0: `KodeNusa-0.10.0.apk`, **32.772.723 bita**, sha256 `50a56613334cda7dcedf9138639a2f6f28ca2911a53c143956690ae67d5a7895`.

## Berikutnya, berurutan menurut risiko yang dihilangkan

### 1. ~~Hapus janji palsu "build di server"~~ ✅ dikerjakan 20-11-2026
`ProjectLoader` dulu menampilkan "build akan diarahkan ke server" untuk projek yang punya `build.gradle`, padahal Tier 1 tidak ada. Sekarang pesannya menyebut batas sebenarnya: *"Tier 0 (di dalam HP) hanya membangun projek kodenusa.json; build di server belum tersedia, jadi projek ini belum bisa dibangun di sini."* Tombol Build tetap dimatikan dengan alasan tertulis (`ProjectValidation.bisaDibangun`).

### 2. Penangkap crash + "Bagikan laporan" (offline)
`Thread.setDefaultUncaughtExceptionHandler` menulis laporan kecil (versi, API, RAM, jejak tumpukan) ke berkas, lalu layar berikutnya menawarkan "Bagikan laporan" lewat `FileProvider` yang sudah ada. Tanpa telemetri, tanpa server. **Ini satu-satunya cara kita tahu apa yang rusak di HP orang.**

### 3. Klon projek dari GitHub
`RepoGit.klon()` sudah ada dan teruji tetapi belum tersambung ke UI. Menyambungkannya melengkapi perjalanan dua arah: kirim dari HP A, lanjutkan di HP B. Butuh satu repo uji sekali pakai untuk verifikasi nyata.

### 4. Catatan "Kotlin belum didukung"
Sama seperti `.py`: saat pengguna membuka `.kt`, jelaskan bahwa Tier 0 hanya Java. **Jangan** menambahkan `kotlinc` (ADR-003).

### 5. Yang menunggu perangkat atau kunci milik pengguna
- Ukur di HP 4 GB fisik → isi [lembar ukur](08-lembar-ukur-4gb.md).
- Uji unggah 32 MB di jaringan seluler.
- Uji kamera sungguhan memotret.
- `AI_EVAL=1` dengan kunci sendiri → isi [eval agen](07-eval-agen.md).

## Ditunda dengan alasan tertulis

| Rencana | Alasan ditunda | ADR |
|---|---|---|
| Kotlin di perangkat | Ukuran dan JVM tidak realistis di 4 GB | ADR-003 |
| Tier 1 (build di server) | Butuh server + data pengguna berpindah | ADR-001, ADR-007 |
| Tier 2 proot/JDK | Hanya layak ≥ 4 GB, dan menambah 200–400 MB | ADR-001 |
| Model AI di dalam HP | 400–900 MB, 2–8 token/detik | ADR-023 |
| Play Store | APK langsung sudah bisa dibagikan | ADR-010 |
| Terminal | Permukaan serangan terlalu besar | ADR-008 |