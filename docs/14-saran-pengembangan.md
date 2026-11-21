# 14 — Saran Pengembangan v0.17+ (lengkap & rinci)

**Tanggal:** 21 November 2026 · **Status:** usulan untuk dipilih — belum disetujui
**Dasar:** audit keadaan nyata v0.16.0 (253 uji unit, 106 uji perangkat, 7 template),
`FITUR.md` (daftar ⛔/🟡), `docs/06-rencana-2026.md`, dan prinsip `docs/00-visi-produk.md`.

Aturan yang sama seperti docs/13 berlaku: **tidak ada janji di UI tanpa kode di belakangnya**,
dan setiap butir harus punya bukti terukur sebelum boleh diumumkan.

---

## 1. Ringkasan prioritas

| Prioritas | Tema | Kandungan | Rilis |
|---|---|---|---|
| 🔴 Tinggi | **APK yang benar-benar milik pengguna** | Kunci tanda tangan sendiri, ikon aplikasi, versionCode otomatis, tombol Pasang | v0.17.0 |
| 🔴 Tinggi | **Editor yang terasa nyaman** | Lengkapi kata, ukuran font, buka-berkas cepat, riwayat berkas | v0.17.0 |
| 🟠 Sedang | **Untuk pemilik usaha & pembelajar** | Ekspor CSV, 3 template baru, mode belajar | v0.18.0 |
| 🟡 Sedang | **Agen lebih dewasa + bukti** | Angka AI_EVAL, agen multi-putaran pilihan, scaffold dari deskripsi, galeri template | v0.19.0 |
| ⚪ Terus-menerus | **Keandalan** | Uji 24 jam offline, uji suhu 10 build, ukur HP fisik | paralel |

Ukuran: **S** = 1 sesi · **M** = 2–3 sesi · **L** = seminggu+.

---

## 2. v0.17.0 — "APK-nya terasa milik Anda"

### 2.1 Kunci tanda tangan milik pengguna (M) — masalah paling nyata hari ini

**Masalah.** Semua APK dibuat dengan debug keystore yang dibuat **per pemasangan**
(`DebugKeystore.kt`). Akibatnya: hapus aplikasi atau ganti HP → kunci hilang → APK baru
tidak bisa memperbarui APK lama (`INSTALL_UPDATE_INCOMPATIBLE`). Untuk siapa pun yang
membagikan aplikasi ke pelanggan (warung → karyawan), ini pemutus. Untuk yang ingin naik ke
Play Store, kunci debug bahkan tidak diterima.

**Rancangan.**
1. Layar baru **"Kunci tanda tangan"** (dari BuildScreen ⋯ atau Setelan): buat keystore baru
   (alias, sandi, validitas 10.000 hari) atau impor `.jks`/`.keystore` lewat SAF.
2. Keystore disimpan di `filesDir/kunci-ttd/` dan **sandinya terenskripsi** lewat pola
   `KunciAman` (AES-256-GCM, format `enc1:`) — sama seperti token GitHub. Jangan pernah
   menulis sandi polos.
