package __APP_ID__.app;

import java.util.List;

/**
 * Pembuat CSV untuk ekspor data. Aturan escape mengikuti RFC 4180:
 * bidang yang memuat koma, kutip, atau baris baru dibungkus kutip ganda;
 * kutip di dalam bidang digandakan. Satu sumber untuk semua template —
 * berkas ini disalin ke projek saat template meminta ekspor CSV ("butuhCsv").
 */
public final class Csv {

    private Csv() {
    }

    /** Mengamankan satu bidang: kutip ganda digandakan, bidang berisiko dibungkus kutip. */
    public static String amankan(String nilai) {
        if (nilai == null) {
            return "";
        }
        boolean perluKutip = nilai.contains(",") || nilai.contains("\"")
            || nilai.contains("\n") || nilai.contains("\r");
        String bersih = nilai.replace("\"", "\"\"");
        return perluKutip ? "\"" + bersih + "\"" : bersih;
    }

    /** Satu baris CSV dari beberapa bidang, dipisah koma, tanpa ganti baris di akhir. */
    public static String baris(String... bidang) {
        StringBuilder hasil = new StringBuilder();
        for (int i = 0; i < bidang.length; i++) {
            if (i > 0) {
                hasil.append(',');
            }
            hasil.append(amankan(bidang[i]));
        }
        return hasil.toString();
    }

    /** Seluruh isi CSV: baris pertama lalu data, masing-masing diakhiri ganti baris. */
    public static String dokumen(List<String> barisSemua) {
        StringBuilder hasil = new StringBuilder();
        for (String satu : barisSemua) {
            hasil.append(satu).append('\n');
        }
        return hasil.toString();
    }
}
