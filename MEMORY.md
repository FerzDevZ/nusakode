# MEMORY.md — Invariant Hasil Root-Cause

Dibaca di awal setiap sesi. Format dari `AGENTS.md` Gate 3:
`- **[YY-MM-DD · Komponen]**: gejala → **Root Cause**: penyebab teknis → **Invariant Rule**: aturan permanen.`

---

## Baseline arsitektur

- **[25-11-20 · Produk]**: IDE Android on-device gagal dipakai di HP Indonesia → **Root Cause**: AndroidIDE mensyaratkan 1.5–2 GB RAM bebas + 4 GB storage, sementara median device 2–4 GB RAM / 32–64 GB storage terisi → **Invariant Rule**: setiap fitur baru wajib diuji terhadap anggaran HP 2 GB / 32 GB; anggaran dinyatakan di `docs/01 §7` dan dilanggar hanya lewat ADR baru.

- **[25-11-20 · Build]**: Gradle tidak bisa dijadikan syarat build di target device → **Root Cause**: Gradle daemon + resolusi dependency butuh memori & storage di luar jangkauan target → **Invariant Rule**: Tier 0 tidak boleh menyentuh Gradle. `kodenusa.json` adalah satu-satunya manifest build Tier 0; kehadiran `build.gradle` memindahkan projek ke Tier 1/2, bukan memaksa Tier 0 mencoba.

- **[25-11-20 · Build]**: Toolchain on-device harus dijalankan dari app-private storage → **Root Cause**: Android 10+ membatasi eksekusi di `/sdcard`, dan Android 15+ mewajibkan binary selaras 16 KB → **Invariant Rule**: semua binary diekstrak ke `filesDir/toolchain/<arch>/` dengan permission `0755`, dibangun 16 KB-aligned, dan diverifikasi checksum SHA-256 sebelum eksekusi.

- **[25-11-20 · Keamanan]**: Input user mengalir ke argumen proses eksternal → **Root Cause**: `Runtime.exec(String)` dan `sh -c` menafsirkan metakarakter sehingga membuka command injection → **Invariant Rule**: hanya `ProcessBuilder(List<String>)`, tanpa shell, tanpa interpolasi string; setiap path diverifikasi `canonicalPath().startsWith(workspaceRoot)`.

- **[25-11-20 · Editor]**: Teks besar membuat UI jank → **Root Cause**: Compose `BasicTextField`/`Text` merender seluruh teks dan merekomposisi per karakter → **Invariant Rule**: editor wajib berbasis viewport (Sora Editor) dan state editor tidak boleh memicu rekomposisi per keystroke; anggaran ketik P95 < 16 ms diuji macrobenchmark.

- **[25-11-20 · Editor]**: Ketikan hilang saat aplikasi dibunuh sistem → **Root Cause**: penulisan file langsung ke target tanpa proteksi crash → **Invariant Rule**: simpan selalu tulis `.tmp` lalu `rename()` atomik, auto-save debounce 800 ms, dan pemulihan wajib kehilangan ≤ 1 detik ketikan.

- **[25-11-20 · Build]**: Build yang dibatalkan meninggalkan proses anak → **Root Cause**: cancellation pada `Flow` tidak otomatis mematikan proses OS → **Invariant Rule**: setiap `Process` didaftarkan pada `BuildSession` dan `destroyForcibly()` dipanggil di `finally` + `onCancellation`; ada test yang meng-assert tidak ada proses tersisa.

- **[25-11-20 · Produk]**: User mengharapkan Kotlin di Tier 0 lalu kecewa → **Root Cause**: `kotlinc` adalah program JVM sehingga menuntut JDK port ±60 MB + JRE, membatalkan anggaran 70 MB → **Invariant Rule**: kapabilitas Tier 0 (Java 11, tanpa ProGuard, tanpa kapt) dinyatakan di UI **sebelum** tombol Build ditekan, bukan lewat pesan gagal setelahnya.

- **[25-11-20 · Privasi]**: Kode user berisiko terkirim tanpa sepengetahuan → **Root Cause**: build cloud memerlukan upload source → **Invariant Rule**: tidak ada request jaringan yang membawa kode user tanpa dialog consent eksplisit per projek; default mati, dan consent dapat dicabut.

- **[26-11-20 · Tier 0/ECJ]**: `The package java.util is accessible from more than one module` dan `List cannot be resolved` → **Root Cause**: ECJ pada compliance 9+ menolak `-bootclasspath`, sehingga `android.jar` ditempatkan di `-classpath` dan bertabrakan dengan modul `java.base` milik JVM yang menjalankan ECJ → **Invariant Rule**: Tier 0 selalu `-source 8 -target 8 -bootclasspath android.jar`; JAR tambahan hanya lewat `-classpath`. Diuji oleh `Tier0PlannerTest.Android jar dipakai sebagai bootclasspath pada level bahasa 8`.

