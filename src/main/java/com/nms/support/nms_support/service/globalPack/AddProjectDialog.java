package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import javax.lang.model.SourceVersion;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Custom dialog for adding new projects with improved layout and styling
 */
public class AddProjectDialog {
    private static final Logger logger = LoggerUtil.getLogger();

    private static final String PROJECT_NAME_RULES =
            "Allowed: letters, digits, '_' only. Must start with a letter or '_'. No spaces/special symbols.";
    
    private final Stage dialogStage;
    private TextField projectNameField;
    private Label noteLabel;
    private Button okButton;
    private Button cancelButton;
    private CompletableFuture<Optional<String>> resultFuture;
    
    public AddProjectDialog() {
        this.dialogStage = new Stage();
        initializeDialog();
        createContent();
    }
    
    private void initializeDialog() {
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Add Project");
        dialogStage.setResizable(false);
        dialogStage.setAlwaysOnTop(true);
        
        // Set the standard dialog icon
        IconUtils.setStageIcon(dialogStage);
    }
    
    private void createContent() {
        // Create main container with professional styling
        VBox rootContainer = new VBox(20);
        rootContainer.setPadding(new Insets(24, 24, 24, 24));
        rootContainer.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 12; " +
            "-fx-background-radius: 12; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 10, 0, 0, 4);"
        );
        
        // Create title with proper spacing
        Label titleLabel = new Label("Add New Project");
        titleLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #1e293b;");
        
