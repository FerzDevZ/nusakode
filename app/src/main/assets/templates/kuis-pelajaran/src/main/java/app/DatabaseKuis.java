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

/** Riwayat skor kuis: tabel tunggal, terbaru di atas. */
public class DatabaseKuis extends SQLiteOpenHelper {

    private static final String NAMA_DB = "kuis.db";
    private static final int VERSI_DB = 1;
    private static final String TABEL = "riwayat";
    private static final String KOLOM_SKOR = "skor";
    private static final String KOLOM_TOTAL = "total";
    private static final String KOLOM_WAKTU = "waktu";

    public DatabaseKuis(Context context) {
        super(context, NAMA_DB, null, VERSI_DB);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABEL + " ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + KOLOM_SKOR + " INTEGER NOT NULL, "
            + KOLOM_TOTAL + " INTEGER NOT NULL, "
            + KOLOM_WAKTU + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int lama, int baru) {
        db.execSQL("DROP TABLE IF EXISTS " + TABEL);
        onCreate(db);
    }

    public void simpan(int skor, int total) {
        ContentValues nilai = new ContentValues();
        nilai.put(KOLOM_SKOR, skor);
        nilai.put(KOLOM_TOTAL, total);
        nilai.put(KOLOM_WAKTU, System.currentTimeMillis());
        getWritableDatabase().insert(TABEL, null, nilai);
    }

    /** Riwayat skor terurut terbaru dulu, termasuk tanggalnya. */
    public List<String> semua() {
        List<String> hasil = new ArrayList<>();
        SimpleDateFormat tanggal = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        Cursor kursor = getReadableDatabase().query(
            TABEL, null, null, null, null, null, "_id DESC");
        try {
            int kolomSkor = kursor.getColumnIndexOrThrow(KOLOM_SKOR);
            int kolomTotal = kursor.getColumnIndexOrThrow(KOLOM_TOTAL);
            int kolomWaktu = kursor.getColumnIndexOrThrow(KOLOM_WAKTU);
            while (kursor.moveToNext()) {
                hasil.add(tanggal.format(new Date(kursor.getLong(kolomWaktu)))
                    + " — skor " + kursor.getInt(kolomSkor)
                    + "/" + kursor.getInt(kolomTotal));
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }
}
