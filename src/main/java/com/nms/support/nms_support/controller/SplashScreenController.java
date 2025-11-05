package com.nms.support.nms_support.controller;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

public class SplashScreenController implements Initializable {

    @FXML
    private AnchorPane rootPane;
    
    @FXML
    private Label titleLabel;
    
    @FXML
    private Label statusLabel;
    
    // No animations; keep simple
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupInitialState();
        startAnimations();
    }
    
    private void setupInitialState() {
        // Show labels plainly
        if (titleLabel.getText() == null || titleLabel.getText().isEmpty()) {
            titleLabel.setText("NMS Development Tools");
        }
        statusLabel.setOpacity(1.0);
    }
    
    private void startAnimations() {
        // Intentionally left blank (no animations)
        Platform.runLater(() -> {
            // Ensure status shows a base message if empty
            if (statusLabel.getText() == null || statusLabel.getText().isEmpty()) {
                statusLabel.setText("Loading...");
            }
        });
    }
    
    public void updateStatus(String status) {
        Platform.runLater(() -> {
            statusLabel.setText(status);
        });
    }

    public void fadeOut(Runnable onComplete) {
        // Check if rootPane is still valid
        if (rootPane == null) {
            // Already removed, just run callback
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        // No timelines to stop
        
        try {
            // Create fade-out animation for all elements (smooth 600-800ms)
            FadeTransition fadeOut = new FadeTransition(Duration.millis(700), rootPane);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            
            // Create scale-down animation for smooth transition
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(700), rootPane);
            scaleOut.setFromX(1.0);
            scaleOut.setFromY(1.0);
            scaleOut.setToX(0.98);
            scaleOut.setToY(0.98);
            
            // Combine animations
            ParallelTransition parallelOut = new ParallelTransition(fadeOut, scaleOut);
            
            parallelOut.setOnFinished(event -> {
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            
            parallelOut.play();
        } catch (Exception e) {
            // If animation fails (node removed), just run callback
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
    
    // No status/spinner; breathing title only
}
