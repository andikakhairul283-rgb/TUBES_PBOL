package com.example.tubespbolkel7.ui.login;

import com.example.tubespbolkel7.model.Role;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.example.tubespbolkel7.ui.login.LoginArtwork.Anchor;
import static com.example.tubespbolkel7.ui.login.LoginLayout.Layer;
import static com.example.tubespbolkel7.ui.login.LoginLayout.Model;
import static com.example.tubespbolkel7.ui.login.LoginLayout.list;
import static com.example.tubespbolkel7.ui.login.LoginLayout.number;

final class LoginForm extends Pane {
    private static final Color TEAL = Color.web("#2A9D8F");
    private static final Color NEUTRAL = Color.web("#E5E7EB");
    private static final Color ERROR = Color.web("#D64545");
    private static final CornerRadii CORNERS = new CornerRadii(8);

    @FunctionalInterface
    interface Credentials {
        boolean accepts(String username, String password, Role role);
    }

    private final Layer artwork;
    private final ToggleGroup roles = new ToggleGroup();
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final TextField visiblePassword = new TextField();
    private final ToggleButton reveal = new ToggleButton();
    private final CheckBox remember = new CheckBox("Ingat saya");
    private final Button submit = new Button();
    private final Label error = new Label();
    private final Consumer<LoginView.SignIn> onSignIn;
    private final Credentials credentials;
    private TextField invalidField;

