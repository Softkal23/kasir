# Warkop48 Admin (APK)

Cangkang Android (Capacitor) yang membuka halaman Kasir/Owner: https://warkop48.freedev.app/#/admin
Menu pelanggan tetap di website.

Build: tab Actions > Build APK > Run workflow. Hasil: tab Releases > Warkop48-Admin.apk

## Isi APK
- Layar tidak mati selama aplikasi terbuka (cocok untuk layar kasir).
- Bunyi pesanan baru boleh berbunyi tanpa menunggu sentuhan.
- Unduhan (cadangan database, ekspor CSV) masuk ke folder Download di HP.
- Ikon logo Warkop48 Buaran (folder `resources/`), kode Android ada di `patch/MainActivity.java`.

## Perbaikan v1.12.2
- **Cetak** (Rekap Shift, Keuangan, QR Meja): tombol Cetak kini membuka dialog cetak Android (bisa pilih printer atau "Simpan sebagai PDF").
- **Unduh QR meja** dan file lain berbasis `blob:` kini tersimpan ke folder Download. Android 6-9 akan meminta izin penyimpanan sekali.
- **Tombol Google disembunyikan** di APK karena Google memblokir login di WebView; pakai username/password.
- **RawBT**: bila belum terpasang, muncul pesan dan Play Store terbuka (sebelumnya tidak terjadi apa-apa).
- **Tanpa internet**: tampil halaman "Tidak ada koneksi internet" yang membuka aplikasi sendiri saat koneksi kembali.
- Halaman "Memuat..." kini terlihat (teks putih di latar wine, sebelumnya putih di putih).
- Batasan: notifikasi saat aplikasi ditutup tetap tidak ada (butuh Firebase). Biarkan aplikasi terbuka; layar tetap menyala dan bunyi pesanan berulang.

## Cetak Bluetooth langsung (v1.12.3)
- APK kini mencetak sendiri lewat Bluetooth biasa (SPP), **tanpa RawBT**. Pasangkan printer di Pengaturan Bluetooth HP, buka halaman Pesanan, ketuk tombol printer, pilih namanya, lalu Tes cetak.
- Android 12 ke atas meminta izin "Perangkat di sekitar" sekali. Izin ditambahkan otomatis oleh workflow (BLUETOOTH, BLUETOOTH_CONNECT).
- RawBT tetap tersedia sebagai cadangan di pilihan "Cara cetak".
- Fitur ini butuh situs versi baru (v1.12.3, folder `warkopku`) sudah diunggah ke hosting.
