# 00 — Visi Produk & Terobosan

## 1. Masalah nyata

| Fakta | Konsekuensi |
|---|---|
| Android Studio butuh PC 8–16 GB RAM | 90% calon developer Indonesia tidak punya mesin itu |
| AndroidIDE (acuan open-source terbaik saat ini) minta **1.5–2 GB RAM bebas + 4 GB storage** | Median HP di Indonesia 2–4 GB RAM / 32–64 GB storage terisi — gagal di gerbang setup |
| AIDE/Sketchware: usang, tidak dirawat, Java 7 era, Play Store menolak target API lama | Bukan jalan untuk 2026 |
| Cloud IDE (Replit, StackBlitz, GitHub Codespaces) | Butuh internet stabil + kartu kredit. Di 3T (Terdepan/Terluar/Tertinggal) mati total |
| Semua dokumentasi & pesan error berbahasa Inggris | Pemula berhenti di error pertama, bukan di logika |
| Tidak ada jalur "karya saya dipakai orang" | Tidak ada loop motivasi → retensi nol |

**Kesimpulan:** masalahnya bukan "belum ada IDE Android". Masalahnya **IDE yang ada tidak dirancang untuk HP kelas bawah Indonesia**, dan tidak dirancang untuk orang yang baru mulai.

## 2. Persona (3, bukan 10)

**P1 — Rina, 19, mahasiswa SI, HP Redmi 9A 2/32 GB, kuota 8 GB/bulan.**
Pernah install Android Studio → laptop kakaknya nge-hang. Ingin bikin aplikasi absensi untuk UKM kampusnya. Butuh: buka app → template → ubah teks → build → APK dibagikan ke teman via WhatsApp.

**P2 — Pak Dedi, 34, pemilik warung + toko kelontong, HP 3/64 GB.**
Tidak mau belajar "programming", tapi mau aplikasi kasir sederhana dengan nama tokonya sendiri. Butuh: template → isi nama, harga, logo → APK terpasang di HP kasir.

**P3 — Bayu, 24, freelancer, HP 8/256 GB.**
Butuh baca kode projek klien dari GitHub saat di jalan, quick-fix, push commit. Butuh: git, editor yang benar, bukan mainan.

Desain harus melayani P1 sebagai default, P3 sebagai mode lanjutan. **P2 adalah pengguna yang tidak akan pernah menyentuh Android Studio** — di situ volume pasarnya.

## 3. Terobosan — 4 pilar

### Pilar 1 · Tier 0: build APK tanpa Gradle, tanpa JVM
Rantai kompilasi minimal yang terbukti jalan di Android:

```
.java ──ecj──► .class ──d8──► classes.dex ─┐
res/  ──aapt2 compile──► .flat ──aapt2 link─┼──► .apk ──zipalign──► ──apksigner──► APK terpasang
AndroidManifest.xml ────────────────────────┘
```

Tool yang dibundel: `aapt2`, `d8` (+ `libd8.jar` di bawah ART), `zipalign`, `apksigner`, `ecj.jar`, `android.jar` (API 34/35 android-stub), `debug.keystore`.
Total ±55–70 MB terkompresi — **bukan 4 GB SDK**. Ini, bukan Gradle, yang membuat HP 2 GB sanggup.

Batas Tier 0 yang kita akui terbuka:
- Bahasa: **Java murni** (ECJ belum bisa Kotlin).
- Dependency: JAR lokal / Maven tunggal yang sudah di-resolve (bukan resolusi transitif penuh).
- Tanpa ProGuard/R8 minify, tanpa `kapt`, tanpa `viewBinding` generator (pakai `findViewById` atau Compose runtime jar).
- Batas praktis: ±20 file / ±5.000 LOC.

### Pilar 2 · Tier 1: cloud Gradle sebagai katup, bukan fondasi
Saat projek melewati batas Tier 0 (butuh Kotlin, library besar, AAB), user menekan satu tombol: build dikirim ke worker (Android SDK + JDK 17 + Gradle cache), log di-stream lewat SSE, APK/AAB kembali. Offline tetap bisa *mengedit*; build ditunda, bukan hilang.

### Pilar 3 · Indonesia-first, bukan terjemahan
- Pesan error ECJ/d8/aapt2 diterjemahkan ke bahasa manusia: `error: cannot find symbol class RecyclerView` → *"Kamu pakai RecyclerView tapi library-nya belum ditambahkan. Tambahkan `androidx.recyclerview`?" [Tambahkan otomatis]*.
- Template yang benar-benar dipakai orang: kasir warung, absensi QR, katalog produk + WhatsApp order, kas RT/RW, daftar hafalan, undangan.
- Jalur belajar menempel di editor: setiap konsep punya satu halaman 200 kata, bukan kursus 40 jam.

### Pilar 4 · Distribusi karya
Satu tombol: **Bagikan APK** → FileProvider → WhatsApp/Telegram/Drive. Plus QR code untuk instalasi. Play Store opsional dan dijelaskan jujur (biaya $25, AAB, target API).

## 4. Diferensiasi ringkas

| Produk | Jalan di HP 2 GB? | Offline build? | Bahasa ID? | APK asli? | Template lokal relevan? |
|---|---|---|---|---|---|
| Android Studio | ❌ (PC) | ✅ | ❌ | ✅ | ❌ |
| AndroidIDE | ❌ (butuh 1.5–2 GB bebas) | ✅ | ❌ | ✅ | ❌ |
| AIDE | ⚠️ (tapi usang) | ✅ | ❌ | ✅ | ❌ |
| Sketchware | ✅ | ✅ | ⚠️ | ⚠️ (terbatas) | ⚠️ |
| Replit/StackBlitz | ✅ | ❌ | ❌ | ❌ | ❌ |
| **KodeNusa** | ✅ | ✅ (Tier 0) | ✅ | ✅ | ✅ |

## 5. Metrik keberhasilan (product north star)

1. **Time-to-first-installed-APK** (median) < 10 menit sejak install aplikasi.
2. **Rasio build Tier 0 sukses** ≥ 85% untuk projek di bawah batas.
3. **Retensi D7** ≥ 25% (IDE belajar biasanya < 10%).
4. **Rasio build yang harus lari ke Tier 1** ≤ 15% (kalau lebih tinggi, Tier 0 terlalu sempit).

## 6. Non-goals (tegas)

- Bukan pengganti Android Studio untuk kerja profesional.
- Bukan IDE multi-bahasa (Python/JS/Flutter) di v1 — fokus menang dulu di Java/Android.
- Bukan editor berbasis AI yang menulis kode untukmu; AI hanya asisten opsional dan tidak boleh jadi syarat build.
- Bukan cloud storage; tidak menyimpan kode user di server tanpa aksi eksplisit.

## 7. Prinsip produk

1. **Offline adalah fitur, bukan fallback.** Tanpa internet, editor + build + install harus tetap penuh.
2. **Jangan pernah berbohong soal kapabilitas.** Kalau projek tidak bisa di-build di Tier 0, katakan sebelum user menekan Build, bukan setelah gagal.
3. **Error adalah UI.** Setiap error wajib punya: penyebab, lokasi, dan satu aksi perbaikan.
4. **Hormati HP murah.** Tidak ada animasi 300 ms, tidak ada background service, tidak ada auto-download diam-diam.
5. **Data user adalah milik user.** Projek = folder biasa. Bisa di-zip, di-git, di-export kapan saja, tanpa lock-in.
