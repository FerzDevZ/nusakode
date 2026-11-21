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