- **[26-11-20 · Tier 0/AAPT2]**: `resource string/app_name not found` saat `aapt2 link` → **Root Cause**: langkah link diberi folder `res/` mentah, padahal aapt2 membutuhkan arsip hasil `aapt2 compile`; selain itu `R.java` tidak ikut dikompilasi karena ECJ tidak tahu lokasi hasil generate → **Invariant Rule**: link selalu menerima `res-compiled.zip`, dan ECJ selalu mendapat `-sourcepath gen` plus `gen/<paket>/R.java` eksplisit.

- **[26-11-20 · Tier 0/D8]**: `Unsupported source file type` lalu `Invalid output: .../dex` → **Root Cause**: d8 tidak menerima folder `.class` maupun direktori output tanpa arsip input yang sah → **Invariant Rule**: sebelum D8 selalu ada `classes.jar` hasil `JarPacker` (zip dalam proses, tanpa binary `jar`), dan `--output` menunjuk direktori yang sudah dibuat.

- **[26-11-20 · Build/kecepatan]**: Template APK 12.801 byte dibangun 2 detik dari keadaan bersih → **Root Cause**: tidak ada Gradle, Kotlin daemon, atau konfigurasi proyek yang dibaca; delapan proses pendek dijalankan berurutan → **Invariant Rule**: jangan menambah langkah yang butuh JVM baru atau membaca seluruh graf proyek; setiap langkah baru wajib diukur lewat `scripts/prove/build-hello.sh`.

- **[26-11-20 · Tes/Highlighter]**: `komentar mengalahkan kata kunci` gagal karena mengharapkan nol kata kunci → **Root Cause**: tes menghitung semua token KEYWORD termasuk `int` yang memang berada di luar komentar; yang ingin dibuktikan hanya `public`/`class` di dalam komentar → **Invariant Rule**: asersi tes menyebut token spesifik, bukan jumlah seluruh jenis token.

- **[26-11-20 · Toolchain/repack]**: `app_process ECJ` selalu mati `exit 137` tanpa pesan, `-help` sempat jalan → **Root Cause**: RuntimeInit membunuh proses setelah `MissingResourceException` (bundle `messages.properties` dibuang saat repack jar) dan galat aslinya tidak pernah tercetak karena handler log ikut gagal ("Bad file descriptor"); jejaknya hanya ada di `logcat -b crash` → **Invariant Rule**: repack jar tool wajib mempertahankan seluruh resource non-`.class`; kalau sebuah proses ART mati `exit 137`, periksa `adb logcat -d -b crash`, bukan hanya stdout.

- **[26-11-20 · Toolchain/aset]**: Aset jar tool berubah menjadi jar tanpa `.class` setelah skrip dijalankan dua kali → **Root Cause**: `dex "$CACHE/ecj.jar" "$CACHE/ecj.jar"` memakai path yang sama untuk input dan output, sehingga jalankan kedua men-dex ulang hasil dex pertama → **Invariant Rule**: jar sumber (`sumber/`) dan hasil dex (`dex/`) wajib berada di folder berbeda; skrip tidak boleh menulis ke berkas yang sedang dibaca.

- **[26-11-20 · Tier 0/ECJ]**: `ClassNotFoundException: javax.lang.model.SourceVersion`, lalu `javax.annotation.processing.ProcessingEnvironment` → **Root Cause**: Android tidak punya paket `javax.lang.model` maupun `javax.annotation.processing`, sedangkan ECJ memakainya saat membaca bootclasspath dan menyiapkan annotation processing → **Invariant Rule**: stub `javax.lang.model.SourceVersion` ditanam di `ecj.jar` dan pemanggilan ECJ selalu memakai `-proc:none`; stub baru hanya ditambah kalau ada `ClassNotFoundException` nyata dari perangkat.

- **[26-11-20 · Tier 0/dexer]**: d8 1.5 sampai 8.6 semuanya mati `exit 137` di API 24 → **Root Cause**: semua rilis d8/R8 memanggil `java.nio.file.Paths`, baru ada di API 26 → **Invariant Rule**: dexer perangkat adalah dx 1.16 (lihat ADR-017), bukan d8; `minSdk` projek tidak boleh dijadikan alasan menaikkan d8 ke perangkat.

