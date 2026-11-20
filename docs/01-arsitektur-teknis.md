# 01 — Arsitektur Teknis

## 1. Gambaran besar

```
┌──────────────────────────── HP ANDROID (offline penuh) ────────────────────────────┐
│                                                                                    │
│  :app (Compose, Hilt, Navigation)                                                  │
│    ├─ :feature:workspace      daftar projek, buka/buat, import zip/git             │
│    ├─ :feature:editor         tab, file tree, editor, outline, search              │
│    ├─ :feature:build          tombol Build, log streaming, error→baris             │
│    ├─ :feature:template       wizard 5 langkah, katalog template lokal             │
│    ├─ :feature:learn          halaman konsep 200 kata, glosarium istilah           │
│    └─ :feature:share          export APK, QR, FileProvider                         │
│                                                                                    │
│  :core:editor   Sora Editor (native) + TextMate grammar + incremental highlighter  │
│  :core:project  model projek (kodenusa.json), file index, watcher, undo stack      │
│  :core:build    interface BuildEngine + BuildRequest/BuildEvent/BuildResult        │
│  :build:tier0   ecj + d8 + aapt2 + zipalign + apksigner (ProcessBuilder)           │
│  :build:cloud   Ktor client, SSE log stream, upload resumable, artifact download   │
│  :build:tier2   proot/JDK manager (unduh opsional, power user)                     │
│  :core:diagnose error parser → DiagnosticFix (pesan ID + quick action)             │
│                                                                                    │
│  assets/toolchain/arm64-v8a/  (aapt2, d8 deps, zipalign, apksigner, ecj.jar,       │
│                               android.jar, debug.keystore)  ≈ 55–70 MB             │
│  filesDir/toolchain/          hasil ekstraksi saat first-run (checksum terverifikasi)│
└────────────────────────────────────────────────────────────────────────────────────┘
                                  │ opsional, eksplisit
                                  ▼
┌──────────────────────────── CLOUD TIER 1 (Kotlin/Ktor) ────────────────────────────┐
│  API: POST /v1/builds · GET /v1/builds/{id}/events (SSE) · GET /v1/builds/{id}/apk │
│  Worker: Node/Go + Docker image (JDK 17, Android SDK 35, Gradle cache warm)        │
│  Isolasi: satu container per build, network egress allowlist, TTL 15 menit,        │
│           artefak dihapus setelah 1 jam, tidak ada akses ke kode user lain         │
└────────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Modul & tanggung jawab

| Modul | Tanggung jawab | Dilarang |
|---|---|---|
| `:app` | navigasi, DI graph, tema, ikon, string ID | logika build, akses filesystem langsung |
| `:core:editor` | teks, syntax highlight, selection, bracket match, undo | tahu soal build |
| `:core:project` | model projek, baca/tulis file, validasi `kodenusa.json` | spawn proses |
| `:core:build` | kontrak `BuildEngine`, orkestrasi event, cancellation | tahu tool apa pun |
| `:build:tier0` | eksekusi tool, caching, signing | akses UI |
| `:build:cloud` | HTTP/SSE, retry, resumable | build lokal |
| `:core:diagnose` | parse stderr → `Diagnostic` + `QuickFix` | menampilkan UI |
| `:feature:*` | UI per fitur, state via `ViewModel` + `StateFlow` | saling memanggil langsung |

Aturan dependensi: `feature → core ← impl`. `core` tidak pernah melihat `feature` (skill `android-clean-architecture`).

## 3. Kontrak build (jantung sistem)

```kotlin
// :core:build
sealed interface BuildRequest {
    val projectId: String
    data class Local(override val projectId: String, val tier: Tier0Config) : BuildRequest
    data class Remote(override val projectId: String, val variant: String) : BuildRequest
}

data class Tier0Config(
    val appName: String,
    val applicationId: String,
    val versionCode: Int,
    val minSdk: Int = 24,
    val targetSdk: Int = 35,
    val sourceFiles: List<Path>,      // sudah difilter .java
    val resourceDirs: List<Path>,
    val assetDirs: List<Path>,
    val manifest: Path,
    val extraJars: List<Path>,
    val incremental: Boolean = true,
)

