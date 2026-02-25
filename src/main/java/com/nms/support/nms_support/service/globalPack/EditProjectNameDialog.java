package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.userdata.ProjectManager;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.lang.model.SourceVersion;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Dialog for editing project name
 */
public class EditProjectNameDialog {
    private static final Logger logger = LoggerUtil.getLogger();

    private static final String PROJECT_NAME_RULES =
            "Allowed: letters, digits, '_' only. Must start with a letter or '_'. No spaces/special symbols.";
    
    private Stage dialogStage;
    private ProjectManager projectManager;
    private String currentProjectName;
    private CompletableFuture<Optional<String>> resultFuture;
    
    // UI components
    private Label titleLabel;
    private TextField projectNameField;
    private Label noteLabel;
    private Button updateButton;
    private Button cancelButton;
    
    public EditProjectNameDialog() {
        // Default constructor
    }
    
    /**
     * Shows the edit project name dialog and returns a CompletableFuture with the new project name
     * @param parentWindow The parent window for the dialog
     * @param projectManager The project manager instance
     * @param currentProjectName The current project name
     * @return CompletableFuture containing the new project name or empty if cancelled
     */
    public CompletableFuture<Optional<String>> showDialog(Window parentWindow, 
                                                         ProjectManager projectManager, 
                                                         String currentProjectName) {
        this.projectManager = projectManager;
        this.currentProjectName = currentProjectName;
        this.resultFuture = new CompletableFuture<>();
        
        try {
            // Create the dialog content programmatically
            VBox root = createDialogContent();
            
            // Create and configure the dialog stage
            dialogStage = new Stage();
            dialogStage.initOwner(parentWindow);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Edit Project Name");
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);

            // Let the stage size itself to the content to avoid button overflow.
            dialogStage.sizeToScene();
            dialogStage.setMinWidth(380);
            dialogStage.setMinHeight(240);
            
            // Set the standard dialog icon
            IconUtils.setStageIcon(dialogStage);
            
            // Set up button actions and hover effects
            setupButtonActions();
            setupButtonHoverEffects();
            
            // Show the dialog
            dialogStage.showAndWait();
            
        } catch (Exception e) {
            logger.severe("Error creating edit project name dialog: " + e.getMessage());
            resultFuture.complete(Optional.empty());
        }
        
