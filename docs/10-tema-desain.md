# Rencana: Sistem Tema & Peningkatan Sistem Desain KodeNusa

> Dokumen rencana (belum ADR). Pelaksanaannya akan menghasilkan **ADR-035** (sistem tema) dan
> **ADR-036** (tema unduhan) sesuai aturan DESIGN.md: "perubahan arah visual = ADR baru".
> Sasaran: rilis **0.11.0**.

---

## 1. Diagnosis: kenapa terasa "terlalu dasar"

| Masalah | Bukti di kode | Dampak ke pengguna |
|---|---|---|
| Warna dikodekan langsung | `KodenusaPalette` = `object` konstanta `Int`; semua layar memanggil `Color(KodenusaPalette.ACCENT)` | Mustahil ganti tema tanpa menyentuh semua layar |
| Tema terkunci gelap | `Theme.kt` memakai `darkColorScheme()` tetap, komentar "selalu gelap" | HP diluar ruangan siang terang tetap gelap; tidak ada pilihan |
| Komponen minimum | Hanya `Panel`, `PrimaryButton`, `GhostButton`, `Pill`, `KodeField` | Setiap layar menumpuk `Row`/`Text` mentah, gaya berbeda-beda |
| Ikon berupa teks | `GhostButton("☰")`, menu "⋯" | Terlihat murah, tidak bisa diwarnai/diskalakan |
| Tidak ada gerak (motion) | Tidak ada animasi transisi antar layar/estado | Terasa kaku |
| Tidak ada tema kustom | Tidak ada format, tidak ada penyimpanan | Permintaan pengguna: bisa unduh/pasang tema |

---

## 2. Fase 0 — Fondasi token semantik (wajib lebih dulu)

Semua fase lain berdiri di atas ini. Tanpa fase 0, tema = refactor bencana.

### 2.1 Token semantik, bukan konstanta warna

Ganti `KodenusaPalette` (konstanta) dengan **`KodenusaTokens`** — data class 13 token yang
dibaca komponen lewat `CompositionLocal`:

```kotlin
data class KodenusaTokens(
    val background: Color, val surface: Color, val surfaceRaised: Color,
    val border: Color, val borderStrong: Color,
    val text: Color, val textDim: Color,
    val accent: Color, val accentInk: Color,
    val ok: Color, val warn: Color, val error: Color, val info: Color,
)
val LocalKodenusaTokens = staticCompositionLocalOf { GelapBengkel } // default aman
```

- `MaterialTheme.colorScheme` tetap diisi **dari** token (Material butuhnya), tetapi komponen
  KodeNusa membaca `LocalKodenusaTokens` — token inilah sumber kebenaran.
- Konstanta `Int` lama **tetap dipertahankan sementara** sebagai nilai tema "Gelap bengkel"
  supaya `ContrastTest` (JVM) tetap bisa mengukur tanpa emulator.

### 2.2 Migrasi bertahap yang bisa diverifikasi

1. `core/ui`: buat token + dua skema (gelap, terang) + `KodenusaTheme(mode)` baru.
2. `grep -rn "KodenusaPalette\." app/ core/` → ganti ke `LocalKodenusaTokens.current.…`
   (satu commit per modul, supaya diff bisa ditinjau).
3. `ContrastTest` diperluas: **setiap tema** diuji — pasangan teks/latar ≥ 4,5:1, batas
   komponen ≥ 3:1. Tema yang gagal = build gagal. Ini kunci utama tema terang yang aman.

---

## 3. Fase 1 — Tema bawaan (4 pilihan + ikut sistem)

| Tema | Latar | Permukaan | Teks | Aksen | Catatan |
|---|---|---|---|---|---|
| **Gelap bengkel** (default) | `oklch(0.18 0.012 250)` | `0.23` | `0.95` | amber `0.80 0.155 75` | Palet sekarang, tidak berubah |
| **Gelap AMOLED** | `#000000` | `oklch(0.14 …)` | tetap | amber tetap | Hemat baterai AMOLED; border dinaikkan agar tetap terlihat |
| **Terang kertas** | `oklch(0.98 0.004 250)` | `oklch(0.955 …)` | `oklch(0.22 0.02 250)` | **amber digelapkan** `oklch(0.60 0.13 75)` | Bukan inversi otomatis; aksen dihitung ulang agar ≥ 4,5:1 di latar terang |
| **Terang kontras tinggi** | `#FFFFFF` | `#F2F2F2` | `#111111` | `#8A4B00` | Untuk mata di luar ruangan / aksesibilitas |

Plus pilihan **"Ikuti sistem"** (default? tidak — default tetap **Gelap bengkel** agar
perubahan tidak mengejutkan pengguna lama; ikut sistem adalah pilihan).

### 3.1 Mekanisme

- `TemaStore` (SharedPreferences, **bukan** Keystore — tema bukan rahasia): `mode` +
  `namaTemaKustom`.
- **Anti-kilat**: `MainActivity` membaca mode **sebelum** `setContent` dan meneruskan ke
  `KodenusaTheme(mode)` — tidak ada frame putih/hitam yang salah.
- Layar pilihan tema: segmen 5 pilihan dengan **pratinjau kartu kecil** (bukan hanya nama) —
  kartu pratinjau memakai token tema itu sendiri, jadi pengguna melihat sebelum memilih.
