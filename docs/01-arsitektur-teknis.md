# 01 · Arsitektur Teknis

Dokumen ini menjelaskan **bagaimana** KodeNusa bekerja dan **mengapa** bentuknya begitu. Alasan per keputusan ada di [ADR](03-adr.md); di sini yang dijelaskan adalah rangkaiannya.

**Status dokumen:** terakhir diselaraskan dengan rilis **v0.22.0** (374 uji unit · 142 uji perangkat · detekt 0).

---

## 1. Batasan yang membentuk seluruh desain

| Batasan | Konsekuensi arsitektur |
|---|---|
| Target RAM 2–4 GB | Tidak ada proses latar permanen, tanpa database, log build dibatasi barisnya |
| Harus jalan offline | `android.jar`, ECJ, dx, apksigner ikut di dalam APK (ADR-015, ADR-028) |
| Tanpa Gradle | Format projek sendiri `kodenusa.json` (ADR-005) |
| APK dibagikan langsung (bukan Play Store) | minSdk 24, tanpa izin berbahaya, tanpa `MANAGE_EXTERNAL_STORAGE` (ADR-022) |
| Tidak ada server milik proyek | AI memakai kunci pengguna; unggah APK memakai GitHub pengguna (ADR-023, ADR-029) |
| Kode pengguna tidak boleh hilang | Berkas di disk adalah sumber kebenaran; editor menyimpan otomatis |

## 2. Peta modul

```
:app                 Compose UI + penyimpanan setelan aplikasi
:core:project        Berkas projek, validasi, zip, pencarian, impor SAF
:core:build          Kontrak BuildEngine, SitusStatis, SitusPwa
:core:diagnose       DiagnosticParser + ErrorCatalog
:core:ai             Klien OpenAI-compatible, PromptBuilder, PetaProjek, PemilihKonteks, AgenBuild
:core:git            RepoGit (JGit), GitHubRilis, GaleriTemplate
:core:editor         CodeEditorView, GutterView/GutterModel, Highlighter, Rapikan, PenyediaKandidat
:core:ui             Token warna OKLCH, komponen dasar, pemeriksa kontras
:build:tier0         Pipeline build di perangkat (9 langkah)
:build:hostcli       CLI Tier 0 untuk JVM host (bukti pipeline tanpa emulator)
```

Aturan ketergantungan: `:app` boleh tahu semua modul; `:core:*` tidak boleh tahu `:app`; `:build:tier0` hanya bergantung pada `:core:build` dan `:core:diagnose`. Tidak ada dependency injection framework — `AppContainer` adalah satu objek biasa, karena aplikasi ini harus tetap ringan.

## 3. Aliran projek

```
filesDir/projek/<folder>/
├── kodenusa.json           ← identitas projek (nama, applicationId, jenis, versi)
├── src/main/AndroidManifest.xml
├── src/main/java/...       ← Java 8, dikompilasi ECJ
├── src/main/res/...        ← layout, values, drawable
└── build/                  ← hasil build (boleh dihapus kapan saja)
```

- `ProjectLoader` membaca `kodenusa.json`; bila ada `build.gradle`, projek ditandai **bukan Tier 0** dan build dimatikan dengan alasan tertulis (`ProjectValidation.bisaDibangun`).
- `ProjectTree` menyusun daftar berkas untuk panel; `ProjectFiles` menolak nama tidak sah (absolut, `..`, karakter terlarang) **sebelum** menyentuh disk.
- Projek `web-murni` memakai `web/` atau `src/main/assets/` sebagai akar situs (`SitusStatis.sumber`). Tidak ada berkas Android sama sekali.

## 4. Pipeline Tier 0 (9 langkah)

```
PREPARE → AAPT_COMPILE → AAPT_LINK → JAVAC → JAR → D8 → PACKAGE → ALIGN → SIGN
```

