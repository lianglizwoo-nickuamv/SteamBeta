module com.example.steambeta {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.google.gson;
    opens com.example.steambeta.client to com.google.gson;


    opens com.example.steambeta to javafx.fxml;
    exports com.example.steambeta;
}
