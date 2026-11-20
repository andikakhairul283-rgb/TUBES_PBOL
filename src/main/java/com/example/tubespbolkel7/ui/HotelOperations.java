package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.Role;
import com.example.tubespbolkel7.model.RoomInventory;
import com.example.tubespbolkel7.model.ServiceLedger;

import javafx.scene.Node;
import javafx.stage.Window;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class HotelOperations {
    private final HotelData data;
    private final Consumer<HotelRoute> navigate;
    private final Supplier<Window> window;
    private final ReportPages reports;
    private final RoomPages rooms;
    private final HousekeepingPage housekeeping;
    private final SettingsPages settings;
    private final MaintenanceRoomActions maintenance;
    private final String username;
    private final Role role;
    private String selected;
    private String selectedRoom;
    private RoomInventory.Status roomFilter;

    HotelOperations(HotelData data, String username, Role role, Consumer<HotelRoute> navigate, Supplier<Window> window) {
        this.data = data;
        this.username = username;
        this.role = role;
        this.navigate = navigate;
        this.window = window;
        reports = new ReportPages(this, data.today());
        rooms = new RoomPages(this);
        housekeeping = new HousekeepingPage(this);
        settings = new SettingsPages(this);
        maintenance = new MaintenanceRoomActions(this, housekeeping);
    }

    HotelData data() { return data; }
    ServiceLedger services() { return data.services(); }
    String selected() { return selected; }
    void select(String code) { selected = code; }
    String username() { return username; }
    Role role() { return role; }
    boolean isOwner() { return role == Role.OWNER; }
    boolean isMaintenance() { return role == Role.MAINTENANCE; }
    MaintenanceRoomActions maintenance() { return maintenance; }
    HousekeepingPage housekeeping() { return housekeeping; }
    RoomInventory.Status consumeRoomFilter() {
        var filter = roomFilter;
        roomFilter = null;
        return filter;
    }
    void openRoomGrid(RoomInventory.Status filter) {
        roomFilter = filter;
        open(HotelRoute.ROOM_GRID);
    }
    String selectedRoom() { return selectedRoom; }
    void selectRoom(String number) { selectedRoom = number; }
    Window owner() { return window.get(); }
    void open(HotelRoute route) { navigate.accept(route); }

    Node view(HotelRoute route) {
        return switch (route) {
            case DASHBOARD -> switch (role) {
                case OWNER -> OwnerDashboard.view(this);
                case STAFF -> StaffDashboard.view(this);
                case MAINTENANCE -> MaintenanceDashboard.view(this);
            };
            case RESERVATIONS, NEW_RESERVATION, WALK_IN, AVAILABILITY -> ReservationPage.view(this, route);
            case FLOOR_PLAN -> isOwner() ? OwnerFloorPlan.view(this) : ReservationPage.view(this, route);
            case CHECK_IN, CHECK_OUT, IN_HOUSE, PAYMENTS -> ArrivalPage.view(this, route);
            case GUESTS -> GuestPage.view(this);
            case SERVICES, SERVICE_CATALOG -> ServicePage.view(this, route);
            case OCCUPANCY, REVENUE -> reports.view(route);
            case ROOM_TYPES, ROOMS, ROOM_FORM, ROOM_GRID -> rooms.view(route);
            case HOUSEKEEPING -> housekeeping.view();
            case PROFILE, RATES, POLICIES, USERS, MY_PROFILE -> settings.view(route);
        };
    }
}
