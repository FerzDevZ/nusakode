package __APP_ID__.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Penyimpanan stok: satu tabel barang dengan stok dan batas minimum. Barang yang sisa
 * stoknya sampai di bawah minimum diberi penanda di daftar — peringatan tanpa server.
 */
public class DatabaseStok extends SQLiteOpenHelper {

    private static final String NAMA_DB = "stok.db";
    private static final int VERSI_DB = 1;
    private static final String TABEL = "barang";
    private static final String KOLOM_NAMA = "nama";
    private static final String KOLOM_STOK = "stok";
    private static final String KOLOM_MINIMUM = "minimum";

    public DatabaseStok(Context context) {
        super(context, NAMA_DB, null, VERSI_DB);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABEL + " ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + KOLOM_NAMA + " TEXT NOT NULL UNIQUE, "
            + KOLOM_STOK + " INTEGER NOT NULL, "
            + KOLOM_MINIMUM + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int lama, int baru) {
        db.execSQL("DROP TABLE IF EXISTS " + TABEL);
        onCreate(db);
    }

    /** Barang baru atau perbarui minimum kalau nama sudah ada. Kembali id-nya. */
    public long tambahAtauPerbarui(String nama, int stokAwal, int minimum) {
        ContentValues nilai = new ContentValues();
        nilai.put(KOLOM_NAMA, nama);
        nilai.put(KOLOM_STOK, stokAwal);
        nilai.put(KOLOM_MINIMUM, minimum);
        return getWritableDatabase().insertWithOnConflict(
            TABEL, null, nilai, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Mengubah stok barang: masuk menambah, keluar mengurangi (tidak boleh minus). */
    public void ubahStok(long id, boolean masuk, int jumlah) {
        Cursor kursor = getReadableDatabase().query(
            TABEL, new String[]{KOLOM_STOK}, "_id=?",
            new String[]{String.valueOf(id)}, null, null, null);
        int stokSekarang;
        try {
            if (!kursor.moveToFirst()) {
                return;
            }
            stokSekarang = kursor.getInt(0);
        } finally {
            kursor.close();
        }
        int stokBaru = masuk ? stokSekarang + jumlah : Math.max(0, stokSekarang - jumlah);
        ContentValues nilai = new ContentValues();
        nilai.put(KOLOM_STOK, stokBaru);
        getWritableDatabase().update(TABEL, nilai, "_id=?", new String[]{String.valueOf(id)});
    }

    /** Semua barang urut nama, dengan penanda " [TIPIS]" kalau stok sampai di bawah minimum. */
    public List<String> semua() {
        List<String> hasil = new ArrayList<>();
        Cursor kursor = getReadableDatabase().query(
            TABEL, null, null, null, null, null, KOLOM_NAMA + " ASC");
        try {
            int kolomNama = kursor.getColumnIndexOrThrow(KOLOM_NAMA);
            int kolomStok = kursor.getColumnIndexOrThrow(KOLOM_STOK);
            int kolomMinimum = kursor.getColumnIndexOrThrow(KOLOM_MINIMUM);
            while (kursor.moveToNext()) {
                int stok = kursor.getInt(kolomStok);
                int minimum = kursor.getInt(kolomMinimum);
                hasil.add(kursor.getString(kolomNama)
                    + " — sisa " + stok
                    + (stok <= minimum ? " [TIPIS]" : ""));
            }
        } finally {
            kursor.close();
        }
        return hasil;
    }

    /** Id barang pertama dengan nama persis, atau -1 kalau tidak ada. */
    public long cariId(String nama) {
        Cursor kursor = getReadableDatabase().query(
            TABEL, new String[]{"_id"}, KOLOM_NAMA + "=?",
            new String[]{nama}, null, null, null);
        try {
            return kursor.moveToFirst() ? kursor.getLong(0) : -1L;
        } finally {
            kursor.close();
        }
    }
}
