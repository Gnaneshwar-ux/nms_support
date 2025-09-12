package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
// Animation imports removed as they're not used in this implementation

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Process Monitor for tracking and displaying progress of multiple steps
 */
public class ProcessMonitor {
    private static final Logger logger = Logger.getLogger(ProcessMonitor.class.getName());
    
    public enum StepStatus {
        WAITING,
        RUNNING,
        COMPLETED,
        FAILED
    }
    
    public static class ProcessStep {
        private final String id;
        private final String description;
        private int progress;
        private StepStatus status;
        private String message;
        private final HBox mainRow;
        private final Label statusIcon;
        private final Label descriptionLabel;
        private final ProgressIndicator progressIndicator;
        private final Label percentageLabel;
        private final Label statusLabel;
        private final Button expandButton;
        private final VBox expandableContent;
        private boolean isExpanded = false;
        private boolean userCollapsed = false; // Track if user manually collapsed
        // Add message log for progress tracking - using thread-safe collection
        private final CopyOnWriteArrayList<String> messageLog = new CopyOnWriteArrayList<>();
        
        public ProcessStep(String id, String description) {
            this.id = id;
            this.description = description;
            this.progress = 0;
            this.status = StepStatus.WAITING;
            this.message = "";
            
            // Status icon (waiting, running, success, error)
            this.statusIcon = new Label("⏳");
            this.statusIcon.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
            this.statusIcon.setStyle("-fx-text-fill: #94a3b8; -fx-min-width: 20; -fx-alignment: center;");
            
            // Description label
            this.descriptionLabel = new Label(description);
            this.descriptionLabel.setFont(Font.font("Segoe UI", FontWeight.MEDIUM, 13));
            this.descriptionLabel.setStyle("-fx-text-fill: #1e293b; -fx-min-width: 180; -fx-max-width: 180;");
            
            // Progress indicator (smaller, inline)
            this.progressIndicator = new ProgressIndicator(0);
            this.progressIndicator.setPrefSize(16, 16);
            this.progressIndicator.setMaxSize(16, 16);
            
            // Percentage label
            this.percentageLabel = new Label("0%");
            this.percentageLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            this.percentageLabel.setStyle("-fx-text-fill: #64748b; -fx-min-width: 35; -fx-alignment: center;");
            
            // Status label
            this.statusLabel = new Label("Waiting...");
            this.statusLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
            this.statusLabel.setStyle("-fx-text-fill: #64748b; -fx-min-width: 80; -fx-max-width: 120;");
            
            // Expand button (initially hidden)
            this.expandButton = new Button("▼");
            this.expandButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            this.expandButton.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-border-color: transparent; " +
                "-fx-text-fill: #6b7280; " +
                "-fx-padding: 2 4; " +
                "-fx-min-width: 20; " +
                "-fx-cursor: hand;"
            );
            this.expandButton.setVisible(false);
            this.expandButton.setManaged(false);
            
            // Expandable content area
            this.expandableContent = new VBox(6);
            this.expandableContent.setPadding(new Insets(4, 0, 4, 28));
            this.expandableContent.setVisible(false);
            this.expandableContent.setManaged(false);
            
            // Create main row container
            this.mainRow = new HBox(8);
            this.mainRow.setAlignment(Pos.CENTER_LEFT);
            this.mainRow.setPadding(new Insets(6, 0, 6, 0));
            this.mainRow.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-cursor: hand;");
            
            // Make the entire row clickable to expand/collapse
            this.mainRow.setOnMouseClicked(e -> {
                if (expandButton.isVisible()) {
                    isExpanded = !isExpanded;
                    userCollapsed = !isExpanded; // Track user preference
                    expandableContent.setVisible(isExpanded);
                    expandableContent.setManaged(isExpanded);
                    expandButton.setText(isExpanded ? "▲" : "▼");
                }
            });
            
            // Add all elements to main row
            this.mainRow.getChildren().addAll(
                statusIcon, 
                descriptionLabel, 
                progressIndicator, 
                percentageLabel, 
                statusLabel, 
                expandButton
            );
            
