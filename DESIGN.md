# DESIGN.md — Arah Visual KodeNusa

Dikunci sebelum M1 (sesuai `AGENTS.md`: "For UI: settle DESIGN.md first"). Berlaku sampai **v0.9.6**.
Perubahan arah visual = ADR baru, bukan improvisasi di PR.

---

## 1. Konsep: "Bengkel Kode"

KodeNusa terasa seperti **perkakas kerja**, bukan aplikasi startup. Padat, tenang, gelap, monospace di mana pun kode muncul. Inspirasi rasa: terminal yang rapi, buku manual mesin, label perangkat. Bukan inspirasi: dashboard SaaS, landing page gradient.

Konsekuensi yang mengikat:
- **Editor adalah layar utama.** UI lain adalah panel yang bisa ditutup.
- **Kepadatan dihargai.** Tidak ada hero section, tidak ada whitespace dekoratif di layar kerja.
- **Satu aksen saja.** Aksen dipakai untuk aksi & status, tidak pernah untuk dekorasi.

## 2. Palet (OKLCH, dark-first)

| Token | Nilai OKLCH | Pakai |
|---|---|---|
| `--bg` | `oklch(0.18 0.012 250)` | Latar aplikasi |
| `--surface` | `oklch(0.23 0.014 250)` | Panel, tooltip, sheet |
| `--surface-2` | `oklch(0.28 0.015 250)` | Baris terpilih, header tabel |
| `--border` | `oklch(0.34 0.012 250)` | Garis 1dp, hanya 1dp |
| `--text` | `oklch(0.95 0.008 250)` | Teks utama |
| `--text-dim` | `oklch(0.72 0.010 250)` | Metadata, placeholder |
| `--accent` | `oklch(0.80 0.155 75)` | Build, aksi utama (amber panen) |
| `--accent-ink` | `oklch(0.22 0.05 75)` | Teks di atas aksen |
| `--ok` | `oklch(0.78 0.13 160)` | Build sukses, diff tambah |
| `--warn` | `oklch(0.84 0.14 95)` | Peringatan |
| `--err` | `oklch(0.68 0.19 25)` | Error build, diff hapus |
| `--info` | `oklch(0.75 0.10 235)` | Info netral (bukan ungu) |

**Aturan warna:** tidak ada ungu/indigo, tidak ada gradient `bg-clip-text`, tidak ada glow. Satu aksen (`--accent`) + tiga warna status. Kontras setiap pasangan teks/latar **≥ 4.5:1** (target 7:1 untuk teks kode) — diukur, bukan dikira.

Light mode ditunda sampai M4; kalau dibuat, wajib punya kontras setara dan bukan hasil inversi otomatis.

## 3. Tipografi

| Peran | Font | Ukuran (sp) | Catatan |
|---|---|---|---|
| Judul layar | Plus Jakarta Sans 600 | 20 | Typeface Indonesia (Tokotype) — sengaja |
| Body/UI | Plus Jakarta Sans 400/500 | 14 / 16 | Line-height 1.45 |
| Label/tombol | Plus Jakarta Sans 600 | 13 | Sentence case, bukan ALL CAPS |
| **Kode & log** | JetBrains Mono 400 | 13 (dapat diatur 11–20) | Ligatur **mati**: `!=` harus terbaca jelas |
| Metadata | JetBrains Mono 400 | 11 | Baris/kolom, ukuran file |

Nomor baris memakai `--text-dim`, lebar tetap 4 digit, di luar area seleksi. Tidak ada tab karakter — indentasi 4 spasi, tapi file berpola tab dideteksi otomatis.

## 4. Grid & layout

- Skala spasi 4pt: `4, 8, 12, 16, 24, 32, 48`. Tidak ada `13px`/`17px`.
- Lebar minimum yang harus benar: **320dp**. Uji di 320dp, 360dp, 411dp, 600dp+.
- Editor: baris kode wajib bisa di-scroll horizontal — **jangan pernah** wrap otomatis kode (kecuali mode "baca" yang eksplisit).
- Panel log build: bottom sheet dengan tiga snap point (peek 30% / half / full), bukan dialog.
- Tidak ada baris 3 kartu seragam. Daftar projek = baris padat dengan metadata kanan (asimetris), bukan grid kartu.
- FAB hanya satu per layar. Kalau butuh dua aksi utama, salah satunya salah.

## 5. State wajib per komponen (8)

Setiap komponen interaktif wajib punya desain untuk: `default`, `hover` (stylus/DeX), `focus-visible` (outline 2dp aksen, offset 2dp), `pressed`, `disabled`, `loading`, `error`, `empty`.

Aturan praktis:
- **Loading bukan spinner telanjang.** Build: progress per langkah dengan label Indonesia ("Menyusun resource 3/8").
- **Empty state selalu memberi aksi**, bukan gambar ilustrasi saja.
- **Error selalu punya** penyebab + lokasi + satu tombol perbaikan.

## 6. Gerak (motion)

