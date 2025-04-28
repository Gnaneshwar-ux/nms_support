package com.nms.support.nms_support;


import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.service.globalPack.IconUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;


public class NMSSupportApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        IconUtils.setStageIcon(stage);
        FXMLLoader mainLoader = new FXMLLoader(NMSSupportApplication.class.getResource("view/main-view.fxml"));
        Scene scene = new Scene(mainLoader.load(), 1000, 600);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("styles/light/components/entity-card.css")).toExternalForm());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("styles/light/main-view.css")).toExternalForm());
        stage.setTitle("NMS DevTools");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    @Override
    public void stop(){
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}