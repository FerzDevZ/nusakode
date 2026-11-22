# 11 · Rencana v0.12 dan Seterusnya

> **Status: TUNTAS kecuali satu butir.** Nomor 2, 4, 5 selesai di v0.12.0; nomor 3 (nomor baris + undo/redo) ternyata sudah ada sejak awal dan diperombak penuh di **v0.20.0** (gutter custom View). Nomor 1 (ukur HP fisik) masih menunggu perangkat — lihat [docs/08](08-lembar-ukur-4gb.md).

> Disusun 20 November 2026, setelah 0.11.0 (tema) rilis. Urutan berdasarkan **risiko terhadap
> janji utama** — "semua HP Indonesia, termasuk 2–4 GB RAM, bisa bikin APK tanpa PC" — bukan
> berdasarkan mana yang paling seru. Setiap nomor butuh bukti terukur sebelum boleh diumumkan
> di UI (aturan kejujuran, ADR-001).

## Yang baru selesai (0.11.0) — konteks rencana ini

- Sistem tema: 4 tema bawaan teruji kontras (JVM), "Ikuti sistem" tanpa kilat, layar Tema,
  tema kustom `.kntema` dengan gerbang kontras, ikon vektor, Banner/SegmentedControl.
- Dua bug nyata ditemukan lewat uji manual di emulator dan sudah diperbaiki di rilis yang sama:
  HTTP polos diblokir Android 9+ (unduh tema gagal), dan `gelap` tidak tertulis di berkas
  `.kntema` (kotlinx `encodeDefaults=false`).

## Prioritas v0.12 — "rapikan dasar, ukur yang nyata"

> **Status (20 Nov 2026, rilis 0.12.0):** nomor 2, 4, 5 selesai dan teruji (232 unit · 99
> perangkat · detekt 0). Nomor 1 menunggu HP fisik. Nomor 3 ternyata sudah terpasang sejak
> awal (gutter nomor baris + undo/redo di `CodeEditorView`) — kekurangannya hanya uji; ditambah
> ke daftar uji berikutnya, bukan fitur baru.

| # | Pekerjaan | Kenapa sekarang | Ukuran selesai |
|---|---|---|---|
| 1 | **Ukur di HP fisik 4 GB** (`scripts/ukur-hp.sh`) | Satu-satunya klaim utama yang belum punya angka nyata. Semua keputusan arsitektur selanjutnya (Tier 2, Kotlin) bergantung padanya | Lembar `docs/08-lembar-ukur-4gb.md` terisi angka dari HP sungguhan, bukan emulator |
| 2 | **Ganti-ganti tema di uji perangkat** (recreate × 4 tema otomatis) | Anti-kilat & persistensi sudah diuji 1 tema; keempat tema harus lolos recreate tanpa kilat | 1 uji perangkat baru yang memutar 4 tema + screenshot |
| 3 | **Editor: nomor baris + undo/redo** | Keluhan wajar pengguna IDE; editor adalah layar yang paling lama dipakai | Uji perangkat: nomor baris mengikuti gulir; undo membalik ketikan |
| 4 | **Palet perintah menyebut tema** (`tema`, `gelap`, `terang`) | Palet perintah sudah jadi pusat navigasi; pintasan tema memangkas 2 ketukan | `PaletPerintahUiTest` + 1 kasus |
| 5 | **Perbaiki UX unduh tema**: riwayat tautan terakhir, pesan galat lebih manusiawi | Alur unduh baru; pengguna tanpa internet sama sekali tidak terpengaruh (fitur opsional) | Uji UI: tautan terakhir terisi ulang; galat 404/4 KB berpesan jelas |

## Kandidat v0.13+ — perlu ADR dulu, jangan langsung koding

| Ide | Keputusan yang dibutuhkan | Risiko utama |
|---|---|---|
| **Warna dinamis Material You** (ADR-037 kandidat) | Hanya API 31+; bagaimana berinteraksi dengan tema kustom? (usulan: dynamic color hanya untuk "Ikuti sistem", tema kustom tetap menang) | Dua sumber kebenaran warna; kontras wallpaper tak terkendali → gerbang kontras wajib tetap jalan |
| **Ekspor tema ke galeri komunitas** | Tanpa server (ADR-001): hanya tautan README berisi daftar `.kntema` yang dikurasi manual lewat PR | Moderasi isi; jangan janjikan "toko tema" |
| **Python: modul tambahan** (mis. `sqlite3`, `json` sudah ada) | Ukuran APK vs nilai; micropython-lib murni murah, modul C mahal | APK membesar melampaui 33 MB tanpa nilai nyata |
| **Build Kotlin di perangkat** | Tetap ⛔ (ADR-003) sampai angka HP fisik menunjukkan ada ruang; compiler Kotlin ~ ratusan MB | Menipiskan janji utama untuk HP 2 GB |
| **Tier 2 build (proot + JDK)** | Butuh bukti HP 4 GB sanggup; tanpa angka, jangan mulai | RAM, penyimpanan, kecepatan |

## Yang sengaja tidak dikerjakan (tetap)

- Server KodeNusa / akun / telemetri (ADR-001) — tema dibagikan sebagai berkas, titik.
- Model AI di dalam HP (ADR-023) — AI tetap BYOK lewat internet.
- Menjanjikan build Kotlin/Gradle di HP 2 GB tanpa angka lembar ukur.

## Aturan main yang terbukti dan tetap dipakai

1. Setiap jalur jaringan baru = 1 uji nyata berlingkungan sendiri (terbukti menangkap bug cleartext).
2. Uji manual di emulator **sebelum** rilis, bukan sesudah — dua bug 0.11.0 lolos gerbang otomatis.
3. Setiap tema (bawaan/kustom) diukur dengan `Kontras.rasio` yang sama — satu sumber kebenaran.
4. Screenshot tiap layar × tiap tema disimpan di `docs/ux-visual/` sebagai bukti visual.
