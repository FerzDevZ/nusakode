# Rencana Induk 2026 — KodeNusa

> Dokumen ini adalah **rencana kerja yang bisa dieksekusi dingin**: setiap langkah punya berkas yang disentuh, perintah pembuktian, dan kriteria lulus. Tidak ada langkah yang berbunyi "tingkatkan kualitas" tanpa angka.
> Status terakhir: **186 uji unit · 36 uji perangkat (API 24) · APK 45,8 MB · Tier 0 build 1,4–1,9 s.**
> **Sesi 1 (S1) selesai** — layar Situs hidup: susun, ringkasan, buka di browser, bagikan zip.

---

## 1. Prinsip yang tidak boleh dilanggar

1. **Offline dulu.** Semua fitur inti (edit, build APK, susun situs, git commit) jalan tanpa internet. Jaringan hanya untuk AI (BYOK) dan publikasi.
2. **Tanpa server KodeNusa.** Tidak ada kunci bawaan, tidak ada perantara, tidak ada biaya yang kita tanggung.
3. **Tier 0 = Java saja.** Tanpa Gradle, tanpa AndroidX, tanpa Kotlin di kode pengguna. Fitur yang melanggar ini tidak boleh dipromosikan di UI.
4. **Jujur di UI.** Fitur yang belum ada ditulis ⛔ di `docs/FITUR.md`, bukan disembunyikan dalam tombol mati tanpa alasan.
5. **Bukti lebih kuat dari klaim.** Setiap baris ✅ wajib punya uji atau perintah yang bisa dijalankan ulang.
6. **HP 4 GB adalah target, bukan pengecualian.** Setiap fitur ditimbang dari RAM, ukuran APK, dan waktu respons.

---

## 2. Ukuran keberhasilan (baseline → target)

| Ukuran | Sekarang | Target v0.7 | Target v1.0 |
|---|---|---|---|
| Ukuran APK | 45,8 MB | ≤ 30 MB | ≤ 18 MB |
| RAM saat menganggur | belum diukur di HP asli | ≤ 120 MB | ≤ 100 MB |
| Waktu buka projek → editor | ≈ 0,4 s (emulator) | ≤ 1,5 s | ≤ 1,0 s |
| Build APK template pertama | 1,4–1,9 s | ≤ 10 s | ≤ 10 s |
| Susun situs (100 berkas) | belum ada UI | ≤ 3 s | ≤ 3 s |
| Tugas template lewat agen AI | belum ada UI | ≥ 70% selesai tanpa sentuh | ≥ 85% |
| Uji unit / perangkat | 186 / 34 | ≥ 230 / ≥ 45 | ≥ 300 / ≥ 60 |
| Pangsa HP terpasang (Android 7+) | 100% fitur | tetap 100% | tetap 100% |

---

## 3. Fase & langkah

Setiap langkah dirancang **satu sesi kerja** (≤ 1 PR). Kolom *Paralel* menandai langkah yang boleh dikerjakan bersamaan.

### FASE 1 — Situs murni jadi pengalaman utuh (prioritas tertinggi)

| # | Langkah | Berkas utama | Kriteria lulus | Paralel |
|---|---|---|---|---|
| S1 ✅ | **Layar "Situs"**: pratinjau, susun, bagikan zip | `ui/SitusScreen.kt`, `KodeNusaApp.kt`, `BuildScreen.kt` | `:app:connectedDebugAndroidTest` uji `SitusUiTest` hijau di API 24 & 34 | — |
| S2 | **Terbit ke GitHub Pages** dari layar Situs + tampilkan tautan & tombol salin | `SitusScreen.kt`, `GitSettingsStore.kt` | uji UI: tombol terbit terkunci tanpa token; dengan token uji palsu → pesan galat jelas | G1 |
| S3 | **PWA otomatis**: `manifest.json` + service worker ditulis saat susun (opsional, satu sakelar) | `core/build/SitusStatis.kt` (fungsi `tambahkanPwa`) | uji JVM: `index.html` diberi `<link rel=manifest>`, `sw.js` ada, idempoten | — |
| S4 | **Server lokal di HP** (`http://192.168.x.x:8080`) untuk dibuka keluarga via Wi-Fi | `core/build/ServerLokal.kt` (JVM: `com.sun.net.httpserver`), `SitusScreen.kt` | uji JVM: server menyajikan `index.html` & MIME benar; tombol mati saat tak ada Wi-Fi | S3 |
| S5 | **Pratinjau di dalam app** (WebView aset lokal, tanpa izin jaringan) | `ui/SitusPreview.kt` | uji UI: pratinjau tampil, `file://` tidak bisa keluar folder | S1 |

