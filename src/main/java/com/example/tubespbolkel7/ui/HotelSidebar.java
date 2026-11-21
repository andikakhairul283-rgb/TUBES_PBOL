package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.Role;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class HotelSidebar extends VBox {
    private static final Color NAVY = Color.web("#1E3A5F");
    private static final Color MUTED = Color.web("#A2B8D3");
    private static final Color TEXT = Color.web("#E1EAF6");
    private static final Color ACTIVE = Color.web("#006A60");
    private static final Color HOVER_TINT = Color.web("#FFFFFF", 0.10);
    private static final Color LOGOUT_HOVER_TINT = Color.web("#EF4444", 0.22);
    private static final Color BRAND_GOLD = Color.web("#FFDEA0");
    private static final Color BRAND_INK = Color.web("#513114");
    private static final CornerRadii CORNERS = new CornerRadii(9);

    private final Map<HotelRoute, Button> links = new EnumMap<>(HotelRoute.class);
    private VBox roomLinks;
    private Button roomMenu;

    HotelSidebar(Role role, Consumer<HotelRoute> navigate, Runnable logout) {
        setId("hotel-sidebar");
        setBackground(Background.fill(NAVY));
        setMinWidth(0);
        setBorder(new Border(new BorderStroke(Color.web("#0F172A", 0.18), BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(0, 1, 0, 0))));

        getChildren().add(brand(role));

        VBox menu = buildMenu(role, navigate);
        ScrollPane navigation = new ScrollPane(menu);
        navigation.setFitToWidth(true);
        navigation.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navigation.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navigation.setBackground(Background.EMPTY);
        navigation.setBorder(Border.EMPTY);
        menu.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> navigation.getViewportBounds().getHeight(), navigation.viewportBoundsProperty()));
        VBox.setVgrow(navigation, Priority.ALWAYS);
        getChildren().add(navigation);

        getChildren().add(buildFooter(logout));
    }

    private VBox buildMenu(Role role, Consumer<HotelRoute> navigate) {
        VBox menu = new VBox(14);
        menu.setPadding(new Insets(8, 10, 14, 10));
        menu.setBackground(Background.fill(NAVY));

        for (HotelRoute.Section section : HotelRoute.menu(role)) {
            VBox sectionLinks = new VBox(4);
            for (HotelRoute.Link link : section.links()) {
                Button button = item(link.label(), link.route().icon, section.collapsible(), false);
                button.setId("nav-" + link.route().name().toLowerCase(Locale.ROOT).replace('_', '-'));
                button.setOnAction(event -> navigate.accept(link.route()));
                links.put(link.route(), button);
                sectionLinks.getChildren().add(button);
            }

            if (section.collapsible()) {
                roomLinks = sectionLinks;
                roomMenu = item(section.title(), "▦", false, false);
                roomMenu.setId("nav-room-management");
                roomMenu.setOnAction(event -> setRoomMenuOpen(!roomLinks.isVisible()));
                roomLinks.managedProperty().bind(roomLinks.visibleProperty());
                setRoomMenuOpen(role == Role.STAFF);
                menu.getChildren().add(new VBox(4, roomMenu, roomLinks));
            } else {
                Label heading = label(section.title(), MUTED, 10, FontWeight.BOLD);
                heading.setPadding(new Insets(4, 0, 4, 10));
                menu.getChildren().add(new VBox(5, heading, sectionLinks));
            }
        }
        return menu;
    }

    private VBox buildFooter(Runnable logout) {
        Button exit = item("Keluar", "↪", false, true);
        exit.setId("nav-logout");
        exit.setOnAction(event -> logout.run());

        VBox footer = new VBox(exit);
        footer.setPadding(new Insets(10));
        footer.setBorder(new Border(new BorderStroke(Color.web("#FFFFFF", 0.14), BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(1, 0, 0, 0))));
        return footer;
    }

    void select(HotelRoute route) {
        HotelRoute active = links.containsKey(route) ? route : route.parent;
        links.forEach((destination, button) -> {
            button.getProperties().put("active", destination == active);
            paintItem(button, false);
        });
        if (roomLinks != null && roomLinks.getChildren().contains(links.get(active))) {
            setRoomMenuOpen(true);
        }
    }

    private void setRoomMenuOpen(boolean open) {
        roomLinks.setVisible(open);
        roomMenu.setText(open ? "Manajemen Kamar   ▾" : "Manajemen Kamar   ›");
        roomMenu.setAccessibleText(open ? "Tutup menu Manajemen Kamar" : "Buka menu Manajemen Kamar");
    }

    private HBox brand(Role role) {
        Label mark = label("H", BRAND_INK, 20, FontWeight.BOLD);
        mark.setAlignment(Pos.CENTER);
        mark.setMinSize(38, 38);
        mark.setBackground(new Background(new BackgroundFill(BRAND_GOLD, CORNERS, Insets.EMPTY)));
        mark.setEffect(new DropShadow(6, 0, 2, Color.rgb(0, 0, 0, 0.25)));

        Label title = label("CheckIn.id", Color.WHITE, 17, FontWeight.BOLD);
        Label subtitle = label(role.label().toUpperCase(Locale.ROOT), MUTED, 10, FontWeight.SEMI_BOLD);
        subtitle.setWrapText(true);

        VBox identity = new VBox(2, title, subtitle);
        identity.setMinWidth(0);

        HBox row = new HBox(12, mark, identity);
        row.setPadding(new Insets(20, 14, 18, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button item(String text, String symbol, boolean nested, boolean dangerHover) {
        Label icon = label(symbol, MUTED, 15, FontWeight.BOLD);
        icon.setMinWidth(24);
        icon.setAlignment(Pos.CENTER);

        Button button = new Button(text, icon);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setGraphicTextGap(8);
        button.setCursor(Cursor.HAND);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(38);
        button.setPadding(new Insets(7, 10, 7, nested ? 24 : 10));
        button.setFont(Font.font("Segoe UI", FontWeight.MEDIUM, 13));
        button.setTextFill(TEXT);
        button.setWrapText(true);

        button.hoverProperty().addListener(obs -> paintItem(button, dangerHover));
        button.focusedProperty().addListener(obs -> paintItem(button, dangerHover));
        paintItem(button, dangerHover);
        return button;
    }

    private static void paintItem(Button button, boolean dangerHover) {
        boolean selected = Boolean.TRUE.equals(button.getProperties().get("active"));
        Color fill;
        if (selected) {
            fill = ACTIVE;
        } else if (button.isHover()) {
            fill = dangerHover ? LOGOUT_HOVER_TINT : HOVER_TINT;
        } else {
            fill = Color.TRANSPARENT;
        }

        button.setBackground(new Background(new BackgroundFill(fill, CORNERS, Insets.EMPTY)));
        button.setBorder(new Border(new BorderStroke(
                button.isFocused() ? MUTED : Color.TRANSPARENT,
                BorderStrokeStyle.SOLID, CORNERS, new BorderWidths(1)
        )));
        button.setTextFill(selected ? Color.WHITE : TEXT);
        if (button.getGraphic() instanceof Label icon) {
            icon.setTextFill(selected ? Color.WHITE : MUTED);
        }
    }

    private static Label label(String text, Color color, double size, FontWeight weight) {
        Label label = new Label(text);
        label.setTextFill(color);
        label.setFont(Font.font("Segoe UI", weight, size));
        return label;
    }
}