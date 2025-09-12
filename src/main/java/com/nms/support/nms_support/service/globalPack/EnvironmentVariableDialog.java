package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Professional environment variable selection dialog with search functionality
 */
public class EnvironmentVariableDialog {
    
    private Stage dialogStage;
    private TextField searchField;
    private TableView<EnvVarItem> envVarTable;
    private Label statusLabel;
    private Button selectButton;
    private Button cancelButton;
    private CompletableFuture<Optional<String>> resultFuture;
    
    // Professional typography constants (following existing patterns)
    private static final String PROFESSIONAL_FONT_FAMILY = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
    private static final String MODERN_STYLE = String.format(
        "-fx-font-family: %s; -fx-font-size: 13px; -fx-background-color: #FFFFFF;",
        PROFESSIONAL_FONT_FAMILY
    );
    
    public EnvironmentVariableDialog() {
        createDialog();
    }
    
    private void createDialog() {
        dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(false);
        dialogStage.setTitle("Select Environment Variable");
        dialogStage.setMinWidth(600);
        dialogStage.setMinHeight(450);
        
        // Set app icon
        IconUtils.setStageIcon(dialogStage);
        
        // Main container
        VBox mainContainer = new VBox(12);
        mainContainer.setPadding(new Insets(16));
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle(MODERN_STYLE + "-fx-background-radius: 8;");
        mainContainer.setEffect(new javafx.scene.effect.DropShadow(10, Color.rgb(0, 0, 0, 0.1)));
        
        // Header
        HBox header = createHeader();
        
        // Search section
        VBox searchSection = createSearchSection();
        
        // Environment variables table
        VBox tableSection = createTableSection();
        
        // Status section
        statusLabel = new Label("Loading environment variables...");
        statusLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 11px; -fx-text-fill: #6B7280;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Footer buttons
        HBox footer = createFooter();
        
        mainContainer.getChildren().addAll(header, searchSection, tableSection, statusLabel, footer);
        
        Scene scene = new Scene(mainContainer);
        scene.getRoot().setStyle(MODERN_STYLE);
        dialogStage.setScene(scene);
        
        // Handle close button (X) click
        dialogStage.setOnCloseRequest(event -> {
            if (resultFuture != null && !resultFuture.isDone()) {
                resultFuture.complete(Optional.empty());
            }
            dialogStage.close();
        });
    }
    
    private HBox createHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Use a simple label with text instead of FontIcon to avoid icon issues
        Label iconLabel = new Label("⚙");
        iconLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #3B82F6;");
        