            updateUI();
        }
        
        public void updateProgress(int progress) {
            this.progress = Math.max(0, Math.min(100, progress));
            this.status = StepStatus.RUNNING;
            updateUI();
        }
        
        public void updateProgress(int progress, String message) {
            this.progress = Math.max(0, Math.min(100, progress));
            this.status = StepStatus.RUNNING;
            if (message != null && !message.trim().isEmpty()) {
                this.message = message;
                // Add to message log with timestamp
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                messageLog.add("[" + timestamp + "] " + message);
                
                // Limit log size to prevent memory issues
                if (messageLog.size() > 500) {
                    while (messageLog.size() > 500) {
                        messageLog.remove(0);
                    }
                }
            }
            updateUI();
            // Force refresh of expandable content to update internal scroll
            if (isExpanded && !messageLog.isEmpty()) {
                Platform.runLater(() -> {
                    createExpandableContent(this.message, "#3b82f6");
                });
            }
        }
        
        public void markComplete(String message) {
            this.progress = 100;
            this.status = StepStatus.COMPLETED;
            this.message = message != null ? message : "Completed successfully";
            if (message != null && !message.trim().isEmpty()) {
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                messageLog.add("[" + timestamp + "] " + message);
                
                // Limit log size to prevent memory issues
                if (messageLog.size() > 500) {
                    while (messageLog.size() > 500) {
                        messageLog.remove(0);
                    }
                }
            }
            updateUI();
        }
        
        public void markFailed(String reason) {
            this.status = StepStatus.FAILED;
            this.message = reason != null ? reason : "Failed";
            if (reason != null && !reason.trim().isEmpty()) {
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                messageLog.add("[" + timestamp + "] " + reason);
                
                // Limit log size to prevent memory issues
                if (messageLog.size() > 500) {
                    while (messageLog.size() > 500) {
                        messageLog.remove(0);
                    }
                }
            }
            updateUI();
        }
        
        // Add method to log progress messages
        public void logMessage(String message) {
            if (message != null && !message.trim().isEmpty()) {
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                messageLog.add("[" + timestamp + "] " + message);
                
                // Limit log size to prevent memory issues (keep last 500 entries)
                if (messageLog.size() > 500) {
                    // Remove oldest entries to keep only the last 500
                    while (messageLog.size() > 500) {
                        messageLog.remove(0);
                    }
                }
                
                // Update current message for display
                this.message = message;
                updateUI();
                // Force refresh of expandable content to update internal scroll
                if (isExpanded && !messageLog.isEmpty()) {
                    Platform.runLater(() -> {
                        createExpandableContent(this.message, getStatusColor());
                    });
                }
            }
        }
        
        private String getStatusColor() {
            switch (status) {
                case RUNNING: return "#3b82f6";
                case COMPLETED: return "#10b981";
                case FAILED: return "#ef4444";
                default: return "#64748b";
            }
        }
        
