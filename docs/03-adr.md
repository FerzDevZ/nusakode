# 03 — Architecture Decision Records

Format ringkas. Setiap ADR: konteks → keputusan → konsekuensi → alternatif yang ditolak.

**31 keputusan.** Dibaca berurutan dari atas ke bawah; nomor besar berarti keputusan yang lebih baru dan biasanya menimpa asumsi yang lebih lama. Untuk cepat: tabel di bawah menunjuk nomor yang paling sering dicari.

| Kalau Anda bertanya… | Baca |
|---|---|
| Mengapa tidak memakai Gradle? | ADR-005 |
| Mengapa APK-nya 32 MB? | ADR-028 |
| Mengapa Kotlin tidak didukung? | ADR-003 |
| Mengapa tidak ada izin penyimpanan? | ADR-022 |
| Mengapa kunci AI/token GitHub aman? | ADR-026 |
| Mengapa build di server (Tier 1) belum ada? | ADR-001, ADR-007 |
| Mengapa alat toolchain dijalankan begini? | ADR-006, ADR-021 |
| Mengapa ECJ 3.25 dan dx, bukan javac/d8? | ADR-016, ADR-017 |
| Mengapa JGit 3.7.1? | ADR-024 |
| Mengapa unggah APK lewat GitHub, bukan server sendiri? | ADR-029 |
| Mengapa palet perintah, bukan tombol tambahan? | ADR-031 |

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

---

## ADR-021 — Biner dan jar toolchain di Android 10+: `nativeLibraryDir` dan jar read-only

**Tanggal:** 2025-11-20 · **Status:** diterima · **Konteks aturan:** keamanan platform Android 10–14 (W^X dan larangan dex writable)

**Konteks:** Kebijakan W^X sudah tertulis di daftar risiko, tetapi belum pernah diuji: semua bukti build sebelumnya berasal dari emulator API 24. Uji langsung di emulator **API 34** (`ExecPolicyDeviceTest`) menunjukkan dua kegagalan nyata, bukan teoretis:

1. `aapt2` dari `filesDir` maupun `cacheDir` ditolak OS: `IOException: Cannot run program … error=13, Permission denied`. SELinux melabeli folder data aplikasi sebagai tidak boleh dieksekusi, jadi menyalin biner dan `chmod +x` tidak akan pernah cukup di Android 10+.
2. Setelah `aapt2` dipindah, jar masih gagal: ART menolak dex dari berkas yang bisa ditulis — `SecurityException: Writable dex file '/data/user/0/id.kodenusa.app/files/tool/apk.jar' is not allowed`. Jadi "mengeksekusi app_process" bukan satu-satunya syarat; keberadaan berkas jar itu sendiri ikut diperiksa.
3. `app_process` juga berubah bentuk argumen sejak API 28: bentuk lama `app_process /system/bin <kelas> <argumen>` dibaca sebagai `--nice-name`, sehingga proses mencetak `dalvikvm: [options] class [argument ...]` lalu keluar kode 1.

**Keputusan:**
- `aapt2` **tidak** lagi berada di `assets`. Binernya dikemas sebagai `jniLibs/<abi>/libaapt2.so` dengan `packaging.jniLibs.useLegacyPackaging = true`, sehingga Android mengekstraknya ke `applicationInfo.nativeLibraryDir` yang boleh dieksekusi di semua versi. `ToolchainInstaller` mengambil biner dari sana, bukan dari `filesDir`.
- Jar toolchain (`ecj.jar`, `dx.jar`, `apksigner.jar`, `android.jar`) tetap diekstrak ke `filesDir/toolchain/<abi>` tetapi **wajib ditandai `setReadOnly()`** setelah SHA-256 diverifikasi. Tanpa ini ART 10+ menolak memuat dex.
- `AppProcessLauncher` memakai bentuk argumen sesuai SDK: `>= 28` mengirim `-Djava.class.path=<jar> /system/bin --nice-name=kodenusa-tool <kelas> <argumen>`; `< 28` tetap bentuk lama. `CLASSPATH` tetap dikirim lewat environment untuk Android 7.

