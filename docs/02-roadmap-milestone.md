# Roadmap dan riwayat rilis

Versi terakhir: **0.13.0** (`versionCode 25`), dirilis 20 November 2026.

## Fase

| Fase | Tujuan | Status |
|---|---|---|
| **F0 — Bukti** | Membuktikan APK bisa dibangun di dalam HP | ✅ selesai (v0.1.0) |
| **F1 — Bisa dipakai** | Projek, berkas, simpan, build berulang | ✅ selesai (v0.4.0) |
| **F2 — Bisa dibagikan** | Situs, Git, GitHub, unggah APK | ✅ selesai (v0.9.5) |
| **F3 — Bisa diandalkan** | Mode hemat, rahasia tersandi, palet, uji perangkat 77 | ✅ selesai (v0.9.6) |
| **F3b — Jujur di layar** | Menghapus klaim yang tidak ditepati kode + dokumentasi ditulis ulang | ✅ selesai (v0.9.7) |
| **F4 — Bisa dipercaya orang lain** | Klon dari GitHub, laporan crash, angka HP nyata | ⏳ berikutnya |
| **F5 — Ekosistem** | Kotlin, projek Gradle, Tier 1/2 | ❌ belum, keputusan ditunda |

## Riwayat rilis

| Versi | Isi utama |
|---|---|
| **0.1.0** | Build APK pertama yang berhasil **di dalam HP** (API 24). 9 akar masalah toolchain diselesaikan |
| 0.2.0–0.2.1 | Pipeline stabil, 3 template, `DiagnosticParser` + `ErrorCatalog` |
| **0.3.0** | W^X di Android 10+ diselesaikan (`nativeLibraryDir` + dex tulis); `ZipAligner` |
| **0.4.0** | Projek sebagai folder, SAF impor/ekspor, arsip zip, ADR-022 (tanpa izin penyimpanan) |
| **0.5.0** | AI BYOK: klien OpenAI-compatible, prompt berbatas Tier 0, ADR-023 |
| **0.6.0** | Pindah/salin berkas, agen build, Git lewat UI |
| **0.7.0** | Situs murni (ADR-025), panel terbit, layar Git (JGit + GitHub) |
| **0.8.0** | Papan projek agen, gerbang build ke AI, layar Agen |
| 0.8.1 | Vision: gambar ikut terkirim, diperkecil dulu |
| **0.8.2** | Perbaikan instalasi di Android 13+: `MANAGE_EXTERNAL_STORAGE` dibuang |
| **0.9.0** | Mode hemat RAM, pembersih `build/`, batas log, rilis arm-only terbukti minified |
| 0.9.1 | Rantai galat kompilasi, `maxLines` editor, wizard `Jenis`, cari se-projek |
| 0.9.2 | Ambil berkas dari HP, bagikan berkas, kabar aksi di panel |
| 0.9.3 | Rahasia tersandi Keystore, PWA, eval agen, lembar ukur 4 GB, ADR-028 |
| 0.9.4 | Foto ke AI, ganti semua se-projek, impor zip tanpa ANR, alasan tombol mati |
| 0.9.5 | Pratinjau situs di dalam aplikasi, bagikan APK lewat GitHub Release |
| 0.9.6 | Palet perintah, progres unggah bertahap, catatan jujur soal Python |
| **0.9.7** | Janji "build di server" dihapus dari UI; seluruh dokumentasi ditulis ulang agar cocok dengan kenyataan |
| **0.9.8** | Laporan galat offline + tombol bagikan (ADR-032); klon repo GitHub dari layar projek; buat repo baru dari layar Git; catatan jujur untuk berkas `.kt` |
| **0.9.9** | Perapatan tampilan (ADR-033): tombol sekunder berwujud, kolom nama wizard selalu terlihat di bilah bawah, kelompok pilihan diberi judul, Git menyuruh siapkan repo dulu |
| **0.10.0** | Python bisa dijalankan (ADR-034): MicroPython v1.25 dibawa sebagai libmicropython.so, tombol Jalankan + konsol keluaran di editor, batas 20 detik |
| **0.11.0** | Tema & tampilan (ADR-035/036): token semantik jadi sumber kebenaran, 4 tema bawaan teruji kontras (JVM), pilihan "Ikuti sistem" tanpa kilat, layar Tema, tema kustom `.kntema` ≤ 4 KB dengan gerbang kontras saat impor, ikon vektor, Banner/SegmentedControl, transisi antar layar |

