package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.*;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javafx.scene.control.Control;
import com.nms.support.nms_support.service.globalPack.ChangeTrackingService;


public class ProjectDetailsController {
    private static final Logger logger = Logger.getLogger(ProjectDetailsController.class.getName());
    
    // Public constructor for FXML loading
    public ProjectDetailsController() {
        // Default constructor required for FXML
    }

    public StackPane overlayPane;
    public ProgressIndicator loadingIndicator;
    @FXML public ScrollPane rootScroller;
    @FXML private FlowPane cardPane;

    // Linux Server fields
    @FXML private TextField hostAddressField;
    @FXML private TextField hostUserField;
    @FXML private PasswordField hostPasswordField;
    @FXML private TextField portField; // TODO: Add to ProjectEntity if needed
    @FXML private TextField appUrlField;

    // Oracle DB fields
    @FXML private TextField dbHostField; // TODO: Add to ProjectEntity if needed
    @FXML private TextField dbPortField; // TODO: Add to ProjectEntity if needed
    @FXML private TextField dbUserField; // TODO: Add to ProjectEntity if needed
    @FXML private PasswordField dbPasswordField; // TODO: Add to ProjectEntity if needed
    @FXML private TextField dbSidField; // TODO: Add to ProjectEntity if needed

    // Project Folders fields
    @FXML private TextField projectFolderField; // TODO: Add to ProjectEntity if needed
    @FXML private TextField productFolderField; // TODO: Add to ProjectEntity if needed
    @FXML private TextField envVarField;
    @FXML private TextField svnUrlField;
    @FXML private Button svnBrowseBtn;
    @FXML private Button replaceProjectBuildBtn;
    @FXML private Button replaceProductBuildBtn;
    
    // Import buttons
    @FXML private Button linuxImportBtn;
    @FXML private Button dbImportBtn;
    @FXML private Button foldersImportBtn;

    private MainController mainController;
    private ChangeTrackingService changeTrackingService;
    
    public void setMainController(MainController mc){ 
        this.mainController = mc; 
        this.changeTrackingService = ChangeTrackingService.getInstance();
        registerControlsForChangeTracking();
    }

    @FXML
    private void initialize() {
        // Restrict port text fields to numeric characters only (strip non-digits as user types)
        enforceNumericOnly(portField);
        enforceNumericOnly(dbPortField);
        
        // Add listener to product folder field to notify build automation when it changes
        productFolderField.textProperty().addListener((_, oldValue, newValue) -> {
            // Only notify if the value actually changed
            if (changeTrackingService != null && 
                !java.util.Objects.equals(oldValue, newValue)) {
                notifyProductFolderPathChange();
            }
        });
    }

    /**
     * Register all controls for change tracking
     */
    private void registerControlsForChangeTracking() {
        if (changeTrackingService != null) {
            Set<Control> controls = new HashSet<>();
            controls.add(hostAddressField);
            controls.add(hostUserField);
            controls.add(hostPasswordField);
            controls.add(portField);
            controls.add(appUrlField);
            controls.add(dbHostField);
            controls.add(dbPortField);
            controls.add(dbUserField);
            controls.add(dbPasswordField);
            controls.add(dbSidField);
            controls.add(projectFolderField);
            controls.add(productFolderField);
            controls.add(envVarField);
            controls.add(svnUrlField);
            
            changeTrackingService.registerTab("Project Configuration", controls);
        }
    }
    