- **[26-11-20 · Tier 0/dexer]**: `NoSuchFieldError: callSiteIds in com.android.dex.TableOfContents` saat dx dijalankan → **Root Cause**: ART menaruh `com.android.dex` di bootclasspath sehingga kelas dx bundel tidak pernah dipakai → **Invariant Rule**: dx wajib direlokasi jarjar (`com.android.dx` → `id.kodenusa.dxtool`, `com.android.dex` → `id.kodenusa.dxlib`) dan `DX_MAIN` menunjuk ke kelas hasil relokasi.

- **[26-11-20 · Tanda tangan/keystore]**: sertifikat X.509 buatan sendiri ditolak `CertificateParsingException: corrupt subject key` (OpenSSL: tag BIT STRING di `TbsCertificate::spki`) → **Root Cause**: `KeyPair.getPublic().encoded` **sudah** berupa `SubjectPublicKeyInfo` (SEQUENCE), lalu dibungkus lagi dengan BIT STRING sehingga field SPKI berawalan tag 3 → **Invariant Rule**: kunci publik dimasukkan ke TBS apa adanya; BIT STRING hanya untuk nilai tanda tangan; sertifikat wajib di-parse ulang lewat `CertificateFactory` sebelum ditulis. Diuji oleh `DebugKeystoreTest`.

- **[26-11-20 · Tanda tangan/kunci]**: membundel `debug.keystore` berarti semua pemasangan KodeNusa memakai kunci tanda tangan yang sama → **Root Cause**: satu berkas aset dipakai seluruh pengguna, jadi kebocoran APK = kebocoran kunci semua aplikasi buatan pengguna → **Invariant Rule**: kunci dibuat per perangkat di `filesDir/keys/debug.keystore` (ADR-019) dan disimpan di luar folder toolchain yang dihapus saat instal ulang. Diuji oleh `OnDeviceBuildTest.keystore_dibuat_perangkat_bukan_dari_aset`.

- **[26-11-20 · Tes/proses]**: tombol Batal hanya menghentikan coroutine, proses OS tetap hidup → **Root Cause**: cancellation `Flow` tidak otomatis mematikan `Process` anak dan tidak ada tes yang mengukurnya → **Invariant Rule**: proses aktif disimpan di `RealProcessRunner.current` dan dimatikan `destroyForcibly()` saat cancel; dibuktikan `ProcessRunnerTest` lewat PID nyata di `/proc`.

- **[26-11-20 · Tier 0/tanda tangan]**: `PKCS12 key store mac invalid - wrong password or corrupted file` → **Root Cause**: PKCS12 modern dari JDK 17 memakai MAC/algoritma yang tidak dikenal BouncyCastle di Android; `-J-Dkeystore.pkcs12.legacy` saja tidak cukup → **Invariant Rule**: keystore dibuat dengan `-J-Dkeystore.pkcs12.legacy` **plus** RC2-40/SHA1 (cert), 3DES/SHA1 (key), HmacPBESHA1 (mac), dan apksigner dipanggil dengan `--ks-type PKCS12`.

- **[26-11-20 · Tier 0/tanda tangan]**: `toPath()` `NoSuchMethodError` dari `ApkSignerTool.sign` di API 24 → **Root Cause**: apksigner 0.9+ memakai NIO, dan 0.8 belum mengenal `--v3-signing-enabled` serta menebak minSdk dari manifest → **Invariant Rule**: bundel memakai apksigner 0.8, argumen `--v2-signing-enabled true --v1-signing-enabled false --min-sdk-version <minSdk>`, tanpa opsi v3.

- **[26-11-20 · Tier 0/aapt2]**: `error: illegal map type 'string' (22)` saat link → **Root Cause**: aapt2 2.19 bionic tidak bisa membaca `android.jar` API 35 → **Invariant Rule**: Tier 0 dipin ke `android.jar` API 34 (`DEFAULT_TARGET_SDK = 34`); menaikkan versi hanya setelah ada biner aapt2 bionic baru yang terbukti.

- **[26-11-20 · UI/projek baru]**: user mengisi form "Projek baru" dengan benar tetapi tombol "Buat projek" tidak pernah bisa ditekan → **Root Cause**: `assets/templates/index.json` berbentuk objek `{"templates": [...]}` sementara `Templates.list()` men-decode sebagai array telanjang; `runCatching{}.getOrDefault(emptyList())` menelan galatnya sehingga daftar template kosong, `selected == null`, dan `canCreate` selalu false tanpa pesan apa pun → **Invariant Rule**: (1) setiap pembacaan aset wajib mengembalikan `Result` dan sebabnya ditampilkan di layar, tidak boleh `getOrDefault(emptyList())`; (2) tombol utama tidak boleh bergantung pada daftar yang bisa kosong — selalu ada pilihan terakhir "projek kosong"; (3) galat validasi untuk field yang tidak punya kotak isian wajib dirender, bukan disembunyikan. Uji: `TemplateCatalogTest`, `TemplatesDeviceTest`.

