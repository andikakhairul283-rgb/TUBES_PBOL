package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
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

    private void signIn(LoginView.SignIn session) {
        rememberedUsername = session.rememberedUsername();
        data.settings().recordLogin(session.username(), session.role());

        HotelWorkspace workspace = new HotelWorkspace(session.role(), data, session.username(), this::showLogin);
        setAccessibleText("CheckIn.id · " + session.role().label());
        setCenter(workspace);
        workspace.open(HotelRoute.DASHBOARD);
    }
}