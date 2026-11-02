/* Modul Manajemen Kamar (Anggota 2): data contoh, pencarian, filter, modal, ubah status.
   Data disimpan di localStorage agar perubahan terlihat di kelima halaman.
   Saat backend siap, ganti fungsi load() dan save() dengan pemanggilan API. */
(function () {
  var KEY = "hotel_kamar_v1";
  var STATUS = { available: "Tersedia", occupied: "Terisi", reserved: "Dipesan", cleaning: "Dibersihkan", maintenance: "Maintenance" };
  var STAFF = ["Sari", "Joko", "Rina", "Dedi"];

  /* ---------- Data ---------- */
  function seed() {
    var tipe = [
      { id: "standard", nama: "Standard", harga: 350000, kapasitas: 2, fasilitas: "AC, TV, WiFi, Kamar mandi dalam" },
      { id: "deluxe", nama: "Deluxe", harga: 550000, kapasitas: 3, fasilitas: "AC, Smart TV, WiFi, Water heater, Sarapan" },
      { id: "suite", nama: "Suite", harga: 950000, kapasitas: 4, fasilitas: "AC, Smart TV, WiFi, Bathtub, Ruang tamu, Sarapan" }
    ];
    var plan = [
      [1, "standard", ["occupied", "available", "occupied", "reserved", "available", "cleaning", "occupied", "maintenance"]],
      [2, "deluxe", ["occupied", "occupied", "available", "reserved", "cleaning", "occupied", "available", "available"]],
      [3, "suite", ["reserved", "occupied", "available", "occupied", "cleaning", "available"]]
    ];
    var kamar = [];
    plan.forEach(function (p) {
      p[2].forEach(function (st, i) {
        var r = { no: String(p[0] * 100 + i + 1), lantai: p[0], tipe: p[1], status: st, catatan: "", petugas: "" };
        if (p[0] === 3 && i >= 4) r.tipe = "deluxe";
        if (st === "cleaning") { r.petugas = STAFF[(i + p[0]) % STAFF.length]; r.catatan = "Pembersihan setelah check-out"; }
        if (st === "maintenance") { r.catatan = "AC tidak dingin"; }
        kamar.push(r);
      });
    });
    return { tipe: tipe, kamar: kamar, selesai: { tanggal: today(), n: 0 } };
  }
  function today() { return new Date().toISOString().slice(0, 10); }
  function load() {
    try {
      var d = JSON.parse(localStorage.getItem(KEY));
      if (d && d.tipe && d.kamar) { if (!d.selesai || d.selesai.tanggal !== today()) d.selesai = { tanggal: today(), n: 0 }; return d; }
    } catch (e) {}
    var s = seed(); save(s); return s;
  }
  function save(d) { try { localStorage.setItem(KEY, JSON.stringify(d)); } catch (e) {} }
  var db = load();

  /* ---------- Helper ---------- */
  function $(s, c) { return (c || document).querySelector(s); }
  function $$(s, c) { return Array.prototype.slice.call((c || document).querySelectorAll(s)); }
  function esc(t) { return String(t == null ? "" : t).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function rupiah(n) { return "Rp " + Number(n || 0).toLocaleString("id-ID"); }
  function tipeById(id) { return db.tipe.filter(function (t) { return t.id === id; })[0]; }
  function tipeNama(id) { var t = tipeById(id); return t ? t.nama : "-"; }
  function kamarByNo(no) { return db.kamar.filter(function (k) { return k.no === no; })[0]; }
  function badge(st) { return '<span class="badge badge-' + st + '">' + STATUS[st] + "</span>"; }
  function tipeBadge(id) { return id === "suite" ? '<span class="badge badge-gold">Suite</span>' : esc(tipeNama(id)); }
  function sortKamar(a, b) { return Number(a.no) - Number(b.no); }
  function param(n) { return new URLSearchParams(location.search).get(n); }
  function opts(list, sel) { return list.map(function (o) { return '<option value="' + esc(o[0]) + '"' + (o[0] === sel ? " selected" : "") + ">" + esc(o[1]) + "</option>"; }).join(""); }
  function tipeOpts(sel, all) { return (all ? '<option value="">Semua tipe</option>' : "") + opts(db.tipe.map(function (t) { return [t.id, t.nama]; }), sel); }
  function statusOpts(sel, all) { return (all ? '<option value="">Semua status</option>' : "") + opts(Object.keys(STATUS).map(function (k) { return [k, STATUS[k]]; }), sel); }
  function staffOpts(sel) { return '<option value="">Belum ditugaskan</option>' + opts(STAFF.map(function (s) { return [s, s]; }), sel); }

  function toast(msg, err) {
    var t = document.createElement("div");
    t.className = "toast" + (err ? " is-error" : "");
    t.setAttribute("role", "status");
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function () { t.remove(); }, 3200);
  }
  function openModal(id) { $("#" + id).classList.add("is-open"); }
  function closeModal(id) { $("#" + id).classList.remove("is-open"); }
  function bindModals() {
    $$(".modal-overlay").forEach(function (m) {
      m.addEventListener("click", function (e) { if (e.target === m) m.classList.remove("is-open"); });
      $$("[data-close]", m).forEach(function (b) { b.addEventListener("click", function () { m.classList.remove("is-open"); }); });
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") $$(".modal-overlay.is-open").forEach(function (m) { m.classList.remove("is-open"); });
    });
  }
  function setInvalid(el, bad) {
    el.classList.toggle("is-invalid", bad);
    var err = el.parentNode.querySelector(".form-error");
    if (err) err.classList.toggle("hidden", !bad);
  }
  function flash() {
    var f = param("saved");
    if (f) {
      toast(f === "tambah" ? "Kamar berhasil ditambahkan." : "Perubahan kamar berhasil disimpan.");
      history.replaceState(null, "", location.pathname);
    }
  }
  function bindReset() {
    var b = $("#reset-data");
    if (b) b.addEventListener("click", function () {
      if (confirm("Kembalikan semua data kamar ke data contoh?")) { localStorage.removeItem(KEY); location.reload(); }
    });
  }

  /* ---------- P04 Tipe Kamar ---------- */
  function pageTipe() {
    var editing = null, deleting = null;
    var body = $("#tipe-body");

    function render() {
      body.innerHTML = db.tipe.map(function (t) {
        var n = db.kamar.filter(function (k) { return k.tipe === t.id; }).length;
        var chips = t.fasilitas.split(",").map(function (f) { return f.trim(); }).filter(Boolean)
          .map(function (f) { return '<span class="chip">' + esc(f) + "</span>"; }).join("");
        return "<tr><td>" + (t.id === "suite" ? '<span class="badge badge-gold">' + esc(t.nama) + "</span>" : '<span class="cell-strong">' + esc(t.nama) + "</span>") + "</td>" +
          '<td><span class="price">' + rupiah(t.harga) + " <small>/ malam</small></span></td>" +
          "<td>" + t.kapasitas + " tamu</td>" +
          '<td><div class="chips">' + chips + "</div></td>" +
          "<td>" + n + " kamar</td>" +
          '<td><div class="cell-actions"><button class="btn btn-outline btn-sm" data-edit="' + esc(t.id) + '">Ubah</button>' +
          '<button class="btn btn-outline btn-sm" data-del="' + esc(t.id) + '">Hapus</button></div></td></tr>';
      }).join("");
      $("#tipe-empty").classList.toggle("hidden", db.tipe.length > 0);
      $("#tipe-count").textContent = db.tipe.length + " tipe kamar";
    }

    function openForm(t) {
      editing = t ? t.id : null;
      $("#tipe-title").textContent = t ? "Ubah tipe kamar" : "Tambah tipe kamar";
      $("#t-nama").value = t ? t.nama : "";
      $("#t-harga").value = t ? t.harga : "";
      $("#t-kap").value = t ? t.kapasitas : "";
      $("#t-fas").value = t ? t.fasilitas : "";
      $$("#form-tipe .form-control").forEach(function (el) { setInvalid(el, false); });
      openModal("modal-tipe");
      $("#t-nama").focus();
    }

    $("#btn-tambah-tipe").addEventListener("click", function () { openForm(null); });
    body.addEventListener("click", function (e) {
      var ed = e.target.closest("[data-edit]"), dl = e.target.closest("[data-del]");
      if (ed) openForm(tipeById(ed.dataset.edit));
      if (dl) {
        deleting = tipeById(dl.dataset.del);
        var n = db.kamar.filter(function (k) { return k.tipe === deleting.id; }).length;
        $("#del-name").textContent = deleting.nama;
        $("#del-block").classList.toggle("hidden", n === 0);
        $("#del-block-n").textContent = n;
        $("#del-confirm").disabled = n > 0;
        openModal("modal-hapus");
      }
    });

    $("#form-tipe").addEventListener("submit", function (e) {
      e.preventDefault();
      var nama = $("#t-nama"), harga = $("#t-harga"), kap = $("#t-kap"), fas = $("#t-fas");
      var dup = db.tipe.some(function (t) { return t.nama.toLowerCase() === nama.value.trim().toLowerCase() && t.id !== editing; });
      var chk = [[nama, nama.value.trim() !== "" && !dup], [harga, Number(harga.value) > 0], [kap, Number(kap.value) >= 1], [fas, fas.value.trim() !== ""]];
      $("#t-nama-err").textContent = dup ? "Nama tipe sudah dipakai." : "Nama tipe wajib diisi.";
      var ok = true;
      chk.forEach(function (c) { setInvalid(c[0], !c[1]); if (!c[1]) ok = false; });
      if (!ok) return;
      var data = { nama: nama.value.trim(), harga: Number(harga.value), kapasitas: Number(kap.value), fasilitas: fas.value.trim() };
      if (editing) { Object.assign(tipeById(editing), data); }
      else {
        var id = data.nama.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "tipe" + Date.now();
        data.id = id; db.tipe.push(data);
      }
      save(db); render(); closeModal("modal-tipe");
      toast(editing ? "Tipe kamar diperbarui." : "Tipe kamar ditambahkan.");
    });

    $("#del-confirm").addEventListener("click", function () {
      db.tipe = db.tipe.filter(function (t) { return t.id !== deleting.id; });
      save(db); render(); closeModal("modal-hapus"); toast("Tipe kamar dihapus.");
    });
    render();
  }

  /* ---------- P05 Data Kamar ---------- */
  function pageData() {
    var body = $("#kamar-body"), q = $("#f-cari"), fs = $("#f-status"), ft = $("#f-tipe"), deleting = null;
    fs.innerHTML = statusOpts("", true);
    ft.innerHTML = tipeOpts("", true);

    function render() {
      var kw = q.value.trim().toLowerCase();
      var list = db.kamar.filter(function (k) {
        return (!fs.value || k.status === fs.value) && (!ft.value || k.tipe === ft.value) &&
          (!kw || (k.no + " " + tipeNama(k.tipe) + " " + k.catatan).toLowerCase().indexOf(kw) > -1);
      }).sort(sortKamar);
      body.innerHTML = list.map(function (k) {
        return '<tr><td class="cell-strong">' + esc(k.no) + "</td><td>Lantai " + k.lantai + "</td><td>" + tipeBadge(k.tipe) + "</td><td>" + badge(k.status) + "</td>" +
          '<td class="text-muted">' + (esc(k.catatan) || "-") + "</td>" +
          '<td><div class="cell-actions"><a class="btn btn-outline btn-sm" href="form-kamar.html?no=' + encodeURIComponent(k.no) + '">Ubah</a>' +
          '<button class="btn btn-outline btn-sm" data-del="' + esc(k.no) + '">Hapus</button></div></td></tr>';
      }).join("");
      $("#kamar-empty").classList.toggle("hidden", list.length > 0);
      $("#kamar-table").classList.toggle("hidden", list.length === 0);
      $("#kamar-count").textContent = "Menampilkan " + list.length + " dari " + db.kamar.length + " kamar";
    }
    [q, fs, ft].forEach(function (el) { el.addEventListener("input", render); });
    $("#f-reset").addEventListener("click", function () { q.value = ""; fs.value = ""; ft.value = ""; render(); });

    body.addEventListener("click", function (e) {
      var dl = e.target.closest("[data-del]");
      if (!dl) return;
      deleting = kamarByNo(dl.dataset.del);
      $("#del-name").textContent = deleting.no;
      $("#del-warn").classList.toggle("hidden", deleting.status !== "occupied" && deleting.status !== "reserved");
      openModal("modal-hapus");
    });
    $("#del-confirm").addEventListener("click", function () {
      db.kamar = db.kamar.filter(function (k) { return k.no !== deleting.no; });
      save(db); render(); closeModal("modal-hapus"); toast("Kamar " + deleting.no + " dihapus.");
    });
    flash(); bindReset(); render();
  }

  /* ---------- P06 Form Tambah/Ubah Kamar ---------- */
  function pageForm() {
    var no = param("no"), cur = no ? kamarByNo(no) : null;
    var f = { no: $("#k-no"), lantai: $("#k-lantai"), tipe: $("#k-tipe"), status: $("#k-status"), cat: $("#k-catatan") };
    f.tipe.innerHTML = tipeOpts(cur ? cur.tipe : "");
    f.status.innerHTML = statusOpts(cur ? cur.status : "available");

    if (cur) {
      $("#form-title").textContent = "Ubah kamar " + cur.no;
      $("#form-sub").textContent = "Perbarui data kamar. Nomor kamar tidak dapat diubah.";
      f.no.value = cur.no; f.no.disabled = true;
      f.lantai.value = cur.lantai; f.cat.value = cur.catatan;
      document.title = "Ubah kamar | Sistem Manajemen Hotel";
      $("#btn-simpan").textContent = "Simpan perubahan";
    } else if (no) {
      $("#form-notfound").classList.remove("hidden");
      $("#form-kamar").classList.add("hidden");
    }

    // aktifkan menu "Data kamar" di sidebar (halaman form tidak punya menu sendiri)
    var link = $('.nav-link[href$="data-kamar.html"]');
    if (link) link.classList.add("is-active");

    // lantai otomatis dari 3 digit nomor kamar saat tambah baru
    f.no.addEventListener("input", function () {
      if (!cur && /^\d{3,4}$/.test(f.no.value)) f.lantai.value = f.no.value.slice(0, f.no.value.length - 2);
      preview();
    });

    function preview() {
      var st = f.status.value || "available", t = tipeById(f.tipe.value);
      $("#pv-tile").className = "room-tile room-" + st;
      $("#pv-no").textContent = f.no.value.trim() || "---";
      $("#pv-type").textContent = t ? t.nama : "-";
      $("#pv-status").textContent = STATUS[st];
      $("#pv-harga").textContent = t ? rupiah(t.harga) : "-";
      $("#pv-kap").textContent = t ? t.kapasitas + " tamu" : "-";
      $("#pv-lantai").textContent = f.lantai.value ? "Lantai " + f.lantai.value : "-";
    }
    [f.lantai, f.tipe, f.status].forEach(function (el) { el.addEventListener("input", preview); });
    preview();

    $("#form-kamar").addEventListener("submit", function (e) {
      e.preventDefault();
      var nomor = f.no.value.trim();
      var dup = !cur && !!kamarByNo(nomor);
      $("#k-no-err").textContent = dup ? "Nomor kamar sudah terdaftar." : "Nomor kamar wajib diisi (angka).";
      var chk = [[f.no, /^\d{1,5}$/.test(nomor) && !dup], [f.lantai, Number(f.lantai.value) >= 1], [f.tipe, f.tipe.value !== ""]];
      var ok = true;
      chk.forEach(function (c) { setInvalid(c[0], !c[1]); if (!c[1]) ok = false; });
      if (!ok) { var first = $(".is-invalid"); if (first) first.focus(); return; }
      var data = { no: nomor, lantai: Number(f.lantai.value), tipe: f.tipe.value, status: f.status.value, catatan: f.cat.value.trim() };
      if (cur) {
        if (data.status !== "cleaning") cur.petugas = "";
        Object.assign(cur, data);
      } else { data.petugas = ""; db.kamar.push(data); }
      save(db);
      location.href = "data-kamar.html?saved=" + (cur ? "ubah" : "tambah");
    });
  }

  /* ---------- P07 Grid Status Kamar ---------- */
  function pageGrid() {
    var fl = $("#f-lantai"), ft = $("#f-tipe"), activeStatus = "", current = null;
    var floors = db.kamar.map(function (k) { return k.lantai; }).filter(function (v, i, a) { return a.indexOf(v) === i; }).sort();
    fl.innerHTML = '<option value="">Semua lantai</option>' + floors.map(function (n) { return '<option value="' + n + '">Lantai ' + n + "</option>"; }).join("");
    ft.innerHTML = tipeOpts("", true);
    $("#m-status").innerHTML = statusOpts("");
    $("#m-petugas").innerHTML = staffOpts("");

    function base() {
      return db.kamar.filter(function (k) { return (!fl.value || String(k.lantai) === fl.value) && (!ft.value || k.tipe === ft.value); });
    }
    function render() {
      var b = base();
      $("#legend").innerHTML = '<button class="legend-item' + (activeStatus ? "" : " is-active") + '" data-st="">Semua <b>' + b.length + "</b></button>" +
        Object.keys(STATUS).map(function (s) {
          var n = b.filter(function (k) { return k.status === s; }).length;
          return '<button class="legend-item' + (activeStatus === s ? " is-active" : "") + '" data-st="' + s + '"><i class="bg-' + s + '"></i>' + STATUS[s] + " <b>" + n + "</b></button>";
        }).join("");
      var list = b.filter(function (k) { return !activeStatus || k.status === activeStatus; }).sort(sortKamar);
      var byFloor = {};
      list.forEach(function (k) { (byFloor[k.lantai] = byFloor[k.lantai] || []).push(k); });
      var html = Object.keys(byFloor).sort().map(function (n) {
        return '<section class="floor"><div class="floor-head"><h3>Lantai ' + n + "</h3><span>" + byFloor[n].length + ' kamar</span></div><div class="grid grid-rooms">' +
          byFloor[n].map(function (k) {
            return '<div class="room-tile room-' + k.status + '" role="button" tabindex="0" data-no="' + esc(k.no) + '" aria-label="Kamar ' + esc(k.no) + ", " + STATUS[k.status] + '">' +
              '<div class="room-no">' + esc(k.no) + '</div><div class="room-type">' + esc(tipeNama(k.tipe)) + '</div><div class="room-status">' + STATUS[k.status] + "</div>" +
              (k.status === "cleaning" && k.petugas ? '<div class="room-staff">Petugas: ' + esc(k.petugas) + "</div>" : "") + "</div>";
          }).join("") + "</div></section>";
      }).join("");
      $("#grid").innerHTML = html;
      $("#grid-empty").classList.toggle("hidden", list.length > 0);
    }

    $("#legend").addEventListener("click", function (e) {
      var b = e.target.closest("[data-st]"); if (!b) return;
      activeStatus = b.dataset.st; render();
    });
    [fl, ft].forEach(function (el) { el.addEventListener("input", render); });

    function open(no) {
      current = kamarByNo(no);
      $("#m-title").textContent = "Kamar " + current.no;
      var t = tipeById(current.tipe);
      $("#m-detail").innerHTML = "<dt>Tipe</dt><dd>" + esc(tipeNama(current.tipe)) + "</dd><dt>Lantai</dt><dd>" + current.lantai + "</dd><dt>Harga</dt><dd>" + (t ? rupiah(t.harga) + " / malam" : "-") + "</dd><dt>Status</dt><dd>" + badge(current.status) + "</dd><dt>Catatan</dt><dd>" + (esc(current.catatan) || "-") + "</dd>";
      $("#m-status").value = current.status;
      $("#m-petugas").value = current.petugas || "";
      $("#m-edit").href = "form-kamar.html?no=" + encodeURIComponent(current.no);
      togglePetugas();
      openModal("modal-kamar");
    }
    function togglePetugas() { $("#m-petugas-wrap").classList.toggle("hidden", $("#m-status").value !== "cleaning"); }
    $("#m-status").addEventListener("input", togglePetugas);
    $("#grid").addEventListener("click", function (e) { var t = e.target.closest("[data-no]"); if (t) open(t.dataset.no); });
    $("#grid").addEventListener("keydown", function (e) {
      var t = e.target.closest("[data-no]");
      if (t && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); open(t.dataset.no); }
    });
    $("#m-save").addEventListener("click", function () {
      current.status = $("#m-status").value;
      current.petugas = current.status === "cleaning" ? $("#m-petugas").value : "";
      save(db); render(); closeModal("modal-kamar");
      toast("Kamar " + current.no + " sekarang " + STATUS[current.status].toLowerCase() + ".");
    });
    render();
  }

  /* ---------- P08 Housekeeping ---------- */
  function pageHk() {
    var filter = "all", body = $("#hk-body");
    $("#a-jenis").innerHTML = opts([["cleaning", "Pembersihan"], ["maintenance", "Perbaikan (maintenance)"]], "cleaning");
    $("#a-petugas").innerHTML = staffOpts("");

    function tasks() { return db.kamar.filter(function (k) { return k.status === "cleaning" || k.status === "maintenance"; }).sort(sortKamar); }
    function render() {
      var all = tasks(), nc = all.filter(function (k) { return k.status === "cleaning"; }).length, nm = all.length - nc;
      $("#s-clean").textContent = nc; $("#s-maint").textContent = nm; $("#s-done").textContent = db.selesai.n;
      $$("#hk-filter button").forEach(function (b) { b.classList.toggle("is-active", b.dataset.f === filter); });
      $("#n-all").textContent = all.length; $("#n-cleaning").textContent = nc; $("#n-maintenance").textContent = nm;
      var list = all.filter(function (k) { return filter === "all" || k.status === filter; });
      body.innerHTML = list.map(function (k) {
        return '<tr><td class="cell-strong">' + esc(k.no) + '<div class="text-sm text-muted" style="font-weight:400">Lantai ' + k.lantai + " &middot; " + esc(tipeNama(k.tipe)) + "</div></td>" +
          "<td>" + badge(k.status) + "</td>" +
          '<td class="text-muted">' + (esc(k.catatan) || "-") + "</td>" +
          '<td><select class="form-control" data-staff="' + esc(k.no) + '" aria-label="Petugas kamar ' + esc(k.no) + '">' + staffOpts(k.petugas) + "</select></td>" +
          '<td><div class="cell-actions"><button class="btn btn-primary btn-sm" data-done="' + esc(k.no) + '">' + (k.status === "cleaning" ? "Selesai dibersihkan" : "Selesai diperbaiki") + "</button></div></td></tr>";
      }).join("");
      $("#hk-empty").classList.toggle("hidden", list.length > 0);
      $("#hk-table").classList.toggle("hidden", list.length === 0);
    }

    $("#hk-filter").addEventListener("click", function (e) {
      var b = e.target.closest("[data-f]"); if (!b) return; filter = b.dataset.f; render();
    });
    body.addEventListener("input", function (e) {
      var s = e.target.closest("[data-staff]"); if (!s) return;
      kamarByNo(s.dataset.staff).petugas = s.value; save(db);
      toast(s.value ? "Petugas " + s.value + " ditugaskan ke kamar " + s.dataset.staff + "." : "Petugas dikosongkan.");
    });
    body.addEventListener("click", function (e) {
      var d = e.target.closest("[data-done]"); if (!d) return;
      var k = kamarByNo(d.dataset.done);
      k.status = "available"; k.petugas = ""; k.catatan = "";
      db.selesai.n++; save(db); render();
      toast("Kamar " + k.no + " kembali tersedia.");
    });

    // tambah tugas baru
    function fillRooms() {
      var avail = db.kamar.filter(function (k) { return k.status !== "cleaning" && k.status !== "maintenance"; }).sort(sortKamar);
      $("#a-kamar").innerHTML = '<option value="">Pilih kamar</option>' + opts(avail.map(function (k) { return [k.no, k.no + " - " + tipeNama(k.tipe) + " (" + STATUS[k.status] + ")"]; }), "");
    }
    $("#btn-tugas").addEventListener("click", function () {
      fillRooms(); $("#a-catatan").value = ""; setInvalid($("#a-kamar"), false); openModal("modal-tugas");
    });
    $("#form-tugas").addEventListener("submit", function (e) {
      e.preventDefault();
      var sel = $("#a-kamar");
      if (!sel.value) { setInvalid(sel, true); return; }
      var k = kamarByNo(sel.value);
      k.status = $("#a-jenis").value; k.petugas = $("#a-petugas").value; k.catatan = $("#a-catatan").value.trim();
      save(db); render(); closeModal("modal-tugas");
      toast("Tugas untuk kamar " + k.no + " ditambahkan.");
    });
    render();
  }

  /* ---------- Mulai ---------- */
  bindModals();
  var page = document.body.dataset.page;
  if (page === "tipe") pageTipe();
  else if (page === "data") pageData();
  else if (page === "form") pageForm();
  else if (page === "grid") pageGrid();
  else if (page === "housekeeping") pageHk();
})();
