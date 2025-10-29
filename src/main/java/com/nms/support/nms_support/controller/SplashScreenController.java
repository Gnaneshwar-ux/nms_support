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
    
    private Timeline pulseTimeline;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupInitialState();
        startAnimations();
    }
    
    private void setupInitialState() {
        // Minimal setup: only title with initial opacity for breathing
        titleLabel.setOpacity(0.8);
        statusLabel.setOpacity(0);
    }
    
    private void startAnimations() {
        // Create breathing fade for title (black text) - faster breathing
        FadeTransition titlePulse = new FadeTransition(Duration.millis(1000), titleLabel);
        titlePulse.setFromValue(1.0);
        titlePulse.setToValue(0.6);
        titlePulse.setAutoReverse(true);
        titlePulse.setCycleCount(Animation.INDEFINITE);
        
        // Fade in status label
        FadeTransition statusFadeIn = new FadeTransition(Duration.millis(800), statusLabel);
        statusFadeIn.setFromValue(0);
        statusFadeIn.setToValue(1.0);
        
        // Start animations
        Platform.runLater(() -> {
            titlePulse.play();
            statusFadeIn.play();
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
        
        // Stop pulse animations
        if (pulseTimeline != null) {
            pulseTimeline.stop();
        }
        
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
