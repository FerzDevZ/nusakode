# Fitur KodeNusa — peta lengkap

Satu halaman untuk menjawab: **apa yang sudah bisa, apa yang belum, dan buktinya mana.**
Status hanya boleh "✅" kalau ada uji atau perintah yang membuktikannya.

**Rilis terakhir: 0.9.8** (`versionCode 20`) · **236 uji unit · 84 uji perangkat · detekt 0 temuan**

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
| Word-wrap (lipat baris) | ✅ | `FindBar` toggle; **semua baris tetap terlihat** walau wrap mati (`EditorBarisDeviceTest`) |
| Nomor baris, posisi kursor | ✅ | `KeyBar` |
| **Cari di seluruh projek** (bukan cuma berkas aktif) | ✅ | `ProjectSearch` + `ProjectSearchTest` (5 uji); panel hasil `jalur:baris` bisa diketuk |
| **Ambil berkas dari HP** (Unduhan, Drive, kartu SD, foto) | ✅ | tombol `ambil-dari-hp` → pemilih berkas Android (SAF) → `BerkasMasuk.simpan`; masuk ke folder berkas yang sedang dibuka; batas 24 MB; nama bentrok jadi ` (2)`; nama berbahaya **ditolak** dengan alasan (`BerkasMasukTest`, `BerkasHpUiTest`) |
| **Bagikan berkas keluar** (WhatsApp/Drive/Gmail) | ✅ | menu `⋯` → "Bagikan berkas…" lewat `FileProvider` (`file_paths.xml` hanya membuka `projek/` + `cache/`) |
| **Tanpa izin penyimpanan sama sekali** | ✅ | tidak ada `READ/WRITE_EXTERNAL_STORAGE` aktif maupun `MANAGE_EXTERNAL_STORAGE`; `<queries>` mendeklarasikan pemilih berkas agar bisa di-resolve di Android 11+ |
| **Palet perintah** | ✅ | Tombol **Aksi** di editor: ketik "apk", "bangun", "cari", "impor" untuk menjalankan aksi tanpa mencari tombolnya. Pencarian memakai semua kata (AND), dan projek web murni tidak pernah menawarkan Build APK. |
| **Progres unggahan APK** | ✅ | Kartu Bagikan APK menampilkan "Mengunggah 42% (13,4 MB dari 32,3 MB)" — dikirim per potongan 64 KB, bukan sekali kirim yang tampak menggantung. |
| **Catatan jujur soal Python** | ✅ | Membuka berkas `.py` menampilkan: *"Python belum bisa dijalankan atau dibuild di KodeNusa. Berkas tetap bisa disunting dan dikirim ke GitHub."* |
| **Pratinjau situs di dalam aplikasi** | ✅ | Tombol **Pratinjau** di editor memuat `index.html` projek lewat WebView — sunting lalu lihat tanpa keluar aplikasi, tanpa internet. Batas `file://` disebutkan di layar. |
| **Bagikan APK lewat GitHub Release** | ✅ | Setelah APK jadi, kartu **Bagikan APK** mengunggahnya ke rilis repo Anda dan menampilkan tautan unduh. Ukuran (32 MB) dan tujuan ditampilkan sebelum tombol ditekan. |
| **Foto langsung ke AI** | ✅ | tombol `foto-kamera` memakai kamera HP (hasil disimpan sementara lewat FileProvider), jadi layar galat bisa difoto, bukan dicari dulu di galeri |
| **Model tanpa dukungan gambar dijelaskan** | ✅ | 400 yang menyebut `image_url` diterjemahkan menjadi "Model yang dipilih tidak bisa membaca gambar" + saran model yang mendukung, bukan pesan mentah penyedia |
| **Ganti semua di seluruh projek** | ✅ | kolom `isi-pengganti` + tombol `ganti-semua`; peka huruf besar/kecil, berkas tanpa kecocokan tidak disentuh, hasil dilaporkan ("3 potongan di 2 berkas"), berkas aktif dimuat ulang |
| **Impor .zip tidak lagi ANR** | ✅ | peluncur pemilih berkas dipindah ke `LaunchedEffect` — di Android 11+ peluncuran intent di tengah pembongkaran koneksi aksesibilitas membuat main thread menunggu Binder |
| **Uji jalur agen tanpa jaringan** | ✅ | `EvalAgenJalurTest`: 10 tugas evaluasi dijalankan lewat loop `AgenBuild` dengan model tiruan, termasuk penolakan jalur `../` |
| **Kunci AI & token GitHub tersandi (Android Keystore)** | ✅ | `KunciAman` AES-256-GCM, format `enc1:`; migrasi otomatis dari teks biasa; 7 uji perangkat `KunciAmanDeviceTest` memeriksa isi `shared_prefs` |
| **Situs murni bisa dipasang & dibuka tanpa internet (PWA)** | ✅ | tombol `siapkan-pwa` → `manifest.json` + `service-worker.js` + pendaftar di `index.html`; 9 uji `SitusPwaTest` + 1 uji layar |
| **Kabar hasil aksi berkas** | ✅ | `pesan-berkas` di panel + snackbar `pesan-aksi`; dulu semua pesan ditulis tetapi tidak pernah tampil |
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
| **AI bekerja di tingkat folder projek, bukan cuma berkas aktif** | ✅ | tombol `konteks-projek` + `pilih-berkas-ai` (`AiProjectBoard.konteks()`, 2.400 karakter, lewati `build/.git/node_modules`) |
| **"Simpan berkas dari jawaban ini ke projek"** — blok `<<<BERKAS: jalur>>>` langsung ditulis ke disk | ✅ | `UbahanBerkas.ambil()` + `AiProjectBoard.tulis()`; `AiProjectBoardTest` (6 uji), `UbahanBerkasTest` (6 uji) |
| Lampirkan gambar/screenshot galat (vision) | ✅ | `GambarUntukAi` (≤1024 px, ≤700 KB) + `GambarVisionTest`, `GambarAiDeviceTest` |

