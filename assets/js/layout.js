/* Layout bersama (KOM): menyisipkan sidebar, navbar, footer ke halaman.
   Pakai: <div id="page">isi halaman</div>
          <script src="../assets/js/layout.js" data-root="../"></script>
   data-root: "../" untuk pages/x.html, "../../" untuk pages/modul/x.html */
(function () {
  var root = document.currentScript.dataset.root || "../";
  var MENU = [
    { t: "Utama", i: [["Dashboard", "pages/dashboard.html"]] },
    { t: "Manajemen kamar", i: [
      ["Tipe kamar", "pages/kamar/tipe-kamar.html"],
      ["Data kamar", "pages/kamar/data-kamar.html"],
      ["Grid status kamar", "pages/kamar/grid-status.html"],
      ["Housekeeping", "pages/kamar/housekeeping.html"]] },
    { t: "Reservasi", i: [
      ["Daftar reservasi", "pages/reservasi/daftar-reservasi.html"],
      ["Reservasi baru", "pages/reservasi/reservasi-baru.html"],
      ["Kalender ketersediaan", "pages/reservasi/kalender.html"]] },
    { t: "Check-in/out & tamu", i: [
      ["Check-in", "pages/tamu/checkin.html"],
      ["Check-out", "pages/tamu/checkout.html"],
      ["Tamu in-house", "pages/tamu/tamu-inhouse.html"],
      ["Daftar tamu", "pages/tamu/daftar-tamu.html"]] },
    { t: "Pembayaran & layanan", i: [
      ["Tagihan tamu", "pages/pembayaran/tagihan.html"],
      ["Form pembayaran", "pages/pembayaran/pembayaran.html"],
      ["Riwayat transaksi", "pages/pembayaran/riwayat-transaksi.html"],
      ["Layanan tambahan", "pages/pembayaran/layanan-tambahan.html"]] },
    { t: "Laporan & pengaturan", i: [
      ["Laporan okupansi & tamu", "pages/laporan/laporan-okupansi.html"],
      ["Laporan pendapatan", "pages/laporan/laporan-pendapatan.html"],
      ["Pengguna & role", "pages/laporan/pengguna.html"],
      ["Pengaturan hotel", "pages/laporan/pengaturan.html"],
      ["Profil pengguna", "pages/laporan/profil.html"]] }
  ];

  var here = location.pathname.split("/").pop();
  var nav = MENU.map(function (g) {
    return '<div class="nav-group"><div class="nav-title">' + g.t + "</div>" +
      g.i.map(function (m) {
        var active = m[1].split("/").pop() === here ? " is-active" : "";
        return '<a class="nav-link' + active + '" href="' + root + m[1] + '">' + m[0] + "</a>";
      }).join("") + "</div>";
  }).join("");

  var page = document.getElementById("page");
  var side = document.createElement("aside");
  side.className = "app-sidebar";
  side.innerHTML =
    '<div class="app-brand"><div class="brand-mark">H</div><div><div class="brand-name">Hotel Manager</div><div class="brand-sub">Sistem manajemen hotel</div></div></div>' +
    '<nav class="app-nav">' + nav + "</nav>";

  var back = document.createElement("div");
  back.className = "app-backdrop";

  var main = document.createElement("div");
  main.className = "app-main";
  main.innerHTML =
    '<header class="app-navbar">' +
      '<div class="nav-left"><button class="nav-toggle" aria-label="Buka menu"><svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 4h14M2 9h14M2 14h14"/></svg></button>' +
      '<span class="nav-date"></span></div>' +
      '<div class="nav-right"><div class="user-avatar">AD</div>' +
      '<div class="user-meta"><div class="user-name">Admin</div><div class="user-role">Administrator</div></div>' +
      '<a class="btn btn-outline btn-sm" href="' + root + 'pages/login.html">Keluar</a></div>' +
    "</header>" +
    '<div class="app-content"></div>' +
    '<footer class="app-footer">&copy; Kelompok 7 - Sistem Manajemen Hotel</footer>';

  main.querySelector(".nav-date").textContent = new Date().toLocaleDateString("id-ID",
    { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  main.querySelector(".app-content").appendChild(page);

  document.body.insertBefore(main, document.body.firstChild);
  document.body.insertBefore(back, document.body.firstChild);
  document.body.insertBefore(side, document.body.firstChild);

  function toggle(open) {
    side.classList.toggle("is-open", open);
    back.classList.toggle("is-open", open);
  }
  main.querySelector(".nav-toggle").addEventListener("click", function () { toggle(true); });
  back.addEventListener("click", function () { toggle(false); });
})();
