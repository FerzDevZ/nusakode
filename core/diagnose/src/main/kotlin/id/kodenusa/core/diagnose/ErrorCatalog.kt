package id.kodenusa.core.diagnose

/**
 * Kalimat yang dilihat user. [title] singkat dan menyebut masalah, [explanation] menjelaskan
 * kenapa, [fix] adalah satu tindakan konkret. Tidak ada "Unknown error".
 */
data class LocalizedError(
    val messageId: String,
    val title: String,
    val explanation: String,
    val fix: String,
)

object ErrorCatalog {

    const val UNKNOWN = "err.unknown"

    private val entries: Map<String, LocalizedError> = listOf(
        LocalizedError(
            "sym.not_found",
            "Ada nama yang tidak dikenali",
            "Kamu memakai sebuah class, variabel, atau method yang belum dideklarasikan atau salah tulis.",
            "Periksa ejaan dan huruf besar/kecil. Kalau berasal dari library, tambahkan JAR-nya di dependencies.",
        ),
        LocalizedError(
            "sym.package_missing",
            "Library belum ditambahkan",
            "Kode memakai package yang tidak ada di classpath projek ini.",
            "Tambahkan file JAR library tersebut ke folder projek lalu daftarkan di kodenusa.json.",
        ),
        LocalizedError(
            "type.incompatible",
            "Tipe data tidak cocok",
            "Nilai yang dimasukkan berbeda tipe dengan yang diminta.",
            "Ubah tipe variabelnya, atau konversi nilainya (mis. Integer.parseInt untuk teks ke angka).",
        ),
        LocalizedError(
            "type.abstract_missing",
            "Method wajib belum diisi",
            "Class ini mewarisi class/interface abstrak sehingga semua method wajibnya harus ditulis.",
            "Tambahkan method yang kurang, atau hapus kata kunci extends/implements.",
        ),
        LocalizedError(
            "type.return_missing",
            "Method tidak mengembalikan nilai",
            "Method dengan tipe kembalian bukan void harus selalu mengembalikan nilai di semua jalur.",
            "Tambahkan return di akhir method atau di setiap cabang if.",
        ),
        LocalizedError(
            "syntax.expected",
            "Ada tanda baca yang kurang",
            "Compiler mengharapkan karakter tertentu (; { } atau )) tetapi tidak menemukannya.",
            "Periksa baris yang ditandai: biasanya titik koma hilang atau kurung tidak berpasangan.",
        ),
        LocalizedError(
            "syntax.unclosed",
            "Kurung atau kutip belum ditutup",
            "Ada blok kode, string, atau komentar yang dibuka tetapi tidak ditutup.",
            "Hitung ulang pasangan { } dan \" \" pada file ini.",
        ),
        LocalizedError(
            "syntax.illegal_start",
            "Karakter atau kata tidak boleh di posisi ini",
            "Ada teks di luar struktur class/method, atau file berisi karakter tak terlihat.",
            "Pastikan semua kode berada di dalam method, dan tidak ada karakter aneh hasil salin-tempel.",
        ),
        LocalizedError(
            "res.attr_missing",
            "Atribut layout tidak ada",
            "Kamu memakai atribut yang tidak dikenal oleh versi Android target.",
            "Periksa ejaan atribut. Untuk atribut baru, naikkan minSdk atau gunakan app: namespace.",
        ),
        LocalizedError(
            "res.not_found",
            "Resource tidak ditemukan",
            "Layout, string, atau gambar yang dirujuk tidak ada di folder res.",
            "Pastikan nama file sama persis (huruf kecil, tanpa spasi) dan ada di folder res yang benar.",
        ),
        LocalizedError(
            "res.compile_failed",
            "Resource gagal dikompilasi",
            "aapt2 menolak salah satu file di folder res.",
            "Buka file yang disebut di pesan ini; biasanya ada tag XML tidak tertutup atau nama file tidak valid.",
        ),
        LocalizedError(
            "res.manifest_invalid",
            "AndroidManifest.xml bermasalah",
            "Manifest tidak valid atau merujuk package/activity yang tidak ada.",
            "Pastikan ada satu <activity> dengan android:name yang cocok dengan class MainActivity.",
        ),
        LocalizedError(
            "res.duplicate",
            "Resource duplikat",
            "Ada dua file resource dengan nama sama di folder yang berbeda.",
            "Hapus atau ganti nama salah satunya; nama resource harus unik per tipe.",
        ),
        LocalizedError(
            "dex.duplicate_class",
            "Class ganda",
            "Class yang sama muncul lebih dari sekali — biasanya JAR didaftarkan dua kali.",
            "Hapus salah satu JAR dari dependencies di kodenusa.json.",
        ),
        LocalizedError(
            "dex.failed",
            "Gagal mengubah ke dex",
            "dx tidak bisa memproses hasil kompilasi, umumnya karena bytecode memakai fitur yang " +
                "belum didukung dexer offline.",
            "Ganti lambda (->) dengan anonymous class, atau naikkan projek ke build cloud.",
        ),
        LocalizedError(
            "dex.lambda",
            "Lambda belum didukung di build offline",
            "Dexer bawaan (dx) belum mengenal invokedynamic yang dihasilkan lambda dan method reference.",
            "Tulis ulang lambda sebagai anonymous class, atau bangun projek ini lewat build cloud.",
        ),
        LocalizedError(
            "sdk.too_low",
            "API terlalu baru untuk minSdk",
            "Kode memakai API Android yang belum ada di minSdk projek ini.",
            "Naikkan minSdk di kodenusa.json, atau bungkus pemanggilan dengan pemeriksaan Build.VERSION.SDK_INT.",
        ),
        LocalizedError(
            "sign.failed",
            "Gagal menandatangani APK",
            "Keystore tidak bisa dibuka atau password/alias tidak cocok.",
            "Gunakan keystore debug bawaan, atau periksa ulang alias dan password keystore release-mu.",
        ),
        LocalizedError(
            "align.failed",
            "Gagal menyelaraskan APK",
            "File APK hasil pembungkusan rusak atau tidak bisa dibaca.",
            "Build ulang projek. Kalau tetap gagal, hapus folder build/ lalu coba lagi.",
        ),
        LocalizedError(
            "warn.deprecated",
            "API usang (peringatan)",
            "Kamu memakai API yang sudah usang; masih jalan tetapi akan dihapus nanti.",
            "Ganti dengan API pengganti yang disarankan dokumentasi Android.",
        ),
        LocalizedError(
            "warn.unchecked",
            "Operasi generik tanpa pemeriksaan (peringatan)",
            "Compiler tidak bisa memastikan tipe aman pada operasi generik.",
            "Tambahkan parameter tipe, atau abaikan bila kamu yakin aman.",
        ),
        LocalizedError(
            "warn.unreachable",
            "Kode tidak pernah dijalankan (peringatan)",
            "Ada baris setelah return/throw yang tidak akan pernah dieksekusi.",
            "Hapus baris tersebut atau pindahkan sebelum return.",
        ),
        LocalizedError(
            "io.failed",
            "File tidak bisa dibaca/ditulis",
            "Proses build tidak punya akses ke salah satu file projek.",
            "Pastikan folder projek tidak terhapus dan penyimpanan masih punya ruang kosong.",
        ),
        LocalizedError(
            UNKNOWN,
            "Build gagal",
            "Penyebabnya belum dikenali katalog KodeNusa.",
            "Buka detail mentah di panel log, lalu laporkan pesan tersebut agar bisa ditambahkan ke katalog.",
        ),
    ).associateBy { it.messageId }

    fun get(messageId: String): LocalizedError = entries[messageId] ?: entries.getValue(UNKNOWN)

    fun contains(messageId: String): Boolean = entries.containsKey(messageId)

    fun all(): List<LocalizedError> = entries.values.sortedBy { it.messageId }
}