**Bukti ke endpoint sungguhan** (dijalankan terhadap endpoint milik pengguna):
model terbaca `zai-org/GLM-5.3-Flash`, `deepseek-ai/DeepSeek-V4-Flash-0731`, `MiniMaxAI/MiniMax-M2.7`;
jawaban streaming keluar sebagai blok `java`. Perintahnya:

```bash
AI_ENDPOINT=https://api.crown.ai.id/v1 AI_KEY=... AI_MODEL=... \
  ./gradlew :core:ai:test --tests '*ChatNyataTest*'
```

## 4c. Agen build otomatis

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Baca balasan model menjadi banyak berkas (`<<<BERKAS: jalur>>>`) | ✅ | `UbahanBerkasTest` (6 uji) |
| Tolak jalur berbahaya dari model (absolut, `..`, `~`) | ✅ | `UbahanBerkas.rapikanJalur` + uji |
| Loop tugas → ubah berkas → build → galat dikirim balik ke model | ✅ | `AgenBuildTest` (5 uji) |
| Batas keras 3 putaran (jangan bakar kuota pengguna) | ✅ | `AgenBuildTest.berhenti_setelah_batas_putaran…` |
| Build tidak dijalankan bila tak ada berkas tertulis | ✅ | `AgenBuildTest.build_tidak_dijalankan_bila_semua_berkas_ditolak…` |
| **Layar "Minta AI buatkan…"** dengan log per putaran & tombol Hentikan | ✅ | `AgenUiTest` (2 uji: terkunci tanpa kunci AI, aktif setelah kunci + tugas) |
| **Papan tulis projek nyata**: ringkasan ≤ 2,4 KB, tulis berkas lewat penjaga jalur | ✅ | `AiProjectBoardTest` (6 uji: jalur luar projek, biner, batas ukuran) |
| **Gerbang build nyata** untuk agen (galat diterjemahkan `ErrorCatalog`) | ✅ | `AiBuildGateTest` (2 uji **di HP**, API 24 & 35: build sukses, Java rusak → galat berbahasa manusia) |
| **Mode hemat otomatis** (RAM ≤ 4,2 GB → log 600 baris, sorotan dibatasi) | ✅ | `ModeHematDeviceTest` (3 uji **di HP**, API 35) |
| **Bersihkan hasil build** (tampilkan ukuran `build/`, hapus tanpa menyentuh sumber) | ✅ | `PenyimpananProjekTest` (5 uji) + tombol `bersihkan-build` |
| **APK rilis 30,7 MB** (R8 + shrink + arm-only, ditandatangani) | ✅ | `:app:assembleRelease` = 32.242.759 B; kode minified diuji jalan di API 35 (`-PsemuaAbi`) |

## 4d. Git & GitHub (mesin git sudah nyata di HP)

| Fitur | Status | Bukti / catatan |
|---|---|---|
| Commit offline (tanpa internet) di dalam HP | ✅ | `RepoGitTest` + `GitDeviceTest.git_bisa_commit_dan_push_di_dalam_hp` (API 24) |
| Status perubahan (ditambah/diubah/dihapus) | ✅ | `RepoGit.keadaan` + 8 uji JVM |
| Riwayat commit terbaca | ✅ | `GitDeviceTest.hasil_build_diabaikan_dan_riwayat_terbaca` |
| Push ke remote HTTPS dengan token pengguna | ✅ | `RepoGit.dorong`; token **tidak** ditulis ke `.git/config` (diuji) |
| Unduh (klon) repo jadi projek | ✅ | `RepoGit.klon`; klon kosong dilaporkan sebagai galat, bukan folder hampa |
| `build/`, `*.apk`, keystore otomatis diabaikan | ✅ | `.gitignore` bawaan + uji |
| Jalan di Android 7 (API 24) | ✅ | JGit 3.7.1; ADR-024. JGit 5.x/4.11 **gagal** (`java.nio.file`) |
| Layar GitHub di aplikasi (token, commit, push, riwayat) | ✅ | `GitScreen` + `GitUiTest`, `GitDeviceTest` |
| Buat repo baru langsung dari layar Git | ✅ | `GitHubRilis.buatRepo` (REST, teruji `GitHubRilisTest`) + `BuatRepoCard` (`BuatRepoUiTest`); tanpa token tombol mati dengan alasan |
| Ambil (klon) repo dari UI layar projek | ✅ | `KlonProjekDialog` + `LaporanKlonUiTest.klon_dari_repo_lokal_menghasilkan_projek_yang_terbuka` (remote `file://`, tanpa jaringan) |

