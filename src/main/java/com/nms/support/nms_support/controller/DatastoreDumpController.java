package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.DataStoreRecord;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.dataStoreTabPack.ParseDataStoreReport;
import com.nms.support.nms_support.service.dataStoreTabPack.ReportGenerator;
import com.nms.support.nms_support.service.dataStoreTabPack.ReportCacheService;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ManageFile;
import com.nms.support.nms_support.service.globalPack.SSHExecutor;
import com.nms.support.nms_support.service.globalPack.UnifiedSSHService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javafx.scene.control.Control;
import com.nms.support.nms_support.service.globalPack.ChangeTrackingService;

public class DatastoreDumpController {
    private static final Logger logger = LoggerUtil.getLogger();
    
    // Public constructor for FXML loading
    public DatastoreDumpController() {
        // Default constructor required for FXML
    }

    @FXML
    public ProgressIndicator loadingIndicator;
    
    @FXML
    private Label loadingTitle;
    
    @FXML
    private Label loadingMessage;
    
    @FXML
    private ProgressBar loadingProgress;

    @FXML
    public Button openReportButton;

    // Keep only datastore user field - other SSH fields are in project details tab
    @FXML
    private TextField datastoreUserField;

    @FXML
    private Button loadButton;
    
    @FXML
    private Label lastGeneratedLabel;

    @FXML
    private TextField column1Filter;

    @FXML
    private TextField column2Filter;

    @FXML
    private TextField column3Filter;

    @FXML
    private TextField column4Filter;

    @FXML
    private TextField column5Filter;

    @FXML
    private TableView<DataStoreRecord> datastoreTable;

    @FXML
    private TableColumn<DataStoreRecord, String> column1;

    @FXML
    private TableColumn<DataStoreRecord, String> column2;

    @FXML
    private TableColumn<DataStoreRecord, String> column3;

    @FXML
    private TableColumn<DataStoreRecord, String> column4;

    @FXML
    private TableColumn<DataStoreRecord, String> column5;

    @FXML
    private StackPane overlayPane; // StackPane to hold the spinner
    
    @FXML
    private VBox mainContainer; // Main container for global click handling

    private final ObservableList<DataStoreRecord> datastoreRecords = FXCollections.observableArrayList();
    
    // Thread management and caching
    private Thread currentReportThread;
    private String currentProcessKey;
    private ReportCacheService reportCacheService;
    private volatile boolean isGeneratingReport = false;
    private volatile boolean userCancelled = false;
    private volatile String activeThreadId = null;

