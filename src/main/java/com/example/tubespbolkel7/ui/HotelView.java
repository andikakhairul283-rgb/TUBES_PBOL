package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.Pengguna;
import com.example.tubespbolkel7.model.AdminResepsionis;
import com.example.tubespbolkel7.model.Owner;
import com.example.tubespbolkel7.model.PengurusMaintenance;
import com.example.tubespbolkel7.model.Role;
import com.example.tubespbolkel7.ui.login.LoginView;

import javafx.scene.layout.BorderPane;
import java.io.IOException;

public final class HotelView extends BorderPane {
    private final HotelData data = new HotelData();
    private final LoginView login = new LoginView(data.settings().accounts(), this::signIn);
    private String rememberedUsername = "";

    public HotelView() throws IOException {
        showLogin();
    }

    private void showLogin() {
        login.reset(rememberedUsername);
        setCenter(login);
        setAccessibleText("Masuk CheckIn.id");
    }

    private Pengguna createPengguna(Role role, String username) {
    var account = data.settings().accounts().find(username);
    String nama = account == null ? username : account.name();

    return switch (role) {
        case STAFF -> new AdminResepsionis(username, nama);
        case OWNER -> new Owner(username, nama);
        case MAINTENANCE -> new PengurusMaintenance(username, nama);
    };
}

    private void signIn(LoginView.SignIn session) {
        rememberedUsername = session.rememberedUsername();
        data.settings().recordLogin(session.username(), session.role());

        Pengguna pengguna = createPengguna(session.role(), session.username());
        
        HotelWorkspace workspace = new HotelWorkspace(pengguna, data, this::showLogin);
        setAccessibleText("CheckIn.id · " + session.role().label());
        setCenter(workspace);
        workspace.open(HotelRoute.DASHBOARD);
    }
}