# KodeNusa — IDE Android untuk semua HP Indonesia

> **Satu kalimat:** IDE Android native yang menghasilkan APK asli di dalam HP 2–4 GB RAM / 32–64 GB penyimpanan — tanpa PC, tanpa Gradle, tanpa internet wajib — sepenuhnya berbahasa Indonesia.

**Status: v0.16.0** · 253 uji unit · 106 uji perangkat · detekt 0 temuan · Android 7.0+

Dokumen ini adalah **indeks dan peta status**. Detail ada di `docs/`.

---

## Baca berurutan

| # | Dokumen | Isi |
|---|---|---|
| 0 | [`docs/00-visi-produk.md`](docs/00-visi-produk.md) | Untuk siapa, masalah apa, janji yang dipegang, dan yang sengaja tidak dikerjakan |
| 1 | [`docs/01-arsitektur-teknis.md`](docs/01-arsitektur-teknis.md) | Modul, pipeline 9 langkah, aliran galat, AI, Git, rahasia, strategi verifikasi |
| 2 | [`docs/02-roadmap-milestone.md`](docs/02-roadmap-milestone.md) | Riwayat v0.1.0 → v0.12.0 dan langkah berikutnya menurut risiko |
| 3 | [`docs/12-saran-ui-ux-ai.md`](docs/12-saran-ui-ux-ai.md) | Fakta + saran UI/UX layar AI; butir 2 (v0.13.0) sudah dikerjakan |
| 3 | [`docs/11-rencana-v0.12.md`](docs/11-rencana-v0.12.md) | Rencana v0.12+: prioritas terukur + kandidat yang butuh ADR |
| 3 | [`docs/03-adr.md`](docs/03-adr.md) | 31 ADR + tabel "kalau Anda bertanya X, baca nomor ini" |
| 4 | [`docs/04-backlog-agent-matrix.md`](docs/04-backlog-agent-matrix.md) | Agen/skill yang dipakai, backlog per milestone, gate yang tidak boleh dilewati |
| 5 | [`docs/05-toolchain-di-perangkat.md`](docs/05-toolchain-di-perangkat.md) | Versi alat, alasan pin, cara verifikasi ulang |
| 6 | [`docs/06-rencana-2026.md`](docs/06-rencana-2026.md) | Status nyata rencana lama: yang tercapai, yang dibatalkan, yang menunggu |
| 7 | [`docs/07-eval-agen.md`](docs/07-eval-agen.md) | 10 tugas evaluasi agen AI dan cara mengisinya |
| 8 | [`docs/08-lembar-ukur-4gb.md`](docs/08-lembar-ukur-4gb.md) | Lembar ukur RAM/build/suhu di HP nyata |
| 9 | [`docs/FITUR.md`](docs/FITUR.md) | Peta fitur ✅/🟡/⛔ beserta bukti uji |
| — | [`DESIGN.md`](DESIGN.md) | Arah visual, token OKLCH, tipografi, aturan state |
| — | [`MEMORY.md`](MEMORY.md) | Invariant dari akar masalah nyata, dibaca setiap sesi |

---

## Keputusan yang sudah dikunci

1. **Build hybrid 3-tier, tetapi hanya Tier 0 yang dibangun.** Tier 1 (Gradle di cloud) dan Tier 2 (proot/JDK) tetap tercatat sebagai rencana di ADR-001, belum ada kodenya — karena itu projek Gradle **tidak bisa dibuild** dan aplikasi wajib mengatakan itu (ADR-001, ADR-007).
2. **Tier 0 = nol Gradle, di dalam HP.** `aapt2 → ECJ 3.25 → dx → apksigner 0.8`, perataan oleh `ZipAligner` Kotlin. Build template hangat **1,4–1,9 detik**; APK pertama di perangkat butuh 21 detik karena penyalinan `android.jar` 24 MB (sekali saja).
3. **Native Kotlin + Jetpack Compose**, `minSdk 24`, dua ABI (`arm64-v8a`, `armeabi-v7a`). Bukan WebView: editor harus 60 fps di HP kelas bawah.
4. **Lokal-first.** Folder projek di penyimpanan internal adalah sumber kebenaran. AI, Git, dan GitHub hanya menyala bila pengguna mengisinya.
5. **Java dulu, Kotlin tidak.** Tier 0 mengompilasi Java 8. Kotlin butuh compiler 70 MB+ atau JVM penuh — ditolak dengan alasan di ADR-003.

## Angka yang mengikat (terukur, bukan target)

| Metrik | Angka | Sumber |
|---|---|---|
| Ukuran APK rilis | **32.772.723 bita** | `dist/KodeNusa-0.10.0.apk` |
| RAM menganggur | **67 MB PSS** | emulator API 35 (HP fisik belum) |
| Build template hangat | **1,4–1,9 s** | emulator, 3 template |
| Build pertama setelah pasang | **21,4 s** | penyalinan `android.jar` 24 MB |
| Cold start editor | **≈ 0,4 s** | emulator |
| Uji unit / perangkat | **252 / 77** | `./gradlew test` · `:app:connectedDebugAndroidTest` |