    public void loadProjectDetails() {
        try {
            // Start loading mode to prevent change tracking during data loading
            changeTrackingService.startLoading();
            
            ProjectEntity project = mainController.getSelectedProject();
            if (project == null) {
                logger.warning("No project selected for loading details.");
                return;
            }
            
            // Linux Server - safely handle null values
            hostAddressField.setText(safe(project.getHost()));
            hostUserField.setText(safe(project.getHostUser()));
            hostPasswordField.setText(safe(project.getHostPass()));
            
            // Safely handle port values
            try {
                if (project.getHostPort() > 0) {
                    portField.setText(String.valueOf(project.getHostPort()));
                } else {
                    portField.setText("22"); // Default SSH port
                }
            } catch (Exception e) {
                logger.warning("Error setting host port: " + e.getMessage() + ", using default 22");
                portField.setText("22");
            }
            
            appUrlField.setText(safe(project.getNmsAppURL()));

            // Oracle DB - safely handle null values
            dbHostField.setText(safe(project.getDbHost()));
            
            // Safely handle database port
            try {
                dbPortField.setText(project.getDbPort() > 0 ? String.valueOf(project.getDbPort()) : "1521");
            } catch (Exception e) {
                logger.warning("Error setting database port: " + e.getMessage() + ", using default 1521");
                dbPortField.setText("1521");
            }
            
            dbUserField.setText(safe(project.getDbUser()));
            dbPasswordField.setText(safe(project.getDbPassword()));
            dbSidField.setText(safe(project.getDbSid()));

            // Project Folders - safely handle null values
            // Display project folder path (not jconfig path) to user
            projectFolderField.setText(safe(project.getProjectFolderPath()));
            productFolderField.setText(safe(project.getExePath()));
            envVarField.setText(safe(project.getNmsEnvVar()));
            svnUrlField.setText(safe(project.getSvnRepo()));

        } catch (Exception e) {
            logger.severe("Error loading project details: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load project details: " + e.getMessage());
        } finally {
            // End loading mode to resume change tracking
            changeTrackingService.endLoading();
        }
    }

    public void saveProjectDetails() {
        try {
            ProjectEntity project = mainController.getSelectedProject();
            if (project == null) {
                logger.warning("No project selected for saving details.");
                return;
            }

            // Linux Server
            project.setHost(hostAddressField.getText().trim());
            project.setHostUser(hostUserField.getText().trim());
            project.setHostPass(hostPasswordField.getText());
            try {
                project.setHostPort(Integer.parseInt(portField.getText().trim()));
            } catch (NumberFormatException e) {
                project.setHostPort(22); // default
            }
            project.setNmsAppURL(appUrlField.getText().trim());

            // Oracle DB
            project.setDbHost(dbHostField.getText().trim());
            try {
                project.setDbPort(Integer.parseInt(dbPortField.getText().trim()));
            } catch (NumberFormatException e) {
                project.setDbPort(1521); // default
            }
            project.setDbUser(dbUserField.getText().trim());
            project.setDbPassword(dbPasswordField.getText());
            project.setDbSid(dbSidField.getText().trim());

            // Project Folders
            // Store project folder path internally as jconfig path for backward compatibility
            project.setProjectFolderPath(projectFolderField.getText().trim());
            project.setExePath(productFolderField.getText().trim());
            project.setNmsEnvVar(envVarField.getText().trim());
            project.setSvnRepo(svnUrlField.getText().trim());

            // Save to database
            mainController.projectManager.saveData();
            
            // Notify build automation controller to refresh application dropdown
            notifyBuildAutomationRefresh();
            
            // Removed success popup as requested

        } catch (Exception e) {
            logger.severe("Error saving project details: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to save project details: " + e.getMessage());
        }
    }

    /**
     * Notifies the build automation controller to refresh its application dropdown
     * when project details are saved
     */
    private void notifyBuildAutomationRefresh() {
        if (mainController != null && mainController.getBuildAutomation() != null) {
            mainController.getBuildAutomation().reloadApplicationDropdown();
            logger.info("Notified build automation controller to refresh application dropdown");
        }
    }
    
    /**
     * Notifies the build automation controller immediately when product folder path changes
     */
    private void notifyProductFolderPathChange() {
        if (mainController != null && mainController.getBuildAutomation() != null) {
            // Use Platform.runLater to ensure UI updates happen on the JavaFX thread
            Platform.runLater(() -> {
                mainController.getBuildAutomation().reloadApplicationDropdown();
                logger.info("Notified build automation controller of product folder path change");
            });
        }
    }

    @FXML
    private void browseProjectFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Folder");
        
        // Try to use existing path as initial directory
        String existingPath = projectFolderField.getText().trim();
        java.io.File initialDir = null;
        
        if (!existingPath.isEmpty()) {
            java.io.File pathFile = new java.io.File(existingPath);
            if (pathFile.exists() && pathFile.isDirectory()) {
                initialDir = pathFile;
            } else if (pathFile.getParentFile() != null && pathFile.getParentFile().exists()) {
                initialDir = pathFile.getParentFile();
            }
        }
        
        // If no valid path found, use This PC (root directory)
        if (initialDir == null) {
            initialDir = new java.io.File(System.getProperty("user.home"));
        }
        
