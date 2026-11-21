package id.kodenusa.build.tier0

import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugKeystoreTest {

    private val folder: File = Files.createTempDirectory("kodenusa-ks").toFile()

    @Test
    fun `keystore hasil generate bisa dibuka ulang dengan kata sandi android`() {
        val target = File(folder, "debug.keystore")
        DebugKeystore.generate(target)

        val store = KeyStore.getInstance("PKCS12").apply {
            target.inputStream().use { load(it, DebugKeystore.PASSWORD.toCharArray()) }
        }
        assertTrue(store.containsAlias(DebugKeystore.ALIAS), "alias ${DebugKeystore.ALIAS} tidak ada")
        assertTrue(store.isKeyEntry(DebugKeystore.ALIAS), "entri bukan private key")
        assertTrue(target.length() > 1_000, "keystore terlalu kecil: ${target.length()} byte")
    }

    @Test
    fun `sertifikat yang dihasilkan sah dan ditandatangani kuncinya sendiri`() {
        val target = File(folder, "debug.keystore")
        DebugKeystore.generate(target)

        val store = KeyStore.getInstance("PKCS12").apply {
            target.inputStream().use { load(it, DebugKeystore.PASSWORD.toCharArray()) }
        }
        val cert = store.getCertificate(DebugKeystore.ALIAS)
        val public = store.getKey(DebugKeystore.ALIAS, DebugKeystore.PASSWORD.toCharArray())
            .let { (it as java.security.PrivateKey) }
        assertTrue(cert is java.security.cert.X509Certificate, "sertifikat bukan X.509")
        cert as java.security.cert.X509Certificate
        cert.checkValidity()
        cert.verify(cert.publicKey)
        assertEquals("X.509", cert.type)
        assertTrue(public.algorithm == "RSA", "kunci bukan RSA: ${public.algorithm}")
        assertTrue(cert.subjectDN.name.contains("CN=Android Debug"), "subject: ${cert.subjectDN}")
    }

    @Test
    fun `ensure memakai berkas yang sudah ada tanpa menimpa`() {
        val target = File(folder, "tetap.keystore")
        DebugKeystore.generate(target)
        val sidik = target.readBytes()

        val dipakai = DebugKeystore.ensure(target)

        assertEquals(target, dipakai)
        assertTrue(sidik.contentEquals(target.readBytes()), "ensure menimpa keystore yang sudah ada")
    }

    @Test
    fun `dua generate menghasilkan kunci berbeda`() {
        val a = DebugKeystore.generate(File(folder, "a.keystore")).readBytes()
        val b = DebugKeystore.generate(File(folder, "b.keystore")).readBytes()
        assertTrue(!a.contentEquals(b), "dua keystore identik; kunci tidak acak")
    }

    /** docs/14 2.1 (ADR-041): kunci pengguna pakai alias + sandi sendiri, validitas 10.000 hari. */
    @Test
    fun `kunci pengguna alias dan sandi sendiri bisa dibuka ulang`() {
        val target = File(folder, "pengguna.p12")
        DebugKeystore.generate(
            target = target,
            commonName = "KodeNusa warung",
            alias = "warung",
            password = "rahasia123",
            masaBerlakuHari = 10_000,
        )

        val store = KeyStore.getInstance("PKCS12").apply {
            target.inputStream().use { load(it, "rahasia123".toCharArray()) }
        }
        assertTrue(store.containsAlias("warung"), "alias warung tidak ada")
        val cert = store.getCertificate("warung") as java.security.cert.X509Certificate
        cert.checkValidity()
        // 10.000 hari ~ 27 tahun: sertifikat harus jauh melampaui 10 tahun keystore debug.
        val sisaHari = (cert.notAfter.time - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)
        assertTrue(sisaHari > 9_000, "validitas kunci pengguna hanya $sisaHari hari")
    }

    @Test
    fun `kunci pengguna menolak sandi salah`() {
        val target = File(folder, "salah.p12")
        DebugKeystore.generate(target, alias = "x", password = "benar123")
        val store = KeyStore.getInstance("PKCS12")
        val gagal = runCatching {
            target.inputStream().use { store.load(it, "salah999".toCharArray()) }
        }
        assertTrue(gagal.isFailure, "keystore terbuka dengan sandi salah")
    }
}
