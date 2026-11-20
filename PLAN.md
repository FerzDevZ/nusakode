# KodeNusa — IDE Android untuk Semua HP Indonesia

> **Satu kalimat:** IDE Android native yang bisa menghasilkan APK asli di HP 2 GB RAM / 32 GB storage, tanpa PC, tanpa Gradle wajib, tanpa internet wajib — berbahasa Indonesia penuh.

Status: **M1–M2 tercapai** — Tier 0 terbukti membangun APK di host *dan* di dalam emulator API 24
(13 uji perangkat hijau, 3 template terbangun di HP). Dokumen ini indeks; detail ada di `docs/`.

---

## Baca berurutan

| # | Dokumen | Isi |
|---|---|---|
| 0 | [`docs/00-visi-produk.md`](docs/00-visi-produk.md) | Masalah, persona, terobosan, diferensiasi, metrik, non-goals |
| 1 | [`docs/01-arsitektur-teknis.md`](docs/01-arsitektur-teknis.md) | Arsitektur 3-tier, modul, pipeline build, security, anggaran performa |
| 2 | [`docs/02-roadmap-milestone.md`](docs/02-roadmap-milestone.md) | M0–M7, exit criteria, perintah verifikasi Gate 1 |
| 3 | [`docs/03-adr.md`](docs/03-adr.md) | ADR-001..019: keputusan + alasan + konsekuensi |
| 4 | [`docs/04-backlog-agent-matrix.md`](docs/04-backlog-agent-matrix.md) | Tugas → agent `.agents/agents/` → skill `.agents/skills/` → DoD |
| 5 | [`DESIGN.md`](DESIGN.md) | Arah visual anti-slop, token OKLCH, tipografi, state |
| 6 | [`MEMORY.md`](MEMORY.md) | Invariant hasil root-cause (Gate 3), dibaca tiap sesi |

---

## Keputusan yang sudah dikunci

1. **Hybrid 3-tier build** (bukan cloud-only, bukan proot-only).
   - Tier 0 — nol-Gradle, on-device, Java murni, `aapt2 + ecj + dx + apksigner` (perataan dikerjakan `ZipAligner` di dalam proses). Terbukti: 12.566 byte dalam 1,7 detik di emulator API 24, footprint aset 28 MB. Lihat ADR-017.
   - Tier 1 — Gradle penuh di worker cloud, opsional, streaming log SSE. Untuk projek besar / Kotlin / dependency berat.
   - Tier 2 — proot/Termux (JDK + Gradle lokal) untuk power user di HP 4 GB+.
2. **Native Kotlin + Jetpack Compose**, `minSdk 24`, dua ABI (`arm64-v8a`, `armeabi-v7a`). Compose bukan WebView — editor harus 60 fps di HP kentang.
3. **Lokal-first**: folder projek di storage lokal adalah *source of truth*. Cloud, git, dan AI assistant hanya opsional dan tidak pernah jadi syarat untuk build.
4. **Java dulu, Kotlin belakangan.** Tier 0 mengompilasi Java (ECJ). Kotlin baru masuk Tier 1/2 karena butuh JVM — jangan bohongin user di marketing.

## Angka target yang mengikat

| Metrik | Target | Kenapa |
|---|---|---|
| Ukuran install APK | < 80 MB | Kuota & storage Indonesia |
| RAM idle aplikasi | < 120 MB | HP 2 GB harus bisa buka IDE + browser |
| Build template pertama | < 60 detik (Redmi 9A / 2 GB) | Sesi pertama harus sukses, bukan menyerah |
| Cold start editor | < 1.5 detik | Standar "langsung pakai" |
| Konversi onboarding→APK ter-install | ≥ 70% | Satu-satunya metrik produk yang jujur |
| Bahasa UI | 100% Indonesia + istilah teknis dipertahankan | Error harus bisa dipahami |

## Cara menjalankan rencana ini

```bash
# Gate 1 — setelah M0 selesai
./gradlew :app:assembleDebug :build:tier0:testDebugUnitTest
./gradlew detekt lintDebug
```

Setiap milestone **tidak boleh** ditandai selesai tanpa exit criteria dieksekusi dan exit code 0 (lihat `docs/02`).
