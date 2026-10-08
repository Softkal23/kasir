# APK Kasir Warkop48

APK ini hanya pembungkus: membuka https://warkop48.freedev.app/#/admin. Data tetap di server yang sama dengan web pelanggan dan Owner.
Setiap perubahan di hosting otomatis ikut berubah di aplikasi; APK tidak perlu dibuat ulang.

## Cara membuat APK tanpa Android Studio (GitHub Actions, gratis)
1. Buat akun di github.com, lalu buat repositori baru (Private boleh).
2. Upload SEMUA isi folder ini (termasuk folder .github) ke repositori itu.
3. Buka tab **Actions**, pilih **Build APK Kasir**, klik **Run workflow**.
4. Tunggu sekitar 5-10 menit sampai centang hijau. APK otomatis terbit sebagai **Release** di repositori.
5. Di HP/tablet, buka https://github.com/NAMA_AKUN/NAMA_REPO/releases/latest lalu ketuk **warkop48-kasir.apk** untuk mengunduh, buka, dan pasang (izinkan "pasang dari sumber tidak dikenal").
   Agar tautan bisa dibuka tanpa login GitHub, jadikan repositori **Public** (Settings > General > Danger Zone > Change visibility). Isi repositori ini tidak berisi password.

## Yang perlu dites di tablet (belum terbukti)
- Login Kasir, daftar pesanan, bunyi pesanan baru.
- Cetak ke PT-210 lewat mode RawBT (pasang RawBT dan pasangkan printer lebih dulu). Tombol pembuka RawBT dari dalam APK belum diuji.
- Bluetooth langsung dari browser TIDAK tersedia di dalam APK.
- Notifikasi saat aplikasi ditutup (Web Push) kemungkinan tidak aktif di dalam APK.
