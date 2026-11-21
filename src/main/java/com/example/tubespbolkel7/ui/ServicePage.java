package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.ServiceLedger;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.example.tubespbolkel7.model.ServiceLedger.CatalogItem;
import static com.example.tubespbolkel7.model.ServiceLedger.Category;
import static com.example.tubespbolkel7.model.ServiceLedger.Order;
import static com.example.tubespbolkel7.model.ServiceLedger.RoomGuest;
import static com.example.tubespbolkel7.model.ServiceLedger.Status;

final class ServicePage {
    private static final Color NAVY = Color.web("#082A4D");
    private static final Color MUTED = Color.web("#607189");
    private static final Color TEAL = Color.web("#006A60");
    private static final Color TEAL_HOVER = Color.web("#00554D");
    private static final Color WASH = Color.web("#F5F7FC");
    private static final Color SOFT = Color.web("#EDF2FD");
    private static final Color SOFT_HOVER = Color.web("#E0E9FC");
    private static final Color EDGE = Color.web("#DEE7F3");
    private static final Color PALE_TEAL = Color.web("#E2F9F4");
    private static final Locale INDONESIAN = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", INDONESIAN);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm 'WIB'");
    private static final int PAGE_SIZE = 7;

    private final HotelOperations app;
    private final ServiceLedger ledger;
    private final boolean catalogOnly;
    private final EnumMap<Category, Button> categoryCards = new EnumMap<>(Category.class);
    private final EnumMap<Category, Label> categorySummary = new EnumMap<>(Category.class);
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final DatePicker date = new DatePicker();
    private final TableView<Order> orders = table("Tidak ada pesanan yang sesuai.");
    private final TableView<CatalogItem> items = table("Tidak ada layanan yang sesuai.");
    private final Label count = label("", 12, MUTED, FontWeight.NORMAL);
    private final Label paginationLabel = label("", 12, MUTED, FontWeight.NORMAL);
    private final Pagination pagination = new Pagination();
    private final VBox miniCatalog = new VBox(8);

    private List<Order> filteredOrders = List.of();
    private Category selectedCategory;

    static Node view(HotelOperations app, HotelRoute route) {
        return new ServicePage(app, route == HotelRoute.SERVICE_CATALOG).build();
    }

    private ServicePage(HotelOperations app, boolean catalogOnly) {
        this.app = app;
        this.ledger = app.services();
        this.catalogOnly = catalogOnly;
    }

    private Node build() {
        String title = catalogOnly ? "Layanan Tambahan" : "Layanan Kamar";
        String description = catalogOnly
                ? "Kelola kategori, tarif, dan ketersediaan layanan tambahan."
                : "Pantau pesanan dan catat layanan ke folio tamu yang sedang menginap.";

        Button create = button(catalogOnly ? "+ Tambah layanan" : "+ Pesanan baru", true,
                catalogOnly ? "add-catalog-item" : "new-service-order");
        create.setOnAction(event -> {
            if (catalogOnly) {
                editItem(null);
            } else {
                newOrder(null);
            }
        });

        HBox header = new HBox(12,
                new VBox(4, label(title, 28, NAVY, FontWeight.BOLD), label(description, 14, MUTED, FontWeight.NORMAL)),
                spacer(), create);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = catalogOnly ? new VBox(catalogTable()) : split(orderTable(), catalogSidebar());
        VBox main = new VBox(20, header, categoryCards(), toolbar(), content);
        main.setPadding(new Insets(24, 26, 28, 26));

        Label brand = label(app.data().settings().hotel().name(), 15, NAVY, FontWeight.BOLD);
        Label role = label(app.role().label(), 12, TEAL, FontWeight.SEMI_BOLD);
        role.setPadding(new Insets(5, 10, 5, 10));
        decorate(role, PALE_TEAL, PALE_TEAL, 20);

        HBox top = new HBox(brand, spacer(), role);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(13, 26, 13, 26));
        top.setMinHeight(64);
        top.setBackground(Background.fill(Color.WHITE));
        top.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));

        VBox page = app.isOwner() ? new VBox(main) : new VBox(top, main);
        page.setBackground(Background.fill(WASH));
        refresh();
        return page;
    }

    private Node categoryCards() {
        HBox row = new HBox(12);
        row.setMinHeight(134);
        row.setPrefHeight(134);
        row.setMaxHeight(134);

        for (Category category : Category.values()) {
            Label heading = label(category.toString(), 16, NAVY, FontWeight.BOLD);
            Label icon = label(category.symbol(), 18, TEAL, FontWeight.BOLD);
            icon.setPadding(new Insets(4, 9, 4, 9));
            decorate(icon, SOFT, SOFT, 8);

            Label caption = label(category.caption(), 12, MUTED, FontWeight.NORMAL);
            Label summary = label("", 12, TEAL, FontWeight.SEMI_BOLD);
            categorySummary.put(category, summary);

            Button card = new Button("", new VBox(7, icon, heading, caption, summary));
            card.setId("service-category-" + category.name().toLowerCase(Locale.ROOT));
            card.setAccessibleText("Filter kategori " + category);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setCursor(Cursor.HAND);
            card.setPadding(new Insets(14));
            card.setMinWidth(0);
            card.setPrefWidth(0);
            card.setMaxWidth(Double.MAX_VALUE);
            card.setPrefHeight(134);
            card.setMaxHeight(Double.MAX_VALUE);
            card.setEffect(new DropShadow(8, 0, 2, Color.web("#082A4D", 0.04)));

            card.hoverProperty().addListener(obs -> paintCategoryCard(category, card));
            card.setOnAction(event -> {
                selectedCategory = (selectedCategory == category) ? null : category;
                refresh();
            });
            HBox.setHgrow(card, Priority.ALWAYS);
            categoryCards.put(category, card);
            row.getChildren().add(card);
        }
        return row;
    }

    private void paintCategoryCard(Category category, Button card) {
        boolean active = (selectedCategory == category);
        Color bg = active ? PALE_TEAL : (card.isHover() ? Color.web("#F8FAFF") : Color.WHITE);
        Color border = active ? TEAL : (card.isHover() ? Color.web("#C7D6EA") : EDGE);
        decorate(card, bg, border, 11);
    }

    private Node toolbar() {
        search.setId("service-search");
        search.setAccessibleText(catalogOnly ? "Cari layanan" : "Cari kamar atau tamu");
        search.setPromptText(catalogOnly ? "Cari nama atau satuan layanan..." : "Cari kamar atau tamu...");
        search.setPrefWidth(320);
        fieldStyle(search);
        search.textProperty().addListener((observable, previous, value) -> refresh());

        filter.setId("service-filter");
        filter.getItems().addAll(catalogOnly
                ? List.of("Semua ketersediaan", "Tersedia", "Tidak tersedia")
                : List.of("Semua status", "Diproses", "Diantar", "Selesai", "Dibatalkan"));
        filter.getSelectionModel().selectFirst();
        filter.setPrefWidth(190);
        filter.setCursor(Cursor.HAND);
        filter.setAccessibleText(catalogOnly ? "Filter ketersediaan" : "Filter status pesanan");
        fieldStyle(filter);
        filter.setOnAction(event -> refresh());

        FlowPane row = new FlowPane(10, 8, search, filter);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        decorate(row, Color.WHITE, EDGE, 11);
        row.setEffect(new DropShadow(8, 0, 2, Color.web("#082A4D", 0.03)));

        if (!catalogOnly) {
            date.setId("service-date");
            date.setPromptText("Semua tanggal");
            date.setEditable(false);
            date.setPrefWidth(176);
            date.setAccessibleText("Tanggal pesanan");
            fieldStyle(date);
            date.setOnAction(event -> refresh());
            row.getChildren().add(date);
        }

        Button reset = button("Reset filter", false, "service-reset-filter");
        reset.setOnAction(event -> {
            selectedCategory = null;
            search.clear();
            filter.getSelectionModel().selectFirst();
            date.setValue(null);
            refresh();
        });
        row.getChildren().add(reset);
        return row;
    }

    private VBox orderTable() {
        orders.setId("service-orders");
        orders.getColumns().setAll(List.of(
                column("WAKTU", order -> DAY.format(order.created()) + "\n" + CLOCK.format(order.created()), 108),
                column("KAMAR", order -> order.roomGuest().room(), 54),
                column("TAMU", order -> order.roomGuest().guest(), 108),
                column("LAYANAN", order -> order.item().name(), 120),
                column("JUMLAH", order -> order.quantity() + " " + order.item().unit(), 70),
                column("SUBTOTAL", order -> money(order.subtotal()), 94),
                statusColumn("STATUS", order -> order.status().toString()),
                actionColumn("Detail", "service-order-detail", this::orderDetail)
        ));

        pagination.setId("service-pagination");
        pagination.setMaxPageIndicatorCount(5);
        pagination.setPageFactory(index -> {
            displayOrderPage(index);
            return new Region();
        });
        pagination.setPrefHeight(38);
        pagination.setMaxHeight(44);

        HBox footer = new HBox(9, paginationLabel, spacer(), pagination);
        footer.setAlignment(Pos.CENTER_LEFT);

        Region headings = new VBox(4, label("Pesanan Layanan", 17, NAVY, FontWeight.BOLD), count);
        VBox card = card(headings, orders, footer);
        card.setMinWidth(0);
        return card;
    }

    private VBox catalogTable() {
        items.setId("service-catalog");
        items.getColumns().setAll(List.of(
                column("LAYANAN", CatalogItem::name, 150),
                column("KATEGORI", item -> item.category().toString(), 110),
                column("SATUAN", CatalogItem::unit, 75),
                column("TARIF", item -> money(item.price()), 90),
                statusColumn("KETERSEDIAAN", item -> item.available() ? "Tersedia" : "Tidak tersedia"),
                actionColumn("Ubah", "edit-service-item", this::editItem),
                actionColumn("Pesan", "order-service-item", this::newOrder)
        ));
        items.setPrefHeight(500);
        return card(new VBox(4, label("Katalog Layanan & Harga", 17, NAVY, FontWeight.BOLD), count), items);
    }

    private VBox catalogSidebar() {
        Button manage = button("Kelola katalog", false, "manage-service-catalog");
        manage.setMaxWidth(Double.MAX_VALUE);
        manage.setOnAction(event -> app.open(HotelRoute.SERVICE_CATALOG));

        VBox card = card(
                new VBox(3, label("Katalog Layanan & Harga", 17, NAVY, FontWeight.BOLD),
                        label("Pilih layanan untuk membuat pesanan.", 12, MUTED, FontWeight.NORMAL)),
                miniCatalog, manage);
        card.setMinWidth(0);
        return card;
    }

    private VBox split(VBox orderCard, VBox catalogCard) {
        VBox container = new VBox(16, orderCard, catalogCard);
        HBox columns = new HBox(16);
        columns.setFillHeight(false);
        HBox.setHgrow(orderCard, Priority.ALWAYS);

        container.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            boolean wide = newWidth.doubleValue() >= 1060;
            if (wide == container.getChildren().contains(columns)) {
                return;
            }
            container.getChildren().clear();
            columns.getChildren().clear();
            if (wide) {
                catalogCard.setPrefWidth(300);
                catalogCard.setMaxWidth(300);
                columns.getChildren().addAll(orderCard, catalogCard);
                container.getChildren().add(columns);
            } else {
                catalogCard.setPrefWidth(Region.USE_COMPUTED_SIZE);
                catalogCard.setMaxWidth(Double.MAX_VALUE);
                container.getChildren().addAll(orderCard, catalogCard);
            }
        });
        return container;
    }

    private void refresh() {
        refreshCategoryCards();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        String selection = filter.getValue();
        boolean unrestricted = filter.getSelectionModel().getSelectedIndex() <= 0;

        if (catalogOnly) {
            List<CatalogItem> filtered = ledger.catalog().stream()
                    .filter(item -> matchesCategory(item.category()))
                    .filter(item -> unrestricted || item.available() == "Tersedia".equals(selection))
                    .filter(item -> contains(query, item.name(), item.unit(), item.category().toString()))
                    .toList();
            items.getItems().setAll(filtered);
            count.setText(filtered.size() + " layanan ditampilkan");
        } else {
            filteredOrders = ledger.orders().stream()
                    .filter(order -> matchesCategory(order.item().category()))
                    .filter(order -> unrestricted || order.status().toString().equals(selection))
                    .filter(order -> date.getValue() == null || date.getValue().equals(order.created().toLocalDate()))
                    .filter(order -> contains(query, order.code(), order.roomGuest().room(),
                            order.roomGuest().guest(), order.item().name()))
                    .toList();
            count.setText(filteredOrders.size() + " transaksi sesuai filter");
            pagination.setPageCount(Math.max(1, (filteredOrders.size() + PAGE_SIZE - 1) / PAGE_SIZE));
            pagination.setCurrentPageIndex(0);
            displayOrderPage(0);
            updateMiniCatalog();
        }
    }

    private void displayOrderPage(int index) {
        int start = Math.min(index * PAGE_SIZE, filteredOrders.size());
        int end = Math.min(start + PAGE_SIZE, filteredOrders.size());
        orders.getItems().setAll(filteredOrders.subList(start, end));
        orders.setPrefHeight(54 + Math.max(3, end - start) * 60);
        paginationLabel.setText(end == 0 ? "0 pesanan"
                : "Menampilkan " + (start + 1) + "–" + end + " dari " + filteredOrders.size());
    }

    private void refreshCategoryCards() {
        categoryCards.forEach((category, card) -> {
            paintCategoryCard(category, card);
            long total = catalogOnly
                    ? ledger.catalog().stream().filter(item -> item.category() == category && item.available()).count()
                    : ledger.orders().stream().filter(order -> order.item().category() == category
                            && order.status() != Status.CANCELLED
                            && order.created().toLocalDate().equals(app.data().today())).count();
            categorySummary.get(category).setText(total + (catalogOnly ? " layanan aktif" : " pesanan hari ini"));
        });
    }

    private void updateMiniCatalog() {
        miniCatalog.getChildren().clear();
        for (CatalogItem item : ledger.catalog()) {
            if (!item.available() || !matchesCategory(item.category())) {
                continue;
            }
            Button entry = button("", false, null);
            Label itemLabel = label(item.name(), 13, NAVY, FontWeight.SEMI_BOLD);
            Label priceLabel = label(money(item.price()) + " / " + item.unit(), 12, TEAL, FontWeight.NORMAL);
            entry.setAccessibleText("Pesan " + item.name() + ", " + priceLabel.getText());
            entry.setGraphic(new VBox(3, itemLabel, priceLabel));
            entry.setWrapText(true);
            entry.setAlignment(Pos.CENTER_LEFT);
            entry.setMaxWidth(Double.MAX_VALUE);
            entry.setOnAction(event -> newOrder(item));
            miniCatalog.getChildren().add(entry);
        }
        if (miniCatalog.getChildren().isEmpty()) {
            miniCatalog.getChildren().add(label("Belum ada layanan yang tersedia.", 12, MUTED, FontWeight.NORMAL));
        }
    }

    private boolean matchesCategory(Category category) {
        return selectedCategory == null || selectedCategory == category;
    }

    private void newOrder(CatalogItem selectedItem) {
        List<RoomGuest> guests = app.data().bookings().stream()
                .filter(booking -> booking.status() == HotelData.Status.IN_HOUSE)
                .map(booking -> new RoomGuest(booking.code(), booking.room().number(), booking.guest()))
                .toList();

        ComboBox<RoomGuest> guest = options("service-order-guest", guests);
        guest.setPromptText("Pilih kamar dan tamu");
        guests.stream().filter(item -> item.reservation().equals(app.selected())).findFirst().ifPresent(guest::setValue);

        ComboBox<CatalogItem> item = options("service-order-item",
                ledger.catalog().stream().filter(CatalogItem::available).toList());
        item.setPromptText("Pilih layanan dari katalog");
        if (selectedItem != null && selectedItem.available()) {
            item.setValue(selectedItem);
        } else if (selectedCategory != null) {
            item.getItems().stream().filter(entry -> entry.category() == selectedCategory).findFirst().ifPresent(item::setValue);
        }

        TextField quantity = new TextField("1");
        quantity.setId("service-order-quantity");
        fieldStyle(quantity);

        TextArea notes = new TextArea();
        notes.setId("service-order-notes");
        notes.setPromptText("Contoh: antar sebelum pukul 17.00.");
        notes.setWrapText(true);
        notes.setPrefRowCount(2);

        Label subtotal = label("Subtotal · Rp 0", 17, TEAL, FontWeight.BOLD);
        Runnable updateTotal = () -> {
            int units = quantity.getText().matches("[0-9]{1,3}") ? Integer.parseInt(quantity.getText()) : 0;
            subtotal.setText("Subtotal · " + money(item.getValue() == null ? 0 : units * item.getValue().price()));
        };
        quantity.textProperty().addListener((observable, previous, value) -> updateTotal.run());
        item.valueProperty().addListener((observable, previous, value) -> updateTotal.run());
        updateTotal.run();

        VBox form = new VBox(12,
                field("Kamar dan tamu menginap", guest),
                field("Layanan", item),
                field("Jumlah", quantity),
                field("Catatan", notes),
                subtotal);

        if (guests.isEmpty()) {
            form.getChildren().addFirst(label("Belum ada tamu menginap. Lakukan check-in terlebih dahulu.",
                    13, MUTED, FontWeight.NORMAL));
        }

        dialog("Pesanan Layanan Baru", form, "Simpan pesanan", () -> {
            int units = parseQuantity(quantity.getText());
            RoomGuest roomGuest = guest.getValue();
            boolean stillInHouse = roomGuest != null && app.data().bookings().stream()
                    .anyMatch(booking -> booking.code().equals(roomGuest.reservation())
                            && booking.status() == HotelData.Status.IN_HOUSE);
            if (!stillInHouse) {
                throw new IllegalArgumentException("Pilih tamu yang masih menginap.");
            }
            ledger.addOrder(LocalDateTime.of(app.data().today(), LocalTime.now().withSecond(0).withNano(0)),
                    roomGuest, item.getValue(), units, notes.getText());
            if (catalogOnly) {
                app.open(HotelRoute.SERVICES);
            } else {
                selectedCategory = null;
                filter.getSelectionModel().selectFirst();
                search.clear();
                date.setValue(null);
                refresh();
            }
        });
    }

    private void orderDetail(Order order) {
        ComboBox<Status> status = options("service-detail-status", order.status().choices());
        status.setValue(order.status());
        boolean stayOpen = app.data().bookings().stream()
                .anyMatch(booking -> booking.code().equals(order.roomGuest().reservation())
                        && booking.status() == HotelData.Status.IN_HOUSE);
        status.setDisable(!stayOpen || order.status().choices().size() == 1);

        VBox form = new VBox(12,
                label(order.roomGuest().toString(), 16, NAVY, FontWeight.BOLD),
                label(order.code() + " · " + DAY.format(order.created()) + " · " + CLOCK.format(order.created()),
                        13, MUTED, FontWeight.NORMAL),
                field("Layanan", label(order.item().name(), 15, NAVY, FontWeight.SEMI_BOLD)),
                label(order.quantity() + " " + order.item().unit() + " × " + money(order.item().price()) + " = "
                        + money(order.subtotal()), 15, TEAL, FontWeight.BOLD),
                field("Catatan", label(order.notes().isBlank() ? "Tidak ada catatan" : order.notes(),
                        13, MUTED, FontWeight.NORMAL)),
                field("Status pesanan", status));

        if (!stayOpen) {
            form.getChildren().add(label("Folio tamu sudah ditutup.", 12, MUTED, FontWeight.NORMAL));
        }

        dialog("Detail Pesanan", form, status.isDisable() ? null : "Simpan status", () -> {
            ledger.updateStatus(order, status.getValue());
            refresh();
        });
    }

    private void editItem(CatalogItem original) {
        TextField name = new TextField(original == null ? "" : original.name());
        name.setId("catalog-name");

        ComboBox<Category> category = options("catalog-category", List.of(Category.values()));
        category.setValue(original == null ? selectedCategory : original.category());
        category.setPromptText("Pilih kategori");

        TextField unit = new TextField(original == null ? "" : original.unit());
        unit.setId("catalog-unit");

        TextField price = new TextField(original == null ? "" : Long.toString(original.price()));
        price.setId("catalog-price");

        CheckBox available = new CheckBox("Tersedia untuk dipesan");
        available.setId("catalog-available");
        available.setCursor(Cursor.HAND);
        available.setSelected(original == null || original.available());

        for (TextField field : List.of(name, unit, price)) {
            fieldStyle(field);
        }

        VBox form = new VBox(12, field("Nama layanan", name), field("Kategori", category),
                field("Satuan", unit), field("Tarif (Rp)", price), available);

        dialog(original == null ? "Tambah Layanan Katalog" : "Ubah Layanan Katalog", form, "Simpan layanan", () -> {
            if (category.getValue() == null) {
                throw new IllegalArgumentException("Pilih kategori layanan.");
            }
            String enteredPrice = price.getText().strip();
            if (!enteredPrice.matches("[0-9]{1,9}|[0-9]{1,3}(\\.[0-9]{3}){1,2}")) {
                throw new IllegalArgumentException("Masukkan tarif dalam rupiah, misalnya 15000.");
            }
            long amount = Long.parseLong(enteredPrice.replace(".", ""));
            ledger.saveItem(original, new CatalogItem(name.getText().strip(), category.getValue(),
                    unit.getText().strip(), amount, available.isSelected()));
            refresh();
        });
    }

    private void dialog(String title, VBox form, String action, Runnable onCommit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (app.owner() != null) {
            dialog.initOwner(app.owner());
        }
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().setPrefWidth(520);

        Label error = label("", 13, Color.web("#A21E29"), FontWeight.SEMI_BOLD);
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        form.getChildren().add(error);
        dialog.getDialogPane().setContent(form);

        if (action == null) {
            dialog.getDialogPane().getButtonTypes().add(new ButtonType("Tutup", ButtonType.CLOSE.getButtonData()));
        } else {
            ButtonType commit = new ButtonType(action, ButtonType.OK.getButtonData());
            dialog.getDialogPane().getButtonTypes().addAll(
                    new ButtonType("Batal", ButtonType.CANCEL.getButtonData()), commit);
            Button accept = (Button) dialog.getDialogPane().lookupButton(commit);
            accept.setId("service-dialog-save");
            accept.addEventFilter(ActionEvent.ACTION, event -> {
                try {
                    onCommit.run();
                } catch (IllegalArgumentException failure) {
                    error.setText(failure.getMessage());
                    event.consume();
                }
            });
        }
        dialog.showAndWait();
    }

    private static int parseQuantity(String text) {
        if (!text.strip().matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("Masukkan jumlah dalam angka.");
        }
        return Integer.parseInt(text.strip());
    }

    private static boolean contains(String query, String... values) {
        return String.join(" ", values).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String money(long amount) {
        return "Rp " + String.format(INDONESIAN, "%,d", amount);
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static Label label(String value, int size, Color color, FontWeight weight) {
        Label label = new Label(value);
        label.setTextFill(color);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setWrapText(true);
        return label;
    }

    private static void decorate(Region node, Color fill, Color border, int radius) {
        CornerRadii corners = new CornerRadii(radius);
        node.setBackground(new Background(new BackgroundFill(fill, corners, Insets.EMPTY)));
        node.setBorder(new Border(new BorderStroke(border, BorderStrokeStyle.SOLID, corners, BorderStroke.THIN)));
    }

    private static Button button(String text, boolean primary, String id) {
        Button button = new Button(text);
        button.setId(id);
        button.setMinHeight(34);
        button.setPadding(new Insets(7, 11, 7, 11));
        button.setCursor(Cursor.HAND);
        button.setTextFill(primary ? Color.WHITE : NAVY);
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        Color baseBg = primary ? TEAL : SOFT;
        Color hoverBg = primary ? TEAL_HOVER : SOFT_HOVER;
        decorate(button, baseBg, baseBg, 8);
        button.hoverProperty().addListener((obs, wasHover, isHover) -> {
            Color bg = isHover ? hoverBg : baseBg;
            decorate(button, bg, bg, 8);
        });
        return button;
    }

    private static VBox card(Node... content) {
        VBox node = new VBox(12, content);
        node.setPadding(new Insets(16));
        decorate(node, Color.WHITE, EDGE, 11);
        node.setEffect(new DropShadow(10, 0, 2, Color.web("#082A4D", 0.04)));
        return node;
    }

    private static VBox field(String name, Node control) {
        Label label = label(name, 13, NAVY, FontWeight.SEMI_BOLD);
        label.setLabelFor(control);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(4, label, control);
    }

    private static void fieldStyle(Region field) {
        field.setMinHeight(38);
        decorate(field, WASH, EDGE, 8);
    }

    private static <T> ComboBox<T> options(String id, List<T> values) {
        ComboBox<T> combo = new ComboBox<>(FXCollections.observableArrayList(values));
        combo.setId(id);
        combo.setCursor(Cursor.HAND);
        fieldStyle(combo);
        return combo;
    }

    private static <T> TableView<T> table(String empty) {
        TableView<T> table = new TableView<>() {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                for (String selector : List.of(".column-header-background", ".column-header", ".filler")) {
                    lookupAll(selector).forEach(node -> {
                        if (node instanceof Region region && region.getProperties().putIfAbsent("hotel-header", true) == null) {
                            region.setBackground(Background.fill(SOFT));
                            Node label = region.lookup(".label");
                            if (label instanceof Label heading) {
                                heading.setTextFill(NAVY);
                                heading.setAlignment(Pos.CENTER_LEFT);
                                heading.setPadding(new Insets(0, 6, 0, 6));
                            }
                        }
                    });
                }
            }
        };
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(60);
        table.setPlaceholder(label(empty, 13, MUTED, FontWeight.NORMAL));
        table.setMinWidth(0);
        decorate(table, Color.WHITE, EDGE, 8);
        return table;
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setGraphic(empty || text == null ? null : label(text, 13, NAVY, FontWeight.NORMAL));
            }
        });
        return column;
    }

    private static <T> TableColumn<T, String> statusColumn(String title, Function<T, String> value) {
        TableColumn<T, String> column = column(title, value, 108);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setGraphic(null);
                    return;
                }
                boolean closed = "Dibatalkan".equals(text) || "Tidak tersedia".equals(text);
                boolean done = "Selesai".equals(text) || "Tersedia".equals(text);
                Color background = closed ? Color.web("#FBE9EA") : done ? PALE_TEAL : SOFT;
                Label badge = label(text, 11, closed ? Color.web("#A21E29") : done ? TEAL : NAVY, FontWeight.SEMI_BOLD);
                badge.setPadding(new Insets(4, 7, 4, 7));
                decorate(badge, background, background, 16);
                setGraphic(badge);
            }
        });
        return column;
    }

    private static <T> TableColumn<T, T> actionColumn(String text, String id, Consumer<T> action) {
        TableColumn<T, T> column = new TableColumn<>("Pesan".equals(text) ? "PESAN" : "AKSI");
        column.setPrefWidth(77);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button button = button(text, false, id);

            {
                button.setOnAction(event -> action.accept(getItem()));
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                button.setDisable(item instanceof CatalogItem entry && "Pesan".equals(text) && !entry.available());
                setGraphic(empty || item == null ? null : button);
            }
        });
        return column;
    }
}