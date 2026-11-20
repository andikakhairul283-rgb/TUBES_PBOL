package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.Role;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

import java.util.Locale;

final class HotelWorkspace extends BorderPane {
    private final Role role;
    private final HotelSidebar sidebar;
    private final ScrollPane viewport = new ScrollPane();
    private final HotelOperations staff;
    private final BorderPane ownerLayout;
    private final Runnable logout;

    HotelWorkspace(Role role, HotelData data, String username, Runnable logout) {
        this.role = role;
        this.logout = logout;
        staff = new HotelOperations(data, username, role, this::open,
                () -> getScene() == null ? null : getScene().getWindow());
        setId("hotel-workspace");
        setBackground(Background.fill(Color.web("#F8F9FF")));

        sidebar = new HotelSidebar(role, this::open, logout);
        sidebar.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.clamp(getWidth() * 0.20, 180, 260), widthProperty()));
        setLeft(sidebar);

        viewport.setId("hotel-page-scroll");
        viewport.setFitToWidth(true);
        viewport.setFitToHeight(true);
        viewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        viewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        viewport.setBackground(Background.EMPTY);
        viewport.setBorder(Border.EMPTY);
        ownerLayout = role == Role.OWNER ? new BorderPane(viewport) : null;
        setCenter(ownerLayout == null ? viewport : ownerLayout);
    }

    void open(HotelRoute route) {
        if (!route.availableTo(role)) {
            return;
        }
        Node page = staff.view(route);
        if (ownerLayout != null) ownerLayout.setTop(OwnerToolbar.create(staff, logout));
        page.setId("page-" + route.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        page.setAccessibleText(route.title);
        viewport.setContent(page);
        viewport.setVvalue(0);
        sidebar.select(route);
    }
}
