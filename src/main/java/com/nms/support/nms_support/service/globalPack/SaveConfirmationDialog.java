package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Set;
import java.util.logging.Logger;

/**
 * Dialog to confirm saving changes before switching tabs or closing window
 */
public class SaveConfirmationDialog {
    private static final Logger logger = LoggerUtil.getLogger();
    
    private final Stage dialogStage;
    private final ChangeTrackingService changeTrackingService;
    private volatile boolean saveClicked = false;
    private volatile boolean cancelClicked = false;
    private volatile boolean dialogClosed = false;
    
    public SaveConfirmationDialog(Stage owner, ChangeTrackingService changeTrackingService) {
        this.changeTrackingService = changeTrackingService;
        this.dialogStage = new Stage();
        
        initializeDialog(owner);
        createContent();
    }
    
    private void initializeDialog(Stage owner) {
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Unsaved Changes");
        dialogStage.setResizable(false);
        dialogStage.setAlwaysOnTop(true);
    }
    
    private void createContent() {
        // Main container
        VBox mainContainer = new VBox(20);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setPadding(new Insets(30, 40, 30, 40));
        mainContainer.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10;");
        
        // Header with icon and title
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon warningIcon = new FontIcon("fa-exclamation-triangle");
        warningIcon.setIconSize(32);
        warningIcon.setIconColor(Color.web("#ff6b35"));
        
        VBox titleBox = new VBox(5);
        Label titleLabel = new Label("Unsaved Changes Detected");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#2c3e50"));
        
        Label subtitleLabel = new Label("You have unsaved changes that will be lost if you continue.");
        subtitleLabel.setFont(Font.font("Segoe UI", 12));
        subtitleLabel.setTextFill(Color.web("#7f8c8d"));
        
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        headerBox.getChildren().addAll(warningIcon, titleBox);
        
        // Content area
        VBox contentBox = new VBox(15);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        
        // Show which tabs have changes
        Set<String> tabsWithChanges = changeTrackingService.getTabsWithChanges();
        if (!tabsWithChanges.isEmpty()) {
            Label tabsLabel = new Label("Tabs with unsaved changes:");
            tabsLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
            tabsLabel.setTextFill(Color.web("#34495e"));
            
            VBox tabsList = new VBox(5);
            for (String tabName : tabsWithChanges) {
                HBox tabItem = new HBox(10);
                tabItem.setAlignment(Pos.CENTER_LEFT);
                
                FontIcon tabIcon = new FontIcon("fa-folder");
                tabIcon.setIconSize(16);
                tabIcon.setIconColor(Color.web("#3498db"));
                
                Label tabNameLabel = new Label(tabName);
                tabNameLabel.setFont(Font.font("Segoe UI", 12));
                tabNameLabel.setTextFill(Color.web("#2c3e50"));
                
                tabItem.getChildren().addAll(tabIcon, tabNameLabel);
                tabsList.getChildren().add(tabItem);
            }
            
            contentBox.getChildren().addAll(tabsLabel, tabsList);
        }
        
        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button saveButton = createStyledButton("Save Changes", "fa-save", "#27ae60");
        saveButton.setOnAction(e -> {
            if (!dialogClosed) {
                logger.info("Save button clicked");
                saveClicked = true;
                dialogClosed = true;
                dialogStage.close();
            }
        });
        // Add mouse click as backup
        saveButton.setOnMouseClicked(e -> {
            if (!dialogClosed) {
                logger.info("Save button mouse clicked");
                saveClicked = true;
                dialogClosed = true;
                dialogStage.close();
            }
        });
        
        Button discardButton = createStyledButton("Discard Changes", "fa-trash", "#e74c3c");
        discardButton.setOnAction(e -> {
            if (!dialogClosed) {
                logger.info("Discard button clicked");
                // No flag needed for discard - it's the default behavior when no flag is set
                dialogClosed = true;
                dialogStage.close();
            }
        });
        // Add mouse click as backup
        discardButton.setOnMouseClicked(e -> {
            if (!dialogClosed) {
                logger.info("Discard button mouse clicked");
                dialogClosed = true;
                dialogStage.close();
            }
        });
        
        Button cancelButton = createStyledButton("Cancel", "fa-times", "#95a5a6");
        cancelButton.setOnAction(e -> {
            if (!dialogClosed) {
                logger.info("Cancel button clicked");
                cancelClicked = true;
                dialogClosed = true;
                dialogStage.close();
            }
        });
        // Add mouse click as backup
        cancelButton.setOnMouseClicked(e -> {
            if (!dialogClosed) {
                logger.info("Cancel button mouse clicked");
                cancelClicked = true;
                dialogClosed = true;
                dialogStage.close();
            }
        });
        
        // Handle window close button (X) as cancel
        dialogStage.setOnCloseRequest(e -> {
            if (!dialogClosed) {
                logger.info("Window close request received");
                cancelClicked = true;
                dialogClosed = true;
                dialogStage.close();
            }
        });
        
        buttonBox.getChildren().addAll(saveButton, discardButton, cancelButton);
        
        // Add all components to main container
        mainContainer.getChildren().addAll(headerBox, contentBox, buttonBox);
        
        // Create scene
        Scene scene = new Scene(mainContainer);
        scene.getStylesheets().add(getClass().getResource("/com/nms/support/nms_support/styles/light/main-view.css").toExternalForm());
        dialogStage.setScene(scene);
        
        // Also handle ESC key to close dialog
        scene.setOnKeyPressed(e -> {
            if (!dialogClosed && e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                logger.info("ESC key pressed, closing dialog");
                cancelClicked = true;
                dialogClosed = true;
                dialogStage.close();
            }
        });
        
        // Set default button
        dialogStage.setOnShown(e -> saveButton.requestFocus());
    }
    