        // Create description with proper spacing
        Label descriptionLabel = new Label("Enter a name for the new project:");
        descriptionLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 14));
        descriptionLabel.setStyle("-fx-text-fill: #64748b;");
        
        // Create input field with proper styling
        projectNameField = new TextField();
        projectNameField.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 14));
        projectNameField.setPrefWidth(320);
        projectNameField.setMaxWidth(320);
        projectNameField.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #d1d5db; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 12 16; " +
            "-fx-min-height: 44; " +
            "-fx-text-fill: #374151;"
        );
        
        // Add focus styling
        projectNameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                projectNameField.setStyle(projectNameField.getStyle() + 
                    "-fx-border-color: #3b82f6; " +
                    "-fx-border-width: 2;");
            } else {
                // border is controlled by validation; no-op here
            }
        });

        // Static note label (keeps dialog stable; no dynamic sizing)
        noteLabel = new Label(PROJECT_NAME_RULES);
        noteLabel.setWrapText(true);
        noteLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 12));
        noteLabel.setStyle("-fx-text-fill: #6b7280;");
        
        // Create buttons with proper styling
        cancelButton = new Button("Cancel");
        cancelButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 14));
        cancelButton.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #cbd5e1; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: #64748b; " +
            "-fx-padding: 10 20; " +
            "-fx-min-width: 80; " +
            "-fx-min-height: 40; " +
            "-fx-cursor: hand;"
        );
        
        okButton = new Button("OK");
        okButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 14));
        okButton.setStyle(
            "-fx-background-color: #3b82f6; " +
            "-fx-border-color: #3b82f6; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 24; " +
            "-fx-min-width: 80; " +
            "-fx-min-height: 40; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 4, 0, 0, 2);"
        );
        
        // Add hover effects
        setupButtonHoverEffects();
        
        // Create button container with proper spacing
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(16, 0, 0, 0));
        buttonBox.getChildren().addAll(cancelButton, okButton);
        
        // Create input container with proper spacing
        VBox inputContainer = new VBox(8);
        inputContainer.getChildren().addAll(descriptionLabel, projectNameField, noteLabel);
        
        // Add all components to root with proper spacing
        rootContainer.getChildren().addAll(titleLabel, inputContainer, buttonBox);
        
        // Set up the scene
        Scene scene = new Scene(rootContainer);
        dialogStage.setScene(scene);
        
        // Set up button actions
        setupButtonActions();

        // Setup inline validation (disable OK until valid)
        setupInlineValidation();
        
        // Set up keyboard shortcuts
        setupKeyboardShortcuts();
    }
    
    private void setupButtonHoverEffects() {
        // Cancel button hover effect
        cancelButton.setOnMouseEntered(e -> {
            cancelButton.setStyle(
                "-fx-background-color: #f8fafc; " +
                "-fx-border-color: #94a3b8; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-text-fill: #475569; " +
                "-fx-padding: 10 20; " +
                "-fx-min-width: 80; " +
                "-fx-min-height: 40; " +
                "-fx-cursor: hand;"
            );
        });
        
        cancelButton.setOnMouseExited(e -> {
            cancelButton.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-text-fill: #64748b; " +
                "-fx-padding: 10 20; " +
                "-fx-min-width: 80; " +
                "-fx-min-height: 40; " +
                "-fx-cursor: hand;"
            );
        });
        
        // OK button hover effect
        okButton.setOnMouseEntered(e -> {
            okButton.setStyle(
                "-fx-background-color: #2563eb; " +
                "-fx-border-color: #2563eb; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 10 24; " +
                "-fx-min-width: 80; " +
                "-fx-min-height: 40; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(37, 99, 235, 0.4), 6, 0, 0, 3);"
            );
        });
        
        okButton.setOnMouseExited(e -> {
            okButton.setStyle(
                "-fx-background-color: #3b82f6; " +
                "-fx-border-color: #3b82f6; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 10 24; " +
                "-fx-min-width: 80; " +
                "-fx-min-height: 40; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 4, 0, 0, 2);"
            );
        });
    }
    
    private void setupButtonActions() {
        okButton.setOnAction(e -> {
            String projectName = projectNameField.getText().trim();

            // Button is disabled when invalid; keep a final guard anyway.
            if (!isProjectNameValid(projectName)) return;

            resultFuture.complete(Optional.of(projectName));
            dialogStage.close();
        });
        
        cancelButton.setOnAction(e -> {
            resultFuture.complete(Optional.empty());
            dialogStage.close();
        });
    }

    private void setupInlineValidation() {
        // Initial state
        validateAndUpdateUI(projectNameField.getText());

        projectNameField.textProperty().addListener((obs, oldVal, newVal) -> validateAndUpdateUI(newVal));
    }

    private void validateAndUpdateUI(String text) {
        String raw = text == null ? "" : text;
        String candidate = raw.trim();

        // Treat empty as neutral (disabled button, default border)
        if (candidate.isEmpty()) {
            okButton.setDisable(true);
            setTextFieldBorder(projectNameField, "#d1d5db", 1);
            return;
        }

        boolean valid = isProjectNameValid(candidate);
        okButton.setDisable(!valid);

        if (valid) {
            setTextFieldBorder(projectNameField, "#16a34a", 2);
        } else {
            setTextFieldBorder(projectNameField, "#ef4444", 2);
        }
    }

    /**
     * Java-identifier style validation (no spaces/special symbols).
     * Disallows '$' even though Java allows it.
     */
    private boolean isProjectNameValid(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String trimmed = name.trim();
        if (trimmed.indexOf('$') >= 0) return false;
        if (!SourceVersion.isIdentifier(trimmed)) return false;
        return !SourceVersion.isKeyword(trimmed);
    }

    private void setTextFieldBorder(TextField field, String color, int width) {
        field.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-border-color: " + color + "; " +
                        "-fx-border-width: " + width + "; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6; " +
                        "-fx-padding: 12 16; " +
                        "-fx-min-height: 44; " +
                        "-fx-text-fill: #374151;"
        );
    }
    
    private void setupKeyboardShortcuts() {
        // Enter key submits the dialog
        projectNameField.setOnAction(e -> okButton.fire());
        
        // Escape key cancels the dialog
        dialogStage.getScene().setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                cancelButton.fire();
            }
        });
    }
    
    /**
     * Shows the dialog and returns a CompletableFuture with the result
     * @param owner The owner window for the dialog
     * @return CompletableFuture containing the project name or empty if cancelled
     */
    public CompletableFuture<Optional<String>> showDialog(Window owner) {
        this.resultFuture = new CompletableFuture<>();
        
        if (owner != null) {
            dialogStage.initOwner(owner);
        }
        
        // Focus the text field when dialog is shown
        Platform.runLater(() -> {
            projectNameField.requestFocus();
        });
        
        dialogStage.showAndWait();
        return resultFuture;
    }
}
