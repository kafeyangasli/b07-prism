![Alt Text](src/main/resources/static/images/prism-dark.svg)

---

**Platform for Reservation and Issue Management** adalah aplikasi web terpusat untuk mengelola reservasi fasilitas kampus dan pelaporan kerusakan atau gangguan pada fasilitas tersebut.

PRISM membantu pengunjung dan civitas akademika melihat fasilitas serta ketersediaannya, mengajukan reservasi, dan melaporkan masalah. Petugas dan Admin dapat memproses reservasi, menangani laporan, mengatur periode blokir fasilitas, serta memantau pemanfaatan fasilitas melalui dashboard dan rekapitulasi.

> Proyek ini dikembangkan untuk memenuhi tugas mata kuliah **Pengembangan Platform Khusus**.

## Kontributor

Seluruh individu yang berkontribusi pada projek ini terafiliasi dengan
> **Departemen Informatika/Ilmu Komputer**\
> **Fakultas Sains dan Matematika**\
> **Universitas Diponegoro**

| Nama                           | NIM            | Profil GitHub                                      |
|--------------------------------|----------------|----------------------------------------------------|
| Imam Alfarezel                 | 24060124120028 | [Rezzel22](https://github.com/Rezzel22)            |
| Jordan Tenggara                | 24060124120044 | [jordantenggara](https://github.com/jordantenggara) |
| Maulana Ghazzam Adil Al Faiq   | 24060124130083 | [kafeyangasli](https://github.com/kafeyangasli)    |
| Ikrar Maheswara Rabbani Wibowo | 24060124140202 | [Maesh-shush](https://github.com/Maesh-shush)      |

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
- Pengaturan akun mandiri: nama tampilan, perubahan kata sandi, dan penonaktifan akun Pengguna dengan pemeriksaan reservasi aktif. [Detail fitur dan pengujian](docs/ACCOUNT_SETTINGS.md).
- Foto profil opsional: unggah JPEG/PNG/WebP, ganti atau hapus foto sendiri, dan pratinjau sebelum unggah. [Penyimpanan dan pengujian](docs/PROFILE_PICTURES.md).
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
- Setiap proposal yang dilampirkan, termasuk proposal opsional, wajib divalidasi dengan unggahan PDF resmi sebelum persetujuan. [Alur, migrasi, penyimpanan, dan pengujian](docs/VALIDATED_PROPOSALS.md).
- Pencegahan konflik jadwal secara transaksional.
- Reservasi Alat berdasarkan jumlah: stok bersama, alokasi sejak Menunggu, proposal untuk lebih dari 5 unit, dan pengelolaan stok oleh Admin. [Aturan, migrasi, dan pengujian](docs/EQUIPMENT_RESERVATIONS.md).
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
│   │   │   ├── user/           # Akun, peran, dan status pengguna
│   │   │   ├── facility/       # Data fasilitas dan ketersediaan
│   │   │   ├── reservation/    # Pengajuan dan pengelolaan reservasi
│   │   │   ├── report/         # Pelaporan kerusakan dan resolusi
│   │   │   ├── blockage/       # Blokir fasilitas dan jenis blokir
│   │   │   └── administration  # Peran administrasi  
│   │   ├── config/             # Konfigurasi aplikasi
│   │   ├── security/           # Autentikasi dan otorisasi
│   │   └── shared/             # Komponen umum dan exception
│   └── resources/
│       ├── static/             # Resource statis
│       ├── templates/          # Template Thymeleaf untuk SSR
│       ├── db/migration/       # Migrasi skema basis data dengan Flyway
│       └── application.yaml    # Konfigurasi aplikasi
└── test/                       # Pengujian aplikasi
```

Setiap folder pada `feature` dapat memiliki lapisan `model` dan `repository`, serta dapat dikembangkan dengan lapisan `controller` dan `service` sesuai kebutuhan fitur. Pemisahan ini menjaga agar aturan bisnis, akses data, dan komponen pendukung setiap fitur tetap terorganisir.

## Menjalankan Proyek

Jalankan perintah pada bagian ini dari direktori root repositori, yaitu direktori yang
berisi `pom.xml`, `mvnw`, dan `mvnw.cmd`.

### 1. Siapkan perangkat lunak

Pastikan perangkat berikut tersedia:

- **JDK 25**, sesuai konfigurasi `java.version` pada `pom.xml`;
- **MySQL 8 atau lebih baru** dan layanan MySQL dalam keadaan aktif;
- **Node.js dan npm** untuk membangun aset frontend;
- Git, jika proyek diperoleh melalui clone repositori.

Periksa instalasi dari terminal:

```powershell
java -version
node --version
npm --version
```

Maven tidak perlu dipasang secara global karena proyek menyediakan Maven Wrapper.
Pada Windows gunakan `mvnw.cmd`; pada Linux/macOS gunakan `./mvnw`.

### 2. Buat basis data MySQL

Masuk ke MySQL sebagai pengguna yang dapat membuat database, lalu buat database dan
pengguna khusus aplikasi. Ganti kata sandi contoh berikut dengan kata sandi lokal yang
aman:

```sql
CREATE DATABASE prism
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER 'prism_app'@'localhost' IDENTIFIED BY 'ganti_dengan_password_lokal';
GRANT ALL PRIVILEGES ON prism.* TO 'prism_app'@'localhost';
FLUSH PRIVILEGES;
```

Jika sudah memiliki database dan pengguna MySQL sendiri, langkah pembuatan tersebut
dapat dilewati. Pengguna database harus memiliki izin untuk membuat dan mengubah tabel
agar Flyway dapat menjalankan migrasi.

### 3. Atur konfigurasi lingkungan

Aplikasi membutuhkan tiga variabel koneksi database berikut:

| Variabel | Contoh nilai | Keterangan |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:mysql://localhost:3306/prism` | JDBC URL database PRISM |
| `DATABASE_USERNAME` | `prism_app` | Pengguna MySQL |
| `DATABASE_PASSWORD` | `ganti_dengan_password_lokal` | Kata sandi pengguna MySQL |

Pada PowerShell, atur variabel untuk sesi terminal yang sedang aktif:

```powershell
$env:DATABASE_URL = "jdbc:mysql://localhost:3306/prism"
$env:DATABASE_USERNAME = "prism_app"
$env:DATABASE_PASSWORD = "ganti_dengan_password_lokal"
```

Pada Bash atau Zsh:

```bash
export DATABASE_URL='jdbc:mysql://localhost:3306/prism'
export DATABASE_USERNAME='prism_app'
export DATABASE_PASSWORD='ganti_dengan_password_lokal'
```

File `.env.example` mencantumkan variabel tambahan untuk penyimpanan dan batas unggahan.
Salinan `.env` dapat digunakan sebagai catatan konfigurasi lokal, tetapi aplikasi tidak
memuat file `.env` secara otomatis. Variabel harus diekspor ke proses terminal atau
diatur melalui konfigurasi IDE/layanan yang menjalankan aplikasi. Jangan commit kata
sandi database ke repositori.

Konfigurasi opsional menggunakan nilai default berikut:

| Variabel | Nilai default | Fungsi |
| --- | --- | --- |
| `PRISM_PROPOSAL_STORAGE` | `./storage/proposals` | Penyimpanan proposal |
| `PRISM_FACILITY_STORAGE` | `./storage/facilities` | Penyimpanan gambar fasilitas |
| `PRISM_AVATAR_STORAGE` | `./storage/avatars` | Penyimpanan foto profil |
| `PRISM_AVATAR_MAX_BYTES` | `5242880` | Batas foto profil (5 MiB) |
| `PRISM_FACILITY_IMAGE_MAX_BYTES` | `5242880` | Batas satu gambar fasilitas (5 MiB) |
| `PRISM_FACILITY_IMAGE_MAX_COUNT` | `20` | Jumlah maksimal gambar per fasilitas |
| `PRISM_MULTIPART_MAX_FILE_SIZE` | `10MB` | Batas satu file unggahan |
| `PRISM_MULTIPART_MAX_REQUEST_SIZE` | `110MB` | Batas keseluruhan request unggahan |
| `PRISM_RESERVATION_LIFECYCLE_MS` | `60000` | Interval pemrosesan status reservasi (ms) |
| `PRISM_SEED_ENABLED` | `true` | Menjalankan seeder awal di luar profil produksi |
| `PRISM_SEED_DIRECTORY` | `./initial_data` | Lokasi sumber fasilitas awal |

Path relatif dihitung dari direktori kerja saat aplikasi dijalankan. Direktori di bawah
`storage/` dibuat saat dibutuhkan dan telah dikecualikan dari Git.

### 4. Bangun aset frontend

Instal dependensi frontend dan bangun CSS serta HTMX lokal:

```powershell
npm install
npm run build
```

Perintah ini menghasilkan `src/main/resources/static/css/app.css` dan menyalin HTMX ke
`src/main/resources/static/vendor/htmx.min.js`. Jalankan kembali `npm run build` setelah
mengubah template, CSS sumber, atau versi dependensi frontend.

### 5. Jalankan aplikasi

Untuk menjalankan aplikasi, gunakan:

```powershell
.\mvnw.cmd spring-boot:run
```

Pada Linux/macOS:

```bash
./mvnw spring-boot:run
```

Saat startup, Flyway menjalankan seluruh migrasi pada
`src/main/resources/db/migration`, lalu Hibernate memvalidasi kecocokan entity dengan
schema. Seeder kemudian otomatis mengisi fasilitas dan akun awal yang belum ada. Dengan
demikian, database baru langsung siap digunakan pada startup pertama tanpa profile atau
perintah tambahan. Jangan membuat tabel secara manual. Startup akan berhenti dengan
pesan kesalahan jika database tidak dapat diakses, schema tidak sesuai, atau sumber seed
tidak valid.

Setelah log menampilkan bahwa aplikasi telah dimulai, buka:

```text
http://localhost:8080
```

Hentikan aplikasi dengan `Ctrl+C` pada terminal yang menjalankannya.

### 6. Data awal dan akun pengembangan

Pada startup pertama, seeder otomatis membuat 18 fasilitas dari `initial_data`, tipe
fasilitas yang dibutuhkan, gambar pendamping beserta thumbnail, dan empat akun berikut.
Gambar `.jpg`, `.jpeg`, atau `.png` dipasangkan dengan file `.txt` yang memiliki nama
dasar sama, lalu disalin ke `PRISM_FACILITY_STORAGE`. Seeder juga mengisi galeri kosong
pada fasilitas yang sudah ada; galeri dan thumbnail yang sudah diunggah tetap dipertahankan.
Seeder bersifat idempoten: startup
berikutnya memeriksa kode fasilitas, kode/nama tipe, dan email sehingga tidak membuat
duplikat atau menimpa data yang sudah ada.

Seeder aktif secara default dan tidak berjalan jika profil `prod` atau `production`
aktif. Seeder juga dapat dimatikan secara eksplisit dengan environment variable
`PRISM_SEED_ENABLED=false`. Detail parsing data, pemetaan fasilitas, kredensial demo,
dan aturan idempotensi tersedia pada
[panduan seeder pengembangan](docs/DEVELOPMENT_SEED.md).

| Email | Kata sandi | Peran |
| --- | --- | --- |
| `pengguna1@university.ac.id` | `pengguna_prism` | PENGGUNA |
| `pengguna2@university.ac.id` | `pengguna_prism` | PENGGUNA |
| `petugas@university.ac.id` | `petugas_prism` | PETUGAS |
| `admin@university.ac.id` | `admin_prism` | ADMIN |

Kredensial tersebut hanya untuk pengembangan dan pengujian. Deployment produksi harus
mengaktifkan profil `prod` atau `production`; sebaiknya juga menetapkan
`PRISM_SEED_ENABLED=false` sebagai perlindungan konfigurasi tambahan.

### 7. Menjalankan dari IDE

Gunakan `com.github.kafeyangasli.prism.PrismApplication` sebagai main class. Tambahkan
`DATABASE_URL`, `DATABASE_USERNAME`, dan `DATABASE_PASSWORD` pada environment variables
run configuration. Seeder sudah aktif tanpa active profile. Untuk konfigurasi produksi,
gunakan opsi program `--spring.profiles.active=prod` dan set
`PRISM_SEED_ENABLED=false`.

Pastikan working directory mengarah ke root repositori agar `initial_data` dan path
penyimpanan relatif dapat ditemukan.

### 8. Build dan jalankan JAR

Bangun executable JAR dengan:

```powershell
.\mvnw.cmd clean package
```

Setelah seluruh test berhasil, jalankan artefak yang dihasilkan:

```powershell
java -jar target/prism-0.0.1-SNAPSHOT.jar
```

Lokasi `initial_data` harus tersedia ketika seeder aktif. Jika JAR dijalankan dari root
repositori, perintah biasa di atas sudah menjalankan seeder. Jika JAR dijalankan dari
direktori lain, berikan path absolut sumber seed:

```powershell
java -jar target/prism-0.0.1-SNAPSHOT.jar `
  --prism.seed.directory="C:\path\ke\b07-prism\initial_data"
```

### 9. Menjalankan test

Jalankan seluruh test standar dengan:

```powershell
.\mvnw.cmd test
```

Test standar menggunakan H2 untuk isolasi dan tidak mengubah database MySQL yang
ditentukan oleh `DATABASE_URL`. Beberapa test integrasi MySQL bersifat opt-in dan
didokumentasikan pada [pengujian reservasi alat](docs/EQUIPMENT_RESERVATIONS.md) serta
[panduan seeder pengembangan](docs/DEVELOPMENT_SEED.md).

### Pemecahan masalah umum

- **`DATABASE_URL` tidak ditemukan:** pastikan variabel diatur pada terminal atau run
  configuration yang sama dengan proses aplikasi.
- **`Communications link failure`:** pastikan layanan MySQL aktif, port pada JDBC URL
  benar, dan database dapat diakses dengan username/password tersebut.
- **`Access denied for user`:** periksa kredensial dan hak akses pengguna terhadap
  database `prism`.
- **Migrasi Flyway gagal:** baca nama serta pesan migrasi yang gagal. Jangan mengubah
  migration yang sudah pernah diterapkan; gunakan database pengembangan baru jika data
  lama tidak perlu dipertahankan.
- **Port 8080 sudah digunakan:** jalankan dengan `--server.port=8081`, misalnya
  `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`.
- **Aset tampilan tidak ditemukan atau usang:** jalankan `npm install` lalu
  `npm run build` sebelum memulai ulang aplikasi.
- **Seeder tidak menemukan `initial_data`:** jalankan dari root repositori atau atur
  `--prism.seed.directory` ke path absolut direktori tersebut.

Konvensi komponen dan pola HTMX didokumentasikan lebih lanjut pada
[`docs/UI_CONVENTIONS.md`](docs/UI_CONVENTIONS.md).
