# PRISM

---

**Platform for Reservation and Issue Management** adalah aplikasi web terpusat untuk mengelola reservasi fasilitas kampus dan pelaporan kerusakan atau gangguan pada fasilitas tersebut.

PRISM membantu pengunjung dan civitas akademika melihat fasilitas serta ketersediaannya, mengajukan reservasi, dan melaporkan masalah. Petugas dan Admin dapat memproses reservasi, menangani laporan, mengatur periode blokir fasilitas, serta memantau pemanfaatan fasilitas melalui dashboard dan rekapitulasi.

Proyek ini dikembangkan untuk memenuhi tugas mata kuliah **Pengembangan Platform Khusus**.

## Teknologi yang Digunakan ( Tech Stack )

- **Bahasa pemrograman:** Java 25
- **Framework backend:** Spring Boot 4.1.1 dengan arsitektur MVC
- **Template engine:** Thymeleaf
- **Keamanan:** Spring Security
- **Persistensi:** Spring Data JPA dan Hibernate
- **Basis data:** MySQL
- **Migrasi basis data:** Flyway
- **Build tool:** Apache Maven
- **Pengujian:** JUnit, Spring Boot Test, Spring Security Test, dan H2
- **Frontend:** HTML, CSS, JavaScript, serta Thymeleaf untuk server-side rendering
- **UI foundation:** TailwindCSS dan HTMX dengan aset yang dibangun secara lokal

## Fitur Aplikasi

### Autentikasi dan Akun

- Registrasi mandiri untuk Pengguna.
- Verifikasi atau penolakan akun oleh Admin.
- Login dan logout dengan pengamanan berbasis sesi.
- Pengelolaan akun Pengguna dan Petugas oleh Admin.
- Otorisasi berdasarkan peran: Pengunjung, Pengguna, Petugas, dan Admin.

### Fasilitas dan Ketersediaan

- Melihat daftar fasilitas dan ketersediaan per slot waktu.
- Mencari fasilitas berdasarkan tipe, lokasi, dan kapasitas.
- Menampilkan perbedaan status tersedia, dipesan, dan diblokir.
- Menampilkan alasan publik dari blokir fasilitas tanpa membuka identitas pemohon.
- Menambah, mengubah, dan menonaktifkan fasilitas oleh Admin.

### Reservasi

- Mengajukan reservasi fasilitas pada satu tanggal dan mengisi tujuan penggunaan.
- Validasi jam operasional 07.00-20.00 WIB dan interval 30 menit.
- Aturan khusus pemesanan Aula untuk satu hari operasional penuh.
- Pengunduhan template proposal dan pengunggahan proposal bertanda tangan untuk reservasi yang memenuhi syarat.
- Persetujuan atau penolakan reservasi oleh Petugas dan Admin.
- Pencegahan konflik jadwal secara transaksional.
- Batas maksimal tujuh reservasi disetujui yang belum berakhir untuk setiap Pengguna.
- Pembatalan reservasi sesuai hak akses dan batas waktu.
- Kedaluwarsa otomatis untuk reservasi yang masih menunggu.
- Riwayat, detail, dan status reservasi Pengguna.

### Pelaporan Kerusakan

- Membuat laporan kerusakan atau masalah pada fasilitas.
- Menyertakan kategori, deskripsi, dan foto laporan.
- Memantau status laporan milik sendiri.
- Memproses laporan oleh Petugas dan Admin: baru, diproses, selesai, atau ditolak.
- Mencatat catatan resolusi, petugas penanganan, dan waktu perubahan.

### Blokir Fasilitas

- Mengelola jenis blokir: perbaikan, pemeliharaan terencana, dan keadaan kahar.
- Membuat, memperpanjang, dan menyelesaikan blokir oleh Petugas dan Admin.
- Menghubungkan blokir perbaikan dengan laporan kerusakan.
- Membatalkan reservasi disetujui dan menolak reservasi menunggu yang terdampak blokir.
- Menampilkan konfirmasi jumlah reservasi yang terdampak sebelum blokir disimpan.
- Mengaktifkan dan menyelesaikan blokir berdasarkan waktu.
- Membuka kembali slot mendatang ketika blokir diselesaikan lebih awal.

