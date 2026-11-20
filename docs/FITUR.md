# Fitur KodeNusa — peta lengkap

Satu halaman untuk menjawab: **apa yang sudah bisa, apa yang belum, dan buktinya mana.**
Status hanya boleh "✅" kalau ada uji atau perintah yang membuktikannya.

Legenda: ✅ ada dan teruji · 🟡 ada, sebagian · ⛔ belum, sengaja ditunda

---

## 1. Projek

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Buat projek dari 4 template (Halo Dunia, Kasir Warung, Katalog WhatsApp, Web jadi APK) | ✅ | `TemplatesDeviceTest`, `build-templates.sh` (4/4 lulus) |
| Buat projek kosong dari nol | ✅ | `BlankProject`, `TemplateCatalogTest` |
| ID aplikasi otomatis dari nama | ✅ | `WizardScreen.efektifId`, `WizardUiTest` |
| Daftar projek + buka + hapus | ✅ | `WorkspaceScreen` |
| Ekspor projek ke `.zip` di lokasi pilihan (Unduhan/Drive/kartu SD) | ✅ | `ProjectArchiveTest` (6 uji) |
| Impor projek dari `.zip` milik sendiri | ✅ | `ProjectArchiveTest`, penolakan zip jahat ikut diuji |
| Pilih folder sembarang sebagai tempat projek | ⛔ | Android 11+ melarang aplikasi menulis ke folder bebas tanpa izin; build juga butuh folder yang boleh mengeksekusi biner. Jalan yang jujur: ekspor/impor zip (ADR-022) |
| Pindah lokasi penyimpanan seluruh aplikasi | ⛔ | ditunda; butuh SAF + salinan penuh |

## 2. Berkas

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Tambah berkas baru di folder aktif | ✅ | `ProjectFilesTest`, `FileOpsUiTest` |
| Tambah folder baru | ✅ | `ProjectFilesTest`, `FileOpsUiTest` |
| Ganti nama berkas/folder | ✅ | `ProjectFilesTest`, menu `⋯` di panel |
| Hapus berkas/folder beserta isinya | ✅ | `ProjectFilesTest` |
| Tolak nama berbahaya (`/`, `..`, nama sistem) | ✅ | `ProjectFilesTest` (12 uji) |
| Java baru langsung dapat `package` + `class` yang benar | ✅ | `ProjectFilesTest.kerangka_java_memuat_paket_dan_nama_kelas` |
| Panel berkas menampilkan HTML/CSS/JS/PY/SQL (bukan hanya Java) | ✅ | `ProjectTree.TEXT_EXTENSIONS` |
| Pindah berkas/folder ke folder lain ("Pindahkan…") | ✅ | `ProjectFiles.move` (5 uji) + `FileOpsUiTest.berkas_bisa_dipindah_ke_folder_lain` |
| Duplikat berkas/folder (`-salinan`, `-salinan-2`) | ✅ | `ProjectFiles.duplicate` + `FileOpsUiTest.berkas_bisa_diduplikasi_dari_menu` |
| Lokasi berkas/folder baru dipilih pengguna (bawaan: akar projek) | ✅ | `FileActionDialog.LokasiField` (`testTag("pilih-lokasi")`) |
| Berkas/folder baru tidak lagi "hilang" di dalam `src/main/java/…` | ✅ | uji memeriksa disk: `File(akar, "komponen").isDirectory` |
| Cari di seluruh projek (bukan hanya 1 berkas) | ⛔ | rencana |

## 3. Editor

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Editor kode dengan penyorotan sintaks | ✅ | `HighlighterTest` |
| Tab banyak berkas + penanda belum disimpan | ✅ | `EditorSession` (maks 8 tab untuk RAM) |
| Simpan manual + simpan otomatis | ✅ | `AutoSave`, `KeyBar` |
| Cari dalam berkas + lompat antar hasil | ✅ | `FindBar` |
| Undo / redo, geser kursor, sisip simbol | ✅ | `CodeEditorView`, `KeyBar` |
| Word-wrap (lipat baris) | ✅ | `FindBar` toggle |
| Nomor baris, posisi kursor | ✅ | `KeyBar` |
| Multi-kursor, lipat kode (code folding) | ⛔ | rencana |
| Format otomatis (prettier/google-java-format) | ⛔ | rencana |

