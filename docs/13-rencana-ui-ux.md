# 13 — Rencana UI/UX & Polesan Desain (v0.16.0+)

**Tanggal:** 21 November 2026 · **Status:** tuntas di v0.16.0 — semua paket A, B, dan C terlaksana (lihat catatan rilis)
**Dasar:** audit visual nyata (screenshot + pengukuran DOM di emulator API 35), `DESIGN.md`
(bengkel kode), skills `make-interfaces-feel-better`, `design-minimal-sleek-linear`,
`antislop-ui`, `accessibility`, dan agents `@design-director`, `@design-architect`,
`@a11y-contrast-enforcer`, `@motion-designer`.

---

## 0. Hasil audit nyata (20–21 Nov 2026)

Pengukuran, bukan kira-kira (emulator API 35, 1080×2340, APK debug berisi kartu izin baru):

| Temuan | Bukti | Dampak |
|---|---|---|
| Kartu "Tidak ada dialog izin?" render benar — klaim vision model soal teks bertumpuk **terbantahkan oleh DOM**: judul ~y490–545, isi y566–866, bounds terpisah | `uiautomator dump` + `docs/ux-visual/kartu-crop.png` | Tidak perlu perbaikan; pelajaran: klaim visual wajib diukur dulu |
| **Catatan izin dobel**: kartu baru (atas) dan teks redup lama "Ekspor projek ada di menu ⋯ … Tidak perlu izin penyimpanan…" (bawah, y1888–1976) menjelaskan hal yang sama dua kali | `audit-workspace2.png`, dump bounds | Pemborosan ruang & pesan membelah; dirapikan di A1 |
| **Empty state mendominasi**: panel "Belum ada projek" y1108–1498, lalu ruang kosong sampai tombol impor di y1773 — bawah layar "menggantung" | dump bounds | Layar pertama pengguna baru terasa kosong; dirapikan di A2 |
| **Koordinat tap skrip menjadi basi**: kartu baru menggeser tata letak, tap wizard lama (540,994) kini mendarat di kartu, bukan tombol | sesi audit gagal membuat projek lewat skrip koordinat | Skrip uji manual (uji-tap.py) rentan; perkuat dengan testTag + aksesibilitas |
| Spasi antarbagian tidak konsisten (16dp vs 8dp campur aduk tanpa pola) | dump bounds | Sesuai skala 4pt DESIGN.md §4 tapi pemakaiannya tak berpola |

---

## 1. Prinsip yang mengikat (tidak bisa ditawar)

1. **Bengkel kode, bukan dashboard SaaS** (DESIGN.md §1): padat, tenang, gelap, satu aksen amber.
2. **Anti-slop UI** (skill `antislop-ui`): tanpa emoji ikon, tanpa kartu seragam 3 kolom,
   tanpa glow dekoratif, tanpa badge pil tanpa fungsi, tanpa angka/delta karangan.
3. **8 state wajib** per komponen interaktif (DESIGN.md §5): default, hover, focus-visible,
   pressed, disabled, loading, error, empty.
4. **Gerak 90–140 ms**, hanya `transform`/`opacity`, tekan = `scale(0.96)`, haptic 8 ms sekali
   pada aksi tuntas (DESIGN.md §6 + skill make-interfaces-feel-better).
5. **Target sentuh ≥ 48×48 dp** dan kontras ≥ 4.5:1 terukur (DESIGN.md §8, skill accessibility).
6. **Jujur**: tidak ada janji fitur di UI sebelum kodenya ada (aturan tetap proyek).

---

## 2. Paket A — Polesan visual cepat (v0.16.0, ±1 sesi)

Perubahan kecil per berkas, dampak langsung terasa, semua bisa diuji:

| # | Item | Berkas | Detail |
|---|---|---|---|
| A1 | **Satu sumber penjelasan izin** | `WorkspaceScreen.kt` | Hapus teks redup lama di bawah tombol impor; kartu baru jadi satu-satunya penjelas. Hemat ~90px vertikal |
| A2 | **Empty state dirapatkan** | `WorkspaceScreen.kt` | Panel kosong tidak lagi `fillMaxWidth` raksasa; tambah satu baris bantuan di bawah CTA ("Butuh contoh? Template kasir siap pakai") supaya bawah layar tidak menggantung |
| A3 | **Tekan terasa** | `Components.kt` | `PrimaryButton`/`GhostButton` dapat state pressed `scale(0.96)` 90 ms + haptic 8 ms pada aksi tuntas (build sukses, APK terpasang) — sesuai DESIGN.md §6 |
| A4 | **Angka tabular** | `EditorScreen.kt`, `AiScreen.kt`, `BuildScreen.kt` | Nomor baris, ukuran berkas, "terkirim: N karakter" pakai `fontFeatureSettings = "tnum"` supaya digit tidak goyang |
| A5 | **Titik status build** | `BuildScreen.kt` | Status per langkah pakai titik 6dp (`ok`/`warn`/`err`) ala Linear — bukan teks saja, bukan glow |
| A6 | **Radius konsentris** | `Components.kt` | Kartu dalam kartu (dialog di panel) memakai `radius luar = radius dalam + padding`; kunci `RADIUS_MEDIUM` jadi satu nilai |

