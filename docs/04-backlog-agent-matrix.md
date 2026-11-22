# 04 · Agen, Skill, dan Matriks Kerja

Proyek ini dibangun dengan disiplin *swarm*: 113 persona di `.agents/agents/`, 611 skill modular di `.agents/skills/`, dan 23 aturan di `.agents/rules/`. Skill dimuat **hanya saat dibutuhkan** (JIT), 1–3 per langkah, supaya konteks tidak penuh oleh hal yang tidak dipakai.

Dokumen ini mencatat **siapa mengerjakan apa** dan **bukti seperti apa yang dituntut** — bukan daftar lengkap 611 skill.

**Status dokumen:** terakhir diselaraskan dengan rilis **v0.22.0**.

---

## 1. Triad yang dipakai per domain

| Domain | Arsitek | Pembangun | Pemeriksa |
|---|---|---|---|
| Pipeline build (Tier 0) | `@architect` | `@systems-rust-cpp-engineer` (pola proses/native), `@backend-developer` | `@code-reviewer`, `@swe-healer` |
| UI Compose | `@design-director`, `@design-architect` | `@shadcn-craftsman`, `@frontend-developer` | `@antislop-auditor`, `@a11y-contrast-enforcer` |
| AI & agen | `@ai-agent-systems-engineer` | `@ml-ai-architect` | `@rag-pipeline-reviewer`, `@mle-reviewer` |
| Git & GitHub | `@distributed-systems-architect` | `@backend-developer` | `@security-reviewer` |
| Keamanan rahasia | `@cybersecurity-redteam-pro` | `@security-engineer` | `@penetration-tester` |
| Verifikasi rilis | `@qa-automation-architect` | `@test-automator` | `@e2e-runner`, `@code-reviewer` |
| Dokumentasi | `@adr-tech-documentation` | `@documentation-engineer` | `@copy-editor` |

## 2. Skill yang benar-benar dipakai (dan kapan)

| Skill | Dipakai untuk |
|---|---|
| `android-clean-architecture` | Batas modul `:core:*` vs `:app`, arah ketergantungan |
| `mobile-android-compose-master` | Layar, state, `LaunchedEffect`, testTag |
| `clean-code-anti-slop` | Menolak placeholder, `TODO`, nama generik |
| `antislop-ui` + `antislop-human` | Kontras, target sentuh 48dp, keadaan tombol mati |
| `accessibility` (WCAG 2.2 AA) | `contentDescription`, focus, teks tombol yang bisa dibaca pembaca layar |
| `systems-rust-tokio-production` (pola) | Disiplin proses: timeout, output dibatasi, tanpa `sh -c` |
| `distributed-systems-scale-2026` | Idempotensi unggah rilis, backoff, pemetaan galat HTTP |
| `cybersecurity-appsec-redteam-2026` | Penolakan jalur berbahaya dari model AI, tanpa secret di log |
| `jwt-session-security-passkeys` (pola rahasia) | Penyimpanan token/kunci dengan Android Keystore, bukan SharedPreferences polos |
| `ai-agent-mcp-orchestration-2026` + `rag-llm-integrator` | Konteks otomatis v0.21.0 + tulis berkas otomatis di chat v0.22.0: memilih leksikal-deterministik, bukan embeddings, karena target offline 2 GB |
| `verification-loop` | Gerbang 6 fase sebelum rilis |
| `adr-tech-documentation` | 41 ADR dengan konteks–keputusan–konsekuensi |
| `memory-keeper` | `MEMORY.md` sebagai memori akar masalah |

## 3. Backlog per milestone

