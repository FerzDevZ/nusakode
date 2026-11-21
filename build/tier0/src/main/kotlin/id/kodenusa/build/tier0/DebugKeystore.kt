package id.kodenusa.build.tier0

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Membuat keystore debug **di perangkat**, bukan mengambil kunci yang sama untuk semua pemasangan.
 *
 * Alasan: kunci debug yang dibundel berarti setiap pengguna KodeNusa menandatangani APK dengan kunci
 * yang sama. Kalau kunci itu bocor, siapa pun bisa menerbitkan pembaruan palsu untuk aplikasi hasil
 * KodeNusa. Keystore per perangkat menutup celah itu tanpa menambah satu byte pun ke APK.
 *
 * Android tidak punya `sun.security.x509`, jadi sertifikat X.509 dibuat langsung sebagai DER:
 * `Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, BIT STRING signature }`.
 * Hasilnya diverifikasi ulang lewat [CertificateFactory] sebelum ditulis, sehingga keystore rusak
 * tidak pernah sampai ke tahap tanda tangan.
 */
object DebugKeystore {

    const val ALIAS = "androiddebugkey"
    const val PASSWORD = "android"

    /** Membuat (atau menimpa) keystore PKCS12 di [target]. */
    fun generate(
        target: File,
        commonName: String = "Android Debug",
        alias: String = ALIAS,
        password: String = PASSWORD,
        masaBerlakuHari: Long = 3650,
    ): File {
        target.parentFile?.mkdirs()

        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
        val cert = selfSigned(pair, commonName, masaBerlakuHari)
        verify(cert, pair)

        val store = KeyStore.getInstance("PKCS12")
        store.load(null, null)
        store.setKeyEntry(alias, pair.private, password.toCharArray(), arrayOf(cert))
        FileOutputStream(target).use { store.store(it, password.toCharArray()) }
        return target
    }

    /** Mengembalikan keystore yang sudah ada, atau membuat baru bila belum ada. */
    fun ensure(target: File): File = if (target.isFile && target.length() > 0) target else generate(target)

    private fun selfSigned(pair: KeyPair, commonName: String, masaBerlakuHari: Long): X509Certificate {
        val der = certificateDer(pair, commonName, masaBerlakuHari)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
    }

    /** DER sertifikat X.509 v3 yang menandatangani dirinya sendiri. Terpisah agar bisa diuji langsung. */
    internal fun certificateDer(pair: KeyPair, commonName: String, masaBerlakuHari: Long = 3650): ByteArray {
        val algorithm = Der.sequence(Der.oid(OID_SHA256_RSA), Der.nullValue())
        val nama = Der.sequence(
            Der.set(Der.sequence(Der.oid(OID_COUNTRY), Der.printable("ID"))),
            Der.set(Der.sequence(Der.oid(OID_ORG), Der.printable("Android"))),
            Der.set(Der.sequence(Der.oid(OID_CN), Der.printable(commonName))),
        )
        val sejak = Date(System.currentTimeMillis() - SATU_HARI_MS)
        val sampai = Date(System.currentTimeMillis() + masaBerlakuHari * SATU_HARI_MS)
        val tbs = Der.sequence(
            Der.context(0xA0, Der.integer(BigInteger.valueOf(2))),
            Der.integer(BigInteger(64, SecureRandom()).abs()),
            algorithm,
            nama,
            Der.sequence(Der.waktu(sejak), Der.waktu(sampai)),
            nama,
            // `encoded` kunci publik RSA sudah berupa SubjectPublicKeyInfo lengkap (SEQUENCE),
            // jadi dipakai apa adanya — membungkusnya lagi dengan BIT STRING merusak DER.
            pair.public.encoded,
            Der.context(0xA3, Der.sequence(Der.basicConstraints())),
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private)
            update(tbs)
            sign()
        }
        return Der.sequence(tbs, algorithm, Der.bitString(signature))
    }

    private fun verify(cert: X509Certificate, pair: KeyPair) {
        cert.checkValidity()
        cert.verify(pair.public)
    }

    private const val OID_SHA256_RSA = "1.2.840.113549.1.1.11"
    private const val OID_CN = "2.5.4.3"
    private const val OID_ORG = "2.5.4.10"
    private const val OID_COUNTRY = "2.5.4.6"
    private const val SATU_HARI_MS = 24 * 60 * 60 * 1000L
}

/** Penyandi DER minimal untuk kebutuhan sertifikat di atas. */
@Suppress("TooManyFunctions") // satu fasad tipis per tipe tag ASN.1; memecahnya hanya menambah lapisan
internal object Der {

    fun sequence(vararg bagian: ByteArray): ByteArray = tag(0x30, gabung(bagian))
    fun set(vararg bagian: ByteArray): ByteArray = tag(0x31, gabung(bagian))
    fun integer(nilai: BigInteger): ByteArray = tag(0x02, nilai.toByteArray())
    fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)
    fun boolean(nilai: Boolean): ByteArray = tag(0x01, byteArrayOf(if (nilai) -1 else 0))
    fun utf8(teks: String): ByteArray = tag(0x0C, teks.toByteArray(Charsets.UTF_8))
    fun printable(teks: String): ByteArray = tag(0x13, teks.toByteArray(Charsets.US_ASCII))
    fun octetString(isi: ByteArray): ByteArray = tag(0x04, isi)

    /** BIT STRING selalu diawali byte jumlah bit tak terpakai; untuk tanda tangan nilainya 0. */
    fun bitString(isi: ByteArray): ByteArray = tag(0x03, byteArrayOf(0) + isi)

    fun context(tagNilai: Int, isi: ByteArray): ByteArray = tag(tagNilai, isi)

    fun basicConstraints(): ByteArray = sequence(
        oid("2.5.29.19"),
        boolean(true),
        octetString(sequence()),
    )

    fun oid(dotted: String): ByteArray {
        val bagian = dotted.split('.').map { it.toInt() }
        val isi = ByteArrayOutputStream()
        isi.write(40 * bagian[0] + bagian[1])
        for (nilai in bagian.drop(2)) {
            var v = nilai
            val buffer = ArrayList<Int>()
            do {
                buffer.add(v and 0x7F)
                v = v shr 7
            } while (v > 0)
            for (i in buffer.indices.reversed()) {
                isi.write(if (i == 0) buffer[i] else buffer[i] or 0x80)
            }
        }
        return tag(0x06, isi.toByteArray())
    }

    /** UTCTime untuk tahun 1950–2049, GeneralizedTime di luar itu. */
    fun waktu(saat: Date): ByteArray {
        val teks = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(saat)
        val tahun = teks.substring(0, 4).toInt()
        return if (tahun in 1950..2049) {
            tag(0x17, (teks.substring(2) + "Z").toByteArray(Charsets.US_ASCII))
        } else {
            tag(0x18, (teks + "Z").toByteArray(Charsets.US_ASCII))
        }
    }

    private fun tag(nomor: Int, isi: ByteArray): ByteArray {
        val kepala = ByteArrayOutputStream()
        kepala.write(nomor)
        val panjang = isi.size
        when {
            panjang < 0x80 -> kepala.write(panjang)
            panjang < 0x100 -> {
                kepala.write(0x81)
                kepala.write(panjang)
            }
            else -> {
                kepala.write(0x82)
                kepala.write(panjang shr 8)
                kepala.write(panjang and 0xFF)
            }
        }
        return kepala.toByteArray() + isi
    }

    private fun gabung(bagian: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        bagian.forEach { out.write(it) }
        return out.toByteArray()
    }
}