Artefak 0.10.0: `KodeNusa-0.10.0.apk`, **32.772.723 bita**, sha256 `50a56613334cda7dcedf9138639a2f6f28ca2911a53c143956690ae67d5a7895`.

## Berikutnya, berurutan menurut risiko yang dihilangkan

### 1. ~~Hapus janji palsu "build di server"~~ ✅ dikerjakan 20-11-2026
`ProjectLoader` dulu menampilkan "build akan diarahkan ke server" untuk projek yang punya `build.gradle`, padahal Tier 1 tidak ada. Sekarang pesannya menyebut batas sebenarnya: *"Tier 0 (di dalam HP) hanya membangun projek kodenusa.json; build di server belum tersedia, jadi projek ini belum bisa dibangun di sini."* Tombol Build tetap dimatikan dengan alasan tertulis (`ProjectValidation.bisaDibangun`).

### 2. Penangkap crash + "Bagikan laporan" (offline)
`Thread.setDefaultUncaughtExceptionHandler` menulis laporan kecil (versi, API, RAM, jejak tumpukan) ke berkas, lalu layar berikutnya menawarkan "Bagikan laporan" lewat `FileProvider` yang sudah ada. Tanpa telemetri, tanpa server. **Ini satu-satunya cara kita tahu apa yang rusak di HP orang.**

### 3. Klon projek dari GitHub
`RepoGit.klon()` sudah ada dan teruji tetapi belum tersambung ke UI. Menyambungkannya melengkapi perjalanan dua arah: kirim dari HP A, lanjutkan di HP B. Butuh satu repo uji sekali pakai untuk verifikasi nyata.

### 4. Catatan "Kotlin belum didukung"
Sama seperti `.py`: saat pengguna membuka `.kt`, jelaskan bahwa Tier 0 hanya Java. **Jangan** menambahkan `kotlinc` (ADR-003).

### 5. Yang menunggu perangkat atau kunci milik pengguna
- Ukur di HP 4 GB fisik → isi [lembar ukur](08-lembar-ukur-4gb.md).
- Uji unggah 32 MB di jaringan seluler.
- Uji kamera sungguhan memotret.
- `AI_EVAL=1` dengan kunci sendiri → isi [eval agen](07-eval-agen.md).

## Ditunda dengan alasan tertulis

| Rencana | Alasan ditunda | ADR |
|---|---|---|
| Kotlin di perangkat | Ukuran dan JVM tidak realistis di 4 GB | ADR-003 |
| Tier 1 (build di server) | Butuh server + data pengguna berpindah | ADR-001, ADR-007 |
| Tier 2 proot/JDK | Hanya layak ≥ 4 GB, dan menambah 200–400 MB | ADR-001 |
| Model AI di dalam HP | 400–900 MB, 2–8 token/detik | ADR-023 |
| Play Store | APK langsung sudah bisa dibagikan | ADR-010 |
| Terminal | Permukaan serangan terlalu besar | ADR-008 |

## Ke depan

Rencana v0.12 dan kandidat setelahnya disusun terpisah: lihat [`11-rencana-v0.12.md`](11-rencana-v0.12.md).


## v0.20.0 — perombakan editor & AI paham projek (21 November 2026)

Dua keluhan pengguna dituntaskan sampai akar penyebabnya:

