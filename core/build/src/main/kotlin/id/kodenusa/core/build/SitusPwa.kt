package id.kodenusa.core.build

import java.io.File

/**
 * Menyiapkan projek **situs murni** supaya bisa dipasang di HP dan dibuka tanpa internet (PWA).
 *
 * Yang dibuat hanya tiga hal, semuanya di akar situs:
 * 1. `manifest.json` — nama, warna, dan mode `standalone`, sehingga Chrome menawarkan "Tambahkan ke
 *    layar utama" seperti aplikasi.
 * 2. `service-worker.js` — menyimpan berkas situs di cache HP, jadi tautan tetap terbuka saat sinyal
 *    hilang. Ini kebutuhan nyata pengguna di Indonesia, bukan sekadar nilai tambah.
 * 3. Satu baris `<script>` pendaftar di `index.html` (hanya bila belum ada).
 *
 * Aturan yang dipegang:
 * - **Tidak pernah menimpa** `manifest.json` atau `service-worker.js` yang sudah dibuat pengguna;
 *   keduanya dilaporkan sebagai "dilewati" agar tidak ada perubahan tanpa sepengetahuan pemilik.
 * - **Tetap nol berkas Android** (ADR-025): tidak ada `src/`, tidak ada `kodenusa.json` baru.
 * - Ikon hanya didaftarkan bila berkasnya benar-benar ada; manifest yang menunjuk berkas hilang
 *   membuat Chrome menolak memasang PWA.
 */
object SitusPwa {

    const val NAMA_MANIFEST = "manifest.json"
    const val NAMA_SERVICE_WORKER = "service-worker.js"
    const val NAMA_INDEX = "index.html"

    /** Warna bawaan mengikuti aksen KodeNusa. */
    const val TEMA_BAWAAN = "#f2a03d"
    const val LATAR_BAWAAN = "#14110f"

    data class Hasil(
        val dibuat: List<String>,
        val dilewati: List<String>,
        val indexDiubah: Boolean,
        val jumlahDiberkas: Int,
        val catatan: String? = null,
    ) {
        val ringkas: String
            get() = buildString {
                append("$jumlahDiberkas berkas disiapkan untuk offline")
                if (dibuat.isNotEmpty()) append(" · dibuat: ${dibuat.joinToString(", ")}")
                if (dilewati.isNotEmpty()) append(" · dibiarkan: ${dilewati.joinToString(", ")}")
                catatan?.let { append(" · $it") }
            }
    }

    /**
     * @param akar akar projek (folder yang memuat `index.html`)
     * @param nama nama yang tampil di layar utama
     * @param tema warna bilah (theme color)
     * @param latar warna splash (background color)
     */
    fun siapkan(
        akar: File,
        nama: String = akar.name,
        tema: String = TEMA_BAWAAN,
        latar: String = LATAR_BAWAAN,
    ): Hasil {
        // Templat "situs murni" menyimpan halaman di `web/`, jadi berkas PWA harus ikut ke sana —
        // bukan ke akar projek, tempat tidak ada index.html dan pendaftar tidak akan pernah jalan.
        val folder = SitusStatis.sumber(akar) ?: akar
        val dibuat = mutableListOf<String>()
        val dilewati = mutableListOf<String>()
        val aset = asetSitus(folder)
        val ikon = ikonSitus(folder)

        val manifest = File(folder, NAMA_MANIFEST)
        if (manifest.exists()) {
            dilewati += NAMA_MANIFEST
        } else {
            manifest.writeText(isiManifest(nama.ifBlank { akar.name }, tema, latar, ikon))
            dibuat += NAMA_MANIFEST
        }

        val pekerja = File(folder, NAMA_SERVICE_WORKER)
        if (pekerja.exists()) {
            dilewati += NAMA_SERVICE_WORKER
        } else {
            pekerja.writeText(isiServiceWorker(nama.ifBlank { akar.name }, aset))
            dibuat += NAMA_SERVICE_WORKER
        }

        val index = File(folder, NAMA_INDEX)
        val indexDiubah = if (index.exists()) sisipkanPendaftar(index) else false

        return Hasil(
            dibuat = dibuat,
            dilewati = dilewati,
            indexDiubah = indexDiubah,
            jumlahDiberkas = aset.size,
            catatan = when {
                !index.exists() -> "index.html belum ada, jadi pendaftaran service worker dilewati"
                ikon.isEmpty() -> "belum ada ikon PNG/SVG; PWA tetap jalan offline tetapi belum bisa dipasang"
                else -> null
            },
        )
    }

