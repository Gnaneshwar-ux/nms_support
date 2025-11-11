package com.nms.support.nms_support.controller;


import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.*;
import com.nms.support.nms_support.service.database.DatabaseUserTypeService;
import com.nms.support.nms_support.service.database.FireData;
import com.nms.support.nms_support.service.globalPack.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.scene.control.Control;
import com.nms.support.nms_support.service.globalPack.ChangeTrackingService;
import com.nms.support.nms_support.service.ProjectCodeService;

import static com.nms.support.nms_support.service.globalPack.Checker.isEmpty;

public class BuildAutomation implements Initializable {
    private static final Logger logger = LoggerUtil.getLogger();

    public TextArea buildLog;
    public Button reloadButton;
    public TextField usernameField;
    public ComboBox<String> userTypeComboBox;
    public CheckBox autoLoginCheckBox;
    public CheckBox manageToolCheckBox;
    public Button deleteButton;
    // Removed updateButton - now handled by global save
    public ComboBox<String> buildMode;
    public ComboBox<String> appName;
    public Button buildButton;
    public Button stopButton;
    public Button startButton;
    public Button openNMSLogButton;
    public Button openBuildLogButton;
    public Button restartButton;
    public TextField passwordField;
    public TextField projectLogComboBox;
    public AnchorPane buildRoot;
    public Button reloadUsersButton;
    public TextField jdkPathField;
    public Button jdkBrowseButton;

    private MainController mainController;
    private ControlApp controlApp;
    private ChangeTrackingService changeTrackingService;

    @FXML
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing BuildAutomation controller");
        appendTextToLog("=== APPLICATION MANAGEMENT SYSTEM INITIALIZED ===");
        appendTextToLog("System: " + System.getProperty("os.name") + " | Java: " + System.getProperty("java.version"));

        reloadButton.setOnAction(event -> reloadLogNames());
        // Removed updateButton action - now handled by global save
        deleteButton.setOnAction(event -> deleteSetup());
        buildButton.setOnAction(event -> build());
        stopButton.setOnAction(event -> stopAsync());
        startButton.setOnAction(event -> {
            String selectedApp = getSelectedAppName();
            if (selectedApp != null) {
                start(mainController.getSelectedProject(), selectedApp);
            } else {
                appendTextToLog("ERROR: Application selection required");
                appendTextToLog("DETAILS: No application selected from the Application dropdown");
                appendTextToLog("RESOLUTION: Please select an application from the 'Application' dropdown menu");
            }
        });
        openNMSLogButton.setOnAction(event -> openNMSLog());
        openBuildLogButton.setOnAction(event -> openBuildLog());
        restartButton.setOnAction(event -> restart());
        reloadUsersButton.setOnAction(event -> initializeUserTypes());
        if (jdkBrowseButton != null) {
            jdkBrowseButton.setOnAction(event -> browseJdk());
        }

