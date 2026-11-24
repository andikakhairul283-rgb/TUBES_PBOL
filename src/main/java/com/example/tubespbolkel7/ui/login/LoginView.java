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
import java.util.Objects;
import java.util.function.Consumer;

public final class LoginView extends Pane {
    public record SignIn(Role role, String username, String rememberedUsername) { }

    private static final Color BACKDROP_COLOR = Color.web("#14243A");

    private final LoginLayout.Model model = LoginLayout.load();
    private final LoginPainter painter = new LoginPainter(model);
    private final LoginLayout.Layer screen = LoginArtwork.withoutFooterResolution(model.screen());
    private final UserAccounts accounts;
    private final Consumer<SignIn> signIn;

    private final Canvas canvas = new Canvas();
    private final Group artwork = new Group(canvas);
    private final Scale scale = new Scale();

    private LoginForm form;
    private double lastLogicalWidth = -1;
    private double lastLogicalHeight = -1;

    public LoginView(UserAccounts accounts, Consumer<SignIn> signIn) throws IOException {
        this.accounts = Objects.requireNonNull(accounts, "UserAccounts tidak boleh null");
        this.signIn = Objects.requireNonNull(signIn, "Callback signIn tidak boleh null");

        artwork.setManaged(false);
        artwork.getTransforms().add(scale);

        getChildren().add(artwork);
        setId("login-view");
        setBackground(Background.fill(BACKDROP_COLOR));
    }

    public void reset(String rememberedUsername) {
        String safeUsername = rememberedUsername == null ? "" : rememberedUsername;
        form = new LoginForm(screen, model, safeUsername, accounts::signIn, signIn);
        form.resize(screen.width(), screen.height());

        lastLogicalWidth = -1;
        lastLogicalHeight = -1;
        artwork.getChildren().setAll(canvas, form);
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        if (form == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        double factor = Math.min(getWidth() / screen.width(), getHeight() / screen.height());
        scale.setX(factor);
        scale.setY(factor);

        double logicalWidth = getWidth() / factor;
        double logicalHeight = getHeight() / factor;

        if (Math.abs(lastLogicalWidth - logicalWidth) < 0.5
                && Math.abs(lastLogicalHeight - logicalHeight) < 0.5) {
            return;
        }

        lastLogicalWidth = logicalWidth;
        lastLogicalHeight = logicalHeight;

        LoginArtwork.Fit fit = LoginArtwork.fit(form.artwork(), logicalWidth, logicalHeight);
        canvas.setWidth(logicalWidth);
        canvas.setHeight(logicalHeight);

        form.setTranslateX(fit.formX());
        form.setTranslateY(fit.formY());
        painter.draw(canvas.getGraphicsContext2D(), fit.screen());
    }
}
