package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Simple progress dialog for showing operation progress
 */
public class ProgressDialog {
    private Stage dialog;
    private ProgressIndicator progressIndicator;
    private Label contentLabel;
    private Label titleLabel;
    
    // Professional typography constants
    private static final String PROFESSIONAL_FONT_FAMILY = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
    private static final String MODERN_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-background-color: #FFFFFF;
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    public ProgressDialog() {
        createDialog();
    }
    
    private void createDialog() {
        dialog = new Stage();
        dialog.initStyle(StageStyle.UTILITY);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setAlwaysOnTop(true);
        
        // Set app icon
        IconUtils.setStageIcon(dialog);
        
        // Main container
        VBox mainContainer = new VBox(16);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle(MODERN_STYLE + "-fx-background-radius: 12;");
        mainContainer.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.15)));
        
        // Title label
        titleLabel = new Label("Processing...");
        titleLabel.setStyle("""
            -fx-font-family: %s;
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-text-fill: #1F2937;
            -fx-text-alignment: center;
            """.formatted(PROFESSIONAL_FONT_FAMILY));
        
        // Progress indicator with professional styling
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(48, 48);
        progressIndicator.setStyle("""
            -fx-progress-color: #3B82F6;
            -fx-background-color: transparent;
            """);
        
        // Content label
        contentLabel = new Label("Please wait...");
        contentLabel.setStyle("""
            -fx-font-family: %s;
            -fx-font-size: 13px;
            -fx-text-fill: #6B7280;
            -fx-text-alignment: center;
            """.formatted(PROFESSIONAL_FONT_FAMILY));
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(280);
        
        mainContainer.getChildren().addAll(titleLabel, progressIndicator, contentLabel);
        
        Scene scene = new Scene(mainContainer, 320, 180);
        scene.getRoot().setStyle(MODERN_STYLE);
        dialog.setScene(scene);
    }
    
    public void setTitle(String title) {
        dialog.setTitle(title);
    }
    
    public void setHeaderText(String headerText) {
        // For simplicity, we'll use the title for header
        dialog.setTitle(headerText);
    }
    
    public void setContentText(String contentText) {
        if (Platform.isFxApplicationThread()) {
            contentLabel.setText(contentText);
        } else {
            Platform.runLater(() -> contentLabel.setText(contentText));
        }
    }
    
    public void show() {
        if (Platform.isFxApplicationThread()) {
            dialog.show();
        } else {
            Platform.runLater(() -> dialog.show());
        }
    }
    
    public void close() {
        if (Platform.isFxApplicationThread()) {
            dialog.close();
        } else {
            Platform.runLater(() -> dialog.close());
        }
    }
    
    public Label getContentLabel() {
        return contentLabel;
    }
}
