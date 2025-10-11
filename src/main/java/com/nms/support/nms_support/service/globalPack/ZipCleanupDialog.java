package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Dialog for managing and cleaning up server-side temporary zip files
 */
public class ZipCleanupDialog {
    private static final Logger logger = Logger.getLogger(ZipCleanupDialog.class.getName());
    private static final String PROFESSIONAL_FONT_FAMILY = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
    
    private Stage dialog;
    private ProjectEntity project;
    private Stage parentStage;
    private VBox filesContainer;
    private Label statusLabel;
    private Button cleanupButton;
    private Button cancelButton;
    private ProgressIndicator progressIndicator;
    private VBox progressContainer;
    
    private List<ZipFileEntry> zipFileEntries = new ArrayList<>();
    private SSHJSessionManager sshSession = null; // Reuse same session for entire process
    
    /**
     * Shows the cleanup dialog for the given project
     */
    public void showDialog(Stage parentStage, ProjectEntity project) {
        this.parentStage = parentStage;
        this.project = project;
        
        createDialog();
        
        // Start scanning for zip files
        scanServerZipFiles();
        
        dialog.showAndWait();
    }
    
    private void createDialog() {
        dialog = new Stage();
        dialog.initStyle(StageStyle.UTILITY);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(parentStage);
        dialog.setResizable(false);
        dialog.setTitle("Server Temp Files Cleanup - " + project.getName());
        
        // Set app icon
        IconUtils.setStageIcon(dialog);
        
        // Main container
        VBox mainContainer = new VBox(16);
        mainContainer.setPadding(new Insets(24));
        mainContainer.setStyle(String.format(
            "-fx-font-family: %s; -fx-background-color: #F9FAFB; -fx-background-radius: 12;",
            PROFESSIONAL_FONT_FAMILY
        ));
        mainContainer.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.15)));
        
        // Header
        HBox header = createHeader();
        
        // Description
        Label descLabel = new Label("The following temporary zip files were found on the server. Select files to delete:");
        descLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 13px; -fx-text-fill: #6B7280; -fx-wrap-text: true;",
            PROFESSIONAL_FONT_FAMILY
        ));
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(550);
        
        // Progress container (hidden initially)
        progressContainer = new VBox(12);
        progressContainer.setAlignment(Pos.CENTER);
        progressContainer.setVisible(false);
        progressContainer.setManaged(false);
        
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(40, 40);
        
        Label progressLabel = new Label("Scanning server...");
        progressLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 13px; -fx-text-fill: #6B7280;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        progressContainer.getChildren().addAll(progressIndicator, progressLabel);
        
        // Files container (scrollable)
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setMaxHeight(300);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: #E5E7EB; -fx-border-radius: 8;");
        
        filesContainer = new VBox(8);
        filesContainer.setPadding(new Insets(12));
        filesContainer.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 8;");
        
        scrollPane.setContent(filesContainer);
        
        // Status label
        statusLabel = new Label("Ready to scan");
        statusLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #9CA3AF; -fx-font-style: italic;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Buttons
        HBox buttonBar = createButtonBar();
        
        mainContainer.getChildren().addAll(header, descLabel, progressContainer, scrollPane, statusLabel, buttonBar);
        
        Scene scene = new Scene(mainContainer, 600, 500);
        dialog.setScene(scene);
    }
    
    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon icon = new FontIcon("fa-trash");
        icon.setIconSize(24);
        icon.setIconColor(Color.web("#EF4444"));
        
        Label titleLabel = new Label("Server Temp Files Cleanup");
        titleLabel.setFont(Font.font(PROFESSIONAL_FONT_FAMILY, FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #1F2937;");
        
        header.getChildren().addAll(icon, titleLabel);
        return header;
    }
    
    private HBox createButtonBar() {
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        
        cancelButton = new Button("Cancel");
        cancelButton.setStyle(
            "-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; " +
            "-fx-font-size: 13px; " +
            "-fx-background-color: #E5E7EB; " +
            "-fx-text-fill: #374151; " +
            "-fx-padding: 8 16; " +
            "-fx-background-radius: 6; " +
            "-fx-cursor: hand;"
        );
        cancelButton.setOnAction(event -> {
            // Close SSH session if open
            if (sshSession != null) {
                try {
                    sshSession.close();
                } catch (Exception ignored) {}
            }
            dialog.close();
        });
        
        cleanupButton = new Button("Clean Up Selected");
        cleanupButton.setStyle(
            "-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; " +
            "-fx-font-size: 13px; " +
            "-fx-background-color: #EF4444; " +
            "-fx-text-fill: #FFFFFF; " +
            "-fx-padding: 8 16; " +
            "-fx-background-radius: 6; " +
            "-fx-cursor: hand;"
        );
        cleanupButton.setDisable(true);
        cleanupButton.setOnAction(event -> performCleanup());
        
        buttonBar.getChildren().addAll(cancelButton, cleanupButton);
        return buttonBar;
    }
    
    /**
     * Scans the server for tracked zip files and verifies their existence
     */
    private void scanServerZipFiles() {
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        statusLabel.setText("Connecting to server...");
        
        Thread scanThread = new Thread(() -> {
            try {
                // Validate project authentication
                if (!UnifiedSSHService.validateProjectAuth(project)) {
                    Platform.runLater(() -> {
                        progressContainer.setVisible(false);
                        progressContainer.setManaged(false);
                        statusLabel.setText("Error: Invalid SSH configuration");
                        showError("Invalid SSH configuration. Please configure Linux Server settings first.");
                    });
                    return;
                }
                
                Platform.runLater(() -> statusLabel.setText("Connecting to server..."));
                
                // Create SSH session and store for reuse
                sshSession = UnifiedSSHService.createSSHSession(project, "zip_cleanup");
                sshSession.initialize();
                
                Platform.runLater(() -> statusLabel.setText("Scanning for zip files..."));
                
                // Get tracked zip files from project
                List<ProjectEntity.ServerZipFile> trackedZips = project.getServerZipFiles();
                
                if (trackedZips == null || trackedZips.isEmpty()) {
                    Platform.runLater(() -> {
                        progressContainer.setVisible(false);
                        progressContainer.setManaged(false);
                        statusLabel.setText("No tracked zip files found");
                        filesContainer.getChildren().add(createNoFilesLabel());
                        updateButtonForNoFiles();
                    });
                    return;
                }
                
                // Check each file's existence on server
                List<ZipFileInfo> existingFiles = new ArrayList<>();
                List<String> filesToRemoveFromTracking = new ArrayList<>();
                
                for (ProjectEntity.ServerZipFile zipFile : trackedZips) {
                    String path = zipFile.getPath();
                    
                    Platform.runLater(() -> statusLabel.setText("Checking: " + path));
                    
                    // Check if file exists and get its size
                    SSHJSessionManager.CommandResult checkResult = sshSession.executeCommand(
                        "test -f " + path + " && stat -c '%s' " + path + " || echo 'NOT_FOUND'", 30
                    );
                    
                    if (checkResult.isSuccess()) {
                        String output = checkResult.getOutput().trim();
                        if (!"NOT_FOUND".equals(output)) {
                            try {
                                long size = Long.parseLong(output);
                                existingFiles.add(new ZipFileInfo(
                                    path, 
                                    zipFile.getPurpose(), 
                                    zipFile.getCreatedTimestamp(), 
                                    size,
                                    true
                                ));
                            } catch (NumberFormatException e) {
                                logger.warning("Failed to parse file size for: " + path);
                                existingFiles.add(new ZipFileInfo(
                                    path, 
                                    zipFile.getPurpose(), 
                                    zipFile.getCreatedTimestamp(), 
                                    0,
                                    true
                                ));
                            }
                        } else {
                            // File doesn't exist, mark for removal from tracking
                            logger.info("File not found on server, will remove from tracking: " + path);
                            filesToRemoveFromTracking.add(path);
                            existingFiles.add(new ZipFileInfo(
                                path, 
                                zipFile.getPurpose(), 
                                zipFile.getCreatedTimestamp(), 
                                0,
                                false
                            ));
                        }
                    }
                }
                
                // Remove missing files from project tracking
                for (String pathToRemove : filesToRemoveFromTracking) {
                    project.removeServerZipFile(pathToRemove);
                    logger.info("Removed missing file from tracking: " + pathToRemove);
                }
                
                // SSH session kept open for potential cleanup operations
                
                // Update UI with results
                final List<ZipFileInfo> finalExistingFiles = existingFiles;
                final List<String> finalRemovedPaths = new ArrayList<>(filesToRemoveFromTracking);
                Platform.runLater(() -> {
                    progressContainer.setVisible(false);
                    progressContainer.setManaged(false);
                    displayZipFiles(finalExistingFiles);
                    
                    // Mark missing files as removed from tracking in the UI
                    for (ZipFileEntry entry : zipFileEntries) {
                        if (finalRemovedPaths.contains(entry.fileInfo.path)) {
                            entry.markAsRemovedFromTracking();
                        }
                    }
                });
                
            } catch (Exception e) {
                logger.severe("Error scanning server zip files: " + e.getMessage());
                LoggerUtil.error(e);
                Platform.runLater(() -> {
                    progressContainer.setVisible(false);
                    progressContainer.setManaged(false);
                    statusLabel.setText("Error: " + e.getMessage());
                    showError("Failed to scan server: " + e.getMessage());
                });
            }
            // Note: SSH session is kept open for potential cleanup operations
        });
        
        scanThread.setDaemon(true);
        scanThread.start();
    }
    
    private Label createNoFilesLabel() {
        Label noFilesLabel = new Label("✓ No temporary zip files found on the server");
        noFilesLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 14px; -fx-text-fill: #10B981; -fx-padding: 24;",
            PROFESSIONAL_FONT_FAMILY
        ));
        return noFilesLabel;
    }
    
    /**
     * Displays the list of zip files in the UI
     */
    private void displayZipFiles(List<ZipFileInfo> files) {
        filesContainer.getChildren().clear();
        zipFileEntries.clear();
        
        if (files.isEmpty()) {
            filesContainer.getChildren().add(createNoFilesLabel());
            statusLabel.setText("No files to clean up");
            updateButtonForNoFiles();
            return;
        }
        
        int existingCount = 0;
        int missingCount = 0;
        
        for (ZipFileInfo fileInfo : files) {
            ZipFileEntry entry = new ZipFileEntry(fileInfo);
            zipFileEntries.add(entry);
            filesContainer.getChildren().add(entry.getNode());
            
            if (fileInfo.exists) {
                existingCount++;
            } else {
                missingCount++;
            }
        }
        
        // Update status message
        if (missingCount > 0) {
            statusLabel.setText(String.format("Found %d existing file(s), %d missing file(s) (removed from tracking)", existingCount, missingCount));
        } else {
            statusLabel.setText(String.format("Found %d file(s) to clean up", existingCount));
        }
        
        // Update button behavior based on results
        if (existingCount == 0) {
            updateButtonForNoFiles();
        } else {
            // Check current selection state and update button accordingly
            updateButtonForSelection();
        }
    }
    
    /**
     * Updates button to show "OK" when no files are available for cleanup
     */
    private void updateButtonForNoFiles() {
        cleanupButton.setText("OK");
        cleanupButton.setStyle(
            "-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; " +
            "-fx-font-size: 13px; " +
            "-fx-background-color: #10B981; " +
            "-fx-text-fill: #FFFFFF; " +
            "-fx-padding: 8 16; " +
            "-fx-background-radius: 6; " +
            "-fx-cursor: hand;"
        );
        cleanupButton.setDisable(false);
        cleanupButton.setOnAction(event -> dialog.close());
    }
    
    /**
     * Updates button behavior when files are selected/deselected
     */
    private void updateButtonForSelection() {
        int selectedCount = (int) zipFileEntries.stream()
            .filter(entry -> entry.checkBox.isSelected() && entry.fileInfo.exists)
            .count();
        
        if (selectedCount == 0) {
            cleanupButton.setDisable(true);
        } else {
            cleanupButton.setDisable(false);
            cleanupButton.setText("Clean Up Selected (" + selectedCount + ")");
        }
    }
    
    /**
     * Performs the cleanup operation for selected files
     */
    private void performCleanup() {
        List<ZipFileEntry> selectedEntries = new ArrayList<>();
        for (ZipFileEntry entry : zipFileEntries) {
            if (entry.isSelected() && entry.fileInfo.exists) {
                selectedEntries.add(entry);
            }
        }
        
        if (selectedEntries.isEmpty()) {
            showWarning("No files selected", "Please select at least one file to clean up.");
            return;
        }
        
        // Confirm cleanup with proper formatting
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Cleanup");
        confirmDialog.setHeaderText("Delete " + selectedEntries.size() + " file(s) from server?");
        
        // Build detailed message with file list
        StringBuilder message = new StringBuilder();
        message.append("This action cannot be undone. \nThe following files will be permanently deleted from the server:\n\n");
        
        int maxFilesToShow = 5;
        int count = 0;
        for (ZipFileEntry entry : selectedEntries) {
            if (count < maxFilesToShow) {
                message.append("• ").append(entry.fileInfo.path).append("\n");
                count++;
            } else {
                message.append("... and ").append(selectedEntries.size() - maxFilesToShow).append(" more file(s)\n");
                break;
            }
        }
        
        message.append("\nAre you sure you want to continue?");
        confirmDialog.setContentText(message.toString());
        
        // Fix dialog size and text wrapping
        confirmDialog.getDialogPane().setMinWidth(550);
        confirmDialog.getDialogPane().setPrefWidth(550);
        confirmDialog.getDialogPane().setMaxWidth(600);
        
        // Apply text wrapping and styling to content
        Label contentLabel = (Label) confirmDialog.getDialogPane().lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; -fx-font-size: 13px;");
        }
        
        // Set custom button text
        ButtonType deleteButton = new ButtonType("Delete Files", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(deleteButton, cancelButton);
        
        IconUtils.setStageIcon((Stage) confirmDialog.getDialogPane().getScene().getWindow());
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == deleteButton) {
                executeCleanup(selectedEntries);
            }
        });
    }
    
    /**
     * Executes the cleanup operation
     */
    private void executeCleanup(List<ZipFileEntry> selectedEntries) {
        cleanupButton.setDisable(true);
        cancelButton.setDisable(true);
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        statusLabel.setText("Cleaning up files...");
        
        Thread cleanupThread = new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            
            try {
                // Reuse existing SSH session or create new one if needed
                if (sshSession == null) {
                    sshSession = UnifiedSSHService.createSSHSession(project, "zip_cleanup_delete");
                    sshSession.initialize();
                }
                
                List<String> failedFiles = new ArrayList<>();
                List<String> failureReasons = new ArrayList<>();
                
                for (ZipFileEntry entry : selectedEntries) {
                    String path = entry.fileInfo.path;
                    
                    Platform.runLater(() -> statusLabel.setText("Deleting: " + path));
                    
                    try {
                        // Use rm -f to handle write-protected files without prompting
                        SSHJSessionManager.CommandResult deleteResult = sshSession.executeCommand(
                            "rm -f " + path + " && echo 'DELETED' || echo 'FAILED'", 10
                        );
                        
                        if (deleteResult.isSuccess() && deleteResult.getOutput().contains("DELETED")) {
                            // Remove from project tracking
                            project.removeServerZipFile(path);
                            successCount++;
                            Platform.runLater(() -> entry.markAsDeleted());
                            logger.info("Successfully deleted zip file: " + path);
                        } else {
                            failCount++;
                            failedFiles.add(path);
                            
                            // Capture failure reason
                            String failureReason = "Unknown error";
                            if (!deleteResult.isSuccess()) {
                                failureReason = "Command execution failed (exit code: " + deleteResult.getExitCode() + ")";
                            } else {
                                String output = deleteResult.getOutput();
                                if (output.contains("FAILED")) {
                                    failureReason = "File deletion failed - may not exist or insufficient permissions";
                                } else {
                                    failureReason = "Unexpected response: " + output;
                                }
                            }
                            failureReasons.add(failureReason);
                            logger.warning("Failed to delete zip file: " + path + " - " + failureReason);
                            
                            // Mark the entry as failed in the UI
                            final String finalFailureReason = failureReason;
                            Platform.runLater(() -> entry.markAsFailed(finalFailureReason));
                        }
                    } catch (Exception e) {
                        failCount++;
                        failedFiles.add(path);
                        String failureReason = "Exception: " + e.getMessage();
                        failureReasons.add(failureReason);
                        logger.severe("Error deleting zip file " + path + ": " + e.getMessage());
                        
                        // Mark the entry as failed in the UI
                        final String finalFailureReason = failureReason;
                        Platform.runLater(() -> entry.markAsFailed(finalFailureReason));
                    }
                }
                
                final int finalSuccessCount = successCount;
                final int finalFailCount = failCount;
                final List<String> finalFailedFiles = new ArrayList<>(failedFiles);
                final List<String> finalFailureReasons = new ArrayList<>(failureReasons);
                
                Platform.runLater(() -> {
                    progressContainer.setVisible(false);
                    progressContainer.setManaged(false);
                    cleanupButton.setDisable(false);
                    cancelButton.setDisable(false);
                    
                    // Update status label with failure details
                    if (finalFailCount > 0) {
                        StringBuilder statusMessage = new StringBuilder();
                        statusMessage.append(String.format("Cleanup complete: %d succeeded, %d failed", 
                            finalSuccessCount, finalFailCount));
                        
                        // Add failure details
                        if (finalFailedFiles.size() > 0) {
                            statusMessage.append("\n\nFailed files:");
                            for (int i = 0; i < finalFailedFiles.size() && i < 3; i++) {
                                String fileName = finalFailedFiles.get(i);
                                String reason = finalFailureReasons.get(i);
                                statusMessage.append(String.format("\n• %s: %s", 
                                    fileName.substring(fileName.lastIndexOf('/') + 1), reason));
                            }
                            if (finalFailedFiles.size() > 3) {
                                statusMessage.append(String.format("\n... and %d more", finalFailedFiles.size() - 3));
                            }
                        }
                        
                        statusLabel.setText(statusMessage.toString());
                        statusLabel.setStyle(String.format(
                            "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #EF4444;",
                            PROFESSIONAL_FONT_FAMILY
                        ));
                    } else {
                        statusLabel.setText(String.format("Cleanup complete: %d succeeded, %d failed", 
                            finalSuccessCount, finalFailCount));
                        statusLabel.setStyle(String.format(
                            "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #10B981;",
                            PROFESSIONAL_FONT_FAMILY
                        ));
                    }
                    
                    if (finalSuccessCount > 0) {
                        showSuccess("Cleanup Complete", 
                            String.format("Successfully deleted %d file(s) from the server.", finalSuccessCount));
                        
                        // Update button to show OK since cleanup is complete
                        updateButtonForNoFiles();
                    } else {
                        cleanupButton.setDisable(false);
                    }
                    
                    // Show detailed failure dialog if there were failures
                    if (finalFailCount > 0) {
                        showFailureDetails(finalFailedFiles, finalFailureReasons);
                    }
                });
                
            } catch (Exception e) {
                logger.severe("Error during cleanup: " + e.getMessage());
                LoggerUtil.error(e);
                Platform.runLater(() -> {
                    progressContainer.setVisible(false);
                    progressContainer.setManaged(false);
                    cleanupButton.setDisable(false);
                    cancelButton.setDisable(false);
                    statusLabel.setText("Error during cleanup");
                    showError("Cleanup failed: " + e.getMessage());
                });
            } finally {
                // Close SSH session when cleanup is complete
                if (sshSession != null) {
                    try {
                        sshSession.close();
                        sshSession = null;
                    } catch (Exception ignored) {}
                }
            }
        });
        
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Failed");
        alert.setContentText(message);
        
        // Fix content wrapping
        alert.getDialogPane().setMaxWidth(500);
        alert.getDialogPane().setPrefWidth(500);
        Label contentLabel = (Label) alert.getDialogPane().lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; -fx-font-size: 13px;");
        }
        
        IconUtils.setStageIcon((Stage) alert.getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }
    
    private void showWarning(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(header);
        alert.setContentText(message);
        
        // Fix content wrapping
        alert.getDialogPane().setMaxWidth(500);
        alert.getDialogPane().setPrefWidth(500);
        Label contentLabel = (Label) alert.getDialogPane().lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; -fx-font-size: 13px;");
        }
        
        IconUtils.setStageIcon((Stage) alert.getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }
    
    private void showSuccess(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(header);
        alert.setContentText(message);
        
        // Fix content wrapping
        alert.getDialogPane().setMaxWidth(500);
        alert.getDialogPane().setPrefWidth(500);
        Label contentLabel = (Label) alert.getDialogPane().lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; -fx-font-size: 13px;");
        }
        
        IconUtils.setStageIcon((Stage) alert.getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }
    
    private void showFailureDetails(List<String> failedFiles, List<String> failureReasons) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Cleanup Failed");
        alert.setHeaderText("Some files could not be deleted");
        
        // Build detailed failure message
        StringBuilder message = new StringBuilder();
        message.append("The following files could not be deleted from the server:\n\n");
        
        for (int i = 0; i < failedFiles.size(); i++) {
            String filePath = failedFiles.get(i);
            String reason = failureReasons.get(i);
            message.append(String.format("• %s\n   Reason: %s\n\n", filePath, reason));
        }
        
        message.append("Please check the file permissions or try again later.");
        
        alert.setContentText(message.toString());
        
        // Fix content wrapping and sizing
        alert.getDialogPane().setMinWidth(600);
        alert.getDialogPane().setPrefWidth(600);
        alert.getDialogPane().setMaxWidth(700);
        
        Label contentLabel = (Label) alert.getDialogPane().lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-font-family: " + PROFESSIONAL_FONT_FAMILY + "; -fx-font-size: 13px;");
        }
        
        IconUtils.setStageIcon((Stage) alert.getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }
    
    /**
     * Inner class representing a zip file entry in the UI
     */
    private class ZipFileEntry {
        private ZipFileInfo fileInfo;
        private CheckBox checkBox;
        private HBox node;
        private Label statusLabel;
        
        public ZipFileEntry(ZipFileInfo fileInfo) {
            this.fileInfo = fileInfo;
            createNode();
        }
        
        private void createNode() {
            node = new HBox(12);
            node.setPadding(new Insets(12));
            node.setAlignment(Pos.CENTER_LEFT);
            node.setStyle(
                "-fx-background-color: " + (fileInfo.exists ? "#F9FAFB" : "#FEF2F2") + "; " +
                "-fx-border-color: " + (fileInfo.exists ? "#E5E7EB" : "#FCA5A5") + "; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;"
            );
            
            checkBox = new CheckBox();
            checkBox.setSelected(fileInfo.exists);
            checkBox.setDisable(!fileInfo.exists);
            
            // Add listener to update button behavior when selection changes
            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> updateButtonForSelection());
            });
            
            VBox details = new VBox(4);
            
            Label pathLabel = new Label(fileInfo.path);
            pathLabel.setFont(Font.font(PROFESSIONAL_FONT_FAMILY, FontWeight.BOLD, 12));
            pathLabel.setStyle("-fx-text-fill: #1F2937;");
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date(fileInfo.createdTimestamp));
            
            Label infoLabel = new Label(String.format(
                "%s | Created: %s | Size: %s",
                fileInfo.purpose,
                timestamp,
                formatSize(fileInfo.size)
            ));
            infoLabel.setFont(Font.font(PROFESSIONAL_FONT_FAMILY, 11));
            infoLabel.setStyle("-fx-text-fill: #6B7280;");
            
            statusLabel = new Label(fileInfo.exists ? "✓ Exists on server" : "✗ Not found (will be removed from tracking)");
            statusLabel.setFont(Font.font(PROFESSIONAL_FONT_FAMILY, 11));
            statusLabel.setStyle("-fx-text-fill: " + (fileInfo.exists ? "#10B981" : "#EF4444") + ";");
            
            details.getChildren().addAll(pathLabel, infoLabel, statusLabel);
            HBox.setHgrow(details, Priority.ALWAYS);
            
            node.getChildren().addAll(checkBox, details);
        }
        
        public HBox getNode() {
            return node;
        }
        
        public boolean isSelected() {
            return checkBox.isSelected();
        }
        
        public void markAsDeleted() {
            checkBox.setSelected(false);
            checkBox.setDisable(true);
            statusLabel.setText("✓ Deleted");
            statusLabel.setStyle("-fx-text-fill: #10B981;");
            node.setStyle(
                "-fx-background-color: #F0FDF4; " +
                "-fx-border-color: #86EFAC; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;"
            );
            
            // Update button behavior after marking as deleted
            Platform.runLater(() -> updateButtonForSelection());
        }
        
        public void markAsRemovedFromTracking() {
            checkBox.setSelected(false);
            checkBox.setDisable(true);
            statusLabel.setText("✓ Removed from tracking");
            statusLabel.setStyle("-fx-text-fill: #F59E0B;");
            node.setStyle(
                "-fx-background-color: #FFFBEB; " +
                "-fx-border-color: #FCD34D; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;"
            );
        }
        
        public void markAsFailed(String reason) {
            checkBox.setSelected(false);
            checkBox.setDisable(true);
            statusLabel.setText("✗ Failed: " + reason);
            statusLabel.setStyle("-fx-text-fill: #EF4444;");
            node.setStyle(
                "-fx-background-color: #FEF2F2; " +
                "-fx-border-color: #FCA5A5; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6;"
            );
        }
    }
    
    /**
     * Inner class representing zip file information
     */
    private static class ZipFileInfo {
        String path;
        String purpose;
        long createdTimestamp;
        long size;
        boolean exists;
        
        public ZipFileInfo(String path, String purpose, long createdTimestamp, long size, boolean exists) {
            this.path = path;
            this.purpose = purpose;
            this.createdTimestamp = createdTimestamp;
            this.size = size;
            this.exists = exists;
        }
    }
    
    /**
     * Formats file size in human-readable format
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}

