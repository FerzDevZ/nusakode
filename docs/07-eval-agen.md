# Evaluasi agen AI (A5)

Pertanyaan yang dijawab dokumen ini: **apakah agen "buat fitur → build → perbaiki galat" benar-benar
menyelesaikan tugas, atau hanya terlihat pintar di demo?**

Tanpa angka, setiap perubahan prompt berisiko menjadi regresi yang tidak terlihat. Karena itu ada
10 tugas tetap dan satu cara menjalankannya.

**Status dokumen:** tetap berlaku sejak v0.19.0. Harness **siap dan lulus tanpa kunci**
(`EvalAgenJalurTest`, 10 tugas lewat loop `AgenBuild` dengan model tiruan). Tabel angka di bawah
sudah terisi dari **dua putaran nyata** (21 Nov 2026, penyedia `api.crown.ai.id`, model
`zai-org/GLM-5.3-Flash`): 9/10 dua-duanya, median 41,8 s lalu 18,6 s.

## Cara menjalankan

```bash
export JAVA_HOME=/opt/jdk17 PATH=/opt/jdk17/bin:/opt/gradle/bin:$PATH ANDROID_HOME=$HOME/android-sdk
AI_ENDPOINT=https://api.contoh.id/v1 AI_KEY=... AI_MODEL=... AI_EVAL=1 \
  ./gradlew :core:ai:test --tests '*EvalAgenTest*' --info
```

Tanpa `AI_EVAL=1` uji ini **dilewati**, jadi CI tanpa kunci tetap hijau.

Keluaran yang dicari, satu baris per tugas:

```
EVAL|judul-layout|12.4s|1 putaran|LULUS
EVAL|RINGKAS|8/10 tugas selesai tanpa disentuh
```

## Sepuluh tugas

Semua tugas sengaja berada di dalam batas **Tier 0** (Java 8, tanpa Gradle, tanpa AndroidX, tanpa
Kotlin), karena kemampuan itulah yang dijanjikan aplikasi.

| # | Nama | Yang diminta | Dianggap lulus bila |
|---|---|---|---|
| 1 | `judul-layout` | ubah teks judul di `activity_main.xml` | teks baru ada di layout |
| 2 | `tombol-simpan` | tambah tombol `tombolSimpan` | id dan teks ada |
| 3 | `method-hitung` | tambah `hitungTotal(int, int)` | method ada dan mengembalikan nilai |
| 4 | `toast-klik` | Toast "Data disimpan" saat diklik | `Toast.makeText` + teks ada |
| 5 | `warna-aksen` | warna `aksen` = `#F2A03D` | berkas `colors.xml` memuat keduanya |
| 6 | `string-app` | string `app_name` = "Kasir Warung" | `strings.xml` benar |
| 7 | `kelas-produk` | kelas `Produk` dengan `nama` dan `harga` | kelas + field ada |
| 8 | `layout-linear` | akar layout `LinearLayout` vertikal | tag + orientasi ada |
| 9 | `simpan-preferensi` | simpan total ke `SharedPreferences("warung")` | kunci benar |
| 10 | `perbaiki-galat` | perbaiki galat "cannot find symbol variable total" | variabel dideklarasikan |

## Batas yang harus jujur disebut

- Gerbang build pada evaluasi ini memeriksa **berkas dan kata kunci**, bukan kompilasi sungguhan.
  Bukti kompilasi tetap datang dari build di perangkat (`OnDeviceBuildTest`, 3 template).
- Skor 10/10 di sini **bukan** jaminan APK-nya jalan; itu sebabnya setiap tugas tetap punya padanan
  build nyata di perangkat sebelum dirilis.
- Model murah sering menolak permintaan panjang; kalau satu tugas gagal karena batas konteks, catat
  sebagai kegagalan model, bukan kegagalan agen — dan tulis nama modelnya.

## Yang sudah terbukti tanpa kunci API

`EvalAgenJalurTest` (JVM, jalan di CI) menjalankan **10 tugas yang sama** lewat loop `AgenBuild`
dengan model tiruan yang "sudah tahu" jawabannya. Tujuannya bukan mengukur kecerdasan model, tetapi
membuktikan jaringannya tidak putus:

- 10 dari 10 tugas benar-benar selesai lewat loop agen (bukan diketik manual di uji),
- berkas yang ditulis model mendarat di jalur yang diminta,
- gerbang build dipanggil setiap putaran,
- galat build sungguhan memicu putaran perbaikan, lalu berhenti jujur saat batas putaran habis,
- model yang menulis `../keluar.java` ditolak tanpa menyentuh disk.

Yang sudah terbukti dengan kunci asli (tabel di bawah): kecerdasan model sungguhan, waktu,
dan jumlah putaran — untuk model yang diukur. Model lain tetap harus diukur sendiri-sendiri.

## Hasil terakhir

| Tanggal | Model | Lulus | Median waktu | Catatan |
|---|---|---|---|---|
| 21 Nov 2026 (putaran 1) | `zai-org/GLM-5.3-Flash` (crown.ai.id) | **9/10** | 41,8 s | Semua tugas selesai di putaran pertama |
| 21 Nov 2026 (putaran 2) | `zai-org/GLM-5.3-Flash` (crown.ai.id) | **9/10** | 18,6 s | Konsisten dengan putaran 1 |

Dua-dua putaran: tugas `layout-linear` selalu gagal-penyedia (503 sesaat melewati 3× coba-ulang,
15/30/45 s) — kegagalan penyedia, bukan model; tugas yang sama lulus di pengukuran mock dan 9
tugas lainnya lulus dua-dua putaran nyata. Ambang rilis ≥ 7/10 dan median ≤ 2 menit **terpenuhi**.

Catatan jujur tentang penyedia: `api.crown.ai.id` kadang menjawab 503 selama beberapa menit
(terbukti saat pengukuran ini: 200 lalu 503 dalam hitungan menit). Uji sudah tahan-coba-ulang
(maks 3× per tugas dengan jeda bertingkat) dan mencatat kegagalan penyedia terpisah dari
kegagalan model supaya angka pass-rate tetap sah. Angka di atas dari model 5.3-Flash —
model lain belum diukur.

## Ambang rilis

- **≥ 7/10** tugas selesai tanpa disentuh.
- **Median ≤ 2 menit** per tugas.
- **Tidak ada** tugas yang membuat berkas di luar projek (dijaga `AiProjectBoard` + `UbahanBerkas`).

Kalau salah satu ambang gagal, perbaiki prompt (`PromptBuilder.untukAgen`) atau batas putaran
(`AgenBuild.MAKS_PUTARAN_BAWAAN`), lalu ukur ulang — jangan menaikkan ambangnya.
