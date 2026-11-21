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

/**
 * Layar absensi kelas: ketik nama siswa, tandai hadir atau tidak, rekap tersimpan di SQLite.
 * Semua data hidup di dalam HP — tidak ada server, tidak ada internet (ADR-001).
 */
public class MainActivity extends Activity {

    private DatabaseAbsensi database;
    private EditText inputNama;
    private TextView teksRekap;
    private TextView teksKosong;
    private ListView daftar;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseAbsensi(this);
        inputNama = findViewById(R.id.input_nama);
        teksRekap = findViewById(R.id.teks_rekap);
        teksKosong = findViewById(R.id.teks_kosong);
        daftar = findViewById(R.id.daftar_siswa);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, baris);
        daftar.setAdapter(adapter);

        Button hadir = findViewById(R.id.tombol_hadir);
        hadir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                catat(true);
            }
        });

        Button tidakHadir = findViewById(R.id.tombol_tidak_hadir);
        tidakHadir.setOnClickListener(new View.OnClickListener() {
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

    private void catat(boolean hadir) {
        String nama = inputNama.getText().toString().trim();
        if (nama.isEmpty()) {
            Toast.makeText(this, R.string.nama_kosong, Toast.LENGTH_SHORT).show();
            return;
        }
        database.catat(nama, hadir);
        inputNama.setText("");
        muatUlang();
    }

    private void muatUlang() {
        baris.clear();
        baris.addAll(database.rekapHariIni());
        adapter.notifyDataSetChanged();
        teksKosong.setVisibility(baris.isEmpty() ? View.VISIBLE : View.GONE);
        teksRekap.setText(getString(R.string.format_rekap, database.jumlahHadir(), database.jumlahTidakHadir()));
    }
}
