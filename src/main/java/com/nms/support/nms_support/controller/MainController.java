package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.ControlApp;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.CreateInstallerCommand;
import com.nms.support.nms_support.service.globalPack.*;
import com.nms.support.nms_support.service.userdata.LogManager;
import com.nms.support.nms_support.service.userdata.ProjectManager;
import com.nms.support.nms_support.service.userdata.VpnManager;
import javafx.stage.WindowEvent;
import com.nms.support.nms_support.service.globalPack.ChangeTrackingService;
import com.nms.support.nms_support.service.globalPack.SaveConfirmationDialog;
import com.nms.support.nms_support.service.globalPack.AddProjectDialog;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;

import static com.nms.support.nms_support.service.globalPack.DialogUtil.showProjectSetupDialog;
import static com.nms.support.nms_support.service.globalPack.SVNAutomationTool.deleteFolderContents;

public class MainController implements Initializable {

    private static final Logger logger = LoggerUtil.getLogger();

    // Public constructor for FXML loading
    public MainController() {
        // Default constructor required for FXML
    }

    @FXML
    public ComboBox<String> themeComboBox;
    @FXML
    public Tab buildTab;
    public ComboBox<String> projectComboBox;
    public ProjectManager projectManager;
    public LogManager logManager;
    public VpnManager vpnManager;
    public Button addButton;
    public Button delButton;
    public Button editProjectButton;
    public Button clineButton;
    @FXML
    public Tab dataStoreTab;
    public Button openVpnButton;
    @FXML
    public Tab projectTab;
    @FXML
    public Tab jarDecompilerTab;
    @FXML
    private Parent root;
    @FXML
    private ImageView themeIcon;
    @FXML
    public Button mainSaveButton;
    @FXML
    public Button infoButton;

    BuildAutomation buildAutomation;
    private ProjectDetailsController projectDetailsController;
    private DatastoreDumpController datastoreDumpController;
    private EnhancedJarDecompilerController jarDecompilerController;
    private ChangeTrackingService changeTrackingService;
    
    // Splash overlay components
    private Parent splashOverlay;
    private SplashScreenController splashController;
    private final java.util.concurrent.atomic.AtomicInteger preloadRemaining = new java.util.concurrent.atomic.AtomicInteger(3);
    
    // Flag to prevent listener from triggering during programmatic changes
    private volatile boolean isUpdatingComboBoxProgrammatically = false;

    /**
     * Gets the build automation controller instance
     * @return the build automation controller
     */
    public BuildAutomation getBuildAutomation() {
        return buildAutomation;
    }

    /**
     * Gets the datastore dump controller instance
     * @return the datastore dump controller
     */
    public DatastoreDumpController getDatastoreDumpController() {
        return datastoreDumpController;
    }

    /**
     * Gets the jar decompiler controller instance
     * @return the jar decompiler controller
     */
    public EnhancedJarDecompilerController getJarDecompilerController() {
        return jarDecompilerController;
    }


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Only log initialization issues or important configuration details
        if (root != null) {
            root.getStyleClass().add("root");
        } else {
            logger.warning("Root is null");
        }

        // Show splash overlay first
        showSplashOverlay();
        
        // Initialize managers
        String user = System.getProperty("user.name");
        projectManager = new ProjectManager("C:\\Users\\" + user + "\\Documents\\nms_support_data\\projects.json");
        logManager = new LogManager("C:\\Users\\" + user + "\\Documents\\nms_support_data\\logs.json");
        vpnManager = new VpnManager("C:\\Users\\" + user + "\\Documents\\nms_support_data\\vpn.json");

        addButton.setOnAction(event -> addProject());
        delButton.setOnAction(event -> removeProject());
        editProjectButton.setOnAction(event -> editProject());
        if (infoButton != null) {
            infoButton.setOnAction(event -> showInfoDialog());
        }
        if (clineButton != null) {
            clineButton.setOnAction(event -> openClineWorkspace());
        }
        reloadProjectNamesCB();
        
        // Initialize change tracking service
        changeTrackingService = ChangeTrackingService.getInstance();
        
