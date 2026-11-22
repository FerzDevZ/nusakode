# KodeNusa — IDE Android di dalam HP

[![Rilis](https://img.shields.io/badge/rilis-v0.22.0-0f766e?style=flat-square)](https://github.com/FerzDevZ/nusakode/releases/latest)
[![Unduh APK](https://img.shields.io/badge/unduh-APK%2033%20MB-1d4ed8?style=flat-square)](https://github.com/FerzDevZ/nusakode/releases/latest)
[![Uji unit](https://img.shields.io/badge/uji%20unit-374%20lolos-16a34a?style=flat-square)](docs/FITUR.md)
[![Uji perangkat](https://img.shields.io/badge/uji%20perangkat-142%20lolos-16a34a?style=flat-square)](docs/FITUR.md)
[![detekt](https://img.shields.io/badge/detekt-0%20temuan-16a34a?style=flat-square)](detekt.yml)

**KodeNusa adalah IDE Android yang berjalan sepenuhnya di dalam HP: menulis kode, mengompilasi, menandatangani, dan memasang APK — tanpa PC, tanpa Gradle, tanpa internet.**

Aplikasi ini dibangun untuk perangkat yang paling banyak dipakai orang Indonesia: RAM 2–4 GB, penyimpanan 32–64 GB, sinyal yang sering putus. Tidak ada asumsi bahwa pengguna memiliki laptop, Android Studio, atau koneksi stabil. Semua klaim fitur di repositori ini didukung uji otomatis yang bisa Anda jalankan sendiri — apa yang belum terbukti, ditulis terbuka di bawah.

**→ [Unduh KodeNusa 0.22.0](https://github.com/FerzDevZ/nusakode/releases/download/v0.22.0/KodeNusa-0.22.0.apk)** · [semua rilis](https://github.com/FerzDevZ/nusakode/releases) · [daftar fitur lengkap](docs/FITUR.md)

---

## Mulai dalam 3 langkah

1. **Pasang** — unduh APK dari [halaman rilis](https://github.com/FerzDevZ/nusakode/releases/latest), buka, dan izinkan pemasangan dari sumber yang Anda pakai (izin Android standar). KodeNusa **tidak meminta izin penyimpanan sama sekali**; semua berkas lewat pemilih berkas sistem (SAF).
2. **Buat projek** — pilih salah satu dari 10 template siap pakai (lihat daftar di bawah), atau impor `.zip` projek orang lain.
3. **Bangun** — tekan *Build APK*. Toolchain (compiler Java, dexer, signer) sudah ada di dalam APK; hasilnya APK yang bisa langsung dipasang dan dibagikan.

Butuh **Android 7.0 (API 24)** ke atas. Teruji di Android 7, 9, 13, 14, dan 15 (emulator API 24/34/35).

## Apa yang bisa dikerjakan

| Kemampuan | Status | Catatan |
|---|---|---|
| Tulis Java + XML, build jadi APK di dalam HP | ✅ | ECJ 3.25 → dx → apksigner; pipeline 9 tahap di perangkat |
| 10 template siap pakai | ✅ | Halo Dunia, Kasir Warung, Katalog WhatsApp, Absensi Kelas, Laporan Warung, Jurnal Harian, Stok Barang, Kuis Pelajaran, Situs Murni, Web jadi APK |
| Editor kode profesional | ✅ | Gutter digambar ulang penuh (0.21.0): nomor baris selalu akurat saat scroll, wrap, dan ganti ukuran font; baris kursor disorot |
| Cari & ganti seluruh projek | ✅ | Peka huruf besar/kecil pada penggantian |
| **AI mempelajari seluruh projek** (0.21.0) | ✅ | Konteks otomatis: AI menyusun peta projek dan memilih sendiri berkas relevan — tanpa memilih berkas satu per satu; tombol **Pelajari projek** |
| AI asisten (BYOK) | ✅ | Endpoint OpenAI-compatible / Gemini / Qwen / Ollama; **endpoint cadangan otomatis saat utama 503 (0.21.0)**; **AI menulis berkas otomatis di chat biasa — format `<<<BERKAS:>>>` + aturan satu-berkas-satu-jenis (0.22.0)**; tanpa kunci = aplikasi tetap 100% offline |
| Cadangan otomatis harian (0.21.0) | ✅ | Semua projek di-zip per hari, maks 2 salinan, tanpa izin, tanpa dialog |
| Agen perbaikan build otomatis | ✅ | Maksimum 12 berkas, 3 putaran build, tiap tulisan bisa dibatalkan |
| Mode belajar | ✅ | Pelajaran langkah-demi-langkah di editor; progres tersimpan per projek |
| Situs murni (HTML/CSS/JS) + PWA | ✅ | Tanpa APK; pratinjau WebView di dalam aplikasi |
| Git sungguhan + rilis GitHub | ✅ | JGit 3.7.1; unggah APK ke GitHub Release dengan token Anda |
| Palet perintah & tema | ✅ | 4 tema bawaan + tema `.kntema` dengan gerbang kontras WCAG |
| **Python** | ⚠️ | Bisa dijalankan (MicroPython dibawa di APK) untuk skrip sederhana; belum bisa jadi APK |
| **Kotlin / projek Gradle** | ❌ | Tier 0 hanya Java 8; aplikasi menyatakannya terbuka di layar |
| **Model AI di dalam HP** | ❌ | 400–900 MB, 2–8 token/detik di HP 4 GB — ditolak dengan alasan ([ADR-023](docs/03-adr.md)) |

Legenda: ✅ teruji otomatis · ⚠️ terbatas, diungkap jujur · ❌ tidak dikerjakan, dengan alasan.

## Kejujuran teknis (kenapa hal ini penting)

- **APK rilis 33 MB** karena membawa `android.jar` (24 MB) sebagai classpath kompilasi. Memangkasnya membuat kode pengguna yang sah gagal dikompilasi; mengunduhnya belakangan melanggar janji offline. Analisis byte-per-byte: [ADR-028](docs/03-adr.md).
- **Kunci tanda tangan dibuat di HP Anda** saat pertama dipakai ([ADR-019](docs/03-adr.md), [ADR-041](docs/03-adr.md)) — build Anda, milik Anda; tapi APK ini bukan produk Play Store.
- **Kunci AI & token GitHub disandikan Android Keystore** (AES-256-GCM). Tidak ada server KodeNusa — tidak ada satu byte pun yang dikirim ke kami, karena kami tidak punya server.
- **Tanpa telemetri.** Satu-satunya trafik jaringan adalah yang Anda picu sendiri (endpoint AI, GitHub).

## Yang belum terbukti — ditulis terbuka

Kami tidak menulis "sudah jalan" untuk hal yang belum dijalankan:

- Pengukuran di **HP 4 GB fisik** (RAM, suhu, waktu build) — baru data emulator di [docs/08-lembar-ukur-4gb.md](docs/08-lembar-ukur-4gb.md).
- **Unggah APK 33 MB lewat jaringan seluler** — yang teruji: lapisan pengiriman bita dan satu unggahan nyata berkas kecil.
- **Kamera sungguhan** (emulator tidak punya kamera) — yang teruji: deklarasi, penjaga `resolveActivity`, pesan galat.
- **Pass-rate agen AI dengan model nyata** — jalankan `AI_EVAL=1` dengan kunci Anda ([docs/07-eval-agen.md](docs/07-eval-agen.md)).

## Bangun sendiri

```bash
# JDK 17 (bukan 21) + Android SDK (platform 35, build-tools 28.0.3 & 35.0.0)
export JAVA_HOME=/opt/jdk17
export ANDROID_HOME=$HOME/android-sdk

./gradlew :app:assembleRelease               # arm64-v8a + armeabi-v7a, ditandatangani
./gradlew :app:assembleRelease -PsemuaAbi    # + x86_64 (untuk emulator)
./gradlew test detekt                        # 374 uji unit + analisis statis
./gradlew :app:connectedDebugAndroidTest     # 142 uji perangkat (butuh emulator/HP)
```

Pipeline Tier 0 juga bisa dibuktikan tanpa emulator: `scripts/prove/build-hello.sh` membangun APK lewat `:build:hostcli` di JVM host — alur yang sama dengan yang berjalan di HP.

## Arsitektur

```
:app             Compose UI: Workspace, Wizard, Editor, Build, Situs, Git, Agen, AI, Tema
:core:project    Berkas projek, validasi, arsip zip, cari/ganti, impor SAF
:core:build      Kontrak build, SitusStatis, SitusPwa
:core:diagnose   DiagnosticParser + ErrorCatalog (pesan galat berbahasa Indonesia)
:core:ai         Klien OpenAI-compatible, PromptBuilder, PetaProjek, PemilihKonteks, AgenBuild
:core:git        JGit (RepoGit), GitHubRilis, GaleriTemplate
:core:editor     CodeEditorView, GutterView/GutterModel, Highlighter, Rapikan, PenyediaKandidat
:core:ui         Token warna OKLCH, komponen dasar, pemeriksa kontras
:build:tier0     Pipeline perangkat: PREPARE → AAPT_COMPILE → AAPT_LINK → JAVAC → JAR → D8 → PACKAGE → ALIGN → SIGN
:build:hostcli   CLI Tier 0 untuk JVM host (pembuktian tanpa emulator)
```

Alasan di balik tiap keputusan (kenapa tanpa Gradle, kenapa SAF-only, kenapa model AI di HP ditolak, dst.) ada di **41 ADR**: [docs/03-adr.md](docs/03-adr.md). Arsitektur teknis lengkap: [docs/01-arsitektur-teknis.md](docs/01-arsitektur-teknis.md).

## Dokumentasi

| Berkas | Isi |
|---|---|
| [docs/00-visi-produk.md](docs/00-visi-produk.md) | Untuk siapa, masalah apa, dan apa yang sengaja tidak dikerjakan |
| [docs/01-arsitektur-teknis.md](docs/01-arsitektur-teknis.md) | Modul, aliran build, invariant keamanan |
| [docs/02-roadmap-milestone.md](docs/02-roadmap-milestone.md) | Riwayat rilis v0.1.0 → kini dan langkah berikutnya |
| [docs/03-adr.md](docs/03-adr.md) | 32 keputusan arsitektur beserta konsekuensinya |
| [docs/04-backlog-agent-matrix.md](docs/04-backlog-agent-matrix.md) | Agen dan skill yang dipakai membangun proyek ini |
| [docs/05-toolchain-di-perangkat.md](docs/05-toolchain-di-perangkat.md) | Versi tool, alasan pin, cara memverifikasi |
| [docs/06-rencana-2026.md](docs/06-rencana-2026.md) | Rencana fitur + status nyatanya |
| [docs/07-eval-agen.md](docs/07-eval-agen.md) | Cara menguji agen AI, dan apa yang belum terbukti |
| [docs/08-lembar-ukur-4gb.md](docs/08-lembar-ukur-4gb.md) | Lembar ukur RAM, waktu build, suhu baterai |
| [docs/09-rencana-v2-android11.md](docs/09-rencana-v2-android11.md) | Kenapa aplikasi tidak muncul di "data" HP, izin, rencana Android 11+ & HP 4 GB |
| [docs/11-rencana-v0.12.md](docs/11-rencana-v0.12.md) | Rencana perombakan editor (terlaksana di v0.20.0) |
| [docs/10-tema-desain.md](docs/10-tema-desain.md) | Sistem tema dan format `.kntema` |
| [docs/12-saran-ui-ux-ai.md](docs/12-saran-ui-ux-ai.md) · [docs/13-rencana-ui-ux.md](docs/13-rencana-ui-ux.md) · [docs/14-saran-pengembangan.md](docs/14-saran-pengembangan.md) | Rencana UI/UX & AI, termasuk konteks otomatis (14) |
| [docs/FITUR.md](docs/FITUR.md) | Daftar fitur lengkap + uji pembuktinya |
| [DESIGN.md](DESIGN.md) | Sistem warna, tipografi, dan aturan UI |
| [MEMORY.md](MEMORY.md) | Catatan akar masalah yang tidak boleh terulang (buku besar pembelajaran) |
| [PLAN.md](PLAN.md) | Fase kerja dan statusnya |

## Kontribusi

Masukan apa pun diterima — terutama laporan dari HP kelas bawah sungguhan (RAM 2–3 GB). Buka *issue* dengan: tipe HP, versi Android, langkah mengulang, dan tangkapan layar. Untuk perubahan kode, jalankan `./gradlew test detekt` dulu; PR yang membuat uji merah tidak akan diterima.

## Lisensi

Belum ditetapkan. Sampai lisensi dipilih, anggap "semua hak dilindungi": Anda boleh membaca dan membangunnya untuk diri sendiri, tetapi tidak mendistribusikan ulang tanpa izin.