- Editor & konsol Python: CodeEditorView (View, bukan Compose) punya warna sendiri →
  `Highlighter` menerima palet dari token juga, termasuk tema terang.

---

## 4. Fase 2 — Tema unduhan & kustom (`.kntema`)

### 4.1 Format berkas

```
nama-tema.kntema  ← JSON ≤ 4 KB:
{
  "versi": 1,
  "nama": "Senja Bandung",
  "gelap": true,
  "warna": {
    "background": "#0E1217", "surface": "#181E23", "surfaceRaised": "#232A30",
    "border": "#484E54", "borderStrong": "#6F757B",
    "text": "#EBEFF4", "textDim": "#A0A5AB",
    "accent": "#F7AD30", "accentInk": "#281600",
    "ok": "#61D19A", "warn": "#E7C952", "error": "#F75D59", "info": "#6AB8E4"
  }
}
```

### 4.2 Aturan penerimaan (kejujuran diterapkan ke tema)

1. Semua 13 warna wajib ada, hex 6 digit valid, ukuran ≤ 4 KB.
2. **Kontras diuji saat impor** — pasangan teks/latar < 4,5:1 → tema **ditolak** dengan pesan
   menyebut pasangan mana yang gagal ("Teks #888 terlalu redup di latar #101010 — 2,9:1,
   butuh ≥ 4,5:1"). Tema buruk tidak boleh masuk hanya karena pengguna mengimpornya.
3. Disimpan di `filesDir/tema/`; daftar tema kustom tampil di layar tema dengan tombol hapus.

### 4.3 Sumber tema

- **Impor berkas** (SAF picker — pola ekspor .zip yang sudah ada).
- **Unduh dari URL** (raw GitHub / server apa pun via `HttpURLConnection`, pola yang sudah
  dipakai klon repo) — opsional, tetap bisa tanpa internet.
- **Ekspor tema aktif** → bagikan `.kntema` via SAF (orang bisa saling berbagi tema offline).
- Tidak ada toko tema terpusat (sesuai prinsip: tanpa server KodeNusa).

### 4.4 UI layar Tema

- Baris tema: pratinjau mini (3 blok warna + teks contoh), nama, tanda "aktif".
- Aksi: Pakai · Hapus · Ekspor; tombol "Impor .kntema" dan "Unduh dari tautan".

---

## 5. Fase 3 — Naikkan kelas komponen & gerak

| Upgrade | Isi |
|---|---|
| **Ikon vektor** | Ganti `"☰"`, `"⋯"`, `"‹"` dengan `ImageVector` Material Symbols (offline, +beberapa KB) — ikut diwarnai tema |
| **Komponen baru** | `SegmentedControl` (pilihan tema, tab), `Banner` (info/warn/galat — menggantikan Row catatan manual di 4 layar), `EmptyState` (sudah ada, dijadikan komponen resmi), `ListItem` (daftar berkas/projek) |
| **State lengkap** | Setiap komponen interaktif punya 8 state (default, hover/fokus, aktif, dipilih, disabilitas, loading, galat, kosong) — sesuai standar anti-slop |
| **Motion** | Durasi standar 150 ms (micro) / 250 ms (layar), `FastOutSlowIn`; tombol pressed `scale 0,98`; transisi antar layar fade+slide halus; **hormati "kurangi gerak" sistem** |
| **Bentuk & elevasi** | Radius token sudah ada (8/12/18) — terapkan konsisten; elevasi hanya lewat `surfaceRaised`, bukan bayangan |
| **Tipografi** | Skala rapi: display/headline/title/body/label + `FontFamily.Monospace` khusus kode; ukuran minimum body 14sp |

---

## 6. Fase 4 — Verifikasi & rilis 0.11.0

1. **Kontras**: `ContrastTest` × 4 tema bawaan + sampel tema kustom valid/invalid.
2. **Uji perangkat**: tema tersimpan setelah matikan aplikasi; impor tema valid/terlalu
   kontras-rendah/gagal-JSON; anti-kilat (tema terang dari awal peluncuran).
3. **Bukti visual**: screenshot 5 layar × 4 tema (script adb), tinjauan penglihatan seperti
   iterasi 0.9.9, tersimpan `docs/ux-visual/`.
4. Gate penuh + instal rilis + rilis GitHub 0.11.0.

---

## 7. Estimasi & risiko

| Item | Perkiraan | Risiko |
|---|---|---|
| Fase 0 (token + migrasi) | terbesar — menyentuh semua layar | diff besar → pecah per modul |
| Fase 1 (4 tema + setelan) | sedang | aksen amber di terang: wajib diukur, bukan dikira |
| Fase 2 (.kntema) | sedang | tema buruk dari luar → gerbang kontras saat impor |
| Fase 3 (komponen+motion) | sedang | jangan menambah ukuran APK berarti (vektor, bukan font ikon) |
| Fase 4 (verifikasi) | kecil | — |

Ukuran APK: **+0 biner besar** — tema adalah data; ikon vektor +beberapa KB.

## 8. Yang tidak dilakukan (disiplin)

- Tidak ada tema warna-warni-warni acak: tetap 1 aksen + 3 warna status (DESIGN.md).
- Tidak ada ungu/indigo default, tidak ada gradient (anti-slop).
- Tidak ada tema server/toko tema (tanpa server KodeNusa).
- Tidak ada Material You dynamic color dulu (butuh API 31+, minSdk kita 24; bisa jadi ADR
  lanjutan bila diminta).
