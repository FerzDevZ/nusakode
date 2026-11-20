package id.kodenusa.app

import java.io.File

/**
 * Projek kosong tanpa template: manifest `kodenusa.json`, satu manifest Android, satu activity,
 * satu layout, satu berkas string. Ditulis di sini — bukan disalin dari aset — supaya bisa diuji
 * di JVM tanpa `Context` dan tetap bisa dibangun oleh pipeline Tier 0 tanpa perubahan apa pun.
 */
object BlankProject {

    const val ID = "kosong"

    fun create(target: File, appName: String, appId: String): File {
        require(!target.exists()) { "Folder ${target.name} sudah dipakai" }
        val paket = appId
        val berkas = mapOf(
            "kodenusa.json" to manifestProjek(appName, appId),
            "src/main/AndroidManifest.xml" to manifestAndroid(paket),
            "src/main/java/${paket.replace('.', '/')}/MainActivity.java" to activityKosong(paket),
            "src/main/res/layout/activity_main.xml" to LAYOUT,
            "src/main/res/values/strings.xml" to stringsKosong(appName),
        )
        for ((relative, isi) in berkas) {
            val out = File(target, relative)
            out.parentFile?.mkdirs()
            out.writeText(isi)
        }
        return target
    }

    private fun manifestProjek(appName: String, appId: String): String = """
        {
          "format": 1,
          "name": ${kutip(appName)},
          "applicationId": ${kutip(appId)},
          "versionName": "1.0.0",
          "versionCode": 1,
          "minSdk": 24,
          "targetSdk": 34,
          "javaVersion": 8,
          "entry": "MainActivity",
          "buildEngine": "tier0",
          "templateId": "$ID",
          "dependencies": { "files": [] }
        }
    """.trimIndent() + "\n"

    private fun manifestAndroid(paket: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:label="@string/app_name"
                android:theme="@android:style/Theme.Material.Light.NoActionBar">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent() + "\n"

    private fun activityKosong(paket: String): String = """
        package $paket;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);
            }
        }
    """.trimIndent() + "\n"

    private fun stringsKosong(appName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name">${escapeXml(appName)}</string>
        </resources>
    """.trimIndent() + "\n"

    private fun kutip(nilai: String): String = "\"" + nilai.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun escapeXml(nilai: String): String = nilai
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    val LAYOUT: String = """
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="24dp">

            <TextView
                android:id="@+id/teks_utama"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/app_name"
                android:textSize="22sp" />
        </LinearLayout>
    """.trimIndent() + "\n"
}