    private Button createStyledButton(String text, String iconLiteral, String color) {
        Button button = new Button();
        
        // Ensure button is focusable and clickable
        button.setFocusTraversable(true);
        button.setDefaultButton(false);
        button.setCancelButton(false);
        
        button.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: white; " +
            "-fx-font-family: 'Segoe UI'; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 8 16; " +
            "-fx-background-radius: 6; " +
            "-fx-cursor: hand; " +
            "-fx-min-width: 120; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2);",
            color
        ));
        
        // Set both text and graphic to ensure button is clickable
        button.setText(text);
        
        HBox buttonContent = new HBox(8);
        buttonContent.setAlignment(Pos.CENTER);
        
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(14);
        icon.setIconColor(Color.WHITE);
        
        buttonContent.getChildren().add(icon);
        button.setGraphic(buttonContent);
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            button.setStyle(String.format(
                "-fx-background-color: %s; " +
                "-fx-text-fill: white; " +
                "-fx-font-family: 'Segoe UI'; " +
                "-fx-font-size: 12px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 16; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-min-width: 120; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 3); " +
                "-fx-scale-x: 1.05; " +
                "-fx-scale-y: 1.05;",
                color
            ));
        });
        
        button.setOnMouseExited(e -> {
            button.setStyle(String.format(
                "-fx-background-color: %s; " +
                "-fx-text-fill: white; " +
                "-fx-font-family: 'Segoe UI'; " +
                "-fx-font-size: 12px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 16; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-min-width: 120; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2); " +
                "-fx-scale-x: 1.0; " +
                "-fx-scale-y: 1.0;",
                color
            ));
        });
        
        return button;
    }
    
    /**
     * Show the dialog and return the user's choice
     * @return true if user clicked Save, false if user clicked Discard, null if cancelled
     */
    public Boolean showAndWait() {
        logger.info("Showing save confirmation dialog");
        logger.info("Initial state - saveClicked: " + saveClicked + ", cancelClicked: " + cancelClicked);
        
        // Reset state before showing dialog
        saveClicked = false;
        cancelClicked = false;
        dialogClosed = false;
        
        dialogStage.showAndWait();
        
        logger.info("Dialog closed - saveClicked: " + saveClicked + ", cancelClicked: " + cancelClicked + ", dialogClosed: " + dialogClosed);
        
        if (saveClicked) {
            logger.info("User chose to save changes");
            return true;
        } else if (cancelClicked) {
            logger.info("User cancelled the dialog");
            return null;
        } else {
            logger.info("User chose to discard changes");
            return false;
        }
    }
    
    /**
     * Close the dialog
     */
    public void close() {
        if (!dialogClosed) {
            dialogClosed = true;
            dialogStage.close();
        }
    }
    
    /**
     * Check if dialog is closed
     */
    public boolean isClosed() {
        return dialogClosed;
    }
}