## Cara menjaga rencana ini jujur

```bash
export JAVA_HOME=/opt/jdk17 PATH=/opt/jdk17/bin:/opt/gradle/bin:$PATH ANDROID_HOME=$HOME/android-sdk

./gradlew clean test detekt :app:assembleDebug     # gate 1
./gradlew :app:connectedDebugAndroidTest           # gate 2 — 77 uji
./gradlew :app:assembleRelease -PsemuaAbi          # dipasang & dibuka sebelum dibagikan
bash scripts/prove/build-hello.sh                  # pipeline tanpa emulator
```

Milestone **tidak** ditandai selesai tanpa exit criteria dijalankan dan exit code 0. Setiap akar masalah baru masuk `MEMORY.md` sebagai invariant.

## Status per rilis

| Versi | Yang berubah |
|---|---|
| 0.4.0 | Operasi berkas penuh, ekspor/impor zip, peta fitur jujur |
| 0.5.0 | AI BYOK, tanpa kunci bawaan |
| 0.6.0 | Pindah/salin berkas, agen build, Git di UI |
| 0.7.0 | Situs murni, terbit ke GitHub Pages, layar Git |
| 0.8.x | Papan projek agen, gerbang build, vision, perbaikan instalasi Android 13+ |
| 0.9.0–0.9.2 | Mode hemat RAM, cari se-projek, SAF impor/bagikan |
| 0.9.3 | Rahasia tersandi Keystore, PWA, eval agen, lembar ukur |
| 0.9.4 | Foto ke AI, ganti semua, impor zip tanpa ANR |
| 0.9.5 | Pratinjau situs, bagikan APK lewat GitHub Release |
| 0.9.6 | Palet perintah, progres unggah bertahap, catatan jujur soal Python |
| 0.9.9 | Perapatan tampilan (ADR-033): tombol sekunder berwujud, kolom nama wizard selalu terlihat, kelompok pilihan berjudul, Git menyuruh siapkan repo dulu |
| 0.10.0 | Python bisa dijalankan (ADR-034): MicroPython dibawa di APK, tombol Jalankan + konsol di editor, 5 uji perangkat nyata |
| 0.16.0 | **Polesan UI/UX penuh + 2 template baru** (docs/13 tuntas): sentuhan lebih hidup (tekan 0.96×, haptic sekali saat build sukses), angka tabel rapi (tnum), titik status build ala Linear, ProjectRow asimetris, fokus ring keyboard/OTG, pesan AI bertata letak, pencarian projek lintas berkas, tombol **Rapikan** indentasi, **riwayat build** per projek (10 terakhir), **mode hemat kuota** AI, template **absensi-kelas** & **laporan-warung** (total 7 template), kartu izin tidak bertumpuk |
| 0.15.0 | **Riwayat agen + prompt tahu template** (ADR-039): tulisan AI tercatat per projek (tugas → berkas berubah → batch undo) dan bisa dibuka ulang dari layar AI; prompt agen otomatis diberi struktur tipikal template projek (kasir-warung → DatabaseKasir, dst.) |
| 0.14.0 | **AI tidak bisa lagi merusak kerja** (ADR-038): pratinjau tulis wajib (daftar berkas baru/timpa + ukuran), cadangan otomatis + tombol "Batalkan tulisan AI" (jalur Simpan & Sisip), berkas sematan maks 3 dikirim utuh, chip perintah cepat baru, indikator karakter terkirim |
| 0.13.0 | **AI bisa membuat berkas baru di sub-folder** — bug `createFile` (folder belum ada ditolak) diperbaiki, tombol "Buat berkas baru dari kode" di jawaban AI, prompt agen eksplisit izin berkas baru, pemilih berkas AI jadi pohon + pencarian |
| 0.12.0 | Perbaikan dari uji manual (ADR-037): unduh tema dari tautan jalan (HTTP polos diizinkan dengan pagar 4 KB + gerbang kontras), pesan galat unduh manusiawi, riwayat tautan, bug `gelap` di `.kntema` diperbaiki, pintasan tema di palet perintah, uji memutar 5 tema × recreate. Sebelumnya (0.11.0): tema & tampilan (ADR-035/036): 4 tema bawaan teruji kontras, "Ikuti sistem", layar Tema, tema kustom `.kntema` dengan gerbang kontras, ikon vektor + Banner/SegmentedControl |
| **0.9.7** | Janji "build di server" dihapus; dokumentasi ditulis ulang penuh |

Berikutnya: penangkap crash offline, klon projek dari GitHub, catatan "Kotlin belum didukung". Detail dan alasannya di [`docs/02`](docs/02-roadmap-milestone.md).