**Konsekuensi:** (+) Tier 0 kini terbukti berjalan di **API 24 dan API 34** — 22 uji perangkat hijau di keduanya, termasuk build tiga template. (+) Tidak ada perubahan pada pipeline 9 langkah; yang berubah hanya cara toolchain disiapkan. (+) `aapt2` tidak perlu diunduh saat pemasangan; Android sudah mengekstraknya. (−) `libaapt2.so` per ABI menambah ukuran APK dan wajib disertakan untuk setiap ABI yang didukung; kalau lupa, `ToolchainInstaller` mengembalikan `Missing` dan UI memberi tahu. (−) Jar read-only berarti setiap pembaruan toolchain harus menghapus folder lama lebih dulu — sudah dilakukan di `install()`.

---

## ADR-022 — Operasi berkas di dalam projek, dan "pilih lokasi" yang jujur

**Tanggal:** 2025-11-20 · **Status:** diterima

**Konteks:** Pengguna melaporkan hal yang paling mendasar: projek bisa dibuat, tetapi tidak ada cara menambah berkas, mengganti nama, menghapus, atau memilih tempat penyimpanan. Panel berkas hanya bisa dibuka-tutup folder dan membuka berkas yang sudah ada. Selain itu `ProjectTree.TEXT_EXTENSIONS` hanya memuat ekstensi Java/XML/umum, sehingga berkas HTML/CSS/JS dari template web tidak pernah muncul di panel meski ada di disk.

**Keputusan:**
- Semua operasi berkas masuk ke **`:core:project/ProjectFiles`** sebagai fungsi murni yang bisa diuji tanpa Android: `createFile`, `createFolder`, `rename`, `delete`, `safeRelative`. Nama divalidasi (bukan dibersihkan diam-diam), nama kembar ditolak, dan setiap path dihitung ulang lewat `canonicalPath` sebelum dipakai.
- Panel berkas mendapat tombol **+ Berkas** dan **+ Folder** yang selalu terlihat, serta menu `⋯` per baris untuk **Ganti nama** dan **Hapus**. Dialog galat ditampilkan di dalam dialog yang sama supaya ketikan pengguna tidak hilang.
- Berkas `.java` baru langsung dibuat dengan `package` dan `class` yang sesuai lokasinya, sehingga hasilnya bisa di-build tanpa diedit dulu.
- `TEXT_EXTENSIONS` diperluas (html/css/js/ts/py/sql/yml/sh/…).
- **"Pilih lokasi" diwujudkan sebagai ekspor/impor `.zip` lewat pemilih berkas Android** (`CreateDocument`/`OpenDocument`), bukan folder langsung. Alasannya teknis, bukan kemalasan: Android 11+ melarang aplikasi menulis ke folder sembarang tanpa izin pemilih, dan build Tier 0 butuh folder yang boleh mengeksekusi biner (`nativeLibraryDir`). Menjanjikan "pilih folder mana saja" akan menghasilkan build yang gagal di HP modern.
- Pengimpor zip memperlakukan isi zip sebagai **masukan tidak tepercaya**: entri yang keluar folder (`../`, path absolut) ditolak dan seluruh ekstraksi dibatalkan.

**Konsekuensi:** (+) Pengguna bisa mengelola berkas sendiri, dan projek bisa dipindah ke Unduhan/Drive/kartu SD sebagai satu berkas. (+) Semua aturan keamanan path teruji tanpa perangkat (`ProjectFilesTest` 12 uji, `ProjectArchiveTest` 6 uji). (+) Berkas web sekarang terlihat dan bisa diedit. (−) Menyunting projek langsung di folder luar (mis. lewat aplikasi lain) belum bisa; itu menunggu Tier 2 dengan izin SAF penuh.

---

## ADR-023 — AI dengan endpoint + kunci milik pengguna (BYOK), model dibaca otomatis

**Tanggal:** 2025-11-20 · **Status:** diterima

