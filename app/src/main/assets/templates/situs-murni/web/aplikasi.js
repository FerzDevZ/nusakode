// Data barang ditaruh di sini supaya pemilik warung cukup mengubah satu tempat.
const BARANG = [
  { nama: 'Beras 5 kg', harga: 62000 },
  { nama: 'Minyak 2 liter', harga: 34000 },
  { nama: 'Gula 1 kg', harga: 17000 },
  { nama: 'Telur 1 kg', harga: 28000 }
];

const NOMOR_WHATSAPP = '6281234567890'; // ganti dengan nomor warung

const rupiah = (angka) => 'Rp' + angka.toLocaleString('id-ID');

function tampilkanBarang() {
  const daftar = document.getElementById('daftar-barang');
  let total = 0;

  BARANG.forEach((barang) => {
    total += barang.harga;
    const baris = document.createElement('li');
    const nama = document.createElement('span');
    nama.textContent = barang.nama;
    const harga = document.createElement('span');
    harga.textContent = rupiah(barang.harga);
    baris.append(nama, harga);
    daftar.append(baris);
  });

  document.getElementById('total').textContent = rupiah(total);
}

function kirimPesanan() {
  const nama = document.getElementById('nama').value.trim();
  const bantuan = document.getElementById('bantuan');

  if (nama.length < 2) {
    bantuan.textContent = 'Isi nama pembeli dulu (minimal 2 huruf).';
    document.getElementById('nama').focus();
    return;
  }

  const isi = BARANG.map((barang) => '- ' + barang.nama + ' (' + rupiah(barang.harga) + ')').join('\n');
  const pesan = 'Halo, saya ' + nama + '. Saya mau pesan:\n' + isi;
  const tautan = 'https://wa.me/' + NOMOR_WHATSAPP + '?text=' + encodeURIComponent(pesan);

  bantuan.textContent = 'Membuka WhatsApp…';
  window.open(tautan, '_blank');
}

tampilkanBarang();
document.getElementById('pesan').addEventListener('click', kirimPesanan);
