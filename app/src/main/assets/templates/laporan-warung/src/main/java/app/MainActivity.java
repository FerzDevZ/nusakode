package __APP_ID__.app;

import __APP_ID__.R;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Laporan warung: catat uang masuk dan keluar per hari, lihat total dan sisa.
 * Data hidup di SQLite di dalam HP — tanpa internet, tanpa akun (ADR-001).
 */
public class MainActivity extends Activity {

    private DatabaseLaporan database;
    private EditText inputKeterangan;
    private EditText inputJumlah;
    private TextView teksRingkasan;
    private TextView teksKosong;
    private ListView daftar;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseLaporan(this);
        inputKeterangan = findViewById(R.id.input_keterangan);
        inputJumlah = findViewById(R.id.input_jumlah);
        teksRingkasan = findViewById(R.id.teks_ringkasan);
        teksKosong = findViewById(R.id.teks_kosong);
        daftar = findViewById(R.id.daftar_catatan);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, baris);
        daftar.setAdapter(adapter);

        Button masuk = findViewById(R.id.tombol_masuk);
        masuk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                catat(true);
            }
        });

        Button keluar = findViewById(R.id.tombol_keluar);
        keluar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                catat(false);
            }
        });

        Button ekspor = findViewById(R.id.tombol_ekspor);
        ekspor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                eksporCsv();
            }
        });

        muatUlang();
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void catat(boolean masuk) {
        String keterangan = inputKeterangan.getText().toString().trim();
        String teksJumlah = inputJumlah.getText().toString().trim();
        if (keterangan.isEmpty() || teksJumlah.isEmpty()) {
            Toast.makeText(this, R.string.isi_belum_lengkap, Toast.LENGTH_SHORT).show();
            return;
        }
        long jumlah;
        try {
            jumlah = Long.parseLong(teksJumlah);
        } catch (NumberFormatException galat) {
            Toast.makeText(this, R.string.jumlah_tak_sah, Toast.LENGTH_SHORT).show();
            return;
        }
        if (jumlah <= 0) {
            Toast.makeText(this, R.string.jumlah_tak_sah, Toast.LENGTH_SHORT).show();
            return;
        }
        database.catat(keterangan, jumlah, masuk);
        inputKeterangan.setText("");
        inputJumlah.setText("");
        muatUlang();
    }

    /** Semua catatan jadi CSV: Android 10+ langsung ke folder Unduhan, di bawahnya lewat bagikan teks. */
    private void eksporCsv() {
        List<String> barisSemua = new ArrayList<>();
        barisSemua.add(Csv.baris("tanggal", "keterangan", "jenis", "jumlah"));
        for (String[] catatan : database.semuaUntukCsv()) {
            barisSemua.add(Csv.baris(catatan[0], catatan[1], catatan[2], catatan[3]));
        }
        String isi = Csv.dokumen(barisSemua);
        if (Build.VERSION.SDK_INT >= 29) {
            simpanKeUnduhan(isi);
        } else {
            Intent kirim = new Intent(Intent.ACTION_SEND);
            kirim.setType("text/plain");
            kirim.putExtra(Intent.EXTRA_TEXT, isi);
            startActivity(Intent.createChooser(kirim, getString(R.string.tombol_ekspor)));
        }
    }

    /** Menulis CSV ke folder Unduhan lewat MediaStore — tanpa izin penyimpanan (Android 10+). */
    private void simpanKeUnduhan(String isi) {
        ContentValues nilai = new ContentValues();
        nilai.put(MediaStore.MediaColumns.DISPLAY_NAME, "laporan-warung.csv");
        nilai.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        nilai.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, nilai);
        if (uri == null) {
            Toast.makeText(this, getString(R.string.csv_gagal, "berkas tidak bisa dibuat"),
                Toast.LENGTH_SHORT).show();
            return;
        }
        OutputStream keluaran = null;
        try {
            keluaran = getContentResolver().openOutputStream(uri);
            if (keluaran == null) {
                throw new IOException("berkas tidak bisa dibuka");
            }
            keluaran.write(isi.getBytes("UTF-8"));
            Toast.makeText(this, getString(R.string.csv_tersimpan, "laporan-warung.csv"),
                Toast.LENGTH_LONG).show();
        } catch (IOException galat) {
            Toast.makeText(this, getString(R.string.csv_gagal, galat.getMessage()),
                Toast.LENGTH_SHORT).show();
        } finally {
            if (keluaran != null) {
                try {
                    keluaran.close();
                } catch (IOException diabaikan) {
                    // Berkas sudah ditulis; kegagalan tutup tidak mengubah isi.
                }
            }
        }
    }

    private void muatUlang() {
        baris.clear();
        baris.addAll(database.rekapHariIni());
        adapter.notifyDataSetChanged();
        teksKosong.setVisibility(baris.isEmpty() ? View.VISIBLE : View.GONE);
        NumberFormat rupiah = NumberFormat.getNumberInstance(new Locale("in", "ID"));
        teksRingkasan.setText(getString(
            R.string.judul_ringkasan,
            rupiah.format(database.totalMasuk()),
            rupiah.format(database.totalKeluar()),
            rupiah.format(database.totalMasuk() - database.totalKeluar())));
    }
}