- **Gutter nomor baris diperombak total** (keluhan: "nomor cuma 1 / 1–33 lalu hitam"). Akar: cache `lineCount` dibaca sebelum layout dibangun. Kini `GutterView` custom View melukis dari `Layout` hidup tiap gambar — akurat saat scroll, wrap, ganti ukuran font; baris kursor disorot. Bukti layar di `bukti/`.
- **AI mempelajari seluruh projek tanpa pilih berkas 1-1** (keluhan: "masa kita harus pilih di editor 1-1"). `PetaProjek` menyusun peta (identitas, daftar berkas berperan, kerangka kelas/metode/resource); `PemilihKonteks` memilih berkas relevan dari pertanyaan (skor leksikal + bobot kepentingan, deterministik, tanpa server). Tombol **Pelajari projek** + info jujur "N dari M berkas · X karakter".
- **Mode belajar** (docs/14 3.3): pelajaran langkah-demi-langkah per template, progres tersimpan per projek.
- Flake `PasangApkDeviceTest` dituntaskan akar-cause: back otomatis membatalkan pemasang; kini back hanya saat fokus Setelan + bukti dump di pesan gagal.
- 354 uji unit · 140 uji perangkat · detekt 0.

## v0.19.0 — agen putaran pilihan & galeri komunitas (21 November 2026)

- Agen build dengan **putaran pilihan**: batas putaran 3/5 + estimasi kuota sebelum mulai; evaluasi agen nyata 9/10 tugas ([docs/07-eval-agen.md](07-eval-agen.md)).
- **Template saya**: scaffold AI dari deskripsi singkat → projek siap build.
- **Galeri komunitas**: impor template `.zip` dengan gerbang SHA-256 + penolakan zip-slip.
- Housekeeping docs/06: status nyata semua rencana.
- 312 uji unit · 129 uji perangkat · detekt 0.

## v0.18.0 — ekspor CSV & 3 template baru (21 November 2026)

- **Ekspor CSV** dari template berbasis database (satu sumber aset `Csv.java` bersama).
- Template baru: **Jurnal Harian**, **Stok Barang**, **Kuis Pelajaran** (total 10 template).
- **Cadangkan semua projek** ke satu `.zip` (SAF), beserta pemulihannya.
- 3 template baru membawa uji perangkat masing-masing.

## v0.17.0 — editor makin tajam & kunci milik pengguna (21 November 2026)

- **versionCode otomatis** dari penghitung versi (tidak lagi diedit manual per rilis).
- **Ukuran font editor** bisa diatur 14–24 sp (tersimpan, uji perangkat).
- **Buka berkas cepat** lewat palet: cari nama berkas di seluruh projek.
- **Lengkapi kata** dari kumpulan kata berkas terbuka.
- **Ikon aplikasi hasil build** dari gambar pengguna + **riwayat berkas** yang baru dibuka.
- **Tombol Pasang**: APK hasil build langsung dipasang lewat pemasang Android.
- **Kunci tanda tangan pengguna** (ADR-041): impor keystore `.jks` sendiri lewat SAF.

## v0.16.0 — polesan UI/UX penuh & template baru (21 November 2026)

Semua paket dari [`13-rencana-ui-ux.md`](13-rencana-ui-ux.md) terlaksana:

- **Paket A**: sentuhan lebih hidup (tombol mengecil 0,96× saat ditekan, getar sekali saat build
  sukses), angka pakai font tabel (tnum) agar tidak "menari", titik status build ala Linear.
- **Paket B**: baris projek asimetris (nama kiri, status kanan, aksi di bawah), fokus ring untuk
  keyboard/OTG, pesan AI bertata letak (kirim kanan, balasan kiri dengan garis aksen).
- **Paket C**: pencarian lintas berkas (sudah ada, diverifikasi), tombol **Rapikan** indentasi
  konservatif (hanya spasi depan; Python/Markdown tak disentuh), **riwayat build** per projek
  (10 terakhir, JSON di penyimpanan privat), **mode hemat kuota** AI (opsi, bawaan mati), dan dua
  template baru: **absensi-kelas** & **laporan-warung** — total 7 template, semuanya dibuktikan
  terbangun jadi APK bertanda tangan oleh `scripts/prove/build-templates.sh` (6/6, web-murni
  dilewati karena memang tanpa APK).
- **Perbaikan bug nyata dari audit perangkat**: kartu penjelasan izin dan baris projek menumpuk
  karena `Panel` adalah `Box` — kini dibungkus `Column` (terverifikasi lewat bounds uiautomator).

## v0.15.0 — riwayat agen & prompt tahu template projek (20 November 2026)

