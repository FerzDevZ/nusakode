package id.kodenusa.app.ui

/**
 * Daftar aksi untuk palet perintah di editor.
 *
 * Kenapa dipisah dari `PaletPerintah.kt`: daftar ini tahu urusan projek (build, situs, pratinjau),
 * sedangkan dialognya hanya tahu cara mencari dan menampilkan. Memisahkannya membuat pencarian bisa
 * diuji tanpa Activity.
 *
 * Urutan sengaja: aksi yang paling sering dipakai lebih dulu, karena di layar HP daftar teratas
 * adalah yang paling mungkin ditekan sebelum pengguna sempat mengetik.
 */
@Suppress("LongParameterList") // Satu lambda per aksi; memecahnya jadi kelas hanya menambah lapisan.
fun daftarPerintah(
    onSimpan: () -> Unit,
    onCari: () -> Unit,
    onBuild: () -> Unit,
    onSitus: () -> Unit,
    onPratinjau: () -> Unit,
    onBerkasBaru: () -> Unit,
    onImpor: () -> Unit,
    onTemaGelap: () -> Unit,
    onTemaTerang: () -> Unit,
    onTemaSistem: () -> Unit,
    webMurni: Boolean,
    adaSitus: Boolean,
): List<Perintah> {
    val hasil = mutableListOf(
        Perintah(
            id = "simpan",
            judul = "Simpan semua berkas",
            keterangan = "Berkas juga tersimpan otomatis setelah berhenti mengetik.",
            alias = listOf("save", "simpan"),
            jalankan = onSimpan,
        ),
        Perintah(
            id = "cari",
            judul = "Cari di seluruh projek",
            keterangan = "Termasuk ganti semua, peka huruf besar/kecil.",
            alias = listOf("find", "ganti", "replace"),
            jalankan = onCari,
        ),
        Perintah(
            id = "berkas-baru",
            judul = "Berkas baru",
            keterangan = "Dibuat di folder berkas yang sedang terbuka.",
            alias = listOf("new file", "buat"),
            jalankan = onBerkasBaru,
        ),
        Perintah(
            id = "impor",
            judul = "Impor berkas dari HP",
            keterangan = "Foto, CSS, logo, .java — masuk ke folder berkas aktif.",
            alias = listOf("ambil", "upload", "saf"),
            jalankan = onImpor,
        ),
        Perintah(
            id = "tema-gelap",
            judul = "Tema gelap",
            keterangan = "Langsung memasang tema gelap bengkel tanpa membuka layar Tema.",
            alias = listOf("tema", "gelap", "dark"),
            jalankan = onTemaGelap,
        ),
        Perintah(
            id = "tema-terang",
            judul = "Tema terang",
            keterangan = "Langsung memasang tema terang kertas.",
            alias = listOf("terang", "light"),
            jalankan = onTemaTerang,
        ),
        Perintah(
            id = "tema-sistem",
            judul = "Tema ikut sistem",
            keterangan = "Gelap saat malam Android menyala, terang saat siang.",
            alias = listOf("sistem", "otomatis"),
            jalankan = onTemaSistem,
        ),
    )
    if (webMurni) {
        hasil += Perintah(
            id = "susun-situs",
            judul = "Susun situs",
            keterangan = "Menyiapkan folder situs siap unggah.",
            alias = listOf("web", "situs", "publish"),
            jalankan = onSitus,
        )
    } else {
        hasil += Perintah(
            id = "build",
            judul = "Build APK",
            keterangan = "Kompilasi di HP dengan ECJ + dx + apksigner.",
            alias = listOf("apk", "bangun", "kompilasi", "compile"),
            jalankan = onBuild,
        )
    }
    if (adaSitus) {
        hasil += Perintah(
            id = "pratinjau",
            judul = "Pratinjau situs",
            keterangan = "Membuka index.html projek tanpa keluar aplikasi.",
            alias = listOf("lihat", "preview", "browser"),
            jalankan = onPratinjau,
        )
    }
    return hasil
}
