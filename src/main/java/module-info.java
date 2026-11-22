module com.example.steambeta {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;
    requires java.net.http;
    requires com.google.gson;

    opens com.example.steambeta to javafx.fxml;
    opens com.example.steambeta.client to com.google.gson;
    opens com.example.steambeta.client.ui to com.google.gson, javafx.fxml;

    exports com.example.steambeta;
    exports com.example.steambeta.client;
    exports com.example.steambeta.client.ui;
}
