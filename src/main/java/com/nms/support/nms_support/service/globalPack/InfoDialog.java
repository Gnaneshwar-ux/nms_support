package com.nms.support.nms_support.service.globalPack;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Information dialog displaying DevTools version and keyboard shortcuts
 */
public class InfoDialog {
    private final Stage dialogStage;
    
    public InfoDialog() {
        this.dialogStage = new Stage();
        initializeDialog();
        createContent();
    }
    
    private void initializeDialog() {
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("NMS DevTools - Information");
        dialogStage.setResizable(false);
        dialogStage.setAlwaysOnTop(true);
        
        // Set the standard dialog icon
        IconUtils.setStageIcon(dialogStage);
    }
    
    private void createContent() {
        // Create main container with professional styling - reduced sizes
        VBox rootContainer = new VBox(18);
        rootContainer.setPadding(new Insets(24, 28, 20, 28));
        rootContainer.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8fafc, #ffffff); " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 12; " +
            "-fx-background-radius: 12; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.10), 10, 0, 0, 3);"
        );
        
        // Create title section - reduced sizes
        VBox titleSection = new VBox(6);
        titleSection.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label("NMS DevTools");
        titleLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 20));
        titleLabel.setStyle("-fx-text-fill: #1e293b;");
        
        // Get version information
        String version = AppDetails.getApplicationVersion();
        Label versionLabel = new Label("Version: " + version);
        versionLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.MEDIUM, 14));
        versionLabel.setStyle("-fx-text-fill: #64748b;");
        
        // Developer credit
        Label devLabel = new Label("Forged by Gnaneshwar Gurram | Stewarded by Visnu Anumolu");
        devLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 12));
        devLabel.setStyle("-fx-text-fill: #94a3b8;");
        
        titleSection.getChildren().addAll(titleLabel, versionLabel, devLabel);
        
        // Create separator
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #e2e8f0; -fx-pref-width: 100%;");
        
        // Create shortcuts section - reduced sizes
        VBox shortcutsSection = new VBox(12);
        
        Label shortcutsTitle = new Label("Keyboard Shortcuts");
        shortcutsTitle.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 15));
        shortcutsTitle.setStyle("-fx-text-fill: #1e293b;");
        
        // Create shortcuts grid - reduced gaps
        GridPane shortcutsGrid = new GridPane();
        shortcutsGrid.setHgap(16);
        shortcutsGrid.setVgap(10);
        shortcutsGrid.setPadding(new Insets(6, 0, 0, 0));
        
        // Add shortcuts with styling
        addShortcutRow(shortcutsGrid, 0, "Ctrl + S", "Save changes");
        addShortcutRow(shortcutsGrid, 1, "Ctrl + K", "Open NMS DevTools log file");
        addShortcutRow(shortcutsGrid, 2, "Ctrl + L", "Open recent NMS log of current selected project");
        addShortcutRow(shortcutsGrid, 3, "Ctrl + D", "Open command prompt at JConfig path");
        addShortcutRow(shortcutsGrid, 4, "Ctrl + Shift + L", "Open OracleNMS log directory");
        addShortcutRow(shortcutsGrid, 5, "Ctrl + R", "Restart application (Application Management tab)");
        addShortcutRow(shortcutsGrid, 6, "Ctrl + E", "Edit default Cline workflow template");
        
        shortcutsSection.getChildren().addAll(shortcutsTitle, shortcutsGrid);
        
        // Create close button - reduced size
        Button closeButton = new Button("Close");
        closeButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 12));
        closeButton.setStyle(
            "-fx-background-color: #3b82f6; " +
            "-fx-border-color: #3b82f6; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 8 24; " +
            "-fx-min-width: 100; " +
            "-fx-min-height: 32; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.25), 3, 0, 0, 1);"
        );
        
        // Add hover effects
        closeButton.setOnMouseEntered(e -> {
            closeButton.setStyle(
                "-fx-background-color: #2563eb; " +
                "-fx-border-color: #2563eb; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 8 24; " +
                "-fx-min-width: 100; " +
                "-fx-min-height: 32; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(37, 99, 235, 0.35), 5, 0, 0, 2);"
            );
        });
        
        closeButton.setOnMouseExited(e -> {
            closeButton.setStyle(
                "-fx-background-color: #3b82f6; " +
                "-fx-border-color: #3b82f6; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 8 24; " +
                "-fx-min-width: 100; " +
                "-fx-min-height: 32; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.25), 3, 0, 0, 1);"
            );
        });
        
        closeButton.setOnAction(e -> dialogStage.close());
        
        // Button container - reduced padding
        HBox buttonContainer = new HBox();
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setPadding(new Insets(12, 0, 0, 0));
        buttonContainer.getChildren().add(closeButton);
        
        // Add all components to root
        rootContainer.getChildren().addAll(titleSection, separator, shortcutsSection, buttonContainer);
        
        // Set up the scene
        Scene scene = new Scene(rootContainer);
        dialogStage.setScene(scene);
        
        // Set up keyboard shortcuts
        setupKeyboardShortcuts();
    }
    
    private void addShortcutRow(GridPane grid, int row, String shortcut, String description) {
        // Shortcut key label - reduced size
        Label shortcutLabel = new Label(shortcut);
        shortcutLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 11));
        shortcutLabel.setStyle(
            "-fx-text-fill: #3b82f6; " +
            "-fx-background-color: #eff6ff; " +
            "-fx-padding: 3 10; " +
            "-fx-border-color: #bfdbfe; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5; " +
            "-fx-min-width: 120;"
        );
        shortcutLabel.setAlignment(Pos.CENTER);
        
        // Description label - reduced size
        Label descLabel = new Label(description);
        descLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 11));
        descLabel.setStyle("-fx-text-fill: #475569;");
        
        grid.add(shortcutLabel, 0, row);
        grid.add(descLabel, 1, row);
    }
    
    private void setupKeyboardShortcuts() {
        // Escape key or Enter key closes the dialog
        dialogStage.getScene().setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE || 
                e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                dialogStage.close();
            }
        });
    }
    
    /**
     * Shows the dialog
     * @param owner The owner window for the dialog
     */
    public void showDialog(Window owner) {
        if (owner != null) {
            dialogStage.initOwner(owner);
        }
        
        dialogStage.showAndWait();
    }
}