    /** Berkas yang ikut disimpan di cache: halaman, gaya, skrip, gambar, data kecil. */
    fun asetSitus(akar: File): List<String> = akar.walkTopDown()
        .onEnter { it == akar || it.name !in DIBUANG }
        .filter { it.isFile }
        .filter { it.length() <= MAKS_ASET_BYTE }
        .filter { it.extension.lowercase() in EKSTENSI }
        .map { it.relativeTo(akar).invariantSeparatorsPath }
        .filter { it != NAMA_SERVICE_WORKER }
        .sorted()
        .toList()

    /** Ikon yang benar-benar ada di akar situs, dipakai manifest. */
    fun ikonSitus(akar: File): List<String> = akar.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() in IKON }
        .map { it.name }
        .sorted()
        .toList()

    private fun isiManifest(nama: String, tema: String, latar: String, ikon: List<String>): String {
        val daftarIkon = if (ikon.isEmpty()) {
            ""
        } else {
            val butir = ikon.joinToString(",\n") { berkas ->
                val tipe = when (berkas.substringAfterLast('.', "").lowercase()) {
                    "svg" -> "image/svg+xml"
                    "webp" -> "image/webp"
                    "jpg", "jpeg" -> "image/jpeg"
                    else -> "image/png"
                }
                // "any" dipakai untuk SVG karena ukurannya bebas; PNG tetap dicantumkan angkanya bila
                // namanya menyebut ukuran (mis. ikon-192.png).
                val ukuran = Regex("(\\d{2,4})").find(berkas)?.groupValues?.get(1) ?: "any"
                """    {"src": "$berkas", "sizes": "$ukuran", "type": "$tipe", "purpose": "any"}"""
            }
            "\n  \"icons\": [\n$butir\n  ],"
        }
        return """
{
  "name": "$nama",
  "short_name": "${nama.take(12)}",
  "start_url": "./",
  "scope": "./",
  "display": "standalone",
  "orientation": "any",
  "background_color": "$latar",
  "theme_color": "$tema",$daftarIkon
  "lang": "id"
}
""".trimStart()
    }

    private fun isiServiceWorker(nama: String, aset: List<String>): String {
        val daftar = aset.joinToString(",\n") { "  \"./$it\"" }
        val versi = nama.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "situs" }
        return """
// Service worker KodeNusa — dibuat otomatis, boleh disunting sendiri.
// Tugasnya satu: menyimpan berkas situs di HP supaya tetap terbuka saat internet mati.
const CACHE = "$versi-v1";
const ASET = [
$daftar
];

self.addEventListener("install", (peristiwa) => {
  peristiwa.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(ASET)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (peristiwa) => {
  peristiwa.waitUntil(
    caches.keys()
      .then((kunci) => Promise.all(kunci.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (peristiwa) => {
  const permintaan = peristiwa.request;
  if (permintaan.method !== "GET" || new URL(permintaan.url).origin !== self.location.origin) return;
  peristiwa.respondWith(
    caches.match(permintaan).then((tersimpan) => {
      if (tersimpan) return tersimpan;
      return fetch(permintaan)
        .then((jawaban) => {
          const salinan = jawaban.clone();
          caches.open(CACHE).then((cache) => cache.put(permintaan, salinan));
          return jawaban;
        })
        .catch(() => caches.match("./index.html"));
    })
  );
});
""".trimStart()
    }

    /** Menyisipkan pendaftar service worker sekali saja; index.html lain tidak disentuh. */
    private fun sisipkanPendaftar(index: File): Boolean {
        val isi = runCatching { index.readText() }.getOrNull() ?: return false
        if (isi.contains("serviceWorker.register")) return false
        val skrip = """<script>if('serviceWorker' in navigator){window.addEventListener('load',""" +
            """function(){navigator.serviceWorker.register('service-worker.js');});}</script>"""
        val posisi = isi.lastIndexOf("</body>", ignoreCase = true)
        val baru = if (posisi >= 0) {
            isi.substring(0, posisi) + skrip + "\n" + isi.substring(posisi)
        } else {
            // HTML tanpa </body> tetap sah; skrip ditaruh di akhir agar tetap dimuat.
            isi + "\n" + skrip + "\n"
        }
        index.writeText(baru)
        return true
    }

    private val EKSTENSI = setOf(
        "html", "htm", "css", "js", "mjs", "json", "svg", "png", "jpg", "jpeg", "webp", "gif", "ico",
        "woff", "woff2", "ttf", "webmanifest",
    )

    private val IKON = setOf("png", "svg", "webp", "jpg", "jpeg")

    private val DIBUANG = setOf(".git", ".gradle", "build", "node_modules", "dist", ".tools")

    /** Aset di atas 2 MB tidak diprecache agar tidak menghabiskan kuota dan RAM HP 2 GB. */
    private const val MAKS_ASET_BYTE = 2_000_000L
}
