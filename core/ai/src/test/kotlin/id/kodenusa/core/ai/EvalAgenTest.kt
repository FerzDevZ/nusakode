package id.kodenusa.core.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Evaluasi agen "buat fitur" terhadap 10 tugas nyata (rencana A5, lihat `docs/07-eval-agen.md`).
 *
 * Uji ini **butuh model sungguhan** milik pengguna, jadi dilewati bila variabel lingkungan tidak
 * diisi:
 *
 * ```
 * AI_ENDPOINT=... AI_KEY=... AI_MODEL=... AI_EVAL=1 ./gradlew :core:ai:test --tests '*EvalAgenTest*'
 * ```
 *
 * Catatan jujur: gerbang build di sini hanya memeriksa keberadaan berkas dan kata kunci penting.
 * Kompilasi sungguhan tetap dilakukan di perangkat lewat tombol "Bangun APK"; angka di sini mengukur
 * apakah agen menulis perubahan yang **tepat sasaran**, bukan apakah APK berhasil dibangun.
 */
class EvalAgenTest {

    private val endpoint = System.getenv("AI_ENDPOINT")
    private val kunci = System.getenv("AI_KEY")
    private val model = System.getenv("AI_MODEL")
    private val aktif = System.getenv("AI_EVAL") == "1"

    private data class Tugas(
        val nama: String,
        val perintah: String,
        val berkasWajib: List<String>,
        val kataKunci: List<String>,
    )

    private val tugas = listOf(
        Tugas(
            "judul-layout",
            "Ubah teks judul di activity_main.xml menjadi 'KodeNusa Warung'.",
            listOf("src/main/res/layout/activity_main.xml"),
            listOf("KodeNusa Warung"),
        ),
        Tugas(
            "tombol-simpan",
            "Tambahkan satu tombol dengan id tombolSimpan dan teks 'Simpan' di activity_main.xml.",
            listOf("src/main/res/layout/activity_main.xml"),
            listOf("tombolSimpan", "Simpan"),
        ),
        Tugas(
            "method-hitung",
            "Tambahkan method hitungTotal(int harga, int jumlah) pada MainActivity yang mengembalikan hasil perkalian.",
            listOf("src/main/java/app/MainActivity.java"),
            listOf("hitungTotal", "return"),
        ),
        Tugas(
            "toast-klik",
            "Saat tombolSimpan diklik, tampilkan Toast berisi 'Data disimpan'.",
            listOf("src/main/java/app/MainActivity.java"),
            listOf("Toast.makeText", "Data disimpan"),
        ),
        Tugas(
            "warna-aksen",
            "Tambahkan warna bernama aksen dengan nilai #F2A03D di res/values/colors.xml.",
            listOf("src/main/res/values/colors.xml"),
            listOf("aksen", "F2A03D"),
        ),
        Tugas(
            "string-app",
            "Tambahkan string app_name bernilai 'Kasir Warung' di strings.xml.",
            listOf("src/main/res/values/strings.xml"),
            listOf("app_name", "Kasir Warung"),
        ),
        Tugas(
            "kelas-produk",
            "Buat kelas Produk dengan field nama (String) dan harga (int) beserta konstruktornya.",
            listOf("src/main/java/app/Produk.java"),
            listOf("class Produk", "harga"),
        ),
        Tugas(
            "layout-linear",
            "Ubah activity_main.xml memakai LinearLayout vertikal sebagai akar.",
            listOf("src/main/res/layout/activity_main.xml"),
            listOf("LinearLayout", "vertical"),
        ),
        Tugas(
            "simpan-preferensi",
            "Simpan total belanja ke SharedPreferences dengan nama 'warung' saat tombolSimpan diklik.",
            listOf("src/main/java/app/MainActivity.java"),
            listOf("getSharedPreferences", "warung"),
        ),
        Tugas(
            "perbaiki-galat",
            "Kode gagal dikompilasi: 'cannot find symbol variable total'. Perbaiki MainActivity agar total dideklarasikan.",
            listOf("src/main/java/app/MainActivity.java"),
            listOf("total"),
        ),
    )