**Konteks:** Pengguna ingin asisten AI di dalam IDE. Tiga pilihan ada: (a) awan dengan kunci milik pengguna, (b) model kecil di perangkat lewat llama.cpp (400–900 MB, 2–8 token/detik di HP 4 GB), (c) hibrida. Pengguna memilih **a**, dengan syarat eksplisit: *"orang input endpoint dan key-nya sendiri, terus apps kita otomatis baca model yang tersedia dan kita bebas memilih model dan mengganti model"*.

**Keputusan:**
- Modul baru `:core:ai` berisi klien **OpenAI-compatible** murni Kotlin/JVM: `GET {endpoint}/models` untuk membaca daftar model, `POST {endpoint}/chat/completions` untuk bertanya. Tanpa library HTTP pihak ketiga (`HttpURLConnection`), supaya APK tidak bertambah berat dan uji bisa jalan di JVM.
- **Tidak ada kunci bawaan, tidak ada server perantara.** Tanpa setelan, tidak ada satu byte pun keluar dari HP. Izin `INTERNET` ditambahkan ke manifes, tetapi jalur jaringan hanya aktif setelah pengguna mengisi endpoint + kunci.
- Daftar model **dibaca dari endpoint** (bukan ditulis mati di kode), disimpan sebagai cache supaya layar tidak kosong saat offline, dan pengguna bisa **mengganti model** kapan saja.
- Jawaban dialirkan potongan demi potongan (SSE) dengan pemisahan tegas: `delta.content` = jawaban, `delta.reasoning` = proses berpikir. Reasoning **tidak** dimasukkan ke kode, hanya dipakai sebagai cadangan bila `content` kosong. Ini penting karena endpoint uji mengirim `reasoning` lebih dulu.
- Galat dipetakan ke bahasa Indonesia dengan langkah perbaikan: 401/403 kunci salah, 404 alamat salah, 429 kuota, 5xx penyedia (dicoba ulang sekali), jaringan mati, balasan aneh.
- Prompt sistem mengunci batas teknis projek: **Java 8, minSdk 24, tanpa Gradle, tanpa AndroidX, tanpa Kotlin** — supaya model tidak menyarankan hal yang mustahil di-build di HP.
- Jawaban dengan blok kode bisa dimasukkan ke berkas aktif sekali tekan ("Masukkan kode ke berkas").
- Uji: 17 uji unit dengan **server HTTP sungguhan di localhost** (bukan mock), plus `ChatNyataTest` yang memanggil endpoint asli dan otomatis dilewati bila variabel lingkungan tidak diisi.

**Konsekuensi:** (+) Fitur AI berguna tanpa biaya server dan tanpa membesarkan APK berarti (+0,0–0,1 MB). (+) Privasi tetap jujur: AI opsional, semua fitur lain 100% offline. (+) Mesin bisa ditukar: kalau nanti model di perangkat dipakai, ia tinggal mengimplementasi antarmuka yang sama. (−) Pengguna harus punya endpoint + kunci sendiri; ini dinyatakan terus terang di layar AI, bukan disembunyikan. (−) Kunci disimpan di `SharedPreferences` privat aplikasi (tidak bisa dibaca aplikasi lain tanpa root); enkripsi dengan Android Keystore bisa ditambahkan kemudian.

---

## ADR-024 — Git sungguhan di dalam HP: JGit 3.7.1 (bukan 5.x), Apache HttpClient dibuang

**Konteks:** Pengguna meminta GitHub tersambung seperti repo: commit, push, unduh projek. Dua pilihan: GitHub REST API, atau git sungguhan di perangkat.

