# Evaluasi agen AI (A5)

Pertanyaan yang dijawab dokumen ini: **apakah agen "buat fitur → build → perbaiki galat" benar-benar
menyelesaikan tugas, atau hanya terlihat pintar di demo?**

Tanpa angka, setiap perubahan prompt berisiko menjadi regresi yang tidak terlihat. Karena itu ada
10 tugas tetap dan satu cara menjalankannya.

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

Yang tetap **belum** terbukti sampai `AI_EVAL=1` dijalankan dengan kunci asli: kecerdasan model
sungguhan, waktu, dan jumlah putaran. Kolom itu masih kosong di bawah — memang belum diukur.

## Hasil terakhir

| Tanggal | Model | Lulus | Median waktu | Catatan |
|---|---|---|---|---|
| _(belum dijalankan — butuh kunci AI pengguna)_ | | | | |

## Ambang rilis

- **≥ 7/10** tugas selesai tanpa disentuh.
- **Median ≤ 2 menit** per tugas.
- **Tidak ada** tugas yang membuat berkas di luar projek (dijaga `AiProjectBoard` + `UbahanBerkas`).

Kalau salah satu ambang gagal, perbaiki prompt (`PromptBuilder.untukAgen`) atau batas putaran
(`AgenBuild.MAKS_PUTARAN_BAWAAN`), lalu ukur ulang — jangan menaikkan ambangnya.
