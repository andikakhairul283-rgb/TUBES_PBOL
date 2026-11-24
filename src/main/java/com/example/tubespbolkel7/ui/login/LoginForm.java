package com.example.tubespbolkel7.ui.login;

import com.example.tubespbolkel7.model.Role;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
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
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static com.example.tubespbolkel7.ui.login.LoginArtwork.Anchor;
import static com.example.tubespbolkel7.ui.login.LoginLayout.Layer;
import static com.example.tubespbolkel7.ui.login.LoginLayout.Model;
import static com.example.tubespbolkel7.ui.login.LoginLayout.list;
import static com.example.tubespbolkel7.ui.login.LoginLayout.number;

final class LoginForm extends Pane {
    // Palet warna konsisten dengan desain CheckIn.id
    private static final Color TEAL = Color.web("#2A9D8F");
    private static final Color TEAL_TINT_ACTIVE = Color.web("#2A9D8F", 0.12);
    private static final Color TEAL_TINT_HOVER = Color.web("#2A9D8F", 0.05);
    private static final Color NEUTRAL = Color.web("#E5E7EB");
    private static final Color NEUTRAL_HOVER = Color.web("#CBD5E1");
    private static final Color ERROR = Color.web("#D64545");
    private static final Color SUBMIT_HOVER_OVERLAY = Color.rgb(0, 0, 0, 0.10);
    private static final Color SUBMIT_PRESSED_OVERLAY = Color.rgb(0, 0, 0, 0.20);

    private static final CornerRadii CORNERS = new CornerRadii(8);
    private static final int MIN_PASSWORD_LENGTH = 8;

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

        Set<String> cardIds = initRoleCards(root);
        initInputs(rememberedUsername, usernameBox, passwordBox, revealBox);
        initControls(rememberedUsername, rememberBox, validation, submitBox, model);