**Keputusan:**
- **JGit** (git sungguhan: commit offline, status, riwayat, branch, push/pull), bukan hanya REST. Alasan: janji "offline dulu" tidak boleh dilanggar — commit harus bisa dilakukan tanpa sinyal. REST tetap dipakai nanti hanya untuk dua hal yang JGit tak bisa lakukan: membuat repo di GitHub dan mengunggah APK ke Releases.
- Versi **3.7.1.201504261725-r**, bukan 5.x. Bukti dari pengukuran isi jar: **5.13 memakai `java.nio.file.Paths`, 4.11 memakai `File.toPath()`, sedangkan 3.7.1 nol referensi `java.nio.file`**. Percobaan nyata di emulator **API 24** membuktikan 5.13 gagal `NoClassDefFoundError: Ljava/nio/file/Paths;` dan 4.11 gagal `NoSuchMethodError: File.toPath()`. `java.nio.file` baru ada di API 26 dan tidak bisa di-desugar, jadi versi 3.7.1 satu-satunya yang jujur untuk `minSdk 24`.
- **`org.apache.httpcomponents` dikeluarkan** dari dependensi: HttpClient bawaan JGit bertabrakan dengan `org.apache.http` milik Android (dan hilang sejak API 28). Tanpa itu, JGit memakai `HttpURLConnection` platform. Tanpa pengecualian ini build gagal: `2 files found with path 'META-INF/DEPENDENCIES'`.
- JGit 3.x belum `Closeable`, jadi `RepoGit` menutup repo lewat helper `denganGit` + `try/finally`.
- **Token tidak pernah ditulis ke `.git/config`**; ia hanya dipakai sebagai kata sandi HTTPS saat push (`UsernamePasswordCredentialsProvider("x-access-token", token)`). Diuji: `token_tidak_pernah_ditulis_ke_konfigurasi_git`.
- Remote hanya menerima `https://`, `file://`, atau jalur lokal; alamat SSH (`git@…`) ditolak dengan kalimat jelas.
- `.gitignore` bawaan menutup `build/`, `dist/`, `gen/`, `*.apk`, `*.jks` — hasil build tidak boleh masuk repo (diuji).
- Nama cabang diganti ke `main` **setelah commit pertama**, karena JGit menolak rename pada repo tanpa commit.
- `klon` memeriksa hasil akhir: kalau repo terunduh tetapi tidak ada berkas yang keluar, ia mengembalikan galat yang menjelaskan, bukan folder kosong tanpa sebab.

**Bukti:** 8 uji JVM (`RepoGitTest`, termasuk push ke remote bare lokal lalu klon ulang), 2 uji **di HP** (API 24: `git_bisa_commit_dan_push_di_dalam_hp`, `hasil_build_diabaikan_dan_riwayat_terbaca`). APK +1,0 MB (44,77 → 45,77 MB).

**Konsekuensi:** (+) Commit, riwayat, dan push bekerja penuh offline di Android 7 ke atas. (+) Tanpa library tambahan untuk HTTP. (−) JGit 3.7.1 (2015) lebih tua: belum ada dukungan protokol git terbaru (mis. partial clone) dan kita harus menutup repo manual. (−) Tombol "buat repo di GitHub" dan "unggah APK ke Releases" tetap butuh REST — akan ditambahkan sebagai langkah terpisah.

---

## ADR-025 — Situs murni (tanpa APK, tanpa WebView): satu projek dua keluaran

**Konteks:** Tidak semua orang butuh APK. Sebagian hanya ingin **website** yang bisa dibuka siapa pun lewat tautan, atau ingin memasangnya sebagai PWA tanpa Play Store. Memaksa semuanya jadi APK membuat kita menyelesaikan masalah yang tidak mereka punya.

**Keputusan:**
- Jenis projek baru **`web-murni`** (`web/index.html` + `web/gaya.css` + `web/aplikasi.js`, tanpa `AndroidManifest.xml`). Template `situs-murni` tersedia di wizard.
- `:core:build` mendapat **`SitusStatis`**: menyusun folder situs ke `build/situs`. Sumber diambil dari `web/` bila ada, kalau tidak dari `src/main/assets/` — jadi satu projek bisa punya **dua keluaran** (situs dan APK) tanpa menduplikasi berkas.
- Aturan yang ditegakkan dengan galat berbahasa Indonesia: **`index.html` wajib di akar situs** (bukan subfolder, karena itu merusak alamat), folder hasil tidak boleh berada di dalam sumber, dan sisa build lama dibersihkan supaya berkas usang tidak ikut terbit.
- Folder yang tidak pernah ikut terbit: `.git`, `build`, `dist`, `gen`, `node_modules`, `.gradle`, `.idea`, `.tools`, `dex`; berkas dengan ekstensi tak dikenal (mis. `.exe`, `.psd`) dilewati dan **dilaporkan**, tidak disembunyikan.
- **Terbit gratis lewat GitHub Pages** memakai mesin git yang sudah ada (ADR-024): `RepoGit.terbitkanSitus` mengirim folder situs ke cabang `gh-pages` dari repo sementara (bukan dari repo projek — supaya kode Java tidak ikut tampil di tautan publik), lalu `RepoGit.halamanGitHub` menghitung tautan `https://<pemilik>.github.io/<repo>/` (dan bentuk khusus `<pemilik>.github.io`).
- Karena keluaran situs hanyalah berkas statis, jalur lain tetap terbuka tanpa kode tambahan: unggah zip ke Netlify Drop / Cloudflare Pages, atau bagikan lewat server lokal di HP.