## 4. Build APK (Tier 0 — tanpa Gradle, tanpa internet)

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Build APK di HP untuk projek Java | ✅ | `OnDeviceBuildTest`, 1,4–1,9 detik/template |
| Build projek web (HTML/CSS/JS) jadi APK | ✅ | `build-templates.sh` (4/4), `ExecPolicyDeviceTest` |
| Tanda tangan APK | ✅ | apksigner 0.8, v2-only |
| Kunci tanda tangan dibuat per pemasangan | ✅ | `DebugKeystoreTest`, ADR-019 |
| Jalan di Android 7 **dan** Android 14 | ✅ | 22 uji perangkat hijau di API 24 dan API 34 |
| Pesan galat bahasa Indonesia + saran perbaikan | ✅ | `ErrorCatalog` (26 entri), `SpecificDiagnosticTest` |
| Cache build (hanya bangun ulang yang berubah) | ✅ | `BuildCacheTest` |
| Laporan ukuran & waktu build | ✅ | `BuildScreen` |
| Kotlin di perangkat | ⛔ | butuh compiler Kotlin JVM (berat) → Tier 1/2 |
| Python di perangkat | ⛔ | butuh interpreter; tidak dijanjikan di UI |
| Build lewat Gradle di perangkat (proot + JDK) | ⛔ | Tier 2, untuk HP 4 GB+ |
| Build di cloud | ⛔ | Tier 1, butuh server + biaya |

## 4b. Asisten AI (BYOK — endpoint & kunci milik pengguna)

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Isi endpoint sendiri (mis. `https://api.crown.ai.id/v1`) + kunci sendiri | ✅ | `AiUiTest` — tombol terkunci sebelum diisi |
| Baca daftar model otomatis dari endpoint | ✅ | `OpenAiCompatClientTest.daftar_model_dibaca_dari_endpoint_pengguna` |
| Ganti model kapan saja | ✅ | Layar AI → Atur → pilih model |
| Jawaban mengalir (streaming) huruf demi huruf | ✅ | `aliran_sse_content_dan_reasoning_dirakit_benar` |
| `reasoning` tidak dicampur ke kode jawaban | ✅ | uji yang sama (`reasoning` dipisah) |
| Galat 401/404/429/5xx/jaringan dijelaskan dalam bahasa Indonesia | ✅ | 8 uji klien |
| Penyedia yang mengabaikan `stream` tetap terbaca | ✅ | `penyedia_yang_mengabaikan_stream_tetap_terbaca` |
| 503 sesaat dicoba ulang sekali | ✅ | `galat_503_dicoba_ulang_sekali_lalu_berhasil` |
| Jelaskan & perbaiki galat build dari layar Build | ✅ | tombol "Tanya AI" di `BuildScreen` |
| Perbaiki/ubah berkas aktif | ✅ | konteks berkas otomatis dikirim |
| Masukkan kode jawaban ke berkas aktif sekali tekan | ✅ | `CodeBlocks.terbesar` + tombol "Masukkan kode ke berkas" |
| Prompt mengunci batas projek (Java 8, tanpa Gradle/AndroidX/Kotlin) | ✅ | `PromptBuilderTest` |
| Model berjalan di dalam HP (offline penuh) | ⛔ | ditunda; butuh model 400–900 MB, lambat di HP 4 GB (ADR-023) |
| Kunci bawaan dari pembuat aplikasi / server perantara | ⛔ | sengaja tidak ada — aplikasi tidak menyimpan biaya AI |
| Enkripsi kunci dengan Android Keystore | ⛔ | rencana; sekarang di `SharedPreferences` privat aplikasi |
| Setelan endpoint/kunci bertahan setelah layar AI ditutup | ✅ | `AiUiTest.setelan_ai_tidak_hilang_setelah_layar_ditutup` (draf disimpan tiap ketikan) |
| Riwayat percakapan disimpan per projek | ✅ | `AiSettingsStore.simpanPercakapan` / `muatPercakapan` |
| Kode AI benar-benar masuk ke berkas (buffer + disk) | ✅ | `EditorScreen.ketikaSisip` → `session.update` + `session.save` + `editor.setContent` |

