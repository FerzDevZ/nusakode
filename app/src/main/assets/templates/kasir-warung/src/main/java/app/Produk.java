package __APP_ID__.app;

/** Satu baris transaksi. Sengaja kelas biasa tanpa library supaya APK tetap kecil. */
public class Produk {

    public final long id;
    public final String nama;
    public final long harga;
    public final long waktu;

    public Produk(long id, String nama, long harga, long waktu) {
        this.id = id;
        this.nama = nama;
        this.harga = harga;
        this.waktu = waktu;
    }
}