sealed interface BuildEvent {
    data class Step(val name: BuildStep) : BuildEvent
    data class Log(val stream: LogStream, val line: String) : BuildEvent
    data class Diagnostic(val diagnostic: Diagnostic) : BuildEvent
    data class Progress(val step: BuildStep, val percent: Int) : BuildEvent
}

data class BuildResult(
    val success: Boolean,
    val apk: Path?,                    // null jika gagal
    val durationMs: Long,
    val diagnostics: List<Diagnostic>,
    val apkSizeBytes: Long?,
)

interface BuildEngine {
    fun build(request: BuildRequest): Flow<BuildEvent>   // cold flow, cancellable
}

enum class BuildStep { PREPARE, AAPT_COMPILE, AAPT_LINK, JAVAC, D8, PACKAGE, ALIGN, SIGN }
```

`Flow` dipilih (bukan callback) supaya cancellation gratis saat user menekan Batal atau meninggalkan layar.

## 4. Pipeline Tier 0 — langkah detail

| # | Langkah | Perintah | Output | Cache key |
|---|---|---|---|---|
| 1 | PREPARE | validasi `kodenusa.json`, ekstrak toolchain bila checksum beda | `build/` bersih | versi toolchain |
| 2 | AAPT_COMPILE | `aapt2 compile --dir res -o build/res.zip` | `res.zip` | hash semua file `res/**` |
| 3 | AAPT_LINK | `aapt2 link -I android.jar --manifest AndroidManifest.xml -o build/base.apk --java build/gen build/res.zip` | `base.apk` + `R.java` | manifest + res hash |
| 4 | JAVAC | `ecj -source 11 -target 11 -bootclasspath android.jar -d build/classes <sources> <extraJars>` | `.class` | hash per file `.java` |
| 5 | D8 | `d8 --min-api 24 --output build/dex build/classes/**/*.class` | `classes.dex` | hash class dir |
| 6 | PACKAGE | zip: `base.apk` + `classes.dex` (+ assets) | `unsigned.apk` | langkah 3+5 |
| 7 | ALIGN | `zipalign -p -f 4 unsigned.apk aligned.apk` | `aligned.apk` | — |
| 8 | SIGN | `apksigner sign --ks debug.keystore --ks-pass pass:android --v1-signing-enabled true --v2-signing-enabled true` | `app-debug.apk` | — |

**Target waktu** (Redmi 9A, template 8 file / 12 resource): langkah 1–3 ≈ 4 s, 4 ≈ 6 s, 5 ≈ 5 s, 6–8 ≈ 3 s → **±20 s build dingin, ±6 s build inkremental**.

**Eksekusi binary:** toolchain diekstrak ke `filesDir/toolchain/<arch>/`, permission `0755`, dijalankan via `ProcessBuilder` dengan `LD_LIBRARY_PATH` di-set ke direktori yang sama. Semua binary dibangun untuk **16 KB page size** (wajib Android 15+).

## 5. Format projek (tanpa Gradle)

```json
{
  "format": 1,
  "name": "Kasir Warung Bu Dedi",
  "applicationId": "id.kodenusa.warungdedi",
  "versionName": "1.0.0",
  "versionCode": 1,
  "minSdk": 24,
  "targetSdk": 35,
  "javaVersion": 11,
  "entry": "src/main/java/id/kodenusa/warung/MainActivity.java",
  "buildEngine": "tier0",
  "dependencies": { "files": ["libs/appcompat-1.7.0.jar"] },
  "templateId": "kasir-sederhana"
}
```

Aturan: `kodenusa.json` **wajib** ada. Kalau ada `build.gradle`, projek dianggap Tier 1/2 dan tombol Build menawarkan cloud — tanpa mencoba menipu user dengan Tier 0.

## 6. Keamanan

| Ancaman | Mitigasi |
|---|---|
| Kode user menjalankan shell arbitrary | Tidak ada akses terminal di v1. Argumen `ProcessBuilder` disusun dari `List<String>` — tanpa `sh -c`, tanpa interpolasi string. |
| Toolchain dimodifikasi di disk | Checksum SHA-256 tiap binary, diverifikasi tiap first-run dan tiap versi aplikasi berubah. |
| APK yang dibuat berbahaya | APK hanya bisa dibuat user untuk dirinya; tidak ada layanan build publik di v1. |
| Penyimpanan kunci signing | `debug.keystore` dibundel (rahasia umum, memang untuk debug). Keystore release user: disimpan di app-private storage, **tidak pernah** dikirim ke cloud, dan tidak pernah di-log. |
| Kebocoran kode ke Tier 1 | Build cloud hanya setelah dialog eksplisit yang menyebut "kode projek ini akan diunggah ke server". Default: mati. |
| Path traversal dari zip/git import | Normalisasi path + tolak entri zip di luar root projek (zip-slip). |
| Data user hilang | Tidak ada auto-delete. Workspace di `filesDir/workspaces/<id>` dan bisa di-export kapan saja. |

## 7. Anggaran performa (dipaksa, bukan diharapkan)

| Area | Anggaran | Cara menegakkan |
|---|---|---|
| Cold start → editor siap | < 1500 ms | Baseline profile + macrobenchmark di CI |
| RAM idle | < 120 MB | Heap dump di emulator 2 GB, assert `Runtime.maxMemory` usage |
| Keystroke → paint | < 16 ms | Macrobenchmark `FrameTimingMetric` pada file 1.000 baris |
| Scroll file 2.000 baris | 0 frame drop > 32 ms | Sora Editor viewport rendering, bukan Compose `Text` |
| Ukuran APK | < 80 MB | `apkanalyzer apk summary` di CI, gagal kalau lebih |
| Build template (2 GB) | < 60 s | Automation test di emulator RAM terbatas |

## 8. Risiko teknis & mitigasi (jujur, ini yang bisa membunuh proyek)

| Risiko | Dampak | Mitigasi | Rencana B kalau gagal |
|---|---|---|---|
| Eksekusi binary dari data dir diblokir di versi Android tertentu | Tier 0 mati | Binary 16 KB-aligned, `extractNativeLibs=true`, uji di API 24/29/34/35/36 sebelum M2 selesai | Pindahkan toolchain ke `nativeLibraryDir` sebagai `.so`, atau jalankan via `libsu`/`libadb` |
| `d8` butuh JVM, tidak jalan murni di ART | Dexing mustahil | Pakai `libd8.jar` via `app_process` (pola Termux), atau bundel `dx` lama, atau ganti ke `dexlib2` untuk konversi langsung | Kirim `.class` ke Tier 1 hanya untuk dexing (downgrade kecil) |
| Kotlin tidak bisa Tier 0 | Ekspektasi user | Ditulis jelas di UI sejak awal; Kotlin = Tier 1 | Tambah `kotlinc` + minimal JRE di Tier 2 |
| Ukuran bundel membengkak | Gagal di HP 32 GB | Pisahkan toolchain per-ABI, unduh on-demand untuk armeabi-v7a | Toolchain jadi unduhan terpisah saat onboarding |
| Play Store menolak `REQUEST_INSTALL_PACKAGES` | Distribusi | Deklarasi jelas + alternatif "buka manual dari Files" | Distribusi via APK langsung + F-Droid |
| Pemeliharaan 3 pipeline | Tim kelelahan | Kerjakan berurutan M2 → M5 → M7. Tidak pernah paralel | Buang Tier 2 lebih dulu |

## 9. Verifikasi (Gate 1)

```bash
# build + static analysis
./gradlew :app:assembleDebug :build:tier0:testDebugUnitTest detekt lintDebug

# uji pipeline build tanpa device (JVM, toolchain di-host)
./gradlew :build:tier0:testDebugUnitTest --tests '*Tier0PipelineTest*'

# uji on-device (emulator API 24 & 35, RAM 2 GB)
./gradlew :app:connectedDebugAndroidTest

# ukuran artefak
./gradlew :app:assembleRelease && apkanalyzer apk summary app/build/outputs/apk/release/*.apk
```

Lulus = exit code 0 untuk semua, APK < 80 MB, dan `Tier0PipelineTest` menghasilkan APK yang lolos `apksigner verify`.
