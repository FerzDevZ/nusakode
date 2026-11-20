# KodeNusa

[![Unduh APK](https://img.shields.io/badge/Unduh-APK%200.7.0-amber?style=flat-square)](https://github.com/FerzDevZ/nusakode/releases/download/v0.7.0/KodeNusa-0.7.0-debug.apk)
[![Rilis](https://img.shields.io/badge/rilis-v0.7.0-blue?style=flat-square)](https://github.com/FerzDevZ/nusakode/releases)

IDE Android native untuk mengoding **langsung di HP**, dirancang untuk perangkat kelas bawah Indonesia (2 GB RAM / 32 GB storage), bekerja offline, dan berbahasa Indonesia penuh.

## Unduh dan pasang

**Peta fitur lengkap (apa yang sudah bisa, apa yang belum):** [docs/FITUR.md](docs/FITUR.md)

**→ [Unduh APK KodeNusa 0.7.0 (44,7 MB)](https://github.com/FerzDevZ/nusakode/releases/download/v0.7.0/KodeNusa-0.7.0-debug.apk)** · [semua rilis](https://github.com/FerzDevZ/nusakode/releases)

1. Buka tautan di atas dari HP (boleh lewat browser apa pun, tanpa login).
2. Buka berkas `KodeNusa-0.7.0-debug.apk` dari notifikasi atau folder Unduhan.
3. Saat muncul “Untuk keamanan, HP-mu tidak mengizinkan…”, pilih **Setelan** → aktifkan **Izinkan dari sumber ini**.
4. Tekan **Instal**. Play Protect bisa meminta konfirmasi tambahan karena ini APK debug.

Butuh Android 7.0 (API 24) atau lebih baru. Aplikasi tidak meminta izin internet — seluruh build berjalan di HP.

Halaman unduhan alternatif (lewat Tailscale, dari mesin build langsung): `http://100.122.225.77:8080/` — hanya aktif selama `bash scripts/serve-apk.sh` berjalan.

Repositori ini berisi **implementasi yang sudah berjalan** untuk Tier 0 (build APK tanpa Gradle)
dan aplikasi Android-nya, bukan hanya rencana.

**Mulai di sini → [`PLAN.md`](PLAN.md)**

| Dokumen | Isi |
|---|---|
| [`PLAN.md`](PLAN.md) | Indeks, keputusan terkunci, angka target |
| [`docs/00-visi-produk.md`](docs/00-visi-produk.md) | Masalah, persona, 4 pilar terobosan, metrik |
| [`docs/01-arsitektur-teknis.md`](docs/01-arsitektur-teknis.md) | Arsitektur 3-tier, modul, pipeline build, keamanan |
| [`docs/02-roadmap-milestone.md`](docs/02-roadmap-milestone.md) | M0–M7 + exit criteria + gate command |
| [`docs/03-adr.md`](docs/03-adr.md) | 19 keputusan arsitektur |
| [`docs/04-backlog-agent-matrix.md`](docs/04-backlog-agent-matrix.md) | Tugas → agent → skill → DoD |
| [`docs/05-toolchain-di-perangkat.md`](docs/05-toolchain-di-perangkat.md) | Status toolchain, yang kurang, urutan M2 |
| [`DESIGN.md`](DESIGN.md) | Arah visual, token OKLCH, aturan anti-slop |
| [`MEMORY.md`](MEMORY.md) | Invariant hasil root-cause |

## Inti terobosan

Tier 0 membangun APK asli di perangkat **tanpa Gradle dan tanpa JVM**:

```
.java → ecj → .class → dx → classes.dex ─┐
res/  → aapt2 compile/link ──────────────┼→ apk → align → apksigner v2 → terpasang
AndroidManifest.xml ─────────────────────┘
```

Toolchain 28 MB di dalam APK, bukan 4 GB SDK. Target: build < 60 detik di HP 2 GB.

## Status

**Tier 0 sudah terbukti menghasilkan APK asli dan terpasang-siap di dua tempat sekaligus**: CLI
host (JVM) dan **di dalam emulator Android 7.0 (API 24)** lewat `app_process`, tanpa Gradle dan
tanpa JVM.

| Bagian | Status | Bukti |
|---|---|---|
| Pipeline Tier 0 (9 langkah) | Selesai | host: `app-debug.apk` 12.566 byte · perangkat: 12.566 byte, 1,7 s, `apksigner verify` lolos |
| 3 template bawaan | Selesai | host 3/3 lulus · perangkat: `halo-dunia` 12.566 B, `kasir-warung` 16.662 B, `katalog-whatsapp` 12.566 B |
| Diagnostik Bahasa Indonesia | Selesai | 17 tes parser, `ErrorCatalog` |
| Desain + tema | Selesai | `ContrastTest` (WCAG AA di setiap pasangan teks) |
| Editor kode | Selesai (M1) | 9 tes `Highlighter`, `CodeEditorView` di atas `EditText` |
| Aplikasi Android | Terbangun | `app-debug.apk` 46,1 MB, `:app:assembleDebug` |
| Toolchain di HP | **Selesai (API 24)** | 14 uji instrumentasi hijau, detail di [`docs/05-toolchain-di-perangkat.md`](docs/05-toolchain-di-perangkat.md) |

63 tes unit + 14 uji perangkat hijau, `detekt` bersih (0 temuan).

## Menjalankan sendiri

Butuh JDK 17, Android SDK (build-tools 35, platform 35).

```bash
export JAVA_HOME=/opt/jdk17 PATH=$JAVA_HOME/bin:$PATH ANDROID_HOME=~/android-sdk

./gradlew :app:assembleDebug                 # APK aplikasi
./gradlew test detekt                        # 63 tes + analisis statis

./gradlew :build:hostcli:installDist
bash scripts/prove/build-hello.sh            # APK contoh + verifikasi tanda tangan
bash scripts/prove/build-templates.sh        # 3 template → 3 APK sah

bash scripts/toolchain/build-android-toolchain.sh   # aset toolchain untuk ART (butuh build-tools 28.0.3)
bash scripts/prove/build-di-perangkat.sh             # 14 uji di perangkat/emulator
```

## Batasan yang jujur

- **Tier 0 hanya Java 8**, tanpa Kotlin, tanpa ProGuard, dan **tanpa lambda** (dx belum mengenal
  `invokedynamic`; UI memberi saran anonymous class atau build cloud).
- **Android 10+ W^X** belum diuji; rencana `nativeLibraryDir`/`libsu` ada di `docs/05`.
- **Kunci tanda tangan** dibuat per perangkat (`filesDir/keys/`, ADR-019), jadi APK lama tidak bisa
  diperbarui setelah KodeNusa dihapus dan dipasang ulang. Impor keystore sendiri belum ada.
- **Tier 1 (cloud) dan Tier 2 (proot + Gradle)** masih rencana.
