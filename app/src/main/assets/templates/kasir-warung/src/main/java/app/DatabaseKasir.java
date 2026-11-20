package __APP_ID__.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Penyimpanan transaksi memakai SQLite bawaan Android. Tidak ada server, tidak ada akun:
 * data tetap ada walaupun HP sedang offline.
 */
public class DatabaseKasir extends SQLiteOpenHelper {

    private static final String NAMA_DB = "kasir.db";
    private static final int VERSI_DB = 1;
    private static final String TABEL = "transaksi";

    public DatabaseKasir(Context context) {
        super(context, NAMA_DB, null, VERSI_DB);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABEL + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "nama TEXT NOT NULL,"
                + "harga INTEGER NOT NULL,"
                + "waktu INTEGER NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int versiLama, int versiBaru) {
        db.execSQL("DROP TABLE IF EXISTS " + TABEL);
        onCreate(db);
    }

    public long tambah(String nama, long harga) {
        ContentValues nilai = new ContentValues();
        nilai.put("nama", nama);
        nilai.put("harga", harga);
        nilai.put("waktu", System.currentTimeMillis());
        return getWritableDatabase().insert(TABEL, null, nilai);
    }

    public List<Produk> semua() {
        List<Produk> hasil = new ArrayList<>();
        Cursor kursor = getReadableDatabase().query(
            TABEL,
            new String[] {"id", "nama", "harga", "waktu"},
            null,
            null,
            null,
            null,
            "id DESC"
        );
        try {
            while (kursor.moveToNext()) {
                hasil.add(new Produk(
                    kursor.getLong(0),
                    kursor.getString(1),
                    kursor.getLong(2),
                    kursor.getLong(3)
                ));
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }

    public long totalHariIni() {
        long awalHari = System.currentTimeMillis() - (24L * 60L * 60L * 1000L);
        Cursor kursor = getReadableDatabase().rawQuery(
            "SELECT SUM(harga) FROM " + TABEL + " WHERE waktu >= ?",
            new String[] {String.valueOf(awalHari)}
        );
        try {
            return kursor.moveToFirst() ? kursor.getLong(0) : 0L;
        } finally {
            kursor.close();
        }
    }
}
