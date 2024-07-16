package com.nms.support.nms_support;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;

public class NMSSupportApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader mainLoader = new FXMLLoader(NMSSupportApplication.class.getResource("view/main-view.fxml"));
        Scene scene = new Scene(mainLoader.load(), 1000, 600);
        scene.getStylesheets().add(getClass().getResource("styles/light/components/entity-card.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("styles/light/main-view.css").toExternalForm());
        stage.setTitle("NMS Support");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}