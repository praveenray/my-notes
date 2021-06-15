package com.praveenray.notes;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class ApplicationMain extends Application {
    @Override
    public void start(Stage primaryStage) {
        Parameters params = getParameters();
        primaryStage.getIcons().add(new Image("/icons/app_icon.png"));
        (new ApplicationMainKot(params)).start(primaryStage);
    }

    public static void appMain(String[] args) {
        launch(args);
    }
}

