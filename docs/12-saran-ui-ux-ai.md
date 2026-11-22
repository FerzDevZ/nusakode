# 12 · Saran UI/UX & Fitur — Layar AI dan Agen

> **Status: SEMUA butir TUNTAS** di v0.13.0–v0.15.0 (semua uji hijau sejak rilis-rilis itu). Dokumen dipertahankan sebagai rekaman diagnosis → perbaikan; setiap butir diberi label **[FAKTA]** = terverifikasi di kode/uji, **[SARAN]** = usulan yang kini sudah dikerjakan.

> Disusun 20 November 2026 setelah membaca seluruh kode layar AI (`AiScreen.kt`), agen
> (`AgenBuild.kt`, `AiProjectBoard.kt`, `PromptBuilder.kt`), dan panel berkas editor.

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

## 3. Saran lanjutan — STATUS: SEMUA (1–7) SELESAI di v0.13.0–v0.15.0

| # | Saran | Status | Catatan |
|---|---|---|---|
| 1 | **Pratinjau diff sebelum AI menimpa berkas** | ✅ v0.14.0 | `DialogKonfirmasiTulis`: daftar berkas baru/timpa + ukuran lama→baru; timpa berwarna peringatan |
| 2 | **Undo satu langkah agen** | ✅ v0.14.0 | `UndoTulis.kt`, `filesDir/undo-tulis/`, maks 5 batch; jalur Sisip juga tercadangkan; tombol "Batalkan tulisan AI" |
| 3 | **Daftar perintah cepat baru di chat** | ✅ v0.14.0 | chip "Buat layout XML", "Tambah layar kedua", "Jelaskan berkas ini", "Perbaiki galat build", "Perbaiki berkas ini", "Buat Activity", "Bantu itu apa" |
| 4 | **Agen bisa membaca berkas yang dipilih pengguna** | ✅ v0.14.0 | berkas sematan maks 3, dikirim utuh via `untukBerkasSematan`; lebih dari 3 ditolak dengan pesan jujur |
| 5 | **Indikator biaya/kuota per percakapan** | ✅ v0.14.0 | "terkirim: N karakter ke endpoint" per projek, di-nol-kan lewat "Bersihkan" |
| 6 | **Riwayat agen yang bisa dibuka ulang** | ✅ v0.15.0 | `RiwayatAgen.kt` per projek (maks 20 catatan); dialog "Riwayat tulisan AI"; berkas bisa diketuk untuk dibuka di editor |
| 7 | **Template prompt per template projek** | ✅ v0.15.0 | `PromptBuilder.templatProjek(id)` — struktur tipikal tiap template dikirim ke model; tak dikenal → `null` |

## 4. Saran UI/UX umum yang sudah dipenuhi sebelumnya (catatan)

- Tombol sekunder jelas sebagai tombol (ADR-033) — sudah.
- Semua ikon punya `contentDescription` — sudah (`GhostIconButton`).
- Kontras semua layar diuji otomatis — sudah (`ContrastTest`, gerbang `.kntema`).
- Yang masih terbuka: pratinjau diff (butir 3.1) dan undo agen (3.2) adalah dua yang paling
  melindungi pengguna; sisanya kenyamanan.