        private void updateUI() {
            Platform.runLater(() -> {
                switch (status) {
                    case WAITING:
                        statusIcon.setText("⏳");
                        statusIcon.setStyle("-fx-text-fill: #94a3b8; -fx-min-width: 20; -fx-alignment: center;");
                        progressIndicator.setProgress(0);
                        progressIndicator.setStyle("-fx-progress-color: #94a3b8;");
                        percentageLabel.setText("0%");
                        percentageLabel.setStyle("-fx-text-fill: #64748b; -fx-min-width: 35; -fx-alignment: center;");
                        statusLabel.setText("Waiting...");
                        statusLabel.setStyle("-fx-text-fill: #64748b; -fx-min-width: 80; -fx-max-width: 120;");
                        break;
                        
                    case RUNNING:
                        statusIcon.setText("🔄");
                        statusIcon.setStyle("-fx-text-fill: #3b82f6; -fx-min-width: 20; -fx-alignment: center;");
                        progressIndicator.setProgress(progress / 100.0);
                        progressIndicator.setStyle("-fx-progress-color: #3b82f6;");
                        percentageLabel.setText(progress + "%");
                        percentageLabel.setStyle("-fx-text-fill: #3b82f6; -fx-min-width: 35; -fx-alignment: center;");
                                                 statusLabel.setText(message.isEmpty() ? "Running..." : truncateMessage(message, 15));
                         statusLabel.setStyle("-fx-text-fill: #3b82f6; -fx-min-width: 80; -fx-max-width: 120;");
                         // Auto-expand running processes only if user hasn't manually collapsed
                         Platform.runLater(() -> {
                             createExpandableContent(message, "#3b82f6");
                             if (!userCollapsed && !isExpanded) {
                                 isExpanded = true;
                                 expandableContent.setVisible(true);
                                 expandableContent.setManaged(true);
                                 expandButton.setText("▲");
                             }
                         });
                        break;
                        
                    case COMPLETED:
                        statusIcon.setText("✅");
                        statusIcon.setStyle("-fx-text-fill: #10b981; -fx-min-width: 20; -fx-alignment: center;");
                        progressIndicator.setProgress(1.0);
                        progressIndicator.setStyle("-fx-progress-color: #10b981;");
                        percentageLabel.setText("100%");
                        percentageLabel.setStyle("-fx-text-fill: #10b981; -fx-min-width: 35; -fx-alignment: center;");
                                                 statusLabel.setText("Completed");
                         statusLabel.setStyle("-fx-text-fill: #10b981; -fx-min-width: 80; -fx-max-width: 120;");
                         Platform.runLater(() -> {
                             createExpandableContent(message, "#10b981");
                             // Auto-collapse completed processes
                             if (isExpanded) {
                                 isExpanded = false;
                                 userCollapsed = false; // Reset user preference for completed tasks
                                 expandableContent.setVisible(false);
                                 expandableContent.setManaged(false);
                                 expandButton.setText("▼");
                             }
                         });
                        break;
                        
                    case FAILED:
                        statusIcon.setText("❌");
                        statusIcon.setStyle("-fx-text-fill: #ef4444; -fx-min-width: 20; -fx-alignment: center;");
                        progressIndicator.setProgress(0);
                        progressIndicator.setStyle("-fx-progress-color: #ef4444;");
                        percentageLabel.setText("Failed");
                        percentageLabel.setStyle("-fx-text-fill: #ef4444; -fx-min-width: 35; -fx-alignment: center;");
                                                 statusLabel.setText("Failed");
                         statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-min-width: 80; -fx-max-width: 120;");
                         Platform.runLater(() -> {
                             createExpandableContent(message, "#ef4444");
                         });
                        break;
                }
            });
        }
        
        private void createExpandableContent(String fullMessage, String color) {
            // Clear existing content
            expandableContent.getChildren().clear();
            
            // Show expand button if there are messages or message log
            boolean hasContent = (fullMessage != null && !fullMessage.trim().isEmpty() && 
                                !fullMessage.equals("Completed successfully") && 
                                !fullMessage.equals("Failed")) || !messageLog.isEmpty();
            
            if (!hasContent) {
                return;
            }
            
            // Show expand button
            expandButton.setVisible(true);
            expandButton.setManaged(true);
            
            // Create message display
            VBox messageBox = new VBox(2); // Reduced spacing from 4 to 2
            
            // Create copy button functionality
            Button copyButton = new Button("Copy Log");
            copyButton.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
            copyButton.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-border-color: #6b7280; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 3; " +
                "-fx-background-radius: 3; " +
                "-fx-text-fill: #6b7280; " +
                "-fx-padding: 2 6; " +
                "-fx-cursor: hand;"
            );
            
