package com.nms.support.nms_support.service.globalPack;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Dialog to edit an Environment Variable's value (expected to be a path).
 * - Prefills existing value
 * - Provides folder browse button
 * - Returns new value on Update
 */
public class EditEnvVarValueDialog {

    private static final Logger logger = LoggerUtil.getLogger();

    private Stage dialogStage;
    private TextField pathField;
    private Button browseButton;
    private Button updateButton;
    private Button cancelButton;
    private Label titleLabel;
    private Label varNameLabel;

    private CompletableFuture<Optional<String>> resultFuture;

    public EditEnvVarValueDialog() {
        // default
    }

    /**
     * Show the dialog and return a future with the updated value if user clicks Update.
     * @param parentStage parent window
     * @param varName environment variable name (display only)
     * @param currentValue current value (prefill)
     * @return future with Optional value
     */
    public CompletableFuture<Optional<String>> showDialog(Stage parentStage, String varName, String currentValue) {
        resultFuture = new CompletableFuture<>();
        try {
            VBox root = createDialogContent(varName, currentValue);

            dialogStage = new Stage();
            if (parentStage != null) dialogStage.initOwner(parentStage);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Edit Environment Variable Value");
            dialogStage.setResizable(false);
            dialogStage.setScene(new Scene(root));
            dialogStage.setMinWidth(520);
            dialogStage.setMinHeight(220);

            IconUtils.setStageIcon(dialogStage);

            setupActions(varName);

            // Use show() to avoid blocking FX thread; result is delivered via CompletableFuture
            dialogStage.show();

        } catch (Exception e) {
            logger.severe("Error creating EditEnvVarValueDialog: " + e.getMessage());
            resultFuture.complete(Optional.empty());
        }
        return resultFuture;
    }

    private VBox createDialogContent(String varName, String currentValue) {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);"
        );

        titleLabel = new Label("Edit Environment Variable Value");
        titleLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 16px; -fx-text-fill: #1e293b;");

        varNameLabel = new Label("Variable: " + (varName != null ? varName : ""));
        varNameLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: #475569;");

        Label pathLabel = new Label("Path value:");
        pathLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: #374151; -fx-font-weight: 600;");

        HBox pathBox = new HBox(8);
        pathBox.setAlignment(Pos.CENTER_LEFT);

        pathField = new TextField(currentValue != null ? currentValue : "");
        pathField.setPromptText("Enter or browse to the folder path...");
        pathField.setStyle(
            "-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-padding: 8 10; -fx-font-family: 'Inter'; -fx-font-size: 12px;"
        );
        HBox.setHgrow(pathField, Priority.ALWAYS);

        browseButton = new Button("Browse");
        browseButton.setStyle(
            "-fx-background-color: #f3f4f6; -fx-border-color: #cbd5e1; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-text-fill: #111827; -fx-padding: 6 12;"
        );

        pathBox.getChildren().addAll(pathField, browseButton);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        cancelButton = new Button("Cancel");
        cancelButton.setStyle(
            "-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-text-fill: #374151; -fx-padding: 8 14;"
        );

        updateButton = new Button("Update");
        updateButton.setDefaultButton(true);
        updateButton.setStyle(
            "-fx-background-color: #1565c0; -fx-border-color: #1565c0; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-text-fill: #ffffff; -fx-padding: 8 14;"
        );

        buttons.getChildren().addAll(cancelButton, updateButton);

        root.getChildren().addAll(titleLabel, varNameLabel, pathLabel, pathBox, buttons);
        return root;
    }

    private void setupActions(String varName) {
        browseButton.setOnAction(e -> onBrowse());

        updateButton.setOnAction(e -> onUpdate());

        cancelButton.setOnAction(e -> onCancel());

        // Enter key updates
        pathField.setOnAction(e -> onUpdate());

        // Esc key cancels
        dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                onCancel();
            }
        });
    }

    private void onBrowse() {
        try {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Folder");

            File initialDir = null;
            String existing = pathField.getText() != null ? pathField.getText().trim() : "";
            if (!existing.isEmpty()) {
                File f = new File(existing);
                if (f.exists() && f.isDirectory()) {
                    initialDir = f;
                } else if (f.getParentFile() != null && f.getParentFile().exists()) {
                    initialDir = f.getParentFile();
                }
            }
            if (initialDir == null) {
                initialDir = new File(System.getProperty("user.home"));
            }
            chooser.setInitialDirectory(initialDir);

            File sel = chooser.showDialog(dialogStage);
            if (sel != null) {
                pathField.setText(sel.getAbsolutePath());
            }
        } catch (Exception ex) {
            logger.warning("Browse failed: " + ex.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Browse Failed", "Failed to open folder browser:\n" + ex.getMessage());
        }
    }

    private void onUpdate() {
        String newPath = pathField.getText() != null ? pathField.getText().trim() : "";
        if (newPath.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "Invalid Value", "Path cannot be empty.");
            return;
        }
        // Warn if path doesn't exist, but still allow
        File f = new File(newPath);
        if (!f.exists() || !f.isDirectory()) {
            Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
            warn.setTitle("Non-existent Path");
            warn.setHeaderText("The specified path does not exist.");
            warn.setContentText("Continue anyway?");
            warn.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    completeAndClose(Optional.of(newPath));
                }
            });
            return;
        }
        completeAndClose(Optional.of(newPath));
    }

    private void onCancel() {
        completeAndClose(Optional.empty());
    }

    private void completeAndClose(Optional<String> value) {
        try {
            if (resultFuture != null && !resultFuture.isDone()) {
                resultFuture.complete(value);
            }
        } finally {
            if (dialogStage != null) {
                dialogStage.close();
            }
        }
    }
}
