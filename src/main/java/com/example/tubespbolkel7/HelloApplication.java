package com.example.tubespbolkel7;

import com.example.tubespbolkel7.ui.HotelView;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Parent view = createUi();
        stage.setTitle("CheckIn.id");
        Scene scene = new Scene(view, 1050, 750);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(540);
        stage.setMinHeight(420);
        stage.setMaximized(true);
        stage.setFullScreen(true);
        stage.show();
        view.requestFocus();
    }

    public Parent createUi() throws IOException {
        return new HotelView();
    }
}
