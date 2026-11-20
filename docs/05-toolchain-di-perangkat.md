# Toolchain Tier 0 di perangkat

Dokumen ini mencatat apa yang sudah terbukti **di HP**, apa yang belum, dan aturan yang tidak boleh
dilanggar saat mengganti komponen.

## Status terakhir

| Bukti | Hasil |
|---|---|
| `aapt2` bionic menjalankan `aapt2 version` di API 24 | lolos (`2.19-6696031`) |
| `ecj.jar` mengompilasi `Halo.java` lewat `app_process` | lolos, `app/Halo.class` dihasilkan |
| `dx.jar` terelokasi men-dex `Halo.class` | lolos, `classes.dex` 728 byte |
| `apksigner` 0.8 menandatangani zip + verifikasi v2 | lolos |
| Keystore PKCS12 buatan Android (`DebugKeystore`) diterima apksigner 0.8 | lolos, 3 template tetap terbangun |
| **Tiga template terbangun penuh di dalam emulator API 24** | `halo-dunia` 12.566 B / 1,7 s · `kasir-warung` 16.662 B / 1,9 s · `katalog-whatsapp` 12.566 B / 1,7 s |
| `:app:connectedDebugAndroidTest` (14 uji) | semuanya hijau; keystore dibuat di perangkat dan dipakai apksigner 0.8 |

Angka di atas berasal dari emulator x86_64 API 24 dengan RAM 3 GB. Pengukuran pada HP 4 GB
(Redmi 9A) masih menjadi tugas P4.

## Komponen yang dibundel

| Komponen | Berkas | Ukuran di aset | Keterangan |
|---|---|---|---|
| Resource compiler + linker | `aapt2` (bionic, 3 ABI) | 3,2–5,0 MB per ABI | build-bridge pihak ketiga, API 34 |
| Kompiler Java | `ecj.jar` 3.25.0 + stub `javax.lang.model` | 1,5 MB | EPL-2.0, lihat ADR-016 |
| Dexer | `dx.jar` 1.16 terelokasi | 379 KB | Apache-2.0, lihat ADR-017 |
| Signer | `apksigner.jar` 0.8 | 150 KB | Apache-2.0, lihat ADR-018 |
| Platform | `android.jar` API 34 | 26 MB | Apache-2.0, lihat ADR-015 |
| Keystore debug | dibuat `DebugKeystore` per perangkat | 0 KB (tidak dibundel) | ADR-019 |

Total aset bersama 28 MB; kalau ditambah tiga ABI, `app-debug.apk` menjadi 48 MB. Batas rilis
adalah 80 MB, jadi masih ada ruang, tetapi `android.jar` tetap kandidat pertama untuk dipangkas.

## Aturan yang wajib dijaga

1. **`app_process` butuh `/system/bin` sebagai argumen kedua.** Bentuk yang benar:
   `app_process /system/bin <kelas> <argumen…>`; tanpa itu ART memperlakukan nama kelas sebagai
   direktori lalu berhenti dengan `exit 134`.
2. **Jangan menjalankan d8/R8 di Android 7.** Semua rilisnya memanggil `java.nio.file.Paths`
   (API 26+). `exit 137` yang muncul adalah RuntimeInit yang membunuh proses setelah
   `ClassNotFoundException`, bukan kehabisan RAM.
3. **Jangan membuang resource saat repack jar.** ECJ memuat `messages.properties` lewat
   `ResourceBundle`; menghapusnya membuat setiap pemanggilan `Main` melempar
   `MissingResourceException` dan proses mati tanpa pesan.
4. **dx wajib direlokasi.** ART menaruh `com.android.dex` di bootclasspath, jadi kelas dx bundel
   tidak akan pernah dipakai tanpa relokasi jarjar.
5. **Keystore tidak dibundel.** Dibuat per perangkat di `filesDir/keys/` (ADR-019). Untuk host,
   CLI membuatnya sendiri di `.tools/debug.keystore`. Jangan menyalin keystore ke `assets/`.
6. **Tanda tangan v2 saja.** v1 (JAR signing) membuat ART membunuh apksigner; v3 belum dikenal
   apksigner 0.8.

## Yang masih kurang

1. **Android 10+ (W^X).** `exec` dari `filesDir` bisa diblokir. Rencana: pindahkan `aapt2` ke
   `nativeLibraryDir` sebagai `libaapt2.so` atau pakai `libsu`. Harus dibuktikan di emulator API 29/35.
2. **Kunci milik pengguna.** Keystore sekarang dibuat per perangkat (ADR-019), tetapi belum bisa
   diimpor/diekspor pengguna. Langkah berikutnya: pilih keystore sendiri untuk rilis ke Play.
3. **Lambda.** dx tidak mendukung `invokedynamic`; pengguna mendapat diagnostik `dex.lambda`.
   Solusi jangka panjang: Tier 1 cloud atau dexer pengganti.
4. **Ukuran `android.jar`.** 26 MB adalah berkas terbesar di bundel; pemangkasan ke kelas yang
   dipakai (target < 12 MB) belum dikerjakan.

## Urutan kerja berikutnya

1. Uji `:app:connectedDebugAndroidTest` di emulator API 29/35 → putuskan W^X.
2. Ganti keystore bundel dengan generasi perangkat.
3. Ukur di HP fisik 4 GB: waktu build, RAM diam, ukuran APK.
4. Pangkas `android.jar` dan ukur ulang bundel.