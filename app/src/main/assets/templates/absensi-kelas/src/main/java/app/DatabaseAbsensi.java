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
 * Penyimpanan absensi: satu tabel, hari dicatat sebagai teks tanggal lokal supaya rekap
 * "hari ini" mudah dihitung tanpa dependensi tambahan.
 */
public class DatabaseAbsensi extends SQLiteOpenHelper {

    private static final String NAMA_DB = "absensi.db";
    private static final int VERSI_DB = 1;
    private static final String TABEL = "absensi";
    private static final String KOLOM_NAMA = "nama";
    private static final String KOLOM_HADIR = "hadir";
    private static final String KOLOM_WAKTU = "waktu";

    public DatabaseAbsensi(Context context) {
        super(context, NAMA_DB, null, VERSI_DB);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABEL + " ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + KOLOM_NAMA + " TEXT NOT NULL, "
            + KOLOM_HADIR + " INTEGER NOT NULL, "
            + KOLOM_WAKTU + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int lama, int baru) {
        db.execSQL("DROP TABLE IF EXISTS " + TABEL);
        onCreate(db);
    }

    public void catat(String nama, boolean hadir) {
        ContentValues isi = new ContentValues();
        isi.put(KOLOM_NAMA, nama);
        isi.put(KOLOM_HADIR, hadir ? 1 : 0);
        isi.put(KOLOM_WAKTU, hariIni());
        getWritableDatabase().insert(TABEL, null, isi);
    }

    /** Rekap siswa hari ini, terbaru di atas. */
    public List<String> rekapHariIni() {
        List<String> hasil = new ArrayList<>();
        Cursor kursor = getReadableDatabase().query(
            TABEL, null, KOLOM_WAKTU + "=?", new String[]{hariIni()}, null, null, "_id DESC");
        try {
            int kolomNama = kursor.getColumnIndexOrThrow(KOLOM_NAMA);
            int kolomHadir = kursor.getColumnIndexOrThrow(KOLOM_HADIR);
            while (kursor.moveToNext()) {
                hasil.add((kursor.getInt(kolomHadir) == 1 ? "\u2713 " : "\u2717 ")
                    + kursor.getString(kolomNama));
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }

    public int jumlahHadir() {
        return hitung(true);
    }

    public int jumlahTidakHadir() {
        return hitung(false);
    }

    private int hitung(boolean hadir) {
        Cursor kursor = getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + TABEL + " WHERE " + KOLOM_WAKTU + "=? AND " + KOLOM_HADIR + "=?",
            new String[]{hariIni(), hadir ? "1" : "0"});
        try {
            return kursor.moveToFirst() ? kursor.getInt(0) : 0;
        } finally {
            kursor.close();
        }
    }

    private static String hariIni() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