**Bukti:** 8 uji `SitusStatisTest` (termasuk `node_modules`/build diabaikan, index di subfolder ditolak, sisa build dibersihkan), 3 uji `PagesTest` (terbit ke `gh-pages` lalu diklon ulang isinya sama; tautan GitHub Pages dihitung benar), dan 3 uji **di HP** (`SitusDeviceTest`: template situs murni tersusun tanpa APK, template `web-katalog` juga bisa terbit, projek aplikasi biasa ditolak dengan pesan jelas).

**Konsekuensi:** (+) Pengguna web tidak lagi dipaksa membuat APK. (+) Publikasi gratis tanpa server KodeNusa, tanpa biaya. (+) Tidak ada berkas biner baru: situs hanyalah salinan aset projek. (−) GitHub Pages hanya `https`, tidak bisa untuk situs dengan backend/database — untuk itu jalur lain (VPS/hosting pengguna) tetap di luar lingkup. (−) Layar "Jadikan situs" + tombol "Terbitkan" belum ada; mesin dan ujinya sudah siap.

---

## ADR-026 — Kunci AI dan token GitHub disandikan dengan Android Keystore

**Keputusan:** rahasia pengguna disimpan **tersandi** (AES-256-GCM) dengan kunci yang dibuat di Android Keystore dan tidak bisa diekspor. Nilai lama yang masih teks biasa dibaca sekali lalu ditulis ulang dalam bentuk tersandi (migrasi otomatis).

**Konteks:** sampai 0.9.2, `AiSettingsStore` dan `GitSettingsStore` menulis kunci API serta token GitHub sebagai teks biasa ke `SharedPreferences`. Berkas itu tidak bisa dibaca aplikasi lain tanpa root, tetapi ikut tersalin oleh aplikasi backup, `adb backup`, atau siapa pun yang memegang HP dalam keadaan tidak terkunci. Kunci AI dan token GitHub adalah kredensial berbayar dan punya akses tulis ke repo — kebocorannya berdampak nyata, bukan teoretis.

**Bentuk:** `Penyandi` (antarmuka) dengan dua implementasi: `KunciAman` (Keystore; format `enc1:<iv>:<ciphertext>` base64) dan `PenyandiPolos` (hanya untuk uji). IV acak 12 bita per penyandian, karena IV berulang pada GCM membocorkan isi. Nilai yang rusak atau tidak bisa didekripsi menghasilkan string kosong — aplikasi meminta pengguna mengisi ulang, tidak menampilkan layar galat.

- Alamat repo GitHub **tidak** disandikan: bukan rahasia, dan harus bisa ditampilkan di layar Terbit.
- `setUserAuthenticationRequired(false)`: memaksa sidik jari akan menghentikan build otomatis serta agen yang berjalan.
- Format menyimpan versi (`enc1`) supaya format berikutnya bisa migrasi tanpa menebak.

**Bukti:** 7 uji di perangkat `KunciAmanDeviceTest` — bolak-balik, IV berbeda tiap penyandian, isi `shared_prefs` tidak memuat teks asli (diperiksa berkasnya, bukan hanya API), token GitHub tersandi sementara alamat repo tetap terbaca, migrasi nilai lama, nilai rusak tidak membuat aplikasi berhenti, dan setelah kunci Keystore dihapus nilai tersandi menjadi kosong.

