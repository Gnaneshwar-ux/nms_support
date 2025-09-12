package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.userdata.ProjectManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Dialog for importing project configuration data from another project
 */
public class ProjectImportDialog {
    private static final Logger logger = LoggerUtil.getLogger();
    
    private Stage dialogStage;
    private ProjectManager projectManager;
    private String currentProjectName;
    private CompletableFuture<Optional<ProjectEntity>> resultFuture;
    
    // UI components
    private VBox rootContainer;
    private Label titleLabel;
    private Label descriptionLabel;
    private ListView<ProjectEntity> projectListView;
    private Button importButton;
    private Button cancelButton;
    
    public ProjectImportDialog() {
        // Default constructor
    }
    
    /**
     * Shows the project import dialog and returns a CompletableFuture with the selected project
     * @param parentWindow The parent window for the dialog
     * @param projectManager The project manager instance
     * @param currentProjectName The name of the current project (to filter out)
     * @param cardType The type of card being imported (for display purposes)
     * @return CompletableFuture containing the selected project or empty if cancelled
     */
    public CompletableFuture<Optional<ProjectEntity>> showDialog(Window parentWindow, 
                                                               ProjectManager projectManager, 
                                                               String currentProjectName,
                                                               String cardType) {
        this.projectManager = projectManager;
        this.currentProjectName = currentProjectName;
        this.resultFuture = new CompletableFuture<>();
        
        try {
            // Create the dialog content programmatically
            VBox root = createDialogContent(cardType);
            
            // Create and configure the dialog stage
            dialogStage = new Stage();
            dialogStage.initOwner(parentWindow);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Import " + cardType + " Configuration");
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            dialogStage.setMinWidth(400);
            dialogStage.setMinHeight(320);
            dialogStage.setMaxHeight(400);
            
            // Set the standard dialog icon
            IconUtils.setStageIcon(dialogStage);
            
                    // Set up button actions and hover effects
        setupButtonActions();
        setupButtonHoverEffects();
            
            // Show the dialog
            dialogStage.showAndWait();
            
        } catch (Exception e) {
            logger.severe("Error creating project import dialog: " + e.getMessage());
            resultFuture.complete(Optional.empty());
        }
        
        return resultFuture;
    }
    
    /**
     * Creates the dialog content programmatically
     */
    private VBox createDialogContent(String cardType) {
        // Create main container with professional styling
        VBox rootContainer = new VBox(16);
        rootContainer.setPadding(new javafx.geometry.Insets(24));
        rootContainer.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 12; " +
            "-fx-background-radius: 12; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 10, 0, 0, 4);"
        );
        
        // Create title and description with professional styling
        titleLabel = new Label("Import " + cardType + " Configuration");
        titleLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #1e293b;");
        