        Set<String> hiddenIds = Set.of(
                usernameBox.layer().id(),
                passwordBox.layer().id(),
                validation.layer().id(),
                badge.layer().id(),
                rememberBox.layer().id()
        );
        this.artwork = LoginArtwork.withLiveForm(screen, hiddenIds, cardIds, submitBox.layer().id());
    }

    Layer artwork() {
        return artwork;
    }

    private Set<String> initRoleCards(Anchor root) {
        Set<String> cardIds = new HashSet<>();
        for (Role role : Role.values()) {
            String prefix = "Button - " + (role.ordinal() + 1) + '.';
            Anchor box = root.find(layer -> layer.name().startsWith(prefix));
            cardIds.add(box.layer().id());
            addRoleCard(role, box, role == Role.STAFF);
        }
        return cardIds;
    }

    private void initInputs(String rememberedUsername, Anchor usernameBox, Anchor passwordBox, Anchor revealBox) {
        username.setText(rememberedUsername);
        prepareField(username, "username", "Username", "Masukkan username", usernameBox, 41, 12);
        prepareField(password, "password", "Kata sandi", "Masukkan kata sandi", passwordBox, 42, 36);
        prepareField(visiblePassword, "password-visible", "Kata sandi", "Masukkan kata sandi", passwordBox, 42, 36);

        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        password.visibleProperty().bind(reveal.selectedProperty().not());
        visiblePassword.visibleProperty().bind(reveal.selectedProperty());
        prepareReveal(revealBox);
    }

    private void initControls(String rememberedUsername, Anchor rememberBox, Anchor validation,
                              Anchor submitBox, Model model) {
        remember.setId("remember-username");
        remember.setCursor(Cursor.HAND);
        remember.setSelected(!rememberedUsername.isBlank());
        remember.setFont(Font.font(12));
        remember.setTextFill(Color.web("#475569"));
        remember.setPadding(Insets.EMPTY);
        place(remember, rememberBox, 0, -2, 24, 4);

        error.setId("login-error");
        error.setTextFill(ERROR);
        error.setFont(Font.font("System", FontWeight.MEDIUM, 12));
        error.setVisible(false);
        place(error, validation, 0, 0, 0, 0);

        configureSubmitButton(submitBox, model);

        refreshSubmitLabel();
        roles.selectedToggleProperty().addListener((obs, prev, curr) -> {
            if (curr != null) {
                refreshSubmitLabel();
            }
        });
    }

    private void configureSubmitButton(Anchor submitBox, Model model) {
        submit.setId("sign-in");
        submit.setCursor(Cursor.HAND);
        submit.setBackground(Background.EMPTY);
        submit.setTextFill(Color.WHITE);
        submit.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        submit.setGraphic(submitIcon(submitBox, model));
        submit.setGraphicTextGap(8);
        submit.setDefaultButton(true);

        // Efek elevasi halus pada tombol utama
        DropShadow buttonShadow = new DropShadow(10, 0, 3, Color.web("#2A9D8F", 0.30));
        submit.setEffect(buttonShadow);

        Runnable updateSubmitVisual = () -> {
            if (submit.isPressed()) {
                submit.setBackground(new Background(new BackgroundFill(SUBMIT_PRESSED_OVERLAY, CORNERS, Insets.EMPTY)));
                submit.setScaleX(0.99);
                submit.setScaleY(0.99);
            } else if (submit.isHover()) {
                submit.setBackground(new Background(new BackgroundFill(SUBMIT_HOVER_OVERLAY, CORNERS, Insets.EMPTY)));
                submit.setScaleX(1.0);
                submit.setScaleY(1.0);
            } else {
                submit.setBackground(Background.EMPTY);
                submit.setScaleX(1.0);
                submit.setScaleY(1.0);
            }
            submit.setBorder(submit.isFocused() ? border(Color.WHITE, 2) : Border.EMPTY);
        };

        submit.hoverProperty().addListener(obs -> updateSubmitVisual.run());
        submit.pressedProperty().addListener(obs -> updateSubmitVisual.run());
        submit.focusedProperty().addListener(obs -> updateSubmitVisual.run());
        submit.setOnAction(event -> submit());

        place(submit, submitBox, 0, 0, 0, 0);
    }

    private void addRoleCard(Role role, Anchor box, boolean selected) {
        ToggleButton button = new ToggleButton();
        button.setId("role-" + role.name().toLowerCase(Locale.ROOT));
        button.setAccessibleText(role.label());
        button.setUserData(role);
        button.setToggleGroup(roles);
        button.setCursor(Cursor.HAND);
        button.setSelected(selected);
        button.setOnAction(event -> roles.selectToggle(button));

        Runnable updateStyle = () -> {
            boolean active = button.isSelected();
            boolean hovered = button.isHover();
            boolean focused = button.isFocused();

            if (active) {
                button.setBackground(new Background(new BackgroundFill(TEAL_TINT_ACTIVE, CORNERS, Insets.EMPTY)));
                button.setBorder(border(TEAL, 2));
            } else if (hovered || focused) {
                button.setBackground(new Background(new BackgroundFill(TEAL_TINT_HOVER, CORNERS, Insets.EMPTY)));
                button.setBorder(border(focused ? TEAL : NEUTRAL_HOVER, focused ? 2 : 1.5));
            } else {
                button.setBackground(Background.EMPTY);
                button.setBorder(border(NEUTRAL, 1));
            }
        };

        button.selectedProperty().addListener(obs -> updateStyle.run());
        button.hoverProperty().addListener(obs -> updateStyle.run());
        button.focusedProperty().addListener(obs -> updateStyle.run());
        updateStyle.run();

        place(button, box, 0, 0, 0, 0);
    }

    private void prepareField(TextField field, String id, String name, String prompt,
                              Anchor box, int paddingLeft, int paddingRight) {
        field.setId(id);
        field.setAccessibleText(name);
        field.setPromptText(prompt);
        field.setFont(Font.font(14));
        field.setBackground(Background.EMPTY);
        field.setPadding(new Insets(0, paddingRight, 0, paddingLeft));

        field.focusedProperty().addListener(obs -> refreshFieldBorders());
        field.hoverProperty().addListener(obs -> refreshFieldBorders());
        field.textProperty().addListener(obs -> {
            boolean isCurrentInvalid = (invalidField == field)
                    || (invalidField == password && field == visiblePassword);
            if (isCurrentInvalid) {
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

        Runnable updateRevealStyle = () -> {
            if (reveal.isHover()) {
                reveal.setBackground(new Background(new BackgroundFill(
                        Color.web("#0F172A", 0.05), new CornerRadii(6), Insets.EMPTY)));
            } else {
                reveal.setBackground(Background.EMPTY);
            }
            reveal.setBorder(reveal.isFocused() ? border(TEAL, 1) : Border.EMPTY);
        };

        reveal.hoverProperty().addListener(obs -> updateRevealStyle.run());
        reveal.focusedProperty().addListener(obs -> updateRevealStyle.run());
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
        List<?> strokePaths = list(vector.properties(), "strokePaths");
        SVGPath icon = new SVGPath();
        if (!strokePaths.isEmpty()) {
            List<?> outline = list(strokePaths.getFirst());
            String pathData = model.path((int) number(outline, 0, -1));
            if (pathData != null) {
                icon.setContent(pathData);
            }
        }
        icon.setFill(Color.WHITE);
        return icon;
    }

    private Role selectedRole() {
        Toggle selected = roles.getSelectedToggle();
        return (selected != null && selected.getUserData() instanceof Role role) ? role : Role.STAFF;
    }

    private void refreshSubmitLabel() {
        submit.setText("Masuk / Daftar " + selectedRole().label());
    }

    private void submit() {
        if (username.getText().isBlank()) {
            showError(username, "Masukkan username terlebih dahulu.");
            return;
        }
        if (password.getText().isBlank() || password.getLength() < MIN_PASSWORD_LENGTH) {
            showError(password, "Kata sandi minimal " + MIN_PASSWORD_LENGTH + " karakter.");
            return;
        }

        Role role = selectedRole();
        String account = username.getText().strip();
        try {
            if (!credentials.accepts(account, password.getText(), role)) {
                showError(password, "Akun nonaktif, kata sandi salah, atau username sudah dipakai untuk peran lain.");
                return;
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            showError(password, failure.getMessage());
            return;
        }

        password.clear();
        onSignIn.accept(new LoginView.SignIn(role, account, remember.isSelected() ? account : ""));
    }

    private void showError(TextField field, String message) {
        invalidField = field;
        error.setText(message);
        error.setOpacity(0);
        error.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), error);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        TextField focusTarget = (field == password && reveal.isSelected()) ? visiblePassword : field;
        focusTarget.requestFocus();
        playShake(focusTarget);
        refreshFieldBorders();
    }

    private static void playShake(Node target) {
        SequentialTransition shake = new SequentialTransition();
        double[] offsets = {-5, 5, -3, 3, 0};
        for (double offset : offsets) {
            TranslateTransition step = new TranslateTransition(Duration.millis(40), target);
            step.setToX(offset);
            shake.getChildren().add(step);
        }
        shake.play();
    }

    private void refreshFieldBorders() {
        boolean passwordHovered = password.isHover() || visiblePassword.isHover();
        username.setBorder(fieldBorder(username, invalidField == username, username.isHover()));
        password.setBorder(fieldBorder(password, invalidField == password, passwordHovered));
        visiblePassword.setBorder(fieldBorder(visiblePassword, invalidField == password, passwordHovered));
    }

    private Border fieldBorder(TextField field, boolean invalid, boolean hovered) {
        if (invalid) {
            return border(ERROR, 2);
        }
        if (field.isFocused()) {
            return border(TEAL, 2);
        }
        if (hovered) {
            return border(NEUTRAL_HOVER, 1.5);
        }
        return border(NEUTRAL, 1);
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