## 4e. Situs murni (tanpa APK, tanpa WebView)

| Fitur | Status | Bukti / catatan |
|---|---|---|
| **Jenis projek "Website saja (tanpa APK)"** di wizard — hasilnya **nol berkas Android** | ✅ | `WizardUiTest.website_saja_tidak_membuat_folder_aplikasi_android` (menegaskan `src/` tidak ada, `web/index.html` ada) |
| Editor membuka `web/index.html`, dan tombol utama jadi **"Susun situs"** (bukan Build APK) | ✅ | `EditorScreen` `webMurni` + `jenis` di `kodenusa.json` |
| Template "Situs murni" di wizard (HTML/CSS/JS saja, tanpa AndroidManifest) | ✅ | `SitusDeviceTest.template_situs_murni_disusun_jadi_folder_siap_terbit` |
| "Jadikan situs" → folder `build/situs` siap unggah | ✅ | `SitusStatisTest` (8 uji) + `SitusDeviceTest` (3 uji di HP) |
| **Layar "Situs"** di app: tombol Susun, ringkasan berkas/ukuran, daftar berkas yang dilewati | ✅ | `SitusUiTest.menekan_susun_menampilkan_isi_situs` |
| **Buka hasil di browser** & **bagikan situs sebagai zip** | ✅ | `SitusUiTest` + tombol `buka-situs` / `bagikan-situs` (FileProvider `${applicationId}.berkas`) |
| Projek aplikasi biasa diberi alasan jelas, bukan tombol mati | ✅ | `SitusUiTest.projek_tanpa_situs_menampilkan_alasan_bukan_tombol_mati` |
| Satu projek dua keluaran (situs **dan** APK) tanpa duplikasi berkas | ✅ | `SitusStatis.sumber` memilih `web/` atau `src/main/assets/` |
| `index.html` wajib di akar situs (bukan subfolder) | ✅ | `SitusStatisTest.index_di_subfolder_ditolak` |
| `node_modules`, `build`, `.exe`, `.psd` tidak ikut terbit | ✅ | `SitusStatisTest.node_modules_build_dan_berkas_aneh…` |
| Sisa build lama dibersihkan sebelum terbit | ✅ | `SitusStatisTest.hasil_build_lama_dibersihkan` |
| Terbit gratis ke GitHub Pages (`gh-pages`) + tautan otomatis | ✅ | `PagesTest` (3 uji) + panel "Terbitkan" di layar Situs (`SitusUiTest.terbit_*`) |
| Bagikan lewat zip (Netlify Drop / Cloudflare Pages) | ✅ | tombol "Bagikan zip" di layar Situs (`SitusUiTest`) |
| Server lokal di HP untuk dibuka keluarga via Wi-Fi | ⛔ | rencana langkah S4 (`docs/06-rencana-2026.md`) |
| PWA (manifest + service worker) agar bisa "dipasang" tanpa APK | ⛔ | rencana |

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
| Uji unit | ✅ | 252 uji (`./gradlew test`) |
| Uji di perangkat | ✅ | 77 uji, hijau di Android 15 (API 35); subset besar juga dijalankan di API 24 & 34 |
| Analisis statis detekt, 0 temuan | ✅ | `./gradlew detekt` |
| CI menjalankan gerbang yang sama | ✅ | `.github/workflows/ci.yml` |

---

## Urutan pekerjaan berikutnya

| # | Pekerjaan | Kenapa sekarang | Ukuran |
|---|---|---|---|
| 1 | ~~Hapus janji "build di server"~~ ✅ | Teks itu menyebut server yang tidak ada | Selesai |
| 2 | ~~Penangkap crash + "Bagikan laporan" (offline)~~ ✅ | Selesai di 0.9.8: `LaporanGalat` + kartu di layar projek (ADR-032) | Selesai |
| 3 | ~~Klon projek dari GitHub~~ ✅ | Selesai di 0.9.8: `KlonProjekDialog` tersambung ke `RepoGit.klon` | Selesai |
| 4 | ~~Catatan "Kotlin belum didukung" saat membuka `.kt`~~ ✅ | Selesai di 0.9.8: `catatan-kotlin`, sejajar catatan Python | Selesai |
| 5 | ~~Tombol "buat repo" di layar Git~~ ✅ | Selesai di 0.9.8: `BuatRepoCard` tersambung ke `GitHubRilis.buatRepo` | Selesai |
| 6 | Ukur di HP 4 GB fisik + `AI_EVAL=1` | Satu-satunya angka yang belum bisa saya ukur sendiri | Butuh perangkat/kunci pengguna |

Yang **tidak** akan dikerjakan tanpa bukti kebutuhan nyata: Kotlin di perangkat (ADR-003), Tier 1
build di server (ADR-001/007), model AI di dalam HP (ADR-023).
