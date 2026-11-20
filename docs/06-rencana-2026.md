# Rencana induk 2026 — status nyata

Dokumen ini dulu berisi target v0.7/v1.0. Sekarang isinya **apa yang benar-benar terjadi**, karena rencana yang tidak diperbarui lebih berbahaya daripada tidak ada rencana: ia membuat orang (termasuk saya) mengira sesuatu sudah selesai.

Baseline hari ini: **0.9.6**, 252 uji unit, 77 uji perangkat, APK 32,3 MB.

## 1. Prinsip yang tidak boleh dilanggar

1. **Tanpa server milik proyek.** AI dan GitHub memakai kredensial pengguna.
2. **Offline adalah fitur.** Yang tidak bisa dipakai tanpa jaringan harus dikatakan begitu di layar.
3. **Tidak ada janji di UI tanpa kode di belakangnya.** Bila belum ada, tulis batasnya.
4. **Berkas pengguna tidak hilang.** Simpan otomatis, berkas di disk sebagai sumber kebenaran.
5. **Rilis diverifikasi dengan memasangnya**, bukan hanya dengan melihat "BUILD SUCCESSFUL".

## 2. Ukuran keberhasilan: target lama vs kenyataan

| Ukuran | Target lama | Kenyataan 0.9.6 | Catatan |
|---|---|---|---|
| Ukuran APK | ≤ 30 MB | **32,3 MB** | Target itu ditolak sendiri oleh ADR-028: `android.jar` adalah classpath kompilasi. Memangkasnya membuat kode sah gagal dikompilasi |
| RAM menganggur | ≤ 120 MB | **67 MB** (emulator) | HP fisik belum diukur |
| Buka projek → editor | ≤ 1,5 s | ≈ 0,4 s (emulator) | |
| Build template pertama | ≤ 10 s | **1,4–1,9 s** | |
| Susun situs | ≤ 3 s | belum diukur terpisah | |
| Tugas agen selesai tanpa sentuh | ≥ 85% | **belum ada angka** | Harness ada (`AI_EVAL=1`), angka kosong |
| Uji unit / perangkat | ≥ 300 / ≥ 60 | **252 / 77** | Uji perangkat melewati target |
| Android 7+ | 100% fitur | 100% | Diuji di API 24, 34, 35 |

## 3. Status tiap langkah rencana lama

### Fase 1 — Situs murni

| # | Langkah | Status | Bukti |
|---|---|---|---|
| S1 | Layar Situs (susun, ringkasan, bagikan zip) | ✅ | `SitusScreen.kt`, `SitusUiTest` |
| S2 | Terbit ke GitHub Pages | ✅ | `PagesTest`, panel terbit di layar Situs |
| S3 | PWA otomatis (manifest + service worker) | ✅ | `SitusPwaTest` 10 uji, `siapkan-pwa` |
| S4 | Server lokal di HP via Wi-Fi | ❌ **ditolak** | ADR-030: socket/port/lifecycle rapuh di HP 2 GB; diganti pratinjau `file://` (S5) |
| S5 | Pratinjau di dalam aplikasi | ✅ | `PratinjauUiTest` |

### Fase 2 — GitHub sebagai repo

| # | Langkah | Status | Bukti |
|---|---|---|---|
| G1 | Setelan repo + token terpisah dari projek | ✅ | `GitSettingsStore`, tersandi Keystore |
| G2 | Layar GitHub: commit, kirim, riwayat | ✅ | `GitUiTest`, `GitDeviceTest` (JGit nyata) |
| G3 | Buat repo otomatis (REST) | ⚠️ fungsi ada, **belum tersambung UI** | `GitHubRilis.buatRepo` diuji, tidak ada tombolnya |
| G4 | Bagikan APK ke Releases | ✅ | `BagikanApkCard`, uji jaringan nyata dengan `GH_TOKEN` |
| G5 | Buka projek dari GitHub (klon) | ⚠️ `RepoGit.klon` ada & teruji, **belum tersambung UI** | — |

### Fase 3 — Agen AI

