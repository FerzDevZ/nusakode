# 03 — Architecture Decision Records

Format ringkas. Setiap ADR: konteks → keputusan → konsekuensi → alternatif yang ditolak.

---

## ADR-001 · Build hybrid 3-tier (Tier 0 lokal, Tier 1 cloud, Tier 2 proot)

**Status:** Diterima · **Tanggal:** 2025-11-20

**Konteks:** AndroidIDE (acuan terbaik saat ini) mensyaratkan 1.5–2 GB RAM bebas + 4 GB storage. Median HP Indonesia 2–4 GB RAM / 32–64 GB storage. Cloud-only IDE mati tanpa internet.

**Keputusan:** Tiga mesin build di balik satu interface `BuildEngine`. Default selalu Tier 0. Tier 1 hanya setelah consent. Tier 2 untuk power user.

**Konsekuensi:**
- (+) Menjangkau HP 2 GB tanpa mengorbankan kemampuan projek besar.
- (+) Offline tetap menghasilkan APK.
- (−) Tiga jalur untuk dipelihara; risiko divergensi perilaku. Dimiliki oleh interface tunggal + contract test yang sama untuk ketiga implementasi.

**Ditolak:** Cloud-only (butuh internet + biaya server jadi bottleneck + melanggar prinsip lokal-first); proot-only (mengulang kegagalan AndroidIDE di segmen yang justru jadi target).

---

## ADR-002 · Native Kotlin + Jetpack Compose, bukan Flutter/WebView

**Status:** Diterima

**Konteks:** IDE = editor teks berkinerja tinggi, filesystem, dan eksekusi proses. Ketiganya butuh akses langsung ke platform.

**Keputusan:** Kotlin + Compose, `minSdk 24`, ABI `arm64-v8a` + `armeabi-v7a`.

**Konsekuensi:**
- (+) Akses penuh ke ProcessBuilder, SAF, MediaStore, FileProvider; satu bahasa dengan tier build.
- (+) APK lebih kecil daripada membawa engine Flutter (±15–20 MB) + runtime Dart.
- (−) Tidak ada reuse UI lintas platform; tidak masalah karena target hanya Android.

**Ditolak:** Flutter (overhead memori + proses build butuh Gradle → kontradiksi dengan Tier 0 sendiri); WebView + CodeMirror (jank di HP rendah, dan tidak bisa menjalankan binary).

---

## ADR-003 · Tier 0 hanya mengompilasi Java (ECJ), Kotlin ditunda

**Status:** Diterima

**Konteks:** `kotlinc` adalah program JVM; ART tidak menjalankan `kotlinc` tanpa JDK port (±60 MB + JRE) — itu langsung membatalkan anggaran 70 MB.

**Keputusan:** Tier 0 = Java 11 via ECJ. Kotlin ditawarkan sebagai Tier 1 (cloud) atau Tier 2 (proot + JDK). UI menyatakan ini di depan, bukan setelah gagal build.

**Konsekuensi:**
- (+) Bundel toolchain tetap kecil, build tetap cepat.
- (−) Sebagian user modern ekspektasi Kotlin. Dimigasi dengan template Java-first + jalur upgrade jelas.

**Ditolak:** Memaksa Kotlin di Tier 0 (membuat bundel > 150 MB dan build > 3 menit di HP 2 GB).

---

## ADR-004 · Editor: Sora Editor native, bukan Compose `BasicTextField`

**Status:** Diterima

**Konteks:** Compose text field merender seluruh teks; file 2.000 baris akan jank dan memakan memori.

**Keputusan:** `io.github.Rosemoe.sora-editor` (view Android native) dibungkus `AndroidView` dengan state yang dikelola `:core:editor`.

**Konsekuensi:**
- (+) Rendering viewport saja, incremental highlight, undo stack bawaan, TextMate grammar siap pakai.
- (−) Api-compose boundary harus dijaga hati-hati (jangan rekomposisi tiap karakter).

**Ditolak:** CodeMirror di WebView (jembatan JS↔Kotlin, memori, dan startup lambat); `BasicTextField` (tidak lolos anggaran §7).