- Durasi 90–140 ms. Tidak ada animasi > 200 ms.
- Hanya `transform` dan `opacity` yang dianimasikan. **Dilarang `transition-all`** dan `scale(1.05)` seragam.
- Bottom sheet: spring 320/34 (Compose `spring(dampingRatio = 0.85f, stiffness = 320f)`).
- Haptic 8 ms pada aksi tuntas (build sukses, APK terpasang) — satu kali, tidak berulang.
- `prefers-reduced-motion`/pengaturan OS dihormati: semua animasi jadi fade 80 ms.

## 7. Ikon

- Lucide-style stroke 1.5px, 20/24dp, warna `--text-dim`; aksen hanya bila aktif.
- **Tidak ada emoji sebagai ikon fitur.** Emoji hanya boleh di isi template milik user (mis. nama produk di katalog).
- Ikon status build: tabung/rekahan, bukan centang dekoratif.

## 8. Aksesibilitas (dipaksa)

- Target sentuh ≥ 48×48 dp, jarak antar target ≥ 8 dp.
- Semua ikon-tombol punya `contentDescription` bahasa Indonesia.
- Fokus keyboard urut logis; editor bisa dioperasikan penuh dengan keyboard eksternal (`Ctrl+S`, `Ctrl+B` build, `Ctrl+F` cari).
- Live region untuk status build (`aria`-ekuivalen: `liveRegion = Polite` pada Compose).
- Kontras diverifikasi di CI dengan snapshot theme → assert ≥ 4.5:1.

## 9. Checklist anti-slop (Gate 2, harus lulus semua)

- [ ] Tidak ada gradient ungu/indigo, tidak ada headline gradient.
- [ ] Tidak ada layout center-everything.
- [ ] Tidak ada baris 3 kartu identik.
- [ ] Tidak ada `transition-all`, tidak ada hover scale seragam.
- [ ] Tidak ada emoji sebagai ikon UI.
- [ ] Semua warna dari token OKLCH, tidak ada hex acak di komponen.
- [ ] Semua 8 state ada untuk komponen interaktif.
- [ ] Kontras ≥ 4.5:1 terukur.
- [ ] Semua copy bahasa Indonesia, tanpa buzzword ("seamless", "supercharge", "revolutionary").
- [ ] Layar 320dp diuji, tanpa overflow horizontal.

## 10. Inventaris layar (nyata, v0.9.6)

| Layar | Berkas | Inti | Catatan desain yang berlaku |
|---|---|---|---|
| Daftar projek | `ui/WorkspaceScreen.kt` | buka/buat/impor, cari projek | Baris padat + aksi "⋯" per baris; tombol "Buat projek" di **bottom bar** tetap, bukan item terakhir daftar |
| Wizard | `ui/WizardScreen.kt` | jenis → nama → template → buat | Tombol mati wajib punya `alasan-buat`; hanya pertanyaan yang perlu yang ditanyakan |
| Editor | `ui/EditorScreen.kt` | panel berkas + tab + kode + cari/ganti | Drawer di 360dp; bilah atas dua baris; tombol **Aksi** membuka palet perintah |
| Palet perintah | `ui/PaletPerintah.kt` | cari aksi | Daftar satu kolom, tanpa ikon dekoratif, `aksi-kosong` menyebut kata yang gagal |
| Build | `ui/BuildScreen.kt` | langkah, log, galat, bagikan APK | Log dibatasi baris (mode hemat); kartu Bagikan APK **di atas** pesan setup |
| Situs | `ui/SitusScreen.kt` | susun, terbit, PWA | Tautan hasil + tombol salin; galat terbit di dalam panel yang sama |
| Pratinjau situs | `ui/PratinjauSitus.kt` | WebView `file://` | Dialog penuh; catatan batas `file://` (PWA/service worker) ditampilkan apa adanya |
| Git | `ui/GitScreen.kt` | commit, kirim, riwayat | Tombol kirim mati dengan alasan bila repo/token belum lengkap |
| Agen | `ui/AgenScreen.kt` | tugas, putaran, hentikan | Log per putaran berurutan; tombol Hentikan benar-benar membatalkan |
| AI | `ui/AiScreen.kt` | tanya, lampirkan, foto, sisipkan kode | Galat penyedia diterjemahkan; foto kamera diperkecil sebelum dikirim |

## 11. Yang berubah sejak dokumen ini dikunci

| Keputusan awal | Kenyataan sekarang | Alasan |
|---|---|---|
| Light mode ditunda sampai M4 | **tidak pernah dibuat** | Sasaran pengguna memakai HP murah malam hari; menambah light mode berarti menguji 2× palet kontras tanpa bukti kebutuhan |
| Ikon Lucide stroke | sebagian diganti **teks tombol** di bilah atas | Di 360dp, tombol teks lebih cepat dipahami daripada ikon tanpa label |
| Bottom sheet build dengan 3 snap | tetap, tetapi tombol aksi ada di **bottom bar** | Aksi yang harus ditekan tidak boleh ikut tergulir |
| `--surface-2` untuk baris terpilih | dipakai juga untuk bilah atas | Tidak menambah token baru hanya demi satu perbedaan 0,05 lightness |
| "FAB hanya satu per layar" | dihapus; tidak ada FAB di aplikasi | FAB menutupi kode dan tidak bisa dijangkau jempol di layar sempit |