        return resultFuture;
    }
    
    /**
     * Creates the dialog content programmatically
     */
    private VBox createDialogContent() {
        // Create main container with compact styling
        VBox rootContainer = new VBox(18);
        rootContainer.setPadding(new javafx.geometry.Insets(24, 24, 24, 24));
        rootContainer.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 8, 0, 0, 2);"
        );
        
        // Create compact title
        titleLabel = new Label("Edit Project Name");
        titleLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 16));
        titleLabel.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: normal;");
        
        // Create compact input field
        projectNameField = new TextField(currentProjectName);
        projectNameField.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 16));
        projectNameField.setPrefWidth(320);
        projectNameField.setMaxWidth(320);
        projectNameField.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #d1d5db; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 10 12; " +
            "-fx-min-height: 40; " +
            "-fx-font-weight: bold;"
        );
        
        // Select all text for easy editing
        projectNameField.selectAll();

        // Static note label (keeps dialog stable)
        noteLabel = new Label(PROJECT_NAME_RULES);
        noteLabel.setWrapText(true);
        noteLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        
        // Create compact buttons
        cancelButton = new Button("Cancel");
        cancelButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 14));
        cancelButton.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #d1d5db; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-text-fill: #374151; " +
            "-fx-min-width: 90; " +
            "-fx-min-height: 40; " +
            "-fx-pref-height: 40; " +
            "-fx-cursor: hand; " +
            "-fx-font-weight: normal;"
        );
        
        updateButton = new Button("Update");
        updateButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 14));
        updateButton.setStyle(
            "-fx-background-color: #1565c0; " +
            "-fx-border-color: #1565c0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-text-fill: #ffffff; " +
            "-fx-min-width: 90; " +
            "-fx-min-height: 40; " +
            "-fx-pref-height: 40; " +
            "-fx-cursor: hand; " +
            "-fx-font-weight: normal; " +
            "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.3), 3, 0, 0, 1);"
        );
        
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttonBox.setPadding(new javafx.geometry.Insets(20, 0, 0, 0));
        buttonBox.getChildren().addAll(cancelButton, updateButton);
        
        // Add all components to root
        rootContainer.getChildren().addAll(titleLabel, projectNameField, noteLabel, buttonBox);
        
        return rootContainer;
    }
    
    /**
     * Sets up button actions
     */
    private void setupButtonActions() {
        updateButton.setOnAction(event -> updateProjectName());
        cancelButton.setOnAction(event -> cancelDialog());
        
        // Handle enter key to update
        projectNameField.setOnAction(event -> updateProjectName());

        // Inline validation while typing
        setupInlineValidation();
        
        // Handle escape key
        dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                cancelDialog();
            }
        });
    }

    private void setupInlineValidation() {
        validateAndUpdateUI(projectNameField.getText());
        projectNameField.textProperty().addListener((obs, oldVal, newVal) -> validateAndUpdateUI(newVal));
    }

    private void validateAndUpdateUI(String text) {
        String raw = text == null ? "" : text;
        String candidate = raw.trim();

        if (candidate.isEmpty()) {
            updateButton.setDisable(true);
            setTextFieldBorder(projectNameField, "#d1d5db", 1);
            return;
        }

        boolean valid = isProjectNameValid(candidate);
        if (!valid) {
            updateButton.setDisable(true);
            setTextFieldBorder(projectNameField, "#ef4444", 2);
            return;
        }

        // valid
        boolean changed = currentProjectName != null && !candidate.equals(currentProjectName);
        updateButton.setDisable(!changed);
        setTextFieldBorder(projectNameField, "#16a34a", 2);
    }

    private void setTextFieldBorder(TextField field, String color, int width) {
        field.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-border-color: " + color + "; " +
                        "-fx-border-width: " + width + "; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6; " +
                        "-fx-padding: 10 12; " +
                        "-fx-min-height: 40; " +
                        "-fx-font-weight: bold;"
        );
    }
    
    /**
     * Sets up professional hover effects for buttons
     */
    private void setupButtonHoverEffects() {
        // Cancel button hover effects
        cancelButton.setOnMouseEntered(event -> {
            if (!cancelButton.isDisabled()) {
                cancelButton.setStyle(
                    "-fx-background-color: #f8fafc; " +
                    "-fx-border-color: #94a3b8; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-background-radius: 6; " +
                    "-fx-text-fill: #1f2937; " +
                    "-fx-min-width: 90; " +
                    "-fx-min-height: 40; " +
                    "-fx-pref-height: 40; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: normal;"
                );
            }
        });
        
        cancelButton.setOnMouseExited(event -> {
            if (!cancelButton.isDisabled()) {
                cancelButton.setStyle(
                    "-fx-background-color: #ffffff; " +
                    "-fx-border-color: #d1d5db; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-background-radius: 6; " +
                    "-fx-text-fill: #374151; " +
                    "-fx-min-width: 90; " +
                    "-fx-min-height: 40; " +
                    "-fx-pref-height: 40; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: normal;"
                );
            }
        });
        
        // Update button hover effects
        updateButton.setOnMouseEntered(event -> {
            if (!updateButton.isDisabled()) {
                updateButton.setStyle(
                    "-fx-background-color: #0d47a1; " +
                    "-fx-border-color: #0d47a1; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-background-radius: 6; " +
                    "-fx-text-fill: #ffffff; " +
                    "-fx-min-width: 90; " +
                    "-fx-min-height: 40; " +
                    "-fx-pref-height: 40; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: normal; " +
                    "-fx-effect: dropshadow(gaussian, rgba(13, 71, 161, 0.4), 4, 0, 0, 2);"
                );
            }
        });
        
        updateButton.setOnMouseExited(event -> {
            if (!updateButton.isDisabled()) {
                updateButton.setStyle(
                    "-fx-background-color: #1565c0; " +
                    "-fx-border-color: #1565c0; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-background-radius: 6; " +
                    "-fx-text-fill: #ffffff; " +
                    "-fx-min-width: 90; " +
                    "-fx-min-height: 40; " +
                    "-fx-pref-height: 40; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: normal; " +
                    "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.3), 3, 0, 0, 1);"
                );
            }
        });
    }
    
    /**
     * Updates the project name and closes the dialog
     */
    private void updateProjectName() {
        String newProjectName = projectNameField.getText().trim();
        
        // Button is disabled when invalid; keep a final guard.
        if (!isProjectNameValid(newProjectName)) return;
        
        if (newProjectName.equals(currentProjectName)) {
            // No change, just close
            resultFuture.complete(Optional.empty());
            dialogStage.close();
            return;
        }
        
        // Check if project name already exists
        if (projectManager.projectExists(newProjectName)) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "Name Already Exists", 
                "A project with the name '" + newProjectName + "' already exists. Please choose a different name.");
            return;
        }
        
        logger.info("Updating project name from '" + currentProjectName + "' to '" + newProjectName + "'");
        resultFuture.complete(Optional.of(newProjectName));
        dialogStage.close();
    }
    
    /**
     * Cancels the dialog without updating
     */
    private void cancelDialog() {
        logger.info("Edit project name dialog cancelled");
        resultFuture.complete(Optional.empty());
        dialogStage.close();
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
}
