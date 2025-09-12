package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import oracle.jdbc.proxy._Proxy_;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Professional process selection dialog with modern UI and enhanced functionality
 */
public class ProcessSelectionDialog {
    
    private Stage dialogStage;
    private TableView<Map<String, String>> processTable;
    private Button selectButton;
    private Button cancelButton;
    private CheckBox filterLocalCheckBox;
    private CheckBox filterJnlpCheckBox;
    private List<Map<String, String>> selectedProcesses;
    private VBox searchSection;
    private ProjectEntity project;
    
         // Professional typography constants (following existing patterns)
     private static final String PROFESSIONAL_FONT_FAMILY = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
     private static final String MODERN_STYLE = String.format(
         "-fx-font-family: %s; -fx-font-size: 12px; -fx-background-color: #FFFFFF;",
         PROFESSIONAL_FONT_FAMILY
     );


    // Dialog purpose types
    public enum DialogPurpose {
        STOP_PROCESS("Stop Process", "Select the process(es) you want to stop:"),
        VIEW_LOG("View Log", "Select the process log you want to view:"),
        RESTART_PROCESS("Restart Process", "Select the process(es) you want to restart:"),
        GENERAL("Select Process", "Select from the list below:");
        
        private final String title;
        private final String description;
        
        DialogPurpose(String title, String description) {
            this.title = title;
            this.description = description;
        }
        
        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
    
    private DialogPurpose purpose;
    private List<Map<String, String>> processes;
    
    public ProcessSelectionDialog(ProjectEntity project, DialogPurpose purpose, List<Map<String, String>> processes) {
        this.purpose = purpose;
        this.processes = processes;
        this.project = project;
        createDialog();
    }
    
    private void createDialog() {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(true);
        dialogStage.setTitle(purpose.getTitle());
                 dialogStage.setMinWidth(580);
         dialogStage.setMinHeight(400);
         dialogStage.setWidth(620);
         dialogStage.setHeight(450);
        
        // Set app icon
        IconUtils.setStageIcon(dialogStage);
        
                 // Main container
         VBox mainContainer = new VBox(6);
         mainContainer.setPadding(new Insets(10));
         mainContainer.setAlignment(Pos.TOP_CENTER);
         mainContainer.setStyle(MODERN_STYLE + "-fx-background-radius: 8;");
         mainContainer.setEffect(new javafx.scene.effect.DropShadow(10, Color.rgb(0, 0, 0, 0.1)));
        
        // Header
        VBox header = createHeader();
        
        // Search and filter section
        searchSection = createSearchSection();
        
        // Process table
        VBox tableSection = createTableSection();
        
        
        
                 // Footer buttons
         HBox footer = createFooter();
         
         mainContainer.getChildren().addAll(header, searchSection, tableSection, footer);
        
        Scene scene = new Scene(mainContainer);
        scene.getRoot().setStyle(MODERN_STYLE);
        dialogStage.setScene(scene);
        
                 // Handle close button (X) click
         dialogStage.setOnCloseRequest(event -> {
             selectedProcesses = null;
             dialogStage.close();
         });
        
        // Initialize data
        initializeData();
    }
    
         private VBox createHeader() {
         VBox header = new VBox(4);
         header.setAlignment(Pos.CENTER_LEFT);
         
         HBox titleRow = new HBox(6);
         titleRow.setAlignment(Pos.CENTER_LEFT);
        
        // Icon based on purpose
        String icon = switch (purpose) {
            case STOP_PROCESS -> "⏹";
            case VIEW_LOG -> "📋";
            case RESTART_PROCESS -> "🔄";
            case GENERAL -> "⚙";
        };
        
                 Label iconLabel = new Label(icon);
         iconLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #3B82F6;");
         
         Label titleLabel = new Label(purpose.getTitle());
         titleLabel.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1F2937;",
             PROFESSIONAL_FONT_FAMILY
         ));
        
        titleRow.getChildren().addAll(iconLabel, titleLabel);
        