- **[26-11-20 · Bahasa]**: user mengira KodeNusa hanya bisa Java → **Root Cause**: satu-satunya template yang membangun APK memakai Java; HTML/CSS/JS sebenarnya sudah bisa diedit tapi tidak ada jalur "jadi APK" → **Invariant Rule**: pipeline Tier 0 sudah memaket `src/main/assets` (`ApkPackager.copyAssets`), jadi template web cukup menaruh berkas di `src/main/assets` dan dibungkus WebView — tidak perlu mengubah pipeline. Python tetap butuh interpreter, jadi tidak boleh dijanjikan di Tier 0.

---

## Cara menambah entri

1. Hanya untuk bug/gagal build/gotcha non-trivial yang sudah didiagnosis dan diperbaiki.
2. Tiga bagian wajib: gejala (yang terlihat user), root cause (teknis, spesifik), invariant rule (bisa diuji).
3. Kalau root cause belum pasti, **jangan** tulis invariant — tulis dulu di catatan investigasi.
4. Invariant yang bisa diuji harus punya test; sebut nama test-nya kalau ada.

- **[25-11-20 · Toolchain/Android 10+]**: build yang lulus di API 24 gagal total di API 34 → **Root Cause**: (1) SELinux melarang eksekusi biner dari folder data aplikasi (`error=13 Permission denied`), (2) ART menolak `dex` dari berkas writable (`SecurityException: Writable dex file is not allowed`), (3) `app_process` sejak API 28 membaca token ketiga sebagai `--nice-name`, bukan folder kelas → **Invariant Rule**: biner native hanya boleh dijalankan dari `nativeLibraryDir` (kemas sebagai `lib*.so` + `useLegacyPackaging=true`); jar wajib `setReadOnly()` setelah verifikasi SHA-256; `AppProcessLauncher` memilih argv lama/modern berdasarkan `Build.VERSION.SDK_INT`. Diuji oleh `ExecPolicyDeviceTest` dan seluruh `OnDeviceBuildTest` di API 24 **dan** API 34.


- **[25-11-20 · Panel berkas]**: projek bisa dibuat tetapi berkas tidak bisa ditambah/diubah dan berkas web tidak muncul → **Root Cause**: panel hanya membaca daftar, tidak punya operasi tulis; `TEXT_EXTENSIONS` tidak memuat html/css/js sehingga berkas template web tidak pernah ditampilkan → **Invariant Rule**: setiap permukaan yang menampilkan berkas wajib punya jalur tambah/ganti nama/hapus lewat satu modul murni (`ProjectFiles`) dengan validasi nama dan pengecekan `canonicalPath`; daftar ekstensi teks wajib mencakup semua jenis berkas yang bisa dibuat aplikasi sendiri. Diuji oleh `ProjectFilesTest`, `ProjectArchiveTest`, dan `FileOpsUiTest`.


- **[25-11-20 · AI/BYOK]**: jawaban model terlihat kosong di awal aliran → **Root Cause**: endpoint OpenAI-compatible mengirim `delta.reasoning` sebelum `delta.content`; menggabungkan keduanya juga membuat kode yang disisipkan ke editor diawali omonongan model → **Invariant Rule**: `content` dan `reasoning` wajib dipisah di klien (reasoning hanya cadangan bila content kosong), dan penyedia yang mengabaikan `stream` maupun membalas 503 sesaat wajib ditangani tanpa membuat pengguna melihat exception mentah. Diuji oleh `OpenAiCompatClientTest` + `ChatNyataTest`.


- **[20-11-25 · Panel berkas]**: Berkas/folder baru "hilang" setelah dibuat → **Root Cause**: dua hal sekaligus — (1) folder tujuan diambil otomatis dari berkas aktif sehingga berkas masuk ke `src/main/java/id/…`, (2) `LazyColumn` dengan `key` mempertahankan posisi gulir saat item disisipkan di atas, jadi baris baru lahir di luar layar. → **Invariant Rule**: pembuatan berkas/folder **wajib** menampilkan pemilih lokasi dengan bawaan "akar projek", dan uji UI harus `performScrollToNode` sebelum memeriksa baris baru.
- **[20-11-25 · Uji UI Compose]**: `onAllNodesWithTag` gagal padahal data ada di state → **Root Cause**: item berada di luar viewport `LazyColumn` sehingga belum dikompos, bukan karena state salah; log `segarkan()`/panel membuktikan daftar sudah berisi item. → **Invariant Rule**: sebelum menyimpulkan "UI tidak me-refresh", gulir dulu ke node yang dicari; buktikan state lewat log, bukan lewat kegagalan pencarian node.