---

## ADR-005 · Format projek sendiri (`kodenusa.json`), Gradle bukan syarat

**Status:** Diterima

**Konteks:** Gradle adalah alasan utama IDE on-device butuh 2 GB RAM dan 4 GB storage.

**Keputusan:** `kodenusa.json` sebagai manifest build untuk Tier 0. Kehadiran `build.gradle` otomatis mengalihkan projek ke Tier 1/2.

**Konsekuensi:**
- (+) Build terukur, cepat, dan bisa di-cache per file.
- (−) Tidak kompatibel langsung dengan projek Android Studio. Dimigrasi satu arah: importer `build.gradle` sederhana → `kodenusa.json` untuk kasus umum (dikerjakan di M6).

**Ditolak:** Menjalankan Gradle di Tier 0 (kontradiksi seluruh premis produk).

---

## ADR-006 · Eksekusi tool via `ProcessBuilder` dengan argumen list, tanpa shell

**Status:** Diterima

**Konteks:** Input user (nama file, applicationId, resource) mengalir ke perintah eksternal.

**Keputusan:** Semua proses memakai `ProcessBuilder(List<String>)`, tanpa `sh -c`, tanpa string interpolation. Path diverifikasi `startsWith(workspaceRoot)` setelah `canonicalPath`.

**Konsekuensi:**
- (+) Menutup command injection sepenuhnya; audit mudah.
- (−) Beberapa tool yang butuh pipe harus dipanggil bertahap, bukan satu baris shell. Dapat diterima.

---

## ADR-007 · Offline-first: cloud selalu opt-in eksplisit

**Status:** Diterima

**Keputusan:** Tidak ada request jaringan otomatis yang membawa kode user. Telemetri opt-in, agregat, tanpa isi kode.

**Konsekuensi:** (+) Kepercayaan pengguna dan kepatuhan UU PDP lebih mudah. (−) Lebih sedikit data produk; metrik utama harus dari build lokal.

---

## ADR-008 · Tanpa terminal di v1

**Status:** Diterima

**Konteks:** Terminal menggoda, tetapi menjadi permukaan serangan terbesar dan sumber dukungan tak berujung.

**Keputusan:** Tidak ada terminal sampai minimal M7. Build log ditampilkan read-only.

**Konsekuensi:** (+) Permukaan serangan kecil, onboarding sederhana. (−) Power user (P3) mungkin kecewa; ditawarkan Tier 2 proot sebagai jalan keluar terpisah.

---

## ADR-009 · Projek = folder biasa, tanpa database sebagai sumber kebenaran

**Status:** Diterima

**Keputusan:** Filesystem adalah sumber kebenaran. Room hanya menyimpan indeks/metadata yang bisa dibangun ulang (riwayat, preferensi, cache simbol).

**Konsekuensi:** (+) Tanpa lock-in, export/import sepele, aman dari korupsi DB. (−) Query lintas projek lebih lambat; diterima karena jumlah projek kecil.

---

## ADR-010 · Distribusi: APK langsung lebih dulu, Play Store belakangan

**Status:** Diterima

**Keputusan:** Rilis M3–M6 lewat APK langsung + F-Droid. Play Store baru di M7 setelah stabil.

**Konsekuensi:** (+) Iterasi cepat, tanpa menunggu review. (−) Perlu edukasi pemasangan APK; UI harus memandu izin "instal aplikasi tak dikenal" dengan jelas.

---

## ADR-011 · ECJ dikunci pada level bahasa 8 dengan `-bootclasspath android.jar`

**Status:** Diterima (menggantikan asumsi "Java 11 lokal" di ADR-004)

**Konteks:** Percobaan nyata memakai `-source 11 -target 11 -classpath android.jar` gagal dua kali: `List cannot be resolved to a type` dan `The package java.util is accessible from more than one module`. ECJ menolak `-bootclasspath` pada compliance 9+, sementara `android.jar` di classpath bertabrakan dengan modul `java.base` dari JVM yang menjalankan ECJ.

