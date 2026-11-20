package __APP_ID__.app;

import __APP_ID__.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Contoh paling kecil yang tetap berjalan: satu layar, satu tombol, satu pesan yang berubah. */
public class MainActivity extends Activity {

    private int jumlahKetukan = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView sapaan = findViewById(R.id.teks_sapaan);
        Button tombol = findViewById(R.id.tombol_ketuk);

        tombol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumlahKetukan++;
                sapaan.setText("Sudah diketuk " + jumlahKetukan + " kali");
            }
        });
    }
}
