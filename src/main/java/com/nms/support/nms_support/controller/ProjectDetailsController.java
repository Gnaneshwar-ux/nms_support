package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @FXML private CheckBox useLdapToggle;
    @FXML private VBox basicAuthBox;
    @FXML private VBox ldapAuthBox;
    @FXML private TextField ldapUserField;
    @FXML private TextField targetUserField;
    @FXML private PasswordField ldapPasswordField;
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
    @FXML private Button dbSshImportBtn;
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
        
        
        // Initialize LDAP toggle (default OFF)
        if (useLdapToggle != null) {
            useLdapToggle.setSelected(false);
            applyAuthVisibility(false, false);
            useLdapToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
                applyAuthVisibility(newVal, true);
            });
        }

        // Add listener to product folder field to notify build automation when it changes
        productFolderField.textProperty().addListener((obs, oldValue, newValue) -> {
            // Only notify if the value actually changed
            if (changeTrackingService != null && 
                !java.util.Objects.equals(oldValue, newValue)) {
                notifyProductFolderPathChange();
            }
        });
    }

    /**
     * Applies visibility between basic and LDAP auth groups with optional fade animation.
     * Also clears the opposite authentication fields when switching to prevent stale data.
     */
    private void applyAuthVisibility(boolean showLdap, boolean animate) {
        if (basicAuthBox == null || ldapAuthBox == null) return;

        VBox toShow = showLdap ? ldapAuthBox : basicAuthBox;
        VBox toHide = showLdap ? basicAuthBox : ldapAuthBox;

        if (!animate) {
            toShow.setVisible(true);
            toShow.setManaged(true);
            toHide.setVisible(false);
            toHide.setManaged(false);
            return;
        }

        // Fade out the current box, then show the other with fade in
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), toHide);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            toHide.setVisible(false);
            toHide.setManaged(false);
            toShow.setOpacity(0.0);
            toShow.setVisible(true);
            toShow.setManaged(true);
            javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), toShow);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
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
            controls.add(useLdapToggle);
            controls.add(ldapUserField);
            controls.add(targetUserField);
            controls.add(ldapPasswordField);
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
            
            // Clear all fields first to prevent showing data from previous project
            clearFields();
            
            ProjectEntity project = mainController.getSelectedProject();
            if (project == null) {
                logger.warning("No project selected for loading details.");
                return;
            }
            
            // Linux Server - safely handle null values
            hostAddressField.setText(safe(project.getHost()));

            // LDAP/basicauth defaults and load
            boolean useLdap = project.isUseLdap();
            if (useLdapToggle != null) {
                useLdapToggle.setSelected(useLdap);
            }
            if (useLdap) {
                ldapUserField.setText(safe(project.getLdapUser()));
                targetUserField.setText(safe(project.getTargetUser()));
                ldapPasswordField.setText(safe(project.getLdapPassword()));
                applyAuthVisibility(true, false);
            } else {
                hostUserField.setText(safe(project.getHostUser()));
                hostPasswordField.setText(safe(project.getHostPass()));
                applyAuthVisibility(false, false);
            }
            
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
            
            // Auto-fill environment variable if empty or newly created
            String envVar = project.getNmsEnvVar();
            if (envVar == null || envVar.trim().isEmpty()) {
                envVar = project.getName() + "_HOME_DEVTOOLS";
                project.setNmsEnvVar(envVar);
                logger.info("Auto-filled environment variable for project '" + project.getName() + "': " + envVar);
            }
            envVarField.setText(envVar);
            svnUrlField.setText(safe(project.getSvnRepo()));

        } catch (Exception e) {
            logger.severe("Error loading project details: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load project details: " + e.getMessage());
        } finally {
            // End loading mode to resume change tracking
            changeTrackingService.endLoading();
        }
    }

    /**
     * Clear all fields to prevent showing data from previous project
     */
    private void clearFields() {
        // Linux Server fields
        hostAddressField.clear();
        hostUserField.clear();
        hostPasswordField.clear();
        ldapUserField.clear();
        targetUserField.clear();
        ldapPasswordField.clear();
        portField.clear();
        appUrlField.clear();
        
        // Oracle DB fields
        dbHostField.clear();
        dbPortField.clear();
        dbUserField.clear();
        dbPasswordField.clear();
        dbSidField.clear();
        
        // Project Folders fields
        projectFolderField.clear();
        productFolderField.clear();
        envVarField.clear();
        svnUrlField.clear();
        
        // Reset LDAP toggle to default state
        if (useLdapToggle != null) {
            useLdapToggle.setSelected(false);
            applyAuthVisibility(false, false);
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
            boolean useLdap = useLdapToggle != null && useLdapToggle.isSelected();
            project.setUseLdap(useLdap);
            if (useLdap) {
                project.setLdapUser(ldapUserField != null ? ldapUserField.getText().trim() : "");
                project.setTargetUser(targetUserField != null ? targetUserField.getText().trim() : "");
                project.setLdapPassword(ldapPasswordField != null ? ldapPasswordField.getText() : "");
            } else {
                project.setHostUser(hostUserField.getText().trim());
                project.setHostPass(hostPasswordField.getText());
                // Clear LDAP fields to avoid stale data if desired
                project.setLdapUser(safe(ldapUserField != null ? ldapUserField.getText() : null));
                project.setTargetUser(safe(targetUserField != null ? targetUserField.getText() : null));
                project.setLdapPassword(safe(ldapPasswordField != null ? ldapPasswordField.getText() : null));
            }
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
        // Determine setup mode based on selected option
        SetupService.SetupMode setupMode;
        switch (selectedMode.getId()) {
            case "PATCH_UPGRADE":
                setupMode = SetupService.SetupMode.PATCH_UPGRADE;
                break;
            case "PRODUCT_ONLY":
                setupMode = SetupService.SetupMode.PRODUCT_ONLY;
                break;
            case "FULL_CHECKOUT":
                setupMode = SetupService.SetupMode.FULL_CHECKOUT;
                break;
            case "PROJECT_AND_PRODUCT_FROM_SERVER":
                setupMode = SetupService.SetupMode.PROJECT_AND_PRODUCT_FROM_SERVER;
                break;
            case "PROJECT_ONLY_SVN":
                setupMode = SetupService.SetupMode.PROJECT_ONLY_SVN;
                break;
            case "PROJECT_ONLY_SERVER":
                setupMode = SetupService.SetupMode.PROJECT_ONLY_SERVER;
                break;
            case "HAS_JAVA_MODE":
                setupMode = SetupService.SetupMode.HAS_JAVA_MODE;
                break;
            default:
                LoggerUtil.getLogger().warning("Unknown setup mode: " + selectedMode.getId());
                setupMode = SetupService.SetupMode.PATCH_UPGRADE;
                break;
        }

        // Get the selected project first to get its name
        ProjectEntity project = mainController.getSelectedProject();
        if (project == null) {
            DialogUtil.showAlert(Alert.AlertType.ERROR, "No Project Selected", "Please select a project before starting setup.");
            return;
        }

        // Create process monitor with project name
        ProcessMonitor processMonitor = new ProcessMonitor("Local Setup / Upgrade", project.getName()+" - "+ setupMode);
        
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
                processMonitor.logMessage("setup_init", "Setup mode: " + setupMode.toString());
                
                // Project is already validated and available from earlier in the method
                
                processMonitor.logMessage("setup_init", "Selected project: " + project.getName());
                processMonitor.logMessage("setup_init", "Project folder: " + project.getProjectFolderPath());
                processMonitor.logMessage("setup_init", "JConfig path: " + project.getJconfigPathForBuild());
                processMonitor.logMessage("setup_init", "Product path: " + project.getExePath());

                SetupService setup = new SetupService(project, processMonitor, setupMode);
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
        
        field.textProperty().addListener((obs, oldValue, newValue) -> {
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

        EnhancedEnvironmentVariableDialog envDialog = new EnhancedEnvironmentVariableDialog();
        envDialog.showDialog((Stage) rootScroller.getScene().getWindow(), project, null, false).thenAccept(result -> {
            if (result.isPresent()) {
                EnhancedEnvironmentVariableDialog.EnvVarReplacement replacement = result.get();
                String oldValue = replacement.getOldValue();
                String newValue = replacement.getNewValue();
                
                try {
                    // Use BuildFileParser to perform the replacement
                    List<String> updatedFiles = BuildFileParser.replaceEnvironmentVariable(project, false, oldValue, newValue);
                    
                    if (!updatedFiles.isEmpty()) {
                        project.setNmsEnvVar(newValue);
                        mainController.projectManager.saveData();
                        
                        String message = String.format("Successfully replaced '%s' with '%s' in %d project build files:\n\n%s", 
                            oldValue, newValue, updatedFiles.size(), String.join("\n", updatedFiles));
                        DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Replacement Complete", message);
                    } else {
                        DialogUtil.showAlert(Alert.AlertType.WARNING, "No Changes", 
                            String.format("No files were updated. The environment variable '%s' was not found in any project build files.", oldValue));
                    }
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

        EnhancedEnvironmentVariableDialog envDialog = new EnhancedEnvironmentVariableDialog();
        envDialog.showDialog((Stage) rootScroller.getScene().getWindow(), project, null, true).thenAccept(result -> {
            if (result.isPresent()) {
                EnhancedEnvironmentVariableDialog.EnvVarReplacement replacement = result.get();
                String oldValue = replacement.getOldValue();
                String newValue = replacement.getNewValue();
                
                try {
                    // Use BuildFileParser to perform the replacement
                    List<String> updatedFiles = BuildFileParser.replaceEnvironmentVariable(project, true, oldValue, newValue);
                    
                    if (!updatedFiles.isEmpty()) {
                        project.setNmsEnvVar(newValue);
                        mainController.projectManager.saveData();
                        
                        String message = String.format("Successfully replaced '%s' with '%s' in %d product build files:\n\n%s", 
                            oldValue, newValue, updatedFiles.size(), String.join("\n", updatedFiles));
                        DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Replacement Complete", message);
                    } else {
                        DialogUtil.showAlert(Alert.AlertType.WARNING, "No Changes", 
                            String.format("No files were updated. The environment variable '%s' was not found in any product build files.", oldValue));
                    }
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
    
    @FXML
    private void importOracleDbDataFromSSH() {
        ProjectEntity currentProject = mainController.getSelectedProject();
        if (currentProject == null) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project Selected", "Please select a project first.");
            return;
        }
        
        // Check if SSH connection details are available
        if (currentProject.getHost() == null || currentProject.getHost().trim().isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "SSH Configuration Missing", 
                "Please configure Linux Server connection details first before importing DB data from SSH.");
            return;
        }
        
        // Show confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Import DB Data from SSH");
        confirmDialog.setHeaderText("Import Oracle DB Configuration from SSH Server");
        confirmDialog.setContentText("This will execute SSH commands to retrieve database configuration from the server.\n\n" +
            "Host: " + currentProject.getHost() + "\n" +
            "This will overwrite existing DB configuration. Continue?");
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Disable project switching and show progress dialog
                disableProjectSwitching();
                importDbDataFromSSHAsync(currentProject);
            }
        });
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
        // Copy both basic and LDAP auth values
        hostUserField.setText(safe(sourceProject.getHostUser()));
        hostPasswordField.setText(safe(sourceProject.getHostPass()));
        if (useLdapToggle != null) {
            useLdapToggle.setSelected(sourceProject.isUseLdap());
            if (sourceProject.isUseLdap()) {
                ldapUserField.setText(safe(sourceProject.getLdapUser()));
                targetUserField.setText(safe(sourceProject.getTargetUser()));
                ldapPasswordField.setText(safe(sourceProject.getLdapPassword()));
                applyAuthVisibility(true, false);
            } else {
                applyAuthVisibility(false, false);
            }
        }
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
     * Imports Oracle DB configuration from SSH server asynchronously
     */
    private void importDbDataFromSSHAsync(ProjectEntity project) {
        // Show progress dialog
        ProgressDialog progressDialog = new ProgressDialog();
        progressDialog.setTitle("Importing DB Configuration");
        progressDialog.setHeaderText("Connecting to SSH server and retrieving DB information...");
        progressDialog.setContentText("Please wait...");
        progressDialog.show();
        
        // Create a background task for SSH operations
        javafx.concurrent.Task<Void> sshImportTask = new javafx.concurrent.Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Connecting to SSH server...");
                Thread.sleep(500); // Small delay to show loading
                
                // Execute SSH commands to get DB information
                com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager ssh = UnifiedSSHService.createSSHSession(project);
                ssh.initialize();
                
                updateMessage("Retrieving environment variables...");
                Thread.sleep(300);
                
                // Get RDBMS_HOST from environment variable
                String rdbmsHost = getEnvironmentVariable(ssh, "RDBMS_HOST");
                
                // Get Oracle user from environment variables
                String oracleUser = getEnvironmentVariable(ssh, "ORACLE_READ_ONLY_USER");
                if (oracleUser == null || oracleUser.trim().isEmpty()) {
                    oracleUser = getEnvironmentVariable(ssh, "ORACLE_READ_WRITE_USER");
                }
                
                // Get Oracle SID from environment variable
                String oracleSid = getEnvironmentVariable(ssh, "ORACLE_SERVICE_NAME");
                
                updateMessage("Reading TNS names file...");
                Thread.sleep(300);
                
                // Parse TNS names file to get DB host and port
                String tnsContent = getTnsNamesContent(ssh);
                TnsNamesInfo tnsInfo = parseTnsNames(tnsContent, rdbmsHost);
                
                updateMessage("Processing database configuration...");
                Thread.sleep(300);
                
                // Make variables final for lambda usage
                final String finalOracleUser = oracleUser;
                final String finalOracleSid = oracleSid;
                final String finalRdbmsHost = rdbmsHost;
                final TnsNamesInfo finalTnsInfo = tnsInfo;
                
                // Update UI fields with retrieved information on JavaFX thread
                Platform.runLater(() -> {
                    try {
                        // Set DB Host - prioritize TNS parsed host, fallback to RDBMS_HOST
                        if (finalTnsInfo.host != null && !finalTnsInfo.host.trim().isEmpty()) {
                            dbHostField.setText(finalTnsInfo.host);
                            logger.info("Set DB Host from TNS: " + finalTnsInfo.host);
                        } else if (finalRdbmsHost != null && !finalRdbmsHost.trim().isEmpty()) {
                            dbHostField.setText(finalRdbmsHost);
                            logger.info("Set DB Host from RDBMS_HOST: " + finalRdbmsHost);
                        } else {
                            logger.warning("No DB host found, leaving field empty");
                        }
                        
                        // Set DB Port - from TNS or default
                        if (finalTnsInfo.port > 0) {
                            dbPortField.setText(String.valueOf(finalTnsInfo.port));
                            logger.info("Set DB Port from TNS: " + finalTnsInfo.port);
                        } else {
                            dbPortField.setText("1521"); // Default Oracle port
                            logger.info("Set DB Port to default: 1521");
                        }
                        
                        // Set DB User - from environment variables
                        if (finalOracleUser != null && !finalOracleUser.trim().isEmpty()) {
                            dbUserField.setText(finalOracleUser);
                            logger.info("Set DB User: " + finalOracleUser);
                        } else {
                            logger.warning("No Oracle user found, leaving field empty");
                        }
                        
                        // Set DB SID - from environment variables (NOT from RDBMS_HOST)
                        if (finalOracleSid != null && !finalOracleSid.trim().isEmpty()) {
                            dbSidField.setText(finalOracleSid);
                            logger.info("Set DB SID: " + finalOracleSid);
                        } else {
                            logger.warning("No Oracle SID found, leaving field empty");
                        }
                        
                        // Clear password field for security
                        dbPasswordField.clear();
                        
                        logger.info("Successfully imported DB configuration from SSH server");
                        
                    } catch (Exception e) {
                        logger.severe("Error updating UI with imported DB data: " + e.getMessage());
                    }
                });
                
                ssh.close();
                return null;
            }
        };
        
        // Handle task completion
        sshImportTask.setOnSucceeded(event -> {
            progressDialog.close();
            enableProjectSwitching();
            DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Import Successful", 
                "Database configuration has been imported from SSH server.");
        });
        
        // Handle task failure
        sshImportTask.setOnFailed(event -> {
            progressDialog.close();
            enableProjectSwitching();
            Throwable exception = sshImportTask.getException();
            logger.severe("Error importing DB data from SSH: " + exception.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "SSH Import Error", 
                "Failed to import database configuration from SSH server:\n" + exception.getMessage());
        });
        
        // Bind progress dialog content to task message
        progressDialog.getContentLabel().textProperty().bind(sshImportTask.messageProperty());
        
        // Start the background task
        Thread backgroundThread = new Thread(sshImportTask);
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }
    
    /**
     * Gets an environment variable value from SSH server
     */
    private String getEnvironmentVariable(com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager ssh, String varName) throws Exception {
        try {
            com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult result = ssh.executeCommand("echo $" + varName, 30);
            if (result.isSuccess() && result.getOutput() != null) {
                String value = result.getOutput().trim();
                return value.isEmpty() ? null : value;
            }
        } catch (Exception e) {
            logger.warning("Failed to get environment variable " + varName + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets TNS names file content from SSH server
     */
    private String getTnsNamesContent(com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager ssh) throws Exception {
        try {
            // Try to get NMS_HOME first
            String nmsHome = getEnvironmentVariable(ssh, "NMS_HOME");
            if (nmsHome == null || nmsHome.trim().isEmpty()) {
                logger.warning("NMS_HOME environment variable not found");
                return null;
            }
            
            String tnsPath = nmsHome + "/etc/tnsnames.ora";
            logger.info("Reading TNS names file from: " + tnsPath);
            
            com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult result = ssh.executeCommand("cat " + tnsPath, 30);
            
            if (result.isSuccess() && result.getOutput() != null) {
                String output = result.getOutput();
                logger.info("Raw TNS names file content length: " + output.length());
                logger.info("Raw TNS content preview: " + output.substring(0, Math.min(300, output.length())) + "...");
                return output;
            } else {
                logger.warning("Failed to read TNS names file: " + tnsPath + ", Exit code: " + result.getExitCode());
                return null;
            }
        } catch (Exception e) {
            logger.warning("Error reading TNS names file: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parses TNS names file to extract host and port information
     */
    private TnsNamesInfo parseTnsNames(String tnsContent, String rdbmsHost) {
        TnsNamesInfo info = new TnsNamesInfo();
        
        if (tnsContent == null || tnsContent.trim().isEmpty()) {
            logger.warning("TNS names content is empty");
            return info;
        }
        
        try {
            logger.info("Parsing TNS names content for RDBMS_HOST: " + rdbmsHost);
            logger.info("TNS content preview: " + tnsContent.substring(0, Math.min(200, tnsContent.length())) + "...");
            
            // Clean the content first - remove extra whitespace but preserve structure
            String cleanedContent = cleanTnsContent(tnsContent);
            logger.info("Cleaned TNS content: " + cleanedContent);
            
            // Find the service entry that matches RDBMS_HOST
            String targetService = findMatchingService(cleanedContent, rdbmsHost);
            if (targetService != null) {
                logger.info("Found matching service: " + targetService);
                extractHostAndPort(targetService, info);
            } else {
                logger.warning("No matching service found for RDBMS_HOST: " + rdbmsHost);
                // Fallback: try to find any service with HOST and PORT
                findAnyServiceWithHostPort(cleanedContent, info);
            }
            
            logger.info("Final parsed result - Host: " + info.host + ", Port: " + info.port);
            
        } catch (Exception e) {
            logger.severe("Error parsing TNS names file: " + e.getMessage());
            e.printStackTrace();
        }
        
        return info;
    }
    
    /**
     * Cleans TNS content by normalizing whitespace while preserving structure
     */
    private String cleanTnsContent(String content) {
        if (content == null) return "";
        
        // Split into lines and clean each line
        String[] lines = content.split("\n");
        StringBuilder cleaned = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                cleaned.append(trimmed).append("\n");
            }
        }
        
        return cleaned.toString();
    }

    private String findMatchingService(String content, String rdbmsHost) {
        if (rdbmsHost == null || rdbmsHost.trim().isEmpty()) {
            return null;
        }

        String targetHost = rdbmsHost.trim().toUpperCase();
        logger.info("Looking for service matching RDBMS_HOST: " + targetHost);

        // Remove comments and normalize spacing
        content = content.replaceAll("#.*", ""); // remove comments
        content = content.replaceAll("[\\r\\n\\t]+", " "); // flatten all newlines/tabs
        content = content.replaceAll("\\s{2,}", " "); // collapse multiple spaces

        // This regex roughly finds service names followed by balanced parentheses
        List<String> serviceBlocks = extractBalancedServiceBlocks(content);

        for (String block : serviceBlocks) {
            String upperBlock = block.toUpperCase();
            if (upperBlock.contains(targetHost)) {
                logger.info("Found potential matching block containing host: " + targetHost);
                int eq = block.indexOf('=');
                if (eq != -1) {
                    String desc = block.substring(eq + 1).trim();
                    logger.info("Extracted description: " + desc.substring(0, Math.min(100, desc.length())) + "...");
                    return desc;
                }
            }
        }

        logger.warning("No service found containing host: " + targetHost);
        return null;
    }

    /**
     * Extracts each TNS service definition by scanning for balanced parentheses.
     * This ignores newlines and whitespace formatting.
     */
    private List<String> extractBalancedServiceBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inService = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (!inService) {
                // Look for potential start of service = (
                if (Character.isLetter(ch)) {
                    int eqIndex = content.indexOf('=', i);
                    if (eqIndex != -1) {
                        inService = true;
                    }
                }
            }

            if (inService) {
                current.append(ch);
                if (ch == '(') depth++;
                if (ch == ')') depth--;
                if (depth == 0 && ch == ')') {
                    // One full service block captured
                    blocks.add(current.toString().trim());
                    current.setLength(0);
                    inService = false;
                }
            }
        }
        return blocks;
    }

    private void extractHostAndPort(String serviceBlock, TnsNamesInfo info) {
        if (serviceBlock == null || serviceBlock.trim().isEmpty()) return;

        logger.info("Extracting HOST and PORT from block: " + serviceBlock);

        // Normalize spaces
        String clean = serviceBlock.replaceAll("\\s+", " ");

        // HOST pattern: catches names, IPs, or domains inside or outside parentheses
        Matcher hostMatcher = Pattern.compile("(?i)HOST\\s*=\\s*([A-Za-z0-9_.-]+)").matcher(clean);
        if (hostMatcher.find()) {
            info.host = hostMatcher.group(1);
            logger.info("Found HOST: " + info.host);
        } else {
            logger.warning("HOST not found.");
        }

        // PORT pattern
        Matcher portMatcher = Pattern.compile("(?i)PORT\\s*=\\s*(\\d+)").matcher(clean);
        if (portMatcher.find()) {
            try {
                info.port = Integer.parseInt(portMatcher.group(1));
                logger.info("Found PORT: " + info.port);
            } catch (NumberFormatException e) {
                logger.warning("Invalid PORT format: " + portMatcher.group(1));
            }
        } else {
            logger.warning("PORT not found.");
        }
    }


    /**
     * Fallback method to find any service with HOST and PORT
     */
    private void findAnyServiceWithHostPort(String content, TnsNamesInfo info) {
        logger.info("Fallback: Looking for any service with HOST and PORT");
        
        // Split content into service blocks
        String[] services = content.split("(?=\\w+\\s*=)");
        
        for (String service : services) {
            service = service.trim();
            if (service.isEmpty()) continue;
            
            // Check if this service has both HOST and PORT
            if (service.toUpperCase().contains("HOST") && service.toUpperCase().contains("PORT")) {
                logger.info("Found service with HOST and PORT: " + service.substring(0, Math.min(100, service.length())) + "...");
                extractHostAndPort(service, info);
                if (info.host != null && info.port > 0) {
                    break; // Found what we need
                }
            }
        }
    }
    
    
    /**
     * Data class to hold TNS names information
     */
    private static class TnsNamesInfo {
        String host;
        int port = 0;
    }
    
    /**
     * Shows enhanced loading screen with progress indication
     */
    private void showEnhancedLoadingScreen(String title) {
        Platform.runLater(() -> {
            if (overlayPane != null && loadingIndicator != null) {
                overlayPane.setVisible(true);
                loadingIndicator.setVisible(true);
                loadingIndicator.setProgress(-1); // Indeterminate progress
                logger.info("Showing enhanced loading screen: " + title);
            }
        });
    }
    
    /**
     * Hides enhanced loading screen
     */
    private void hideEnhancedLoadingScreen() {
        Platform.runLater(() -> {
            if (overlayPane != null && loadingIndicator != null) {
                overlayPane.setVisible(false);
                loadingIndicator.setVisible(false);
                logger.info("Hiding enhanced loading screen");
            }
        });
    }
    
    
    /**
     * Disables project switching during import operations
     */
    private void disableProjectSwitching() {
        Platform.runLater(() -> {
            if (mainController != null) {
                // Disable project combo box and related controls
                if (mainController.projectComboBox != null) {
                    mainController.projectComboBox.setDisable(true);
                }
                if (mainController.addButton != null) {
                    mainController.addButton.setDisable(true);
                }
                if (mainController.delButton != null) {
                    mainController.delButton.setDisable(true);
                }
                if (mainController.editProjectButton != null) {
                    mainController.editProjectButton.setDisable(true);
                }
                logger.info("Project switching disabled during SSH import");
            }
        });
    }
    
    /**
     * Enables project switching after import operations
     */
    private void enableProjectSwitching() {
        Platform.runLater(() -> {
            if (mainController != null) {
                // Re-enable project combo box and related controls
                if (mainController.projectComboBox != null) {
                    mainController.projectComboBox.setDisable(false);
                }
                if (mainController.addButton != null) {
                    mainController.addButton.setDisable(false);
                }
                if (mainController.delButton != null) {
                    mainController.delButton.setDisable(false);
                }
                if (mainController.editProjectButton != null) {
                    mainController.editProjectButton.setDisable(false);
                }
                logger.info("Project switching enabled after SSH import");
            }
        });
    }
    
    /**
     * Shows loading indicator with message (legacy method for compatibility)
     */
    private void showLoadingIndicator(String message) {
        showEnhancedLoadingScreen(message);
    }
    
    /**
     * Hides loading indicator (legacy method for compatibility)
     */
    private void hideLoadingIndicator() {
        hideEnhancedLoadingScreen();
    }
    
    
    /**
     * Test method for TNS parsing - can be called for debugging
     */
    public void testTnsParsing() {
        String testTnsContent = "NMSDB,NMSDBadmin,NMSDBdrms = (DESCRIPTION = (ADDRESS = (PROTOCOL = TCP) (HOST = ugbu-phx-674.snphxprshared1.gbucdsint02phx.oraclevcn.com) (PORT = 1521)) (CONNECT_DATA = (SERVER = DEDICATED) (SERVICE_NAME = NMSDB) ) )";
        String rdbmsHost = "NMSDB";
        
        logger.info("Testing TNS parsing with content: " + testTnsContent);
        logger.info("RDBMS_HOST: " + rdbmsHost);
        
        TnsNamesInfo result = parseTnsNames(testTnsContent, rdbmsHost);
        
        logger.info("Parsed result - Host: " + result.host + ", Port: " + result.port);
    }
    
    /**
     * Functional interface for copying data from source project
     */
    @FunctionalInterface
    private interface ImportDataCopier {
        void copyData(ProjectEntity sourceProject);
    }
}


