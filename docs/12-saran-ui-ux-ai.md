# Saran UI/UX & fitur — fokus layar AI dan agen

> Disusun 20 November 2026 setelah membaca seluruh kode layar AI (`AiScreen.kt`), agen
> (`AgenBuild.kt`, `AiProjectBoard.kt`, `PromptBuilder.kt`), dan panel berkas editor.
> Setiap butir diberi label: **[FAKTA]** = sudah terverifikasi di kode/uji, **[SARAN]** =
> **STATUS: SEMUA butir di bagian 2 sudah dikerjakan dan dirilis di v0.13.0** (232→234 unit,
> 99→102 perangkat, semua hijau). Bukti per butir ada di bawah.

## 1. Fakta: apa yang sebenarnya sudah bisa dan belum

| Kemampuan | Status | Bukti |
|---|---|---|
| Editor: buat berkas baru, buat folder, rename, hapus, masuk sub-folder | ✅ sudah ada | `ProjectFiles.createFile/createFolder`, panel editor punya lipat/buka folder (`ProjectTree.visible`) |
| Agen build: menulis berkas **baru** termasuk membuat foldernya | ✅ sudah ada | `AiProjectBoard.tulis()` → `parentFile.mkdirs()` |
| Agen build: membaca struktur sub-folder | ✅ sudah ada | `konteks()` memakai `walkTopDown()` — semua folder terdaftar |
| Chat AI: menyisipkan kode | ⚠️ **hanya menimpa berkas yang sedang terbuka** | `onSisip` → `ketikaSisip` menolak bila tak ada berkas aktif |
| Chat AI: menyimpan berkas baru ke projek | ⚠️ tombolnya ada, tapi **nyaris tak pernah muncul** | tombol hanya muncul bila jawaban berisi `<<<BERKAS:>>>`, dan prompt chat tidak pernah mengajari format itu |
| Chat AI: pemilih berkas konteks | ⚠️ daftar datar jalur panjang, tanpa struktur folder & tanpa pencarian | dialog "Pilih berkas projek" |
| Aturan agen | ⚠️ hanya menyebut "berkas yang **berubah**" — model cenderung menolak membuat berkas baru | `PromptBuilder.ATURAN_AGEN` |

Kesimpulan: keluhan "AI cuma bisa edit berkas lama, ga bisa buka sub-folder" **beralasan** —
bukan karena mesinnya tak sanggup, tapi karena **prompt dan tombolnya tidak menuntun ke sana**.

## 2. Yang dikerjakan sekarang (rilis 0.13.0) — ✅ SEMUA SELESAI

1. **[FAKTA→FIX] Buat berkas baru langsung dari jawaban AI.** Tombol baru "Buat berkas baru
   dari kode" di bawah blok kode: pengguna menulis jalur lengkap (`src/main/java/app/Layar.java`,
   sub-folder ikut dibuat), berkas jadi, langsung terbuka di editor.
   **Selesai + temuan ekstra:** saat menguji ini lewat uji perangkat, ketemu **bug nyata** di
   `ProjectFiles.createFile` — folder tujuan yang belum ada membuatnya menolak dengan
   "Folder tujuan tidak ada". Diperbaiki: sub-folder dibuat otomatis, jalur `../` tetap ditolak
   (`ProjectFilesTest.berkas_baru_dibuat_di_sub_folder_yang_belum_ada`,
   `AiBuatBerkasTest` 2 uji perangkat).
2. **[FAKTA→FIX] Prompt agen mengizinkan berkas baru secara eksplisit** — ATURAN_AGEN kini
   menyebut "berkas baru boleh dibuat bila tugasnya butuh; tulis jalur lengkap, folder dibuat
   otomatis".
3. **[FAKTA→FIX] Pemilih berkas AI jadi pohon**: folder tampil sebagai judul, berkas menjorok
   sesuai kedalaman, plus kolom pencarian — sub-folder terlihat dan bisa dibuka. ✅ Selesai;
   diverifikasi di emulator (screenshot `docs/ux-visual/ai-pohon-berkas.png`) + uji
   `AiUiTest.pemilih_berkas_ai_menampilkan_pohon_dan_pencarian_sub_folder`.
4. **[FAKTA→FIX] Chip "Konteks projek" diberi label jujur**: "Konteks projek + bikin berkas",
   dengan satu baris petunjuk kapan dipakai.
5. **[FAKTA→FIX] Layout bawah dirapikan**: dua baris chip (konteks + gambar) digabung satu baris
   yang bisa digulir — menghemat ruang vertikal di layar 5 inci. ✅ Selesai.

> **Catatan jujur:** uji AI nyata ke endpoint pengguna tidak selesai di sesi ini karena
> penyedia AI (`api.crown.ai.id`) sedang mengembalikan HTTP 503 — terverifikasi lewat `curl`
> langsung, bukan bug aplikasi. Jalur galatnya sendiri terbukti bekerja: pesan
> "Penyedia AI sedang bermasalah (503)" tampil jujur di layar. Sisa verifikasi end-to-end
> AI nyata dikerjakan saat penyedia sudah pulih.

## 3. Saran lanjutan (belum dikerjakan, urut nilai)

| # | Saran | Kenapa layak | Risiko |
|---|---|---|---|
| 1 | **Pratinjau diff sebelum AI menimpa berkas** — tampilkan lama vs baru, pengguna setuju dulu | Menimpa berkas sekarang langsung jalan; satu salah jawaban model bisa menghapus kerja pengguna | UI diff di layar kecil sulit; mulai dari "tampilkan berkas yang akan ditimpa + ukurannya" |
| 2 | **Undo satu langkah agen** — simpan salinan berkas sebelum ditimpa (`filesDir/undo/`) | Sekali model salah, kerja hilang permanen; undo murah dan jujur | Penyimpanan; batas 12 berkas × 200 KB sudah wajar |
| 3 | **Daftar perintah cepat baru di chat**: "buat layout XML", "tambah layar kedua", "jelaskan berkas ini" | Pengguna HP sering blank mau ngetik apa; 4 chip ada, tambah yang sering dipakai | Kosmetik saja |
| 4 | **Agen bisa membaca berkas yang dipilih pengguna** (bukan hanya ringkasan 2.400 karakter) | Ringkasan memotong isi berkas penting; biarkan pengguna menyematkan berkas wajib-baca | Kuota token naik; batasi 3 berkas sematan |
| 5 | **Indikator biaya/kuota per percakapan** (jumlah karakter terkirim) | Pengguna bayar kunci sendiri; angka membuat mereka percaya | Kecil |
| 6 | **Riwayat agen yang bisa dibuka ulang** (tugas → hasil → berkas yang berubah) | Setelah layar ditutup, jejak agen hilang | Penyimpanan kecil, mudah |
| 7 | **Template prompt per template projek** (kasir warung, katalog) | Model langsung tahu struktur projek tipikal | Perlu menjaga agar tidak berbohong soal kemampuan |

## 4. Saran UI/UX umum yang sudah dipenuhi sebelumnya (catatan)

- Tombol sekunder jelas sebagai tombol (ADR-033) — sudah.
- Semua ikon punya `contentDescription` — sudah (`GhostIconButton`).
- Kontras semua layar diuji otomatis — sudah (`ContrastTest`, gerbang `.kntema`).
- Yang masih terbuka: pratinjau diff (butir 3.1) dan undo agen (3.2) adalah dua yang paling
  melindungi pengguna; sisanya kenyamanan.
