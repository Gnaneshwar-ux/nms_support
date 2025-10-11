package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.service.globalPack.JarDecompilerService;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
// Using TextArea instead of RichTextFX for now

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Controller for the Jar Decompiler tab
 */
public class JarDecompilerController implements Initializable {
    
    private static final Logger logger = LoggerUtil.getLogger();
    
    // FXML Controls
    @FXML private TextField jarPathField;
    @FXML private Button browseButton;
    @FXML private Button decompileButton;
    @FXML private Button openVsButton;
    @FXML private TextField searchField;
    @FXML private ListView<String> jarListView;
    @FXML private ListView<String> classListView;
    @FXML private TextArea codeArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    
    // Data
    private ObservableList<String> jarFiles = FXCollections.observableArrayList();
    private ObservableList<String> classFiles = FXCollections.observableArrayList();
    private Map<String, Boolean> jarSelectionMap = new HashMap<>();
    private String currentJarPath = "";
    private String currentProjectName = "";
    private JarDecompilerService decompilerService;
    
    // Main controller reference
    private MainController mainController;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing Jar Decompiler Controller");
        
        // Initialize UI components
        initializeUI();
        setupEventHandlers();
        setupCodeArea();
        
        // Initialize service
        decompilerService = new JarDecompilerService();
        setupServiceHandlers();
    }
    
    /**
     * Set the main controller reference
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Initialize UI components
     */
    private void initializeUI() {
        // Setup jar list view with checkboxes
        jarListView.setCellFactory(CheckBoxListCell.forListView(item -> {
            if (!jarSelectionMap.containsKey(item)) {
                // Default selection for nms_*.jar files
                boolean selected = item.startsWith("nms_") && item.endsWith(".jar");
                jarSelectionMap.put(item, selected);
            }
            // Create a simple property for the checkbox
            javafx.beans.property.BooleanProperty property = new javafx.beans.property.SimpleBooleanProperty(jarSelectionMap.get(item));
            property.addListener((obs, oldVal, newVal) -> {
                jarSelectionMap.put(item, newVal);
                updateDecompileButtonState();
            });
            return property;
        }));
        
        jarListView.setItems(jarFiles);
        
        // Setup class list view
        classListView.setItems(classFiles);
        
        // Setup search field
        searchField.setPromptText("Search classes...");
        
        // Setup buttons
        decompileButton.setDisable(true);
        openVsButton.setDisable(true);
        
        // Setup progress bar
        progressBar.setVisible(false);
        
        // Setup status label
        statusLabel.setText("Ready");
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Browse button
        browseButton.setOnAction(event -> browseForJarDirectory());
        
        // Decompile button
        decompileButton.setOnAction(event -> startDecompilation());
        
        // Open VS button
        openVsButton.setOnAction(event -> openInVSCode());
        
        // Search field
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterClasses());
        
        // Jar list selection
        jarListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadClassesForJar(newValue);
            }
        });
        
        // Class list selection
        classListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadDecompiledCode(newValue);
            }
        });
        
        // Jar list checkbox changes
        jarListView.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<String>) change -> {
            updateDecompileButtonState();
        });
    }
    
    /**
     * Setup code area
     */
    private void setupCodeArea() {
        codeArea.setEditable(false);
        codeArea.setWrapText(true);
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace;");
    }
    
    /**
     * Browse for JAR directory
     */
    private void browseForJarDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select JAR Directory");
        
        // Set initial directory if jarPathField has content
        if (!jarPathField.getText().isEmpty()) {
            File initialDir = new File(jarPathField.getText());
            if (initialDir.exists() && initialDir.isDirectory()) {
                directoryChooser.setInitialDirectory(initialDir);
            }
        }
        
        Stage stage = (Stage) browseButton.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);
        
        if (selectedDirectory != null) {
            jarPathField.setText(selectedDirectory.getAbsolutePath());
            loadJarFiles(selectedDirectory.getAbsolutePath());
        }
    }
    
    /**
     * Load JAR files from the specified directory
     */
    private void loadJarFiles(String directoryPath) {
        try {
            currentJarPath = directoryPath;
            List<String> jars = JarDecompilerService.getJarFiles(directoryPath);
            
            jarFiles.clear();
            jarSelectionMap.clear();
            
            for (String jar : jars) {
                jarFiles.add(jar);
                // Default selection for nms_*.jar files
                boolean selected = jar.startsWith("nms_") && jar.endsWith(".jar");
                jarSelectionMap.put(jar, selected);
            }
            
            // Refresh the list view to show checkboxes
            jarListView.refresh();
            
            updateDecompileButtonState();
            statusLabel.setText("Found " + jars.size() + " JAR files");
            
            logger.info("Loaded " + jars.size() + " JAR files from: " + directoryPath);
            
        } catch (Exception e) {
            logger.severe("Error loading JAR files: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load JAR files: " + e.getMessage());
        }
    }
    
    /**
     * Load classes for the selected JAR
     */
    private void loadClassesForJar(String jarName) {
        try {
            List<String> classes = JarDecompilerService.getClassFilesInJar(currentJarPath, jarName);
            classFiles.clear();
            classFiles.addAll(classes);
            
            // Clear code area
            codeArea.clear();
            
            statusLabel.setText("Found " + classes.size() + " classes in " + jarName);
            logger.info("Loaded " + classes.size() + " classes from JAR: " + jarName);
            
        } catch (Exception e) {
            logger.severe("Error loading classes from JAR " + jarName + ": " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load classes: " + e.getMessage());
        }
    }
    
    /**
     * Load decompiled code for the selected class
     */
    private void loadDecompiledCode(String className) {
        String selectedJar = jarListView.getSelectionModel().getSelectedItem();
        if (selectedJar == null) {
            return;
        }
        
        try {
            // Check if decompiled files exist
            String extractedPath = getExtractedPath();
            if (extractedPath == null || !new File(extractedPath).exists()) {
                codeArea.clear();
                codeArea.appendText("// Please decompile the JAR first to view the source code");
                return;
            }
            
            String code = JarDecompilerService.getDecompiledCode(extractedPath, selectedJar, className);
            codeArea.clear();
            codeArea.appendText(code);
            
            statusLabel.setText("Loaded decompiled code for: " + className);
            logger.info("Loaded decompiled code for class: " + className);
            
        } catch (Exception e) {
            logger.severe("Error loading decompiled code for " + className + ": " + e.getMessage());
            codeArea.clear();
            codeArea.appendText("// Error loading decompiled code: " + e.getMessage());
        }
    }
    
    /**
     * Filter classes based on search text
     */
    private void filterClasses() {
        String searchText = searchField.getText().toLowerCase();
        if (searchText.isEmpty()) {
            // Show all classes
            classFiles.clear();
            String selectedJar = jarListView.getSelectionModel().getSelectedItem();
            if (selectedJar != null) {
                List<String> allClasses = JarDecompilerService.getClassFilesInJar(currentJarPath, selectedJar);
                classFiles.addAll(allClasses);
            }
        } else {
            // Filter classes
            List<String> filteredClasses = new ArrayList<>();
            String selectedJar = jarListView.getSelectionModel().getSelectedItem();
            if (selectedJar != null) {
                List<String> allClasses = JarDecompilerService.getClassFilesInJar(currentJarPath, selectedJar);
                for (String className : allClasses) {
                    if (className.toLowerCase().contains(searchText)) {
                        filteredClasses.add(className);
                    }
                }
            }
            classFiles.clear();
            classFiles.addAll(filteredClasses);
        }
    }
    
    /**
     * Start decompilation process
     */
    private void startDecompilation() {
        if (currentJarPath.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Directory", "Please select a JAR directory first.");
            return;
        }
        
        // Get selected JARs
        List<String> selectedJars = new ArrayList<>();
        for (String jar : jarFiles) {
            if (jarSelectionMap.getOrDefault(jar, false)) {
                selectedJars.add(jar);
            }
        }
        
        if (selectedJars.isEmpty()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Selection", "Please select at least one JAR file to decompile.");
            return;
        }
        
        // Get project name
        if (mainController != null && mainController.getSelectedProject() != null) {
            currentProjectName = mainController.getSelectedProject().getName();
        } else {
            // Prompt for project name
            TextInputDialog dialog = new TextInputDialog("default_project");
            dialog.setTitle("Project Name");
            dialog.setHeaderText("Enter project name for decompiled files:");
            dialog.setContentText("Project name:");
            
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                currentProjectName = result.get().trim();
            } else {
                return;
            }
        }
        
        // Start decompilation
        decompilerService.setDecompilationParams(currentJarPath, currentProjectName, selectedJars);
        decompilerService.restart();
    }
    
    /**
     * Open extracted folder in VS Code
     */
    private void openInVSCode() {
        String extractedPath = getExtractedPath();
        if (extractedPath == null || !new File(extractedPath).exists()) {
            DialogUtil.showAlert(Alert.AlertType.WARNING, "No Extracted Files", "Please decompile JARs first.");
            return;
        }
        
        try {
            JarDecompilerService.openInVSCode(extractedPath);
            statusLabel.setText("Opened VS Code with extracted files");
        } catch (Exception e) {
            logger.severe("Error opening VS Code: " + e.getMessage());
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to open VS Code: " + e.getMessage());
        }
    }
    
    /**
     * Setup service event handlers
     */
    private void setupServiceHandlers() {
        decompilerService.setOnRunning(event -> {
            Platform.runLater(() -> {
                progressBar.setVisible(true);
                decompileButton.setDisable(true);
                statusLabel.setText("Decompiling JARs...");
            });
        });
        
        decompilerService.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                decompileButton.setDisable(false);
                openVsButton.setDisable(false);
                statusLabel.setText("Decompilation completed successfully");
                DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Success", "JARs decompiled successfully!");
            });
        });
        
        decompilerService.setOnFailed(event -> {
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                decompileButton.setDisable(false);
                statusLabel.setText("Decompilation failed");
                DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Decompilation failed: " + 
                    decompilerService.getException().getMessage());
            });
        });
        
        decompilerService.progressProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                progressBar.setProgress(newValue.doubleValue());
            });
        });
    }
    
    /**
     * Update decompile button state based on selection
     */
    private void updateDecompileButtonState() {
        boolean hasSelection = jarSelectionMap.values().stream().anyMatch(selected -> selected);
        decompileButton.setDisable(!hasSelection || currentJarPath.isEmpty());
    }
    
    /**
     * Get extracted path for current project
     */
    private String getExtractedPath() {
        if (currentProjectName.isEmpty()) {
            return null;
        }
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "Documents", "nms_support_data", "extracted", currentProjectName).toString();
    }
    
    // Note: Syntax highlighting removed for simplicity - using basic TextArea
    
    /**
     * Called when the tab is selected
     */
    public void onTabSelected() {
        // Refresh project name if main controller is available
        if (mainController != null && mainController.getSelectedProject() != null) {
            currentProjectName = mainController.getSelectedProject().getName();
        }
    }
}
