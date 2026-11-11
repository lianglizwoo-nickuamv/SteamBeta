package com.example.steambeta;

import javafx.application.Application;
import javafx.stage.Stage;
import com.example.steambeta.client.AcademicWindow;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        new AcademicWindow(stage).open();
    }
}
