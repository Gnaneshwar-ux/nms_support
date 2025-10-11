package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.service.globalPack.ProcessMonitorManager;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Controller for Process Monitor dialog
 */
public class ProcessMonitorController {
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private ProgressBar progressBar;
    
    @FXML
    private TextArea logArea;
    
    @FXML
    private Button stopButton;
    
    @FXML
    private Button closeButton;
    
    private Stage stage;
    private ProcessMonitorManager processMonitorManager;
    
    public void initialize() {
        processMonitorManager = ProcessMonitorManager.getInstance();
        updateStatus();
        
        // Set up close button handler
        if (closeButton != null) {
            closeButton.setOnAction(e -> handleClose());
        }
        
        // Set up stop button handler
        if (stopButton != null) {
            stopButton.setOnAction(e -> handleStop());
        }
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
        
        // Hide the close button (X) and disable window controls
        stage.setResizable(false);
        stage.initStyle(StageStyle.UTILITY); // Removes minimize/maximize buttons
        
        // Override close behavior - enable/disable based on operation state
        stage.setOnCloseRequest(e -> {
            if (processMonitorManager.hasInProgressOperations()) {
                // Operations in progress - prevent closing
                LoggerUtil.getLogger().info("Process monitor close blocked - operations in progress");
                e.consume(); // Cancel the close event
            } else {
                // No operations in progress - allow close with cleanup
                LoggerUtil.getLogger().info("Process monitor closing - performing cleanup");
                processMonitorManager.forceCleanupAllSessions();
            }
        });
    }
    
    @FXML
    private void handleClose() {
        if (processMonitorManager.hasInProgressOperations()) {
            LoggerUtil.getLogger().info("Close button clicked but operations in progress - blocking close");
            return; // Don't close if operations are running
        }
        
        LoggerUtil.getLogger().info("Process monitor closing via close button");
        processMonitorManager.forceCleanupAllSessions();
        stage.close();
    }
    
    /**
     * Force close without warning (for emergency cases)
     */
    public void forceClose() {
        LoggerUtil.getLogger().info("Force closing process monitor");
        processMonitorManager.forceCleanupAllSessions();
        stage.close();
    }
    
    @FXML
    private void handleStop() {
        LoggerUtil.getLogger().info("User clicked Stop button - performing graceful cleanup");
        
        // Disable stop button to prevent multiple clicks
        if (stopButton != null) {
            stopButton.setDisable(true);
            stopButton.setText("Stopping...");
        }
        
        // Perform graceful cleanup
        processMonitorManager.gracefulCleanupAllSessions();
        
        // Update UI
        updateStatus();
        appendLog("All operations stopped by user");
        
        // Re-enable stop button
        if (stopButton != null) {
            stopButton.setDisable(false);
            stopButton.setText("Stop All");
        }
    }
    
    public void updateStatus() {
        if (statusLabel != null) {
            statusLabel.setText(processMonitorManager.getStatusInfo());
        }
        
        // Update progress bar based on operation state
        if (progressBar != null) {
            if (processMonitorManager.hasInProgressOperations()) {
                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            } else {
                progressBar.setProgress(0.0);
            }
        }
        
        // Update close button state based on operation status
        updateCloseButtonState();
    }
    
    /**
     * Update close button state based on operation status
     */
    private void updateCloseButtonState() {
        if (closeButton != null) {
            boolean hasOperations = processMonitorManager.hasInProgressOperations();
            closeButton.setDisable(hasOperations);
            
            if (hasOperations) {
                closeButton.setText("Close (Operations Running)");
                closeButton.setStyle("-fx-background-color: #ffcccc; -fx-text-fill: #cc0000;");
            } else {
                closeButton.setText("Close");
                closeButton.setStyle("-fx-background-color: #cccccc; -fx-text-fill: #000000;");
            }
        }
    }
    
    public void appendLog(String message) {
        if (logArea != null) {
            String timestamp = java.time.LocalTime.now().toString();
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            
            // Auto-scroll to bottom
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }
    
    public void clearLog() {
        if (logArea != null) {
            logArea.clear();
        }
    }
    
    /**
     * Update the UI with current status
     */
    public void refresh() {
        updateStatus();
    }
    
    /**
     * Start periodic UI updates to reflect operation states
     */
    public void startPeriodicUpdates() {
        // Update UI every 2 seconds to reflect current operation states
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), e -> updateStatus())
        );
        timeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        timeline.play();
    }
}
