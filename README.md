# KodeNusa

[![Unduh APK](https://img.shields.io/badge/Unduh-APK%200.12.0-amber?style=flat-square)](https://github.com/FerzDevZ/nusakode/releases/download/v0.12.0/KodeNusa-0.12.0.apk)
[![Rilis](https://img.shields.io/badge/rilis-v0.12.0-blue?style=flat-square)](https://github.com/FerzDevZ/nusakode/releases)
[![Uji](https://img.shields.io/badge/uji-252%20unit%20%C2%B7%2077%20perangkat-brightgreen?style=flat-square)](docs/FITUR.md)

**IDE Android yang menulis kode dan membangun APK sendiri di dalam HP — tanpa PC, tanpa Gradle, tanpa internet.**

Dibuat untuk HP yang paling banyak dipakai di Indonesia: RAM 2–4 GB, penyimpanan 32–64 GB, jaringan yang sering putus. Aplikasi ini tidak mengasumsikan Anda punya laptop, Android Studio, atau koneksi stabil.

**→ [Unduh APK KodeNusa 0.12.0](https://github.com/FerzDevZ/nusakode/releases/download/v0.12.0/KodeNusa-0.12.0.apk)** · [semua rilis](https://github.com/FerzDevZ/nusakode/releases) · [daftar fitur](docs/FITUR.md)

## Pasang

1. Unduh APK di atas, lalu buka dari notifikasi atau folder **Unduhan**.
2. Bila muncul "Aplikasi dari sumber tidak dikenal", izinkan untuk aplikasi **Files/Chrome** yang Anda pakai membuka berkas. Ini izin Android biasa, bukan izin khusus aplikasi ini.
3. KodeNusa tidak meminta izin penyimpanan sama sekali. Anda memilih berkas lewat pemilih berkas sistem.

Butuh **Android 7.0 (API 24)** ke atas. Sudah diuji jalan di Android 7, 9, 13, 14, dan 15.

## Yang bisa dikerjakan

| Kemampuan | Status | Catatan |
|---|---|---|
| Tulis Java + XML layout Android, build jadi APK | ✅ | ECJ 3.25 → dx → apksigner, semua di dalam HP |
| Template siap pakai | ✅ | Halo Dunia, Kasir Warung, Katalog WhatsApp, Web Katalog, Situs Murni |
| Editor kode dengan penyorotan, nomor baris, undo/redo | ✅ | Simpan otomatis 900 ms setelah berhenti mengetik |
| Cari & ganti seluruh projek | ✅ | Peka huruf besar/kecil pada penggantian |
| Situs murni (HTML/CSS/JS) tanpa APK | ✅ | Tombol "Susun situs", bisa dijadikan PWA |
| Pratinjau situs di dalam aplikasi | ✅ | WebView `file://`, tanpa internet |
| Bagikan APK lewat GitHub Release | ✅ | Butuh token GitHub Anda sendiri |
| Git sungguhan (commit, remote, kirim) | ✅ | JGit 3.7.1, tanpa shell |
| AI asisten (BYOK: OpenAI-compatible / Gemini / Qwen / Ollama) | ✅ | Tanpa kunci = aplikasi tetap 100% offline |
| Foto langsung ke AI | ✅ | Layar galat bisa dipotret dan dikirim |
| Agen perbaikan build otomatis | ✅ | Maksimum 12 berkas, 3 putaran build |
| Ambil berkas dari HP (SAF), impor `.zip`, bagikan berkas | ✅ | Tanpa izin penyimpanan |
| Palet perintah | ✅ | Ketik "apk", "bangun", "cari" — tanpa mencari tombol |
| **Python** | ❌ | Tidak dijalankan dan tidak dibuild. Berkas `.py` bisa disunting/dibagikan, dan aplikasi mengatakannya di layar |
| **Kotlin** | ❌ | Tier 0 hanya Java |
| **Projek Gradle** | ❌ | Build di server (Tier 1) belum ada; aplikasi menyatakannya di layar dan mematikan tombol Build dengan alasan |
| Model AI di dalam HP | ❌ | 400–900 MB dan 2–8 token/detik di HP 4 GB — ditolak (ADR-023) |

Daftar lengkap beserta test tag-nya ada di [docs/FITUR.md](docs/FITUR.md).

## Kejujuran soal batas

- **APK rilis 32,3 MB** karena membawa `android.jar` (24,1 MB) sebagai classpath kompilasi. Memangkasnya membuat kode pengguna yang sah gagal dikompilasi; mengunduhnya sekali di awal melanggar janji offline. Analisis byte-per-byte ada di [ADR-028](docs/03-adr.md).
- **Rilis ditandatangani dengan debug keystore yang dibuat di HP Anda** saat pertama dipakai (ADR-019). Artinya APK ini tidak bisa memperbarui aplikasi dari Play Store, dan keystore tiap orang berbeda — build Anda, milik Anda.
- **Kunci AI dan token GitHub disandikan dengan Android Keystore** (AES-GCM, alias `kodenusa.rahasia.v1`). Tidak ada satu byte pun yang dikirim ke server KodeNusa — tidak ada server KodeNusa.
- **Tidak ada telemetri.** Aplikasi tidak mengumpulkan apa pun. Satu-satunya jaringan keluar terjadi bila Anda sendiri mengisi endpoint AI atau menekan unggah ke GitHub.

## Yang belum dibuktikan

Saya tidak akan menulis "sudah jalan" untuk hal yang belum dijalankan:

- **Unggah 32 MB dari HP di jaringan seluler** belum pernah diukur. Yang teruji: lapisan penulisan bita, dan satu unggahan nyata berkas kecil ke GitHub.
- **Kamera sungguhan** belum diuji memotret — emulator tidak punya kamera. Yang teruji: deklarasi `<queries>`, penjaga `resolveActivity`, dan pesan bila tidak ada aplikasi kamera.
- **Angka pass-rate agen AI dengan model sungguhan** masih kosong; jalankan `AI_EVAL=1` dengan kunci Anda ([docs/07-eval-agen.md](docs/07-eval-agen.md)).
- **Pengukuran di HP 4 GB fisik** belum ada; baru baris emulator di [docs/08-lembar-ukur-4gb.md](docs/08-lembar-ukur-4gb.md).

## Bangun sendiri

```bash
export JAVA_HOME=/opt/jdk17                      # JDK 17, bukan 21
export ANDROID_HOME=$HOME/android-sdk            # platform 35 + build-tools 28.0.3 & 35.0.0
./gradlew :app:assembleRelease                   # arm64-v8a + armeabi-v7a, ditandatangani
./gradlew :app:assembleRelease -PsemuaAbi        # + x86_64, untuk emulator
./gradlew test detekt                            # 252 uji unit + analisis statis
./gradlew :app:connectedDebugAndroidTest         # 77 uji perangkat (butuh emulator/HP)
```

Perintah di perangkat memakai jalur yang sama sekali berbeda dari Gradle: `scripts/prove/build-hello.sh` membangun APK lewat `:build:hostcli` di JVM host untuk membuktikan pipeline Tier 0 tanpa emulator.

## Arsitektur sekilas

```
:app             Compose UI: Workspace, Wizard, Editor, Build, Situs, Git, Agen, AI
:core:project    Berkas projek, validasi nama, arsip zip, cari/ganti, SAF import
:core:build      Kontrak build, SitusStatis, SitusPwa
:core:diagnose   DiagnosticParser + ErrorCatalog (pesan Indonesia)
:core:ai         Klien OpenAI-compatible, PromptBuilder, AgenBuild, UbahanBerkas
:core:git        JGit (RepoGit), GitHubRilis (unggah APK)
:core:editor     CodeEditorView (EditText + gutter) dan Highlighter
:core:ui         Token warna OKLCH, komponen (Panel, PrimaryButton, Pill)
:build:tier0     Pipeline: PREPARE → AAPT_COMPILE → AAPT_LINK → JAVAC → JAR → D8 → PACKAGE → ALIGN → SIGN
:build:hostcli   CLI Tier 0 yang bisa dijalankan di JVM host (untuk uji)
```

Detail aliran data, invariant keamanan, dan alasan tiap pilihan ada di [docs/01-arsitektur-teknis.md](docs/01-arsitektur-teknis.md) dan [31 ADR](docs/03-adr.md).

## Dokumentasi

| Berkas | Isi |
|---|---|
| [docs/00-visi-produk.md](docs/00-visi-produk.md) | Untuk siapa, masalah apa, dan apa yang sengaja tidak dikerjakan |
| [docs/01-arsitektur-teknis.md](docs/01-arsitektur-teknis.md) | Modul, aliran build, invariant keamanan |
| [docs/02-roadmap-milestone.md](docs/02-roadmap-milestone.md) | Riwayat rilis v0.1.0 → v0.12.0 dan langkah berikutnya |
| [docs/11-rencana-v0.12.md](docs/11-rencana-v0.12.md) | Rencana v0.12+: ukur HP fisik, editor, ADR kandidat (Material You, galeri tema) |
| [docs/03-adr.md](docs/03-adr.md) | 31 keputusan arsitektur beserta konsekuensinya |
| [docs/04-backlog-agent-matrix.md](docs/04-backlog-agent-matrix.md) | Agen dan skill yang dipakai membangun proyek ini |
| [docs/05-toolchain-di-perangkat.md](docs/05-toolchain-di-perangkat.md) | Versi tool, alasan pin, dan cara memverifikasi |
| [docs/06-rencana-2026.md](docs/06-rencana-2026.md) | Rencana fitur + status nyatanya |
| [docs/07-eval-agen.md](docs/07-eval-agen.md) | Cara menguji agen AI, dan apa yang belum terbukti |
| [docs/08-lembar-ukur-4gb.md](docs/08-lembar-ukur-4gb.md) | Lembar ukur RAM, waktu build, suhu baterai |
| [docs/FITUR.md](docs/FITUR.md) | Daftar fitur + jumlah uji |
| [DESIGN.md](DESIGN.md) | Sistem warna, tipografi, dan aturan UI |
| [MEMORY.md](MEMORY.md) | Catatan akar masalah yang tidak boleh terulang |
| [PLAN.md](PLAN.md) | Fase kerja dan statusnya |

## Lisensi

Belum ditetapkan. Sampai lisensi dipilih, anggap "semua hak dilindungi": Anda boleh membaca dan membangunnya untuk diri sendiri, tetapi tidak mendistribusikan ulang tanpa izin.