            // Copy functionality
            copyButton.setOnAction(e -> {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                
                // Copy the full log if available, otherwise just the message
                String textToCopy = !messageLog.isEmpty() ? 
                    String.join("\n", messageLog) : 
                    (fullMessage != null ? fullMessage : "");
                
                content.putString(textToCopy);
                clipboard.setContent(content);
                
                // Show temporary feedback
                String originalText = copyButton.getText();
                copyButton.setText("Copied!");
                copyButton.setStyle(
                    "-fx-background-color: #10b981; " +
                    "-fx-border-color: #10b981; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 3; " +
                    "-fx-background-radius: 3; " +
                    "-fx-text-fill: white; " +
                    "-fx-padding: 2 6; " +
                    "-fx-cursor: hand;"
                );
                
                // Reset after 2 seconds
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Platform.runLater(() -> {
                            copyButton.setText(originalText);
                            copyButton.setStyle(
                                "-fx-background-color: transparent; " +
                                "-fx-border-color: #6b7280; " +
                                "-fx-border-width: 1; " +
                                "-fx-border-radius: 3; " +
                                "-fx-background-radius: 3; " +
                                "-fx-text-fill: #6b7280; " +
                                "-fx-padding: 2 6; " +
                                "-fx-cursor: hand;"
                            );
                        });
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
            
            // If we have a message log, show it instead of just the final message
            if (!messageLog.isEmpty()) {
                // Create scrollable log view with dynamic height
                VBox logContainer = new VBox(2);
                int logEntryCount = messageLog.size();
                int calculatedHeight = Math.min(Math.max(50, logEntryCount * 20 + 40), 200);
                logContainer.setPrefHeight(calculatedHeight);
                logContainer.setMaxHeight(calculatedHeight);
                logContainer.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 8;");
                
                // Log title
                Label logTitle = new Label("Progress Log:");
                logTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                logTitle.setStyle("-fx-text-fill: #374151; -fx-padding: 0 0 4 0;");
                
                // Log entries - limit to last 100 entries to prevent memory issues
                int startIndex = Math.max(0, messageLog.size() - 100);
                for (int i = startIndex; i < messageLog.size(); i++) {
                    String logEntry = messageLog.get(i);
                    Label logLabel = new Label(logEntry);
                    logLabel.setFont(Font.font("Consolas", FontWeight.NORMAL, 10));
                    logLabel.setStyle("-fx-text-fill: #6b7280; -fx-wrap-text: true;");
                    logContainer.getChildren().add(logLabel);
                }
                
                                 // Add indicator if we're showing limited entries
                 if (messageLog.size() > 100) {
                     Label limitLabel = new Label("... showing last 100 of " + messageLog.size() + " entries ...");
                     limitLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, FontPosture.ITALIC, 9));
                     limitLabel.setStyle("-fx-text-fill: #9ca3af; -fx-padding: 2 0;");
                     logContainer.getChildren().add(0, limitLabel);
                 }
                
                // Scroll pane for log with internal scroll tracking
                ScrollPane logScrollPane = new ScrollPane(logContainer);
                logScrollPane.setFitToWidth(true);
                logScrollPane.setPrefHeight(calculatedHeight);
                logScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
                
                // Track internal scroll position for auto-scroll to bottom
                boolean[] wasInternalAtBottom = {true};
                logScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                    double maxScroll = logScrollPane.getVmax();
                    wasInternalAtBottom[0] = (newVal.doubleValue() >= maxScroll - 0.05);
                });
                
                // Auto-scroll internal log to bottom when new content is added
                Platform.runLater(() -> {
                    if (wasInternalAtBottom[0]) {
                        logScrollPane.setVvalue(logScrollPane.getVmax());
                    }
                });
                
                messageBox.getChildren().add(logScrollPane);
                
                // Action buttons - moved closer to log content
                HBox buttonRow = new HBox(6);
                buttonRow.setAlignment(Pos.CENTER_LEFT);
                buttonRow.setPadding(new Insets(2, 0, 0, 0)); // Small top padding to separate from log
                buttonRow.getChildren().add(copyButton);
                messageBox.getChildren().add(buttonRow);
                
            } else if (fullMessage != null && !fullMessage.trim().isEmpty()) {
                // Fallback to single message display
                Label messageLabel = new Label(fullMessage);
                messageLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
                messageLabel.setStyle("-fx-text-fill: " + color + "; -fx-wrap-text: true;");
                messageLabel.setMaxWidth(500);
                messageBox.getChildren().add(messageLabel);
                
                // Action buttons for single message
                HBox buttonRow = new HBox(6);
                buttonRow.setAlignment(Pos.CENTER_LEFT);
                buttonRow.setPadding(new Insets(2, 0, 0, 0));
                buttonRow.getChildren().add(copyButton);
                messageBox.getChildren().add(buttonRow);
            }
            expandableContent.getChildren().add(messageBox);
            
            // Expand button action
            expandButton.setOnAction(e -> {
                isExpanded = !isExpanded;
                userCollapsed = !isExpanded; // Track user preference
                expandableContent.setVisible(isExpanded);
                expandableContent.setManaged(isExpanded);
                expandButton.setText(isExpanded ? "▲" : "▼");
            });
        }
        
        private String truncateMessage(String message, int maxLength) {
            if (message == null || message.length() <= maxLength) {
                return message;
            }
            return message.substring(0, maxLength - 3) + "...";
        }
        
        // Getters
        public String getId() { return id; }
        public String getDescription() { return description; }
        public int getProgress() { return progress; }
        public StepStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public HBox getMainRow() { return mainRow; }
        public VBox getExpandableContent() { return expandableContent; }
    }
    
    private final String title;
    private final String projectName;
    private final Map<String, ProcessStep> steps = new ConcurrentHashMap<>();
    private final VBox stepsContainer = new VBox(2);
    private final Stage dialogStage;
    private final Button actionButton;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean hasFailed = new AtomicBoolean(false);
    private final AtomicBoolean isCompleted = new AtomicBoolean(false);
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    
    // Scroll position tracking
    private ScrollPane stepsScrollPane;
    private boolean wasAtBottom = true;
    private double lastScrollPosition = 0.0;
    
    public ProcessMonitor(String title) {
        this(title, null);
    }
    
    public ProcessMonitor(String title, String projectName) {
        this.title = title;
        this.projectName = projectName;
        
        // Create window stage (not modal dialog)
        this.dialogStage = new Stage();
        this.dialogStage.initModality(Modality.NONE); // Remove modal behavior
        this.dialogStage.initStyle(StageStyle.DECORATED);
        this.dialogStage.setResizable(true); // Allow resizing
        this.dialogStage.setMinWidth(600);
        this.dialogStage.setMinHeight(400);
        // Set window title with project name if available
        String windowTitle = title + " - Process Monitor";
        if (projectName != null && !projectName.trim().isEmpty()) {
            windowTitle = projectName + " - " + title + " - Process Monitor";
        }
        this.dialogStage.setTitle(windowTitle);
        
        // Enable taskbar integration and native window behavior
        this.dialogStage.setAlwaysOnTop(false);
        this.dialogStage.setIconified(false);
        
        // Ensure proper window ownership for taskbar integration
        this.dialogStage.initOwner(null);
        
        // Set window icon
        IconUtils.setStageIcon(this.dialogStage);
        
        // Create action button with improved styling
        this.actionButton = new Button("Stop");
        this.actionButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        this.actionButton.setStyle(
            "-fx-background-color: #ef4444; " +
            "-fx-border-color: #ef4444; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 8 20; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(239, 68, 68, 0.3), 3, 0, 0, 1);"
        );
        this.actionButton.setOnAction(e -> {
            if (isRunning.get()) {
                // Stop the process
                isRunning.set(false);
                hasFailed.set(true);
                actionButton.setText("Stopping...");
                actionButton.setDisable(true);
                completionLatch.countDown();
            } else {
                // Close window when process is completed
                dialogStage.close();
                completionLatch.countDown();
            }
        });
        
        // Handle window close event - minimize during execution, allow close when completed
        this.dialogStage.setOnCloseRequest(e -> {
            if (isRunning.get()) {
                e.consume(); // Prevent default close behavior during execution
                dialogStage.setIconified(true); // Minimize the window
            }
            // Allow normal closing when process is completed
        });
        
        // Handle window minimize event
        this.dialogStage.iconifiedProperty().addListener((obs, wasIconified, isIconified) -> {
            if (isIconified) {
                // Window was minimized - keep it running in background
                logger.info("Process Monitor window minimized - keeping process running");
            }
        });
        
        setupUI();
    }
    
    private void setupUI() {
        // Main container - now resizable
        VBox mainContainer = new VBox(0);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle("-fx-background-color: #FFFFFF;");
        
        // Header
        VBox header = createHeader();
        
        // Content
        VBox content = createContent();
        
        // Footer
        HBox footer = createFooter();
        
        mainContainer.getChildren().addAll(header, content, footer);
        
        // Set scene with proper sizing
        Scene scene = new Scene(mainContainer, 700, 500); // Larger default size
        dialogStage.setScene(scene);
        
        // Center the window on screen
        dialogStage.centerOnScreen();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(16, 20, 12, 20));
        header.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12 12 0 0;");
        
        // Project name (if available)
        if (projectName != null && !projectName.trim().isEmpty()) {
            Label projectLabel = new Label(projectName);
            projectLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            projectLabel.setStyle("-fx-text-fill: #3b82f6;");
            header.getChildren().add(projectLabel);
        }
        
        // Title
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        titleLabel.setStyle("-fx-text-fill: #1e293b;");
        
        // Subtitle
        Label subtitleLabel = new Label("Monitoring process execution...");
        subtitleLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        subtitleLabel.setStyle("-fx-text-fill: #64748b;");
        
        header.getChildren().addAll(titleLabel, subtitleLabel);
        return header;
    }
    
    private VBox createContent() {
        VBox content = new VBox(8);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(12, 20, 12, 20));
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content to grow
        
        // Steps container
        stepsContainer.setAlignment(Pos.TOP_LEFT);
        stepsContainer.setPadding(new Insets(0));
        
        // Scroll pane for steps with scroll position tracking
        stepsScrollPane = new ScrollPane(stepsContainer);
        stepsScrollPane.setFitToWidth(true);
        stepsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(stepsScrollPane, Priority.ALWAYS); // Allow scroll pane to grow
        
        // Track scroll position changes
        stepsScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            lastScrollPosition = newVal.doubleValue();
            // Check if user is at bottom (within 0.05 of max)
            double maxScroll = stepsScrollPane.getVmax();
            wasAtBottom = (newVal.doubleValue() >= maxScroll - 0.05);
        });
        
        // Also track when content changes to update scroll position
        stepsContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            // Small delay to ensure content is rendered
            Platform.runLater(() -> {
                if (wasAtBottom) {
                    stepsScrollPane.setVvalue(stepsScrollPane.getVmax());
                }
            });
        });
        
        content.getChildren().add(stepsScrollPane);
        return content;
    }
    
    private HBox createFooter() {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 20, 16, 20));
        footer.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 0 0 12 12;");
        
        footer.getChildren().add(actionButton);
        return footer;
    }
    
    /**
     * Smart scroll to bottom only if user was already at bottom
     */
    private void smartScrollToBottom() {
        if (wasAtBottom) {
            Platform.runLater(() -> {
                // Ensure we're at the very bottom
                double maxScroll = stepsScrollPane.getVmax();
                stepsScrollPane.setVvalue(maxScroll);
                // Double-check after a small delay to handle any layout changes
                Platform.runLater(() -> {
                    stepsScrollPane.setVvalue(stepsScrollPane.getVmax());
                });
            });
        }
    }
    
    /**
     * Add a new step to the process monitor
     */
    public void addStep(String stepId, String description) {
        ProcessStep step = new ProcessStep(stepId, description);
        steps.put(stepId, step);
        
        Platform.runLater(() -> {
            stepsContainer.getChildren().add(step.getMainRow());
            stepsContainer.getChildren().add(step.getExpandableContent());
            // Small delay to ensure content is rendered before scrolling
            Platform.runLater(() -> {
                smartScrollToBottom();
            });
        });
    }
    
    /**
     * Update the progress of a specific step
     */
    public void updateState(String stepId, int progress) {
        ProcessStep step = steps.get(stepId);
        if (step != null) {
            step.updateProgress(progress);
            smartScrollToBottom();
        } else {
            logger.warning("Step not found: " + stepId);
        }
    }
    
    /**
     * Update the progress of a specific step with a message
     */
    public void updateState(String stepId, int progress, String message) {
        ProcessStep step = steps.get(stepId);
        if (step != null) {
            step.updateProgress(progress, message);
            smartScrollToBottom();
        } else {
            logger.warning("Step not found: " + stepId);
        }
    }
    
    /**
     * Log a message for a specific step without changing progress
     */
    public void logMessage(String stepId, String message) {
        ProcessStep step = steps.get(stepId);
        if (step != null) {
            step.logMessage(message);
            smartScrollToBottom();
        } else {
            logger.warning("Step not found: " + stepId);
        }
    }
    
    /**
     * Mark a step as complete
     */
    public void markComplete(String stepId, String message) {
        ProcessStep step = steps.get(stepId);
        if (step != null) {
            step.markComplete(message);
            smartScrollToBottom();
        } else {
            logger.warning("Step not found: " + stepId);
        }
    }
    
    /**
     * Mark a step as failed
     */
    public void markFailed(String stepId, String reason) {
        ProcessStep step = steps.get(stepId);
        if (step != null) {
            step.markFailed(reason);
            hasFailed.set(true);
            isRunning.set(false);
            smartScrollToBottom();
            // Update button to show OK
            Platform.runLater(() -> {
                actionButton.setText("OK");
                actionButton.setStyle(
                    "-fx-background-color: #3b82f6; " +
                    "-fx-border-color: #3b82f6; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-background-radius: 6; " +
                    "-fx-text-fill: white; " +
                    "-fx-padding: 8 20; " +
                    "-fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 3, 0, 0, 1);"
                );
                actionButton.setDisable(false);
            });
        } else {
            logger.warning("Step not found: " + stepId);
        }
    }
    
    /**
     * Mark entire process as completed successfully
     * This will mark all remaining steps as complete and show OK button
     */
    public void markProcessCompleted(String completionMessage) {
        Platform.runLater(() -> {
            // Mark all waiting/running steps as completed
            for (ProcessStep step : steps.values()) {
                if (step.getStatus() == StepStatus.WAITING || step.getStatus() == StepStatus.RUNNING) {
                    step.markComplete("Completed");
                }
            }
            
            // Update button to show OK
            actionButton.setText("OK");
            actionButton.setStyle(
                "-fx-background-color: #10b981; " +
                "-fx-border-color: #10b981; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 8 20; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.3), 3, 0, 0, 1);"
            );
            actionButton.setDisable(false);
            
            isCompleted.set(true);
            isRunning.set(false);
            smartScrollToBottom();
        });
    }
    
    /**
     * Mark entire process as failed
     * This will mark all remaining steps as failed and show OK button
     */
    public void markProcessFailed(String failureReason) {
        Platform.runLater(() -> {
            // Mark all waiting/running steps as failed
            for (ProcessStep step : steps.values()) {
                if (step.getStatus() == StepStatus.WAITING || step.getStatus() == StepStatus.RUNNING) {
                    step.markFailed("Cancelled due to failure");
                }
            }
            
            // Update button to show OK
            actionButton.setText("OK");
            actionButton.setStyle(
                "-fx-background-color: #ef4444; " +
                "-fx-border-color: #ef4444; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 8 20; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(239, 68, 68, 0.3), 3, 0, 0, 1);"
            );
            actionButton.setDisable(false);
            
            hasFailed.set(true);
            isRunning.set(false);
            smartScrollToBottom();
        });
    }
    
    /**
     * Wait for the dialog to close and return the result
     * @return true if process completed successfully, false if failed or cancelled
     */
    public boolean waitForCompletion() {
        try {
            completionLatch.await();
            return isCompleted.get() && !hasFailed.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Show the process monitor window (non-blocking)
     */
    public void show(Stage parentStage) {
        // Don't set parent to ensure standalone behavior
        dialogStage.show();
        dialogStage.requestFocus(); // Bring window to front
    }
    
    /**
     * Show the process monitor window without parent (standalone)
     */
    public void show() {
        dialogStage.show();
        dialogStage.requestFocus(); // Bring window to front
    }
    
    /**
     * Hide the process monitor window
     */
    public void hide() {
        dialogStage.hide();
    }
    
    /**
     * Minimize the process monitor window
     */
    public void minimize() {
        dialogStage.setIconified(true);
    }
    
    /**
     * Restore the process monitor window from minimized state
     */
    public void restore() {
        dialogStage.setIconified(false);
        dialogStage.show();
        dialogStage.requestFocus();
    }
    
    /**
     * Close the process monitor window immediately
     */
    public void close() {
        dialogStage.close();
        completionLatch.countDown();
    }
    
    /**
     * Check if the window is visible
     */
    public boolean isVisible() {
        return dialogStage.isShowing();
    }
    
    /**
     * Check if the window is minimized
     */
    public boolean isMinimized() {
        return dialogStage.isIconified();
    }
    
    /**
     * Check if the process is still running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Check if any step has failed
     */
    public boolean hasFailed() {
        return hasFailed.get();
    }
    
    /**
     * Check if the entire process has completed successfully
     */
    public boolean isCompleted() {
        return isCompleted.get();
    }
    
    /**
     * Get a specific step
     */
    public ProcessStep getStep(String stepId) {
        return steps.get(stepId);
    }
    
    /**
     * Get all steps
     */
    public Map<String, ProcessStep> getAllSteps() {
        return new HashMap<>(steps);
    }
    
    /**
     * Get the total number of steps
     */
    public int getStepCount() {
        return steps.size();
    }
    
    /**
     * Get the number of completed steps
     */
    public int getCompletedStepCount() {
        return (int) steps.values().stream()
            .filter(step -> step.getStatus() == StepStatus.COMPLETED)
            .count();
    }
    
    /**
     * Get the number of failed steps
     */
    public int getFailedStepCount() {
        return (int) steps.values().stream()
            .filter(step -> step.getStatus() == StepStatus.FAILED)
            .count();
    }
    
    /**
     * Get overall progress percentage (0-100)
     */
    public int getOverallProgress() {
        if (steps.isEmpty()) return 0;
        
        int totalProgress = steps.values().stream()
            .mapToInt(ProcessStep::getProgress)
            .sum();
        
        return totalProgress / steps.size();
    }
    
    /**
     * Get the project name
     */
    public String getProjectName() {
        return projectName;
    }
    
    /**
     * Wait for the dialog to close (legacy method)
     */
    public void waitForClose() {
        waitForCompletion();
    }
    
    /**
     * Reset the process monitor for reuse
     */
    public void reset() {
        isRunning.set(true);
        hasFailed.set(false);
        isCompleted.set(false);
        wasAtBottom = true;
        
        Platform.runLater(() -> {
            // Reset all steps
            for (ProcessStep step : steps.values()) {
                step.updateProgress(0);
            }
            
            // Reset button
            actionButton.setText("Stop");
            actionButton.setStyle(
                "-fx-background-color: #ef4444; " +
                "-fx-border-color: #ef4444; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-text-fill: white; " +
                "-fx-padding: 8 20; " +
                "-fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(239, 68, 68, 0.3), 3, 0, 0, 1);"
            );
            actionButton.setDisable(false);
        });
    }
}

