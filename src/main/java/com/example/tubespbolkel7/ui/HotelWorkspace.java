package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.Role;
import com.example.tubespbolkel7.model.Pengguna;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Locale;

final class HotelWorkspace extends BorderPane {
    private static final Color CANVAS_BG = Color.web("#F8F9FF");

    private final Role role;
    private final HotelSidebar sidebar;
    private final ScrollPane viewport = new ScrollPane();
    private final HotelOperations operations;
    private final BorderPane ownerLayout;
    private final Runnable logout;

    HotelWorkspace(Pengguna pengguna, HotelData data, Runnable logout) {
        this.role = pengguna.getRole();
        this.logout = logout;
        this.operations = new HotelOperations(
        data, pengguna.getUsername(), role, this::open,
        () -> getScene() == null ? null : getScene().getWindow()
);

        setId("hotel-workspace");
        setBackground(Background.fill(CANVAS_BG));

        sidebar = new HotelSidebar(role, this::open, logout);
        sidebar.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.clamp(getWidth() * 0.20, 185, 260), widthProperty()));
        setLeft(sidebar);

        viewport.setId("hotel-page-scroll");
        viewport.setFitToWidth(true);
        viewport.setFitToHeight(true);
        viewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        viewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        viewport.setBackground(Background.EMPTY);
        viewport.setBorder(Border.EMPTY);

        ownerLayout = (role == Role.OWNER) ? new BorderPane(viewport) : null;
        setCenter(ownerLayout == null ? viewport : ownerLayout);
    }

    void open(HotelRoute route) {
        if (route == null || !route.availableTo(role)) {
            return;
        }
        Node page = operations.view(route);
        if (ownerLayout != null) {
            ownerLayout.setTop(OwnerToolbar.create(operations, logout));
        }
        page.setId("page-" + route.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        page.setAccessibleText(route.title);

        page.setOpacity(0);
        viewport.setContent(page);
        viewport.setVvalue(0);
        sidebar.select(route);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(140), page);
        fadeIn.setFromValue(0.35);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
}