        buildMode.getItems().addAll("Ant config", "Ant clean config");
        buildMode.getSelectionModel().select(0);
        appName.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> appNameChange(newValue));


        LogNewVersionDetails();
        appendTextToLog("=== READY ===");
        appendTextToLog("");
    }

    public void LogNewVersionDetails() {
        appendTextToLog("Current Version: " + AppDetails.getApplicationVersion());
        // Create a Task to perform the operation on a separate thread
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Perform HTTP request and retrieve data
                HashMap<String, String> vData = FireData.readVersionData();
                if (vData != null) {
                    // Get the version data
                    String latestVersion = vData.get("version");
                    String description = vData.get("description");

                    // Update the UI safely using Platform.runLater
                                         Platform.runLater(() -> {
                         appendTextToLog("Latest Version Available: " + latestVersion);
                         if(latestVersion != null && !latestVersion.isEmpty() && description != null && !description.isEmpty()) {
                             appendTextToLog("New Version Info: \n" + description);
                         }
                         appendTextToLog("");
                     });
                }

                return null;
            }

            @Override
            protected void failed() {
                // Handle any exception
                Throwable exception = getException();
                                 Platform.runLater(() -> {
                     appendTextToLog("Warning: Unable to retrieve version information");
                     appendTextToLog("");
                 });
            }
        };

        // Run the task in a separate thread
        Thread thread = new Thread(task);
        thread.setDaemon(true); // Ensure the thread stops when the application exits
        thread.start();
    }




    private void appNameChange(String newValue) {
        if (newValue != null && !newValue.trim().isEmpty()) {
            ProjectEntity project = mainController.getSelectedProject();
            if (project != null) {
                // Save the selected application to the project entity
                project.setSelectedApplication(newValue);
                logger.info("Application selection changed to: " + newValue + " for project: " + project.getName());
                
                // Changes are automatically tracked by the change tracking service
            }
            
            // Reload user types for the new application
            populateUserTypeComboBox(mainController.getSelectedProject());
        }
    }

    public void setMainController(MainController mainController) {
        logger.info("Setting main controller");
        this.mainController = mainController;
        this.changeTrackingService = ChangeTrackingService.getInstance();
        controlApp = new ControlApp(this, this.mainController.logManager, mainController.projectManager);

        // Project selection is now handled centrally by MainController
        // No need for individual listeners here
        initializeProjectLogComboBox();
        registerControlsForChangeTracking();
    }
    
    /**
     * Called by MainController when project selection changes
     * @param newProjectName The newly selected project name
     */
    public void onProjectSelectionChanged(String newProjectName) {
        if (newProjectName != null && !"None".equals(newProjectName)) {
            loadProjectDetails();
            reloadApplicationDropdown(); // Reload app dropdown when project changes
        }
    }
    
    /**
     * Register controls for change tracking
     */
    private void registerControlsForChangeTracking() {
        if (changeTrackingService != null) {
            Set<Control> controls = new HashSet<>();
            controls.add(usernameField);
            controls.add(passwordField);
            controls.add(userTypeComboBox);
            controls.add(autoLoginCheckBox);
            controls.add(manageToolCheckBox);
            controls.add(projectLogComboBox);
            if (jdkPathField != null) {
                controls.add(jdkPathField);
            }
            // Note: buildMode is not tracked as it's not a persistent setting
            // Note: appName is not tracked as it's a runtime selection, not a saved setting
            
            changeTrackingService.registerTab("Application Management", controls);
        }
    }

    /**
     * Reloads the application dropdown based on the current project's product folder
     */
    public void reloadApplicationDropdown() {
        ProjectEntity project = mainController.getSelectedProject();
        if (project != null && project.getExePath() != null && !project.getExePath().trim().isEmpty()) {
            populateAppNameComboBox(project.getExePath());
            logger.info("Application dropdown reloaded for project: " + project.getName());
        } else {
            // Clear the application dropdown when exe path is null/empty
            clearApplicationDropdown();
            logger.info("Application dropdown cleared - project or exe path is null/empty");
        }
    }
    
    /**
     * Clears the application dropdown and user type combo box
     */
    public void clearApplicationDropdown() {
        // Use Platform.runLater only if we're not on the FX thread
        if (Platform.isFxApplicationThread()) {
            // We're already on the FX thread, update directly
            appName.getItems().clear();
            userTypeComboBox.getItems().clear();
            appName.setValue(null);
            userTypeComboBox.setValue(null);
        } else {
            // We're on a background thread, use Platform.runLater
            Platform.runLater(() -> {
                appName.getItems().clear();
                userTypeComboBox.getItems().clear();
                appName.setValue(null);
                userTypeComboBox.setValue(null);
            });
        }
    }

    /**
     * Called when the application management tab is selected to refresh data
     */
    public void onTabSelected() {
        logger.info("Application management tab selected - refreshing data");
        reloadApplicationDropdown();
        reloadLogNamesCB();
        loadProjectDetails();
    }

    private void initializeProjectLogComboBox() {
        logger.fine("Initializing project log text field");
        // Text field is initialized empty and populated when project is selected
        projectLogComboBox.setText("");
    }

    private String getSelectedAppName() {
        return appName.getSelectionModel().getSelectedItem();
    }

    public void populateUserTypeComboBox(ProjectEntity project) {
        if (project == null) {
            userTypeComboBox.getItems().clear();
            return;
        }

        List<String> types = project.getTypes(getSelectedAppName()) != null ? new ArrayList<>(project.getTypes(getSelectedAppName())) : new ArrayList<>();

        // Preserve current selection if it's still valid
        String currentSelection = userTypeComboBox.getValue();
        boolean shouldPreserveSelection = currentSelection != null && types.contains(currentSelection);

        // Use Platform.runLater only if we're not on the FX thread
        if (Platform.isFxApplicationThread()) {
            // We're already on the FX thread, update directly
            userTypeComboBox.getItems().setAll(types);
            
            if (shouldPreserveSelection) {
                // Keep the current selection if it's still valid
                userTypeComboBox.setValue(currentSelection);
            } else {
                // Fall back to saved value or default
                String prevType = project.getPrevTypeSelected(getSelectedAppName());
                if (prevType != null && types.contains(prevType)) {
                    userTypeComboBox.setValue(prevType);
                } else {
                    String adminType = types.stream()
                            .filter(type -> type.toLowerCase().contains("admin"))
                            .findFirst()
                            .orElse(null);
                    userTypeComboBox.setValue(adminType != null ? adminType : (types.isEmpty() ? null : types.get(0)));
                }
            }
        } else {
            // We're on a background thread, use Platform.runLater
            Platform.runLater(() -> {
                userTypeComboBox.getItems().setAll(types);
                
                if (shouldPreserveSelection) {
                    // Keep the current selection if it's still valid
                    userTypeComboBox.setValue(currentSelection);
                } else {
                    // Fall back to saved value or default
                    String prevType = project.getPrevTypeSelected(getSelectedAppName());
                    if (prevType != null && types.contains(prevType)) {
                        userTypeComboBox.setValue(prevType);
                    } else {
                        String adminType = types.stream()
                                .filter(type -> type.toLowerCase().contains("admin"))
                                .findFirst()
                                .orElse(null);
                        userTypeComboBox.setValue(adminType != null ? adminType : (types.isEmpty() ? null : types.get(0)));
                    }
                }
            });
        }
    }

    public void populateAppNameComboBox(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            clearApplicationDropdown();
            return;
        }

        ProjectEntity project = mainController.getSelectedProject();
        String previousSelection = project != null ? project.getSelectedApplication() : null;

        List<String> exeFiles = new ArrayList<>();
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".exe"));

            if (files != null) {
                for (File file : files) {
                    exeFiles.add(file.getName());
                }
            }
        }

        // Check if the file list has actually changed
        List<String> currentItems = new ArrayList<>();
        if (appName.getItems() != null) {
            currentItems.addAll(appName.getItems());
        }
        
        // Only reload if the file list has actually changed
        if (!currentItems.equals(exeFiles)) {
            // Use Platform.runLater only if we're not on the FX thread
            if (Platform.isFxApplicationThread()) {
                // We're already on the FX thread, update directly
                appName.setItems(FXCollections.observableArrayList(exeFiles));
                selectApplicationFromList(exeFiles, previousSelection, project);
            } else {
                // We're on a background thread, use Platform.runLater
                Platform.runLater(() -> {
                    appName.setItems(FXCollections.observableArrayList(exeFiles));
                    selectApplicationFromList(exeFiles, previousSelection, project);
                });
            }
        }
    }

    private void selectApplicationFromList(List<String> exeFiles, String previousSelection, ProjectEntity project) {
        if (exeFiles.isEmpty()) {
            // Clear user type combo box when no applications found
            userTypeComboBox.getItems().clear();
            userTypeComboBox.setValue(null);
            return;
        }

        String selectedApp = null;

        // Priority 1: Try to select the previously selected application
        if (previousSelection != null && exeFiles.contains(previousSelection)) {
            selectedApp = previousSelection;
            logger.info("Selected previously chosen application: " + selectedApp);
        }
        // Priority 2: If no previous selection, try to find exe with "workspace" in name
        else if (previousSelection == null) {
            selectedApp = exeFiles.stream()
                    .filter(exe -> exe.toLowerCase().contains("workspace"))
                    .findFirst()
                    .orElse(null);
            
            if (selectedApp != null) {
                logger.info("Selected application with 'workspace' in name: " + selectedApp);
                // Save this selection to the project
                if (project != null) {
                    project.setSelectedApplication(selectedApp);
                }
            }
        }

        // Priority 3: If still no selection, select the first available exe
        if (selectedApp == null) {
            selectedApp = exeFiles.get(0);
            logger.info("Selected first available application: " + selectedApp);
            // Save this selection to the project
            if (project != null) {
                project.setSelectedApplication(selectedApp);
            }
        }

        // Set the selection
        appName.getSelectionModel().select(selectedApp);
        
        // Update the project entity with the final selection
        if (project != null && !selectedApp.equals(project.getSelectedApplication())) {
            project.setSelectedApplication(selectedApp);
        }
    }

    public void loadProjectDetails() {
        ProjectEntity selectedProject = mainController.getSelectedProject();
        if (selectedProject == null) {
            logger.warning("No project selected for loading details");
            return;
        }
        
        logger.fine("Loading project details for: " + selectedProject.getName());
        
        // Start loading mode to prevent change tracking during data loading
        changeTrackingService.startLoading();
        
        try {
            clearFields();
            ProjectEntity project = selectedProject;
            if (project != null) {
                // Safely handle null values
                usernameField.setText(project.getUsername() != null ? project.getUsername() : "");
                passwordField.setText(project.getPassword() != null ? project.getPassword() : "");
                
                // Safely parse boolean values with fallbacks
                try {
                    autoLoginCheckBox.setSelected(project.getAutoLogin() != null ? Boolean.parseBoolean(project.getAutoLogin()) : false);
                } catch (Exception e) {
                    logger.warning("Failed to parse autoLogin value: " + project.getAutoLogin() + ", using default false");
                    autoLoginCheckBox.setSelected(false);
                }
                
                try {
                    manageToolCheckBox.setSelected(project.getManageTool() != null ? Boolean.parseBoolean(project.getManageTool()) : true);
                } catch (Exception e) {
                    logger.warning("Failed to parse manageTool value: " + project.getManageTool() + ", using default true");
                    manageToolCheckBox.setSelected(true);
                }
                
                projectLogComboBox.setText(project.getLogId() != null ? project.getLogId() : "");
                if (jdkPathField != null) {
                    jdkPathField.setText(project.getJdkHome() != null ? project.getJdkHome() : "");
                }
                
                // Only populate usertype if it's empty or needs initialization
                if (userTypeComboBox.getItems().isEmpty()) {
                    populateUserTypeComboBox(project);
                }
                reloadApplicationDropdown();
            }
        } catch (Exception e) {
            logger.severe("Error loading project details: " + e.getMessage());
        } finally {
            // End loading mode to resume change tracking after all population is complete
            changeTrackingService.endLoading();
        }
    }

    public void clearFields() {
        logger.fine("Clearing fields");
        usernameField.clear();
        passwordField.clear();
        autoLoginCheckBox.setSelected(false);
        manageToolCheckBox.setSelected(true); // Default to checked for new projects
        projectLogComboBox.setText("");
        userTypeComboBox.getItems().clear();
        userTypeComboBox.setValue(null);
        appName.getItems().clear();
        appName.setValue(null);
        if (jdkPathField != null) jdkPathField.clear();
    }

    private void restart() {
        clearLog();
        logger.info("Initiating application restart sequence");
        appendTextToLog("=== RESTART SEQUENCE STARTED ===");
        
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            appendTextToLog("Error: No project selected");
            logger.warning("Restart operation failed - no project selected");
            return;
        }
        
        String app = getSelectedAppName();
        if (app == null || app.trim().isEmpty()) {
            appendTextToLog("Error: No application selected");
            logger.warning("Restart operation failed - no application selected");
            return;
        }
        
        appendTextToLog("Project: " + project.getName() + " | App: " + app + " | Mode: " + buildMode.getValue());
        appendTextToLog("Auto Login: " + (autoLoginCheckBox.isSelected() ? "ON" : "OFF") + " | Manage Tool: " + (manageToolCheckBox.isSelected() ? "ON" : "OFF"));
        
        // Cancel existing operations
        if(ControlApp.restartThread != null){
            ControlApp.restartThread.interrupt();
            ControlApp.restartThread = null;
        }
        
        String projectName = project.getName();
        if (controlApp.isBuildRunning(projectName)) {
            appendTextToLog("Warning: Cancelling previous build for " + projectName);
            controlApp.cancelBuild(projectName);
        }
        
        appendTextToLog("Step 1: Stopping application...");
        if (!stop()) {
            appendTextToLog("  → Failed to stop application");
            return;
        }
        appendTextToLog("  → Stopped successfully");
        
        try {
            appendTextToLog("Step 2: Saving settings...");
            saveBuildAutomationSettings(true);
            
            appendTextToLog("Step 3: Auto-login setup...");
            boolean res = setupAutoLogin(project);
            if(!autoLoginCheckBox.isSelected()){
                appendTextToLog("  → Skipped (not enabled)");
            }
            else if(res) {
                appendTextToLog("  → Completed");
            } else {
                appendTextToLog("  → Failed");
                return;
            }
            
            appendTextToLog("Step 4: Restart tools setup...");
            if(manageToolCheckBox.isSelected()){
                res = setupRestartTools(project);
                if(res) {
                    appendTextToLog("  → Completed");
                } else {
                    appendTextToLog("  → Failed");
                    return;
                }
            } else {
                appendTextToLog("  → Skipped (not enabled)");
            }
            
            appendTextToLog("Step 5: Starting build...");
            logger.info("Building project: " + project.getName());
            Task<Boolean> buildTask = controlApp.build(project, buildMode.getValue());

            attachDeleteProcess(buildTask, project, app, true, manageToolCheckBox.isSelected());

        } catch (InterruptedException e) {
            appendTextToLog("ERROR: Restart sequence interrupted");
            appendTextToLog("DETAILS: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            appendTextToLog("RESOLUTION: Restart operation was cancelled by user or system");
            LoggerUtil.error(e);
        }
    }

    private void attachDeleteProcess(Task<Boolean> buildTask, ProjectEntity project, String app, boolean isRestart, boolean autoLoginEnabled){
        buildTask.setOnSucceeded(event -> {
            Boolean result = buildTask.getValue();
            logger.info("Build task succeeded for project: " + project.getName() + " with result: " + result);
            
            // Perform cleanup operations first
            appendTextToLog("Step 6: Cleanup...");
            try {
                logger.info("Starting cleanup operations for project: " + project.getName());
                DeleteLoginSetup.delete(project, this, false);
                if(autoLoginEnabled) {
                    logger.info("Deleting restart tools setup for project: " + project.getName());
                    DeleteRestartSetup.delete(project, this, false);
                }
                logger.info("Cleanup operations completed successfully for project: " + project.getName());
                appendTextToLog("  → Completed");
            } catch (IOException e) {
                logger.severe("Cleanup operations failed for project: " + project.getName());
                appendTextToLog("  → Failed");
                LoggerUtil.error(e);
                throw new RuntimeException(e);
            }
            
            // Show final result at the end (after cleanup)
            if (result) {
                logger.info("BUILD COMPLETED SUCCESSFULLY for project: " + project.getName());
                appendTextToLog("=== BUILD COMPLETED SUCCESSFULLY ===");
                appendTextToLog("DETAILS: Build process completed without errors");
                appendTextToLog("RESULT: Project has been built successfully and is ready for deployment");
                if(isRestart) {
                    logger.info("Starting application for project: " + project.getName());
                    appendTextToLog("Starting application...");
                    start(project, app);
                }
            } else {
                logger.severe("BUILD FAILED for project: " + project.getName());
                appendTextToLog("=== BUILD FAILED ===");
                appendTextToLog("DETAILS: Build process completed with errors");
                appendTextToLog("RESOLUTION: Check build logs for specific error messages and verify configuration");
            }
        });

        buildTask.setOnFailed(event -> {
            Boolean result = buildTask.getValue();
            logger.severe("Build task failed for project: " + project.getName() + " with result: " + result);
            
            // Perform cleanup operations first
            appendTextToLog("Step 6: Cleanup (after failure)...");
            try {
                logger.info("Starting cleanup operations after failure for project: " + project.getName());
                DeleteLoginSetup.delete(project, this, false);
                if(autoLoginEnabled) {
                    logger.info("Deleting restart tools setup after failure for project: " + project.getName());
                    DeleteRestartSetup.delete(project, this, false);
                }
                logger.info("Cleanup operations completed after failure for project: " + project.getName());
                appendTextToLog("  → Completed");
            } catch (IOException e) {
                logger.severe("Cleanup operations failed after build failure for project: " + project.getName());
                appendTextToLog("  → Failed");
                LoggerUtil.error(e);
                throw new RuntimeException(e);
            }
            
            // Show final result at the end (after cleanup)
            if (result != null && result) {
                logger.info("BUILD COMPLETED SUCCESSFULLY after failure recovery for project: " + project.getName());
                appendTextToLog("=== BUILD COMPLETED SUCCESSFULLY ===");
                appendTextToLog("DETAILS: Build process completed without errors");
                appendTextToLog("RESULT: Project has been built successfully and is ready for deployment");
                if(isRestart) {
                    logger.info("Starting application after failure recovery for project: " + project.getName());
                    appendTextToLog("Starting application...");
                    start(project, app);
                }
            } else {
                logger.severe("BUILD FAILED after failure recovery for project: " + project.getName());
                appendTextToLog("=== BUILD FAILED ===");
                appendTextToLog("DETAILS: Build process failed with errors");
                appendTextToLog("POSSIBLE CAUSES:");
                appendTextToLog("• Missing dependencies or libraries");
                appendTextToLog("• Incorrect JConfig path or configuration");
                appendTextToLog("• Environment variable issues");
                appendTextToLog("• Permission or access rights problems");
                appendTextToLog("RESOLUTION: Check build logs for specific error messages and verify configuration");
            }
        });

        buildTask.setOnCancelled(event -> {
            Boolean result = buildTask.getValue();
            logger.warning("Build task cancelled for project: " + project.getName() + " with result: " + result);
            
            // Perform cleanup operations first
            appendTextToLog("Step 6: Cleanup (after cancellation)...");
            try {
                logger.info("Starting cleanup operations after cancellation for project: " + project.getName());
                DeleteLoginSetup.delete(project, this, false);
                if(autoLoginEnabled) {
                    logger.info("Deleting restart tools setup after cancellation for project: " + project.getName());
                    DeleteRestartSetup.delete(project, this, false);
                }
                logger.info("Cleanup operations completed after cancellation for project: " + project.getName());
                appendTextToLog("  → Completed");
            } catch (IOException e) {
                logger.severe("Cleanup operations failed after cancellation for project: " + project.getName());
                appendTextToLog("  → Failed");
                LoggerUtil.error(e);
                throw new RuntimeException(e);
            }
            
            // Show final result at the end (after cleanup)
            if (result != null && result) {
                appendTextToLog("=== BUILD COMPLETED SUCCESSFULLY ===");
                appendTextToLog("DETAILS: Build process completed without errors");
                appendTextToLog("RESULT: Project has been built successfully and is ready for deployment");
                if(isRestart) {
                    appendTextToLog("Starting application...");
                    start(project, app);
                }
            } else {
                appendTextToLog("=== BUILD CANCELLED ===");
                appendTextToLog("DETAILS: Build process was cancelled by user or system");
                appendTextToLog("RESULT: Build operation was interrupted and may be incomplete");
            }
        });
    }

    private void openBuildLog() {
        logger.info("Opening build log");
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/build_logs/" + "buildlog_" + mainController.getSelectedProject().getName() + ".log";
        ManageFile.open(dataStorePath);
    }

    private void openNMSLog() {
        logger.info("Opening NMS log");
        clearLog();
        controlApp.viewLog(appName.getSelectionModel().getSelectedItem(), mainController.getSelectedProject());
    }

    private void start(ProjectEntity project, String app) {
        logger.info("Starting application: " + app + " for project: " + project.getName());
        clearLog();
        appendTextToLog("=== STARTING APPLICATION ===");
        appendTextToLog("Project: " + project.getName() + " | App: " + app);
        
        try {
            // Validate custom JDK if provided
            if (jdkPathField != null) {
                String raw = jdkPathField.getText() != null ? jdkPathField.getText().trim() : "";
                if (!raw.isEmpty()) {
                    String jdkHome = JavaEnvUtil.normalizeJdkHome(raw).orElse(raw);
                    if (!JavaEnvUtil.validateJdk(jdkHome)) {
                        appendTextToLog("ERROR: Invalid JDK path provided. Expecting a folder containing bin/java.exe");
                        DialogUtil.showWarning("Invalid JDK", "Invalid JDK path. Please select a valid JDK home (contains bin/java.exe) or clear the field to use system Java.");
                        return;
                    }
                }
            }
            // appendTextToLog("Step 1: Validating configuration...");
            // Validation.ValidationResult validationResult = Validation.validateDetailed(project, app);
            // if (validationResult.isValid()) {
            //     appendTextToLog("  → Configuration validation passed");
            // } else {
            //     appendTextToLog("  → Configuration validation failed");
            //     appendTextToLog("  → Missing or invalid fields:");
            //     for (String error : validationResult.getErrors()) {
            //         appendTextToLog("    • " + error);
            //     }
            //     appendTextToLog("  → Please check the following fields in Project Configuration:");
            //     appendTextToLog("    • Project Code (JConfig Path)");
            //     appendTextToLog("    • Username");
            //     appendTextToLog("    • Password");
            //     appendTextToLog("    • User Type (for selected application)");
            //     appendTextToLog("    • Auto Login setting");
            //     appendTextToLog("  → Resolution: Fill in all required fields and try again");
            //     return;
            // }
            
            appendTextToLog("Step 1: Updating credentials...");
            if (WritePropertiesFile.updateFile(project, app)) {
                appendTextToLog("  → Credentials file updated successfully");
            } else {
                appendTextToLog("  → Failed to update credentials file");
                appendTextToLog("  → Possible causes:");
                appendTextToLog("    • Invalid JConfig path");
                appendTextToLog("    • Missing or inaccessible target directory");
                appendTextToLog("    • File permission issues");
                appendTextToLog("  → Resolution: Verify JConfig path and file permissions");
                return;
            }
            
            appendTextToLog("Step 2: Starting application...");
            controlApp.start(app, project);
            
        } catch (IOException e) {
            appendTextToLog("ERROR: IOException during application start");
            appendTextToLog("  → Error: " + e.getMessage());
            appendTextToLog("  → Possible causes:");
            appendTextToLog("    • File system access issues");
            appendTextToLog("    • Invalid file paths");
            appendTextToLog("    • Permission denied errors");
            appendTextToLog("  → Resolution: Check file paths and permissions");
            LoggerUtil.error(e);
        } catch (InterruptedException e) {
            appendTextToLog("ERROR: Application start operation interrupted");
            appendTextToLog("  → Error: " + e.getMessage());
            appendTextToLog("  → Resolution: Try starting the application again");
            LoggerUtil.error(e);
        }
    }

    private boolean stop() {
        logger.info("Stopping application");
        clearLog();
        appendTextToLog("=== STOPPING APPLICATION ===");
        
        String selectedApp = appName.getSelectionModel().getSelectedItem();
        if (selectedApp == null || selectedApp.trim().isEmpty()) {
            appendTextToLog("Error: No application selected");
            logger.warning("Stop operation failed - no application selected");
            return false;
        }
        
        appendTextToLog("App: " + selectedApp + " | Project: " + (mainController.getSelectedProject() != null ? mainController.getSelectedProject().getName() : "None"));
        
        try {
            boolean result = controlApp.stopProject(selectedApp, mainController.getSelectedProject());
            
            if (result) {
                appendTextToLog("Application stopped successfully");
            } else {
                appendTextToLog("Warning: No running processes found");
            }
            
            return result;
        } catch (IOException e) {
            appendTextToLog("Error: " + e.getMessage());
            logger.severe("IOException in stop() method: " + e.getMessage());
            LoggerUtil.error(e);
            return false;
        } catch (Exception e) {
            appendTextToLog("Error: " + e.getMessage());
            logger.severe("Unexpected error in stop() method: " + e.getMessage());
            LoggerUtil.error(e);
            return false;
        }
    }

    private void stopAsync() {
        // Run the heavy stop flow off the JavaFX Application Thread to keep UI responsive
        // Disable the stop button while work is in progress
        if (stopButton != null) {
            stopButton.setDisable(true);
        }
        Thread worker = new Thread(() -> {
            try {
                stop();
            } finally {
                Platform.runLater(() -> {
                    if (stopButton != null) {
                        stopButton.setDisable(false);
                    }
                });
            }
        }, "StopOperationWorker");
        worker.setDaemon(true);
        worker.start();
    }
    public void build() {
        ProjectEntity project = mainController.getSelectedProject();
        String app = getSelectedAppName();
        
        if (project == null) {
            appendTextToLog("ERROR: Project selection required for build operation");
            appendTextToLog("DETAILS: No project selected from the Project dropdown");
            appendTextToLog("RESOLUTION: Please select a project from the 'Project' dropdown menu");
            return;
        }
        
        if (app == null || app.trim().isEmpty()) {
            appendTextToLog("ERROR: Application selection required for build operation");
            appendTextToLog("DETAILS: No application selected from the Application dropdown");
            appendTextToLog("RESOLUTION: Please select an application from the 'Application' dropdown menu");
            return;
        }
        
        try {
            // Validate custom JDK if provided
            if (jdkPathField != null) {
                String raw = jdkPathField.getText() != null ? jdkPathField.getText().trim() : "";
                if (!raw.isEmpty()) {
                    String jdkHome = JavaEnvUtil.normalizeJdkHome(raw).orElse(raw);
                    if (!JavaEnvUtil.validateJdk(jdkHome)) {
                        appendTextToLog("ERROR: Invalid JDK path provided. Expecting a folder containing bin/java.exe");
                        DialogUtil.showWarning("Invalid JDK", "Invalid JDK path. Please select a valid JDK home (contains bin/java.exe) or clear the field to use system Java.");
                        return;
                    }
                }
            }
            clearLog();
            appendTextToLog("=== BUILD PROCESS STARTED ===");
            appendTextToLog("Project: " + project.getName() + " | App: " + app + " | Mode: " + buildMode.getValue());
            appendTextToLog("Auto Login: " + (autoLoginCheckBox.isSelected() ? "ON" : "OFF") + " | Manage Tool: " + (manageToolCheckBox.isSelected() ? "ON" : "OFF"));
            
            // Check for existing builds
            String projectName = project.getName();
            if (controlApp.isBuildRunning(projectName)) {
                appendTextToLog("Warning: Cancelling previous build for " + projectName);
                controlApp.cancelBuild(projectName);
            }
            
            appendTextToLog("Step 1: Auto-login setup...");
            boolean res = setupAutoLogin(project);
            if(!autoLoginCheckBox.isSelected()){
                appendTextToLog("  → Skipped (not enabled)");
            }
            else if(res) {
                appendTextToLog("  → Completed");
            } else {
                appendTextToLog("  → Failed");
                return;
            }
            
            appendTextToLog("Step 2: Restart tools setup...");
            if(manageToolCheckBox.isSelected()){
                res = setupRestartTools(project);
                if(res) {
                    appendTextToLog("  → Completed");
                } else {
                    appendTextToLog("  → Failed");
                    return;
                }
            } else {
                appendTextToLog("  → Skipped (not enabled)");
            }
            
            appendTextToLog("Step 3: Starting build...");
            logger.info("Building project: " + project.getName());
            Task<Boolean> buildTask = controlApp.build(project, buildMode.getValue());
            attachDeleteProcess(buildTask, project, app, false, manageToolCheckBox.isSelected());
            
        } catch (InterruptedException e) {
            appendTextToLog("ERROR: Build process interrupted");
            appendTextToLog("DETAILS: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            appendTextToLog("RESOLUTION: Build operation was cancelled by user or system");
            logger.severe("InterruptedException in build() method: " + e.getMessage());
            LoggerUtil.error(e);
        }
    }

    private void deleteSetup() {
        logger.info("Deleting setup for project: " + mainController.getSelectedProject());
        //clearLog();
        try {
            // Show single confirmation dialog for both auto-login and restart tools cleanup
            Optional<ButtonType> confirmation = DialogUtil.showConfirmationDialog(
                "Confirm Delete", 
                "Are you sure you want to delete all configuration setups?", 
                "This operation will remove both auto-login and restart tools configurations for this project."
            );
            
            if (confirmation.isPresent() && confirmation.get() == ButtonType.OK) {
                DeleteLoginSetup.delete(mainController.getSelectedProject(), this, false);
                DeleteRestartSetup.delete(mainController.getSelectedProject(), this, false);
                appendTextToLog("All configurations deleted successfully");
            } else {
                appendTextToLog("Delete operation cancelled");
            }
        } catch (IOException e) {
            logger.severe("IOException in deleteSetup() method: " + e.getMessage());
            LoggerUtil.error(e);
        }
    }

    /**
     * Save method that can be called from the global save button
     * This method saves the current application management settings to the project
     */
    public void saveBuildAutomationSettings() {
        saveBuildAutomationSettings(false);
    }

    /**
     * Save method that can be called from the global save button
     * This method saves the current build automation settings to the project
     * @param showValidationPopup Whether to show validation popup for missing user type
     */
    public void saveBuildAutomationSettings(boolean showValidationPopup) {
        logger.info("Saving application management settings for project: " + mainController.getSelectedProject());
        clearLog();
        
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            logger.warning("No project selected for saving application management settings");
            appendTextToLog("No project selected");
            return;
        }

        // Get the project code from the text field
        String projectCode = projectLogComboBox.getText();
        
        // Update project with current settings
        project.setAutoLogin(Boolean.toString(autoLoginCheckBox.isSelected()));
        project.setManageTool(Boolean.toString(manageToolCheckBox.isSelected()));
        project.setLogId(projectCode);
        project.setUsername(usernameField.getText());
        project.setPassword(passwordField.getText());
        if (jdkPathField != null) {
            String raw = jdkPathField.getText() != null ? jdkPathField.getText().trim() : "";
            Optional<String> norm = JavaEnvUtil.normalizeJdkHome(raw);
            project.setJdkHome(norm.orElse(raw));
        }
        if (userTypeComboBox.getValue() != null) {
            project.setPrevTypeSelected(getSelectedAppName(), userTypeComboBox.getValue());
        }
        
        // Save to database
        boolean res = mainController.projectManager.saveData();
        if(res){
            appendTextToLog("Settings saved successfully");
        } else {
            appendTextToLog("Failed to save settings");
        }

        logger.info("Application management settings updated for project: " + project.getName());
        
        // Update cred.properties file
        String selectedApp = getSelectedAppName();
        if (selectedApp != null) {
            if (WritePropertiesFile.updateFile(project, selectedApp)) {
                appendTextToLog("Credentials file updated");
            } else {
                appendTextToLog("Failed to update credentials file");
            }
        } else {
            appendTextToLog("Skipping credentials update - no application selected");
        }
    }

    private void browseJdk() {
        try {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select JDK Home (contains bin/java.exe)");
            // Choose a sensible default directory
            File initial = null;
            try {
                // 1) Current value if valid directory
                if (jdkPathField != null && jdkPathField.getText() != null) {
                    File current = new File(jdkPathField.getText().trim());
                    if (current.exists() && current.isDirectory()) initial = current;
                    // If user typed bin/java.exe or bin, go up to JDK home
                    else if (current.isFile() && current.getName().equalsIgnoreCase("java.exe") && current.getParentFile() != null && current.getParentFile().getParentFile() != null) {
                        initial = current.getParentFile().getParentFile();
                    } else if (current.isDirectory() && current.getName().equalsIgnoreCase("bin") && current.getParentFile() != null) {
                        initial = current.getParentFile();
                    }
                }
                // 2) Common install locations
                if (initial == null) {
                    String[] candidates = new String[] {
                            System.getenv("ProgramFiles") + File.separator + "Java",
                            System.getenv("ProgramFiles(x86)") + File.separator + "Java",
                            System.getenv("ProgramFiles") + File.separator + "Eclipse Adoptium",
                            System.getProperty("user.home") + File.separator + ".jdks",
                            System.getProperty("user.home") + File.separator + "scoop" + File.separator + "apps" + File.separator + "jdk"
                    };
                    for (String c : candidates) {
                        if (c != null) {
                            File f = new File(c);
                            if (f.exists() && f.isDirectory()) { initial = f; break; }
                        }
                    }
                }
            } catch (Exception ignore) { }
            if (initial != null) {
                try { chooser.setInitialDirectory(initial); } catch (Exception ignore) { }
            }
            File selected = chooser.showDialog(buildRoot != null ? buildRoot.getScene().getWindow() : null);
            if (selected == null) return;
            String chosen = selected.getAbsolutePath();
            Optional<String> norm = JavaEnvUtil.normalizeJdkHome(chosen);
            String jdkHome = norm.orElse(chosen);
            if (JavaEnvUtil.validateJdk(jdkHome)) {
                jdkPathField.setText(jdkHome);
                Optional<String> ver = JavaEnvUtil.detectVersion(jdkHome);
                ver.ifPresent(v -> appendTextToLog("JDK selected: " + jdkHome + "\n" + v));
            } else {
                DialogUtil.showWarning("Invalid JDK", "Selected path is not a valid JDK. Expecting a folder containing bin/java.exe");
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
            appendTextToLog("ERROR: Failed to browse JDK - " + e.getMessage());
        }
    }

    private void initializeUserTypes(){
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project", "Please select a project first.");
            return;
        }
        
        // Check if database connection details are available
        if (project.getDbHost() == null || project.getDbHost().trim().isEmpty() ||
            project.getDbUser() == null || project.getDbUser().trim().isEmpty() ||
            project.getDbPassword() == null || project.getDbPassword().trim().isEmpty() ||
            project.getDbSid() == null || project.getDbSid().trim().isEmpty()) {
            
            // Fall back to original text processing method
            appendTextToLog("Database connection details not available, using text processing method");
            SetupAutoLogin.loadUserTypes(project);
            populateUserTypeComboBox(project);
            return;
        }
        
        // Use database approach
        appendTextToLog("Attempting to fetch usertypes from database...");
        
        // Disable button during operation
        reloadUsersButton.setDisable(true);
        
        // Show progress dialog
        ProgressDialog progressDialog = new ProgressDialog();
        progressDialog.setTitle("Fetching UserTypes");
        progressDialog.setHeaderText("Connecting to database and fetching usertype data...");
        progressDialog.setContentText("Please wait...");
        progressDialog.show();
        
        // Run database fetch in background thread
        new Thread(() -> {
            try {
                // Test database connection first
                boolean isValid = DatabaseUserTypeService.validateDatabaseSetup(
                    project.getDbHost(), project.getDbPort(), project.getDbSid(), 
                    project.getDbUser(), project.getDbPassword());
                
                if (!isValid) {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        appendTextToLog("Database connection failed, falling back to text processing method");
                        SetupAutoLogin.loadUserTypes(project);
                        populateUserTypeComboBox(project);
                    });
                    return;
                }
                
                // Fetch usertypes from database
                HashMap<String, List<String>> userTypes = DatabaseUserTypeService.fetchUserTypesFromDatabase(
                    project.getDbHost(), project.getDbPort(), project.getDbSid(), 
                    project.getDbUser(), project.getDbPassword());
                
                Platform.runLater(() -> {
                    progressDialog.close();
                    
                    if (userTypes != null && !userTypes.isEmpty()) {
                        // Update project with fetched usertypes
                        project.setTypes(userTypes);
                        
                        // Save to database
                        mainController.projectManager.saveData();
                        
                        // Show success message with summary
                        StringBuilder summary = new StringBuilder();
                        summary.append("Successfully fetched usertypes from database!\n\n");
                        summary.append("Summary:\n");
                        for (String appName : userTypes.keySet()) {
                            List<String> types = userTypes.get(appName);
                            summary.append("• ").append(appName).append(": ").append(types.size()).append(" types\n");
                        }
                        summary.append("\nUsertypes have been saved to the project.");
                        
                        appendTextToLog(summary.toString());
                        
                        // Populate the combobox
                        populateUserTypeComboBox(project);
                        
                        DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Fetch Successful", 
                            "Successfully fetched " + userTypes.size() + " application types from database.");
                    } else {
                        appendTextToLog("No usertype data found in database, falling back to text processing method");
                        SetupAutoLogin.loadUserTypes(project);
                        populateUserTypeComboBox(project);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressDialog.close();
                    appendTextToLog("Database fetch failed: " + e.getMessage() + ", falling back to text processing method");
                    SetupAutoLogin.loadUserTypes(project);
                    populateUserTypeComboBox(project);
                });
            } finally {
                Platform.runLater(() -> {
                    reloadUsersButton.setDisable(false);
                });
            }
        }, "database-fetch-thread").start();
    }

    private boolean setupAutoLogin(ProjectEntity updatedProject){
        if (autoLoginCheckBox.isSelected()) {
            try {
                appendTextToLog("  → Validating auto-login requirements...");
                
                // Collect all validation errors
                List<String> allErrors = new ArrayList<>();
                
                // Check for missing user type
                if (userTypeComboBox.getValue() == null) {
                    allErrors.add("User type is missing or empty - please load and select a user type");
                }
                
                // Check other auto-login requirements
                Validation.ValidationResult validationResult = Validation.validateForAutologinDetailed(updatedProject);
                if (!validationResult.isValid()) {
                    allErrors.addAll(validationResult.getErrors());
                }
                
                // If there are any errors, show comprehensive dialog
                if (!allErrors.isEmpty()) {
                    appendTextToLog("  → Auto-login validation failed");
                    appendTextToLog("  → Missing required fields:");
                    for (String error : allErrors) {
                        appendTextToLog("    • " + error);
                    }
                    appendTextToLog("  → Please check the following fields:");
                    appendTextToLog("    • Project Code (JConfig Path)");
                    appendTextToLog("    • Username");
                    appendTextToLog("    • Password");
                    appendTextToLog("    • User type (load and select from dropdown)");
                    appendTextToLog("    • Auto login setting");
                    appendTextToLog("    • Project Code");
                    appendTextToLog("  → Resolution: Fill in all required fields and try again");
                    
                    // Create comprehensive error message for dialog
                    StringBuilder errorMessage = new StringBuilder();
                    errorMessage.append("Auto-login setup requires the following fields to be configured:\n\n");
                    for (String error : allErrors) {
                        errorMessage.append("• ").append(error).append("\n");
                    }
                    errorMessage.append("\nPlease configure these fields in the Project Configuration tab and try again.");
                    
                    DialogUtil.showWarning("Auto-login Setup Requirements", errorMessage.toString());
                    return false;
                }
                appendTextToLog("  → Auto-login validation passed");
                
                appendTextToLog("  → Checking existing auto-login setup...");
                Validation.ValidationResult setupValidation = Validation.validateLoginSetupDetailed(updatedProject);
                if (setupValidation.isValid()) {
                    appendTextToLog("  → Auto-login setup already exists and is valid");
                    return true;
                } else {
                    appendTextToLog("  → Auto-login setup validation failed");
                    appendTextToLog("  → Missing setup components:");
                    for (String error : setupValidation.getErrors()) {
                        appendTextToLog("    • " + error);
                    }
                    appendTextToLog("  → Installing auto-login setup...");
                }
                
                boolean res = SetupAutoLogin.execute(mainController.getSelectedProject(), this);
                mainController.projectManager.saveData();
                if (res) {
                    appendTextToLog("  → Auto-login setup installed successfully");
                } else {
                    appendTextToLog("  → Auto-login setup installation failed");
                }
                return res;
            } catch (Exception e) {
                appendTextToLog("  → ERROR: Exception during auto-login setup");
                appendTextToLog("    • Error: " + e.getMessage());
                appendTextToLog("    • Possible causes:");
                appendTextToLog("      - File system access issues");
                appendTextToLog("      - Invalid JConfig path");
                appendTextToLog("      - Missing required files");
                appendTextToLog("    • Resolution: Check file paths and permissions");
                logger.severe("Exception in setup: " + e.getMessage());
                LoggerUtil.error(e);
                return false;
            }
        }
        return false;
    }


    private boolean setupRestartTools(ProjectEntity updatedProject){
            try {
                appendTextToLog("  → Validating restart tools requirements...");
                Validation.ValidationResult validationResult = Validation.validateForRestartToolsDetailed(updatedProject);
                if (!validationResult.isValid()) {
                    appendTextToLog("  → Restart tools validation failed");
                    appendTextToLog("  → Missing required fields:");
                    for (String error : validationResult.getErrors()) {
                        appendTextToLog("    • " + error);
                    }
                    appendTextToLog("  → Please check the following fields:");
                    appendTextToLog("    • Project Code (JConfig Path)");
                    appendTextToLog("    • Executable Path");
                    appendTextToLog("  → Resolution: Fill in all required fields and try again");
                    
                    // Create detailed error message for dialog
                    StringBuilder errorMessage = new StringBuilder();
                    errorMessage.append("Restart tools setup requires the following fields to be configured:\n\n");
                    for (String error : validationResult.getErrors()) {
                        errorMessage.append("• ").append(error).append("\n");
                    }
                    errorMessage.append("\nPlease configure these fields in the Project Configuration tab and try again.");
                    
                    DialogUtil.showWarning("Restart Tools Setup Requirements", errorMessage.toString());
                    return false;
                }
                appendTextToLog("  → Restart tools validation passed");
                
                appendTextToLog("  → Checking existing restart tools setup...");
                Validation.ValidationResult setupValidation = Validation.validateRestartToolsSetupDetailed(updatedProject);
                if (setupValidation.isValid()) {
                    appendTextToLog("  → Restart tools setup already exists and is valid");
                    return true;
                } else {
                    appendTextToLog("  → Restart tools setup validation failed");
                    appendTextToLog("  → Missing setup components:");
                    for (String error : setupValidation.getErrors()) {
                        appendTextToLog("    • " + error);
                    }
                    appendTextToLog("  → Installing restart tools setup...");
                }
                
                boolean res = SetupRestartTool.execute(mainController.getSelectedProject(), this);
                mainController.projectManager.saveData();
                if (res) {
                    appendTextToLog("  → Restart tools setup installed successfully");
                } else {
                    appendTextToLog("  → Restart tools setup installation failed");
                }
                return res;
            } catch (Exception e) {
                appendTextToLog("  → ERROR: Exception during restart tools setup");
                appendTextToLog("    • Error: " + e.getMessage());
                appendTextToLog("    • Possible causes:");
                appendTextToLog("      - File system access issues");
                appendTextToLog("      - Invalid JConfig path");
                appendTextToLog("      - Missing required files");
                appendTextToLog("    • Resolution: Check file paths and permissions");
                logger.severe("Exception in Restart tools setup: " + e.getMessage());
                LoggerUtil.error(e);
                return false;
            }
    }

    private void reloadLogNames() {
        logger.info("Reloading project codes using improved method");
        clearLog();
        
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            appendTextToLog("ERROR: No project selected");
            appendTextToLog("DETAILS: Please select a project before reloading project codes");
            appendTextToLog("RESOLUTION: Select a project from the main project dropdown");
            return;
        }
        
        appendTextToLog("Attempting to get project code using improved method...");
        
        // Try the new improved method first
        String projectCode = ProjectCodeService.getProjectCode(project.getJconfigPathForBuild(), project.getExePath());
        
        if (projectCode != null) {
            appendTextToLog("SUCCESS: Retrieved project code using improved method: " + projectCode);
            
            // Validate project code format
            String[] parts = projectCode.split("#");
            if (parts.length == 2) {
                // Update the project log text field with the new project code
                projectLogComboBox.setText(projectCode);
                
                // Update the project's log ID and save
                project.setLogId(projectCode);
                mainController.projectManager.saveData();
                
                appendTextToLog("Project code updated successfully: " + projectCode);
            } else {
                appendTextToLog("ERROR: Invalid project code format: " + projectCode);
                appendTextToLog("Expected format: projectName#version");
            }
        } else {
            appendTextToLog("WARNING: Improved method failed, falling back to log-based approach...");
            
            // Fallback to the old log-based method using existing ControlApp
            controlApp.refreshLogNames();
            
            // Show dialog with available project codes from logs
            showProjectCodeSelectionDialog();
        }
    }

    private void reloadLogNamesCB() {
        logger.fine("Reloading log names (legacy method)");
        // This method is kept for compatibility with the old log-based approach
        // The actual population is now handled by the dialog selection
    }

    /**
     * Shows a dialog to select project code from available log-based codes
     */
    private void showProjectCodeSelectionDialog() {
        // Get project codes from the log manager (which was populated by controlApp.refreshLogNames())
        List<String> availableProjectCodes = new ArrayList<>();
        List<String> logIds = mainController.logManager.getLogIds();
        
        if (logIds != null && !logIds.isEmpty()) {
            availableProjectCodes.addAll(logIds);
        }
        
        if (availableProjectCodes.isEmpty()) {
            appendTextToLog("ERROR: No project codes found in logs");
            appendTextToLog("DETAILS: Neither improved method nor log-based method found project codes");
            appendTextToLog("RESOLUTION: Ensure project has been started at least once to generate logs");
            return;
        }
        
        appendTextToLog("Found " + availableProjectCodes.size() + " project codes in logs:");
        for (String code : availableProjectCodes) {
            appendTextToLog("  • " + code);
        }
        
        // Create selection dialog
        ChoiceDialog<String> dialog = new ChoiceDialog<>(availableProjectCodes.get(0), availableProjectCodes);
        dialog.setTitle("Select Project Code");
        dialog.setHeaderText("Improved method failed. Please select a project code from logs:");
        dialog.setContentText("Available project codes:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String selectedCode = result.get();
            
            // Update the UI
            projectLogComboBox.setText(selectedCode);
            
            // Update the project's log ID and save
            ProjectEntity project = mainController.getSelectedProject();
            if (project != null) {
                project.setLogId(selectedCode);
                mainController.projectManager.saveData();
                appendTextToLog("Project code updated successfully: " + selectedCode);
            }
        } else {
            appendTextToLog("No project code selected");
        }
    }


    public void appendTextToLog(String text) {
        if (text != null) {
            Platform.runLater(() -> {
                if (buildLog != null) {
//                    String p = buildLog.getText();
//                    if(buildLog.getText().length() > 2000){
//                        List<String> previousLines = getLastNLines(p, 30);
//                        buildLog.clear();
//                        buildLog.appendText(String.join("\n", previousLines) + "\n");
//                    }
                    buildLog.appendText(text+"\n");
                }
            });
        }
    }
    private List<String> getLastNLines(String text, int n) {
        LinkedList<String> lines = new LinkedList<>();
        String[] allLines = text.split("\n");
        int start = Math.max(0, allLines.length - n);
        for (int i = start; i < allLines.length; i++) {
            lines.add(allLines[i]);
        }
        return lines;
    }

    public void clearLog() {
        Platform.runLater(() -> {
            if (buildLog != null) {
                buildLog.clear();
            }
        });
    }
    
    /**
     * Cleanup method to be called when the application is closing
     * This ensures all running builds are properly cancelled and file handles are closed
     */
    public void cleanup() {
        logger.info("Cleaning up application management resources");
        try {
            // Cancel all running builds
            ControlApp.cancelAllBuilds();
            
            // Clear any remaining thread references
            if (ControlApp.restartThread != null) {
                ControlApp.restartThread.interrupt();
                ControlApp.restartThread = null;
            }
            
            // Force garbage collection to help close any remaining file handles
            System.gc();
            
            appendTextToLog("Application management cleanup completed");
            appendTextToLog("File handles have been properly closed");
        } catch (Exception e) {
            logger.severe("Error during application management cleanup: " + e.getMessage());
            LoggerUtil.error(e);
        }
    }
}