**Gate Paket A:** unit + perangkat + detekt hijau, screenshot sebelum/sesudah tiap layar
yang berubah, kontras tak turun (ContrastTest tetap lulus).

---

## 3. Paket B — Tata letak & navigasi (v0.16.0 lanjutan / v0.17.0)

| # | Item | Berkas | Detail |
|---|---|---|---|
| B1 | **Baris projek asimetris** | `WorkspaceScreen.kt` | Baris padat ala DESIGN.md §10: nama + jumlah berkas kiri, ukuran & tanggal ubah terakhir kanan (`tnum`), aksi ⋯ menyatu; bukan kartu seragam |
| B2 | **Audit 320dp** | semua layar | Jalur uji perangkat baru: set lebar 320dp, pastikan tanpa overflow horizontal (DESIGN.md §4) |
| B3 | **8 state di komponen inti** | `Components.kt`, `BuildScreen.kt` | Loading build bertahap dengan label Indonesia ("Menyusun resource 3/8") diverifikasi & dilengkapi; disabled punya alasan (sudah ada di Git — diseragamkan) |
| B4 | **Fokus keyboard terlihat** | `Components.kt` | `focus-visible` outline 2dp aksen offset 2dp untuk navigasi keyboard eksternal (DEX/keyboard OTG) |
| B5 | **Hierarki layar AI** | `AiScreen.kt` | Chat makin padat: gelembung pengguna vs jawaban dibedakan lewat indentasi + garis kiri tipis (bukan warna baru), tombol aksi jawaban dikelompokkan satu baris |

**Gate Paket B:** sama + uji 320dp masuk CI perangkat.

---

## 4. Paket C — Fitur baru yang disarankan (v0.17.0+, perlu keputusan Anda)

Diurutkan dari nilai-per-usaha untuk pengguna Indonesia tanpa PC:

| # | Fitur | Mengapa | Perkiraan usaha |
|---|---|---|---|
| C1 | **Pencarian teks lintas berkas dalam projek** | `ProjectSearch` sudah ada di `core/project` — tinggal UI: hasil per berkas, ketuk → buka baris. Nilai besar bagi pengguna yang mewarisi projek orang | Kecil–sedang |
| C2 | **Perapian indentasi otomatis** (1 tombol "Rapikan") | Pengguna baru sering menulis indentasi kacau; build gagal karena kurung, bukan logika. Murni teks, tanpa library | Kecil |
| C3 | **Riwayat build + ukuran APK tiap build** | "Kenapa APK-ku 12 MB?" terjawab dengan daftar: waktu, sukses/gagal, ukuran. Data sudah lewat tangan BuildSession | Sedang |
| C4 | **2 template baru: Absensi kelas & Laporan warung harian** | Dua kebutuhan nyata UMKM/sekolah yang paling sering diminta; memperluas daya tarik tanpa fitur baru | Sedang |
| C5 | **Mode hemat data AI** (ringkas konteks, maks 2 berkas sematan) | Kuota pengguna HP mahal; tombol "Hemat kuota" memangkas prompt sebelum kirim | Kecil |
| C6 | **Cadangan projek otomatis → zip terjadwal** | Kehilangan kerja = bencana bagi pengguna tanpa PC; cadangan harian ke folder pilihan via SAF | Sedang |

**Yang sengaja TIDAK direncanakan** (jaga fokus): light mode (menunggu bukti kebutuhan,
DESIGN.md §11), Material You dynamic color (minSdk 24), akun/cloud/telemetri (anti-nilai
proyek), Kotlin di perangkat (ADR-003/023).

---

## 5. Urutan kerja yang diusulkan

1. **Paket A** penuh → rilis **v0.16.0**.
2. **Paket B** → rilis **v0.17.0**.
3. **Paket C** dipilih 2–3 butir per rilis berikutnya, masing-masing dengan ADR bila
   menyentuh arsitektur (C6 butuh ADR penyimpanan; C1/C2/C5 tidak).
4. Setiap paket: gate penuh (unit + perangkat + detekt), pasang & luncurkan di emulator,
   screenshot audit ulang, catatan jujur bila ada yang belum terverifikasi di HP fisik.

**Catatan terbuka:** pengukuran HP fisik (todo #10, Samsung A17 5G = Android 15/One UI 7,
4/6/8 GB RAM) tetap prasyarat klaim "4 GB cukup" — rencana ini tidak menggantikannya.