### FASE 2 — GitHub sebagai repo (bukan sekadar unggah)

| # | Langkah | Berkas utama | Kriteria lulus | Paralel |
|---|---|---|---|---|
| G1 | **Penyimpanan setelan git** (alamat repo + token, terpisah dari projek) | `GitSettingsStore.kt` | uji JVM/Android: tersimpan, bisa dihapus, tidak masuk log | — |
| G2 | **Layar GitHub**: commit, push, riwayat, status perubahan | `ui/GitScreen.kt` | uji UI: tanpa repo → tombol push mati dengan alasan; setelah commit → riwayat bertambah | G1, S2 |
| G3 | **Buat repo otomatis** lewat REST (`POST /user/repos`, plus `PUT /repos/{o}/{r}/contents/.gitkeep` bila perlu) | `core/git/GitHubRest.kt` | uji JVM dengan `HttpServer` tiruan: 201 → repo terbuat; 422 → "repo sudah ada, pakai alamat itu" | G1 |
| G4 | **Bagikan APK**: unggah ke Releases (`POST /releases`, `uploads.github.com`) dan salin tautan | `GitHubRest.kt`, `BuildScreen.kt` | uji JVM: dua langkah request, tautan `releases/download/...` dirakit benar | G3 |
| G5 | **Buka projek dari GitHub** (klon ke workspace lewat `RepoGit.klon`) | `ProjectStore.kt`, `ui/GitScreen.kt` | uji perangkat: klon remote lokal → projek muncul di daftar & bisa dibuild | G2 |

### FASE 3 — Agen AI multi-berkas (terobosan utama)

| # | Langkah | Berkas utama | Kriteria lulus | Paralel |
|---|---|---|---|---|
| A1 | **`PapanTulisProjek` nyata**: ringkasan struktur (≤ 2 KB) + tulis/hapus berkas lewat `ProjectFiles` | `app/AiProjectBoard.kt` | uji unit: ringkasan memuat jalur penting, tulis menolak jalur berbahaya | — |
| A2 | **`GerbangBuild` nyata** di atas `Tier0BuildEngine` yang mengembalikan galat `DiagnosticParser` | `app/AiBuildGate.kt` | uji perangkat: tugas "tambah tombol" pada template `halo-dunia` → APK jadi ≤ 3 putaran | A1 |
| A3 | **Layar Agen**: kotak tugas, tombol Hentikan, log per putaran, hasil APK | `ui/AgenScreen.kt` | uji UI: tanpa kunci AI → tombol mati; putaran tampil berurutan | A2 |
| A4 | **Batas biaya & waktu**: maks 3 putaran, batas token per tugas, timeout total 180 s, tombol berhenti benar-benar membatalkan | `core/ai/AgenBuild.kt` | uji: pembatalan menghentikan loop sebelum putaran 2 | A3 |
| A5 | **Evaluasi**: 10 tugas template terdokumentasi + hasil lulus/gagal dicatat | `docs/07-eval-agen.md` | ≥ 7/10 tugas selesai tanpa sentuh; waktu median ≤ 2 menit | A2–A4 |

### FASE 4 — Keamanan & ukuran

| # | Langkah | Berkas utama | Kriteria lulus | Paralel |
|---|---|---|---|---|
| K1 | **Kunci AI & token GitHub dienkripsi Android Keystore** (AES-GCM, kunci tak keluar dari Keystore) | `app/RahasiaStore.kt`, migrasi dari `SharedPreferences` | uji perangkat: nilai di `SharedPreferences` bukan teks biasa; migrasi lama→baru tidak kehilangan kunci | G1 |
| K2 | **APK ≤ 30 MB**: `abiFilters` (arm64 + armeabi-v7a saja), `android.jar` dipangkas jadi `android-lite.jar` saat build rilis | `scripts/toolchain/build-android-toolchain.sh`, `app/build.gradle.kts` | `apksigner verify` pada APK hasil; `llvm-strip`/`zipinfo` menunjukkan pengurangan; uji build perangkat tetap hijau | — |
| K3 | **Tidak ada rahasia di log**: audit `Log.*`, `println`, dan pesan galat | grep + uji | `grep -rn "kunci\\|token" app/src/main | grep Log` kosong | K1 |

