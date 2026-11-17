module com.example.tubespbolkel7 {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.tubespbolkel7 to javafx.fxml;
    exports com.example.tubespbolkel7;
}