        chooser.setInitialDirectory(initialDir);
        
        javafx.stage.Window window = rootScroller.getScene().getWindow();
        java.io.File selectedDir = chooser.showDialog(window);
        
        if (selectedDir != null) {
            projectFolderField.setText(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    private void browseProductFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Product Folder");
        
        // Try to use existing path as initial directory
        String existingPath = productFolderField.getText().trim();
        java.io.File initialDir = null;
        
        if (!existingPath.isEmpty()) {
            java.io.File pathFile = new java.io.File(existingPath);
            if (pathFile.exists() && pathFile.isDirectory()) {
                initialDir = pathFile;
            } else if (pathFile.getParentFile() != null && pathFile.getParentFile().exists()) {
                initialDir = pathFile.getParentFile();
            }
        }
        
        // If no valid path found, use This PC (root directory)
        if (initialDir == null) {
            initialDir = new java.io.File(System.getProperty("user.home"));
        }
        
        chooser.setInitialDirectory(initialDir);
        
        javafx.stage.Window window = rootScroller.getScene().getWindow();
        java.io.File selectedDir = chooser.showDialog(window);
        
        if (selectedDir != null) {
            productFolderField.setText(selectedDir.getAbsolutePath());
            // Notify build automation immediately when a new folder is selected
            notifyProductFolderPathChange();
        }
    }

    @FXML
    private void showEnvVarDialog() {
        try {
            // Create and show the improved environment variable dialog
            EnvironmentVariableDialog dialog = new EnvironmentVariableDialog();
            Stage parentStage = (Stage) rootScroller.getScene().getWindow();
            
            dialog.showDialog(parentStage).thenAccept(result -> {
                if (result.isPresent()) {
                    // Update the environment variable field with the selected value
                    Platform.runLater(() -> {
                        envVarField.setText(result.get());
                        logger.info("Selected environment variable: " + result.get());
                    });
                }
            }).exceptionally(throwable -> {
                logger.severe("Error in environment variable dialog: " + throwable.getMessage());
                Platform.runLater(() -> {
                    DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", 
                        "Failed to show environment variable dialog: " + throwable.getMessage());
                });
                return null;
            });
            
        } catch (Exception e) {
            logger.severe("Error creating environment variable dialog: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", 
                "Failed to create environment variable dialog: " + e.getMessage());
        }
    }

    @FXML
    private void browseSvnUrl() {
        svnBrowseBtn.setDisable(true); // disable immediately in FX thread
        Window fxWindow = svnBrowseBtn.getScene().getWindow();
        
        try {
            // Get the current URL from text field
            String currentUrl = svnUrlField.getText().trim();
            
            // Validate and provide default URL if needed
            String baseUrl = validateAndGetBaseUrl(currentUrl);
            
            // Validate URL format before attempting connection
            if (!isValidSvnUrl(baseUrl)) {
                DialogUtil.showAlert(Alert.AlertType.ERROR, "Invalid SVN URL", 
                    "Please enter a valid SVN URL (e.g., https://server.com/svn/repo)");
                svnBrowseBtn.setDisable(false);
                return;
            }
            
            // Use the advanced JavaFX-based SVN browser dialog
            SVNAutomationTool svnTool = new SVNAutomationTool();
            SVNBrowserDialog browserDialog = new SVNBrowserDialog(fxWindow, baseUrl, svnTool.getAuthManager());
            
            String picked = browserDialog.showAndWait();
            
            if (picked != null) {
                svnUrlField.setText(picked);
            }
            
        } catch (Exception ex) {
            LoggerUtil.error(ex);
            DialogUtil.showAlert(Alert.AlertType.ERROR, "SVN Browse Failed", 
                "An unexpected error occurred: " + ex.getMessage());
        } finally {
            svnBrowseBtn.setDisable(false);
        }
    }

    /**
     * Validates and returns a valid base URL for SVN browsing
     * If the provided URL is valid, returns it; otherwise returns a default URL
     * 
     * @param currentUrl The URL from the text field
     * @return A valid base URL for SVN browsing
     */
    private String validateAndGetBaseUrl(String currentUrl) {
        // Default Oracle SVN URL if no valid URL is provided
        String defaultUrl = "https://adc4110315.us.oracle.com/svn/nms-projects/trunk/projects";
        
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            LoggerUtil.getLogger().info("No URL provided, using default: " + defaultUrl);
            return defaultUrl;
        }
        