| Langkah | Alat | Catatan teknis |
|---|---|---|
| PREPARE | — | Membersihkan `build/`, menyalin aset, memeriksa keutuhan toolchain (SHA-256) |
| AAPT_COMPILE | `aapt2` 2.19 bionic | Biner per ABI di `jniLibs`, dijalankan dari `nativeLibraryDir` (W^X, ADR-021) |
| AAPT_LINK | `aapt2` | Menghasilkan APK tanpa dex, dengan `--java` untuk `R.java` |
| JAVAC | ECJ **3.25.0** | Level bahasa 8, `-bootclasspath android.jar` API 34, `-proc:none` (ADR-011, ADR-016) |
| JAR | `JarPacker` | Di dalam proses; d8/dx menerima arsip, bukan folder (ADR-012) |
| D8 | dx 1.16 (build-tools 28.0.3) | Paket direlokasi ke `id.kodenusa.dxtool` supaya tidak bentrok (ADR-017) |
| PACKAGE | `ApkPackager` | Menyisipkan `classes.dex` + aset ke APK aapt2 |
| ALIGN | `ZipAligner` | Alignment murni Kotlin, tanpa biner `zipalign` |
| SIGN | apksigner **0.8** | v2 signing; keystore dibuat di perangkat, atau kunci pengguna bila diatur (ADR-018, ADR-019, ADR-041) |

Semua alat Java (ECJ, dx, apksigner) dijalankan lewat `/system/bin/app_process` dengan classpath `dalvik-cache` tulis — bukan `java -jar`, karena Android tidak punya JVM (ADR-021). Argumen selalu berupa `List<String>` ke `ProcessBuilder`; **tidak pernah** `sh -c` (ADR-006).

`BuildCache` melewati langkah yang masukannya tidak berubah; sidik jari cache langkah SIGN **wajib memuat keystore** — tanpa itu, ganti kunci dengan masukan identik memakai ulang APK bertanda tangan kunci lama (pelajaran ADR-041).

## 5. Aliran build dan galat

```
BuildScreen → BuildSession → Tier0BuildEngine → BuildEvent
                                   │
                                   ├── LogLine      → log (dibatasi baris)
                                   ├── StepChanged  → daftar langkah
                                   └── Finished     → BuildResult { apk, durasi, diagnostics }
                                                            │
                                        DiagnosticParser ───┘
                                                │
                                          ErrorCatalog → "judul · berkas · baris · cara perbaiki"
```

- `BuildSession` memotong log pada batas baris (`ModeHemat.batasLog`) supaya RAM tidak habis untuk teks.
- Setiap galat yang dikirim ke AI **wajib** melewati `ErrorCatalog`, tidak pernah keluaran mentah alat (`AiBuildGate`).
- Build template hangat 1,4–1,9 detik; build pertama setelah pasang ~21 detik karena penyalinan `android.jar` 24 MB (sekali saja).

## 6. Editor

`CodeEditorView` adalah `FrameLayout` berisi **`GutterView`** (nomor baris, custom View) dan `EditText` (teks). Sejak v0.22.0 gutter **tidak menyimpan cache** `lineCount`: ia melukis dari `Layout` hidup pada setiap gambar, sehingga angka tetap benar saat scroll, word-wrap, dan ganti ukuran font; baris kursor disorot; baris lanjutan hasil wrap tidak bernomor. Logika murni dipisah ke `GutterModel` agar bisa diuji di JVM.

Penyorotan hanya untuk baris yang terlihat di viewport, karena mewarnai seluruh berkas 2.000 baris akan membuat `Spannable` menghabiskan RAM di HP 2 GB.

Invariant: `maxLines = Int.MAX_VALUE` selalu; word-wrap hanya mengubah `setHorizontallyScrolling`. Nama berkas yang diganti saat terbuka = simpan, tutup, buka ulang.

Simpan otomatis berjalan 900 ms setelah pengguna berhenti mengetik (`Autosave` di `EditorScreen`), sehingga aplikasi boleh dibunuh Android kapan saja.

## 7. AI (BYOK)

```
AiScreen → KonteksOtomatis ─┬─ PetaProjek (peta projek: peran + kerangka berkas)
                            └─ PemilihKonteks (skor leksikal → berkas relevan)
         → PromptBuilder (batas Tier 0 + peta + berkas terpilih) → OpenAiCompatClient → penyedia pilihan pengguna
                                   │
                              AgenBuild (maks 12 berkas, putaran 3/5)
                                   │
                            UbahanBerkas → validasi jalur → tulis berkas
```