**Keputusan:** Tier 0 selalu mengompilasi dengan `-source 8 -target 8 -bootclasspath android.jar`, dan JAR tambahan lewat `-classpath`. `javaVersion` di `kodenusa.json` dibatasi ke 8 atau 11. Nilai 11 diterima tetapi tetap dikompilasi pada level 8; fitur Java 9+ hanya tersedia di Tier 1/2.

**Konsekuensi:** (+) `java.util`/`java.lang` berasal dari satu sumber, seluruh template berhasil dirakit. (+) Tidak butuh JDK di HP, hanya `libcore` + `ecj.jar`. (−) `var`, `switch` expression, dan record tidak bisa dipakai di Tier 0; ini harus dikomunikasikan di UI, bukan muncul sebagai error misterius.

---

## ADR-012 · d8 menerima arsip, sehingga `JarPacker` dijalankan di dalam proses

**Status:** Diterima

**Konteks:** d8 menolak folder `.class` (`Unsupported source file type`) dan menolak output berupa direktori tanpa arsip input yang valid (`Invalid output: .../dex`).

**Keputusan:** Sebelum D8, engine membungkus `classes/` menjadi `classes.jar` memakai `JarPacker` (zip murni Kotlin). Tidak memanggil binary `jar` karena binary itu tidak tersedia di Android.

**Konsekuensi:** (+) Satu langkah tambahan < 100 ms untuk projek kecil, tanpa dependensi eksternal. (−) `classes.jar` ikut memakan ruang kerja; dibersihkan saat `clean`.

---

## ADR-013 · Editor sendiri di atas `EditText`, bukan Sora Editor

**Status:** Diterima (menggantikan rencana awal Sora Editor)

**Konteks:** Sora Editor 0.23.6 tersedia di Maven Central, tetapi menambah ±1,5 MB dan lapisan API besar untuk kebutuhan yang sempit: satu berkas, pewarnaan baris, tanpa autocomplete di M1.

**Keputusan:** `:core:editor` berisi `Highlighter` (murni Kotlin, diuji di JVM) dan `CodeEditorView` (`EditText` + gutter, pewarnaan hanya pada baris yang terlihat).

**Konsekuensi:** (+) Keystroke ringan di HP 2 GB, ±1.200 baris kode inti, bisa diuji tanpa emulator. (−) Fitur editor lanjutan (folding, multi-kursor, bracket matching) harus ditulis sendiri; evaluasi ulang Sora di M4 bila dibutuhkan.

---

## ADR-014 · Verifikasi dibuktikan lewat CLI host sebelum aplikasi Android

**Status:** Diterima

**Konteks:** Build di HP sulit di-debug saat toolchain belum terbundel. Tanpa bukti, kegagalan pipeline bisa disalahartikan sebagai masalah Android.

**Keputusan:** `:build:hostcli` menjalankan `Tier0BuildEngine` yang sama di JVM host. `scripts/prove/build-hello.sh` dan `scripts/prove/build-templates.sh` menjadi regression gate: setiap APK wajib lolos `apksigner verify`.

**Konsekuensi:** (+) Pipeline tervalidasi tanpa perangkat, CI bisa menjalankannya. (−) Perbedaan perilaku proses di Android (izin file, `exec`, page size) tetap harus diuji terpisah di perangkat.

---

## ADR-015 · Tier 0 dipin ke `android.jar` API 34 dan aapt2 2.19 bionic

**Status:** Diterima

**Konteks:** Saat menguji build di perangkat, `aapt2 link` menolak `android.jar` API 35 dengan `error: illegal map type 'string' (22)`. Biner aapt2 yang dipakai adalah 2.19-6696031, satu-satunya biner bionic yang stabil untuk tiga ABI.

**Keputusan:** `DEFAULT_TARGET_SDK = 34`, semua template memakai `"targetSdk": 34`, dan `scripts/toolchain/build-android-toolchain.sh` menyalin `platforms/android-34/android.jar`. `aapt2` versi baru tidak dikejar sampai ada biner bionic resmi untuk arsitektur HP target.