        String trimmedUrl = currentUrl.trim();
        
        // Basic SVN URL validation
        if (isValidSvnUrl(trimmedUrl)) {
            LoggerUtil.getLogger().info("Using provided URL: " + trimmedUrl);
            return trimmedUrl;
        } else {
            LoggerUtil.getLogger().warning("Invalid SVN URL provided: " + trimmedUrl + ", using default: " + defaultUrl);
            return defaultUrl;
        }
    }

    /**
     * Validates if the provided string is a valid SVN URL
     * 
     * @param url The URL to validate
     * @return true if the URL is valid, false otherwise
     */
    private boolean isValidSvnUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        String trimmedUrl = url.trim();
        
        // Check for common SVN URL patterns
        return trimmedUrl.startsWith("http://") || 
               trimmedUrl.startsWith("https://") || 
               trimmedUrl.startsWith("svn://") || 
               trimmedUrl.startsWith("file://") ||
               trimmedUrl.startsWith("ssh://");
    }


    @FXML
    private void startLocalSetup() {
        // Check for unsaved changes before starting setup
        if (changeTrackingService != null && changeTrackingService.hasUnsavedChanges()) {
            try {
                SaveConfirmationDialog dialog = new SaveConfirmationDialog((Stage) rootScroller.getScene().getWindow(), changeTrackingService);
                Boolean result = dialog.showAndWait();
                
                if (result == null) {
                    // User cancelled
                    logger.info("Setup cancelled due to unsaved changes");
                    return;
                } else if (result) {
                    // User chose to save, perform save
                    logger.info("User chose to save before setup, performing save operation");
                    if (mainController != null) {
                        mainController.performGlobalSave();
                    }
                } else {
                    // User chose to discard changes
                    logger.info("User chose to discard changes before setup");
                    changeTrackingService.markAsSaved();
                }
            } catch (Exception e) {
                logger.warning("Error showing save confirmation dialog: " + e.getMessage());
                return;
            }
        }
        
        // Use the new professional setup mode dialog
        SetupModeDialog setupDialog = new SetupModeDialog();
        SetupModeDialog.SetupMode selectedMode = setupDialog.showDialog((Stage) (rootScroller.getScene().getWindow()));
        
        if (selectedMode == SetupModeDialog.CANCELLED) {
            LoggerUtil.getLogger().info("Setup cancelled by user");
            return;
        }
        boolean isFull = (selectedMode == SetupModeDialog.FULL_CHECKOUT);

        // Get the selected project first to get its name
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            DialogUtil.showAlert(Alert.AlertType.ERROR, "No Project Selected", "Please select a project before starting setup.");
            return;
        }

        // Create process monitor with project name
        ProcessMonitor processMonitor = new ProcessMonitor("Local Setup / Upgrade", project.getName());
        
        // Add initial steps based on setup mode
//        processMonitor.addStep("validate", "Validating inputs");
        
