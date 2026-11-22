# Pelajaran kasir-warung

Langkah belajar aplikasi kedua. Ditulis manusia, bukan dihasilkan AI (docs/14 3.3):
satu langkah = satu aksi kecil yang benar-benar bisa dilakukan di layar editor.
Aplikasi ini menyimpan setiap transaksi ke basis data SQLite di dalam HP dan
mengekspornya ke berkas CSV di folder Unduhan.

## 1. Kenali layar editor
Di sebelah kiri ada daftar berkas projekmu. Ketuk `MainActivity.java` untuk membukanya.
Cari baris `simpanTransaksi()` — itulah yang dijalankan setiap kali tombol Tambah ditekan.

## 2. Ubah judul total
Buka `res/values/strings.xml`, lalu ubah teks `Total hari ini` pada baris
`judul_total` menjadi `Penjualan hari ini`. Teks yang pengguna lihat di atas
layar kasir berubah ikut dengannya.

## 3. Ubah tombol tambah
Masih di `strings.xml`, ubah teks `Tambah` pada baris `tombol_tambah` menjadi
`Catat`. Perhatikan bahwa kode Java tidak perlu diubah: tombolnya membaca teks
dari `strings.xml`, bukan dari kode.

## 4. Lihat tempat data disimpan
Buka `DatabaseKasir.java`. Cari perintah `CREATE TABLE` — tabel `transaksi`
menyimpan `nama`, `harga`, dan `waktu` setiap penjualan. Data ini tinggal di
dalam HP, walaupun internet mati.

## 5. Bangun APK
Tekan tombol Bangun di kanan atas, lalu tunggu sampai muncul "APK jadi". HP-mu
sedang menerjemahkan kodemu menjadi aplikasi sungguhan.

## 6. Coba kasirmu
Setelah APK jadi, ketuk tombol Pasang dan buka aplikasimu. Isi nama barang dan
harganya, tekan Catat, lalu lihat total dan daftar transaksi di layar.

## 7. Ekspor ke CSV
Tekan tombol Ekspor CSV di aplikasimu. Di Android 10 ke atas berkas
`kasir-warung.csv` tersimpan di folder Unduhan; di Android yang lebih lama
aplikasi menawarkan untuk mengirim isinya lewat pesan.
