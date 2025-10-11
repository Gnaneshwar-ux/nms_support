package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced environment variable selection dialog with build file parsing
 */
public class EnhancedEnvironmentVariableDialog {
    
    private Stage dialogStage;
    private ComboBox<String> envVarComboBox;
    private TextField customEnvVarField;
    private Label statusLabel;
    private Button okButton;
    private Button cancelButton;
    private CompletableFuture<Optional<EnvVarReplacement>> resultFuture;
    private String projectDefaultValue; // Store the project's default value
    
    // Professional typography constants
    private static final String PROFESSIONAL_FONT_FAMILY = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
    private static final String MODERN_STYLE = String.format(
        "-fx-font-family: %s; -fx-font-size: 14px; -fx-background-color: #FFFFFF;",
        PROFESSIONAL_FONT_FAMILY
    );
    
    public EnhancedEnvironmentVariableDialog() {
        createDialog();
    }
    
    private void createDialog() {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(false);
        dialogStage.setTitle("ENV INPUT");
        dialogStage.setMinWidth(450);
        dialogStage.setMinHeight(250);
        
        // Set app icon
        IconUtils.setStageIcon(dialogStage);
        
        // Main container - more compact
        VBox mainContainer = new VBox(16);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle(MODERN_STYLE + "-fx-background-radius: 8;");
        mainContainer.setEffect(new javafx.scene.effect.DropShadow(8, Color.rgb(0, 0, 0, 0.1)));
        
        // Content section
        VBox contentSection = createContentSection();
        
        // Status section
        statusLabel = new Label("Loading environment variables...");
        statusLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #6B7280;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Footer buttons
        HBox footer = createFooter();
        
        mainContainer.getChildren().addAll(contentSection, statusLabel, footer);
        
        Scene scene = new Scene(mainContainer);
        scene.getRoot().setStyle(MODERN_STYLE);
        dialogStage.setScene(scene);
        
        // Handle close button (X) click
        dialogStage.setOnCloseRequest(event -> {
            if (resultFuture != null && !resultFuture.isDone()) {
                resultFuture.complete(Optional.empty());
            }
            dialogStage.close();
        });
    }
    
    
    private VBox createContentSection() {
        VBox contentSection = new VBox(16);
        contentSection.setAlignment(Pos.CENTER);
        
        // ComboBox for environment variables to replace
        VBox comboBoxSection = new VBox(6);
        Label comboBoxLabel = new Label("Select env var to replace:");
        comboBoxLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #374151;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        Label comboBoxSubLabel = new Label("(Note: values loaded from current build files)");
        comboBoxSubLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #6B7280; -fx-font-style: italic;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        envVarComboBox = new ComboBox<>();
        envVarComboBox.setPromptText("Choose environment variable to replace...");
        envVarComboBox.setPrefWidth(350);
        envVarComboBox.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 13px; -fx-background-color: #FFFFFF; -fx-border-color: #D1D5DB; -fx-border-radius: 4; -fx-padding: 8 12;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Input field for new environment variable name
        VBox mainFieldSection = new VBox(6);
        Label mainFieldLabel = new Label("Enter env var name to be replace with:");
        mainFieldLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #374151;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        customEnvVarField = new TextField();
        customEnvVarField.setPromptText("Enter new environment variable name...");
        customEnvVarField.setPrefWidth(350);
        customEnvVarField.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 13px; -fx-background-color: #FFFFFF; -fx-border-color: #D1D5DB; -fx-border-radius: 4; -fx-padding: 8 12;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Set up event handlers
        envVarComboBox.setOnAction(e -> {
            updateOkButtonState();
        });
        
        // Listen for selection changes in combo box
        envVarComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateOkButtonState();
        });
        
        customEnvVarField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateOkButtonState();
        });
        
        comboBoxSection.getChildren().addAll(comboBoxLabel, comboBoxSubLabel, envVarComboBox);
        mainFieldSection.getChildren().addAll(mainFieldLabel, customEnvVarField);
        
        contentSection.getChildren().addAll(comboBoxSection, mainFieldSection);
        
        return contentSection;
    }
    
    private HBox createFooter() {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        
        okButton = new Button("OK");
        okButton.setDefaultButton(true);
        okButton.setPrefWidth(80);
        okButton.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-color: #3B82F6; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 10 18; -fx-cursor: hand;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setPrefWidth(80);
        cancelButton.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-color: #6B7280; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 10 18; -fx-cursor: hand;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        footer.getChildren().addAll(cancelButton, okButton);
        return footer;
    }
    
    public CompletableFuture<Optional<EnvVarReplacement>> showDialog(Stage parentStage, ProjectEntity project, String defaultValue, boolean isProductReplace) {
        resultFuture = new CompletableFuture<>();
        
        // Center on parent
        if (parentStage != null) {
            dialogStage.initOwner(parentStage);
            dialogStage.centerOnScreen();
        }
        
        // Set default value from project entity
        String projectEnvVar = project.getNmsEnvVar();
        projectDefaultValue = projectEnvVar; // Store for later use
        if (projectEnvVar != null && !projectEnvVar.trim().isEmpty()) {
            customEnvVarField.setText(projectEnvVar);
        } else {
            customEnvVarField.setText(""); // Keep field empty if project env var is null
        }
        
        // Load environment variables from appropriate build files
        loadEnvironmentVariablesFromBuildFiles(project, isProductReplace);
        
        // Setup event handlers
        setupEventHandlers();
        
        // Initial state - OK button should be disabled
        updateOkButtonState();
        
        // Show dialog
        dialogStage.show();
        
        return resultFuture;
    }
    
    private void loadEnvironmentVariablesFromBuildFiles(ProjectEntity project, boolean isProductReplace) {
        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                // First validate that required files exist
                List<String> missingFiles = BuildFileParser.validateBuildFiles(project, isProductReplace);
                
                if (!missingFiles.isEmpty()) {
                    Platform.runLater(() -> {
                        String fileType = isProductReplace ? "product" : "project";
                        StringBuilder errorMessage = new StringBuilder();
                        errorMessage.append("Required ").append(fileType).append(" build files are missing:\n\n");
                        for (String missingFile : missingFiles) {
                            errorMessage.append("• ").append(missingFile).append("\n");
                        }
                        errorMessage.append("\nPlease ensure these files exist before proceeding with replacement.");
                        
                        statusLabel.setText(errorMessage.toString());
                        statusLabel.setStyle(String.format(
                            "-fx-font-family: %s; -fx-font-size: 13px; -fx-text-fill: #EF4444;",
                            PROFESSIONAL_FONT_FAMILY
                        ));
                        
                        // Disable OK button if files are missing
                        okButton.setDisable(true);
                    });
                    return;
                }
                
                List<String> envVars;
                String fileType;
                
                if (isProductReplace) {
                    // Load from product build files (java/ant/build.xml and build.properties)
                    envVars = BuildFileParser.parseEnvironmentVariablesFromProduct(project);
                    fileType = "product build files";
                } else {
                    // Load from project build files (jconfig/build.xml and build.properties)
                    envVars = BuildFileParser.parseEnvironmentVariablesFromJconfig(project);
                    fileType = "project build files";
                }
                
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    try {
                        ObservableList<String> observableList = FXCollections.observableArrayList(envVars);
                        envVarComboBox.setItems(observableList);
                        
                        if (!envVars.isEmpty()) {
                            // Select first item (which should be a HOME variable if available)
                            envVarComboBox.getSelectionModel().select(0);
                            // Only set the text field if it's empty or if project default is null
                            if (customEnvVarField.getText().trim().isEmpty() || projectDefaultValue == null) {
                                customEnvVarField.setText(envVars.get(0));
                            }
                        }
                        
                        statusLabel.setText(String.format("Found %d environment variables in %s", envVars.size(), fileType));
                        statusLabel.setStyle(String.format(
                            "-fx-font-family: %s; -fx-font-size: 13px; -fx-text-fill: #10B981;",
                            PROFESSIONAL_FONT_FAMILY
                        ));
                        
                    } catch (Exception e) {
                        statusLabel.setText("Error loading environment variables: " + e.getMessage());
                        statusLabel.setStyle(String.format(
                            "-fx-font-family: %s; -fx-font-size: 13px; -fx-text-fill: #EF4444;",
                            PROFESSIONAL_FONT_FAMILY
                        ));
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error parsing build files: " + e.getMessage());
                    statusLabel.setStyle(String.format(
                        "-fx-font-family: %s; -fx-font-size: 13px; -fx-text-fill: #EF4444;",
                        PROFESSIONAL_FONT_FAMILY
                    ));
                });
            }
        }, "build-file-parser").start();
    }
    
    private void updateOkButtonState() {
        // Return early if button is not yet created
        if (okButton == null) {
            return;
        }
        
        String oldValue = envVarComboBox.getSelectionModel().getSelectedItem();
        String newValue = customEnvVarField.getText();
        
        // Both fields must have values
        boolean hasOldValue = oldValue != null && !oldValue.trim().isEmpty();
        boolean hasNewValue = newValue != null && !newValue.trim().isEmpty();
        
        okButton.setDisable(!(hasOldValue && hasNewValue));
        
        // Update button style based on state
        if (okButton.isDisabled()) {
            okButton.setStyle(String.format(
                "-fx-font-family: %s; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-color: #D1D5DB; -fx-text-fill: #9CA3AF; -fx-background-radius: 6; -fx-padding: 10 18; -fx-cursor: default;",
                PROFESSIONAL_FONT_FAMILY
            ));
        } else {
            okButton.setStyle(String.format(
                "-fx-font-family: %s; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-color: #3B82F6; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 10 18; -fx-cursor: hand;",
                PROFESSIONAL_FONT_FAMILY
            ));
        }
    }
    
    private void setupEventHandlers() {
        // OK button
        okButton.setOnAction(e -> {
            String oldValue = envVarComboBox.getSelectionModel().getSelectedItem();
            String newValue = customEnvVarField.getText().trim();
            
            // Double-check validation (should not happen due to button state, but safety check)
            if (oldValue != null && !oldValue.trim().isEmpty() && !newValue.isEmpty()) {
                EnvVarReplacement replacement = new EnvVarReplacement(oldValue, newValue);
                resultFuture.complete(Optional.of(replacement));
                dialogStage.close();
            }
        });
        
        // Cancel button
        cancelButton.setOnAction(e -> {
            resultFuture.complete(Optional.empty());
            dialogStage.close();
        });
        
    }
    
    /**
     * Data class to hold environment variable replacement information
     */
    public static class EnvVarReplacement {
        private final String oldValue;
        private final String newValue;
        
        public EnvVarReplacement(String oldValue, String newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        
        public String getOldValue() {
            return oldValue;
        }
        
        public String getNewValue() {
            return newValue;
        }
        
        @Override
        public String toString() {
            return "EnvVarReplacement{oldValue='" + oldValue + "', newValue='" + newValue + "'}";
        }
    }
}
