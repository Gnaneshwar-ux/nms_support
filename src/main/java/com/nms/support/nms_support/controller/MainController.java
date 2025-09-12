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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    @FXML
    public Tab dataStoreTab;
    public Button openVpnButton;
    @FXML
    public Tab projectTab;
    @FXML
    private Parent root;
    @FXML
    private ImageView themeIcon;
    @FXML
    public Button mainSaveButton;

    BuildAutomation buildAutomation;
    private ProjectDetailsController projectDetailsController;
    private DatastoreDumpController datastoreDumpController;
    private ChangeTrackingService changeTrackingService;
    
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


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Only log initialization issues or important configuration details
        if (root != null) {
            root.getStyleClass().add("root");
        } else {
            logger.warning("Root is null");
        }

        String user = System.getProperty("user.name");
        projectManager = new ProjectManager("C:\\Users\\" + user + "\\Documents\\nms_support_data\\projects.json");
        logManager = new LogManager("C:\\Users\\" + user + "\\Documents\\nms_support_data\\logs.json");
        vpnManager = new VpnManager("C:\\Users\\" + user + "\\Documents\\nms_support_data\\vpn.json");

        addButton.setOnAction(event -> addProject());
        delButton.setOnAction(event -> removeProject());
        editProjectButton.setOnAction(event -> editProject());
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
        
        // Setup window close handler and accelerators after scene is available
        Platform.runLater(() -> {
            // Load tab content after scene is available
            loadTabContent();
            setupWindowCloseHandler();
            setupAccelerators();
        });
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
            
            // Phase 1: Immediate UI updates (synchronous)
            setTabState(newValue);
            
            // Phase 2: Load project details for all controllers (synchronous)
            if (projectDetailsController != null) {
                projectDetailsController.loadProjectDetails();
            }
            
            // Phase 3: Handle project ordering and combobox refresh (asynchronous)
            if (newValue != null && !"None".equals(newValue)) {
                Platform.runLater(() -> {
                    try {
                        // Update project ordering
                        projectManager.moveProjectToTop(newValue);
                        projectManager.saveData();
                        
                        // Refresh combobox with new order
                        reloadProjectNamesCB();
                        
                        // Notify other controllers about the project change
                        notifyControllersOfProjectChange(newValue);
                        
                    } catch (Exception e) {
                        logger.severe("Error in project selection handling: " + e.getMessage());
                    }
                });
            } else {
                // For "None" selection, still notify controllers
                notifyControllersOfProjectChange(newValue);
            }
            
        } catch (Exception e) {
            logger.severe("Error in project selection listener: " + e.getMessage());
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
                        } else if (result) {
                            // User chose to save, perform save and allow close
                            logger.info("User chose to save before closing");
                            performGlobalSave();
                        } else {
                            // User chose to discard changes, allow close
                            logger.info("User chose to discard changes before closing");
                        }
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
            // Disable buttons when no project is selected
            delButton.setDisable(true);
            editProjectButton.setDisable(true);
            mainSaveButton.setDisable(true);
        }
        else{
            buildTab.setDisable(false);
            dataStoreTab.setDisable(false);
            projectTab.setDisable(false);
            // Enable buttons when a project is selected
            delButton.setDisable(false);
            editProjectButton.setDisable(false);
            mainSaveButton.setDisable(false);
        }
    }

    private void loadTabContent() {
        try {
            // Load build automation tab
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

            // Load datastore dump tab
            FXMLLoader dataStoreLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/datastore-dump.fxml"));
            if (dataStoreLoader.getLocation() == null) {
                logger.severe("Datastore dump FXML resource not found");
                return;
            }
            Parent dataStoreContent = dataStoreLoader.load();
            datastoreDumpController = dataStoreLoader.getController();
            if (datastoreDumpController != null) {
                datastoreDumpController.setMainController(this);
                dataStoreTab.setContent(dataStoreContent);
            } else {
                logger.severe("Datastore dump controller is null");
            }

            // Load project details tab
            FXMLLoader projectLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/project-details.fxml"));
            if (projectLoader.getLocation() == null) {
                logger.severe("Project details FXML resource not found");
                return;
            }
            Parent projectContent = projectLoader.load();
            projectDetailsController = projectLoader.getController();
            if (projectDetailsController != null) {
                projectDetailsController.setMainController(this);
                projectTab.setContent(projectContent);
            } else {
                logger.severe("Project details controller is null");
            }

            // Add tab selection listeners to refresh data when tabs are selected
            setupTabSelectionListeners();

            logger.info("Tab content loaded successfully.");
        } catch (IOException e) {
            LoggerUtil.error(e);
            logger.severe("Error loading tab content: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Unexpected error loading tab content: " + e.getMessage());
            LoggerUtil.error(e);
        }
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
                    
                    // Refresh data based on which tab was selected
                    if (newTab == buildTab && buildAutomation != null) {
                        buildAutomation.onTabSelected();
                    } else if (newTab == projectTab && projectDetailsController != null) {
                        projectDetailsController.loadProjectDetails();
                    } else if (newTab == dataStoreTab && datastoreDumpController != null) {
                        datastoreDumpController.onTabSelected();
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
                    try {
                        // Update the project name in the project manager
                        ProjectEntity project = projectManager.getProject(selectedProjectName);
                        if (project != null) {
                            project.setName(newProjectName);
                            projectManager.saveData();
                            
                            // Refresh the ComboBox and select the updated project
                            reloadProjectNamesCB();
                            projectComboBox.setValue(newProjectName);
                            
                            logger.info("Successfully updated project name from '" + selectedProjectName + "' to '" + newProjectName + "'");
                        } else {
                            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Project not found.");
                        }
                    } catch (Exception e) {
                        logger.severe("Error updating project name: " + e.getMessage());
                        DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to update project name: " + e.getMessage());
                    }
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




}

