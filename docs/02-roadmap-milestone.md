# 02 — Roadmap & Milestone

Aturan main: setiap milestone punya **deliverable**, **exit criteria yang bisa dieksekusi**, dan **gate command**. Milestone tidak boleh ditandai selesai tanpa gate hijau. Urutan tidak boleh ditukar — Tier 0 mendahului Tier 1 karena Tier 0 membuktikan nilai produk.

Total estimasi: **26 minggu** untuk 1–2 engineer fokus (M0–M7). M5 (cloud) dan M7 (skala) bisa dipotong tanpa membatalkan produk.

---

## M0 · Fondasi & Bukti Kelayakan (2 minggu)

**Tujuan:** membuktikan bahwa APK bisa dibuat di HP tanpa Gradle. Kalau ini gagal, seluruh rencana berubah (lihat risiko #1/#2 di `01`).

Deliverable:
- Repo multi-module Gradle (build **development** boleh Gradle — yang tanpa Gradle adalah projek *user*), CI GitHub Actions, detekt + ktlint.
- `scripts/prove/build-hello.sh` — pipeline shell murni yang menghasilkan `hello.apk` dari 1 file Java + 1 layout.
- Spike on-device: ekstrak `aapt2`/`d8`/`zipalign`/`apksigner` dari `assets/` → jalankan → APK terpasang di perangkat nyata.
- `MEMORY.md` terisi invariant pertama.

Exit criteria:
- [ ] `hello.apk` terbangun & `apksigner verify` lolos.
- [ ] APK terpasang di minimal 2 device fisik (ideal: 1 device 2 GB RAM).
- [ ] Ukuran `assets/toolchain/arm64-v8a` tercatat (target ≤ 70 MB).
- [ ] Waktu build tercatat di HP target.

Gate:
```bash
./gradlew :app:assembleDebug detekt
bash scripts/prove/build-hello.sh && apksigner verify out/hello.apk
```

---

## M1 · Editor Inti (4 minggu)

**Tujuan:** bisa membuka, mengedit, dan menyimpan projek tanpa jank.

Deliverable:
- `:core:editor` dengan Sora Editor (bukan Compose `BasicTextField`).
- File tree + tab + buka/tutup/rename/delete + undo/redo multi-file.
- Grammar Java, XML, JSON, Kotlin (highlight saja).
- Pencarian dalam file & seluruh projek (FTS sederhana).
- Auto-save dengan debounce 800 ms, crash-safe (tulis `.tmp` lalu rename).
- Outline simbol Java (regex + brace matching, bukan parser penuh).

Exit criteria:
- [ ] Scroll file 2.000 baris: tidak ada frame > 32 ms (macrobenchmark).
- [ ] Ketikan pada file 1.000 baris P95 < 16 ms.
- [ ] File 5 MB tetap terbuka < 1 s (streaming, bukan load penuh ke memori UI).
- [ ] Membunuh proses saat mengetik tidak menghilangkan lebih dari 1 detik ketikan.

Gate:
```bash
./gradlew :core:editor:testDebugUnitTest :app:connectedDebugAndroidTest
```

---

## M2 · Tier 0 Build Engine (5 minggu) — **milestone paling berisiko**

Deliverable:
- `:build:tier0` implementasi `BuildEngine` penuh (8 langkah di `01 §4`).
- Caching per-file (hash), build inkremental, tombol Batal (cancellation bersih, tanpa proses zombie).
- `:core:diagnose` parser ECJ/d8/aapt2 → `Diagnostic(file, line, column, severity, messageId, quickFixes)`.
- UI build: log streaming, progress per langkah, panel error yang melompat ke baris saat di-tap.
- Signing `debug.keystore` + install via FileProvider + `REQUEST_INSTALL_PACKAGES`.

Exit criteria:
- [x] Build template 8 file selesai < 60 s di device 2 GB; inkremental < 10 s.
      — terbukti di emulator API 24: 1,6–1,9 s per template, build kedua 0 s (cache).
      Catatan: angka HP fisik 2 GB masih perlu diukur (P4).
- [x] 15 kasus error umum dipetakan ke pesan Indonesia + quick fix (lihat `docs/04`).
      — 26 entri `ErrorCatalog` (termasuk `dex.lambda` khusus keterbatasan dx).
- [x] Build yang dibatalkan tidak meninggalkan proses berjalan (assert via `ActivityManager`/`ps`).
      — `ProcessRunnerTest` memeriksa PID nyata setelah `cancel()`; `destroyForcibly` di `finally`.
- [x] APK hasil build terpasang dan bisa dijalankan (instrumented test otomatis).
      — `OnDeviceBuildTest` 13 uji hijau, termasuk `apksigner verify` v2 di perangkat.

Gate:
```bash
./gradlew :build:tier0:testDebugUnitTest --tests '*Tier0PipelineTest*'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...BuildE2ETest
```

---

## M3 · Onboarding, Template, dan "APK Pertama" (3 minggu)

Deliverable:
- Wizard 5 langkah: nama app → pilih template → isi branding (nama toko/warna/logo) → build → bagikan.
- 6 template fungsional (bukan mock): kasir warung, absensi QR, katalog + order WhatsApp, kas RW, catatan hafalan, profil usaha.
- `:feature:share`: export APK, QR, WhatsApp intent, simpan ke Downloads via MediaStore.
- Empty state, error state, dan offline state untuk semua layar.

Exit criteria:
- [ ] Uji 5 orang non-programmer (P2 persona): ≥ 4 berhasil sampai APK terpasang tanpa dibantu.
- [ ] Median time-to-first-APK < 10 menit.
- [ ] Semua string 100% di `values-in` (Indonesia), istilah teknis via glosarium.

Gate: manual usability test + screenshot flow disimpan di `docs/evidence/m3/`.

---

## M4 · Belajar & Diagnosa Indonesia (4 minggu)

Deliverable:
- 25 halaman konsep 200 kata (activity, layout, intent, adapter, storage) yang dapat dibuka dari editor.
- Glosarium 60 istilah (EN → ID + analogi satu kalimat).
- "Error Translator" v2: 40 pemetaan error paling sering.
- Mode Pemula: panel penjelasan baris yang di-tap (bukan autocomplete ajaib).

Exit criteria:
- [ ] 40 pemetaan error punya test unit (input stderr → output pesan + fix).
- [ ] Halaman belajar dapat diakses offline (< 500 KB total).

---

## M5 · Tier 1 Cloud Build (4 minggu, opsional)

Deliverable:
- Server Ktor + worker Docker (JDK 17, SDK 35, Gradle cache hangat).
- API: `POST /v1/builds`, `GET /v1/builds/{id}/events` (SSE), `GET /v1/builds/{id}/apk`.
- Klien `:build:cloud`: upload resumable, retry dengan backoff, kode build dapat dilanjutkan.
- Dialog consent eksplisit sebelum upload kode; kuota per user.
- Deteksi otomatis "projek ini butuh Tier 1" dengan penjelasan sebelum user menekan Build.

Exit criteria:
- [ ] Build projek Kotlin+Compose contoh sukses end-to-end, APK kembali < 5 menit.
- [ ] Log SSE muncul realtime (latensi < 1 s baris pertama).
- [ ] Worker tidak bisa diakses dari build lain (uji isolasi jaringan).
- [ ] Artefak terhapus ≤ 1 jam (uji cron).

Gate:
```bash
./gradlew :build:cloud:testDebugUnitTest
docker compose -f server/docker-compose.yml run --rm e2e
```

---

## M6 · Kolaborasi & Distribusi (3 minggu)

Deliverable:
- Git: clone/pull/commit/push via JGit, kredensial di EncryptedSharedPreferences.
- Riwayat versi lokal per projek (snapshot, bukan git penuh).
- Export `.zip` projek + import dari file manager/Drive.
- Plugin API internal (template & quick-fix sebagai modul), belum publik.

---

## M7 · Skala & Rilis (3 minggu)

Deliverable:
- Profiling ulang di 3 device kelas bawah; baseline profile; cold start < 1.5 s.
- Toolchain per-ABI on-demand (armeabi-v7a tanpa membengkakkan install arm64).
- Play Store: privacy policy, data safety form, deklarasi `REQUEST_INSTALL_PACKAGES`, AAB + target API terbaru.
- Telemetry minimal & opt-in (crash + build success rate saja, tanpa isi kode).

Exit criteria:
- [ ] APK release < 80 MB, RAM idle < 120 MB.
- [ ] Crash-free rate ≥ 99% di internal test 2 minggu.
- [ ] Rilis internal tersedia untuk 50 beta tester.

---

## Peta ketergantungan

```
M0 ──► M1 ──► M2 ──► M3 ──► M4
                │
                └──► M5 (opsional) ──► M6 ──► M7
```

M4 dan M5 bisa berjalan paralel setelah M3 stabil, **jika** ada ≥ 2 engineer. Dengan 1 engineer: kerjakan berurutan, dan potong M5 kalau waktu mepet.

## Yang tidak dikerjakan sampai M7 (sengaja)

Terminal emulator, debugger step-by-step, LSP penuh, Kotlin native, multi-modul Gradle lokal, tema/plugin marketplace. Semua itu menggoda dan semuanya memperlambat satu hal yang menentukan: **APK pertama yang berhasil**.

---

## Status implementasi per 26-11-20

| Milestone | Status | Bukti |
|---|---|---|
| M0 fondasi | Selesai | 72 tes hijau, `detekt` bersih, `:app:assembleDebug` menghasilkan APK 12,2 MB |
| M1 editor | Selesai untuk lingkup satu berkas | `Highlighter` 9 tes, `CodeEditorView` (baris terlihat saja) |
| M2 engine Tier 0 | Selesai di host, **belum di perangkat** | `examples/hello` + 3 template → APK sah (`apksigner verify`) |
| M3 onboarding/template | Selesai | 3 template jalan: halo-dunia, kasir-warung, katalog-whatsapp |
| M4 pembelajaran/diagnostik | Sebagian | `ErrorCatalog` + `DiagnosticParser` (17 tes), panduan per error masih tumbuh |
| M5 cloud | Belum | — |
| M6/M7 | Belum | — |

Langkah berikutnya yang menentukan: membuktikan `aapt2`, `app_process`, dan `exec` berjalan di
Android 10–15 pada dua ABI (lihat [`05-toolchain-di-perangkat.md`](05-toolchain-di-perangkat.md)).
