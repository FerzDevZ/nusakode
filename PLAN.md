# KodeNusa — Peta Proyek & Status

> **Satu kalimat:** IDE Android native yang menghasilkan APK asli di dalam HP 2–4 GB RAM / 32–64 GB penyimpanan — tanpa PC, tanpa Gradle, tanpa internet wajib — sepenuhnya berbahasa Indonesia.

**Status: v0.22.0** · 374 uji unit · 142 uji perangkat · detekt 0 temuan · Android 7.0+ (API 24)

Dokumen ini adalah **indeks dan peta status**. Detail ada di `docs/`; riwayat rilis ada di [`docs/02-roadmap-milestone.md`](docs/02-roadmap-milestone.md).

---

## 1. Baca berurutan

| # | Dokumen | Isi |
|---|---|---|
| 0 | [`docs/00-visi-produk.md`](docs/00-visi-produk.md) | Untuk siapa, masalah apa, janji yang dipegang, dan yang sengaja tidak dikerjakan |
| 1 | [`docs/01-arsitektur-teknis.md`](docs/01-arsitektur-teknis.md) | Modul, pipeline 9 langkah, aliran galat, AI + konteks otomatis, Git, rahasia, verifikasi |
| 2 | [`docs/02-roadmap-milestone.md`](docs/02-roadmap-milestone.md) | Riwayat v0.1.0 → v0.22.0 dan langkah berikutnya menurut risiko |
| 3 | [`docs/03-adr.md`](docs/03-adr.md) | 41 ADR + tabel "kalau Anda bertanya X, baca nomor ini" |
| 4 | [`docs/04-backlog-agent-matrix.md`](docs/04-backlog-agent-matrix.md) | Agen/skill yang dipakai, backlog per milestone, gate yang tidak boleh dilewati |
| 5 | [`docs/05-toolchain-di-perangkat.md`](docs/05-toolchain-di-perangkat.md) | Versi alat, alasan pin, cara verifikasi ulang |
| 6 | [`docs/06-rencana-2026.md`](docs/06-rencana-2026.md) | Status nyata rencana lama: yang tercapai, yang dibatalkan, yang menunggu |
| 7 | [`docs/07-eval-agen.md`](docs/07-eval-agen.md) | 10 tugas evaluasi agen AI + hasil nyata 9/10 |
| 8 | [`docs/08-lembar-ukur-4gb.md`](docs/08-lembar-ukur-4gb.md) | Lembar ukur RAM/build/suhu di HP nyata |
| 9 | [`docs/09-rencana-v2-android11.md`](docs/09-rencana-v2-android11.md) | Scoped storage, izin, anggaran 4/64, panduan pemasangan |
| 10 | [`docs/10-tema-desain.md`](docs/10-tema-desain.md) | Rencana sistem tema (tuntas v0.11.0) |
| 11 | [`docs/11-rencana-v0.12.md`](docs/11-rencana-v0.12.md) | Rencana v0.12 (tuntas) |
| 12 | [`docs/12-saran-ui-ux-ai.md`](docs/12-saran-ui-ux-ai.md) | Diagnosis + perbaikan layar AI (tuntas v0.13–v0.15) |
| 13 | [`docs/13-rencana-ui-ux.md`](docs/13-rencana-ui-ux.md) | Audit visual + polesan desain (tuntas v0.16) |
| 14 | [`docs/14-saran-pengembangan.md`](docs/14-saran-pengembangan.md) | Rencana v0.17–v0.19 (tuntas) |
| — | [`docs/FITUR.md`](docs/FITUR.md) | Peta fitur ✅/🟡/⛔ beserta bukti uji |
| — | [`DESIGN.md`](DESIGN.md) | Arah visual, token OKLCH, tipografi, aturan state |
| — | [`MEMORY.md`](MEMORY.md) | Invariant dari akar masalah nyata, dibaca setiap sesi |

## 2. Keputusan yang sudah dikunci

1. **Build hybrid 3-tier, tetapi hanya Tier 0 yang dibangun.** Tier 1 (Gradle di cloud) dan Tier 2 (proot/JDK) tetap tercatat sebagai rencana di ADR-001, belum ada kodenya — karena itu projek Gradle **tidak bisa dibuild** dan aplikasi wajib mengatakan itu (ADR-001, ADR-007).
2. **Tier 0 = nol Gradle, di dalam HP.** `aapt2 → ECJ 3.25 → dx → apksigner 0.8`, perataan oleh `ZipAligner` Kotlin. Build template hangat **1,4–1,9 detik**; build pertama butuh ~21 detik karena penyalinan `android.jar` 24 MB (sekali saja).
3. **Native Kotlin + Jetpack Compose**, `minSdk 24`, dua ABI (`arm64-v8a`, `armeabi-v7a`). Bukan WebView: editor harus 60 fps di HP kelas bawah.
4. **Lokal-first.** Folder projek di penyimpanan internal adalah sumber kebenaran. AI, Git, dan GitHub hanya menyala bila pengguna mengisinya.
5. **Java dulu, Kotlin tidak.** Tier 0 mengompilasi Java 8. Kotlin butuh compiler 70 MB+ atau JVM penuh — ditolak dengan alasan di ADR-003.
6. **AI BYOK + konteks otomatis** (ADR-023, v0.22.0): kunci milik pengguna, tanpa server perantara; `PetaProjek` + `PemilihKonteks` memilih berkas relevan secara deterministik — tanpa embeddings, tanpa server.

