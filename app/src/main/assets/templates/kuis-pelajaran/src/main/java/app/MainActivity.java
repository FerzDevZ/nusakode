package __APP_ID__.app;

import __APP_ID__.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Kuis dari paket soal di assets/soal.json. Tidak ada server: soal, pilihan, dan kunci
 * jawaban semuanya di dalam APK. Satu kuis = semua soal sekali jalan, skor disimpan.
 */
public class MainActivity extends Activity {

    private static final String PAKET_SOAL = "soal.json";

    private List<String> pertanyaan = new ArrayList<>();
    private List<String[]> pilihan = new ArrayList<>();
    private List<Integer> kunci = new ArrayList<>();

    private TextView teksSoal;
    private TextView teksSkor;
    private ListView daftarRiwayat;
    private ArrayAdapter<String> adapterRiwayat;
    private final List<String> barisRiwayat = new ArrayList<>();

    private DatabaseKuis database;
    private int nomorSoal = 0;
    private int skor = 0;
    private boolean sedangKuis = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseKuis(this);
        teksSoal = findViewById(R.id.teks_soal);
        teksSkor = findViewById(R.id.teks_skor);
        daftarRiwayat = findViewById(R.id.daftar_riwayat);
        adapterRiwayat = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, barisRiwayat);
        daftarRiwayat.setAdapter(adapterRiwayat);

        try {
            muatSoal();
        } catch (IOException | JSONException salah) {
            teksSoal.setText(getString(R.string.soal_gagal, salah.getMessage()));
            return;
        }

        Button mulai = findViewById(R.id.tombol_mulai);
        mulai.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mulaiKuis();
            }
        });
        pasangTombolJawab();

        tampilkanPembuka();
        muatUlangRiwayat();
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void muatSoal() throws IOException, JSONException {
        InputStream masukan = getAssets().open(PAKET_SOAL);
        String isi;
        try {
            ByteArrayOutputStream keluaran = new ByteArrayOutputStream();
            byte[] penyangga = new byte[4096];
            int terbaca;
            while ((terbaca = masukan.read(penyangga)) != -1) {
                keluaran.write(penyangga, 0, terbaca);
            }
            isi = keluaran.toString("UTF-8");
        } finally {
            masukan.close();
        }
        JSONArray daftarSoal = new JSONArray(isi);
        for (int i = 0; i < daftarSoal.length(); i++) {
            JSONObject soal = daftarSoal.getJSONObject(i);
            pertanyaan.add(soal.getString("pertanyaan"));
            JSONArray pilihanSoal = soal.getJSONArray("pilihan");
            String[] teksPilihan = new String[pilihanSoal.length()];
            for (int j = 0; j < pilihanSoal.length(); j++) {
                teksPilihan[j] = pilihanSoal.getString(j);
            }
            pilihan.add(teksPilihan);
            kunci.add(soal.getInt("jawaban"));
        }
    }

    private void pasangTombolJawab() {
        int[] idTombol = {
            R.id.jawab_a, R.id.jawab_b, R.id.jawab_c, R.id.jawab_d,
        };
        View.OnClickListener pendengar = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jawab(Integer.parseInt((String) view.getTag()));
            }
        };
        for (int id : idTombol) {
            findViewById(id).setOnClickListener(pendengar);
        }
    }

    private void tampilkanPembuka() {
        sedangKuis = false;
        teksSoal.setText(getString(R.string.pembuka, pertanyaan.size()));
        tampilkanSkor(getString(R.string.skor_awal));
        aturTombolJawab(false, null);
    }

    private void mulaiKuis() {
        nomorSoal = 0;
        skor = 0;
        sedangKuis = true;
        tampilkanSoal();
    }

    private void tampilkanSoal() {
        teksSoal.setText(getString(R.string.soal_nomor, nomorSoal + 1, pertanyaan.get(nomorSoal)));
        String[] pilihanSoal = pilihan.get(nomorSoal);
        aturTombolJawab(true, pilihanSoal);
        tampilkanSkor(getString(R.string.skor_berjalan, skor));
    }

    private void aturTombolJawab(boolean aktif, String[] teksPilihan) {
        int[] idTombol = {
            R.id.jawab_a, R.id.jawab_b, R.id.jawab_c, R.id.jawab_d,
        };
        for (int i = 0; i < idTombol.length; i++) {
            Button tombol = findViewById(idTombol[i]);
            tombol.setEnabled(aktif);
            if (teksPilihan != null) {
                tombol.setText(teksPilihan[i]);
            }
        }
    }

    private void jawab(int pilihanDipilih) {
        if (!sedangKuis) {
            return;
        }
        boolean benar = pilihanDipilih == kunci.get(nomorSoal);
        if (benar) {
            skor++;
        }
        Toast.makeText(this, benar ? R.string.jawaban_benar : R.string.jawaban_salah,
            Toast.LENGTH_SHORT).show();
        nomorSoal++;
        if (nomorSoal < pertanyaan.size()) {
            tampilkanSoal();
        } else {
            selesaiKuis();
        }
    }

    private void selesaiKuis() {
        sedangKuis = false;
        database.simpan(skor, pertanyaan.size());
        teksSoal.setText(getString(R.string.hasil_akhir, skor, pertanyaan.size()));
        tampilkanSkor(getString(R.string.skor_berjalan, skor));
        aturTombolJawab(false, null);
        muatUlangRiwayat();
    }

    private void tampilkanSkor(String teks) {
        teksSkor.setText(teks);
    }

    private void muatUlangRiwayat() {
        barisRiwayat.clear();
        barisRiwayat.addAll(database.semua());
        adapterRiwayat.notifyDataSetChanged();
    }
}