- **Konteks otomatis adalah jalur bawaan** (v0.22.0): pengguna tidak perlu memilih berkas satu per satu. `PetaProjek.petakan()` menyusun peta (identitas, daftar berkas berperan, kerangka kelas/metode/resource) dengan anggaran karakter; `PemilihKonteks` memilih berkas relevan dari pertanyaan dengan skor leksikal deterministik (tanpa embeddings, tanpa server) — prediktabel, teruji, dan jujur di layar ("N dari M berkas · X karakter").
- Awalan prompt dibuat stabil (peta dulu, pertanyaan terakhir) supaya penyedia bisa memanfaatkan prompt caching.
- Tidak ada kunci bawaan, tidak ada server perantara, tidak ada library HTTP pihak ketiga.
- Jalur dari model dianggap **tidak tepercaya**: absolut, `..`, dan `~` ditolak tanpa "dirapikan".
- Tulisan AI melewati gerbang pratinjau (ADR-038) dan bisa dibatalkan (`UndoTulis`).
- Gambar selalu diperkecil sebelum dikirim (`GambarUntukAi`); foto kamera mentah tidak pernah dikirim.
- Balasan 400 yang menyebut `image`/`vision`/`multimodal` dipetakan ke `GAMBAR_TIDAK_DIDUKUNG`, bukan "galat penyedia".

## 8. Git dan GitHub

- `RepoGit` memakai **JGit 3.7.1** (versi terakhir yang tidak membutuhkan `java.nio.file` API 26 — ADR-024).
- Token tidak pernah ditulis ke `.git/config` dan tidak pernah ke berkas projek; ia hanya muncul di URL satu perintah push, lalu remote dikembalikan ke alamat bersih.
- `GitHubRilis` membuat repo, membuat rilis, dan mengunggah aset dengan `HttpURLConnection`. Badan unggahan ditulis per 64 KB (`AliranUnggah`) supaya progres terlihat.
- Klon dari GitHub masuk ke daftar projek (`KlonProjek`); galeri template komunitas memakai pola yang sama dengan verifikasi SHA-256.

## 9. Rahasia di disk

`Penyandi`/`KunciAman` memakai Android Keystore (AES-256-GCM, kunci tidak bisa diekspor). Nilai disimpan sebagai `enc1:<iv b64>:<ciphertext b64>`; nilai lama tanpa awalan dibaca sekali lalu ditulis ulang tersandi (ADR-026). Bila dekripsi gagal (kunci berubah setelah restore), aplikasi mengosongkan nilai dan meminta ulang — tidak pernah crash. Pola yang sama dipakai untuk sandi kunci tanda tangan pengguna (ADR-041).

## 10. Izin dan permukaan sistem

| Dipakai | Tidak dipakai | Alasan |
|---|---|---|
| SAF: `OpenDocument`, `CreateDocument`, `OpenDocumentTree` | `MANAGE_EXTERNAL_STORAGE` | Izin "Semua file" memicu blokir Play Protect di Android 13+ (ADR-022) |
| `FileProvider` (`${applicationId}.berkas`) | `external-path` | Berbagi hanya dari `filesDir/projek` dan `cacheDir` |
| `<queries>` untuk intent lintas aplikasi | Izin kamera | `TakePicture` memakai aplikasi kamera pengguna, bukan kamera langsung |
| `REQUEST_INSTALL_PACKAGES` (runtime) | Izin instal di manifest saat pasang | Diminta saat tombol Pasang ditekan pertama kali, dengan penjelasan |

## 11. Strategi verifikasi

| Lapis | Jumlah | Perintah |
|---|---|---|
| Uji unit (JVM, JUnit 5) | 374 | `./gradlew test` |
| Uji perangkat (API 35, sebagian di 24 & 34) | 140 | `./gradlew :app:connectedDebugAndroidTest` |
| Analisis statis | 0 temuan | `./gradlew detekt --rerun-tasks` |
| Bukti pipeline tanpa emulator | 9 template APK | `scripts/prove/build-templates.sh` |
| Rilis minified | dipasang & dibuka | `:app:assembleRelease -PsemuaAbi` |

Aturan yang tidak boleh dilanggar:

1. Uji yang dijalankan **di perangkat** untuk setiap fitur yang menyentuh berkas, izin, atau Compose.
2. APK rilis yang minified harus **dipasang dan dibuka** sebelum dibagikan — lulus uji unit tidak membuktikan apa pun tentang R8.
3. Setiap jalur jaringan baru wajib punya satu uji nyata bergerbang env (mis. `GH_TOKEN`), yang membuat sumber daya, memeriksa, lalu menghapusnya.
4. Uji perangkat yang gagal wajib melampirkan bukti (paket fokus, dump uiautomator) di pesan asersinya — bukan ditebak-ulang.
5. Kegagalan dicatat di [MEMORY.md](../MEMORY.md) sebagai invariant, bukan dibiarkan terulang.
