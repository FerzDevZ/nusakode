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
 * Penyimpanan jurnal harian: satu tabel, tanggal sebagai teks yyyy-MM-dd supaya rekap
 * "hari ini" pasti benar tanpa dependensi tambahan. Mood disimpan 1-5.
 */
public class DatabaseJurnal extends SQLiteOpenHelper {

    private static final String NAMA_DB = "jurnal.db";
    private static final int VERSI_DB = 1;
    private static final String TABEL = "catatan";
    private static final String KOLOM_TANGGAL = "tanggal";
    private static final String KOLOM_ISI = "isi";
    private static final String KOLOM_MOOD = "mood";

    public DatabaseJurnal(Context context) {
        super(context, NAMA_DB, null, VERSI_DB);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABEL + " ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + KOLOM_TANGGAL + " TEXT NOT NULL, "
            + KOLOM_ISI + " TEXT NOT NULL, "
            + KOLOM_MOOD + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int lama, int baru) {
        db.execSQL("DROP TABLE IF EXISTS " + TABEL);
        onCreate(db);
    }

    public void tulis(String isi, int mood) {
        ContentValues nilai = new ContentValues();
        nilai.put(KOLOM_TANGGAL, hariIni());
        nilai.put(KOLOM_ISI, isi);
        nilai.put(KOLOM_MOOD, mood);
        getWritableDatabase().insert(TABEL, null, nilai);
    }

    /** Semua catatan, terbaru di atas, dengan penanda tanggal dan mood. */
    public List<String> semua() {
        List<String> hasil = new ArrayList<>();
        Cursor kursor = getReadableDatabase().query(
            TABEL, null, null, null, null, null, "_id DESC");
        try {
            int kolomTanggal = kursor.getColumnIndexOrThrow(KOLOM_TANGGAL);
            int kolomIsi = kursor.getColumnIndexOrThrow(KOLOM_ISI);
            int kolomMood = kursor.getColumnIndexOrThrow(KOLOM_MOOD);
            while (kursor.moveToNext()) {
                hasil.add(kursor.getString(kolomTanggal)
                    + " " + wajah(kursor.getInt(kolomMood))
                    + " — " + kursor.getString(kolomIsi));
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }

    /** Rata-rata mood hari ini dibulatkan, atau 0 bila belum menulis. */
    public int rataMoodHariIni() {
        Cursor kursor = getReadableDatabase().rawQuery(
            "SELECT AVG(" + KOLOM_MOOD + ") FROM " + TABEL + " WHERE " + KOLOM_TANGGAL + "=?",
            new String[]{hariIni()});
        try {
            if (!kursor.moveToFirst() || kursor.isNull(0)) {
                return 0;
            }
            return (int) Math.round(kursor.getDouble(0));
        } finally {
            kursor.close();
        }
    }

    private static String wajah(int mood) {
        switch (mood) {
            case 1: return "[1]";
            case 2: return "[2]";
            case 3: return "[3]";
            case 4: return "[4]";
            default: return "[5]";
        }
    }

    private static String hariIni() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