**Konsekuensi:** (+) Menyalin berkas preferensi tidak lagi cukup untuk mencuri kunci. (+) Tidak ada perubahan pada alur AI dan git. (−) Kunci tidak bisa dipulihkan bila HP di-reset — memang disengaja; pengguna memasukkan ulang. (−) `KunciAman.lupakan()` membuat nilai lama tak terbaca, jadi hanya dipakai saat pengguna menghapus semua setelan.

---

## ADR-027 — Situs murni bisa dipasang di HP sebagai PWA (tetap nol berkas Android)

**Keputusan:** projek `web-murni` mendapat tombol **"Siapkan mode offline"** yang membuat `manifest.json`, `service-worker.js`, dan satu baris `<script>` pendaftar di `index.html`.

**Konteks:** target pengguna sering kehilangan sinyal. Situs statis tanpa service worker tampil sebagai halaman galat browser begitu internet mati — padahal seluruh isinya sudah ada di HP. PWA juga membuat situs bisa "Tambahkan ke layar utama" seperti aplikasi, tanpa Play Store.

**Aturan:**

- Berkas ditulis ke **folder sumber situs** (`web/` bila ada), bukan ke akar projek — kalau tidak, pendaftar tidak akan pernah jalan. Ditentukan lewat `SitusStatis.sumber`.
- **Tidak pernah menimpa** `manifest.json` atau `service-worker.js` milik pengguna; keduanya dilaporkan sebagai "dibiarkan".
- Pendaftar hanya disisipkan **sekali**; `index.html` tanpa `</body>` tetap ditangani.
- Aset di atas 2 MB tidak diprecache (kuota dan RAM HP 2 GB); `.git`, `build`, `node_modules` tidak ikut.
- Ikon hanya didaftarkan bila berkasnya ada; bila belum ada, PWA tetap jalan offline tetapi diberi catatan bahwa belum bisa dipasang.
- Tetap **tanpa** `src/` dan tanpa `kodenusa.json` baru — ADR-025 tidak dilanggar.

**Bukti:** 9 uji `SitusPwaTest` (manifest di folder situs, hanya aset web yang diprecache, berkas pengguna tidak ditimpa, pendaftar sekali, ikon bersyarat, HTML tanpa `body`, folder build/git tidak ikut, berkas 3 MB dilewati) dan 1 uji di HP (`SitusUiTest.siapkan_mode_offline_…` memeriksa `web/manifest.json`, `web/service-worker.js`, dan `serviceWorker.register` benar-benar ada di disk).

**Konsekuensi:** (+) Situs tetap terbuka tanpa internet dan bisa dipasang. (+) Tidak ada biner baru. (−) Service worker hanya aktif di `https` atau `localhost`; saat berkas dibuka lewat `file://` di browser HP, mode offline tidak jalan — batasan browser, bukan bug, dan disebut di layar.

---

## ADR-028 — Ukuran APK 32 MB dipertahankan (hasil analisis, bukan kelalaian)

**Keputusan:** tetap membawa `android.jar` (API 34) di dalam APK. Tidak ada pemangkasan kelas.

**Alasan angka:** dari 32,3 MB APK rilis, **24,1 MB adalah `assets/toolchain/shared/android.jar`** (26,4 MB mentah). Berkas itu sudah terkompresi maksimal oleh aapt2; dibuat ulang dengan deflate -9 hanya menghemat 45 KB. Sisanya: 2 × `libaapt2.so` (3,8 MB terkompresi), `classes.dex` 1,8 MB, `ecj.jar` 1,5 MB, font 0,3 MB.

**Mengapa tidak dipangkas:** `android.jar` adalah classpath kompilasi. Kelas yang dibuang muncul sebagai "cannot find symbol" pada kode pengguna yang sah (Bluetooth, kamera, cetak). Memangkasnya berarti mengorbankan janji "kompilasi di HP untuk kode Java apa pun" demi sekitar 10 MB. Alternatif "unduh toolchain saat build pertama" ditolak karena melanggar janji offline.