3. `Tier0Config`/`ApkPackager` menerima keystore pengguna bila ada; kalau tidak, fallback ke
   debug keystore seperti sekarang (perilaku lama tidak berubah — Chesterton's Fence).
4. Catatan jujur di layar: "Kunci disimpan di HP ini saja. Pindahkan berkas .jks kalau Anda
   pindah HP." + tombol ekspor `.jks` (SAF).

**Bukti selesai (v0.17 bagian 2, ADR-041):** uji perangkat `KunciPenggunaDeviceTest` —
build dengan kunci pengguna → sertifikat APK == sertifikat keystore pengguna ≠ keystore debug;
`apksigner verify` exit 0; build kedua (versionCode 1→2) sertifikat identik;
`scripts/prove/kunci-ttd.sh` → `pm install` v1 Success, `pm install -r` v2 Success
(aapt badging mengonfirmasi versionCode 1 lalu 2). Uji JVM: enkripsi sandi, fallback tanpa kunci.

### 2.2 Ikon aplikasi hasil build (M)

**Masalah.** Semua aplikasi hasil KodeNusa berikon robot default — di layar utama HP semua
aplikasi pengguna tampak sama. Ini yang paling terlihat mata saat "aplikasi saya jadi!".

**Rancangan.**
1. Saat build, `Tier0` membuat `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon):
   latar warna dari `kodenusa.json` (`"warnaIkon": "#0E7490"` misalnya), latar depan vector
   drawable **huruf awal nama aplikasi** (path teks digambar sebagai path, bukan font — supaya
   tidak butuh font bundel).
2. Field opsional di wizard: warna ikon (pilihan 8 warna kontras-teruji dari palet).
3. Manifest template menunjuk `@mipmap/ic_launcher` — ditambahkan otomatis bila belum ada.

**Bukti selesai:** `build-templates.sh` tetap 6/6; uji perangkat memeriksa resource ikon ada
dan APK memuatnya (`aapt dump`).

### 2.3 versionCode otomatis (S)

**Masalah.** `versionCode` beku di 1 di semua template. APK baru dengan isi baru tetap
"versi 1" — membingungkan saat dibagikan berulang.

**Rancangan.** Counter per projek di penyimpanan privat (`version-code/<projectId>`), naik
tiap build sukses; `kodenusa.json` tetap sumber versionName. Tampilkan angkanya di
riwayat build yang sudah ada (v0.16).

**Bukti selesai:** dua build berturut → versionCode 1 lalu 2 (uji JVM pada penyimpanan
counter + 1 uji perangkat).

### 2.4 Tombol "Pasang" di layar Build (M)

**Masalah.** Untuk memasang hasil build, pengguna harus Bagikan APK → buka Files → ketuk
APK → izin. Empat langkah untuk pemula = tempat mereka tersesat.

**Rancangan.**
1. Tombol "Pasang" di kartu hasil build → `PackageInstaller` (API 24+; session-based di 31+)
   dengan `FileProvider` untuk APK.
2. Android 8+: deteksi `canRequestPackageInstalls()`; bila belum, satu penjelasan jujur
   "Android butuh izin 'instal aplikasi tidak dikenal' sekali saja" + tombol buka setelan.
3. Catatan: **tidak** menambah izin berbahaya ke manifest IDE; izin instal diminta runtime
   saat pertama dipakai.

**Bukti selesai:** uji perangkat di emulator memasang APK hasil build lewat jalur yang sama
(klaim UI hanya boleh muncul setelah uji ini hijau).

### 2.5 Editor: lengkapi kata (S/M)

**Masalah.** Mengetik Java di keyboard HP melelahkan; `insertSnippet` ada tetapi manual.

**Rancangan.** Panel kandidat kecil di atas keyboard: sumber = kata unik berkas aktif +
kata kunci bahasa (Java/XML) + simbol yang sudah diketik di berkas. Muncul setelah ≥2 huruf,
maks 6 kandidat, ketuk = `insertSnippet`. **Tanpa** LSP/index projek — dilarang berat
(HP 2 GB). Murni `HashSet<String>` dari isi editor.

**Bukti selesai:** uji JVM `PenyediaKandidat` (sumber, urutan, batas 6, tanpa duplikat) +
1 uji UI mengetik "Sys" → kandidat "System".

### 2.6 Editor: ukuran font (S)

Setelan A−/A+ di bar editor (14–22sp, persist). Bukti: uji perangkat tekan A+ → ukuran
teks editor berubah, tetap setelah layar ditutup.

### 2.7 Buka berkas cepat di palet perintah (S)

Palet perintah sudah ada (ADR-031) — tambah mode "buka berkas": ketik sebagian nama,
kandidat dari `ProjectTree`, Enter = buka tab. Bukti: 2 uji UI.

### 2.8 Riwayat berkas (M)

**Masalah.** Undo ada, tetapi kalau pengguna menimpa berkas lalu menutup projek, versi
lama hilang. Bagi pemula, kehilangan kerja = berhenti total.

**Rancangan.** Sebelum tiap simpan, salin isi lama ke `<projek>/.riwayat/<berkas>/<n>.bak`
(maks 10, LRU, dikecualikan dari build & pencarian seperti `build/`). Dialog "Riwayat
berkas" di menu ⋯ editor: daftar waktu + pulihkan.

**Bukti selesai:** uji JVM (rotasi 10, pemulihan isi, jalur `../` ditolak lewat
`ProjectFiles.safeRelative`) + 1 uji perangkat.

---

## 3. v0.18.0 — "Untuk pemilik usaha & pembelajar"

### 3.1 Ekspor CSV dari template (S per template)

Tombol "Ekspor CSV" di template laporan-warung & kasir-warung: query SQLite → file CSV
di cache → `ACTION_SEND`. Pemilik warung bisa membuka laporan di Sheets/kirim ke pemilik
toko lain. Bukti: uji JVM pembuat CSV (koma/kutip/escape benar) + uji UI tombol.

### 3.2 Tiga template baru (M)

| Template | Isi | Audiens |
|---|---|---|
| `jurnal-harian` | Catatan harian + mood, SQLite, kalender sederhana | pelajar |
| `stok-barang` | Barang masuk/keluar + sisa stok + peringatan stok tipis | warung/toko |
| `kuis-pelajaran` | Paket soal dari `assets/soal.json`, skor, riwayat | pelajar/guru |

Masing-masing: `index.json` + `PromptBuilder.templatProjek` + dibuktikan oleh
`build-templates.sh` (angka dinamis sudah aman untuk penambahan).

### 3.3 Mode belajar (L — butuh keputusan desain dulu)

Overlay pelajaran per template (`pelajaran.md` di folder template, langkah 1..N: "buka
berkas X, ubah Y, tekan Build"). Panel kecil di bawah editor, bisa ditutup. Ini fitur
pembeda terbesar untuk audiens 13–25 tahun, tetapi butuh konten yang ditulis manusia —
mulai dari **satu** template (halo-dunia) sebagai percontohan sebelum melebarkan.

### 3.4 Cadangkan semua projek (S)

Tombol "Cadangkan semua" → satu `.zip` berisi semua folder projek → SAF. Mengembalikan =
impor .zip yang sudah ada. Bukti: uji JVM isi zip = daftar projek.

---

## 4. v0.19.0 — "Agen lebih dewasa + bukti"

1. **Angka AI_EVAL nyata (S bila penyedia pulih):** jalankan harness 10 tugas dengan kunci
   pengguna, catat pass-rate ke `docs/07-eval-agen.md`. Tanpa angka, klaim "agen selesai
   tanpa sentuh" tetap tidak boleh ditulis.
2. **Agen: batas putaran jadi pilihan 3/5 (S)** + tampilkan estimasi karakter terkirim
   sebelum mulai (menghubungkan dengan mode hemat kuota v0.16).
3. **Scaffold projek dari deskripsi (M):** prompt "buatkan template …" → AI menghasilkan
   `kodenusa.json` + berkas → masuk daftar "template saya" (tersimpan di penyimpanan
   privat, bukan assets). Wajib lewat gerbang pratinjau tulis (ADR-038) + `AiBuildGate`.
4. **Galeri template komunitas (M, opsional online):** unduh `.zip` template dari repo
   GitHub publik + verifikasi ukuran/SHA; tanpa jaringan = daftar bawaan saja, dikatakan
   jujur di layar. Tidak ada server KodeNusa — hanya GitHub raw, konsisten ADR-001.
5. **R2/R3 (S/M, butuh HP fisik):** skrip uji 24 jam tanpa internet + suhu/throttle 10
   build berturut, hasil dicatat di `docs/08-lembar-ukur-4gb.md`.

---

## 5. Yang sengaja TIDAK disarankan (dan alasannya)

| Butir | Alasan ditolak |
|---|---|
| Kotlin bisa dikompilasi sekarang | ADR-003: tunggu angka HP fisik (todo #10). Menjanjikan sekarang = janji kosong |
| Build lewat Gradle/proot (Tier 2 penuh) | ADR-001/007: butuh unduhan ratusan MB + RAM di atas kelas target |
| Model AI offline di HP | ADR-023: 400–900 MB + lambat di HP 4 GB |
| Server Wi-Fi lokal | ADR-030: socket/port/lifecycle rapuh; pratinjau `file://` sudah cukup |
| AAB (Android App Bundle) | Butuh bundletool + signing Play; APK sudah menjalankan tujuan distribusi langsung |
| Formatter penuh (prettier/gjf) | Pustaka puluhan MB; Rapikan (v0.16) sudah menutup 80% keluhan indentasi |
| Multi-kursor & code folding | Kompleksitas editor tinggi, permintaan nyata dari pengguna HP kecil |

---

## 6. Housekeeping yang ikut v0.17.0 (kecil, wajib)

1. `docs/06-rencana-2026.md` basi: baseline masih "0.9.6 / 252 uji / 77 uji", G3 & G5 masih
   ditulis "belum tersambung UI" padahal tuntas sejak 0.10.0, dan daftar "Yang berikutnya"
   isinya semua sudah selesai. Perbarui baseline + tabel status + daftar berikutnya.
2. `docs/00-visi-produk.md` tabel "Ukuran keberhasilan" memuat angka emulator — tandai
   mana yang sudah diganti angka HP fisik begitu todo #10 selesai.
3. Setiap butir v0.17 yang dikerjakan wajib punya ADR bila mengubah kontrak (2.1 kunci
   tanda tangan = ADR baru; 2.2 ikon = perubahan kontrak template).