| Milestone | Status | Isi ringkas |
|---|---|---|
| **M0 — Fondasi** | ✅ | Proyek Gradle multi-modul, JDK 17, SDK 35, `detekt.yml` (maxIssues 0), CI `test detekt assembleDebug` |
| **M1 — Editor** | ✅ | `CodeEditorView` di atas `EditText`, gutter (diperombak total di v0.21.0), penyorotan hanya viewport, tab ganda, cari dalam berkas, cari se-projek, ganti semua, simpan otomatis 900 ms |
| **M2 — Build engine** | ✅ (paling kritis) | 9 langkah pipeline; 9 akar masalah diselesaikan dan dicatat (ECJ bootclasspath, W^X, dx relokasi, apksigner 0.8, keystore per perangkat, alignment Kotlin). Bukti: 9 template APK terbangun di API 24 dan 35 |
| **M3 — Onboarding & template** | ✅ | 10 template. Wizard menanyakan hanya yang perlu; `Jenis` wajib cocok dengan `jenis` di `templates/index.json` |
| **M4 — Belajar & diagnosa** | ✅ | `DiagnosticParser` + `ErrorCatalog` (26 entri) mengubah keluaran alat menjadi "judul · berkas · baris · cara perbaiki". Semua galat yang dikirim ke AI melewati katalog ini. **Mode belajar** (v0.21.0): pelajaran langkah-demi-langkah per template |
| **M5 — Cloud** | ⛔ sengaja | Tier 1 (worker Gradle) dan Tier 2 (proot/JDK) **tidak dibangun** — ADR-001 dan ADR-007: server berarti data pengguna berpindah dan janji "tanpa server" gugur. Konsekuensinya projek Gradle tidak bisa dibuild, dan itu dikatakan di UI |
| **M6 — Kolaborasi** | ✅ | Git nyata di HP (JGit 3.7.1), commit, kirim ke GitHub, riwayat, terbit situs ke `gh-pages`, klon dari GitHub (`KlonProjekDialog`, menolak alamat bukan-HTTPS dengan kalimat yang jelas) |
| **M7 — Rilis** | ✅ | v0.1.0 → v0.22.0 di GitHub Releases, APK arm-only ditandatangani kunci perangkat/pengguna, `-PsemuaAbi` untuk emulator, setiap rilis dipasang dan dibuka sebelum dibagikan |

## 4. Gate yang tidak boleh dilewati

| Gate | Perintah | Ambang |
|---|---|---|
| 1 · Kompilasi & tipe | `./gradlew test detekt` | 0 gagal, 0 temuan |
| 2 · Perilaku nyata | `./gradlew :app:connectedDebugAndroidTest` | 142/142 hijau |
| 3 · Pipeline tanpa Gradle | `scripts/prove/build-templates.sh` | semua template APK sah, `apksigner verify` lulus |
| 4 · Anti-slop & keamanan | tinjauan manual + `grep` rahasia | tidak ada `TODO`, `any`, jalur model tanpa validasi, token di disk |
| 5 · Rilis | pasang APK minified, buka, tekan satu alur | aplikasi hidup, tanpa crash |
| 6 · Memori | `MEMORY.md` | setiap akar masalah baru jadi invariant |

## 5. Aturan kerja yang lahir dari kegagalan nyata

1. **Baca dulu, baru ubah.** Setiap klaim "fitur X belum ada" harus dibuktikan dengan membaca berkasnya — pernah ada kesimpulan salah karena satu `grep` huruf kecil.
2. **Perbaikan satu tempat bukan pola.** ANR pemilih berkas muncul tiga kali di tiga layar karena perbaikan pertama hanya menyentuh satu berkas.
3. **Uji yang cocok teks harus memakai pohon tak tergabung** (`useUnmergedTree = true`) bila elemen berada di dalam baris yang bisa diklik.
4. **Jangan menyimpulkan dari "BUILD SUCCESSFUL".** Rilis diverifikasi dengan memasang dan membukanya.
5. **Heredoc shell tidak dipakai** untuk mengubah repo; pakai editor berkas agar diff terbaca.
6. **Uji perangkat yang gagal melampirkan bukti** (paket fokus + dump uiautomator) di pesan asersinya, bukan ditebak-ulang — lahir dari flake `PasangApkDeviceTest` (v0.21.0).