    @FXML
    private void initialize() {
        logger.info("DataStoreDumpController Initialized");

        // Initialize table columns
        column1.setCellValueFactory(new PropertyValueFactory<>("tool"));
        column2.setCellValueFactory(new PropertyValueFactory<>("dataStore"));
        column3.setCellValueFactory(new PropertyValueFactory<>("column"));
        column4.setCellValueFactory(new PropertyValueFactory<>("type"));
        column5.setCellValueFactory(new PropertyValueFactory<>("value"));
        datastoreTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        datastoreTable.setItems(datastoreRecords);

        // Initialize cache service
        reportCacheService = ReportCacheService.getInstance();
        
        // Add event handlers
        loadButton.setOnAction(event -> {
            logger.info("Load Button Clicked");
            handleLoadButton();
        });
        
        openReportButton.setOnAction(actionEvent -> {
            logger.info("Open Report Button Clicked");
            openReport();
        });

        // Implement search and filter functionality
        column1Filter.textProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("Column 1 Filter Changed: " + newValue);
            filterTable();
        });
        column2Filter.textProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("Column 2 Filter Changed: " + newValue);
            filterTable();
        });
        column3Filter.textProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("Column 3 Filter Changed: " + newValue);
            filterTable();
        });
        column4Filter.textProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("Column 4 Filter Changed: " + newValue);
            filterTable();
        });
        column5Filter.textProperty().addListener((observable, oldValue, newValue) -> {
            logger.info("Column 5 Filter Changed: " + newValue);
            filterTable();
        });

        // Bind filter fields width to columns width
        column1Filter.prefWidthProperty().bind(column1.widthProperty());
        column2Filter.prefWidthProperty().bind(column2.widthProperty());
        column3Filter.prefWidthProperty().bind(column3.widthProperty());
        column4Filter.prefWidthProperty().bind(column4.widthProperty());
        column5Filter.prefWidthProperty().bind(column5.widthProperty());

        fitColumns();

        // Add context menu for clearing filters
        ContextMenu contextMenu = new ContextMenu();
        MenuItem clearFiltersItem = new MenuItem("Clear Filter");
        clearFiltersItem.setOnAction(event -> {
            logger.info("Clear Filter Menu Item Clicked");
            clearFilters();
        });
        contextMenu.getItems().add(clearFiltersItem);

        // Set context menu for table
        datastoreTable.setContextMenu(contextMenu);
        
        // Add click handler to close context menu when clicking anywhere
        datastoreTable.setOnMouseClicked(event -> {
            if (contextMenu.isShowing()) {
                contextMenu.hide();
            }
        });
        
        // Add global click handler to close context menu when clicking anywhere in the tab
        mainContainer.setOnMouseClicked(event -> {
            if (contextMenu.isShowing()) {
                contextMenu.hide();
            }
        });

        // Initially hide the spinner
        loadingIndicator.setVisible(false);
        overlayPane.setVisible(false);
        
        // Force scrollbars to always be visible
        Platform.runLater(() -> {
            try {
                // Access the virtual flow and force scrollbar visibility
                javafx.scene.control.ScrollPane scrollPane = (javafx.scene.control.ScrollPane) datastoreTable.lookup(".scroll-pane");
                if (scrollPane != null) {
                    scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.ALWAYS);
                    scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.ALWAYS);
                }
            } catch (Exception e) {
                logger.warning("Could not set scrollbar policy: " + e.getMessage());
            }
        });
    }

    private void openReport() {
        logger.info("Opening Report");
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/datastore_reports/" + "report_" + mainController.getSelectedProject().getName() + ".txt";
        ManageFile.open(dataStorePath);
    }

    /**
     * Called when the datastore explorer tab is selected to refresh data
     */
    public void onTabSelected() {
        logger.info("Datastore explorer tab selected - refreshing data");
        loadProjectDetails();
        
        // Auto-load cached report if available and refresh status
        ProjectEntity project = mainController.getSelectedProject();
        if (project != null) {
            autoLoadCachedReport(project);
            refreshLastGeneratedLabel(project);
        } else {
            lastGeneratedLabel.setText("");
        }
    }

    /**
     * Load project details - specifically the datastore user field
     */
    public void loadProjectDetails() {
        logger.info("Loading project details for datastore explorer tab");
        
        // Start loading mode to prevent change tracking during data loading
        changeTrackingService.startLoading();
        
        ProjectEntity project = mainController.getSelectedProject();
        if (project != null) {
            datastoreUserField.setText(project.getDataStoreUser() != null ? project.getDataStoreUser() : "");
        } else {
            datastoreUserField.clear();
        }
        
        // End loading mode to resume change tracking
        changeTrackingService.endLoading();
    }
    
    /**
     * Save datastore settings to the project
     */
    public void saveDatastoreSettings() {
        logger.info("Saving datastore settings");
        ProjectEntity project = mainController.getSelectedProject();
        if (project != null) {
            project.setDataStoreUser(datastoreUserField.getText());
            boolean success = mainController.projectManager.saveData();
            if (success) {
                logger.info("Datastore settings saved successfully");
            } else {
                logger.warning("Failed to save datastore settings");
            }
        } else {
            logger.warning("No project selected for saving datastore settings");
        }
    }

    private void fitColumns() {
        logger.info("Fitting Columns");
        
        // Unbind any existing bindings first to avoid conflicts
        column1.prefWidthProperty().unbind();
        column2.prefWidthProperty().unbind();
        column3.prefWidthProperty().unbind();
        column4.prefWidthProperty().unbind();
        column5.prefWidthProperty().unbind();
        
        // Unbind filter fields
        column1Filter.prefWidthProperty().unbind();
        column2Filter.prefWidthProperty().unbind();
        column3Filter.prefWidthProperty().unbind();
        column4Filter.prefWidthProperty().unbind();
        column5Filter.prefWidthProperty().unbind();
        
        // Set table to fill available width
        datastoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Bind columns to table width with equal distribution
        double totalColumns = 5.0;
        column1.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column2.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column3.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column4.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column5.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        
        // Bind filter fields to their corresponding columns with exact width matching
        column1Filter.prefWidthProperty().bind(column1.widthProperty());
        column2Filter.prefWidthProperty().bind(column2.widthProperty());
        column3Filter.prefWidthProperty().bind(column3.widthProperty());
        column4Filter.prefWidthProperty().bind(column4.widthProperty());
        column5Filter.prefWidthProperty().bind(column5.widthProperty());
        
        // Ensure table fills available horizontal space
        datastoreTable.setMaxWidth(Double.MAX_VALUE);
        datastoreTable.setMinWidth(0);
    }

    private void clearFilters() {
        logger.info("Clearing filters");
        column1Filter.clear();
        column2Filter.clear();
        column3Filter.clear();
        column4Filter.clear();
        column5Filter.clear();
        datastoreTable.getSortOrder().clear();
        fitColumns();
    }

    private void showSpinner() {
        logger.info("Showing enhanced loading animation");
        overlayPane.setVisible(true);
        loadingIndicator.setVisible(true);
        loadingTitle.setVisible(true);
        loadingMessage.setVisible(true);
        loadingProgress.setVisible(true);
        
        // Initialize progress
        loadingProgress.setProgress(0.0);
        updateLoadingMessage("Initializing connection...", 0.0);
    }

    private void hideSpinner() {
        logger.info("Hiding loading animation");
        loadingIndicator.setVisible(false);
        loadingTitle.setVisible(false);
        loadingMessage.setVisible(false);
        loadingProgress.setVisible(false);
        overlayPane.setVisible(false);
    }
    
    private void updateLoadingMessage(String message, double progress) {
        Platform.runLater(() -> {
            loadingMessage.setText(message);
            loadingProgress.setProgress(progress);
        });
    }

    /**
     * Handle Load button click - always generate new report
     */
    private void handleLoadButton() {
        logger.info("Handling Load button click - generating new report");
        
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            DialogUtil.showError("Invalid Project", "Please select a project first");
            logger.warning("No project selected for report loading");
            return;
        }
        
        // Check if already generating a report
        if (isGeneratingReport) {
            logger.info("Report generation already in progress, cancelling previous and starting new one");
            userCancelled = true; // Mark previous as cancelled
        }
        
        logger.info("Project selected: " + project.getName());
        logger.info("About to call generateReport for project: " + project.getName());
        
        // Always generate new report when Load button is clicked
        generateReport(project);
    }
    
    /**
     * Load cached report data for the current project
     */
    private void loadCachedReport(ProjectEntity project) {
        logger.info("Loading cached report for project: " + project.getName());
        
        ObservableList<DataStoreRecord> cachedData = reportCacheService.getCachedReportData(project.getName());
        if (cachedData != null) {
            datastoreRecords.setAll(cachedData);
            filterTable();
            refreshLastGeneratedLabel(project);
            logger.info("Loaded cached report data for project: " + project.getName());
        } else {
            // Fallback to file loading
            loadFromFile(project);
        }
    }
    
    /**
     * Load report data from file
     */
    private void loadFromFile(ProjectEntity project) {
        loadFromFile(project, true);
    }
    
    /**
     * Load report data from file with option to show warnings
     */
    private void loadFromFile(ProjectEntity project, boolean showWarnings) {
        logger.info("Loading report from file for project: " + project.getName());
        
        ObservableList<DataStoreRecord> fileData = reportCacheService.loadAndCacheReportData(project);
        if (fileData != null) {
            datastoreRecords.setAll(fileData);
            filterTable();
            refreshLastGeneratedLabel(project);
            logger.info("Loaded report data from file for project: " + project.getName());
        } else {
            if (showWarnings) {
                DialogUtil.showError("No Report Available", "No report file found for this project. Generating new report...");
                generateReport(project);
            } else {
                // Silent mode - just log and don't generate report
                logger.info("No report file found for project: " + project.getName() + " (silent mode)");
            }
        }
    }
    
    /**
     * Auto-load cached report when tab is selected
     */
    private void autoLoadCachedReport(ProjectEntity project) {
        logger.info("Auto-loading cached report for project: " + project.getName());
        
        // Check if we have cached data
        if (reportCacheService.hasCachedReportData(project.getName())) {
            loadCachedReport(project);
        } else {
            // Try to load from file in silent mode (no warnings)
            loadFromFile(project, false);
        }
        
        // If no report was loaded, clear the table and show "Report Not Found"
        if (datastoreRecords.isEmpty()) {
            datastoreRecords.clear(); // Ensure table is cleared
            filterTable(); // Refresh the table display
            lastGeneratedLabel.setText("Report Not Found");
        }
    }
    
    /**
     * Generate a new report with thread management
     */
    private void generateReport(ProjectEntity project) {
        logger.info("=== GENERATE REPORT CALLED ===");
        logger.info("Generating new report for project: " + project.getName());
        
        if (!validation(project)) {
            logger.warning("Validation failed for project: " + project.getName());
            return;
        }
        
        logger.info("Validation passed, proceeding with report generation");
        
        // Set generating state
        isGeneratingReport = true;
        userCancelled = false;
        
        // Kill existing process if running
        if (currentProcessKey != null && SSHExecutor.isProcessRunning(currentProcessKey)) {
            logger.info("Killing existing process: " + currentProcessKey);
            SSHExecutor.killProcess(currentProcessKey);
        }
        
        // Interrupt existing thread if running
        if (currentReportThread != null && currentReportThread.isAlive()) {
            logger.info("Interrupting existing report thread");
            currentReportThread.interrupt();
        }
        
        // Generate unique process key and thread ID
        currentProcessKey = "datastore_report_" + project.getName() + "_" + System.currentTimeMillis();
        String threadId = "thread_" + System.currentTimeMillis();
        activeThreadId = threadId;
        
        currentReportThread = new Thread(() -> {
            try {
                logger.info("Starting report generation thread with ID: " + threadId);
                
                // Only show spinner if this is still the active thread
                if (activeThreadId.equals(threadId)) {
                    Platform.runLater(() -> {
                        if (activeThreadId.equals(threadId)) {
                            showSpinner();
                            updateLoadingMessage("Starting report generation...", 0.0);
                        }
                    });
                }
                
                String user = System.getProperty("user.name");
                String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/datastore_reports";
                Path reportPath = Paths.get(dataStorePath);
                
                if (activeThreadId.equals(threadId)) {
                    updateLoadingMessage("Preparing data directory...", 0.1);
                }
                Files.createDirectories(reportPath);
                Thread.sleep(300);
                
                Path reportFilePath = reportPath.resolve("report_" + project.getName() + ".txt");
                FileTime fileTimeBefore = null;
                
                if (Files.exists(reportFilePath)) {
                    fileTimeBefore = Files.getLastModifiedTime(reportFilePath);
                    logger.info("File last modified time before execution: " + fileTimeBefore);
                }
                
                if (activeThreadId.equals(threadId)) {
                    updateLoadingMessage("Executing datastore queries...", 0.3);
                }
                boolean isExecuted = ReportGenerator.execute(project, reportFilePath.toString(), currentProcessKey);
                
                if (!isExecuted) {
                    if (!userCancelled && activeThreadId.equals(threadId)) {
                        Platform.runLater(() -> {
                            if (activeThreadId.equals(threadId)) {
                                String errorDetails = "Command failed execution\n\n" +
                                    "SSH Connection: ✓ Connected successfully\n" +
                                    "Session Management: ✓ Using cached SSHJ session\n" +
                                    "Command Execution: ✗ Failed with exit code 127\n\n" +
                                    "Troubleshooting:\n" +
                                    "1. Check Target User configuration in project settings\n" +
                                    "2. Verify 'Action' command is available for the target user\n" +
                                    "3. Ensure proper sudo elevation is configured\n" +
                                    "4. Check VPN connection and server accessibility";
                                DialogUtil.showDetailedError("Failed Generating Report", errorDetails);
                                hideSpinner();
                            }
                        });
                    }
                    logger.warning("Report generation command failed");
                    return;
                }
                
                if (activeThreadId.equals(threadId)) {
                    updateLoadingMessage("Processing server response...", 0.5);
                }
                
                // Wait for file to be updated
                long stableWaitTime = 3_000;
                long maxWaitTime = 15_000;
                long startTime = System.currentTimeMillis();
                boolean isUpdated = false;
                
                while (System.currentTimeMillis() - startTime < maxWaitTime) {
                    if (Thread.currentThread().isInterrupted() || userCancelled) {
                        logger.info("Report generation thread interrupted or user cancelled");
                        return;
                    }
                    
                    if (Files.exists(reportFilePath)) {
                        FileTime fileTimeAfter = Files.getLastModifiedTime(reportFilePath);
                        
                        if (fileTimeBefore == null || fileTimeAfter.toInstant().isAfter(fileTimeBefore.toInstant())) {
                            fileTimeBefore = fileTimeAfter;
                            long stableStart = System.currentTimeMillis();
                            
                            while (System.currentTimeMillis() - stableStart < stableWaitTime) {
                                if (Thread.currentThread().isInterrupted()) {
                                    logger.info("Report generation thread interrupted during stable wait");
                                    return;
                                }
                                
                                FileTime currentFileTime = Files.getLastModifiedTime(reportFilePath);
                                if (currentFileTime.toInstant().isAfter(fileTimeBefore.toInstant())) {
                                    stableStart = System.currentTimeMillis();
                                    fileTimeBefore = currentFileTime;
                                }
                                Thread.sleep(500);
                            }
                            
                            isUpdated = true;
                            break;
                        }
                    }
                    Thread.sleep(500);
                }
                
                if (!isUpdated) {
                    if (!userCancelled && activeThreadId.equals(threadId)) {
                        Platform.runLater(() -> {
                            if (activeThreadId.equals(threadId)) {
                                DialogUtil.showError("Report Generation Timeout", "Report file was not updated within the expected time.\n1. Is VPN Connected?\n2. Is client running?\n3. Please check URL, username, password...");
                                hideSpinner();
                            }
                        });
                    }
                    logger.warning("File did not update within 15 seconds.");
                    return;
                }
                
                if (activeThreadId.equals(threadId)) {
                    updateLoadingMessage("Parsing datastore records...", 0.8);
                }
                
                if (Files.exists(reportFilePath)) {
                    ObservableList<DataStoreRecord> records = ParseDataStoreReport.parseDSReport(reportFilePath.toString());
                    
                    // Cache the data and metadata
                    reportCacheService.cacheReportData(project.getName(), records);
                    ReportCacheService.ReportMetadata metadata = reportCacheService.getReportFileMetadata(project);
                    if (metadata != null) {
                        reportCacheService.cacheReportMetadata(project.getName(), metadata);
                    }
                    
                    if (activeThreadId.equals(threadId)) {
                        updateLoadingMessage("Loading data into table...", 1.0);
                    }
                    Platform.runLater(() -> {
                        if (activeThreadId.equals(threadId)) {
                            datastoreRecords.setAll(records);
                            filterTable();
                            refreshLastGeneratedLabel(project);
                        }
                    });
                    
                    logger.info("Report generated and cached successfully for project: " + project.getName());
                } else {
                    if (!userCancelled && activeThreadId.equals(threadId)) {
                        Platform.runLater(() -> {
                            if (activeThreadId.equals(threadId)) {
                                DialogUtil.showError("Report Generation Error", "Report file is missing after command execution.\n1. Is VPN Connected?\n2. Is Client Running?\n3. Please check URL, Username, Password and Client user.");
                                hideSpinner();
                            }
                        });
                    }
                    logger.warning("Report file is missing.");
                }
                
            } catch (IOException e) {
                logger.severe("IOException occurred while generating report: " + e.getMessage());
                if (!userCancelled && activeThreadId.equals(threadId)) {
                    Platform.runLater(() -> {
                        if (activeThreadId.equals(threadId)) {
                            // Show detailed error message from ReportGenerator
                            String errorMsg = e.getMessage();
                            if (errorMsg != null && errorMsg.contains("Command failed as user")) {
                                // This is our detailed error from ReportGenerator - use larger dialog
                                DialogUtil.showDetailedError("Report Generation Failed", errorMsg);
                            } else {
                                // Generic error fallback - use regular size
                                DialogUtil.showError("Exception", "An error occurred while generating the report: " + e.getMessage());
                            }
                            hideSpinner();
                        }
                    });
                }
            } catch (InterruptedException e) {
                logger.info("Report generation thread was interrupted");
                Platform.runLater(() -> {
                    if (activeThreadId.equals(threadId)) {
                        hideSpinner();
                    }
                });
            } finally {
                Platform.runLater(() -> {
                    // Only hide spinner and reset state if this is still the active thread
                    if (activeThreadId.equals(threadId)) {
                        hideSpinner();
                        isGeneratingReport = false;
                        currentProcessKey = null;
                        currentReportThread = null;
                        activeThreadId = null;
                    }
                });
            }
        });
        
        currentReportThread.start();
    }
    
    /**
     * Refresh the last generated label with relative time
     */
    private void refreshLastGeneratedLabel(ProjectEntity project) {
        Platform.runLater(() -> {
            ReportCacheService.ReportMetadata metadata = reportCacheService.getCachedReportMetadata(project.getName());
            if (metadata != null) {
                lastGeneratedLabel.setText(metadata.getRelativeTimeAgo());
            } else {
                // Try to get metadata from file
                metadata = reportCacheService.getReportFileMetadata(project);
                if (metadata != null) {
                    lastGeneratedLabel.setText(metadata.getRelativeTimeAgo());
                } else {
                    // Show "Report Not Found" when no report exists
                    lastGeneratedLabel.setText("Report Not Found");
                }
            }
        });
    }

    private void filterTable() {
        logger.info("Filtering table");
        String[] filters = {
                column1Filter.getText(),
                column2Filter.getText(),
                column3Filter.getText(),
                column4Filter.getText(),
                column5Filter.getText()
        };
        ObservableList<DataStoreRecord> filteredData = ParseDataStoreReport.filterRows(datastoreRecords, filters);
        datastoreTable.setItems(filteredData);
        logger.info("Table filtered with criteria: " + String.join(", ", filters));
    }

    MainController mainController;
    private ChangeTrackingService changeTrackingService;

    public void setMainController(MainController mainController) {
        logger.info("Setting Main Controller");
        this.mainController = mainController;
        this.changeTrackingService = ChangeTrackingService.getInstance();
        
        // Register controls for change tracking
        registerControlsForChangeTracking();
        
        // Project selection is now handled centrally by MainController
        // No need for individual listeners here
    }
    
    /**
     * Called by MainController when project selection changes
     * @param newProjectName The newly selected project name
     */
    public void onProjectSelectionChanged(String newProjectName) {
        logger.info("Project ComboBox selection changed to: " + newProjectName);
        if (newProjectName != null && !"None".equals(newProjectName)) {
            // Clear table first to prevent showing old data
            datastoreRecords.clear();
            filterTable();
            lastGeneratedLabel.setText("Loading...");
            
            loadProjectDetails();
            
            // Auto-load cached report for the new project
            ProjectEntity project = mainController.getSelectedProject();
            if (project != null) {
                autoLoadCachedReport(project);
                refreshLastGeneratedLabel(project);
            }
        } else {
            // Clear data when no project is selected
            datastoreRecords.clear();
            filterTable();
            lastGeneratedLabel.setText("");
        }
    }
    
    /**
     * Register controls for change tracking
     */
    private void registerControlsForChangeTracking() {
        if (changeTrackingService != null) {
            Set<Control> controls = new HashSet<>();
            controls.add(datastoreUserField);
            // Note: datastoreTable is not tracked as it's not a persistent setting
            
            changeTrackingService.registerTab("Datastore Explorer", controls);
        }
    }

    /**
     * Validation method that uses project details for SSH fields and local field for datastore user
     */
    private boolean validation(ProjectEntity project) {
        return validation(project, true);
    }
    
    /**
     * Validation method with option to show warnings
     */
    private boolean validation(ProjectEntity project, boolean showWarnings) {
        logger.info("Performing validation using project details and local datastore user field");
        if (project.getHost() == null || project.getHost().trim().isEmpty()) {
            if (showWarnings) {
                DialogUtil.showError("Validation Error", "Host Address is required. Please set it in Project Details tab.");
            }
            logger.warning("Host Address is required.");
            return false;
        }
        
        // Use unified SSH service validation
        if (!UnifiedSSHService.validateProjectAuth(project)) {
            if (showWarnings) {
                DialogUtil.showError("Validation Error", "SSH authentication is not properly configured. Please check your Project Details tab.");
            }
            logger.warning("SSH authentication is not properly configured.");
            return false;
        }
        if (datastoreUserField.getText() == null || datastoreUserField.getText().trim().isEmpty()) {
            if (showWarnings) {
                DialogUtil.showError("Validation Error", "Datastore User is required. Please enter it in the Datastore User field.");
            }
            logger.warning("Datastore User is required.");
            return false;
        }
        logger.info("Validation passed");
        return true;
    }

    /**
     * Reset columns to default equal widths
     */
    private void resetColumnWidths() {
        logger.info("Resetting column widths to default");
        
        // Unbind current width bindings
        column1.prefWidthProperty().unbind();
        column2.prefWidthProperty().unbind();
        column3.prefWidthProperty().unbind();
        column4.prefWidthProperty().unbind();
        column5.prefWidthProperty().unbind();
        
        // Set equal widths
        double defaultWidth = 120.0;
        column1.setPrefWidth(defaultWidth);
        column2.setPrefWidth(defaultWidth);
        column3.setPrefWidth(defaultWidth);
        column4.setPrefWidth(defaultWidth);
        column5.setPrefWidth(defaultWidth);
        
        // Rebind filter fields to maintain synchronization
        column1Filter.prefWidthProperty().bind(column1.widthProperty());
        column2Filter.prefWidthProperty().bind(column2.widthProperty());
        column3Filter.prefWidthProperty().bind(column3.widthProperty());
        column4Filter.prefWidthProperty().bind(column4.widthProperty());
        column5Filter.prefWidthProperty().bind(column5.widthProperty());
    }
}