        // Single centralized project selection listener
        projectComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            handleProjectSelectionChange(oldValue, newValue);
        });
        setTabState("None");
        
        // Global save button action - handles saving for all tabs
        if (mainSaveButton != null) {
            mainSaveButton.setOnAction(event -> performGlobalSave());
        }
        
        // Setup save button visual feedback
        setupSaveButtonVisualFeedback();
        
        // Load content and prepare; overlay will close when all tabs are preloaded
        Platform.runLater(() -> {
            loadTabContent();
            setupWindowCloseHandler();
            setupAccelerators();
        });
    }

    /**
     * Shows the splash overlay on top of the main interface
     */
    private void showSplashOverlay() {
        try {
            // Load splash overlay FXML
            FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/splash-overlay.fxml"));
            if (splashLoader.getLocation() == null) {
                logger.warning("Splash overlay FXML resource not found");
                return;
            }
            
            splashOverlay = splashLoader.load();
            splashController = splashLoader.getController();
            
            // Add splash overlay stylesheet
            try {
                splashOverlay.getStylesheets().add(getClass().getResource("/com/nms/support/nms_support/view/splash-overlay.css").toExternalForm());
            } catch (Exception e) {
                logger.warning("Could not load splash overlay stylesheet: " + e.getMessage());
            }
            
            // Add splash overlay to the root container safely (supports StackPane or AnchorPane)
            if (root != null) {
                if (root instanceof AnchorPane) {
                    AnchorPane anchorRoot = (AnchorPane) root;
                    anchorRoot.getChildren().add(splashOverlay);
                    AnchorPane.setTopAnchor(splashOverlay, 0.0);
                    AnchorPane.setBottomAnchor(splashOverlay, 0.0);
                    AnchorPane.setLeftAnchor(splashOverlay, 0.0);
                    AnchorPane.setRightAnchor(splashOverlay, 0.0);
                } else if (root instanceof StackPane) {
                    StackPane stackRoot = (StackPane) root;
                    stackRoot.getChildren().add(splashOverlay);
                    StackPane.setAlignment(splashOverlay, Pos.CENTER);
                    // Bind to fill the parent
                    ((Region) splashOverlay).prefWidthProperty().bind(stackRoot.widthProperty());
                    ((Region) splashOverlay).prefHeightProperty().bind(stackRoot.heightProperty());
                } else if (root instanceof Pane) {
                    Pane paneRoot = (Pane) root;
                    paneRoot.getChildren().add(splashOverlay);
                    // Bind to fill the parent for generic Pane
                    ((Region) splashOverlay).prefWidthProperty().bind(paneRoot.widthProperty());
                    ((Region) splashOverlay).prefHeightProperty().bind(paneRoot.heightProperty());
                } else {
                    logger.warning("Unsupported root container type for splash overlay: " + root.getClass().getSimpleName());
                }
            }
            
            logger.info("Splash overlay displayed");
            
        } catch (IOException e) {
            logger.severe("Error loading splash overlay: " + e.getMessage());
        }
    }
    
    // Sleep-based splash removed; overlay hides when preloads finish
    
    /**
     * Hides the splash overlay with fade-out animation
     */
    private void hideSplashOverlay() {
        if (splashController != null && splashOverlay != null) {
            splashController.fadeOut(() -> {
                Platform.runLater(() -> {
                    if (root != null && splashOverlay != null) {
                        if (root instanceof Pane) {
                            ((Pane) root).getChildren().remove(splashOverlay);
                            logger.info("Splash overlay removed");
                        }
                    }
                });
            });
        }
    }

    /**
     * Called each time a tab finishes preloading. When all are ready, auto-select project and hide overlay.
     */
    private void onTabPreloaded() {
        int remaining = preloadRemaining.decrementAndGet();
        logger.info("Preload remaining: " + remaining);
        if (remaining <= 0) {
            Platform.runLater(() -> {
                try {
                    List<ProjectEntity> projects = projectManager.getProjects();
                    if (projects != null && !projects.isEmpty()) {
                        String mostRecentProjectName = projects.get(0).getName();
                        if (mostRecentProjectName != null && !mostRecentProjectName.trim().isEmpty()) {
                            projectComboBox.setValue(mostRecentProjectName);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Auto-select on preload completion failed: " + e.getMessage());
                } finally {
                    hideSplashOverlay();
                }
            });
        }
    }

    /**
     * Centralized handler for project selection changes
     * This method coordinates all project selection logic and delegates to other controllers
     * @param oldValue The previous project selection
     * @param newValue The new project selection
     */
    private void handleProjectSelectionChange(String oldValue, String newValue) {
        try {
            // Skip if no actual change
            if (oldValue != null && oldValue.equals(newValue)) {
                return;
            }
            
            // Skip if we're updating the combobox programmatically
            if (isUpdatingComboBoxProgrammatically) {
                logger.info("Skipping listener - programmatic update in progress");
                return;
            }
            
            logger.info("Project selection changed from '" + oldValue + "' to '" + newValue + "'");
            
            // Start global loading mode to prevent change tracking during project switch
            changeTrackingService.startLoading();
            
            try {
                // Phase 1: Immediate UI updates (synchronous)
                setTabState(newValue);
                
                // Phase 2: Load project details immediately for responsive UI
                if (projectDetailsController != null) {
                    projectDetailsController.loadProjectDetails();
                }
                
                // Phase 3: Handle project ordering and combobox refresh (asynchronous)
                if (newValue != null && !"None".equals(newValue)) {
                    // Use background thread for heavy operations
                    Thread.ofVirtual().start(() -> {
                        try {
                            // Update project ordering
                            projectManager.moveProjectToTop(newValue);
                            projectManager.saveData();
                            
                            // Refresh combobox with new order on FX thread
                            Platform.runLater(() -> {
                                reloadProjectNamesCB();
                                notifyControllersOfProjectChange(newValue);
                                // End loading mode after all controllers are notified and data is loaded
                                Platform.runLater(() -> changeTrackingService.endLoading());
                            });
                            
                        } catch (Exception e) {
                            logger.severe("Error in project selection handling: " + e.getMessage());
                            Platform.runLater(() -> changeTrackingService.endLoading());
                        }
                    });
                } else {
                    // For "None" selection, notify controllers immediately
                    notifyControllersOfProjectChange(newValue);
                    changeTrackingService.endLoading();
                }
                
            } catch (Exception e) {
                logger.severe("Error in project selection handling: " + e.getMessage());
                changeTrackingService.endLoading();
            }
            
        } catch (Exception e) {
            logger.severe("Error in project selection listener: " + e.getMessage());
            changeTrackingService.endLoading();
        }
    }
    
    /**
     * Notifies all controllers about project selection changes
     * @param newProjectName The newly selected project name
     */
    private void notifyControllersOfProjectChange(String newProjectName) {
        try {
            // Notify BuildAutomation controller
            if (buildAutomation != null) {
                buildAutomation.onProjectSelectionChanged(newProjectName);
            }
            
            // Notify DatastoreDumpController
            if (datastoreDumpController != null) {
                datastoreDumpController.onProjectSelectionChanged(newProjectName);
            }
            
            // Notify JarDecompilerController
            if (jarDecompilerController != null) {
                jarDecompilerController.onProjectSelectionChanged(newProjectName);
            }
            
        } catch (Exception e) {
            logger.severe("Error notifying controllers of project change: " + e.getMessage());
        }
    }

    /**
     * Performs global save operation for all active tabs
     */
    public void performGlobalSave() {
        logger.info("Performing global save operation");
        
        try {
            // Save project details (always available)
            if (projectDetailsController != null) {
                projectDetailsController.saveProjectDetails();
            }
            
            // Save build automation settings if build tab is active
            if (buildAutomation != null) {
                buildAutomation.saveBuildAutomationSettings();
            }
            
            // Save datastore settings if datastore tab is active
            if (datastoreDumpController != null) {
                datastoreDumpController.saveDatastoreSettings();
            }
            
            // Mark changes as saved
            changeTrackingService.markAsSaved();
            
            logger.info("Global save operation completed successfully");
            
        } catch (Exception e) {
            logger.severe("Error during global save operation: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Save Error", "An error occurred while saving: " + e.getMessage());
        }
    }
    
    /**
     * Setup window close handler to prompt for unsaved changes
     */
    private void setupWindowCloseHandler() {
        try {
            if (root != null && root.getScene() != null && root.getScene().getWindow() != null) {
                Stage stage = (Stage) root.getScene().getWindow();
                stage.setOnCloseRequest(event -> {
                    logger.info("Window close requested - performing cleanup");
                    
                    // Perform build automation cleanup
                    if (buildAutomation != null) {
                        buildAutomation.cleanup();
                    }
                    
                    if (changeTrackingService != null && changeTrackingService.hasUnsavedChanges()) {
                        logger.info("Window close requested with unsaved changes, showing confirmation dialog");
                        SaveConfirmationDialog dialog = new SaveConfirmationDialog(stage, changeTrackingService);
                        Boolean result = dialog.showAndWait();
                        
                        if (result == null) {
                            // User cancelled, prevent window from closing
                            logger.info("User cancelled window close");
                            event.consume();
                            return; // Don't proceed with cleanup if user cancelled
                        } else if (result) {
                            // User chose to save, perform save and allow close
                            logger.info("User chose to save before closing");
                            performGlobalSave();
                        } else {
                            // User chose to discard changes, allow close
                            logger.info("User chose to discard changes before closing");
                        }
                    }
                    
                    // Close all cached SSH sessions on application shutdown
                    try {
                        logger.info("Closing all cached SSH sessions...");
                        SSHSessionManager.closeAllSessions();
                        logger.info("All SSH sessions closed successfully");
                    } catch (Exception e) {
                        logger.warning("Error closing SSH sessions: " + e.getMessage());
                    }
                    
                    logger.info("Window close cleanup completed");
                });
            } else {
                logger.warning("Cannot setup window close handler - stage not available");
            }
        } catch (Exception e) {
            logger.warning("Error setting up window close handler: " + e.getMessage());
        }
    }
    
    /**
     * Setup keyboard accelerators for the application
     */
    private void setupAccelerators() {
        try {
            if (root != null && root.getScene() != null) {
                Scene scene = root.getScene();
                
                // Add Ctrl+S accelerator for save
                KeyCodeCombination saveAccelerator = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);
                scene.getAccelerators().put(saveAccelerator, this::performGlobalSave);
                
                // Add Ctrl+K accelerator for opening NMS support log file (swapped with L)
                KeyCodeCombination logAccelerator = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
                scene.getAccelerators().put(logAccelerator, this::openNmsSupportLog);
                
                // Add Ctrl+L accelerator for opening recent NMS log (swapped with K)
                KeyCodeCombination nmsLogAccelerator = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN);
                scene.getAccelerators().put(nmsLogAccelerator, this::openRecentNmsLog);
                
                // Add Ctrl+D accelerator for opening cmd at jconfig path
                KeyCodeCombination cmdJconfigAccelerator = new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN);
                scene.getAccelerators().put(cmdJconfigAccelerator, this::openCmdAtJconfigPath);
                
                // Add Ctrl+Shift+L accelerator for opening OracleNMS log directory
                KeyCodeCombination oracleNmsLogDirAccelerator = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
                scene.getAccelerators().put(oracleNmsLogDirAccelerator, this::openOracleNmsLogDirectory);
                
                // Add Ctrl+R accelerator for restart (only works when on application management tab)
                KeyCodeCombination restartAccelerator = new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN);
                scene.getAccelerators().put(restartAccelerator, this::handleRestartShortcut);
                
                // Add Ctrl+E accelerator for editing default Cline workflow template
                KeyCodeCombination editTemplateAccelerator = new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN);
                scene.getAccelerators().put(editTemplateAccelerator, this::editDefaultWorkflowTemplate);
                
                logger.info("Keyboard accelerators setup completed");
            } else {
                logger.warning("Cannot setup accelerators - scene not available");
            }
        } catch (Exception e) {
            logger.warning("Error setting up accelerators: " + e.getMessage());
        }
    }
    
    /**
     * Setup save button visual feedback based on unsaved changes
     */
    private void setupSaveButtonVisualFeedback() {
        if (changeTrackingService != null) {
            changeTrackingService.hasUnsavedChangesProperty().addListener((observable, oldValue, newValue) -> {
                if (mainSaveButton != null) {
                    if (newValue) {
                        // Has unsaved changes - make icon red
                        mainSaveButton.getStyleClass().add("save-button-unsaved");
                        mainSaveButton.getStyleClass().remove("save-button-saved");
                    } else {
                        // No unsaved changes - make icon blue
                        mainSaveButton.getStyleClass().add("save-button-saved");
                        mainSaveButton.getStyleClass().remove("save-button-unsaved");
                    }
                }
            });
        }
    }

    private void setTabState(String newValue) {
        if(newValue != null && newValue.equals("None")){
            buildTab.setDisable(true);
            dataStoreTab.setDisable(true);
            projectTab.setDisable(true);
            jarDecompilerTab.setDisable(true);
            // Disable buttons when no project is selected
            delButton.setDisable(true);
            editProjectButton.setDisable(true);
            mainSaveButton.setDisable(true);
        }
        else{
            buildTab.setDisable(false);
            dataStoreTab.setDisable(false);
            projectTab.setDisable(false);
            jarDecompilerTab.setDisable(false);
            // Enable buttons when a project is selected
            delButton.setDisable(false);
            editProjectButton.setDisable(false);
            mainSaveButton.setDisable(false);
        }
    }

    private void loadTabContent() {
        try {
            // Load only the first tab synchronously for immediate responsiveness
            loadBuildAutomationTab();
            
            // Setup tab selection listeners for lazy loading
            setupTabSelectionListeners();
            
            // Preload other tabs in background for faster switching
            preloadTabsInBackground();
            
            logger.info("Initial tab content loaded successfully.");
        } catch (IOException e) {
            LoggerUtil.error(e);
            logger.severe("Error loading initial tab content: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Unexpected error loading initial tab content: " + e.getMessage());
            LoggerUtil.error(e);
        }
    }
    
    /**
     * Preload tabs in background for faster switching
     */
    private void preloadTabsInBackground() {
        Thread.ofVirtual().start(() -> {
            try {
                // Preload project details tab
                Platform.runLater(() -> {
                    if (splashController != null) {
                        splashController.updateStatus("Loading Project Details...");
                    }
                });
                Thread.sleep(200); // Small delay to not interfere with startup
                Platform.runLater(() -> {
                    try {
                        loadProjectDetailsTab();
                        logger.info("Project Details tab preloaded");
                        onTabPreloaded();
                    } catch (IOException e) {
                        logger.warning("Failed to preload Project Details tab: " + e.getMessage());
                        onTabPreloaded();
                    }
                });
                
                // Preload datastore dump tab
                Platform.runLater(() -> {
                    if (splashController != null) {
                        splashController.updateStatus("Loading Datastore Explorer...");
                    }
                });
                Thread.sleep(200);
                Platform.runLater(() -> {
                    try {
                        loadDatastoreDumpTab();
                        logger.info("Datastore Explorer tab preloaded");
                        onTabPreloaded();
                    } catch (IOException e) {
                        logger.warning("Failed to preload Datastore Explorer tab: " + e.getMessage());
                        onTabPreloaded();
                    }
                });
                
                // Preload jar decompiler tab
                Platform.runLater(() -> {
                    if (splashController != null) {
                        splashController.updateStatus("Loading JAR Decompiler...");
                    }
                });
                Thread.sleep(200);
                Platform.runLater(() -> {
                    try {
                        loadJarDecompilerTab();
                        logger.info("JAR Decompiler tab preloaded");
                        onTabPreloaded();
                    } catch (IOException e) {
                        logger.warning("Failed to preload JAR Decompiler tab: " + e.getMessage());
                        onTabPreloaded();
                    }
                });
                
            } catch (InterruptedException e) {
                logger.warning("Tab preloading interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.warning("Error during tab preloading: " + e.getMessage());
            }
        });
    }
    
    /**
     * Load build automation tab synchronously (first tab)
     */
    private void loadBuildAutomationTab() throws IOException {
        FXMLLoader buildLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/build-automation.fxml"));
        if (buildLoader.getLocation() == null) {
            logger.severe("Build automation FXML resource not found");
            return;
        }
        Parent buildContent = buildLoader.load();
        buildAutomation = buildLoader.getController();
        if (buildAutomation != null) {
            buildAutomation.setMainController(this);
            buildTab.setContent(buildContent);
        } else {
            logger.severe("Build automation controller is null");
        }
    }
    
    /**
     * Load datastore dump tab lazily
     */
    private void loadDatastoreDumpTab() throws IOException {
        if (datastoreDumpController != null) return; // Already loaded
        
        FXMLLoader dataStoreLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/datastore-dump.fxml"));
        if (dataStoreLoader.getLocation() == null) {
            logger.severe("Datastore dump FXML resource not found");
            return;
        }
        Parent dataStoreContent = dataStoreLoader.load();
        datastoreDumpController = dataStoreLoader.getController();
        if (datastoreDumpController != null) {
            datastoreDumpController.setMainController(this);
            // Only set content if tab doesn't already have content
            if (dataStoreTab.getContent() == null || dataStoreTab.getContent() instanceof ProgressIndicator) {
                dataStoreTab.setContent(dataStoreContent);
            }
        } else {
            logger.severe("Datastore dump controller is null");
        }
    }
    
    /**
     * Load project details tab lazily
     */
    private void loadProjectDetailsTab() throws IOException {
        if (projectDetailsController != null) return; // Already loaded
        
        FXMLLoader projectLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/project-details.fxml"));
        if (projectLoader.getLocation() == null) {
            logger.severe("Project details FXML resource not found");
            return;
        }
        Parent projectContent = projectLoader.load();
        projectDetailsController = projectLoader.getController();
        if (projectDetailsController != null) {
            projectDetailsController.setMainController(this);
            // Only set content if tab doesn't already have content
            if (projectTab.getContent() == null || projectTab.getContent() instanceof ProgressIndicator) {
                projectTab.setContent(projectContent);
            }
        } else {
            logger.severe("Project details controller is null");
        }
    }
    
    /**
     * Load jar decompiler tab lazily
     */
    private void loadJarDecompilerTab() throws IOException {
        if (jarDecompilerController != null) return; // Already loaded
        
        FXMLLoader jarDecompilerLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/jar-decompiler.fxml"));
        if (jarDecompilerLoader.getLocation() == null) {
            logger.severe("Jar decompiler FXML resource not found");
            return;
        }
        Parent jarDecompilerContent = jarDecompilerLoader.load();
        jarDecompilerController = jarDecompilerLoader.getController();
        if (jarDecompilerController != null) {
            jarDecompilerController.setMainController(this);
            // Only set content if tab doesn't already have content
            if (jarDecompilerTab.getContent() == null || jarDecompilerTab.getContent() instanceof ProgressIndicator) {
                jarDecompilerTab.setContent(jarDecompilerContent);
            }
        } else {
            logger.severe("Jar decompiler controller is null");
        }
    }
    
    /**
     * Load tab with loading indicator and asynchronous loading
     */
    private void loadTabWithIndicator(Tab tab, String tabName, Runnable loadAction) {
        // Check if tab is already loaded
        if (tab.getContent() != null && !(tab.getContent() instanceof ProgressIndicator)) {
            // Tab is already loaded, just run the action
            loadAction.run();
            return;
        }
        
        // Show loading indicator
        showTabLoadingIndicator(tab, tabName);
        
        // Load tab content asynchronously
        Thread.ofVirtual().start(() -> {
            try {
                // Load the tab content immediately for faster response
                Platform.runLater(() -> {
                    try {
                        loadAction.run();
                        hideTabLoadingIndicator(tab);
                        logger.info(tabName + " tab loaded successfully");
                    } catch (Exception e) {
                        logger.severe("Error loading " + tabName + " tab: " + e.getMessage());
                        showTabErrorIndicator(tab, "Error loading " + tabName);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    logger.severe("Error in async loading of " + tabName + " tab: " + e.getMessage());
                    showTabErrorIndicator(tab, "Error loading " + tabName);
                });
            }
        });
    }
    
    /**
     * Show loading indicator in tab
     */
    private void showTabLoadingIndicator(Tab tab, String tabName) {
        VBox loadingContainer = new VBox(10);
        loadingContainer.setAlignment(javafx.geometry.Pos.CENTER);
        loadingContainer.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 20;");
        
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(40, 40);
        progressIndicator.setStyle("-fx-progress-color: #0366d6;");
        
        Label loadingLabel = new Label("Loading " + tabName + "...");
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #586069; -fx-font-weight: 500;");
        
        loadingContainer.getChildren().addAll(progressIndicator, loadingLabel);
        tab.setContent(loadingContainer);
    }
    
    /**
     * Hide loading indicator and show actual content
     */
    private void hideTabLoadingIndicator(Tab tab) {
        // The actual content will be set by the loadAction
        // This method is called after content is loaded to ensure cleanup
        if (tab.getContent() instanceof ProgressIndicator) {
            // Content should already be set by loadAction, but ensure it's not stuck
            logger.info("Clearing loading indicator for tab: " + tab.getText());
        }
    }
    
    /**
     * Show error indicator in tab
     */
    private void showTabErrorIndicator(Tab tab, String errorMessage) {
        VBox errorContainer = new VBox(10);
        errorContainer.setAlignment(javafx.geometry.Pos.CENTER);
        errorContainer.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 20;");
        
        Label errorIcon = new Label("⚠");
        errorIcon.setStyle("-fx-font-size: 24px; -fx-text-fill: #d73a49;");
        
        Label errorLabel = new Label(errorMessage);
        errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #d73a49; -fx-font-weight: 500;");
        
        Button retryButton = new Button("Retry");
        retryButton.setStyle("-fx-background-color: #0366d6; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 8 16;");
        retryButton.setOnAction(e -> {
            // Find the tab pane and trigger a refresh
            TabPane tabPane = findTabPane();
            if (tabPane != null) {
                tabPane.getSelectionModel().select(tab);
            }
        });
        
        errorContainer.getChildren().addAll(errorIcon, errorLabel, retryButton);
        tab.setContent(errorContainer);
    }

    /**
     * Sets up listeners for tab selection to refresh data when tabs are selected
     */
    private void setupTabSelectionListeners() {
        // Get the TabPane from the main view
        TabPane tabPane = findTabPane();
        if (tabPane != null) {
            // Add a flag to prevent recursive tab switching
            final boolean[] isProcessingTabSwitch = {false};
            
            tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
                // Prevent recursive tab switching
                if (isProcessingTabSwitch[0]) {
                    logger.info("Tab switch already in progress, ignoring");
                    return;
                }
                
                if (newTab != null && oldTab != null && !oldTab.equals(newTab)) {
                    logger.info("Tab switching from " + oldTab.getText() + " to " + newTab.getText());
                    
                    // Check for unsaved changes before switching tabs
                    if (changeTrackingService != null && changeTrackingService.hasUnsavedChanges()) {
                        try {
                            isProcessingTabSwitch[0] = true;
                            
                            if (root != null && root.getScene() != null && root.getScene().getWindow() != null) {
                                SaveConfirmationDialog dialog = new SaveConfirmationDialog((Stage) root.getScene().getWindow(), changeTrackingService);
                                Boolean result = dialog.showAndWait();
                                
                                if (result == null) {
                                    // User cancelled, revert tab selection and stay on current tab
                                    logger.info("User cancelled tab switch, staying on " + oldTab.getText());
                                    tabPane.getSelectionModel().select(oldTab);
                                    isProcessingTabSwitch[0] = false;
                                    return;
                                } else if (result) {
                                    // User chose to save, perform save and continue with tab switch
                                    logger.info("User chose to save, performing save operation");
                                    performGlobalSave();
                                } else {
                                    // User chose to discard changes, continue with tab switch
                                    logger.info("User chose to discard changes");
                                    changeTrackingService.markAsSaved();
                                }
                            }
                        } catch (Exception e) {
                            logger.warning("Error showing save confirmation dialog: " + e.getMessage());
                            // If there's an error, revert to the old tab
                            tabPane.getSelectionModel().select(oldTab);
                            isProcessingTabSwitch[0] = false;
                            return;
                        } finally {
                            isProcessingTabSwitch[0] = false;
                        }
                    }
                    
                    // Refresh data based on which tab was selected with lazy loading
                    if (newTab == buildTab && buildAutomation != null) {
                        buildAutomation.onTabSelected();
                    } else if (newTab == projectTab) {
                        if (projectDetailsController != null) {
                            // Tab is already loaded, just refresh
                            projectDetailsController.onTabSelected();
                        } else {
                            loadTabWithIndicator(newTab, "Project Details", () -> {
                                try {
                                    loadProjectDetailsTab();
                                    if (projectDetailsController != null) {
                                        projectDetailsController.onTabSelected();
                                    }
                                } catch (IOException e) {
                                    logger.severe("Error loading project details tab: " + e.getMessage());
                                }
                            });
                        }
                    } else if (newTab == dataStoreTab) {
                        if (datastoreDumpController != null) {
                            // Tab is already loaded, just refresh
                            datastoreDumpController.onTabSelected();
                        } else {
                            loadTabWithIndicator(newTab, "Datastore Explorer", () -> {
                                try {
                                    loadDatastoreDumpTab();
                                    if (datastoreDumpController != null) {
                                        datastoreDumpController.onTabSelected();
                                    }
                                } catch (IOException e) {
                                    logger.severe("Error loading datastore dump tab: " + e.getMessage());
                                }
                            });
                        }
                    } else if (newTab == jarDecompilerTab) {
                        if (jarDecompilerController != null) {
                            // Tab is already loaded, just refresh
                            jarDecompilerController.onTabSelected();
                        } else {
                            loadTabWithIndicator(newTab, "JAR Decompiler", () -> {
                                try {
                                    loadJarDecompilerTab();
                                    if (jarDecompilerController != null) {
                                        jarDecompilerController.onTabSelected();
                                    }
                                } catch (IOException e) {
                                    logger.severe("Error loading jar decompiler tab: " + e.getMessage());
                                    LoggerUtil.error(e);
                                }
                            });
                        }
                    }
                }
            });
        } else {
            logger.warning("TabPane not found - tab selection listeners not set up");
        }
    }

    /**
     * Finds the TabPane in the scene
     */
    private TabPane findTabPane() {
        if (root != null) {
            return findTabPaneRecursive(root);
        }
        return null;
    }

    /**
     * Recursively searches for TabPane in the scene graph
     */
    private TabPane findTabPaneRecursive(javafx.scene.Node node) {
        if (node instanceof TabPane) {
            return (TabPane) node;
        }
        
        if (node instanceof javafx.scene.Parent) {
            javafx.scene.Parent parent = (javafx.scene.Parent) node;
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                TabPane result = findTabPaneRecursive(child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }

    private void removeProject() {
        String selectedProjectName = projectComboBox.getValue();
        if ("None".equals(selectedProjectName)) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project Selected", "Please select a project to remove.");
            return;
        }

        Optional<ButtonType> result = DialogUtil.showConfirmationDialog(
                "Confirm Removal",
                "Are you sure you want to remove this project?",
                "Project: " + selectedProjectName
        );

        if (result.isPresent() && result.get() == ButtonType.OK) {
            projectManager.deleteProject(selectedProjectName);
            projectManager.saveData();
            reloadProjectNamesCB(); // Refresh ComboBox with updated list
            DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Project Removed", "Project removed successfully.");
            logger.info("Project removed: " + selectedProjectName);
        }
    }

    private void addProject() {
        try {
            // Create and show the custom Add Project dialog
            AddProjectDialog dialog = new AddProjectDialog();
            Stage parentStage = (Stage) projectComboBox.getScene().getWindow();
            
            dialog.showDialog(parentStage).thenAccept(result -> {
                if (result.isPresent()) {
                    String projectName = result.get().trim();
                    
                    // Validate project name
                    if (projectName.isEmpty()) {
                        DialogUtil.showAlert(Alert.AlertType.WARNING, "Invalid Input", "Project name cannot be empty.");
                        return;
                    }

                    // Check for duplicate project names
                    List<String> existingProjects = projectManager.getProjects() != null ? projectManager.getProjects().stream()
                            .map(ProjectEntity::getName)
                            .collect(Collectors.toList()) : new ArrayList<>();

                    if (existingProjects.contains(projectName)) {
                        DialogUtil.showAlert(Alert.AlertType.WARNING, "Duplicate Project", "Project with this name already exists.");
                    } else {
                        // Create and add the new project
                        ProjectEntity newProject = new ProjectEntity(projectName);
                        projectManager.addProject(newProject);
                        projectManager.saveData();
                        reloadProjectNamesCB(); // Refresh ComboBox with updated list
                        projectComboBox.setValue(newProject.getName());
                        
                        logger.info("Successfully added new project: " + projectName);
                    }
                } else {
                    logger.info("Add project dialog cancelled by user");
                }
            }).exceptionally(throwable -> {
                logger.severe("Error in add project dialog: " + throwable.getMessage());
                Platform.runLater(() -> {
                    DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", 
                        "Failed to show add project dialog: " + throwable.getMessage());
                });
                return null;
            });
            
        } catch (Exception e) {
            logger.severe("Error creating add project dialog: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", 
                "Failed to create add project dialog: " + e.getMessage());
        }
    }


    public ProjectEntity getSelectedProject() {
        String selectedProjectName = projectComboBox.getValue();
        if ("None".equals(selectedProjectName)) return null;
        logger.info("Retrieved selected project: " + selectedProjectName);
        return projectManager.getProjectByName(selectedProjectName);
    }

    private void editProject() {
        String selectedProjectName = projectComboBox.getValue();
        if (selectedProjectName == null || selectedProjectName.trim().isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project Selected", "Please select a project to edit.");
            return;
        }
        
        try {
            // Create and show the edit project name dialog
            EditProjectNameDialog dialog = new EditProjectNameDialog();
            Stage parentStage = (Stage) projectComboBox.getScene().getWindow();
            
            dialog.showDialog(parentStage, projectManager, selectedProjectName).thenAccept(result -> {
                if (result.isPresent()) {
                    String newProjectName = result.get();
                    Platform.runLater(() -> {
                        try {
                            // Update the project name in the project manager
                            ProjectEntity project = projectManager.getProject(selectedProjectName);
                            if (project != null) {
                                project.setName(newProjectName);
                                projectManager.saveData();

                                // Refresh the ComboBox and select the updated project on FX thread
                                reloadProjectNamesCB();
                                projectComboBox.setValue(newProjectName);

                                // Ensure tabs and dependent controllers reflect the valid selection
                                setTabState(newProjectName);
                                notifyControllersOfProjectChange(newProjectName);

                                logger.info("Successfully updated project name from '" + selectedProjectName + "' to '" + newProjectName + "'");
                            } else {
                                DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Project not found.");
                            }
                        } catch (Exception e) {
                            logger.severe("Error updating project name: " + e.getMessage());
                            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to update project name: " + e.getMessage());
                        }
                    });
                }
            }).exceptionally(throwable -> {
                logger.severe("Error in edit project dialog: " + throwable.getMessage());
                Platform.runLater(() -> {
                    DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", 
                        "Failed to show edit project dialog: " + throwable.getMessage());
                });
                return null;
            });
            
        } catch (Exception e) {
            logger.severe("Error creating edit project dialog: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", 
                "Failed to create edit project dialog: " + e.getMessage());
        }
    }

    private void reloadProjectNamesCB() {
        List<String> projectNames = projectManager.getProjects() != null ? projectManager.getProjects().stream()
                .map(ProjectEntity::getName)
                .collect(Collectors.toList()) : new ArrayList<>();

        projectNames.add(0, "None"); // Add "None" as the first item
        
        // Store current selection before clearing
        String currentSelection = projectComboBox.getValue();
        logger.info("Current selection : "+ currentSelection);
        
        // Safely update the ComboBox items
        try {
            // Set flag to prevent listener from triggering during programmatic changes
            isUpdatingComboBoxProgrammatically = true;
            
            // Clear selection first to prevent JavaFX from maintaining selection by index
            projectComboBox.getSelectionModel().clearSelection();
            
            // Update the items list
            projectComboBox.getItems().setAll(projectNames);
            
            // Restore selection immediately if it still exists in the new list
            if (currentSelection != null && projectNames.contains(currentSelection)) {
                // Set the value immediately to avoid visual flicker
                projectComboBox.setValue(currentSelection);
                logger.info("Restored selection to: " + currentSelection);
            } else {
                // If current selection is no longer valid, select "None"
                projectComboBox.setValue("None");
                logger.info("Current selection invalid, set to None");
                
                // Manually trigger tab state update since listener is disabled
                Platform.runLater(() -> {
                    // Only apply 'None' state if it is STILL the current selection
                    if ("None".equals(projectComboBox.getValue())) {
                        setTabState("None");
                        // Clear all fields in all tabs
                        if (projectDetailsController != null) {
                            projectDetailsController.clearFields();
                        }
                        if (buildAutomation != null) {
                            buildAutomation.clearFields();
                        }
                        if (datastoreDumpController != null) {
                            datastoreDumpController.clearFields();
                        }
                        if (jarDecompilerController != null) {
                            jarDecompilerController.clearFields();
                        }
                    } else {
                        logger.info("Skipping None-state cleanup; current selection is: " + projectComboBox.getValue());
                    }
                });
            }
            
            // Clear the flag after programmatic changes are complete
            isUpdatingComboBoxProgrammatically = false;
            
            // Request layout update in Platform.runLater for proper rendering
            Platform.runLater(() -> {
                try {
                    projectComboBox.requestLayout();
                    logger.info("Final ComboBox value after reload: " + projectComboBox.getValue());
                } catch (Exception e) {
                    logger.warning("Error requesting ComboBox layout: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.severe("Error reloading project names ComboBox: " + e.getMessage());
            // Fallback: try to set a minimal list
            try {
                projectComboBox.getItems().setAll("None");
                projectComboBox.setValue("None");
            } catch (Exception fallbackException) {
                logger.severe("Fallback ComboBox update also failed: " + fallbackException.getMessage());
            } finally {
                // Always clear the flag, even in case of exception
                isUpdatingComboBoxProgrammatically = false;
            }
        }

        logger.info("Project names reloaded in ComboBox.");
    }
    

    @FXML
    private void openVpnManager(){
        try {
            // Load the dialog FXML file
            FXMLLoader vpnloader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/vpn-manager.fxml"));
            Parent root = vpnloader.load();
            Stage dialogStage = new Stage();
            IconUtils.setStageIcon((javafx.stage.Stage) dialogStage);
            dialogStage.initModality(Modality.APPLICATION_MODAL); // Makes it a modal dialog
            dialogStage.setTitle("Cisco VPN Manager");
            dialogStage.setScene(new Scene(root));

            dialogStage.show(); // Show the dialog and wait for it to close
            ((VpnController) vpnloader.getController()).setMainController(this);

        } catch (Exception e) {
            LoggerUtil.error(e);
            e.printStackTrace();
        }
    }

    /**
     * Opens the NMS support log file using intelligent editor selection
     * Priority: VS Code -> Notepad++ -> Windows Notepad
     */
    private void openNmsSupportLog() {
        try {
            // Get log file path
            String user = System.getProperty("user.name");
            String logPath = "C:\\Users\\" + user + "\\Documents\\nms_support_data\\nms_support.log";
            
            logger.info("Attempting to open NMS support log: " + logPath);
            
            // Check if log file exists
            File logFile = new File(logPath);
            if (!logFile.exists()) {
                logger.warning("Log file does not exist: " + logPath);
                DialogUtil.showError("Log File Not Found", 
                    "The NMS support log file was not found at:\n" + logPath + 
                    "\n\nThis may indicate that logging has not been initialized yet.");
                return;
            }
            
            // Try to open with intelligent editor selection
            boolean opened = false;
            
            // 1. Try VS Code first
            if (!opened) {
                opened = tryOpenWithVSCode(logPath);
            }
            
            // 2. Try Notepad++ if VS Code failed
            if (!opened) {
                opened = tryOpenWithNotepadPlusPlus(logPath);
            }
            
            // 3. Fall back to Windows Notepad
            if (!opened) {
                opened = tryOpenWithNotepad(logPath);
            }
            
            if (opened) {
                logger.info("Successfully opened log file with selected editor");
            } else {
                logger.severe("Failed to open log file with any editor");
                DialogUtil.showError("Cannot Open Log File", 
                    "Unable to open the log file with any available editor.\n" +
                    "Please check that you have VS Code, Notepad++, or Windows Notepad installed.");
            }
            
        } catch (Exception e) {
            logger.severe("Error opening NMS support log: " + e.getMessage());
            DialogUtil.showError("Error Opening Log", "An error occurred while trying to open the log file:\n" + e.getMessage());
        }
    }
    
    /**
     * Attempts to open file with VS Code
     */
    private boolean tryOpenWithVSCode(String filePath) {
        try {
            // Common VS Code installation paths
            String[] vsCodePaths = {
                System.getenv("LOCALAPPDATA") + "\\Programs\\Microsoft VS Code\\Code.exe",
                System.getenv("PROGRAMFILES") + "\\Microsoft VS Code\\Code.exe",
                System.getenv("PROGRAMFILES(X86)") + "\\Microsoft VS Code\\Code.exe",
                System.getenv("USERPROFILE") + "\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe"
            };
            
            for (String vsCodePath : vsCodePaths) {
                File vsCodeExe = new File(vsCodePath);
                if (vsCodeExe.exists()) {
                    logger.info("Found VS Code at: " + vsCodePath);
                    ProcessBuilder pb = new ProcessBuilder(vsCodePath, filePath);
                    Process process = pb.start();
                    
                    // Check if process started successfully (non-zero exit code means it started)
                    Thread.sleep(1000); // Give it a moment to start
                    if (process.isAlive() || process.exitValue() == 0) {
                        logger.info("Successfully opened log file with VS Code");
                        return true;
                    }
                }
            }
            
            // Try using 'code' command if available in PATH
            try {
                ProcessBuilder pb = new ProcessBuilder("code", filePath);
                Process process = pb.start();
                Thread.sleep(1000);
                if (process.isAlive() || process.exitValue() == 0) {
                    logger.info("Successfully opened log file with VS Code via command line");
                    return true;
                }
            } catch (Exception e) {
                logger.info("VS Code command line not available: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.warning("Error trying to open with VS Code: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Attempts to open file with Notepad++
     */
    private boolean tryOpenWithNotepadPlusPlus(String filePath) {
        try {
            // Common Notepad++ installation paths
            String[] notepadPaths = {
                System.getenv("PROGRAMFILES") + "\\Notepad++\\notepad++.exe",
                System.getenv("PROGRAMFILES(X86)") + "\\Notepad++\\notepad++.exe",
                System.getenv("LOCALAPPDATA") + "\\Programs\\Notepad++\\notepad++.exe"
            };
            
            for (String notepadPath : notepadPaths) {
                File notepadExe = new File(notepadPath);
                if (notepadExe.exists()) {
                    logger.info("Found Notepad++ at: " + notepadPath);
                    ProcessBuilder pb = new ProcessBuilder(notepadPath, filePath);
                    Process process = pb.start();
                    
                    Thread.sleep(1000);
                    if (process.isAlive() || process.exitValue() == 0) {
                        logger.info("Successfully opened log file with Notepad++");
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error trying to open with Notepad++: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Attempts to open file with Windows Notepad
     */
    private boolean tryOpenWithNotepad(String filePath) {
        try {
            logger.info("Attempting to open log file with Windows Notepad");
            ProcessBuilder pb = new ProcessBuilder("notepad.exe", filePath);
            Process process = pb.start();
            
            Thread.sleep(1000);
            if (process.isAlive() || process.exitValue() == 0) {
                logger.info("Successfully opened log file with Windows Notepad");
                return true;
            }
            
        } catch (Exception e) {
            logger.severe("Error trying to open with Windows Notepad: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * Opens the most recent Oracle NMS log file using intelligent editor selection
     * If a project is selected with a host URL, tries to find logs matching that URL
     * Otherwise opens the most recent log file
     * Priority: VS Code -> Notepad++ -> Windows Notepad
     */
    private void openRecentNmsLog() {
        try {
            logger.info("Attempting to open recent Oracle NMS log");
            
            // Get NMS log directory path
            String logDirectoryPath = com.nms.support.nms_support.service.buildTabPack.ControlApp.getLogDirectoryPath();
            File logDirectory = new File(logDirectoryPath);
            
            if (!logDirectory.exists()) {
                logger.warning("NMS log directory does not exist: " + logDirectoryPath);
                DialogUtil.showError("NMS Log Directory Not Found", 
                    "The Oracle NMS log directory was not found at:\n" + logDirectoryPath + 
                    "\n\nThis may indicate that Oracle NMS has not been started yet.");
                return;
            }
            
            // Get the most recent log file
            File recentLogFile = findRecentNmsLogFile(logDirectory);
            
            if (recentLogFile == null) {
                logger.warning("No NMS log files found in directory: " + logDirectoryPath);
                DialogUtil.showError("No NMS Log Files Found", 
                    "No Oracle NMS log files were found in:\n" + logDirectoryPath + 
                    "\n\nPlease ensure Oracle NMS has been started at least once.");
                return;
            }
            
            logger.info("Found recent NMS log file: " + recentLogFile.getAbsolutePath());
            
            // Try to open with intelligent editor selection
            boolean opened = false;
            
            // 1. Try VS Code first
            if (!opened) {
                opened = tryOpenWithVSCode(recentLogFile.getAbsolutePath());
            }
            
            // 2. Try Notepad++ if VS Code failed
            if (!opened) {
                opened = tryOpenWithNotepadPlusPlus(recentLogFile.getAbsolutePath());
            }
            
            // 3. Fall back to Windows Notepad
            if (!opened) {
                opened = tryOpenWithNotepad(recentLogFile.getAbsolutePath());
            }
            
            if (opened) {
                logger.info("Successfully opened recent NMS log file with selected editor");
            } else {
                logger.severe("Failed to open recent NMS log file with any editor");
                DialogUtil.showError("Cannot Open NMS Log File", 
                    "Unable to open the NMS log file with any available editor.\n" +
                    "Please check that you have VS Code, Notepad++, or Windows Notepad installed.");
            }
            
        } catch (Exception e) {
            logger.severe("Error opening recent NMS log: " + e.getMessage());
            DialogUtil.showError("Error Opening NMS Log", "An error occurred while trying to open the recent NMS log file:\n" + e.getMessage());
        }
    }
    
    /**
     * Finds the most recent NMS log file, filtering by hostname from the selected project's NMS URL
     * @param logDirectory The directory containing NMS log files
     * @return The most recent log file, or null if none found
     */
    private File findRecentNmsLogFile(File logDirectory) {
        try {
            // Get all log files
            File[] allLogFiles = logDirectory.listFiles((dir, name) -> 
                name.toLowerCase().contains(".log") && new File(dir, name).isFile());
            
            if (allLogFiles == null || allLogFiles.length == 0) {
                return null;
            }
            
            // Get selected project info
            ProjectEntity selectedProject = getSelectedProject();
            String projectHostname = null;
            
            if (selectedProject != null && selectedProject.getNmsAppURL() != null && 
                !selectedProject.getNmsAppURL().trim().isEmpty()) {
                
                String nmsUrl = selectedProject.getNmsAppURL().trim();
                projectHostname = extractHostnameFromUrl(nmsUrl);
                logger.info("Looking for logs containing hostname: " + projectHostname);
            }
            
            File[] logFiles = allLogFiles;
            
            // If we have a project hostname, filter logs by hostname
            if (projectHostname != null) {
                java.util.List<File> filteredLogs = new java.util.ArrayList<>();
                for (File logFile : allLogFiles) {
                    if (logFile.getName().toLowerCase().contains(projectHostname.toLowerCase())) {
                        filteredLogs.add(logFile);
                    }
                }
                
                if (!filteredLogs.isEmpty()) {
                    logFiles = filteredLogs.toArray(new File[0]);
                    logger.info("Found " + logFiles.length + " log files containing hostname: " + projectHostname);
                } else {
                    logger.info("No logs found containing hostname: " + projectHostname + ", using all logs");
                }
            }
            
            // Sort files by last modified (most recent first)
            java.util.Arrays.sort(logFiles, (f1, f2) -> 
                Long.compare(f2.lastModified(), f1.lastModified()));
            
            // Return the most recent log file (first in sorted array)
            logger.info("Using most recent log file: " + logFiles[0].getName());
            return logFiles[0];
            
        } catch (Exception e) {
            logger.severe("Error finding recent NMS log file: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts hostname from NMS URL
     * Example: https://ugbu-ash-147.sniadprshared1.gbucdsint02iad.oraclevcn.com:7112/nms/nmsapplications.jsp
     * Returns: ugbu-ash-147
     */
    private String extractHostnameFromUrl(String url) {
        try {
            // Remove protocol if present
            if (url.startsWith("http://") || url.startsWith("https://")) {
                url = url.substring(url.indexOf("://") + 3);
            }
            
            // Get the hostname part (before first slash or colon)
            String hostname = url.split("[/:]")[0];
            
            // Extract the first part before the first dot (e.g., ugbu-ash-147 from ugbu-ash-147.sniadprshared1...)
            if (hostname.contains(".")) {
                hostname = hostname.split("\\.")[0];
            }
            
            logger.info("Extracted hostname from URL: " + hostname);
            return hostname;
            
        } catch (Exception e) {
            logger.warning("Error extracting hostname from URL: " + url + ", error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Opens cmd at jconfig path if it exists, otherwise shows a warning
     * Triggered by Ctrl+D keyboard shortcut
     */
    private void openCmdAtJconfigPath() {
        try {
            logger.info("Attempting to open cmd at jconfig path");
            
            ProjectEntity selectedProject = getSelectedProject();
            if (selectedProject == null) {
                logger.warning("No project selected");
                DialogUtil.showError("No Project Selected", 
                    "Please select a project first to open cmd at jconfig path.");
                return;
            }
            
            String jconfigPath = selectedProject.getJconfigPathForBuild();
            if (jconfigPath == null || jconfigPath.trim().isEmpty()) {
                logger.warning("Jconfig path is missing for project: " + selectedProject.getName());
                DialogUtil.showError("JConfig Path Missing", 
                    "JConfig path is not configured for the selected project: " + selectedProject.getName() + 
                    "\n\nPlease configure the project folder in the Project Configuration tab.");
                return;
            }
            
            File jconfigDir = new File(jconfigPath);
            if (!jconfigDir.exists()) {
                logger.warning("Jconfig directory does not exist: " + jconfigPath);
                DialogUtil.showError("JConfig Directory Not Found", 
                    "The JConfig directory was not found at:\n" + jconfigPath + 
                    "\n\nPlease verify the project folder path in the Project Configuration tab.");
                return;
            }
            
            logger.info("Opening cmd at jconfig path: " + jconfigPath);
            
            // Open cmd at the jconfig path
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", "cd", "/d", jconfigPath);
            pb.start();
            
            logger.info("Successfully opened cmd at jconfig path");
            
        } catch (Exception e) {
            logger.severe("Error opening cmd at jconfig path: " + e.getMessage());
            DialogUtil.showError("Error Opening CMD", 
                "An error occurred while trying to open cmd at jconfig path:\n" + e.getMessage());
        }
    }
    
    /**
     * Opens the OracleNMS log directory in Windows Explorer
     * Triggered by Ctrl+Shift+L keyboard shortcut
     */
    private void openOracleNmsLogDirectory() {
        try {
            logger.info("Attempting to open OracleNMS log directory");
            
            // Get NMS log directory path from temp
            String logDirectoryPath = com.nms.support.nms_support.service.buildTabPack.ControlApp.getLogDirectoryPath();
            File logDirectory = new File(logDirectoryPath);
            
            if (!logDirectory.exists()) {
                logger.warning("OracleNMS log directory does not exist: " + logDirectoryPath);
                DialogUtil.showError("OracleNMS Log Directory Not Found", 
                    "The OracleNMS log directory was not found at:\n" + logDirectoryPath + 
                    "\n\nThis may indicate that Oracle NMS has not been started yet.");
                return;
            }
            
            logger.info("Opening OracleNMS log directory: " + logDirectoryPath);
            
            // Open the directory in Windows Explorer
            ProcessBuilder pb = new ProcessBuilder("explorer.exe", logDirectoryPath);
            pb.start();
            
            logger.info("Successfully opened OracleNMS log directory in Explorer");
            
        } catch (Exception e) {
            logger.severe("Error opening OracleNMS log directory: " + e.getMessage());
            DialogUtil.showError("Error Opening Directory", 
                "An error occurred while trying to open the OracleNMS log directory:\n" + e.getMessage());
        }
    }
    
    /**
     * Handles the restart keyboard shortcut (Ctrl+R)
     * Only triggers restart if the user is currently on the Application Management tab
     */
    private void handleRestartShortcut() {
        try {
            logger.info("Restart shortcut triggered (Ctrl+R)");
            
            // Find the TabPane and check if the selected tab is the build/application management tab
            TabPane tabPane = findTabPane();
            if (tabPane == null) {
                logger.warning("Cannot find TabPane to check selected tab");
                return;
            }
            
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != buildTab) {
                logger.info("Restart shortcut ignored - not on Application Management tab");
                return;
            }
            
            logger.info("Restart shortcut accepted - on Application Management tab");
            
            // Check if build automation controller is available
            if (buildAutomation == null) {
                logger.warning("Build automation controller is not available");
                DialogUtil.showError("Restart Not Available", 
                    "The restart function is not available at this time.");
                return;
            }
            
            // Trigger the restart via the BuildAutomation controller
            // We need to call the restart method through reflection or make it public
            // For now, let's check if the project is selected and call the button action
            ProjectEntity project = getSelectedProject();
            if (project == null) {
                logger.warning("No project selected for restart");
                DialogUtil.showError("No Project Selected", 
                    "Please select a project first to perform restart.");
                return;
            }
            
            // Since the restart method is private in BuildAutomation, we need to trigger it
            // by simulating the button click or calling it through the public interface
            // For now, let's use a workaround by making the method accessible
            try {
                java.lang.reflect.Method restartMethod = buildAutomation.getClass().getDeclaredMethod("restart");
                restartMethod.setAccessible(true);
                restartMethod.invoke(buildAutomation);
                logger.info("Restart triggered successfully via keyboard shortcut");
            } catch (NoSuchMethodException e) {
                logger.severe("Restart method not found in BuildAutomation");
                DialogUtil.showError("Restart Error", "Unable to access restart functionality.");
            } catch (Exception e) {
                logger.severe("Error invoking restart method: " + e.getMessage());
                DialogUtil.showError("Restart Error", "An error occurred while trying to restart:\n" + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.severe("Error handling restart shortcut: " + e.getMessage());
            DialogUtil.showError("Error", "An error occurred while handling restart shortcut:\n" + e.getMessage());
        }
    }
    
    /**
     * Opens VS Code workspace "AI (Cline)" for current project with project/product/decompiled folders
     * Also ensures a per-project workflow file exists under Documents/Cline/Workflows and offers to edit it.
     */
    private void openClineWorkspace() {
        try {
            ProjectEntity project = getSelectedProject();
            if (project == null) {
                DialogUtil.showError("No Project Selected", "Please select a project to open the AI (Cline) workspace.");
                return;
            }

            String projectName = project.getName() != null ? project.getName().trim() : "";
            if (projectName.isEmpty()) {
                DialogUtil.showError("Invalid Project", "Selected project has no name.");
                return;
            }

            // Resolve candidate folders
            String projectFolder = project.getProjectFolderPath(); // project root (without /jconfig)
            String productFolder = project.getExePath();           // product folder
            String decompiledFolder = com.nms.support.nms_support.service.globalPack.JarDecompilerService.getExtractionDirectory(projectName);

            java.util.List<String> workspaceFolders = new java.util.ArrayList<>();
            if (projectFolder != null && !projectFolder.isEmpty() && new File(projectFolder).exists()) {
                workspaceFolders.add(projectFolder);
            }
            if (productFolder != null && !productFolder.isEmpty() && new File(productFolder).exists()) {
                workspaceFolders.add(productFolder);
            }
            if (decompiledFolder != null && !decompiledFolder.isEmpty() && new File(decompiledFolder).exists()) {
                workspaceFolders.add(decompiledFolder);
            }

            if (workspaceFolders.isEmpty()) {
                DialogUtil.showError("No Folders Found", "Could not resolve any valid folders to include in the workspace.\nPlease configure project and product paths first.");
                return;
            }

            // Base directories for workspace and workflows
            Path documentsDir = Paths.get(System.getProperty("user.home"), "Documents");
            Path nmsDataDir = documentsDir.resolve("nms_support_data");
            Path workspacesDir = nmsDataDir.resolve("vscode_workspaces");
            Path workflowsDir = documentsDir.resolve("Cline").resolve("Workflows");
            ensureDir(workspacesDir);
            ensureDir(workflowsDir);
            
            // Workspace file path
            Path workspacePath = workspacesDir.resolve(projectName + ".code-workspace");
            String workspaceJson = buildOrMergeWorkspaceJson(workspacePath, workspaceFolders);
            Files.write(workspacePath, workspaceJson.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Workflow file path and initial content
            String workflowBase = projectName.toLowerCase(java.util.Locale.ROOT);
            Path workflowFile = workflowsDir.resolve(workflowBase + ".workflow.md");
            String templateContent = getOrCreateDefaultWorkflowTemplate();
            String initialWorkflow = fillWorkflowPlaceholders(templateContent, project, projectFolder, productFolder,
                    (decompiledFolder != null && new File(decompiledFolder).exists()) ? decompiledFolder : "Not generated");

            if (!Files.exists(workflowFile)) {
                Files.write(workflowFile, initialWorkflow.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW);
            } else {
                // If exists but empty, seed it
                if (Files.size(workflowFile) == 0) {
                    Files.write(workflowFile, initialWorkflow.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.TRUNCATE_EXISTING);
                }
            }


            // Launch VS Code: open workspace and the workflow file
            String vsCodePath = com.nms.support.nms_support.service.globalPack.JarDecompilerService.findVSCodeExecutable();
            java.util.List<String> cmd = new java.util.ArrayList<>();
            if (vsCodePath != null) {
                cmd.add(vsCodePath);
            } else {
                // fallback to PATH 'code'
                cmd.add("code");
            }
            cmd.add(workspacePath.toString());
            cmd.add(workflowFile.toString()); // open workflow by default

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.start();
            logger.info("Opened VS Code workspace: " + workspacePath + " with workflow file: " + workflowFile);

        } catch (Exception e) {
            logger.severe("Error opening Cline workspace: " + e.getMessage());
            DialogUtil.showError("Error", "Failed to open AI (Cline) workspace:\n" + e.getMessage());
        }
    }

    private String buildWorkspaceJson(java.util.List<String> folderPaths, String displayName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"folders\": [\n");
        for (int i = 0; i < folderPaths.size(); i++) {
            String p = folderPaths.get(i).replace("\\", "/");
            sb.append("    { \"path\": \"").append(p).append("\" }");
            if (i < folderPaths.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"settings\": {\n");
        // Optional: VS Code UX tweaks for Java projects
        sb.append("    \"files.exclude\": {\"**/.git\": true, \"**/.idea\": true, \"**/target\": true},\n");
        sb.append("    \"java.configuration.checkProjectSettingsExclusions\": false\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // New: Build or merge VS Code workspace JSON preserving user folders and settings
    private String buildOrMergeWorkspaceJson(Path workspacePath, java.util.List<String> requiredFolderPaths) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            // Use LinkedHashMap to preserve key order when writing back
            LinkedHashMap<String, Object> root;
            if (Files.exists(workspacePath)) {
                try {
                    root = mapper.readValue(Files.readAllBytes(workspacePath), new TypeReference<LinkedHashMap<String, Object>>() {});
                } catch (Exception e) {
                    // If existing file is malformed, start fresh but do NOT block the flow
                    logger.warning("Existing workspace JSON malformed, recreating: " + e.getMessage());
                    root = new LinkedHashMap<>();
                }
            } else {
                root = new LinkedHashMap<>();
            }

            // Ensure folders array exists
            java.util.List<LinkedHashMap<String, Object>> folders;
            Object foldersObj = root.get("folders");
            if (foldersObj instanceof java.util.List) {
                folders = new java.util.ArrayList<>();
                for (Object o : (java.util.List<?>) foldersObj) {
                    if (o instanceof java.util.Map) {
                        folders.add(new LinkedHashMap<>((java.util.Map<String, Object>) o));
                    }
                }
            } else {
                folders = new java.util.ArrayList<>();
            }

            // Build a set of normalized existing folder paths to avoid duplicates (Windows: case-insensitive)
            Set<String> existing = new HashSet<>();
            for (LinkedHashMap<String, Object> f : folders) {
                Object p = f.get("path");
                if (p instanceof String) {
                    existing.add(normalizePath((String) p));
                }
            }

            // Add required folders if not present
            for (String fp : requiredFolderPaths) {
                String norm = normalizePath(fp);
                if (!existing.contains(norm)) {
                    LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                    entry.put("path", toForwardSlashes(fp));
                    folders.add(entry);
                    existing.add(norm);
                }
            }

            root.put("folders", folders);

            // Merge minimal default settings only when absent; never remove/overwrite user settings
            LinkedHashMap<String, Object> settings;
            Object settingsObj = root.get("settings");
            if (settingsObj instanceof java.util.Map) {
                settings = new LinkedHashMap<>((java.util.Map<String, Object>) settingsObj);
            } else {
                settings = new LinkedHashMap<>();
            }

            // Only add defaults if they don't exist already
            if (!settings.containsKey("files.exclude")) {
                LinkedHashMap<String, Object> filesExclude = new LinkedHashMap<>();
                filesExclude.put("**/.git", true);
                filesExclude.put("**/.idea", true);
                filesExclude.put("**/target", true);
                settings.put("files.exclude", filesExclude);
            }
            settings.putIfAbsent("java.configuration.checkProjectSettingsExclusions", false);

            root.put("settings", settings);

            // Pretty print back to string
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            logger.warning("Failed to build/merge workspace JSON: " + e.getMessage());
            // As a fail-safe, return a minimal workspace containing the required folders
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"folders\": [\n");
            for (int i = 0; i < requiredFolderPaths.size(); i++) {
                String p = toForwardSlashes(requiredFolderPaths.get(i));
                sb.append("    { \"path\": \"").append(p).append("\" }");
                if (i < requiredFolderPaths.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");
            return sb.toString();
        }
    }

    private String normalizePath(String p) {
        // VS Code stores workspace paths as given; for comparison use lowercase and forward slashes
        return toForwardSlashes(p).toLowerCase(java.util.Locale.ROOT);
    }

    private String toForwardSlashes(String p) {
        return p.replace("\\", "/");
    }

    private Path ensureDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }



    // Default Cline workflow template management (stored under Documents/nms_support_data)
    private Path getDefaultWorkflowTemplatePath() {
        Path documentsDir = Paths.get(System.getProperty("user.home"), "Documents");
        Path nmsDataDir = documentsDir.resolve("nms_support_data");
        return nmsDataDir.resolve("cline_default_workflow.md");
    }

    private String getOrCreateDefaultWorkflowTemplate() {
        try {
            Path documentsDir = Paths.get(System.getProperty("user.home"), "Documents");
            Path nmsDataDir = documentsDir.resolve("nms_support_data");
            ensureDir(nmsDataDir);
            Path templatePath = getDefaultWorkflowTemplatePath();
            if (!Files.exists(templatePath)) {
                String template = generateDefaultWorkflowTemplateWithPlaceholders();
                Files.write(templatePath, template.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            }
            byte[] bytes = Files.readAllBytes(templatePath);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warning("Error ensuring default workflow template: " + e.getMessage());
            // Fallback to built-in template
            return generateDefaultWorkflowTemplateWithPlaceholders();
        }
    }

    private String generateDefaultWorkflowTemplateWithPlaceholders() {
                try {
            java.io.InputStream is = getClass().getResourceAsStream("/templates/cline_default_workflow.md");
            if (is != null) {
                byte[] b = is.readAllBytes();
                return new String(b, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.fine("Packaged workflow template not found, using inline default");
        }
        StringBuilder sb = new StringBuilder();

        sb.append("## Oracle NMS JBot Framework â€“ Project Structure and Workflow\n");
        sb.append("The Oracle NMS JBot framework follows a configuration-driven UI defined via XML and properties files. ");
        sb.append("Project-specific overrides supersede product defaults when present, and properties are merged so project-defined keys override product values while non-overridden keys are inherited.\n\n");

        sb.append("---\n");
        sb.append("## Project Folder\n");
        sb.append("Path: {{PROJECT_FOLDER}}\n\n");

        sb.append("Purpose:\n");
        sb.append("- Holds all project-specific customizations:\n");
        sb.append("  - XML configuration\n");
        sb.append("  - Properties files\n");
        sb.append("  - Java command code for custom commands\n");
        sb.append("  - SQL scripts for project-specific database customizations (under `sql`)\n");
        sb.append("- All edits must be made here (do not modify the product folder or decompiled jars).\n");
        sb.append("- To override a product XML, copy the product XML into the corresponding project path and edit it there. ");
        sb.append("The project copy will be used instead of the product one [3].\n\n");

        sb.append("Analysis scope:\n");
        sb.append("- While edits are confined to the Project Folder, always analyze behavior using inputs from all folders ");
        sb.append("(Project, Product, and Decompiled JARs) to understand effective runtime behavior.\n\n");

        sb.append("---\n");
        sb.append("## Product Folder\n");
        sb.append("Path: {{PRODUCT_JAVA_PATH}}\n\n");

        sb.append("Purpose:\n");
        sb.append("- Contains base product code and default XML/properties shipped with NMS.\n");
        sb.append("- Serves as the reference implementation for all tools and dialogs.\n");
        sb.append("- When a project version of an XML exists in the project folder, the project XML supersedes the product XML [3].\n");
        sb.append("- Product and project properties are merged; project keys override product values while any non-overridden keys ");
        sb.append("remain sourced from the product properties [6].\n\n");

        sb.append("---\n");
        sb.append("## Decompiled JAR\n");
        sb.append("Path: {{DECOMPILED_FOLDER}}\n\n");

        sb.append("Purpose:\n");
        sb.append("- Reference copy of backend Java client/server-side logic for analysis only:\n");
        sb.append("  - XML parsing and command execution flows\n");
        sb.append("  - UI initialization/launch sequences\n");
        sb.append("  - Data source and command implementations\n");
        sb.append("  - If `cesejb.jar` is included, it provides server-side EJB/client interaction references\n");
        sb.append("- Do not modify this code. Use it to:\n");
        sb.append("  - Trace how a dialog loads its XML and data sources\n");
        sb.append("  - Confirm command names, parameters, and error handling\n");
        sb.append("  - Map properties keys to UI widgets and behaviors\n\n");

        sb.append("---\n");
        sb.append("## Merge and Resolution Rules (effective behavior)\n");
        sb.append("- XML override:\n");
        sb.append("  - If a project XML exists, it supersedes the product XML for that component/dialog. ");
        sb.append("Typical workflow is to copy the product XML into the project path and then edit the project copy [3].\n");
        sb.append("- Properties merge:\n");
        sb.append("  - Product and project properties are concatenated into a generated/merged output. ");
        sb.append("Project values override the same keys from product; keys not defined by the project continue to come from product [6].\n");
        sb.append("- Build/install merges:\n");
        sb.append("  - The standard NMS process merges project configuration with product configuration and places the results ");
        sb.append("into the runtime/working area used by the applications [2]. Use this merged output for validation and debugging.\n\n");

        sb.append("---\n");
        sb.append("## Debugging Process\n");
        sb.append("1. Inspect the Project Folder first:\n");
        sb.append("   - For a given dialog or tool, check whether a project XML exists. If it does, that file supersedes the product XML [3].\n");
        sb.append("   - Check project properties for overridden keys affecting labels, queries, behaviors, or feature flags [6].\n");
        sb.append("2. Review the Product Folder for baseline behavior:\n");
        sb.append("   - When there is no project XML, the product XML defines the behavior.\n");
        sb.append("   - Compare product vs. project properties to see which keys the project overrides vs. which are inherited [6].\n");
        sb.append("3. Consult the Decompiled JAR for execution flow:\n");
        sb.append("   - Confirm how the framework locates and loads XML/properties, the data sources used, and command invocation paths.\n");
        sb.append("   - Use class and method names to align stack traces with configuration.\n");
        sb.append("4. Validate the merged runtime output in {{WORKING_DIR}}:\n");
        sb.append("   - Confirm which version of each XML is present (project vs. product).\n");
        sb.append("   - Inspect generated/merged properties to verify the final values taking effect (project overrides present; ");
        sb.append("product defaults retained for non-overridden keys) [6].\n");
        sb.append("5. Search strategy across all folders:\n");
        sb.append("   - Use case-insensitive substring searches and tokenized queries.\n");
        sb.append("   - Correlate dialog names, widget IDs, command IDs, and properties keys across project, product, and decompiled code.\n\n");

        sb.append("---\n");
        sb.append("## Code and Configuration Guidelines\n");
        sb.append("- Edits:\n");
        sb.append("  - Make changes only in the Project Folder.\n");
        sb.append("  - To override product XML, copy it into the project path and edit the project file [3].\n");
        sb.append("  - Add or change only the properties keys you need; rely on product defaults for the rest [6].\n");
        sb.append("- XML structure:\n");
        sb.append("  - Follow the XSD for structure/validation: {{JBOT_XSD_PATH}}\n");
        sb.append("  - Do not infer XML structure from non-XML formats.\n");
        sb.append("- Properties files typically include:\n");
        sb.append("  - Labels, colors, queries, UI mappings, feature flags, and other parameters [6].\n");
        sb.append("- Cross-platform commands:\n");
        sb.append("  - When you share commands or scripts, note the shell (cmd/PowerShell/Bash) and adapt quoting/paths/flags accordingly.\n\n");

        sb.append("---\n");
        sb.append("## Build and Runtime Behavior\n");
        sb.append("- Build/install step:\n");
        sb.append("  - The project configuration is merged with product configuration and installed to the working/runtime area ");
        sb.append("used by clients/servers [2]. Inspect this area to validate the effective configuration.\n");
        sb.append("- Merge specifics:\n");
        sb.append("  - XML: project file supersedes product when present [3].\n");
        sb.append("  - Properties: merged; project values override corresponding product keys; non-overridden keys are inherited from product [6].\n");
        sb.append("- Final runtime source:\n");
        sb.append("  - The application loads configurations from the merged output in:\n");
        sb.append("    - {{WORKING_DIR}}\n\n");

        sb.append("---\n");
        sb.append("## Localization and Properties Resolution Tips\n");
        sb.append("- Properties resolution honors locale-specific files before falling back to more general ones ");
        sb.append("(for example: tool_lang_locale.properties, tool_lang.properties, then tool.properties) [6].\n");
        sb.append("- Keep project properties minimalâ€”override only what you need. Validate final generated properties ");
        sb.append("to confirm your overrides are effective [6].\n\n");

        sb.append("---\n");
        sb.append("## Troubleshooting Checklist\n");
        sb.append("- Which XML is in effect?\n");
        sb.append("  - Look in {{WORKING_DIR}} to confirm whether the project copy exists for the dialog ");
        sb.append("(if present, it supersedes product) [3].\n");
        sb.append("- Are your properties overrides loading?\n");
        sb.append("  - Open the generated/merged properties to confirm your keys are present and overriding as expected [6].\n");
        sb.append("- Unexpected product behavior persisting?\n");
        sb.append("  - Check for missing project XML (product XML still active), or for keys not overridden in project properties [6].\n");
        sb.append("- Dialog/data issues:\n");
        sb.append("  - Trace the data source and command flow in {{DECOMPILED_FOLDER}}; verify the expected data source names ");
        sb.append("and command IDs are defined and referenced.\n");
        sb.append("- Comparing baseline vs. project:\n");
        sb.append("  - Diff the product XML/properties against project versions to isolate meaningful changes ");
        sb.append("(avoid duplicating unchanged product content in the project).\n");
        sb.append("- Introducing a new override:\n");
        sb.append("  - Copy the relevant product XML into the project path, then edit the project copy [3]. ");
        sb.append("Add only required properties keys in the project file(s) [6].\n\n");

        sb.append("---\n");
        sb.append("## AI Model Accuracy Tips\n");
        sb.append("- Provide exact file paths, dialog names, widget IDs, and short code snippets to anchor searches.\n");
        sb.append("- Include OS and shell when asking for commands (cmd/PowerShell/Bash).\n");
        sb.append("- Search across all folders (Project, {{PRODUCT_JAVA_PATH}}, and {{DECOMPILED_FOLDER}}) ");
        sb.append("using case-insensitive, tokenized queries for recall.\n");
        sb.append("- Prefer small, incremental changes with explicit acceptance criteria; share diffs/patches when feasible.\n");
        sb.append("- Include actual error messages, stack traces, and command output to ground fixes.\n");
        sb.append("- When paths vary, provide both forward/backslash forms if helpful.\n\n");

        sb.append("This approach enables precise analysis across project, product, and code references, ");
        sb.append("while keeping all edits confined to the project folder. It leverages NMSâ€™s standard override/merge model: ");
        sb.append("project XML supersedes product XML, and properties files merge with project keys overriding product values [3][6], ");
        sb.append("with merged outputs used by runtime after install/build steps [2].\n");
        
        // ---
        sb.append("---\\n");
        sb.append("## User-added Workspace Folders\\n");
        sb.append("Use this space to document any additional folders you've included in the VS Code workspace beyond Project/Product/Decompiled.\\n");
        sb.append("For each folder, describe its purpose so the AI respects it and doesn't attempt to remove or modify it.\\n\\n");
        sb.append("- Folder path: <path>\\n");
        sb.append("- Purpose: <why it's included>\\n");
        sb.append("- Notes: <read-only? scripts? data? any special instructions>\\n\\n");
        sb.append("Add as many entries as needed.\\n");

        return sb.toString();
    }


    private String fillWorkflowPlaceholders(String template, ProjectEntity project, String projectFolder, String productFolder, String decompiledFolder) {
        String name = (project != null && project.getName() != null) ? project.getName() : "Project";
        String envVar = (project != null && project.getNmsEnvVar() != null && !project.getNmsEnvVar().trim().isEmpty())
                ? project.getNmsEnvVar().trim()
                : "N/A";
        String projPath = (projectFolder != null && !projectFolder.isEmpty()) ? projectFolder : "N/A";
        String prodJavaPath = "N/A";
        if (productFolder != null && !productFolder.isEmpty()) {
            prodJavaPath = productFolder + java.io.File.separator + "java";
        }
        String jarPath = (decompiledFolder != null && !decompiledFolder.isEmpty()) ? decompiledFolder : "N/A";
        String jbotXsd = prodJavaPath.equals("N/A") ? "<Product path>/product/global/jbot.xsd" : prodJavaPath.replace("\\", "/") + "/product/global/jbot.xsd";
        String workingDir = (prodJavaPath.equals("N/A") ? "<Product path>" : prodJavaPath) + "/working";

        // DB details (null/empty safe)
        String dbHost = (project != null && project.getDbHost() != null && !project.getDbHost().trim().isEmpty())
                ? project.getDbHost().trim() : "N/A";
        String dbPort = (project != null && project.getDbPort() > 0)
                ? String.valueOf(project.getDbPort()) : "N/A";
        String dbUser = (project != null && project.getDbUser() != null && !project.getDbUser().trim().isEmpty())
                ? project.getDbUser().trim() : "N/A";
        String dbPassword = (project != null && project.getDbPassword() != null && !project.getDbPassword().trim().isEmpty())
                ? project.getDbPassword().trim() : "N/A";
        String dbSid = (project != null && project.getDbSid() != null && !project.getDbSid().trim().isEmpty())
                ? project.getDbSid().trim() : "N/A";

        // Compose JDBC URL and SQLcl connect string when we have enough pieces
        String oracleJdbcUrl = "N/A";
        String sqlclConnect = "N/A";
        boolean hasDbCore = !"N/A".equals(dbHost) && !"N/A".equals(dbPort) && !"N/A".equals(dbSid);
        if (hasDbCore) {
            boolean looksLikeService = dbSid.contains("/") || dbSid.contains(".") || dbSid.contains(" ");
            if (looksLikeService) {
                oracleJdbcUrl = String.format("jdbc:oracle:thin:@//%s:%s/%s", dbHost, dbPort, dbSid);
            } else {
                oracleJdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", dbHost, dbPort, dbSid);
            }
            if (!"N/A".equals(dbUser) && !"N/A".equals(dbPassword)) {
                if (looksLikeService) {
                    sqlclConnect = String.format("%s/%s@//%s:%s/%s", dbUser, dbPassword, dbHost, dbPort, dbSid);
                } else {
                    sqlclConnect = String.format("%s/%s@%s:%s:%s", dbUser, dbPassword, dbHost, dbPort, dbSid);
                }
            }
        }

        String filled = template;
        filled = filled.replace("{{PROJECT_NAME}}", name);
        filled = filled.replace("{{PROJECT_FOLDER}}", projPath);
        filled = filled.replace("{{PRODUCT_JAVA_PATH}}", prodJavaPath);
        filled = filled.replace("{{DECOMPILED_FOLDER}}", jarPath);
        filled = filled.replace("{{NMS_ENV_VAR}}", envVar);
        filled = filled.replace("{{JBOT_XSD_PATH}}", jbotXsd);
        filled = filled.replace("{{WORKING_DIR}}", workingDir);
        // DB placeholders
        filled = filled.replace("{{DB_HOST}}", dbHost);
        filled = filled.replace("{{DB_PORT}}", dbPort);
        filled = filled.replace("{{DB_SID}}", dbSid);
        filled = filled.replace("{{DB_USER}}", dbUser);
        filled = filled.replace("{{DB_PASSWORD}}", dbPassword);
        filled = filled.replace("{{ORACLE_JDBC_URL}}", oracleJdbcUrl);
        filled = filled.replace("{{SQLCL_CONNECT_STRING}}", sqlclConnect);
        return filled;
    }

    private void editDefaultWorkflowTemplate() {
        try {
            // Ensure template exists and then open it with preferred editor
            getOrCreateDefaultWorkflowTemplate();
            Path templatePath = getDefaultWorkflowTemplatePath();
            boolean opened = false;
            if (!opened) {
                opened = tryOpenWithVSCode(templatePath.toString());
            }
            if (!opened) {
                opened = tryOpenWithNotepadPlusPlus(templatePath.toString());
            }
            if (!opened) {
                opened = tryOpenWithNotepad(templatePath.toString());
            }
            if (!opened) {
                DialogUtil.showError("Cannot Open Template",
                        "Unable to open the default workflow template with any available editor.");
            }
        } catch (Exception e) {
            logger.severe("Error opening default workflow template: " + e.getMessage());
            DialogUtil.showError("Error", "Failed to open default workflow template:\n" + e.getMessage());
        }
    }

    private Optional<String> promptEditLargeText(String title, String header, String initial) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        IconUtils.setStageIcon((Stage) dialog.getDialogPane().getScene().getWindow());
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextArea ta = new TextArea(initial != null ? initial : "");
        ta.setWrapText(true);
        ta.setPrefSize(800, 500);

        VBox box = new VBox(10, ta);
        box.setPadding(new javafx.geometry.Insets(10));
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(btn -> btn == saveBtn ? ta.getText() : null);
        Optional<String> res = dialog.showAndWait();
        return res;
    }

    /**
     * Shows the information dialog with version and shortcuts
     */
    private void showInfoDialog() {
        try {
            Stage parentStage = (Stage) (infoButton != null ? infoButton.getScene().getWindow() : root.getScene().getWindow());
            InfoDialog infoDialog = new InfoDialog();
            infoDialog.showDialog(parentStage);
        } catch (Exception e) {
            logger.severe("Error showing info dialog: " + e.getMessage());
            DialogUtil.showError("Error", "An error occurred while showing information dialog:\n" + e.getMessage());
        }
    }


}
