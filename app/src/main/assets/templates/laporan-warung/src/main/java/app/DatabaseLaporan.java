package __APP_ID__.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Penyimpanan laporan keuangan harian. `masuk = 1` berarti uang masuk, `0` uang keluar.
 * Tanggal disimpan sebagai teks yyyy-MM-dd supaya rekap per hari sederhana dan pasti benar.
 */
public class DatabaseLaporan extends SQLiteOpenHelper {

    private static final String NAMA_DB = "laporan.db";
    private static final int VERSI_DB = 1;
    private static final String TABEL = "catatan";
    private static final String KOLOM_KETERANGAN = "keterangan";
    private static final String KOLOM_JUMLAH = "jumlah";
    private static final String KOLOM_MASUK = "masuk";
    private static final String KOLOM_WAKTU = "waktu";

    public DatabaseLaporan(Context context) {
        super(context, NAMA_DB, null, VERSI_DB);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABEL + " ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + KOLOM_KETERANGAN + " TEXT NOT NULL, "
            + KOLOM_JUMLAH + " INTEGER NOT NULL, "
            + KOLOM_MASUK + " INTEGER NOT NULL, "
            + KOLOM_WAKTU + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int lama, int baru) {
        db.execSQL("DROP TABLE IF EXISTS " + TABEL);
        onCreate(db);
    }

    public void catat(String keterangan, long jumlah, boolean masuk) {
        ContentValues isi = new ContentValues();
        isi.put(KOLOM_KETERANGAN, keterangan);
        isi.put(KOLOM_JUMLAH, jumlah);
        isi.put(KOLOM_MASUK, masuk ? 1 : 0);
        isi.put(KOLOM_WAKTU, hariIni());
        getWritableDatabase().insert(TABEL, null, isi);
    }

    /** Daftar catatan hari ini, terbaru di atas, dengan tanda +/-. */
    public List<String> rekapHariIni() {
        List<String> hasil = new ArrayList<>();
        Cursor kursor = getReadableDatabase().query(
            TABEL, null, KOLOM_WAKTU + "=?", new String[]{hariIni()}, null, null, "_id DESC");
        try {
            int kolomKeterangan = kursor.getColumnIndexOrThrow(KOLOM_KETERANGAN);
            int kolomJumlah = kursor.getColumnIndexOrThrow(KOLOM_JUMLAH);
            int kolomMasuk = kursor.getColumnIndexOrThrow(KOLOM_MASUK);
            while (kursor.moveToNext()) {
                hasil.add((kursor.getInt(kolomMasuk) == 1 ? "+ " : "- ")
                    + kursor.getString(kolomKeterangan)
                    + ": " + kursor.getLong(kolomJumlah));
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }

    /** Semua catatan (bukan hanya hari ini) untuk ekspor CSV: {tanggal, keterangan, jenis, jumlah}. */
    public List<String[]> semuaUntukCsv() {
        List<String[]> hasil = new ArrayList<>();
        Cursor kursor = getReadableDatabase().query(
            TABEL, null, null, null, null, null, "_id ASC");
        try {
            int kolomKeterangan = kursor.getColumnIndexOrThrow(KOLOM_KETERANGAN);
            int kolomJumlah = kursor.getColumnIndexOrThrow(KOLOM_JUMLAH);
            int kolomMasuk = kursor.getColumnIndexOrThrow(KOLOM_MASUK);
            int kolomWaktu = kursor.getColumnIndexOrThrow(KOLOM_WAKTU);
            while (kursor.moveToNext()) {
                hasil.add(new String[]{
                    kursor.getString(kolomWaktu),
                    kursor.getString(kolomKeterangan),
                    kursor.getInt(kolomMasuk) == 1 ? "MASUK" : "KELUAR",
                    String.valueOf(kursor.getLong(kolomJumlah)),
                });
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }

    public long totalMasuk() {
        return total(true);
    }

    public long totalKeluar() {
        return total(false);
    }

    private long total(boolean masuk) {
        Cursor kursor = getReadableDatabase().rawQuery(
            "SELECT COALESCE(SUM(" + KOLOM_JUMLAH + "), 0) FROM " + TABEL
                + " WHERE " + KOLOM_WAKTU + "=? AND " + KOLOM_MASUK + "=?",
            new String[]{hariIni(), masuk ? "1" : "0"});
        try {
            return kursor.moveToFirst() ? kursor.getLong(0) : 0L;
        } finally {
            kursor.close();
        }
    }

    private static String hariIni() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
