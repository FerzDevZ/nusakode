package id.kodenusa.core.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/** Ringkasan perubahan yang belum disimpan. */
data class Perubahan(
    val ditambah: List<String> = emptyList(),
    val diubah: List<String> = emptyList(),
    val dihapus: List<String> = emptyList(),
) {
    val kosong: Boolean get() = ditambah.isEmpty() && diubah.isEmpty() && dihapus.isEmpty()
    val jumlah: Int get() = ditambah.size + diubah.size + dihapus.size
}

/** Satu baris riwayat commit untuk ditampilkan di layar HP. */
data class CommitRingkas(val sha: String, val pesan: String, val penulis: String, val waktu: Long) {
    fun sha7(): String = sha.take(7)
}

/** Hasil operasi git: berhasil dengan data, atau gagal dengan kalimat bahasa Indonesia. */
sealed interface Hasil<out T> {
    data class Sukses<T>(val data: T) : Hasil<T>
    data class Gagal(val pesan: String) : Hasil<Nothing>
}

/**
 * Git sungguhan di dalam projek, lewat JGit.
 *
 * Kenapa bukan hanya GitHub REST: commit harus tetap bisa dilakukan tanpa sinyal — itu inti janji
 * "offline dulu" KodeNusa. REST tetap dipakai terpisah untuk dua hal yang tidak bisa dilakukan git
 * lokal: membuat repo di GitHub dan mengunggah APK ke Releases.
 *
 * Semua fungsi mengembalikan pesan galat berbahasa Indonesia, bukan `exception` mentah, karena
 * pengguna memegang HP dan tidak punya terminal untuk membaca stack trace.
 */
@Suppress("TooManyFunctions")
object RepoGit {

    /** Isi `.gitignore` bawaan: hasil build tidak boleh masuk repo. */
    val GITIGNORE: String = """
        build/
        dist/
        .tools/
        .gradle/
        gen/
        dex/
        *.apk
        *.jks
        *.keystore
        local.properties
    """.trimIndent() + "\n"

    private const val BRANCH = "main"

    fun adalahRepo(akar: File): Boolean = File(akar, ".git").isDirectory

    /**
     * Menyiapkan repo di dalam projek: `.gitignore`, identitas penulis, dan nama cabang `main`.
     * Aman dipanggil berulang — repo yang sudah ada tidak dihapus.
     */
    fun siapkan(akar: File, nama: String, email: String): Hasil<Unit> = jalankan {
        if (!akar.isDirectory) return@jalankan Hasil.Gagal("Folder projek tidak ditemukan.")
        tulisGitignore(akar)
        val git = if (adalahRepo(akar)) Git.open(akar) else Git.init().setDirectory(akar).call()
        denganGit(git) {
            it.repository.config.apply {
                setString("user", null, "name", nama)
                setString("user", null, "email", email)
                save()
            }
        }
        Hasil.Sukses(Unit)
    }

    /** Perubahan yang belum di-commit. Repo yang belum ada dianggap "tidak ada perubahan". */
    fun keadaan(akar: File): Perubahan {
        val git = bukaRepo(akar) ?: return Perubahan()
        return runCatching {
            val status = git.status().call()
            Perubahan(
                ditambah = (status.added + status.untracked).sorted(),
                diubah = (status.modified + status.changed).distinct().sorted(),
                dihapus = (status.removed + status.missing).distinct().sorted(),
            )
        }.getOrElse { Perubahan() }
    }

    /** Menyimpan semua perubahan jadi satu commit. Mengembalikan sha bila berhasil. */
    fun simpanSemua(akar: File, pesan: String, nama: String, email: String): Hasil<CommitRingkas> {
        val git = bukaRepo(akar) ?: return Hasil.Gagal("Projek ini belum punya repo git.")
        if (keadaan(akar).kosong) return Hasil.Gagal("Tidak ada perubahan untuk disimpan.")
        if (pesan.isBlank()) return Hasil.Gagal("Pesan commit tidak boleh kosong.")
        return jalankan {
            denganGit(git) {
                it.add().addFilepattern(".").call()
                // Berkas yang dihapus harus ikut tercatat, kalau tidak commit akan "hidup kembali".
                it.add().addFilepattern(".").setUpdate(true).call()
                val commit = it.commit()
                    .setMessage(pesan.trim())
                    .setAuthor(nama, email)
                    .setCommitter(nama, email)
                    .call()
                // JGit membuat cabang `master`; GitHub memakai `main`. Ganti nama setelah commit
                // pertama ada, karena JGit menolak rename pada repo yang belum punya commit.
                if (it.repository.branch == "master") {
                    runCatching { it.branchRename().setNewName(BRANCH).call() }
                }
                Hasil.Sukses(ringkas(commit))
            }
        }
    }

    fun aturRemote(akar: File, url: String): Hasil<Unit> = jalankan {
        val git = bukaRepo(akar) ?: return@jalankan Hasil.Gagal("Projek ini belum punya repo git.")
        val bersih = url.trim()
        if (!bersih.startsWith("https://") && !bersih.startsWith("file://") && !bersih.startsWith("/")) {
            return@jalankan Hasil.Gagal("Alamat repo harus diawali https:// (mis. https://github.com/nama/repo.git).")
        }
        denganGit(git) {
            val config = it.repository.config
            config.setString("remote", "origin", "url", bersih)
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            config.save()
        }
        Hasil.Sukses(Unit)
    }