                 Label descriptionLabel = new Label(purpose.getDescription());
         descriptionLabel.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #6B7280;",
             PROFESSIONAL_FONT_FAMILY
         ));
        
        header.getChildren().addAll(titleRow, descriptionLabel);
        return header;
    }
    
              private VBox createSearchSection() {
         VBox searchSection = new VBox(6);
         
         HBox filterRow = new HBox(12);
         filterRow.setAlignment(Pos.CENTER_LEFT);
         
         filterLocalCheckBox = new CheckBox("Only Local");
         filterLocalCheckBox.setSelected(true);
         filterLocalCheckBox.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #374151; -fx-font-weight: 500;",
             PROFESSIONAL_FONT_FAMILY
         ));
         
         filterJnlpCheckBox = new CheckBox("Only JNLP");
         filterJnlpCheckBox.setSelected(false);
         filterJnlpCheckBox.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #374151; -fx-font-weight: 500;",
             PROFESSIONAL_FONT_FAMILY
         ));


         
         Label countLabel = new Label();
         countLabel.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 11px; -fx-text-fill: #6B7280; -fx-font-weight: 600;",
             PROFESSIONAL_FONT_FAMILY
         ));

         filterRow.getChildren().addAll(filterLocalCheckBox, filterJnlpCheckBox, countLabel);
         
         // Store reference to count label for updates
         searchSection.setUserData(countLabel);
        
         searchSection.getChildren().addAll(filterRow);
         
         return searchSection;
     }
    
         private VBox createTableSection() {
         VBox tableSection = new VBox(6);
        
                 // Create table
         processTable = new TableView<>();
         processTable.setEditable(false);
         processTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
         processTable.setPlaceholder(new Label("No processes found"));
         processTable.setStyle("");
        
                 // Create columns
         TableColumn<Map<String, String>, String> serialCol = new TableColumn<>("#");
         serialCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(
             String.valueOf(processTable.getItems().indexOf(data.getValue()) + 1)));
         serialCol.setPrefWidth(35);
         serialCol.setStyle("-fx-alignment: CENTER;");
         
         TableColumn<Map<String, String>, String> processCol = new TableColumn<>("Process Type");
         processCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("LAUNCHER")));
         processCol.setPrefWidth(100);

         TableColumn<Map<String, String>, String> projectCol = new TableColumn<>("Project ID");
             projectCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("PROJECT_KEY")));
             projectCol.setPrefWidth(200);
         
         TableColumn<Map<String, String>, String> timeCol = new TableColumn<>("Modified Time");
         timeCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("TIME")));
         timeCol.setPrefWidth(130);
         
         TableColumn<Map<String, String>, String> pidCol = new TableColumn<>("Process ID");
         pidCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("PID")));
         pidCol.setPrefWidth(80);
         pidCol.setStyle("-fx-alignment: CENTER;");
         
         TableColumn<Map<String, String>, String> userCol = new TableColumn<>("User");
         userCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("USER")));
         userCol.setPrefWidth(80);
        
                 // Apply basic column styling
         for (TableColumn<Map<String, String>, ?> col : Arrays.asList(serialCol, processCol, timeCol, pidCol, userCol)) {
             col.setStyle("");
         }
        
                 processTable.getColumns().addAll(serialCol, projectCol, processCol, timeCol, pidCol, userCol);
         

         

         
         // Don't make table fill available space - use configured column widths
         VBox.setVgrow(processTable, Priority.NEVER);
        
        tableSection.getChildren().add(processTable);
        return tableSection;
    }
    
         private HBox createFooter() {
         HBox footer = new HBox(12);
         footer.setAlignment(Pos.CENTER_RIGHT);
         footer.setPadding(new Insets(8, 0, 0, 0));
        
        cancelButton = new Button("Cancel");
                 cancelButton.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-color: #F3F4F6; -fx-text-fill: #374151; -fx-border-color: #D1D5DB; -fx-border-radius: 6; -fx-padding: 8 16;",
             PROFESSIONAL_FONT_FAMILY
         ));
         
         selectButton = new Button("Select");
         selectButton.setStyle(String.format(
             "-fx-font-family: %s; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-color: #3B82F6; -fx-text-fill: #FFFFFF; -fx-border-radius: 6; -fx-padding: 8 16;",
             PROFESSIONAL_FONT_FAMILY
         ));
        
        footer.getChildren().addAll(cancelButton, selectButton);
        
        return footer;
    }
    
    private void initializeData() {
        // Create observable list and filtered list
        ObservableList<Map<String, String>> observableProcesses = FXCollections.observableArrayList(processes);
        FilteredList<Map<String, String>> filteredProcesses = new FilteredList<>(observableProcesses, p -> true);
        
        processTable.setItems(filteredProcesses);
        
        
        
                 // Set up filter functionality with mutual exclusivity
         filterLocalCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
             if (newValue && filterJnlpCheckBox.isSelected()) {
                 // If EXE is checked and JNLP is also checked, uncheck JNLP
                 filterJnlpCheckBox.setSelected(false);
             }
             updateFilter();
         });
         
         // Set up JNLP filter functionality with mutual exclusivity
         filterJnlpCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
             if (newValue && filterLocalCheckBox.isSelected()) {
                 // If JNLP is checked and EXE is also checked, uncheck EXE
                 filterLocalCheckBox.setSelected(false);
             }
             updateFilter();
         });

         // Set up button actions
         selectButton.setOnAction(e -> {
             selectedProcesses = new ArrayList<>(processTable.getSelectionModel().getSelectedItems());
             dialogStage.close();
         });
         
         cancelButton.setOnAction(e -> {
             selectedProcesses = null;
             dialogStage.close();
         });
        
                 // Initial filter application - show EXE processes by default
         updateFilter();
        
        // Set up table selection listener
        processTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            updateStatus();
        });
    }
    
         private void updateStatus() {
         int totalItems = processTable.getItems().size();
         int selectedItems = processTable.getSelectionModel().getSelectedItems().size();
         
         // Update count label in search section
         if (searchSection != null && searchSection.getUserData() instanceof Label) {
             Label countLabel = (Label) searchSection.getUserData();
             countLabel.setText(String.format("Total: %d", totalItems));
         }
        
         // Enable/disable select button based on selection
         selectButton.setDisable(selectedItems == 0);
     }
     
     /**
      * Get the selected processes
      */
     public List<Map<String, String>> getSelectedProcesses() {
         return selectedProcesses;
     }
     
     /**
      * Update the filter based on current search text and filter checkboxes
      */
     private void updateFilter() {
         if (processTable.getItems() instanceof FilteredList) {
             FilteredList<Map<String, String>> filteredProcesses = (FilteredList<Map<String, String>>) processTable.getItems();
             
             filteredProcesses.setPredicate(process -> {
                 // Check EXE filter
                 if (filterLocalCheckBox.isSelected() && !"EXE".equalsIgnoreCase(process.get("LAUNCHER"))) {
                     return false;
                 }
                 
                 // Check JNLP filter
                 if (filterJnlpCheckBox.isSelected() && !"JNLP".equalsIgnoreCase(process.get("LAUNCHER"))) {
                     return false;
                 }

                 return true;
             });
             updateStatus();
         }
     }
    

    
    /**
     * Static convenience method for backward compatibility
     */
    public static List<Map<String, String>> selectProcess(ProjectEntity project, List<Map<String, String>> processes) {
        return selectProcess(project, processes, DialogPurpose.GENERAL, null);
    }
    
    /**
     * Static convenience method with purpose specification
     */
    public static List<Map<String, String>> selectProcess(ProjectEntity project, List<Map<String, String>> processes, DialogPurpose purpose, Stage ownerStage) {
        try {
            if (processes == null || processes.isEmpty()) {
                return null;
            }
            
            // Create the custom dialog with all features
            ProcessSelectionDialog dialog = new ProcessSelectionDialog(project, purpose, processes);
            
            // Show the dialog synchronously
            if (ownerStage != null) {
                dialog.dialogStage.initOwner(ownerStage);
            }
            
            dialog.dialogStage.showAndWait();
            
            // Return the result from the dialog
            return dialog.getSelectedProcesses();
            
        } catch (Exception e) {
            System.err.println("Dialog failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
