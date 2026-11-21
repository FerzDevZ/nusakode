package id.kodenusa.app

import id.kodenusa.core.ai.PemilihKonteks
import id.kodenusa.core.ai.PetaProjek
import java.io.File

/**
 * Konteks otomatis untuk layar AI (docs/14 — "AI paham seluruh projek tanpa pilih 1-1").
 *
 * Satu panggilan [kumpulkan] melakukan:
 *  1. Pindai seluruh projek (folder build/.git dsb. dilewati — [PetaProjek.DILEWATI_PROJEK]).
 *  2. Susun peta projek (identitas, daftar berkas berperan, kerangka kode).
 *  3. Pilih berkas paling relevan terhadap pertanyaan (skor leksikal + bobot kepentingan),
 *     manifest dan berkas aktif selalu ikut.
 *
 * Semua deterministik dan tanpa jaringan. Ukuran total dihitung dan dilaporkan — UI menampilkan
 * angkanya apa adanya, bukan klaim "AI sudah paham semuanya".
 */
class KonteksOtomatis(private val akar: File) {

    data class Hasil(
        val peta: String,
        val berkas: List<Pair<String, String>>,
        val jumlahBerkasDipilih: Int,
        val jumlahKarakter: Int,
        val jumlahBerkasProjek: Int,
    )

    /** Baca isi berkas teks yang layak dikirim; berkas besar dipangkas di pemilih, bukan di sini. */
    fun kandidat(): List<PemilihKonteks.Kandidat> =
        akar.walkTopDown()
            .onEnter { it.name !in PetaProjek.DILEWATI_PROJEK }
            .filter { it.isFile && it.extension.lowercase() in EKSTENSI_TEKS }
            .map { satu ->
                val jalur = satu.relativeTo(akar).invariantSeparatorsPath
                val isi = runCatching { satu.readText() }.getOrDefault("")
                PemilihKonteks.Kandidat(jalur, isi, 0)
            }
            .toList()

    fun kumpulkan(pertanyaan: String, hemat: Boolean, berkasAktif: String? = null): Hasil {
        val anggaran = if (hemat) 6_000 else 12_000
        val semua = kandidat()
        val berbobot = semua.map { k ->
            k.copy(skor = PemilihKonteks.skor(pertanyaan, k.jalur, k.isi) + bobotKepentingan(k.jalur))
        }
        val wajibJalur = buildList {
            add("kodenusa.json")
            berkasAktif?.let { add(it) }
        }
        val wajib = berbobot.filter { it.jalur in wajibJalur }
            .map { it.copy(isi = potongAman(it.isi, if (hemat) 2_500 else 4_000)) }
        val terpilih = PemilihKonteks.pilih(
            berbobot.filter { it.jalur !in wajibJalur },
            anggaran,
            wajib,
        )
        val berkas = terpilih.map { it.jalur to potongAman(it.isi, if (hemat) 2_500 else 5_000) }
        val peta = PetaProjek.petakan(akar, if (hemat) 4_000 else PetaProjek.ANGGARAN_BAWAAN)
        val karakter = berkas.sumOf { it.second.length } + peta.length
        return Hasil(peta, berkas, berkas.size, karakter, semua.size)
    }

    /** Bobot statis per jalur supaya berkas inti selalu dipertimbangkan walau pertanyaan umum. */
    private fun bobotKepentingan(jalur: String): Int = when {
        jalur == "kodenusa.json" -> 100
        jalur.endsWith("AndroidManifest.xml") -> 60
        jalur.endsWith("MainActivity.java") -> 50
        else -> 0
    }

    private fun potongAman(teks: String, batas: Int): String =
        if (teks.length <= batas) teks else teks.take(batas) + "\n… [dipotong] …"

    private companion object {
        val EKSTENSI_TEKS = setOf("java", "xml", "html", "css", "js", "json", "properties")
    }
}
