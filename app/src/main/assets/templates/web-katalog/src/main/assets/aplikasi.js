'use strict';

const BARANG = [
  { id: 'beras', nama: 'Beras 5 kg', harga: 68000 },
  { id: 'minyak', nama: 'Minyak goreng 2 L', harga: 34000 },
  { id: 'gula', nama: 'Gula pasir 1 kg', harga: 16000 },
  { id: 'telur', nama: 'Telur 1 kg', harga: 29000 },
  { id: 'kopi', nama: 'Kopi sachet (10)', harga: 12000 },
  { id: 'sabun', nama: 'Sabun cuci 800 g', harga: 21000 }
];

const pesanan = new Map();
const rupiah = (angka) => 'Rp' + angka.toLocaleString('id-ID');

const daftar = document.getElementById('daftar-barang');
const total = document.getElementById('total');
const tombol = document.getElementById('pesan');
const status = document.getElementById('status');

function gambarDaftar() {
  for (const barang of BARANG) {
    const kartu = document.createElement('button');
    kartu.type = 'button';
    kartu.className = 'barang';
    kartu.setAttribute('aria-pressed', 'false');
    kartu.dataset.id = barang.id;

    const kiri = document.createElement('span');
    kiri.className = 'nama';
    kiri.textContent = barang.nama;

    const kanan = document.createElement('span');
    kanan.className = 'harga';
    kanan.textContent = rupiah(barang.harga);

    kartu.append(kiri, kanan);
    kartu.addEventListener('click', () => ubahPesanan(barang, kartu));
    daftar.append(kartu);
  }
}

function ubahPesanan(barang, kartu) {
  const jumlah = (pesanan.get(barang.id) || 0) + 1;
  if (jumlah > 9) {
    pesanan.delete(barang.id);
    kartu.setAttribute('aria-pressed', 'false');
    kartu.querySelector('.nama').textContent = barang.nama;
  } else {
    pesanan.set(barang.id, jumlah);
    kartu.setAttribute('aria-pressed', 'true');
    kartu.querySelector('.nama').textContent = barang.nama + ' ×' + jumlah;
  }
  hitungUlang();
}

function hitungUlang() {
  let jumlahTotal = 0;
  let jumlahBarang = 0;
  for (const [id, jumlah] of pesanan) {
    const barang = BARANG.find((item) => item.id === id);
    if (!barang) continue;
    jumlahTotal += barang.harga * jumlah;
    jumlahBarang += jumlah;
  }
  total.textContent = rupiah(jumlahTotal);
  tombol.disabled = jumlahBarang === 0;
  tombol.textContent = jumlahBarang === 0
    ? 'Pilih barang dulu'
    : 'Kirim ' + jumlahBarang + ' barang lewat WhatsApp';
  status.textContent = '';
}

function pesanWhatsApp() {
  const baris = [];
  for (const [id, jumlah] of pesanan) {
    const barang = BARANG.find((item) => item.id === id);
    if (barang) baris.push('- ' + barang.nama + ' x' + jumlah + ' = ' + rupiah(barang.harga * jumlah));
  }
  const teks = encodeURIComponent('Halo, saya mau pesan:\n' + baris.join('\n') + '\n\nTotal: ' + total.textContent);
  status.textContent = 'Pesanan siap dikirim: ' + total.textContent;
  window.location.href = 'https://wa.me/?text=' + teks;
}

gambarDaftar();
hitungUlang();
tombol.addEventListener('click', pesanWhatsApp);
