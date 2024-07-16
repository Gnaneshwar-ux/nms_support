package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.DataStoreRecord;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.dataStoreTabPack.ParseDataStoreReport;
import com.nms.support.nms_support.service.dataStoreTabPack.ReportGenerator;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.OpenFile;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import javafx.util.converter.NumberStringConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class DatastoreDumpController {

    public ProgressIndicator loadingIndicator;
    public Button openReportButton;
    @FXML
    private TextField hostAddressField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField datastoreUserField;

    @FXML
    private Button saveButton;

    @FXML
    private Button loadButton;

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

    private final ObservableList<DataStoreRecord> datastoreRecords = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        System.out.println("DataStoreDumpController Initialized\n");
        // Initialize table columns
        column1.setCellValueFactory(new PropertyValueFactory<>("tool"));
        column2.setCellValueFactory(new PropertyValueFactory<>("dataStore"));
        column3.setCellValueFactory(new PropertyValueFactory<>("column"));
        column4.setCellValueFactory(new PropertyValueFactory<>("type"));
        column5.setCellValueFactory(new PropertyValueFactory<>("value"));

        // Set the table's items
        datastoreTable.setItems(datastoreRecords);

        // Add event handlers
        saveButton.setOnAction(event -> saveSSHDetails());
        loadButton.setOnAction(event -> loadDatastore());
        openReportButton.setOnAction(actionEvent -> openReport());

        // Implement search and filter functionality
        column1Filter.textProperty().addListener((observable, oldValue, newValue) -> filterTable());
        column2Filter.textProperty().addListener((observable, oldValue, newValue) -> filterTable());
        column3Filter.textProperty().addListener((observable, oldValue, newValue) -> filterTable());
        column4Filter.textProperty().addListener((observable, oldValue, newValue) -> filterTable());
        column5Filter.textProperty().addListener((observable, oldValue, newValue) -> filterTable());

        // Bind filter fields width to columns width
        column1Filter.prefWidthProperty().bind(column1.widthProperty());
        column2Filter.prefWidthProperty().bind(column2.widthProperty());
        column3Filter.prefWidthProperty().bind(column3.widthProperty());
        column4Filter.prefWidthProperty().bind(column4.widthProperty());
        column5Filter.prefWidthProperty().bind(column5.widthProperty());

        fitColumns();

        // Add context menu for clearing filters
        ContextMenu contextMenu = new ContextMenu();
        MenuItem clearFiltersItem = new MenuItem("Clear Filters");
        clearFiltersItem.setOnAction(event -> clearFilters());
        contextMenu.getItems().add(clearFiltersItem);

        datastoreTable.setOnContextMenuRequested((ContextMenuEvent event) -> contextMenu.show(datastoreTable, event.getScreenX(), event.getScreenY()));

        // Initially hide the spinner
        loadingIndicator.setVisible(false);
        overlayPane.setVisible(false);
    }

    private void openReport() {
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/datastore_reports/"+"report_" + mainController.getSelectedProject().getName() + ".txt";
        OpenFile.open(dataStorePath);
    }

    public void loadProjectDetails(String name){
        clearAllFields();
        ProjectEntity project = mainController.getSelectedProject();
        if(project==null)return;
        hostAddressField.setText(project.getHost());
        usernameField.setText(project.getHostUser());
        passwordField.setText(project.getHostPass());
        datastoreUserField.setText(project.getDataStoreUser());
    }

    public void clearAllFields(){
        hostAddressField.clear();
        usernameField.clear();
        passwordField.clear();
        datastoreUserField.clear();
    }

    private void fitColumns(){
        // Bind columns to equal width
        double totalColumns = 5.0;
        column1.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column2.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column3.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column4.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
        column5.prefWidthProperty().bind(datastoreTable.widthProperty().divide(totalColumns));
    }

    private void clearFilters() {
        column1Filter.clear();
        column2Filter.clear();
        column3Filter.clear();
        column4Filter.clear();
        column5Filter.clear();
        datastoreTable.getSortOrder().clear();
        fitColumns();
    }

    private void saveSSHDetails() {
        // Logic to save SSH details
        String projectName = mainController.projectComboBox.getValue();
        if(projectName.equals("None")){
            DialogUtil.showError("Invalid Project", "Please select any project or create new one");
            return;
        }
        ProjectEntity updateProject = mainController.projectManager.getProjectByName(projectName);

        if (updateProject == null){
            DialogUtil.showError("Invalid Project", "Please select any project or create new one");
            return;
        }
        updateProject.setHost(hostAddressField.getText());
        updateProject.setHostUser(usernameField.getText());
        updateProject.setDataStoreUser(datastoreUserField.getText());
        System.out.println(passwordField.getText());
        updateProject.setHostPass(passwordField.getText());
        mainController.projectManager.saveData();
    }

    private void showSpinner() {

            overlayPane.setVisible(true);
            loadingIndicator.setVisible(true);

    }

    private void hideSpinner() {

            loadingIndicator.setVisible(false);
            overlayPane.setVisible(false);

    }

    private void loadDatastore() {
        saveSSHDetails();
        if (!validation()) return;

        showSpinner(); // Show spinner before starting long-running task

        // Run the long-running task on a separate thread to avoid blocking the JavaFX Application Thread
        new Thread(() -> {
            try {
                String user = System.getProperty("user.name");
                String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/datastore_reports";
                Path reportPath = Paths.get(dataStorePath);
                FileTime fileTimeBefore = null;

                Files.createDirectories(reportPath);
                System.out.println("Directory is ready: " + dataStorePath);

                // Check if file exists and get the last modified time before executing the command
                Path reportFilePath = reportPath.resolve("report_" + mainController.getSelectedProject().getName() + ".txt");
                if (Files.exists(reportFilePath)) {
                    fileTimeBefore = Files.getLastModifiedTime(reportFilePath);
                    System.out.println("File last modified time before execution: " + fileTimeBefore);
                }

                ProjectEntity project = mainController.getSelectedProject();
                if (project == null) {
                    Platform.runLater(() -> {
                        DialogUtil.showError("Invalid Project!", "Please select a project");
                        hideSpinner(); // Ensure spinner is hidden if project is null
                    });
                    return;
                }

                boolean isExecuted = ReportGenerator.execute(project, reportFilePath.toString());
                if (!isExecuted) {
                    Platform.runLater(() -> {
                        DialogUtil.showError("Failed Generating Report", "Command failed execution\n1. Is VPN Connected\n2. Is client running.\n3. Please check URL, username, password...");
                        hideSpinner(); // Ensure spinner is hidden if execution fails
                    });
                    return;
                }

                Thread.sleep(3000); // Simulate delay for demo purposes

                // After executing the command, check if the file exists and has a new modified time
                if (Files.exists(reportFilePath)) {
                    FileTime fileTimeAfter = Files.getLastModifiedTime(reportFilePath);
                    System.out.println("File last modified time after execution: " + fileTimeAfter);

                    // Check if file has been updated
                    if (fileTimeBefore == null || fileTimeAfter.toInstant().isAfter(fileTimeBefore.toInstant())) {
                        ObservableList<DataStoreRecord> records = ParseDataStoreReport.parseDSReport(reportFilePath.toString());
                        Platform.runLater(() -> datastoreRecords.setAll(records));  // Update the ObservableList
                    } else {
                        Platform.runLater(() -> DialogUtil.showError("Report Generation Error", "Report file was not updated after command execution.\n1. Is VPN Connected?\n2. Is client running?\n3. Please check URL, username, password..."));
                    }
                } else {
                    Platform.runLater(() -> DialogUtil.showError("Report Generation Error", "Report file is missing after command execution.\n1. Is VPN Connected?\n2. Is Client Running?\n3. Please check URL, Username, Password and Client user."));
                }

            } catch (IOException e) {
                Platform.runLater(() -> {
                    DialogUtil.showError("Exception", "An error occurred while handling the report file.");
                    e.printStackTrace();
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    DialogUtil.showError("Interrupted", "The thread was interrupted.");
                    e.printStackTrace();
                });
            } finally {
                Platform.runLater(() -> hideSpinner()); // Ensure spinner is hidden in case of success or failure
            }
        }).start(); // Start the thread
    }

    private void filterTable() {
        String[] filters = {
                column1Filter.getText(),
                column2Filter.getText(),
                column3Filter.getText(),
                column4Filter.getText(),
                column5Filter.getText()
        };
        ObservableList<DataStoreRecord> filteredData = ParseDataStoreReport.filterRows(datastoreRecords, filters);
        datastoreTable.setItems(filteredData);
    }

    MainController mainController;
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        this.mainController.projectComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> loadProjectDetails(newValue));
    }

    private boolean validation() {
        if (hostAddressField==null || hostAddressField.getText()==null || hostAddressField.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Host Address is required");
            return false;
        }
        if (usernameField==null || usernameField.getText()==null || usernameField.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Username is required");
            return false;
        }
        if (passwordField==null || passwordField.getText()==null || passwordField.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Password is required");
            return false;
        }
        if (datastoreUserField==null || datastoreUserField.getText()==null || datastoreUserField.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Datastore User is required");
            return false;
        }
        return true;
    }
}