| # | Langkah | Status |
|---|---|---|
| A1 | Papan tulis projek (ringkasan + tulis/hapus aman) | ✅ `AiProjectBoard` |
| A2 | Gerbang build di atas Tier 0 | ✅ `AiBuildGate`, `AiBuildGateTest` |
| A3 | Layar Agen (tugas, hentikan, log, hasil) | ✅ `AgenScreen`, `AgenUiTest` |
| A4 | Batas 3 putaran, 12 berkas, timeout, pembatalan | ✅ `AgenBuild`, `EvalAgenJalurTest` |
| A5 | Evaluasi 10 tugas | ⚠️ harness siap, **angka belum ada** (butuh `AI_EVAL=1` + kunci) |

### Fase 4 — Keamanan & ukuran

| # | Langkah | Status |
|---|---|---|
| K1 | Rahasia tersandi Android Keystore | ✅ `Penyandi`/`KunciAman`, 7 uji perangkat, ADR-026 |
| K2 | APK ≤ 30 MB | ❌ **dibatalkan dengan alasan** — ADR-028 menjelaskan byte-per-byte bahwa 32 MB adalah batas wajar selama `android.jar` dibundel |
| K3 | Tidak ada rahasia di log | ✅ tidak ada `Log` yang mencetak kunci/token |

### Fase 5 — Produktivitas editor

| # | Langkah | Status |
|---|---|---|
| E1 | Cari & ganti seluruh projek | ✅ `ProjectSearch` + `GantiSemuaUiTest` |
| E2 | Palet perintah | ✅ `PaletPerintah`, 4 uji UI (ADR-031) |
| E3 | Tab multi-berkas | ✅ `EditorSession` (maks 6 tab) |

### Fase 6 — Bukti di HP nyata

| # | Langkah | Status |
|---|---|---|
| R1 | Ukur di HP 4 GB fisik | ⚠️ skrip `scripts/ukur-hp.sh` jalan; baru baris **emulator** tercatat |
| R2 | Uji 24 jam tanpa internet | ❌ belum |
| R3 | Suhu & throttling 10 build | ❌ belum |

## 4. Yang berikutnya, berurutan

```
1. Hapus janji palsu "build di server"      ← kecil, menutup kebohongan di UI
2. Penangkap crash + bagikan laporan        ← satu-satunya cara tahu apa yang rusak di HP orang
3. G5: klon projek dari GitHub              ← kode inti sudah ada, tinggal UI + uji nyata
4. Catatan "Kotlin belum didukung"          ← kecil, sejajar dengan catatan Python
5. G3: tombol "buat repo"                   ← fungsi REST sudah ada
6. Ukur di HP fisik + AI_EVAL               ← butuh perangkat & kunci pengguna
```

Jalur kritis untuk "bisa dipercaya orang lain" adalah **1 → 2 → 3**. Kotlin dan Tier 1 tetap ditunda (ADR-003, ADR-001) sampai ada bukti bahwa pengguna benar-benar membutuhkannya dan tidak sekadar menginginkannya.

## 5. Definisi selesai untuk setiap rilis

```bash
./gradlew clean test detekt :app:assembleDebug        # 0 gagal, detekt 0 temuan
./gradlew :app:connectedDebugAndroidTest              # 77 uji hijau
./gradlew :app:assembleRelease -PsemuaAbi             # dipasang & dibuka di emulator
bash scripts/prove/build-hello.sh                     # pipeline host tetap sah
```

Rilis hanya naik bila: `docs/FITUR.md` diperbarui, `MEMORY.md` mendapat invariant baru, unduhan diuji dengan `curl` (HTTP 200 + sha256 sama), dan `.git/config` bersih dari token.

## 6. Risiko yang masih terbuka

| Risiko | Dampak | Mitigasi hari ini |
|---|---|---|
| JGit 3.7.1 (2015) | push repo besar bisa lambat | Uji nyata di perangkat sebelum rilis |
| Token GitHub bocor lewat log/crash | akun hilang | Token tidak pernah ditulis ke disk; laporan crash (rencana #2) harus menyaringnya |
| Agen AI menghabiskan kuota pengguna | pengguna marah | 3 putaran, 12 berkas, tombol Hentikan |
| Blocker Android 13+ | aplikasi tidak bisa dipasang | Tidak ada `MANAGE_EXTERNAL_STORAGE`; diuji di API 35 |
| Regresi saat menambah layar | kepercayaan turun | Setiap fitur wajib uji perangkat; tag uji didokumentasikan di FITUR.md |