### Dashboard, Rekap, dan Ekspor

- Dashboard antrean reservasi dan laporan untuk Petugas dan Admin.
- Rekap okupansi fasilitas dan frekuensi kerusakan.
- Filter rekap berdasarkan periode, fasilitas, tipe, dan lokasi.
- Ekspor rekap dalam format CSV, XLSX, dan PDF.

## Peran Pengguna

| Peran | Akses utama |
| --- | --- |
| Pengunjung | Melihat fasilitas, ketersediaan, dan alasan publik blokir tanpa login. |
| Pengguna | Mengajukan dan membatalkan reservasi sendiri, mengunggah proposal, membuat laporan, serta melihat riwayat sendiri. |
| Petugas | Memproses reservasi dan laporan, memvalidasi proposal, serta mengelola blokir fasilitas. |
| Admin | Seluruh hak Petugas, ditambah pengelolaan akun, fasilitas, jenis blokir, rekap, dan ekspor. |

## Struktur Teknologi

Aplikasi menerapkan pola **MVC** dengan pembagian tanggung jawab sebagai berikut:

- **Controller:** menerima permintaan dan mengatur alur aplikasi.
- **Service:** menjalankan aturan bisnis dan transaksi.
- **Repository:** menangani akses data melalui Spring Data JPA.
- **Domain/Model:** merepresentasikan entitas dan status aplikasi.
- **View:** menghasilkan halaman HTML menggunakan Thymeleaf.
- **Migration:** menyimpan migrasi skema basis data pada `src/main/resources/db/migration`.


## Struktur Folder Proyek

Aplikasi ini menggunakan struktur **feature-based**, yaitu kode dipisahkan berdasarkan fitur atau domain bisnis. Melalui pendekatan ini, komponen yang berkaitan dengan satu fitur dapat dikelola dalam satu folder sehingga lebih mudah dikembangkan dan dipelihara.

```text
src/
├── main/
│   ├── java/com/github/kafeyangasli/prism/
│   │   ├── feature/
│   │   │   ├── user/          # Akun, peran, dan status pengguna
│   │   │   ├── facility/      # Data fasilitas dan ketersediaan
│   │   │   ├── reservation/   # Pengajuan dan pengelolaan reservasi
│   │   │   ├── report/        # Pelaporan kerusakan dan resolusi
│   │   │   └── blockage/      # Blokir fasilitas dan jenis blokir
│   │   ├── config/            # Konfigurasi aplikasi
│   │   ├── security/          # Autentikasi dan otorisasi
│   │   └── shared/            # Komponen umum dan exception
│   └── resources/
│       ├── db/migration/      # Migrasi skema basis data dengan Flyway
│       └── application.yaml   # Konfigurasi aplikasi
└── test/                      # Pengujian aplikasi
```

Setiap folder pada `feature` dapat memiliki lapisan `model` dan `repository`, serta dapat dikembangkan dengan lapisan `controller` dan `service` sesuai kebutuhan fitur. Pemisahan ini menjaga agar aturan bisnis, akses data, dan komponen pendukung setiap fitur tetap terorganisir.

## Menjalankan Proyek

Pastikan Java 25 dan MySQL telah terpasang, variabel lingkungan (environment variables) telah diatur, dan layanan MySQL sedang berjalan. Lalu, jalankan perintah berikut:

```bash
./mvnw spring-boot:run
```

Pada Windows, gunakan:

```powershell
.\mvnw.cmd spring-boot:run
```

Konfigurasi koneksi basis data dapat ditambahkan atau disesuaikan pada `src/main/resources/application.yaml`. Flyway akan menjalankan migrasi skema secara berurutan saat aplikasi dimulai.

### Membangun aset frontend

Instal dependensi frontend satu kali dan bangun aset sebelum menjalankan aplikasi:

```bash
npm install
npm run build
```

Perintah build menghasilkan CSS Tailwind di `src/main/resources/static/css/app.css` dan menyalin HTMX ke `src/main/resources/static/vendor/htmx.min.js`. Konvensi komponen dan pola HTMX didokumentasikan di `docs/UI_CONVENTIONS.md`.
