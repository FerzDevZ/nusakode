# Visi produk

> **Satu kalimat:** kalau seseorang hanya punya HP Android, ia tetap harus bisa membuat aplikasi Android sendiri — hari itu juga, tanpa laptop, tanpa Android Studio, tanpa menunggu sinyal bagus.

## Masalah nyata

Di Indonesia, HP adalah komputer pertama bagi banyak orang. Android Studio butuh minimal 8 GB RAM, puluhan GB disk, dan unduhan bertahap yang bisa mencapai beberapa GB. Bagi pengguna HP 2–4 GB dengan kuota terbatas, itu bukan "mahal" — itu **tidak mungkin**.

Akibatnya ada dua kelompok yang tertahan:

1. **Pemula yang ingin belajar.** Ia bisa menulis kode di editor HP, tetapi tidak bisa melihat hasilnya jalan. Tanpa umpan balik, belajar berhenti di tengah.
2. **Pemilik usaha kecil yang butuh alat sendiri.** Kasir, katalog, absensi. Solusi yang ada menuntut langganan atau PC.

KodeNusa mengambil jalur yang selama ini dianggap mustahil: **mengompilasi di dalam HP itu sendiri**.

## Untuk siapa

| Pengguna | Kebutuhan utama | Yang KodeNusa berikan |
|---|---|---|
| Pemula (13–25 tahun) | Melihat kode sendiri jadi APK | Template + build 1,4–1,9 detik + pesan galat Bahasa Indonesia |
| Pemilik warung / toko | Aplikasi kasir atau katalog tanpa langganan | Template Kasir Warung dengan SQLite lokal |
| Pengguna yang sering putus internet | Bekerja tanpa jaringan | Toolchain di dalam APK; AI dan GitHub opsional |
| Pembelajar web | Situs statis cepat | Projek `web-murni` tanpa Android sama sekali |

## Janji yang dipegang

1. **RAM 4 GB cukup.** Ambang mode hemat ditentukan dari RAM yang benar-benar dilaporkan sistem (`AMBANG_RAM_BYTE = 4.200.000.000`), bukan angka pemasaran.
2. **Offline itu fitur, bukan mode cadangan.** Semua yang dibutuhkan untuk membangun APK ada di dalam APK. AI dan GitHub hanya menyala kalau Anda mengisinya.
3. **Tidak ada server KodeNusa.** Tidak ada akun, tidak ada telemetri, tidak ada upload tersembunyi.
4. **Galat dijelaskan dalam Bahasa Indonesia.** Setiap pesan galat build melewati `ErrorCatalog`: judul, berkas, baris, dan cara memperbaiki.
5. **Batas disebut di layar, bukan disembunyikan.** Python, Kotlin, dan projek Gradle tidak didukung; aplikasi mengatakan itu saat Anda menyentuhnya.

## Bukan tujuan (dan alasannya)

| Tidak dikerjakan | Alasan |
|---|---|
| Kotlin di perangkat | `kotlinc` 70 MB+ menaikkan APK ~2,5×; JVM penuh hanya layak di HP ≥ 4 GB (ADR-003) |
| Model AI di dalam HP | 400–900 MB, 2–8 token/detik di 4 GB (ADR-023) |
| Terminal bebas | Permukaan serangan terlalu besar untuk aplikasi yang dibagikan lewat file APK (ADR-008) |
| Memilih folder arbitrer sebagai lokasi projek | SAF tidak memberi pohon berkas yang stabil; diganti ekspor/impor zip (ADR-022) |
| Play Store lebih dulu | APK langsung bisa dibagikan hari ini; Play Store menambah waktu review (ADR-010) |

## Ukuran keberhasilan

Bukan jumlah unduhan. Yang diukur:

1. **Satu template bisa jadi APK di HP 4 GB dalam < 5 detik** (tercapai: 1,4–1,9 detik untuk build hangat).
2. **Pesan RAM diam < 100 MB** (tercapai: 66 MB PSS).
3. **Pengguna baru bisa membuat APK pertamanya tanpa membaca dokumentasi** (diuji lewat alur wizard + build di uji perangkat).
4. **Nol janji palsu di UI** — setiap klaim di layar punya kode dan uji di belakangnya.

## Prinsip desain yang mengikat

- **Tidak ada tombol mati tanpa alasan.** Tombol yang tidak bisa ditekan wajib menampilkan sebabnya (`alasan-buat`).
- **Setiap aksi punya kabar.** Hasil aksi yang dipicu dari panel muncul **di dalam panel itu**, bukan di tempat lain.
- **Berkas pengguna tidak pernah disanitasi diam-diam.** Nama atau jalur yang salah ditolak dengan alasan tertulis.
- **Rahasia disandikan di disk.** Kunci AI dan token GitHub memakai Android Keystore (ADR-026).
- **Kegagalan dijelaskan, bukan ditelan.** Tidak ada `catch` kosong; tidak ada `TODO` di rilis.