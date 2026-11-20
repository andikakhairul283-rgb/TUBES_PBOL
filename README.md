# CheckIn.id

CheckIn.id adalah aplikasi desktop berbasis JavaFX untuk membantu pengurus hotel mengelola reservasi, kedatangan dan keberangkatan tamu, pembayaran, serta kondisi kamar. Aplikasi ini dikembangkan oleh **Kelompok 7** sebagai proyek akhir mata kuliah Pemrograman Berorientasi Objek Lanjutan (PBOL).

## Anggota Kelompok 7

| Nama | NIM |
| --- | --- |
| Andika Chairul Ilham | 251402047 |
| Mochamad Rizki Agiantoro | 251402135 |
| Muhammad Azkha Amorie | 251402092 |
| Nadine Tantiara Hutagaol | 251402050 |
| Mar'ie Rizqullah | 251402129 |
| Ramadiyan Athallah Kusuma | 251402027 |

## Fitur berdasarkan peran

| Peran | Fitur utama |
| --- | --- |
| **Staf Administrasi** | Memeriksa ketersediaan kamar, membuat dan mengubah reservasi, memproses check-in dan check-out, mencatat pembayaran, serta mengelola pesanan layanan kamar. |
| **Owner** | Memantau dasbor operasional, melihat denah kamar, mengelola data tamu, tipe kamar, tarif, dan akun pengguna, serta mengekspor laporan okupansi dan pendapatan ke Excel atau PDF. |
| **Pengurus Maintenance** | Melihat status dan data teknis kamar, menangani tiket pembersihan dan perbaikan, mengajukan material, mengelola stok, serta mengirim pesan internal ke resepsionis. |

Sistem memeriksa benturan tanggal reservasi dan kondisi teknis kamar sebelum menyimpan pemesanan. Setelah tagihan dilunasi dan check-out selesai, aplikasi otomatis membuat tugas pembersihan untuk kamar tersebut.

## Cara menjalankan

Proyek ini menggunakan **JDK 25**, **JavaFX 21.0.6**, dan **Maven Wrapper**.

1. Buka folder proyek di VS Code.
2. Buka terminal PowerShell di folder yang berisi `pom.xml` dan `mvnw.cmd`.
3. Jalankan perintah berikut untuk memeriksa versi Java dan membuka aplikasi.

   ```powershell
   .\mvnw.cmd -v
   .\mvnw.cmd javafx:run
   ```

4. Pilih peran di layar login. Untuk demo awal, gunakan username yang belum terdaftar, misalnya `demo.staf`, dengan kata sandi minimal delapan karakter, misalnya `Demo1234`.

Pada penggunaan pertama, Maven Wrapper dapat mengunduh Maven dan dependensi proyek.

## Struktur kode dan penerapan PBOL

Kode utama berada di `src/main/java/com/example/tubespbolkel7` dan dijalankan melalui `HelloApplication`.

| Paket | Isi |
| --- | --- |
| `model` | Data dan aturan untuk reservasi, kamar, tamu, akun, tarif, serta layanan. |
| `ui` dan `ui.login` | Halaman JavaFX, navigasi berdasarkan peran, formulir, dan penanganan interaksi pengguna. |
| `report` | Perhitungan laporan okupansi dan pendapatan serta ekspor Excel dan PDF. |

- **Class dan object** digunakan untuk merepresentasikan reservasi melalui `HotelData.Booking` dan unit kamar melalui `RoomInventory.Unit`.
- **Enkapsulasi** diterapkan di `HotelData`. Perubahan reservasi dan status menginap dilakukan melalui metode seperti `save`, `checkIn`, dan `checkOut` agar setiap perubahan melewati validasi.
- **Komposisi** diterapkan saat `HotelData` mengelola objek `RoomInventory`, `GuestRegistry`, dan `ServiceLedger`.
- **Pewarisan dan overriding** digunakan oleh `HelloApplication` yang mewarisi `Application` dan meng-override `start`, serta oleh `LoginView` yang mewarisi `Pane` dan meng-override `layoutChildren`.
- **Interface dan event handling** digunakan dalam pemeriksaan login melalui `LoginForm.Credentials` dan dalam penanganan klik tombol serta perubahan pilihan di JavaFX.

## Status penyimpanan data

Data saat ini disimpan di memori dan dapat diakses saat berpindah peran melalui tombol **Keluar**. Data sesi akan hilang setelah aplikasi ditutup. SQLite direncanakan untuk penyimpanan permanen.