    @Test
    fun sepuluh_tugas_dasar_selesai_tanpa_bantuan() = runBlocking {
        assumeTrue(
            aktif && !endpoint.isNullOrBlank() && !kunci.isNullOrBlank(),
            "AI_EVAL/AI_ENDPOINT/AI_KEY tidak diisi — evaluasi dilewati",
        )
        val dasar = AiConfig(endpoint.orEmpty(), kunci.orEmpty())
        val konfigurasi = if (!model.isNullOrBlank()) {
            dasar.denganModel(model.orEmpty())
        } else {
            dasar.denganModel(OpenAiCompatClient().daftarModel(dasar).first().id)
        }

        val hasil = tugas.map { satu ->
            val akar = Files.createTempDirectory("eval-${satu.nama}").toFile()
            siapkanProjek(akar)
            val mulai = System.currentTimeMillis()
            val keluaran = AgenBuild(
                model = object : GerbangModel {
                    override suspend fun balas(pesan: List<ChatMessage>): String =
                        OpenAiCompatClient().bicara(konfigurasi, pesan)
                },
                papan = PapanSederhana(akar),
                build = GerbangCek(akar, satu),
            ).jalankan(satu.perintah)
            val detik = (System.currentTimeMillis() - mulai) / 1000.0
            val nilainya = if (keluaran.sukses) "LULUS" else "gagal: ${keluaran.galat.take(120)}"
            println("EVAL|${satu.nama}|${"%.1f".format(detik)}s|${keluaran.putaran} putaran|$nilainya")
            keluaran.sukses
        }

        val lulus = hasil.count { it }
        println("EVAL|RINGKAS|$lulus/${tugas.size} tugas selesai tanpa disentuh")
        assertTrue(lulus >= 7, "target minimal 7 dari 10 tugas, hasil: $lulus/${tugas.size}")
    }

    /** Kerangka projek Tier 0 minimal: manifest, satu activity, satu layout, satu strings.xml. */
    private fun siapkanProjek(akar: File) {
        File(akar, "src/main/java/app").mkdirs()
        File(akar, "src/main/res/layout").mkdirs()
        File(akar, "src/main/res/values").mkdirs()
        File(akar, "kodenusa.json").writeText(
            """{"nama":"Eval","paket":"app","jenis":"aplikasi","engine":"tier0"}""",
        )
        File(akar, "src/main/AndroidManifest.xml").writeText(
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app">""" +
                """<application android:label="@string/app_name">""" +
                """<activity android:name=".MainActivity" android:exported="true"/></application></manifest>""",
        )
        File(akar, "src/main/java/app/MainActivity.java").writeText(
            """package app;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle simpanan) {
        super.onCreate(simpanan);
        setContentView(R.layout.activity_main);
    }
}
""",
        )
        File(akar, "src/main/res/layout/activity_main.xml").writeText(
            """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:orientation="vertical"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <TextView android:id="@+id/judul" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:text="Halo" />
</LinearLayout>
""",
        )
        File(akar, "src/main/res/values/strings.xml").writeText(
            """<resources><string name="app_name">Eval</string></resources>""",
        )
    }

    private class PapanSederhana(private val akar: File) : PapanTulisProjek {
        override fun konteks(): String = buildString {
            akar.walkTopDown().filter { it.isFile }.sortedBy { it.path }.take(12).forEach { berkas ->
                appendLine("=== ${berkas.relativeTo(akar).invariantSeparatorsPath} ===")
                appendLine(berkas.readText().take(700))
            }
        }.take(2400)

        override fun tulis(edit: BerkasEdit): String? = runCatching {
            if (edit.jalur.startsWith("/") || edit.jalur.contains("..")) return "jalur tidak boleh absolut"
            val tujuan = File(akar, edit.jalur)
            tujuan.parentFile?.mkdirs()
            tujuan.writeText(edit.isi)
            null
        }.getOrElse { "gagal menulis: ${it.message}" }
    }

    /** Gerbang build versi evaluasi: berkas wajib ada dan kata kunci penting muncul. */
    private class GerbangCek(private val akar: File, private val tugas: Tugas) : GerbangBuild {
        override suspend fun build(): HasilBuild {
            for (jalur in tugas.berkasWajib) {
                val berkas = File(akar, jalur)
                if (!berkas.isFile) return HasilBuild(false, "berkas wajib belum ada: $jalur")
                val isi = berkas.readText()
                for (kata in tugas.kataKunci) {
                    if (!isi.contains(kata)) return HasilBuild(false, "kata kunci '$kata' tidak ada di $jalur")
                }
            }
            return HasilBuild(true)
        }
    }
}
