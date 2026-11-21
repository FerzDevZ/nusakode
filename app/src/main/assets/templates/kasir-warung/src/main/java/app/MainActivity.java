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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Layar kasir: masukkan nama dan harga, total diperbarui, riwayat tersimpan. */
public class MainActivity extends Activity {

    private DatabaseKasir database;
    private EditText inputNama;
    private EditText inputHarga;
    private TextView teksTotal;
    private TextView teksKosong;
    private ListView daftar;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseKasir(this);
        inputNama = findViewById(R.id.input_nama);
        inputHarga = findViewById(R.id.input_harga);
        teksTotal = findViewById(R.id.teks_total);
        teksKosong = findViewById(R.id.teks_kosong);
        daftar = findViewById(R.id.daftar_transaksi);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, baris);
        daftar.setAdapter(adapter);

        Button tambah = findViewById(R.id.tombol_tambah);
        tambah.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simpanTransaksi();
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

    private void simpanTransaksi() {
        String nama = inputNama.getText().toString().trim();
        String teksHarga = inputHarga.getText().toString().trim();

        if (nama.isEmpty()) {
            Toast.makeText(this, "Nama barang belum diisi", Toast.LENGTH_SHORT).show();
            return;
        }
        long harga;
        try {
            harga = Long.parseLong(teksHarga);
        } catch (NumberFormatException salah) {
            Toast.makeText(this, "Harga harus berupa angka", Toast.LENGTH_SHORT).show();
            return;
        }
        if (harga <= 0) {
            Toast.makeText(this, "Harga harus lebih dari nol", Toast.LENGTH_SHORT).show();
            return;
        }

        database.tambah(nama, harga);
        inputNama.setText("");
        inputHarga.setText("");
        muatUlang();
    }

    /** Semua transaksi jadi CSV: Android 10+ langsung ke folder Unduhan, di bawahnya lewat bagikan teks. */
    private void eksporCsv() {
        List<String> barisSemua = new ArrayList<>();
        barisSemua.add(Csv.baris("tanggal", "nama", "harga"));
        SimpleDateFormat tanggal = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (Produk produk : database.semua()) {
            barisSemua.add(Csv.baris(
                tanggal.format(new Date(produk.waktu)),
                produk.nama,
                String.valueOf(produk.harga)));
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
        nilai.put(MediaStore.MediaColumns.DISPLAY_NAME, "kasir-warung.csv");
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
            Toast.makeText(this, getString(R.string.csv_tersimpan, "kasir-warung.csv"),
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
        List<Produk> transaksi = database.semua();
        baris.clear();
        NumberFormat rupiah = NumberFormat.getNumberInstance(new Locale("in", "ID"));
        for (Produk produk : transaksi) {
            baris.add(produk.nama + "  ·  Rp " + rupiah.format(produk.harga));
        }
        adapter.notifyDataSetChanged();

        teksKosong.setVisibility(baris.isEmpty() ? View.VISIBLE : View.GONE);
        teksTotal.setText("Total hari ini: Rp " + rupiah.format(database.totalHariIni()));
    }
}
