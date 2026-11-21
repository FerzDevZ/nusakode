package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Uji "template saya" (docs/14 4.3): template buatan AI wajib utuh sebelum boleh disimpan,
 * jalur berbahaya ditolak, dan pembuatan projek mengganti penanda seperti template aset.
 */
class TemplateSayaTest {

    @TempDir
    lateinit var akar: File

    private val kodenusaJson =
        """{"format":1,"name":"__APP_NAME__","applicationId":"__APP_ID__","versionName":"1.0.0",
           "versionCode":1,"minSdk":24,"targetSdk":34,"javaVersion":8,"buildEngine":"tier0"}"""

    private val manifest =
        """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app">
           <application android:label="@string/app_name">
           <activity android:name=".MainActivity" android:exported="true"/>
           </application></manifest>"""

    private val activity =
        """package app;
import __APP_ID__.R;
import android.app.Activity;
import android.os.Bundle;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle s) { super.onCreate(s); setContentView(R.layout.activity_main); }
}"""

    private val layout =
        """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"/>"""

    private val strings = """<resources><string name="app_name">__APP_NAME__</string></resources>"""

    private fun berkasLengkap(): Map<String, String> = mapOf(
        "kodenusa.json" to kodenusaJson,
        "src/main/AndroidManifest.xml" to manifest,
        "src/main/java/app/MainActivity.java" to activity,
        "src/main/res/layout/activity_main.xml" to layout,
        "src/main/res/values/strings.xml" to strings,
    )

    @Test
    fun simpan_dan_list_menghasilkan_template_yang_bisa_dibaca() {
        val saya = TemplateSaya(akar)
        assertNull(saya.simpan("Pencatat Utang", berkasLengkap()))
        val daftar = saya.list()
        assertEquals(1, daftar.size)
        assertEquals("saya:pencatat-utang", daftar[0].id)
        assertEquals("__APP_NAME__", daftar[0].title)
        assertEquals("aplikasi", daftar[0].jenis)
        assertTrue(daftar[0].files.contains("src/main/java/app/MainActivity.java"))
    }

    @Test
    fun simpan_tanpa_kodenusa_json_ditolak() {
        val saya = TemplateSaya(akar)
        val tanpaManifest = berkasLengkap() - "kodenusa.json"
        val galat = saya.simpan("uji", tanpaManifest)
        assertNotNull(galat)
        assertTrue(galat!!.contains("kodenusa.json"))
        assertTrue(saya.list().isEmpty())
    }

    @Test
    fun simpan_tanpa_manifest_atau_tanpa_activity_ditolak() {
        val saya = TemplateSaya(akar)
        assertNotNull(saya.simpan("uji-manifest", berkasLengkap() - "src/main/AndroidManifest.xml"))
        assertNotNull(saya.simpan("uji-activity", berkasLengkap() - "src/main/java/app/MainActivity.java"))
        assertTrue(saya.list().isEmpty(), "tidak boleh ada template setengah jadi yang lolos")
    }

    @Test
    fun simpan_dengan_kodenusa_json_rusak_ditolak() {
        val saya = TemplateSaya(akar)
        val rusak = berkasLengkap().toMutableMap()
        rusak["kodenusa.json"] = "{ ini bukan json"
        val galat = saya.simpan("uji-rusak", rusak)
        assertNotNull(galat)
        assertTrue(saya.list().isEmpty())
    }

    @Test
    fun simpan_dengan_jalur_berbahaya_ditolak() {
        val saya = TemplateSaya(akar)
        val berbahaya = berkasLengkap().toMutableMap()
        berbahaya["../../etc/penampungan"] = "x"
        assertNotNull(saya.simpan("uji-naik", berbahaya))
        val absolut = berkasLengkap().toMutableMap()
        absolut["/tmp/jahat"] = "x"
        assertNotNull(saya.simpan("uji-absolut", absolut))
        assertTrue(saya.list().isEmpty())
    }

    @Test
    fun simpan_dua_kali_dengan_slug_sama_ditolak() {
        val saya = TemplateSaya(akar)
        assertNull(saya.simpan("Kasir Warung", berkasLengkap()))
        assertNotNull(saya.simpan("kasir warung", berkasLengkap()))
    }

    @Test
    fun create_mengganti_penanda_dan_mengisi_folder_projek() {
        val saya = TemplateSaya(akar)
        assertNull(saya.simpan("utang", berkasLengkap()))
        val target = File(akar, "projek-baru")
        val hasil = saya.create(
            templateId = "saya:utang",
            target = target,
            appName = "Utang Teman",
            appId = "id.kodenusa.utang",
        )
        assertTrue(hasil.isSuccess)
        val activityTertulis = File(target, "src/main/java/app/MainActivity.java").readText()
        assertTrue(activityTertulis.contains("import id.kodenusa.utang.R;"))
        assertFalse(activityTertulis.contains("__APP_ID__"))
        assertEquals("Utang Teman", File(target, "src/main/res/values/strings.xml").readText().let {
            Regex("app_name\">([^<]+)<").find(it)?.groupValues?.get(1)
        })
    }

    @Test
    fun create_untuk_id_yang_tidak_ada_gagal_jelas() {
        val saya = TemplateSaya(akar)
        val hasil = saya.create("saya:tidak-ada", File(akar, "x"), "Nama", "id.x")
        assertTrue(hasil.isFailure)
        assertTrue(hasil.exceptionOrNull()!!.message!!.contains("tidak ada"))
    }

    @Test
    fun hapus_menghilangkan_dari_daftar() {
        val saya = TemplateSaya(akar)
        assertNull(saya.simpan("hapus-aku", berkasLengkap()))
        assertEquals(1, saya.list().size)
        assertTrue(saya.hapus("saya:hapus-aku"))
        assertTrue(saya.list().isEmpty())
    }
}