        descriptionLabel = new Label("Select a project to import " + cardType.toLowerCase() + " configuration from:");
        descriptionLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 13));
        descriptionLabel.setStyle("-fx-text-fill: #64748b;");
        
        VBox headerBox = new VBox(8);
        headerBox.getChildren().addAll(titleLabel, descriptionLabel);
        
        // Create project list section
        Label listLabel = new Label("Available Projects:");
        listLabel.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.SEMI_BOLD, 14));
        listLabel.setStyle("-fx-text-fill: #374151;");
        
        projectListView = new ListView<>();
        projectListView.setPrefHeight(140); // Approximately 6 rows
        projectListView.setMinHeight(140);
        projectListView.setMaxHeight(140);
        projectListView.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
        
        VBox listBox = new VBox(8);
        listBox.getChildren().addAll(listLabel, projectListView);
        
        // Create buttons with professional styling matching other dialogs
        cancelButton = new Button("Cancel");
        cancelButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.NORMAL, 14));
        cancelButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-border-color: #cbd5e1; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: #64748b; " +
            "-fx-padding: 8 16; " +
            "-fx-min-width: 80; " +
            "-fx-min-height: 36; " +
            "-fx-cursor: hand;"
        );
        
        importButton = new Button("Import");
        importButton.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 14));
        importButton.setStyle(
            "-fx-background-color: #1565c0; " +
            "-fx-border-color: #1565c0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 8 20; " +
            "-fx-min-width: 80; " +
            "-fx-min-height: 36; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.3), 4, 0, 0, 2);"
        );
        importButton.setDisable(true); // Initially disabled
        
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttonBox.setPadding(new javafx.geometry.Insets(16, 0, 8, 0));
        buttonBox.setMinHeight(50);
        buttonBox.setPrefHeight(50);
        buttonBox.getChildren().addAll(cancelButton, importButton);
        
        // Add all components to root
        rootContainer.getChildren().addAll(headerBox, listBox, buttonBox);
        
        // Set up the content
        setupDialogContent(cardType);
        
        return rootContainer;
    }
    
    /**
     * Sets up the dialog content with project list and labels
     */
    private void setupDialogContent(String cardType) {
        try {
            // Get all projects and filter out the current project
            List<ProjectEntity> allProjects = projectManager.getProjects();
            if (allProjects == null) {
                allProjects = new java.util.ArrayList<>();
            }
            
            List<ProjectEntity> availableProjects = allProjects.stream()
                    .filter(project -> project != null && !project.getName().equals(currentProjectName))
                    .collect(Collectors.toList());
            
            // Check if there are any available projects
            if (availableProjects.isEmpty()) {
                // Show warning message and disable import button
                descriptionLabel.setText("No other projects available to import from.");
                importButton.setDisable(true);
                importButton.setText("No Projects");
                
                // Set empty list and disable mouse events to prevent selection exceptions
                projectListView.setItems(FXCollections.observableArrayList());
                projectListView.setDisable(true);
                projectListView.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #d1d5db; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-opacity: 0.6;");
                return;
            }
            
            // Set up the list view
            ObservableList<ProjectEntity> projectList = FXCollections.observableArrayList(availableProjects);
            projectListView.setItems(projectList);
            projectListView.setCellFactory(_ -> new ProjectListCell());
            
            // Ensure ListView is enabled and has proper styling when it has items
            projectListView.setDisable(false);
            projectListView.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
            
            // Enable/disable import button based on selection
            projectListView.getSelectionModel().selectedItemProperty().addListener((_, _, newVal) -> {
                importButton.setDisable(newVal == null);
            });
            
            // Initially disable import button
            importButton.setDisable(true);
            
            // Set up double-click to import with safety checks
            projectListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !importButton.isDisable() && 
                    projectListView.getItems() != null && !projectListView.getItems().isEmpty()) {
                    importSelectedProject();
                }
            });
            
        } catch (Exception e) {
            logger.severe("Error setting up dialog content: " + e.getMessage());
            descriptionLabel.setText("Error loading projects: " + e.getMessage());
            importButton.setDisable(true);
            importButton.setText("Error");
        }
    }
    
    /**
     * Sets up button actions
     */
    private void setupButtonActions() {
        importButton.setOnAction(_ -> importSelectedProject());
        cancelButton.setOnAction(_ -> cancelDialog());
        
        // Handle escape key
        dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                cancelDialog();
            }
        });
    }
    
    /**
     * Sets up professional hover effects for buttons
     */
    private void setupButtonHoverEffects() {
        // Cancel button hover effects
        cancelButton.setOnMouseEntered(_ -> {
            if (!cancelButton.isDisabled()) {
                cancelButton.setStyle(
                    "-fx-background-color: #f1f5f9; " +
                    "-fx-border-color: #94a3b8; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 8; " +
                    "-fx-background-radius: 8; " +
                    "-fx-text-fill: #475569; " +
                    "-fx-padding: 8 16; " +
                    "-fx-min-width: 80; " +
                    "-fx-min-height: 36; " +
                    "-fx-cursor: hand;"
                );
            }
        });
        
        cancelButton.setOnMouseExited(_ -> {
            if (!cancelButton.isDisabled()) {
                cancelButton.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-border-color: #cbd5e1; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 8; " +
                    "-fx-background-radius: 8; " +
                    "-fx-text-fill: #64748b; " +
                    "-fx-padding: 8 16; " +
                    "-fx-min-width: 80; " +
                    "-fx-min-height: 36; " +
                    "-fx-cursor: hand;"
                );
            }
        });
        
        // Import button hover effects
        importButton.setOnMouseEntered(_ -> {
            if (!importButton.isDisabled()) {
                importButton.setStyle(
                    "-fx-background-color: #0d47a1; " +
                    "-fx-border-color: #0d47a1; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 8; " +
                    "-fx-background-radius: 8; " +
                    "-fx-text-fill: white; " +
                    "-fx-padding: 8 20; " +
                    "-fx-min-width: 80; " +
                    "-fx-min-height: 36; " +
                    "-fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(13, 71, 161, 0.4), 6, 0, 0, 3);"
                );
            }
        });
        
        importButton.setOnMouseExited(_ -> {
            if (!importButton.isDisabled()) {
                importButton.setStyle(
                    "-fx-background-color: #1565c0; " +
                    "-fx-border-color: #1565c0; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 8; " +
                    "-fx-background-radius: 8; " +
                    "-fx-text-fill: white; " +
                    "-fx-padding: 8 20; " +
                    "-fx-min-width: 80; " +
                    "-fx-min-height: 36; " +
                    "-fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.3), 4, 0, 0, 2);"
                );
            }
        });
    }
    
    /**
     * Imports the selected project and closes the dialog
     */
    private void importSelectedProject() {
        ProjectEntity selectedProject = projectListView.getSelectionModel().getSelectedItem();
        if (selectedProject != null) {
            logger.info("Importing configuration from project: " + selectedProject.getName());
            resultFuture.complete(Optional.of(selectedProject));
            dialogStage.close();
        }
    }
    
    /**
     * Cancels the dialog without importing
     */
    private void cancelDialog() {
        logger.info("Project import dialog cancelled");
        resultFuture.complete(Optional.empty());
        dialogStage.close();
    }
    
    /**
     * Custom list cell for displaying project information
     */
    private static class ProjectListCell extends ListCell<ProjectEntity> {
        @Override
        protected void updateItem(ProjectEntity project, boolean empty) {
            super.updateItem(project, empty);
            
            if (empty || project == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(project.getName());
                setGraphic(new FontIcon("fa-folder"));
            }
        }
    }
}