    fun urlRemote(akar: File): String? = bukaRepo(akar)?.let { git ->
        denganGit(git) { it.repository.config.getString("remote", "origin", "url") }
    }

    /**
     * Mengirim commit ke remote. [token] hanya dipakai sebagai kata sandi HTTPS; tidak pernah
     * ditulis ke `.git/config`, supaya berkas projek tidak membocorkan kredensial kalau dibagikan.
     */
    fun dorong(akar: File, token: String, cabang: String = BRANCH): Hasil<Unit> {
        val git = bukaRepo(akar) ?: return Hasil.Gagal("Projek ini belum punya repo git.")
        if (urlRemote(akar).isNullOrBlank()) return Hasil.Gagal("Remote belum diatur. Isi alamat repo GitHub dulu.")
        return jalankan {
            denganGit(git) {
                var perintah = it.push().setRemote("origin").add("refs/heads/$cabang:refs/heads/$cabang")
                if (token.isNotBlank()) perintah = perintah.setCredentialsProvider(sandi(token))
                val hasil = perintah.call()
                val ditolak = hasil.any { satu ->
                    satu.remoteUpdates.any { it.status != RemoteRefUpdate.Status.OK }
                }
                if (ditolak) Hasil.Gagal("GitHub menolak push. Periksa token dan izin repo.") else Hasil.Sukses(Unit)
            }
        }
    }

    fun daftarCommit(akar: File, batas: Int = 20): List<CommitRingkas> {
        val git = bukaRepo(akar) ?: return emptyList()
        return runCatching {
            denganGit(git) {
                it.log().setMaxCount(batas).call().map { commit ->
                    CommitRingkas(
                        sha = commit.name,
                        pesan = commit.shortMessage,
                        penulis = commit.authorIdent.name,
                        waktu = commit.commitTime.toLong() * 1000L,
                    )
                }.toList()
            }
        }.getOrElse { emptyList() }
    }

    /** Mengunduh repo ke [tujuan]. Dipakai untuk "Buka projek dari GitHub". */
    fun klon(url: String, token: String, tujuan: File): Hasil<Unit> = jalankan {
        if (tujuan.exists() && tujuan.listFiles().orEmpty().isNotEmpty()) {
            return@jalankan Hasil.Gagal("Folder tujuan sudah berisi berkas.")
        }
        val perintah = Git.cloneRepository().setURI(url.trim()).setDirectory(tujuan)
        if (token.isNotBlank()) perintah.setCredentialsProvider(sandi(token))
        perintah.call().close()
        // Klon bisa "berhasil" tapi kosong kalau HEAD di remote menunjuk cabang yang tidak ada.
        // Lebih baik gagal terang-terangan daripada pengguna melihat projek kosong tanpa penjelasan.
        val adaIsi = tujuan.listFiles().orEmpty().any { it.name != ".git" }
        if (!adaIsi) {
            Hasil.Gagal("Repo terunduh, tetapi berkasnya tidak bisa dibuka. Cabang bawaan repo mungkin bukan 'main'.")
        } else {
            Hasil.Sukses(Unit)
        }
    }

    private fun sandi(token: String) = UsernamePasswordCredentialsProvider("x-access-token", token)

    private fun tulisGitignore(akar: File) {
        val berkas = File(akar, ".gitignore")
        if (!berkas.exists()) berkas.writeText(GITIGNORE)
    }

    private fun bukaRepo(akar: File): Git? =
        if (adalahRepo(akar)) runCatching { Git.open(akar) }.getOrNull() else null

    /** JGit 3.x belum `Closeable`; tutup manual supaya berkas `.git` tidak terkunci. */
    private inline fun <T> denganGit(git: Git, blok: (Git) -> T): T = try {
        blok(git)
    } finally {
        git.close()
    }

    private fun ringkas(commit: org.eclipse.jgit.revwalk.RevCommit) = CommitRingkas(
        sha = commit.name,
        pesan = commit.shortMessage,
        penulis = commit.authorIdent.name,
        waktu = commit.commitTime.toLong() * 1000L,
    )

    /** Membungkus semua galat JGit menjadi kalimat yang bisa dibaca pengguna. */
    private inline fun <T> jalankan(blok: () -> Hasil<T>): Hasil<T> = try {
        blok()
    } catch (galat: GitAPIException) {
        Hasil.Gagal("Git menolak perintah ini: ${galat.message ?: "penyebab tidak diketahui"}")
    } catch (galat: java.io.IOException) {
        Hasil.Gagal("Penyimpanan tidak bisa diakses: ${galat.message ?: "penyebab tidak diketahui"}")
    } catch (galat: IllegalStateException) {
        Hasil.Gagal("Repo dalam keadaan tidak siap: ${galat.message ?: "penyebab tidak diketahui"}")
    }
}