**Konsekuensi:** (+) Build perangkat berjalan di API 24 tanpa kejutan. (−) Aplikasi belum bisa menargetkan SDK 35 dari Tier 0; pengguna yang butuh targetSdk 35 memakai Tier 1/2.

---

## ADR-016 · ECJ 3.25, bukan versi terbaru, untuk perangkat

**Status:** Diterima

**Konteks:** ECJ 3.46 memanggil `java.io.File.toPath()` dan `java.nio.file.*` saat membaca bootclasspath. Keduanya baru ada di API 26, sehingga di Android 7 proses dibunuh ART (`exit 137`) dengan pesan yang tidak informatif. Android juga tidak menyediakan `javax.lang.model`, dan ECJ memuat bundle pesan lewat `ResourceBundle` sehingga file `.properties` wajib ikut di dalam jar.

**Keputusan:** Bundel memakai **ECJ 3.25.0**, ditambah stub `javax.lang.model.SourceVersion` (`scripts/toolchain/stub/`) yang ditanam ke `ecj.jar`, `-proc:none` (Android tidak punya `javax.annotation.processing`), dan repack yang **menyertakan seluruh resource non-`.class`**.

**Konsekuensi:** (+) Kompilasi Java 8 berjalan di API 24. (−) Fitur ECJ terbaru (Java 21, preview) tidak tersedia offline; itu tugas Tier 1/2.

---

## ADR-017 · Dexer adalah dx yang paketnya direlokasi, bukan d8

**Status:** Diterima (menggantikan ADR-012 sejauh menyangkut alat dex yang dijalankan di perangkat)

**Konteks:** Semua rilis d8/R8 yang tersedia (1.5 sampai 8.6) memanggil `java.nio.file.Paths`, yang baru ada di API 26, sehingga tidak bisa dipakai membangun APK di Android 7. dx 1.16 tidak memakai NIO, tetapi ART menaruh `com.android.dex` di bootclasspath; kelas dx kalah prioritas dan dx gagal dengan `NoSuchFieldError: callSiteIds`.

**Keputusan:** `scripts/toolchain/build-android-toolchain.sh` merelokasi dx dengan jarjar (`com.android.dx` → `id.kodenusa.dxtool`, `com.android.dex` → `id.kodenusa.dxlib`), lalu men-dex hasilnya. `Tier0Planner.DX_MAIN` menunjuk ke `id.kodenusa.dxtool.command.Main`; CLI host memakai jar relokasi yang sama sehingga host dan perangkat tidak pernah berbeda alat.

**Konsekuensi:** (+) APK bisa dibangun di API 24, bundel menyusut 5,7 MB (d8 6,1 MB → dx 379 KB). (−) dx tidak melakukan desugaring: lambda dan method reference ditolak, dan `ErrorCatalog` menerjemahkannya menjadi `dex.lambda` dengan saran anonymous class atau build cloud.

---

## ADR-018 · apksigner 0.8 (build-tools 28) untuk menandatangani di perangkat

**Status:** Diterima

**Konteks:** apksigner 0.9+ memanggil `File.toPath()` di `ApkSignerTool.sign`, sehingga di API 24 proses dibunuh ART. apksigner 0.8 berjalan, tetapi belum mengenal `--v3-signing-enabled` dan menebak minSdk dari manifest sehingga gagal pada APK tanpa manifest.

**Keputusan:** Bundel memakai `build-tools/28.0.3/lib/apksigner.jar`, argumen pemanggilan memakai `--v2-signing-enabled true`, `--v1-signing-enabled false`, dan `--min-sdk-version <minSdk>` eksplisit (sekaligus menghapus `--v3-signing-enabled`). Keystore dibuat dengan `keytool -J-Dkeystore.pkcs12.legacy` plus algoritma SHA1/3DES eksplisit, karena PKCS12 modern ditolak BouncyCastle di Android (`PKCS12 key store mac invalid`).

**Konsekuensi:** (+) APK bertanda tangan v2 sah dan bisa dipasang di Android 7+. (−) Tidak ada skema v3 (rotasi kunci) dari Tier 0; APK tetap valid karena minSdk 24 tidak mewajibkannya.

