package __APP_ID__.app;

import __APP_ID__.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
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