    LoginForm(Layer screen, Model model, String rememberedUsername, Credentials credentials,
              Consumer<LoginView.SignIn> onSignIn) {
        this.onSignIn = onSignIn;
        this.credentials = credentials;
        setManaged(false);
        setPickOnBounds(false);

        Anchor root = new Anchor(screen, 0, 0);
        Anchor usernameBox = root.find(layer -> layer.name().startsWith("Field: Username"))
                .find(layer -> layer.name().equals("Input"));
        Anchor passwordSection = root.find(layer -> layer.name().startsWith("Field: Kata Sandi"));
        Anchor passwordBox = passwordSection.find(layer -> layer.name().equals("Input"));
        Anchor revealBox = passwordSection.find(layer -> layer.name().startsWith("Button - Show / Hide"));
        Anchor validation = passwordSection.find(layer -> layer.name().startsWith("Inline validation"));
        Anchor badge = passwordSection.find(layer -> layer.name().equals("Background+Border"));
        Anchor rememberBox = root.find(layer -> layer.name().startsWith("Checkbox"))
                .find(layer -> layer.name().equals("Label"));
        Anchor submitBox = root.find(layer -> layer.name().startsWith("Full Width Teal Submit Button"));

        Set<String> cardIds = new HashSet<>();
        for (Role role : Role.values()) {
            String prefix = "Button - " + (role.ordinal() + 1) + '.';
            Anchor box = root.find(layer -> layer.name().startsWith(prefix));
            cardIds.add(box.layer().id());
            addRoleCard(role, box, role == Role.STAFF);
        }

        username.setText(rememberedUsername);
        prepareField(username, "username", "Username", "Masukkan username", usernameBox, 41, 12);
        prepareField(password, "password", "Kata sandi", "Masukkan kata sandi", passwordBox, 42, 36);
        prepareField(visiblePassword, "password-visible", "Kata sandi", "Masukkan kata sandi", passwordBox, 42, 36);
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        password.visibleProperty().bind(reveal.selectedProperty().not());
        visiblePassword.visibleProperty().bind(reveal.selectedProperty());
        prepareReveal(revealBox);

        remember.setId("remember-username");
        remember.setSelected(!rememberedUsername.isBlank());
        remember.setFont(Font.font(12));
        remember.setPadding(Insets.EMPTY);
        place(remember, rememberBox, 0, -2, 18, 4);

        error.setId("login-error");
        error.setTextFill(ERROR);
        error.setFont(Font.font(12));
        error.setVisible(false);
        place(error, validation, 0, 0, 0, 0);

        submit.setId("sign-in");
        submit.setCursor(Cursor.HAND);
        submit.setBackground(Background.EMPTY);
        submit.setTextFill(Color.WHITE);
        submit.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        submit.setGraphic(submitIcon(submitBox, model));
        submit.setGraphicTextGap(8);
        submit.focusedProperty().addListener(observable -> submit.setBorder(submit.isFocused()
                ? border(Color.WHITE, 2) : Border.EMPTY));
        submit.setDefaultButton(true);
        submit.setOnAction(event -> submit());
        place(submit, submitBox, 0, 0, 0, 0);
        refreshSubmitLabel();
        roles.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current != null) {
                refreshSubmitLabel();
            }
        });

        Set<String> hidden = Set.of(usernameBox.layer().id(), passwordBox.layer().id(), validation.layer().id(),
                badge.layer().id(), rememberBox.layer().id());
        artwork = LoginArtwork.withLiveForm(screen, hidden, cardIds, submitBox.layer().id());
    }

    Layer artwork() { return artwork; }

    private void addRoleCard(Role role, Anchor box, boolean selected) {
        ToggleButton button = new ToggleButton();
        button.setId("role-" + role.name().toLowerCase(java.util.Locale.ROOT));
        button.setAccessibleText(role.label());
        button.setUserData(role);
        button.setToggleGroup(roles);
        button.setCursor(Cursor.HAND);
        button.setSelected(selected);
        button.setOnAction(event -> roles.selectToggle(button));
        Runnable update = () -> {
            button.setBackground(button.isSelected()
                    ? new Background(new BackgroundFill(Color.web("#2A9D8F", 0.10), CORNERS, Insets.EMPTY))
                    : Background.EMPTY);
            button.setBorder(border(button.isSelected() || button.isFocused() ? TEAL : NEUTRAL,
                    button.isSelected() || button.isFocused() ? 2 : 1));
        };
        button.selectedProperty().addListener(observable -> update.run());
        button.focusedProperty().addListener(observable -> update.run());
        update.run();
        place(button, box, 0, 0, 0, 0);
    }

    private void prepareField(TextField field, String id, String name, String prompt, Anchor box, int left, int right) {
        field.setId(id);
        field.setAccessibleText(name);
        field.setPromptText(prompt);
        field.setFont(Font.font(14));
        field.setBackground(Background.EMPTY);
        field.setPadding(new Insets(0, right, 0, left));
        field.focusedProperty().addListener(observable -> refreshFieldBorders());
        field.textProperty().addListener(observable -> {
            if (invalidField == field || invalidField == password && field == visiblePassword) {
                invalidField = null;
                error.setVisible(false);
                refreshFieldBorders();
            }
        });
        field.setOnAction(event -> submit());
        field.setBorder(border(NEUTRAL, 1));
        place(field, box, 0, 0, 0, 0);
    }

    private void prepareReveal(Anchor box) {
        reveal.setId("toggle-password");
        reveal.setAccessibleText("Tampilkan kata sandi");
        reveal.setCursor(Cursor.HAND);
        reveal.setBackground(Background.EMPTY);
        reveal.setBorder(Border.EMPTY);
        reveal.focusedProperty().addListener(observable -> reveal.setBorder(reveal.isFocused() ? border(TEAL, 1) : Border.EMPTY));
        reveal.setOnAction(event -> {
            boolean showing = reveal.isSelected();
            reveal.setAccessibleText(showing ? "Sembunyikan kata sandi" : "Tampilkan kata sandi");
            TextField previous = showing ? password : visiblePassword;
            TextField current = showing ? visiblePassword : password;
            current.requestFocus();
            current.selectRange(previous.getAnchor(), previous.getCaretPosition());
        });
        place(reveal, box, -4, 4, 0, -8);
    }

    private SVGPath submitIcon(Anchor box, Model model) {
        Layer vector = box.find(layer -> layer.type().equals("VECTOR")).layer();
        List<?> outline = list(list(vector.properties(), "strokePaths").getFirst());
        SVGPath icon = new SVGPath();
        icon.setContent(model.path((int) number(outline, 0, -1)));
        icon.setFill(Color.WHITE);
        return icon;
    }

    private void refreshSubmitLabel() {
        Role role = (Role) roles.getSelectedToggle().getUserData();
        submit.setText("Masuk sebagai " + role.label());
    }

    private void submit() {
        if (username.getText().isBlank()) {
            showError(username, "Masukkan username.");
            return;
        }
        if (password.getText().isBlank() || password.getLength() < 8) {
            showError(password, "Kata sandi minimal 8 karakter.");
            return;
        }
        Role role = (Role) roles.getSelectedToggle().getUserData();
        String account = username.getText().strip();
        if (!credentials.accepts(account, password.getText(), role)) {
            showError(password, "Akun tidak aktif, peran tidak sesuai, atau kata sandi salah.");
            return;
        }
        password.clear();
        onSignIn.accept(new LoginView.SignIn(role, account, remember.isSelected() ? account : ""));
    }

    private void showError(TextField field, String message) {
        invalidField = field;
        error.setText(message);
        error.setVisible(true);
        (field == password && reveal.isSelected() ? visiblePassword : field).requestFocus();
        refreshFieldBorders();
    }

    private void refreshFieldBorders() {
        username.setBorder(fieldBorder(username, invalidField == username));
        password.setBorder(fieldBorder(password, invalidField == password));
        visiblePassword.setBorder(fieldBorder(visiblePassword, invalidField == password));
    }

    private Border fieldBorder(TextField field, boolean invalid) {
        return border(invalid ? ERROR : field.isFocused() ? TEAL : NEUTRAL, invalid || field.isFocused() ? 2 : 1);
    }

    private static Border border(Color color, double width) {
        return new Border(new BorderStroke(color, BorderStrokeStyle.SOLID, CORNERS, new BorderWidths(width)));
    }

    private void place(Region node, Anchor box, double dx, double dy, double dw, double dh) {
        node.setManaged(false);
        node.resizeRelocate(box.x() + dx, box.y() + dy, box.layer().width() + dw, box.layer().height() + dh);
        getChildren().add(node);
    }
}