---

## ADR-019 · Keystore dibuat di perangkat, bukan dibundel di APK

**Status:** Diterima (menggantikan rencana "bundel `debug.keystore`")

**Konteks:** Rencana awal membundel satu `debug.keystore` berisi kunci `androiddebugkey` dengan kata sandi `android`. Kunci debug memang publik di dunia Android, tetapi membundelnya berarti **setiap pengguna KodeNusa menandatangani aplikasi mereka dengan kunci yang sama**. Siapa pun yang mengekstrak APK KodeNusa bisa menerbitkan pembaruan palsu untuk aplikasi buatan pengguna lain, dan dua aplikasi berbeda dari dua pengguna berbeda tidak bisa dipasang bersama.

**Keputusan:** `DebugKeystore` (di `:build:tier0`) membuat pasangan kunci RSA 2048 + sertifikat X.509 v3 yang menandatangani dirinya sendiri langsung di perangkat, disimpan sebagai PKCS12 di `filesDir/keys/debug.keystore`, sekali per pemasangan. Sertifikat disusun sebagai DER manual (`Der`: SEQUENCE/SET/INTEGER/OID/BIT STRING/UTCTime) karena Android tidak punya `sun.security.x509`, lalu diverifikasi ulang lewat `CertificateFactory` sebelum ditulis. CLI host memakai kode yang sama.

**Konsekuensi:** (+) Tidak ada kunci bersama yang bocor lewat APK; tiap pemasangan punya identitas sendiri; aset APK menyusut satu berkas. (+) Tinggal satu langkah menuju "kunci milik pengguna" (pengguna bisa memasok keystore sendiri). (−) APK yang dibangun sebelum uninstall tidak bisa diperbarui setelah aplikasi dihapus dan dipasang ulang, karena kuncinya ikut hilang — perilaku ini disengaja dan harus dijelaskan di UI saat rilis.

**Yang harus diingat saat mengubah kode:** `KeyPair.getPublic().encoded` **sudah** berupa `SubjectPublicKeyInfo` (SEQUENCE). Membungkusnya lagi dengan BIT STRING menghasilkan sertifikat yang ditolak JDK ("corrupt subject key") dan OpenSSL. Diuji oleh `DebugKeystoreTest` dan `OnDeviceBuildTest.keystore_dibuat_perangkat_bukan_dari_aset`.

---

## ADR-020 · Template web dibungkus WebView, aset dipaket pipeline Tier 0

**Status:** Diterima

**Konteks:** Pengguna bertanya mengapa KodeNusa hanya bisa Java. Padahal `ApkPackager` sejak awal sudah memaket folder `src/main/assets` projek ke dalam APK, dan `ProjectLoader` sudah mengumpulkan `src/main/assets` sebagai `assetDirs`. Yang belum ada hanyalah jalur "halaman web → APK": sebuah template yang menaruh HTML/CSS/JS di aset dan activity yang menampilkannya.

**Keputusan:** Template `web-katalog` memakai `WebView` (`file:///android_asset/index.html`, `setJavaScriptEnabled`, `setDomStorageEnabled`, `LOAD_NO_CACHE`) di dalam `MainActivity` berpaket `app`, dengan manifest `android:name=".app.MainActivity"` mengikuti konvensi template lain. Pipeline **tidak diubah**: tidak ada langkah baru, tidak ada flag aapt2 baru. Semua berkas web hidup di `src/main/assets` projek sehingga otomatis ikut lewat `copyAssets`.

**Konsekuensi:** (+) HTML/CSS/JS sekarang juga menghasilkan APK asli, offline, tanpa Gradle; pipeline tetap satu jalur sehingga template web diuji oleh `build-templates.sh` yang sama. (+) Aset yang sudah dipakai template lain tidak berubah perilakunya. (−) Halaman yang butuh server/Node tidak bisa dibangun di HP; untuk itu tetap Tier 1/2. (−) Python dan bahasa yang butuh interpreter **tidak** didukung Tier 0 dan tidak boleh dijanjikan di UI.
