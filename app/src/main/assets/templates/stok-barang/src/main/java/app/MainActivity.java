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

import java.util.ArrayList;
import java.util.List;

/** Layar stok: daftar barang, catat masuk/keluar, peringatan stok tipis. */
public class MainActivity extends Activity {

    private DatabaseStok database;
    private EditText inputNama;
    private EditText inputJumlah;
    private EditText inputMinimum;
    private TextView teksKosong;
    private ListView daftar;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseStok(this);
        inputNama = findViewById(R.id.input_nama);
        inputJumlah = findViewById(R.id.input_jumlah);
        inputMinimum = findViewById(R.id.input_minimum);
        teksKosong = findViewById(R.id.teks_kosong);
        daftar = findViewById(R.id.daftar_barang);

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

        muatUlang();
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void catat(boolean masuk) {
        String nama = inputNama.getText().toString().trim();
        String teksJumlah = inputJumlah.getText().toString().trim();
        if (nama.isEmpty()) {
            Toast.makeText(this, R.string.nama_kosong, Toast.LENGTH_SHORT).show();
            return;
        }
        int jumlah;
        try {
            jumlah = Integer.parseInt(teksJumlah);
        } catch (NumberFormatException salah) {
            Toast.makeText(this, R.string.jumlah_tak_sah, Toast.LENGTH_SHORT).show();
            return;
        }
        if (jumlah <= 0) {
            Toast.makeText(this, R.string.jumlah_tak_sah, Toast.LENGTH_SHORT).show();
            return;
        }

        long id = database.cariId(nama);
        if (id < 0) {
            int minimum = bacaMinimum();
            database.tambahAtauPerbarui(nama, masuk ? jumlah : 0, minimum);
        } else {
            database.ubahStok(id, masuk, jumlah);
        }
        inputNama.setText("");
        inputJumlah.setText("");
        muatUlang();
    }

    private int bacaMinimum() {
        String teks = inputMinimum.getText().toString().trim();
        if (teks.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(teks));
        } catch (NumberFormatException salah) {
            return 0;
        }
    }

    private void muatUlang() {
        baris.clear();
        baris.addAll(database.semua());
        adapter.notifyDataSetChanged();
        teksKosong.setVisibility(baris.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