## 3. Angka yang mengikat (terukur, bukan target)

| Metrik | Angka | Sumber |
|---|---|---|
| Ukuran APK rilis | **32.897.316 bita** | rilis v0.22.0 (`KodeNusa-0.22.0.apk`) |
| RAM menganggur | **66–67 MB PSS** | emulator API 35 (HP fisik belum) |
| Build template hangat | **1,4–1,9 s** | emulator, 9 template |
| Build pertama setelah pasang | **21,4 s** | penyalinan `android.jar` 24 MB |
| Cold start editor | **≈ 0,4 s** | emulator |
| Uji unit / perangkat | **374 / 142** | `./gradlew test` · `:app:connectedDebugAndroidTest` |
| Eval agen nyata | **9/10**, median 18,6–41,8 s | [docs/07](docs/07-eval-agen.md), model GLM-5.3-Flash |

## 4. Cara menjaga rencana ini jujur

```bash
export JAVA_HOME=/opt/jdk17 PATH=/opt/jdk17/bin:/opt/gradle/bin:$PATH ANDROID_HOME=$HOME/android-sdk

./gradlew clean test detekt :app:assembleDebug     # gate 1
./gradlew :app:connectedDebugAndroidTest           # gate 2 — 142 uji
./gradlew :app:assembleRelease -PsemuaAbi          # dipasang & dibuka sebelum dibagikan
bash scripts/prove/build-hello.sh                  # pipeline tanpa emulator
```

Milestone **tidak** ditandai selesai tanpa exit criteria dijalankan dan exit code 0. Setiap akar masalah baru masuk `MEMORY.md` sebagai invariant.

## 5. Status per rilis (ringkas)

| Versi | Yang berubah |
|---|---|
| 0.4.0 | Operasi berkas penuh, ekspor/impor zip, peta fitur jujur |
| 0.5.0 | AI BYOK, tanpa kunci bawaan |
| 0.6.0 | Pindah/salin berkas, agen build, Git di UI |
| 0.7.0 | Situs murni, terbit ke GitHub Pages, layar Git |
| 0.8.x | Papan projek agen, gerbang build, vision, perbaikan instalasi Android 13+ |
| 0.9.x | Mode hemat RAM, cari se-projek, SAF, Keystore, PWA, eval agen, foto ke AI, pratinjau situs, bagikan APK ke GitHub Release, palet perintah, Python (MicroPython) |
| 0.10.0–0.11.0 | Laporan galat offline, klon GitHub, buat repo, tema & tampilan (4 tema + `.kntema`) |
| 0.12.0–0.13.0 | Perbaikan dari uji manual; AI bisa membuat berkas baru di sub-folder; pemilih berkas pohon |
| 0.14.0–0.15.0 | Pratinjau tulis + undo agen (ADR-038); riwayat agen + prompt tahu template (ADR-039) |
| 0.16.0 | Polesan UI/UX penuh (docs/13 tuntas) + 2 template baru |
| 0.17.0 | Kunci tanda tangan pengguna (ADR-041), ikon aplikasi, versionCode otomatis, tombol Pasang, editor makin tajam |
| 0.18.0 | Ekspor CSV, 3 template baru, cadangkan semua projek |
| 0.19.0 | Agen putaran pilihan (eval nyata 9/10), "template saya", galeri komunitas |
| 0.20.0 | Papan projek AI (`PetaProjek`/`PemilihKonteks`), chip "Pelajari projek" |
| **0.21.0** | **Gutter diperombak total + konteks otomatis terverifikasi di projek besar + mode belajar (2 template) + cadangan otomatis harian + endpoint AI cadangan (failover 503) + lampiran log galat build** |
| **0.22.0** | **AI tulis berkas otomatis di chat biasa: format `<<<BERKAS:>>>` diajarkan di semua jalur chat, aturan satu-berkas-satu-jenis (anti HTML-dalam-JS), template web diperjelas, dialog "buat berkas baru" terisi otomatis, + 12 test lama yang mati dihidupkan** |

Riwayat lengkap per rilis: [`docs/02-roadmap-milestone.md`](docs/02-roadmap-milestone.md).

## 6. Yang berikutnya

1. **Ukur HP fisik 4 GB** (todo #10) — prasyarat klaim "4 GB cukup"; skrip siap.
2. **Umpan balik konteks otomatis** di projek nyata — tuning bobot/anggaran bila perlu.
3. **Mode belajar** untuk template ketiga dan seterusnya.
4. **Verifikasi tulis-nyata AI lewat endpoint cadangan** bila penyedia utama masih 503 — isi endpoint kedua di setelan AI lalu uji "Simpan berkas" di layar AI.
