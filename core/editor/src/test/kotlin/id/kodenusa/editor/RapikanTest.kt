package id.kodenusa.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Uji "Rapikan" (docs/13 C2). Kontrak penting: hanya spasi awal baris yang berubah — isi baris
 * tidak pernah disentuh, jadi kode tetap sah meski heuristik kedalaman meleset.
 */
class RapikanTest {

    @Test
    fun java_dengan_indentasi_kacau_jadi_rapi_tanpa_mengubah_isi_baris() {
        val kacau = "class A {\nvoid b() {\nint x = 1;\n}\n}"
        val rapi = Rapikan.rapikan(kacau, Language.JAVA)
        assertEquals(
            "class A {\n    void b() {\n        int x = 1;\n    }\n}",
            rapi,
        )
    }

    @Test
    fun isi_baris_tidak_pernah_disentuh() {
        val isi = "class A {\n  int   x=1;   // komentar   berlebih\n}"
        val rapi = Rapikan.rapikan(isi, Language.JAVA)
        // Isi baris (setelah spasi awal) harus identik.
        val isiBaris = isi.lines().map { it.trimStart() }
        val isiBarisHasil = rapi.lines().map { it.trimStart() }
        assertEquals(isiBaris, isiBarisHasil)
    }

    @Test
    fun xml_nested_dirapikan_tag_sendiri_tidak_menambah_kedalaman() {
        val kacau = "<layout>\n<FrameLayout>\n<TextView\nandroid:text=\"hai\" />\n</FrameLayout>\n</layout>"
        val rapi = Rapikan.rapikan(kacau, Language.XML)
        assertEquals(
            "<layout>\n    <FrameLayout>\n        <TextView\n        android:text=\"hai\" />\n    </FrameLayout>\n</layout>",
            rapi,
        )
    }

    @Test
    fun python_dan_plain_tidak_disentuh() {
        val isi = "def a():\nprint(1)"
        assertEquals(isi, Rapikan.rapikan(isi, Language.PYTHON))
        assertEquals(isi, Rapikan.rapikan(isi, Language.PLAIN))
    }

    @Test
    fun tab_awal_baris_diganti_spasi_konsisten() {
        val isi = "class A {\n\tvoid b() {\n\t}\n}"
        val rapi = Rapikan.rapikan(isi, Language.JAVA)
        assertEquals(
            "class A {\n    void b() {\n    }\n}",
            rapi,
        )
    }

    @Test
    fun berkas_kosong_dan_sudah_rapi_tidak_berubah() {
        assertEquals("", Rapikan.rapikan("", Language.JAVA))
        val rapi = "class A {\n    void b() {\n    }\n}"
        assertEquals(rapi, Rapikan.rapikan(rapi, Language.JAVA))
    }
}
