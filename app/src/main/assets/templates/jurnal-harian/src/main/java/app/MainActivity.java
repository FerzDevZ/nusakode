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

/** Layar jurnal: tulis catatan hari ini, pilih mood, lihat daftar catatan. */
public class MainActivity extends Activity {

    private DatabaseJurnal database;
    private EditText inputIsi;
    private TextView teksRingkasan;
    private TextView teksKosong;
    private ListView daftar;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<>();
    private int moodTerpilih = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseJurnal(this);
        inputIsi = findViewById(R.id.input_isi);
        teksRingkasan = findViewById(R.id.teks_ringkasan);
        teksKosong = findViewById(R.id.teks_kosong);
        daftar = findViewById(R.id.daftar_catatan);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, baris);
        daftar.setAdapter(adapter);

        pasangTombolMood();
        Button simpan = findViewById(R.id.tombol_simpan);
        simpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simpan();
            }
        });

        muatUlang();
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void pasangTombolMood() {
        int[] idTombol = {
            R.id.mood_1, R.id.mood_2, R.id.mood_3, R.id.mood_4, R.id.mood_5,
        };
        View.OnClickListener pendengar = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moodTerpilih = Integer.parseInt((String) view.getTag());
                tandaiMoodTerpilih();
            }
        };
        for (int id : idTombol) {
            findViewById(id).setOnClickListener(pendengar);
        }
        tandaiMoodTerpilih();
    }

    private void tandaiMoodTerpilih() {
        int[] idTombol = {
            R.id.mood_1, R.id.mood_2, R.id.mood_3, R.id.mood_4, R.id.mood_5,
        };
        for (int i = 0; i < idTombol.length; i++) {
            findViewById(idTombol[i]).setAlpha(i + 1 == moodTerpilih ? 1f : 0.45f);
        }
    }

    private void simpan() {
        String isi = inputIsi.getText().toString().trim();
        if (isi.isEmpty()) {
            Toast.makeText(this, R.string.isi_kosong, Toast.LENGTH_SHORT).show();
            return;
        }
        database.tulis(isi, moodTerpilih);
        inputIsi.setText("");
        muatUlang();
    }

    private void muatUlang() {
        baris.clear();
        baris.addAll(database.semua());
        adapter.notifyDataSetChanged();
        teksKosong.setVisibility(baris.isEmpty() ? View.VISIBLE : View.GONE);
        int rata = database.rataMoodHariIni();
        teksRingkasan.setText(rata == 0
            ? getString(R.string.belum_tulis_hari_ini)
            : getString(R.string.ringkasan_hari_ini, rata));
    }
}