### FASE 5 — Produktivitas editor (setelah fondasi stabil)

| # | Langkah | Kriteria lulus |
|---|---|---|
| E1 | Cari & ganti **seluruh projek** (bukan hanya berkas aktif), hasil bisa diklik | uji unit pada `ProjectSearch` + uji UI 1 skenario |
| E2 | Command palette (`Ctrl/⌘+K` analog: tombol cari di bilah atas) | uji UI: mengetik "build" memunculkan aksi Build APK |
| E3 | Tab multi-berkas (buka 3 berkas, pindah tanpa kehilangan posisi kursor) | uji UI: posisi kursor & scroll tiap tab dipertahankan |

### FASE 6 — Bukti nyata di HP 4 GB

| # | Langkah | Kriteria lulus |
|---|---|---|
| R1 | Ukur di Redmi 9A: RAM menganggur, waktu buka projek, build template, build proyek 20 berkas | angka dicatat di `docs/08-ukur-hp-asli.md` dengan cara ukur yang bisa diulang |
| R2 | Uji "tanpa internet 24 jam": semua alur inti lolos | daftar periksa ditandatangani |
| R3 | Uji baterai & suhu saat 10 build berturut-turut | tidak ada `thermal throttling` yang membuat build > 2× lebih lambat |

---

## 4. Graf ketergantungan (jalur kritis)

```
S1 → S2 → (G2) → G4
S1 → S5
S3 ─┐
S4 ─┴→ S2
G1 → G2 → G5
G1 → G3
A1 → A2 → A3 → A4 → A5
K1 (bisa kapan saja setelah G1)
K2, K3, E1–E3, R1–R3: paralel, tidak memblokir
```

**Jalur kritis menuju "terobosan" yang bisa dipamerkan:** `S1 → S2 → A1 → A2 → A3`.
Artinya: layar Situs dulu (cepat terlihat), lalu penerbitan, lalu agen AI yang benar-benar membuat APK.

---

## 5. Risiko & mitigasi

| Risiko | Dampak | Mitigasi yang sudah disiapkan |
|---|---|---|
| JGit 3.7.1 (2015) kurang mendukung protokol baru | push ke repo besar bisa lambat | Uji nyata sebelum rilis; bila gagal, sisa jalur tetap jalan lewat REST |
| GitHub REST rate limit | tombol gagal sesaat | Token pengguna = 5.000/jam; tampilkan pesan 403 dengan sisa waktu |
| Agen AI memakan kuota pengguna | pengguna marah | Batas 3 putaran, batas token, tombol Hentikan, perkiraan biaya sebelum mulai |
| APK tetap besar karena `android.jar` | sulit dipasang di HP 32 GB | K2: `android-lite.jar` dipangkas + `abiFilters` |
| Token bocor di log/crash | kehilangan akun | K1/K3: enkripsi Keystore + audit log |
| Regresi UI saat menambah layar | kepercayaan turun | Setiap langkah wajib menambah uji perangkat, gate dijalankan di API 24 **dan** 34 |

---

## 6. Definisi selesai (setiap rilis)

```bash
./gradlew clean test detekt :app:assembleDebug          # 0 gagal, detekt bersih
./gradlew :app:connectedDebugAndroidTest                # hijau di kodenusa24 & kodenusa34
bash scripts/prove/build-hello.sh                       # APK host tetap sah
bash scripts/prove/build-templates.sh                   # 3 template tetap jalan
```

Rilis hanya boleh naik kalau: `docs/FITUR.md` diperbarui, `MEMORY.md` mendapat invariant baru bila ada akar masalah, dan tautan unduhan diuji dengan `curl -I` (HTTP 200).

---

## 7. Urutan sesi yang saya kerjakan mulai sekarang

1. **Sesi 1 (sekarang):** S1 — layar Situs (susun, ringkasan berkas, bagikan zip) + uji perangkat.
2. **Sesi 2:** S2 + G1 — terbit ke GitHub Pages, setelan repo/token.
3. **Sesi 3:** A1 + A2 — papan tulis projek & gerbang build nyata untuk agen.
4. **Sesi 4:** A3 + A4 — layar Agen dengan batas biaya & tombol berhenti.
5. **Sesi 5:** A5 + rilis v0.7.0 (situss + git + agen) dengan tautan unduhan.
6. **Sesi 6:** K1 + K2 — Keystore & pemangkasan APK ≤ 30 MB.