//        if (isFull) {
//            processMonitor.addStep("svn", "SVN checkout");
//            processMonitor.addStep("configure", "Configure project files");
//        }
//
//        processMonitor.addStep("download", "Download/Install product");
//        processMonitor.addStep("setup", "Configure product files");
//        processMonitor.addStep("finalize", "Finalize setup");
        
        // Show the dialog
        processMonitor.show((Stage) rootScroller.getScene().getWindow());
        
        // Execute setup in background thread with dummy processes
        Thread setupThread = new Thread(() -> {
            try {
                processMonitor.logMessage("setup_init", "Starting setup process...");
                processMonitor.logMessage("setup_init", "Setup mode: " + (isFull ? "FULL_CHECKOUT" : "PRODUCT_ONLY"));
                
                // Project is already validated and available from earlier in the method
                
                processMonitor.logMessage("setup_init", "Selected project: " + project.getName());
                processMonitor.logMessage("setup_init", "Project folder: " + project.getProjectFolderPath());
                processMonitor.logMessage("setup_init", "JConfig path: " + project.getJconfigPathForBuild());
                processMonitor.logMessage("setup_init", "Product path: " + project.getExePath());

                SetupService setup = new SetupService(project, processMonitor, isFull);
                processMonitor.logMessage("setup_init", "SetupService created successfully");

                setup.executeSetup(mainController);
                processMonitor.logMessage("setup_init", "Setup execution completed");

            } catch (Exception e) {
                processMonitor.logMessage("setup_error", "Setup failed with exception: " + e.getMessage());
                processMonitor.logMessage("setup_error", "Exception type: " + e.getClass().getSimpleName());
                processMonitor.logMessage("setup_error", "Stack trace: " + getStackTrace(e));
                LoggerUtil.error(e);
                processMonitor.markProcessFailed("Setup failed: " + e.getMessage());
            }
        }, "setup-service");

        setupThread.start();

        // Handle completion in a separate thread to avoid blocking UI
        Thread completionThread = new Thread(() -> {
            boolean success = processMonitor.waitForCompletion();
            Platform.runLater(() -> {
                if (success) {
                    processMonitor.logMessage("setup_complete", "Setup completed successfully");
                    DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Setup Completed", "Perform 'ant clean config' in Application Management tab to validate setup.");
                } else {
                    processMonitor.logMessage("setup_complete", "Setup failed or was cancelled");
                    processMonitor.markProcessFailed("Setup Failed");
                }
            });
        }, "completion-handler");

        completionThread.start();

        new Thread(() -> {
            while (processMonitor.isRunning()){
                completionThread.interrupt();
                setupThread.interrupt();
                while (setupThread.isInterrupted());
                processMonitor.logMessage("setup_interrupt", "Setup process interrupted by user");
                processMonitor.markProcessFailed("Interrupted by the user");
            }
        });
    }
    
    /**
     * Helper method to get stack trace as string
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }


    // ===== Utility Methods =====
    private String safe(String value) {
        return value != null ? value : "";
    }

    private void enforceNumericOnly(TextField field) {
        if (field == null) return;
        
        field.textProperty().addListener((_, _, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                field.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    @FXML
    private void replaceProjectBuild() {
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project", "Please select a project first.");
            return;
        }
        
        String projectPath = projectFolderField.getText().trim();
        if (projectPath.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "Missing Path", "Please enter a project folder path.");
            return;
        }

        DialogUtil.showTextInputDialog("ENV INPUT", "Enter env var name created for this project:", "Ex: OPAL_HOME", project.getNmsEnvVar()).thenAccept(result -> {
            if (result.isPresent()) {
                String env_name = result.get().trim();
                try {
                    ManageFile.replaceTextInFiles(List.of(projectPath + "/build.properties"), "NMS_HOME", env_name);
                    ManageFile.replaceTextInFiles(List.of(projectPath + "/build.xml"), "NMS_HOME", env_name);
                    project.setNmsEnvVar(env_name);
                    mainController.projectManager.saveData();
                    DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Success", "Replaced NMS_HOME with " + env_name + " in project build files.");
                } catch (Exception e) {
                    logger.severe("Error replacing text in project build files: " + e.getMessage());
                    DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to replace text in project build files: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void replaceProductBuild() {
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project", "Please select a project first.");
            return;
        }
        
        String productPath = productFolderField.getText().trim();
        if (productPath.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "Missing Path", "Please enter a product folder path.");
            return;
        }

        DialogUtil.showTextInputDialog("ENV INPUT", "Enter env var name created for this product:", "Ex: OPAL_HOME", project.getNmsEnvVar()).thenAccept(result -> {
            if (result.isPresent()) {
                String env_name = result.get().trim();
                try {
                    ManageFile.replaceTextInFiles(List.of(productPath + "/java/ant/build.properties"), "NMS_HOME", env_name);
                    ManageFile.replaceTextInFiles(List.of(productPath + "/java/ant/build.xml"), "NMS_HOME", env_name);
                    project.setNmsEnvVar(env_name);
                    mainController.projectManager.saveData();
                    DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Success", "Replaced NMS_HOME with " + env_name + " in product build files.");
                } catch (Exception e) {
                    logger.severe("Error replacing text in product build files: " + e.getMessage());
                    DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to replace text in product build files: " + e.getMessage());
                }
            }
        });
    }
    
    // ===== Import Methods =====
    
    @FXML
    private void importLinuxServerData() {
        importCardData("Linux Server", this::copyLinuxServerData);
    }
    
    @FXML
    private void importOracleDbData() {
        importCardData("Oracle DB", this::copyOracleDbData);
    }
    
    @FXML
    private void importProjectFoldersData() {
        importCardData("Project Folders", this::copyProjectFoldersData);
    }
    
    /**
     * Generic method to show import dialog and copy data from selected project
     */
    private void importCardData(String cardType, ImportDataCopier copier) {
        ProjectEntity currentProject = mainController.getSelectedProject();
        if (currentProject == null) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project Selected", "Please select a project first.");
            return;
        }
        
        try {
            // Check if there are any other projects available
            List<ProjectEntity> allProjects = mainController.projectManager.getProjects();
            if (allProjects == null || allProjects.size() <= 1) {
                DialogUtil.showAlert(Alert.AlertType.WARNING, "No Projects Available", 
                    "No other projects available to import " + cardType.toLowerCase() + " configuration from.");
                return;
            }
            
            // Show the import dialog
            ProjectImportDialog dialog = new ProjectImportDialog();
            Window parentWindow = rootScroller.getScene().getWindow();
            
            dialog.showDialog(parentWindow, mainController.projectManager, currentProject.getName(), cardType)
                    .thenAccept(selectedProject -> {
                        if (selectedProject.isPresent()) {
                            Platform.runLater(() -> {
                                                            try {
                                // Copy the data from selected project
                                copier.copyData(selectedProject.get());
                                
                                logger.info("Successfully imported " + cardType + " data from project: " + selectedProject.get().getName());
                                        
                            } catch (Exception e) {
                                logger.severe("Error importing " + cardType + " data: " + e.getMessage());
                                DialogUtil.showAlert(Alert.AlertType.ERROR, "Import Error", 
                                    "Failed to import " + cardType + " configuration: " + e.getMessage());
                            }
                            });
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.severe("Error in import dialog: " + throwable.getMessage());
                        Platform.runLater(() -> {
                            DialogUtil.showAlert(Alert.AlertType.ERROR, "Import Error", 
                                "Failed to show import dialog: " + throwable.getMessage());
                        });
                        return null;
                    });
                    
        } catch (Exception e) {
            logger.severe("Error setting up import dialog: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Import Error", 
                "Failed to initialize import dialog: " + e.getMessage());
        }
    }
    
    /**
     * Copies Linux Server data from source project to current project
     */
    private void copyLinuxServerData(ProjectEntity sourceProject) {
        // Copy Linux Server fields
        hostAddressField.setText(safe(sourceProject.getHost()));
        hostUserField.setText(safe(sourceProject.getHostUser()));
        hostPasswordField.setText(safe(sourceProject.getHostPass()));
        if (sourceProject.getHostPort() > 0) {
            portField.setText(String.valueOf(sourceProject.getHostPort()));
        } else {
            portField.setText("22");
        }
        appUrlField.setText(safe(sourceProject.getNmsAppURL()));
    }
    
    /**
     * Copies Oracle DB data from source project to current project
     */
    private void copyOracleDbData(ProjectEntity sourceProject) {
        // Copy Oracle DB fields
        dbHostField.setText(safe(sourceProject.getDbHost()));
        dbPortField.setText(sourceProject.getDbPort() > 0 ? String.valueOf(sourceProject.getDbPort()) : "1521");
        dbUserField.setText(safe(sourceProject.getDbUser()));
        dbPasswordField.setText(safe(sourceProject.getDbPassword()));
        dbSidField.setText(safe(sourceProject.getDbSid()));
    }
    
    /**
     * Copies Project Folders data from source project to current project
     */
    private void copyProjectFoldersData(ProjectEntity sourceProject) {
        // Copy Project Folders fields
        // Display project folder path (not jconfig path) to user
        projectFolderField.setText(safe(sourceProject.getProjectFolderPath()));
        productFolderField.setText(safe(sourceProject.getExePath()));
        envVarField.setText(safe(sourceProject.getNmsEnvVar()));
        svnUrlField.setText(safe(sourceProject.getSvnRepo()));
        
        // Notify build automation of product folder change
        notifyProductFolderPathChange();
    }
    
    /**
     * Functional interface for copying data from source project
     */
    @FunctionalInterface
    private interface ImportDataCopier {
        void copyData(ProjectEntity sourceProject);
    }
}