**Konsekuensi:** (+) Semua template dan kode pengguna tetap bisa dikompilasi tanpa internet. (−) APK 32,3 MB, di atas target awal 30 MB; angka ini ditulis terbuka di README, bukan disembunyikan.

---

## ADR-029 — Bagikan APK lewat GitHub Release, bukan server sendiri

**Keputusan:** APK hasil build di HP diunggah langsung ke rilis repo GitHub pengguna lewat REST API GitHub (`POST /user/repos`, `POST /releases`, `POST /releases/{id}/assets`).

**Mengapa:** sasaran aplikasi ini adalah orang tanpa PC. Selama ini hasil build berhenti di folder projek; menyalinnya butuh kabel atau kartu SD. Rilis GitHub memberi tautan `https://github.com/.../releases/download/...` yang bisa dibagikan lewat WhatsApp dan diunduh siapa pun tanpa akun. Server KodeNusa sendiri ditolak: ia berarti biaya bulanan, data pengguna berpindah ke pihak ketiga, dan janji "tanpa server" gugur.

**Bentuk teknis:** `GitHubRilis` di `:core:git`, hanya `HttpURLConnection` (tanpa OkHttp: 1 MB demi 100 baris tidak sepadan). Rilis memakai tag yang sama agar unggahan ulang tidak menumpuk rilis kosong. Nama aset di-encode (`?name=`) karena nama berkas Indonesia sering berspasi.

**Bukti:** `GitHubRilisTest` membuat repo sungguhan, mengunggah berkas, memeriksa tautan aset, lalu menghapus repo — dijalankan dengan `GH_TOKEN` dan lulus. Tanpa `GH_TOKEN` uji ini dilewati, bukan dipalsukan.

**Konsekuensi:** (+) APK bisa dibagikan tanpa PC maupun server. (−) Butuh kuota (32 MB per unggahan) dan token berizin `repo`; keduanya disebut di layar sebelum tombol ditekan.

---

## ADR-030 — Pratinjau situs memakai WebView `file://`, bukan server lokal

**Keputusan:** pratinjau situs dimuat dengan `WebView` langsung dari `file://` folder projek (`SitusStatis.sumber()`), dengan `allowFileAccess`, `allowContentAccess`, dan JavaScript aktif.

**Mengapa bukan server lokal (`127.0.0.1:PORT`):** server berarti membuka soket, memilih port, dan mematikan layanan saat aplikasi ditutup — tiga sumber kegagalan di HP 2 GB demi satu halaman statis. `file://` cukup untuk CSS, JS, dan gambar yang satu folder.

**Batas yang diakui di layar:** service worker (mode offline PWA) tidak aktif di `file://`; itu aturan browser. Pesan di dialog menyebutkan hal ini supaya tidak dianggap bug.

**Konsekuensi:** (+) Sunting → lihat tanpa keluar aplikasi dan tanpa internet. (−) Halaman yang memanggil `fetch()` ke berkas lokal bisa gagal sesuai kebijakan browser; untuk itu jalur "Susun situs" tetap ada.

---

## ADR-031 — Palet perintah, bukan baris tombol tambahan

**Keputusan:** semua aksi editor (simpan, cari se-projek, berkas baru, impor, build/susun situs, pratinjau) bisa dicari dan dijalankan dari **palet perintah** yang dibuka lewat tombol **Aksi**.

**Mengapa:** layar 360dp hanya memuat satu tombol utama dan tiga tombol kecil. Setiap fitur baru selama v0.9.x menuntut satu tombol lagi — jalur itu berakhir di toolbar bergulir yang tidak bisa ditekan dengan jempol. Palet memindahkan biaya "ruang" menjadi biaya "ingat kata kunci", dan kata kunci itu bisa ditulis (build, apk, bangun, kompilasi) sehingga tidak perlu dihafal persis.

**Pencocokan:** setiap kata pada kueri harus muncul di judul/keterangan/alias (AND). Mengetik "simpan git" menghasilkan nol baris, bukan semua baris yang memuat "git" — hasil kosong lebih jujur daripada daftar panjang yang tidak relevan.

