# 05 · Toolchain Tier 0 di Dalam Perangkat

Dokumen ini menjawab satu pertanyaan: **benda apa saja yang ikut di dalam APK supaya HP bisa mengompilasi sendiri, dan mengapa versi itu yang dipilih?**

**Status dokumen:** terakhir diselaraskan dengan rilis **v0.22.0** (APK rilis 32.897.316 bita).

---

## 1. Komponen yang dibundel

| Komponen | Versi | Status | Bukti |
|---|---|---|---|
| ECJ (kompiler Java) | **3.25.0** | ✅ jalan di API 24 & 35 | 9 template APK ter-build di perangkat |
| dx (dexer) | **1.16** (build-tools 28.0.3), paket direlokasi | ✅ | `id.kodenusa.dxtool.command.Main` |
| apksigner | **0.8** (build-tools 28) | ✅ | APK terpasang di Android 15 |
| aapt2 | **2.19** bionic, 3 ABI | ✅ | layout + `R.java` benar |
| `android.jar` | **API 34** | ✅ | classpath kompilasi, 26.361.808 bita |
| ZipAligner | Kotlin murni | ✅ | tanpa biner `zipalign` |
| Keystore | dibuat di perangkat | ✅ | `filesDir/keys/debug.keystore`, ADR-019; kunci pengguna opsional, ADR-041 |

APK rilis membawa komponen di atas: **32.897.316 bita (± 35 MB)**. Analisis mengapa tidak bisa lebih kecil ada di ADR-028.

## 2. Lokasi dan cara dijalankan

```
toolsDir    = filesDir/toolchain          ← jar dari assets (read-only)
runnableDir = filesDir/toolchain/bin      ← hasil dex untuk JavaLauncher
aapt2       = nativeLibraryDir/libaapt2.so ← wajib, lihat ADR-021
```

- **Tidak ada JVM di Android.** Karena itu ECJ/dx/apksigner dijalankan lewat `/system/bin/app_process` (ART) dengan `CLASSPATH` ke dex yang sudah diekstrak. `JavaLauncher` menyusun argumen sesuai tingkat SDK (format `app_process` berbeda antara API < 26 dan ≥ 26).
- **W^X (Android 10+).** Anda tidak boleh `exec` biner dari direktori yang bisa ditulis aplikasi. `libaapt2.so` dipasang sebagai native library (direktori itu `exec`-boleh), jar hanya dibaca (`setReadOnly`).
- **Semua pemanggilan** memakai `ProcessBuilder(List<String>)`; tidak ada `sh -c`, tidak ada interpolasi string perintah (ADR-006). Setiap argumen divalidasi `canonicalPath` agar tidak keluar dari folder projek.

## 3. Aturan yang wajib dijaga saat mengubah toolchain

1. **SHA-256 diperiksa sebelum eksekusi.** `toolchain.json` memuat hash tiap berkas; `ToolchainInstaller` memverifikasi dan melaporkan `Status.Missing`/`Broken`, bukan diam-diam menjalankan berkas rusak.
2. **Versi dipin, tidak pernah "terbaru".** ECJ 3.25 dipilih karena masih bisa menjalankan `-bootclasspath` dengan `android.jar` (ADR-011, ADR-016); dx dipilih karena d8 versi baru membutuhkan runtime yang tidak ada di API 24 (ADR-017).
3. **`android.jar` tidak boleh dipangkas.** Ia adalah classpath kompilasi; memangkasnya membuat kode pengguna yang sah gagal dikompilasi (ADR-028).
4. **Setiap penambahan library Java harus dibuktikan di API 24**, bukan hanya di JVM host. JGit 3.7.1 dipilih persis karena alasan ini (ADR-024).
5. **Mengubah modul library → `--rerun-tasks`.** Tanpa itu, hasil build perangkat bisa memakai dex lama.
6. **Aset biner tidak masuk git** (`.gitignore`): `jniLibs/**/*.so`, jar toolchain, `dist/`, `.tools/`. Yang masuk git hanya `toolchain.json` dan skrip pembangunnya.

## 4. Yang sengaja tidak dibundel

| Tidak ada | Alasan |
|---|---|
| Kotlin compiler | 70 MB+, menaikkan APK ~2,5× (ADR-003) |
| d8 / R8 | tidak bisa jalan di API 24 |
| `zipalign` biner | diganti `ZipAligner` Kotlin (±100 baris, tanpa biner tambahan) |
| Gradle | diganti `kodenusa.json` (ADR-005) |
| JDK penuh (proot) | Tier 2, hanya layak ≥ 4 GB, belum dibangun (ADR-001) |

## 5. Cara memverifikasi ulang

```bash
export JAVA_HOME=/opt/jdk17 PATH=/opt/jdk17/bin:/opt/gradle/bin:$PATH ANDROID_HOME=$HOME/android-sdk

# 1) Pipeline tanpa emulator (JVM host, alat yang sama)
bash scripts/prove/build-hello.sh
bash scripts/prove/build-templates.sh

# 2) Pipeline di dalam HP untuk ketiga ABI emulator
./gradlew :app:assembleRelease -PsemuaAbi
/home/boxd/android-sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk

# 3) Uji perangkat yang menyentuh build
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=id.kodenusa.app.OnDeviceBuildTest,id.kodenusa.app.TemplatesDeviceTest
```

## 6. Bukti lintas versi Android

| API | Perangkat | Hasil |
|---|---|---|
| 24 | `kodenusa24` | template ter-build, APK terpasang |
| 34 | `kodenusa34` | build & pasang |
| 35 | `kodenusa35` | 142 uji perangkat hijau, rilis minified dipasang & dibuka |

Catatan: build pertama setelah instalasi menyalin `android.jar` 24 MB ke penyimpanan internal (21,4 detik di emulator). Build berikutnya (hangat) 1,4–1,9 detik. Angka HP fisik belum ada — lihat [lembar ukur](08-lembar-ukur-4gb.md).