        Label titleLabel = new Label("Environment Variables");
        titleLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1F2937;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        header.getChildren().addAll(iconLabel, titleLabel);
        return header;
    }
    
    private VBox createSearchSection() {
        VBox searchSection = new VBox(6);
        
        Label searchLabel = new Label("Search Environment Variables:");
        searchLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #374151;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        HBox searchBox = new HBox(6);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        // Use a simple label with text instead of FontIcon
        Label searchIconLabel = new Label("🔍");
        searchIconLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");
        
        searchField = new TextField();
        searchField.setPromptText("Type to search environment variables by name or value...");
        searchField.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-background-color: #FFFFFF; -fx-border-color: #D1D5DB; -fx-border-radius: 4; -fx-padding: 6 10; -fx-text-fill: #374151;",
            PROFESSIONAL_FONT_FAMILY
        ));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchBox.getChildren().addAll(searchIconLabel, searchField);
        searchSection.getChildren().addAll(searchLabel, searchBox);
        
        return searchSection;
    }
    
    private VBox createTableSection() {
        VBox tableSection = new VBox(6);
        
        Label tableLabel = new Label("Available Environment Variables:");
        tableLabel.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #374151;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Create table
        envVarTable = new TableView<>();
        envVarTable.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 11px; -fx-background-color: #FFFFFF; -fx-border-color: #D1D5DB; -fx-border-radius: 4;",
            PROFESSIONAL_FONT_FAMILY
        ));
        envVarTable.setPrefHeight(250);
        envVarTable.setPlaceholder(new Label("No environment variables found"));
        
        // Create columns
        TableColumn<EnvVarItem, String> nameColumn = new TableColumn<>("Variable Name");
        nameColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));
        nameColumn.setPrefWidth(180);
        nameColumn.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 11px; -fx-font-weight: 600;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        TableColumn<EnvVarItem, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getValue()));
        valueColumn.setPrefWidth(350);
        valueColumn.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 11px;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        // Add columns to table
        envVarTable.getColumns().addAll(nameColumn, valueColumn);
        
        // Enable row selection
        envVarTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        
        VBox.setVgrow(envVarTable, Priority.ALWAYS);
        tableSection.getChildren().addAll(tableLabel, envVarTable);
        
        return tableSection;
    }
    
    private HBox createFooter() {
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_RIGHT);
        
        selectButton = new Button("Select");
        selectButton.setDefaultButton(true);
        selectButton.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-color: #3B82F6; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 6 12; -fx-cursor: hand;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setStyle(String.format(
            "-fx-font-family: %s; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-color: #6B7280; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 6 12; -fx-cursor: hand;",
            PROFESSIONAL_FONT_FAMILY
        ));
        
        footer.getChildren().addAll(cancelButton, selectButton);
        return footer;
    }
    
    public CompletableFuture<Optional<String>> showDialog(Stage parentStage) {
        resultFuture = new CompletableFuture<>();
        
        // Center on parent
        if (parentStage != null) {
            dialogStage.initOwner(parentStage);
            dialogStage.centerOnScreen();
        }
        
        // Load environment variables
        loadEnvironmentVariables();
        
        // Setup event handlers
        setupEventHandlers();
        
        // Show dialog
        dialogStage.show();
        
        return resultFuture;
    }
    
    private void loadEnvironmentVariables() {
        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                List<EnvVarItem> envVars = new ArrayList<>();
                
                // Get system environment variables
                Map<String, String> systemEnv = System.getenv();
                for (Map.Entry<String, String> entry : systemEnv.entrySet()) {
                    envVars.add(new EnvVarItem(entry.getKey(), entry.getValue()));
                }
                
                // Sort by name
                envVars.sort(Comparator.comparing(EnvVarItem::getName));
                
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    try {
                        ObservableList<EnvVarItem> observableList = FXCollections.observableArrayList(envVars);
                        FilteredList<EnvVarItem> filteredList = new FilteredList<>(observableList, p -> true);
                        
                        // Bind search field to filtered list
                        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                            filteredList.setPredicate(envVar -> {
                                if (newValue == null || newValue.isEmpty()) {
                                    return true;
                                }
                                String lowerCaseFilter = newValue.toLowerCase();
                                return envVar.getName().toLowerCase().contains(lowerCaseFilter) ||
                                       envVar.getValue().toLowerCase().contains(lowerCaseFilter);
                            });
                        });
                        
                        envVarTable.setItems(filteredList);
                        statusLabel.setText(String.format("Found %d environment variables", envVars.size()));
                        
                        // Select first item if available
                        if (!filteredList.isEmpty()) {
                            envVarTable.getSelectionModel().select(0);
                        }
                    } catch (Exception e) {
                        statusLabel.setText("Error displaying environment variables: " + e.getMessage());
                        statusLabel.setStyle(String.format(
                            "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #EF4444;",
                            PROFESSIONAL_FONT_FAMILY
                        ));
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading environment variables: " + e.getMessage());
                    statusLabel.setStyle(String.format(
                        "-fx-font-family: %s; -fx-font-size: 12px; -fx-text-fill: #EF4444;",
                        PROFESSIONAL_FONT_FAMILY
                    ));
                });
            }
        }, "env-var-loader").start();
    }
    
    private void setupEventHandlers() {
        // Select button
        selectButton.setOnAction(e -> {
            EnvVarItem selected = envVarTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                resultFuture.complete(Optional.of(selected.getName()));
                dialogStage.close();
            } else {
                // Show warning if no item selected
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("No Selection");
                alert.setHeaderText(null);
                alert.setContentText("Please select an environment variable.");
                alert.showAndWait();
            }
        });
        
        // Cancel button
        cancelButton.setOnAction(e -> {
            resultFuture.complete(Optional.empty());
            dialogStage.close();
        });
        
        // Double-click to select
        envVarTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                EnvVarItem selected = envVarTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    resultFuture.complete(Optional.of(selected.getName()));
                    dialogStage.close();
                }
            }
        });
        
        // Enter key to select
        envVarTable.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                EnvVarItem selected = envVarTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    resultFuture.complete(Optional.of(selected.getName()));
                    dialogStage.close();
                }
            }
        });
    }
    
    /**
     * Environment variable item for display in the table
     */
    public static class EnvVarItem {
        private final String name;
        private final String value;
        
        public EnvVarItem(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public String getValue() { return value; }
        
        @Override
        public String toString() {
            return name + " = " + value;
        }
    }
}