**Bukti ke endpoint sungguhan** (dijalankan terhadap endpoint milik pengguna):
model terbaca `zai-org/GLM-5.3-Flash`, `deepseek-ai/DeepSeek-V4-Flash-0731`, `MiniMaxAI/MiniMax-M2.7`;
jawaban streaming keluar sebagai blok `java`. Perintahnya:

```bash
AI_ENDPOINT=https://api.crown.ai.id/v1 AI_KEY=... AI_MODEL=... \
  ./gradlew :core:ai:test --tests '*ChatNyataTest*'
```

## 4c. Agen build otomatis (fondasi, belum ada layarnya)

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Baca balasan model menjadi banyak berkas (`<<<BERKAS: jalur>>>`) | ✅ | `UbahanBerkasTest` (6 uji) |
| Tolak jalur berbahaya dari model (absolut, `..`, `~`) | ✅ | `UbahanBerkas.rapikanJalur` + uji |
| Loop tugas → ubah berkas → build → galat dikirim balik ke model | ✅ | `AgenBuildTest` (5 uji) |
| Batas keras 3 putaran (jangan bakar kuota pengguna) | ✅ | `AgenBuildTest.berhenti_setelah_batas_putaran…` |
| Build tidak dijalankan bila tak ada berkas tertulis | ✅ | `AgenBuildTest.build_tidak_dijalankan_bila_semua_berkas_ditolak…` |
| Layar "Minta AI buatkan…" dengan log per putaran | ⛔ | pekerjaan berikutnya; mesinnya sudah siap |

## 5. Distribusi & keamanan

| Fitur | Status | Bukti / catatan |
|---|---|---|
| APK rilis publik (GitHub Releases) | ✅ | v0.3.0, HTTP 200 |
| Izin minimum (tanpa internet, tanpa penyimpanan luas) | ✅ | `AndroidManifest.xml` |
| Tidak mengeksekusi perintah lewat shell | ✅ | `ProcessRunnerTest`, semua `ProcessBuilder(List)` |
| Verifikasi SHA-256 toolchain sebelum dijalankan | ✅ | `ToolchainInstaller` |
| Path projek tidak bisa keluar folder | ✅ | `ProjectFilesTest`, `ProjectArchiveTest` |
| Kunci tanda tangan milik pengguna (impor/ekspor `.jks`) | ⛔ | rencana P3 |
| F-Droid / Play Store | ⛔ | rencana milestone |

## 6. Kualitas

| Fitur | Status | Bukti |
|---|---|---|
| Uji unit | ✅ | 167 uji (`./gradlew test`) |
| Uji di perangkat | ✅ | 29 uji, hijau di Android 7 (API 24) **dan** Android 14 (API 34) |
| Uji perangkat (UI + build + kebijakan exec) | ✅ | 26 uji, API 24 & API 34 |
| Analisis statis detekt, 0 temuan | ✅ | `./gradlew detekt` |
| CI menjalankan gerbang yang sama | ✅ | `.github/workflows/ci.yml` |

---

## Urutan pekerjaan berikutnya (disepakati)

1. **Pindahkan berkas** antar folder + duplikat (paling sering dipakai setelah tambah berkas).
2. **Cari di seluruh projek** — pengganti "grep" yang tidak ada di HP.
3. **Layar agen** "Minta AI buatkan…" di atas `AgenBuild` (mesin + uji sudah ada).
4. **Perkecil APK** 44,7 MB → di bawah 15 MB (potong `android.jar`).
4. **Uji di HP asli 4 GB** (Redmi 9A): RAM diam, waktu build, pemasangan.
5. **Kunci tanda tangan milik pengguna** supaya APK bisa di-update di Play.
6. **Format otomatis** untuk Java/HTML/CSS/JS.
7. **Enkripsi kunci AI** dengan Android Keystore + uji "kunci tidak bocor ke log".
