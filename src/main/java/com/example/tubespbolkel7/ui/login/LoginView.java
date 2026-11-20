package com.example.tubespbolkel7.ui.login;

import com.example.tubespbolkel7.model.Role;
import com.example.tubespbolkel7.model.UserAccounts;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.util.function.Consumer;

public final class LoginView extends Pane {
    public record SignIn(Role role, String username, String rememberedUsername) { }

    private final LoginLayout.Model model = LoginLayout.load();
    private final LoginPainter painter = new LoginPainter(model);
    private final LoginLayout.Layer screen = LoginArtwork.withoutFooterResolution(model.screen());
    private final UserAccounts accounts;
    private final Consumer<SignIn> signIn;
    private final Canvas canvas = new Canvas();
    private final Group artwork = new Group(canvas);
    private final Scale scale = new Scale();
    private LoginForm form;

    public LoginView(UserAccounts accounts, Consumer<SignIn> signIn) throws IOException {
        this.accounts = accounts;
        this.signIn = signIn;
        artwork.setManaged(false);
        artwork.getTransforms().add(scale);
        getChildren().add(artwork);
        setId("login-view");
        setBackground(Background.fill(Color.web("#14243A")));
    }

    public void reset(String rememberedUsername) {
        form = new LoginForm(screen, model, rememberedUsername, accounts::accepts, signIn);
        form.resize(screen.width(), screen.height());
        canvas.setWidth(0);
        canvas.setHeight(0);
        artwork.getChildren().setAll(canvas, form);
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        if (form == null || getWidth() <= 0 || getHeight() <= 0) return;
        double factor = Math.min(getWidth() / screen.width(), getHeight() / screen.height());
        scale.setX(factor);
        scale.setY(factor);
        double width = getWidth() / factor;
        double height = getHeight() / factor;
        if (canvas.getWidth() == width && canvas.getHeight() == height) return;
        LoginArtwork.Fit fit = LoginArtwork.fit(form.artwork(), width, height);
        canvas.setWidth(width);
        canvas.setHeight(height);
        form.setTranslateX(fit.formX());
        form.setTranslateY(fit.formY());
        painter.draw(canvas.getGraphicsContext2D(), fit.screen());
    }
}