Dua saran terakhir dari [`12-saran-ui-ux-ai.md`](12-saran-ui-ux-ai.md) — daftar saran kini tuntas:

- **Riwayat tulisan AI yang bisa dibuka ulang**: setiap tulisan AI (jalur "Simpan berkas" dan
  "Sisip") dicatat per projek — tugas, berkas yang berubah, id batch undo. Dialog "Riwayat tulisan AI"
  di layar AI menampilkannya; berkas bisa diketuk untuk langsung dibuka di editor.
- **Prompt agen tahu struktur template projek**: projek dari template kasir-warung otomatis
  memberi tahu model tentang `DatabaseKasir.java`/`Produk.java`, web-katalog tentang folder
  `assets/`, dst. Template tak dikenal tidak menambah apa pun (tidak mengarang).
- 243 uji unit · 105 uji perangkat · detekt 0.
- **Catatan jujur**: dialog riwayat dan tombolnya teruji di perangkat (uji otomatis), dan logika
  pencatatannya teruji di JVM — tetapi uji end-to-end "tulis AI nyata → entri riwayat" belum bisa
  dijalankan karena penyedia AI (api.crown.ai.id) kembali 503 untuk payload dalam aplikasi
  (4 percobaan; curl kecil tetap 200). Aplikasi menampilkan galat itu apa adanya — perilaku yang
  benar. Uji ulang saat penyedia pulih, seperti pola v0.13.


## v0.14.0 — AI yang tidak merusak: pratinjau, undo, berkas sematan (20 November 2026)

Lima saran teratas dari [`12-saran-ui-ux-ai.md`](12-saran-ui-ux-ai.md) dikerjakan sekaligus:

- **Pratinjau sebelum menulis**: jawaban AI yang berisi `<<<BERKAS:>>>` tidak langsung ditulis —
  dialog menampilkan daftar berkas (baru / timpa + ukuran lama→baru) dan pengguna menyetujuinya dulu.
- **Undo**: sebelum menimpa, isi lama dicadangkan di `filesDir/undo-tulis/` (maks 5 batch);
  jalur "Sisip" di editor juga ikut dicadangkan. Tombol "Batalkan tulisan AI" mengembalikan semuanya.
- **Berkas sematan**: pengguna bisa menyematkan maksimal 3 berkas yang dikirim utuh ke AI
  (bukan ringkasan 2.400 karakter) — pill + tombol Buang di baris chip.
- **Chip perintah cepat baru**: "Buat layout XML", "Tambah layar kedua", "Jelaskan berkas ini", dst.
- **Indikator karakter terkirim**: "terkirim: N karakter ke endpoint" per projek.
- 238 uji unit · 104 uji perangkat · detekt 0.


## v0.13.0 — AI bisa membuat berkas baru & pemilih berkas jadi pohon (20 November 2026)

- **Bug nyata diperbaiki:** `ProjectFiles.createFile` menolak folder tujuan yang belum ada — inilah akar keluhan "AI ga bisa bikin berkas di sub-folder". Kini sub-folder dibuat otomatis; jalur `../` tetap ditolak.
- Tombol "Buat berkas baru dari kode" di tiap blok kode jawaban AI — jalur bebas, sub-folder ikut, langsung terbuka di editor.
- Prompt agen (`ATURAN_AGEN`) eksplisit mengizinkan berkas BARU.
- Pemilih berkas AI jadi pohon folder (`▸ src` dst.) + kolom pencarian; chip konteks diberi label jujur "Konteks projek + bikin berkas"; chip digabung satu baris gulir.
- Dokumen saran UI/UX: [`12-saran-ui-ux-ai.md`](12-saran-ui-ux-ai.md).
- 234 uji unit · 102 uji perangkat · detekt 0.

## v0.12.0 — perbaikan dari uji manual (20 November 2026)

- Unduh tema dari tautan diperbaiki (HTTP polos diizinkan, ADR-037) + pesan galat manusiawi + riwayat tautan.
- Bug serialisasi `.kntema` diperbaiki (`gelap` kini selalu tertulis).
- Pintasan tema di palet perintah; uji memutar 5 tema × recreate.
- 232 uji unit · 99 uji perangkat · detekt 0.
