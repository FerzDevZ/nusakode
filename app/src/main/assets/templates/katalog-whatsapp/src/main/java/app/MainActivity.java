package __APP_ID__.app;

import __APP_ID__.R;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Katalog sederhana: daftar produk di kode, satu ketukan membuka WhatsApp dengan pesan pesanan
 * yang sudah terisi. Nomor admin ada di res/values/strings.xml supaya mudah diganti.
 */
public class MainActivity extends Activity {

    private static final String[] NAMA_PRODUK = {
        "Keripik pisang 250 g",
        "Sambal bawang botol",
        "Kopi robusta 200 g",
        "Gula aren cair 100 ml",
    };

    private static final long[] HARGA = {15_000L, 25_000L, 30_000L, 18_000L};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final List<String> baris = new ArrayList<>();
        for (int i = 0; i < NAMA_PRODUK.length; i++) {
            baris.add(NAMA_PRODUK[i] + "\nRp " + HARGA[i]);
        }

        ListView daftar = findViewById(R.id.daftar_produk);
        daftar.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, baris));
        daftar.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> induk, View view, int posisi, long id) {
                pesanLewatWhatsapp(NAMA_PRODUK[posisi], HARGA[posisi]);
            }
        });
    }

    private void pesanLewatWhatsapp(String namaProduk, long harga) {
        String nomor = getString(R.string.nomor_admin);
        String pesan = "Halo, saya mau pesan " + namaProduk + " (Rp " + harga + ").";
        Uri tautan = Uri.parse("https://wa.me/" + nomor + "?text=" + Uri.encode(pesan));
        Intent intent = new Intent(Intent.ACTION_VIEW, tautan);
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "WhatsApp belum terpasang di HP ini", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(intent);
    }
}
