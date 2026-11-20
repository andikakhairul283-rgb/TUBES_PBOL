package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.Role;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
    private static final CornerRadii CORNERS = new CornerRadii(9);

    private final Map<HotelRoute, Button> links = new EnumMap<>(HotelRoute.class);
    private VBox roomLinks;
    private Button roomMenu;

    HotelSidebar(Role role, Consumer<HotelRoute> navigate, Runnable logout) {
        setId("hotel-sidebar");
        setBackground(Background.fill(NAVY));
        setMinWidth(0);
        getChildren().add(brand(role));

        VBox menu = new VBox(13);
        menu.setPadding(new Insets(8, 10, 14, 10));
        menu.setBackground(Background.fill(NAVY));
        for (HotelRoute.Section section : HotelRoute.menu(role)) {
            VBox sectionLinks = new VBox(3);
            for (HotelRoute.Link link : section.links()) {
                Button button = item(link.label(), link.route().icon, section.collapsible());
                button.setId("nav-" + link.route().name().toLowerCase(Locale.ROOT).replace('_', '-'));
                button.setOnAction(event -> navigate.accept(link.route()));
                links.put(link.route(), button);
                sectionLinks.getChildren().add(button);
            }
            if (section.collapsible()) {
                roomLinks = sectionLinks;
                roomMenu = item(section.title(), "▦", false);
                roomMenu.setId("nav-room-management");
                roomMenu.setOnAction(event -> setRoomMenuOpen(!roomLinks.isVisible()));
                roomLinks.managedProperty().bind(roomLinks.visibleProperty());
                setRoomMenuOpen(role == Role.STAFF);
                menu.getChildren().add(new VBox(4, roomMenu, roomLinks));
            } else {
                Label heading = label(section.title(), MUTED, 10, FontWeight.BOLD);
                heading.setPadding(new Insets(2, 0, 4, 9));
                menu.getChildren().add(new VBox(5, heading, sectionLinks));
            }
        }
        ScrollPane navigation = new ScrollPane(menu);
        navigation.setFitToWidth(true);
        navigation.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navigation.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navigation.setBackground(Background.EMPTY);
        navigation.setBorder(Border.EMPTY);
        menu.minHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> navigation.getViewportBounds().getHeight(), navigation.viewportBoundsProperty()));
        VBox.setVgrow(navigation, Priority.ALWAYS);
        getChildren().add(navigation);

        Button exit = item("Keluar", "↪", false);
        exit.setId("nav-logout");
        exit.setOnAction(event -> logout.run());
        VBox footer = new VBox(exit);
        footer.setPadding(new Insets(10));
        footer.setBorder(new Border(new BorderStroke(Color.web("#FFFFFF", .14), BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(1, 0, 0, 0))));
        getChildren().add(footer);
    }

    void select(HotelRoute route) {
        HotelRoute active = links.containsKey(route) ? route : route.parent;
        links.forEach((destination, button) -> {
            button.getProperties().put("active", destination == active);
            paintItem(button);
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
        Label mark = label("H", Color.web("#513114"), 20, FontWeight.BOLD);
        mark.setAlignment(Pos.CENTER);
        mark.setMinSize(38, 38);
        mark.setBackground(new Background(new BackgroundFill(Color.web("#FFDEA0"), CORNERS, Insets.EMPTY)));
        Label title = label("Hotel Manager", Color.WHITE, 17, FontWeight.BOLD);
        Label subtitle = label(role.label().toUpperCase(Locale.ROOT), MUTED, 10, FontWeight.SEMI_BOLD);
        subtitle.setWrapText(true);
        VBox identity = new VBox(3, title, subtitle);
        identity.setMinWidth(0);
        HBox row = new HBox(11, mark, identity);
        row.setPadding(new Insets(20, 14, 19, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button item(String text, String symbol, boolean nested) {
        Label icon = label(symbol, MUTED, 16, FontWeight.BOLD);
        icon.setMinWidth(26);
        Button button = new Button(text, icon);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setGraphicTextGap(6);
        button.setCursor(Cursor.HAND);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(36);
        button.setPadding(new Insets(7, 9, 7, nested ? 23 : 9));
        button.setFont(Font.font("Segoe UI", FontWeight.MEDIUM, 13));
        button.setTextFill(TEXT);
        button.setWrapText(true);
        button.hoverProperty().addListener(observable -> paintItem(button));
        button.focusedProperty().addListener(observable -> paintItem(button));
        paintItem(button);
        return button;
    }

    private static void paintItem(Button button) {
        boolean selected = Boolean.TRUE.equals(button.getProperties().get("active"));
        Color fill = selected ? ACTIVE : button.isHover() ? Color.web("#FFFFFF", .1) : Color.TRANSPARENT;
        button.setBackground(new Background(new BackgroundFill(fill, CORNERS, Insets.EMPTY)));
        button.setBorder(new Border(new BorderStroke(button.isFocused() ? MUTED : Color.TRANSPARENT,
                BorderStrokeStyle.SOLID, CORNERS, new BorderWidths(1))));
        button.setTextFill(selected ? Color.WHITE : TEXT);
        ((Label) button.getGraphic()).setTextFill(selected ? Color.WHITE : MUTED);
    }

    private static Label label(String text, Color color, double size, FontWeight weight) {
        Label label = new Label(text);
        label.setTextFill(color);
        label.setFont(Font.font("Segoe UI", weight, size));
        return label;
    }
}