**Konsekuensi:** (+) Aksi baru tidak lagi berebut ruang dengan tombol Build APK. (+) Projek `web-murni` tidak pernah menawarkan "Build APK" di palet. (−) Aksi yang sangat sering (Simpan) tetap punya tombol sendiri; palet bukan pengganti tombol utama.


---

## ADR-032 — Laporan galat: offline, disaring rahasianya, dikirim hanya bila pengguna mau

**Keputusan:** penangkap galat tak tertangkap (`Thread.setDefaultUncaughtExceptionHandler` di `MainActivity`) menulis laporan ke penyimpanan internal (`filesDir/laporan/galat-terakhir.txt`, maksimum 60 baris jejak tumpukan), lalu **memanggil penangan lama** supaya proses mati seperti biasa. Laporan tampil sebagai kartu di layar projek dengan dua tombol: **Bagikan laporan** (menyalin ke `cacheDir` dan membuka lembar bagikan Android) dan **Hapus laporan**.

**Mengapa bukan layanan telemetri:** janji produk adalah tanpa server, tanpa akun, tanpa telemetri. Laporan yang tidak pernah keluar dari HP tidak bisa melanggar janji itu. Pengguna yang memang mau melapor bisa membagikannya sendiri ke mana pun.

**Penyaringan rahasia:** sebelum ditulis, isi laporan disaring: pola `ghp_…`, `github_pat_…`, `sk-…`, `AIza…`, `crown-…`, `x-access-token:…`, dan `token/kunci/api_key/password/sandi = nilai` diganti `<disembunyikan>`. Alasannya sederhana: jejak tumpukan bisa memuat URL push yang menyisipkan token.

**Konsekuensi:** (+) Kerusakan di HP pengguna jadi bisa didiagnosis tanpa melanggar privasi. (+) Uji: `LaporanGalatTest` (5 uji JVM: penyaringan, pemotongan jejak) + `LaporanKlonUiTest` (kartu muncul/hilang di perangkat). (−) Laporan hilang kalau pengguna menghapus data aplikasi; hanya satu laporan terakhir disimpan — pilihan sadar, bukan kekurangan.

### ADR-033 — Tombol sekunder harus berwujud; masukan utama dekat tombolnya
**Tanggal:** 2026-11-20 · **Status:** diterima (iterasi UX 0.9.9)

**Konteks.** Audit visual 0.9.8 (screencap + uiautomator + tinjauan model penglihatan) menemukan
pola yang sama di banyak layar: `TextButton` tanpa latar membuat aksi sekunder (AI/Cari/Simpan,
Situs/Git/Agen) tampak seperti teks biasa; kolom nama wizard tersembunyi di bawah lipatan sementara
bilah bawah menyuruh "Isi nama projek dulu"; dua kelompok pilihan wizard memakai gaya radio yang
sama sehingga dua hal tampak terpilih sekaligus; layar Git berjudul "Simpan perubahan" padahal
repo belum ada.

**Keputusan.**
1. `GhostButton` berubah menjadi kotak berlatar SURFACE_RAISED + garis BORDER_STRONG, tinggi 48dp,
   satu baris dengan elipsis. Tombol sekunder tetap jelas lebih tenang dari `PrimaryButton`.
2. Masukan yang mengaktifkan tombol utama ditempatkan di bilah bawah yang sama dengan tombol itu.
3. Kelompok pilihan diberi judul sendiri ("Jenis projek", "Pilih titik awal") dan status terpilih
   ditandai garis aksen ACCENT, bukan hanya titik radio.
4. Judul kartu mengikuti tugas layar saat itu (Git: "Siapkan repo dulu" sebelum repo ada).
5. Baris navigasi build dipecah: Kembali + status pill, lalu tiga tujuan nav sama lebar.

**Konsekuensi.** `Pill` dibatasi satu baris; pengujian wizard tidak lagi menggulir ke kolom nama
(kini selalu terlihat). Semua kontras tetap dijaga oleh `ContrastTest`.
