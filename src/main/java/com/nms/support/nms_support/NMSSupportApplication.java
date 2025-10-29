package com.nms.support.nms_support;


import com.nms.support.nms_support.service.globalPack.IconUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;


public class NMSSupportApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        try {
            System.out.println("Starting NMS Support Application...");
            
            // Set stage icon
            IconUtils.setStageIcon(stage);
            
            // Load main FXML
            System.out.println("Loading main FXML...");
            FXMLLoader mainLoader = new FXMLLoader(NMSSupportApplication.class.getResource("view/main-view.fxml"));
            if (mainLoader.getLocation() == null) {
                throw new IOException("Main FXML resource not found");
            }
            
            Parent root = mainLoader.load();
            System.out.println("Main FXML loaded successfully");
            
            // Create scene
            Scene scene = new Scene(root, 1000, 600);
            System.out.println("Scene created successfully");
            
            // Add stylesheets
            try {
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("styles/light/components/entity-card.css")).toExternalForm());
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("styles/light/main-view.css")).toExternalForm());
                System.out.println("Stylesheets added successfully");
            } catch (Exception e) {
                System.err.println("Warning: Could not load some stylesheets: " + e.getMessage());
            }
            
            // Configure stage
            stage.setTitle("NMS DevTools");
            stage.setScene(scene);
            stage.setMaximized(true);
            
            // Show stage
            stage.show();
            System.out.println("Stage shown successfully");
            
            // Print system info
            System.out.println("Java runtime  : " + System.getProperty("java.runtime.version"));
            System.out.println("Java vendor   : " + System.getProperty("java.vendor"));
            
            // Print JavaFX runtime info with error handling
            try {
                String javafxVersion = javafx.application.Application.class.getModule().getDescriptor().rawVersion().orElse("?");
                System.out.println("JavaFX runtime: " + javafxVersion);
            } catch (Exception e) {
                System.out.println("JavaFX runtime: Unable to determine version - " + e.getMessage());
            }
            
            System.out.println("Application started successfully");
            
        } catch (Exception e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

    }
    
    @Override
    public void stop() {
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}