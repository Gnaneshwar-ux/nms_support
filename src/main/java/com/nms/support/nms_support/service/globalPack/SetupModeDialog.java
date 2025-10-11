package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.ArrayList;

/**
 * Enhanced setup mode selection dialog with dynamic options and modern UI design
 */
public class SetupModeDialog {
    
    public static class SetupMode {
        private final String id;
        private final String title;
        private final String description;
        private final String icon;
        private final boolean isDefault;
        
        public SetupMode(String id, String title, String description, String icon, boolean isDefault) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.isDefault = isDefault;
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }
        public boolean isDefault() { return isDefault; }
    }
    
    // Predefined setup modes
    public static final SetupMode PATCH_UPGRADE = new SetupMode(
        "PATCH_UPGRADE", 
        "Patch Upgrade (Product + Project Update)", 
        "Install product with project validation and build files update",
        "fa-cube",
        true
    );
    
    public static final SetupMode PRODUCT_ONLY = new SetupMode(
        "PRODUCT_ONLY", 
        "Product Only (No Project Validation)", 
        "Install only the product, skip project validation and build files",
        "fa-box",
        false
    );
    
    public static final SetupMode FULL_CHECKOUT = new SetupMode(
        "FULL_CHECKOUT", 
        "Project(SVN Checkout) + product", 
        "Complete setup including SVN checkout and product installation",
        "fa-download",
        false
    );
    
    public static final SetupMode PROJECT_AND_PRODUCT_FROM_SERVER = new SetupMode(
        "PROJECT_AND_PRODUCT_FROM_SERVER", 
        "Project + Product from server", 
        "Fetch project and product from server",
        "fa-server",
        false
    );
    
    public static final SetupMode PROJECT_ONLY_SVN = new SetupMode(
        "PROJECT_ONLY_SVN", 
        "Project only from SVN", 
        "Checkout project from SVN only",
        "fa-code-branch",
        false
    );
    
    public static final SetupMode PROJECT_ONLY_SERVER = new SetupMode(
        "PROJECT_ONLY_SERVER", 
        "Project only from server", 
        "Download project from server only",
        "fa-server",
        false
    );
    
    public static final SetupMode HAS_JAVA_MODE = new SetupMode(
        "HAS_JAVA_MODE", 
        "Has Java Mode (Resources + Exe Creation)", 
        "Java already extracted - load resources, create executables, and update build files",
        "fa-coffee",
        false
    );
    
    public static final SetupMode CUSTOM = new SetupMode(
        "CUSTOM", 
        "Custom setup", 
        "Define your own setup configuration",
        "fa-cog",
        false
    );
    
    public static final SetupMode CANCELLED = new SetupMode(
        "CANCELLED", 
        "Cancelled", 
        "",
        "",
        false
    );
    
    private SetupMode selectedMode = CANCELLED;
    private final List<SetupMode> availableModes;
    private final String dialogTitle;
    private final String dialogSubtitle;
    private Button selectedButton = null;
    
    public SetupModeDialog() {
        this("Local Setup / Upgrade", "Choose your setup mode", List.of(
            PATCH_UPGRADE,
            PRODUCT_ONLY,
            FULL_CHECKOUT,
            PROJECT_AND_PRODUCT_FROM_SERVER,
            PROJECT_ONLY_SVN,
            PROJECT_ONLY_SERVER,
            HAS_JAVA_MODE
        ));
    }
    
    public SetupModeDialog(String title, String subtitle, List<SetupMode> modes) {
        this.dialogTitle = title;
        this.dialogSubtitle = subtitle;
        this.availableModes = new ArrayList<>(modes);
    }
    
    public SetupMode showDialog(Stage parentStage) {
        // Create the dialog stage
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(parentStage);
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.setResizable(false);
        dialogStage.setTitle("Setup Mode Selection");
        
        // Create the main content
        VBox mainContent = createMainContent(dialogStage);
        
        // Create scene
        Scene scene = new Scene(mainContent);
        dialogStage.setScene(scene);
        
        // Set icon
        IconUtils.setStageIcon((javafx.stage.Stage) dialogStage);
        
        // Handle close button (X) click
        dialogStage.setOnCloseRequest(event -> {
            selectedMode = CANCELLED;
            dialogStage.close();
        });
        
        // Center on parent
        dialogStage.centerOnScreen();
        
        // Show and wait
        dialogStage.showAndWait();
        
        return selectedMode;
    }
    
    private VBox createMainContent(Stage dialogStage) {
        // Main container
        VBox mainContainer = new VBox(0);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 16;");
        mainContainer.setEffect(new DropShadow(20, Color.rgb(0, 0, 0, 0.15)));
        mainContainer.setMaxSize(600, 500);
        mainContainer.setMinSize(600, 500);
        
        // Header
        VBox header = createHeader();
        
        // Content with scrollbar
        ScrollPane scrollPane = createScrollableContent();
        
        // Footer
        HBox footer = createFooter(dialogStage);
        
        mainContainer.getChildren().addAll(header, scrollPane, footer);
        
        return mainContainer;
    }
    
    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(24, 24, 16, 24));
        header.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 16 16 0 0;");
        
        // Title
        Label titleLabel = new Label(dialogTitle);
        titleLabel.setFont(Font.font("Inter", FontWeight.BOLD, 20));
        titleLabel.setStyle("-fx-text-fill: #1e293b;");
        titleLabel.setAlignment(Pos.CENTER);
        
        // Subtitle
        Label subtitleLabel = new Label(dialogSubtitle);
        subtitleLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 14));
        subtitleLabel.setStyle("-fx-text-fill: #64748b;");
        subtitleLabel.setAlignment(Pos.CENTER);
        
        header.getChildren().addAll(titleLabel, subtitleLabel);
        return header;
    }
    
    private ScrollPane createScrollableContent() {
        VBox content = createContent();
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        // Custom scrollbar styling - inline for now
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; " +
            "-fx-scrollbar-color: #cbd5e1; " +
            "-fx-scrollbar-thumb-color: #94a3b8; " +
            "-fx-scrollbar-thumb-hover-color: #64748b;");
        
        return scrollPane;
    }
    
    private VBox createContent() {
        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20, 24, 20, 24));
        
        // Mode selection buttons
        VBox modeButtons = new VBox(12);
        modeButtons.setAlignment(Pos.CENTER);
        
        // Create buttons for each available mode
        for (SetupMode mode : availableModes) {
            Button modeButton = createModeButton(mode);
            modeButton.setOnAction(e -> {
                // Update selection
                selectedMode = mode;
                
                // Update button styles
                if (selectedButton != null) {
                    updateButtonStyle(selectedButton, false);
                }
                selectedButton = modeButton;
                updateButtonStyle(selectedButton, true);
            });
            modeButtons.getChildren().add(modeButton);
            
            // Set default selection
            if (mode.isDefault()) {
                selectedMode = mode;
                selectedButton = modeButton;
                updateButtonStyle(selectedButton, true);
            }
        }
        
        content.getChildren().add(modeButtons);
        
        return content;
    }
    
    private Button createModeButton(SetupMode mode) {
        VBox buttonContent = new VBox(4);
        buttonContent.setAlignment(Pos.CENTER_LEFT);
        buttonContent.setPadding(new Insets(16, 20, 16, 20));
        
        // Title
        Label titleLabel = new Label(mode.getTitle());
        titleLabel.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        titleLabel.setStyle("-fx-text-fill: #1e293b;");
        
        // Description
        Label descLabel = new Label(mode.getDescription());
        descLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 13));
        descLabel.setStyle("-fx-text-fill: #64748b;");
        descLabel.setWrapText(true);
        
        buttonContent.getChildren().addAll(titleLabel, descLabel);
        
        Button button = new Button();
        button.setGraphic(buttonContent);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(80);
        
        // Initial style
        updateButtonStyle(button, mode.isDefault());
        
        // Hover effects
        button.setOnMouseEntered(e -> {
            if (button != selectedButton) {
                button.setStyle(
                    "-fx-background-color: #f1f5f9; " +
                    "-fx-border-color: #cbd5e1; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 12; " +
                    "-fx-background-radius: 12; " +
                    "-fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 6, 0, 0, 2);"
                );
            }
        });
        
        button.setOnMouseExited(e -> {
            if (button != selectedButton) {
                button.setStyle(
                    "-fx-background-color: #f8fafc; " +
                    "-fx-border-color: #e2e8f0; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 12; " +
                    "-fx-background-radius: 12; " +
                    "-fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.05), 4, 0, 0, 1);"
                );
            }
        });
        
        return button;
    }
    
    private void updateButtonStyle(Button button, boolean isSelected) {
        if (isSelected) {
            button.setStyle(
                "-fx-background-color: #e0f7fa; " +
                "-fx-border-color: #1565c0; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.2), 8, 0, 0, 2);"
            );
        } else {
            button.setStyle(
                "-fx-background-color: #f8fafc; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.05), 4, 0, 0, 1);"
            );
        }
    }
    
    private HBox createFooter(Stage dialogStage) {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 24, 24, 24));
        footer.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 0 0 16 16;");
        
        // Cancel button
        Button cancelButton = new Button("Cancel");
        cancelButton.setFont(Font.font("Inter", FontWeight.NORMAL, 14));
        cancelButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-border-color: #cbd5e1; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: #64748b; " +
            "-fx-padding: 8 16; " +
            "-fx-cursor: hand;"
        );
        cancelButton.setOnAction(e -> {
            selectedMode = CANCELLED;
            dialogStage.close();
        });
        
        // Continue button
        Button continueButton = new Button("Continue");
        continueButton.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        continueButton.setStyle(
            "-fx-background-color: #1565c0; " +
            "-fx-border-color: #1565c0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 8 20; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.3), 4, 0, 0, 2);"
        );
        continueButton.setOnAction(e -> dialogStage.close());
        
        footer.getChildren().addAll(cancelButton, continueButton);
        return footer;
    }
    
    /**
     * Create a custom setup mode dialog with specific modes
     */
    public static SetupModeDialog createCustom(String title, String subtitle, List<SetupMode> modes) {
        return new SetupModeDialog(title, subtitle, modes);
    }
    
    /**
     * Create a setup mode dialog with only specific modes
     */
    public static SetupModeDialog createWithModes(List<SetupMode> modes) {
        return new SetupModeDialog("Setup Mode Selection", "Choose your setup mode", modes);
    }
}
