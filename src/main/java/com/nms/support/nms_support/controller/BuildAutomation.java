package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.*;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.OpenFile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class BuildAutomation implements Initializable{
    public TextArea buildLog;
    public Button reloadButton;
    public TextField jconfigPath;
    public TextField webWorkspacePath;
    public TextField usernameField;
    public ComboBox<String> userTypeComboBox;
    public CheckBox autoLoginCheckBox;
    public Button deleteButton;
    public Button updateButton;
    public ComboBox<String> buildMode;
    public ComboBox<String> appName;
    public Button buildButton;
    public Button stopButton;
    public Button startButton;
    public Button openNMSLogButton;
    public Button openBuildLogButton;
    public Button restartButton;
    public TextField passwordField;
    public ComboBox<String> projectLogComboBox;
    public AnchorPane buildRoot;
    private MainController mainController;
    private ControlApp controlApp;


    @FXML
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize UI components and set up event handlers
        System.out.println("Build automation initialized");
        reloadButton.setOnAction(event -> reloadLogNames());
        updateButton.setOnAction(event -> updateSetup());
        deleteButton.setOnAction(event -> deleteSetup());
        buildButton.setOnAction(event -> build());
        stopButton.setOnAction(event -> stop());
        startButton.setOnAction(event -> start());
        openNMSLogButton.setOnAction(event -> openNMSLog());
        openBuildLogButton.setOnAction(event -> openBuildLog());
        restartButton.setOnAction(event -> restart());
        buildMode.getItems().addAll("Ant config","Ant clean config");
        buildMode.getSelectionModel().select(0);
        appendTextToLog("\nWelcome "+System.getProperty("user.name")+"!\uD83E\uDD17");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        controlApp = new ControlApp(this, this.mainController.logManager, mainController.projectManager);
        this.mainController.projectComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> loadProjectDetails(newValue));
        initializeProjectLogComboBox();

    }

    private void initializeProjectLogComboBox() {
        // Fetch logs from the logManager
        List<LogEntity> logs = mainController.logManager.getLogWrapper().getLogs();

        // If logs are available, populate the combo box
        if (logs != null && !logs.isEmpty()) {
            List<String> logIds = logs.stream()
                    .map(LogEntity::getId)
                    .collect(Collectors.toList());
            projectLogComboBox.getItems().clear();
            projectLogComboBox.getItems().addAll(logIds);
        } else {
            System.out.println("No logs available to load.");
        }

        // Set event listener to update log details when a new log is selected
//        projectLogComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
//            if (newValue != null && !"None".equals(newValue)) {
//                LogEntity selectedLog = mainController.logManager.getLogById(newValue);
//                if (selectedLog != null) {
//                    // Perform any action based on the selected log
//                    // For example, you can display log details in another component if needed
//                    System.out.println("Selected log: " + selectedLog.toString());
//                }
//            }
//        });
    }
    public void populateUserTypeComboBox(ProjectEntity project) {
        if (project == null){
            userTypeComboBox.getItems().clear();
            return;
        }

        List<String> types = project.getTypes() != null ? new ArrayList<>(project.getTypes()) : new ArrayList<>();

        Platform.runLater(() -> {
            userTypeComboBox.getItems().clear();
            userTypeComboBox.getItems().addAll(types);
            if (project.getPrevTypeSelected() != null && types.contains(project.getPrevTypeSelected())) {
                userTypeComboBox.setValue(project.getPrevTypeSelected());
            } else {
                String adminType = types.stream()
                        .filter(type -> type.toLowerCase().contains("admin"))
                        .findFirst()
                        .orElse(null);
                if (adminType != null) {
                    userTypeComboBox.setValue(adminType);
                    project.setPrevTypeSelected(adminType);
                }
            }
            if (userTypeComboBox.getValue() == null && !userTypeComboBox.getItems().isEmpty()) {
                userTypeComboBox.setValue(userTypeComboBox.getItems().get(0));
            }
        });
    }


    public void populateAppNameComboBox(String folderPath) {
        if(folderPath == null) return;
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

        Platform.runLater(() -> {
            appName.setItems(FXCollections.observableArrayList(exeFiles));
            if (!exeFiles.isEmpty()) {
                String selectedFile = exeFiles.stream()
                        .filter(file -> file.equalsIgnoreCase("webworkspace.exe"))
                        .findFirst()
                        .orElse(exeFiles.get(0));
                appName.getSelectionModel().select(selectedFile);
            }
        });
    }

    private void loadProjectDetails(String projectName) {
        clearFields();
        ProjectEntity project = mainController.getSelectedProject();
        if (project != null) {
            jconfigPath.setText(project.getJconfigPath());
            webWorkspacePath.setText(project.getExePath());
            usernameField.setText(project.getUsername());
            passwordField.setText(project.getPassword());
            autoLoginCheckBox.setSelected(Boolean.parseBoolean(project.getAutoLogin()));

            // Set userTypeComboBox and buildMode based on project details if needed
            // userTypeComboBox.setValue(project.getUserType());
            // buildMode.setValue(project.getBuildMode());
            // appName.setValue(project.getAppName());

            String log = project.getLogId(); // Assuming ProjectEntity has a LogEntity field
            if (log != null) {
                projectLogComboBox.setValue(mainController.logManager.getLogById(log).getId());
            } else {
                projectLogComboBox.setValue("None");
            }
            populateAppNameComboBox(project.getExePath());
            populateUserTypeComboBox(project);
        }
    }

    private void clearFields() {
        jconfigPath.clear();
        webWorkspacePath.clear();
        usernameField.clear();
        passwordField.clear();
        autoLoginCheckBox.setSelected(false);
        projectLogComboBox.setValue("None");
        userTypeComboBox.getItems().clear();
    }


    private void restart() {
        if(!stop())return;
        try {
            Task<Boolean> buildTask = controlApp.build(mainController.getSelectedProject(),buildMode.getValue());
            buildTask.setOnSucceeded(event -> {
                Boolean result = buildTask.getValue();
                if (result) {
                    //appendTextToLog("Build completed successfully.\n");
                    start();
                } else {
                    appendTextToLog("Build failed.\n Not Launching Application.\n");
                }
            });
        } catch (InterruptedException e) {
            appendTextToLog("Exception in build() method");
            throw new RuntimeException(e);
        }

    }

    private void openBuildLog() {
        //appendTextToLog("This Functionality not implemented yet.");
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/build_logs/" + "buildlog_" + mainController.getSelectedProject().getName() + ".log";
        OpenFile.open(dataStorePath);

    }

    private void openNMSLog() {
        clearLog();
        controlApp.viewLog(appName.getValue(),mainController.getSelectedProject());
    }

    private void start() {
        clearLog();
        try {
            if(Validation.validate(mainController.getSelectedProject()) && !WritePropertiesFile.updateFile(mainController.getSelectedProject())){
                appendTextToLog("\nUpdate cred.properties file completed.\n");
            }
            else {
                appendTextToLog("\nUpdate cred.properties file failed.\n");
            }
            controlApp.start(appName.getValue(),mainController.getSelectedProject());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean stop() {
        clearLog();
        try {
            return controlApp.stopProject(appName.getValue(),mainController.getSelectedProject());
        } catch (IOException e) {
            return false;
        }
    }

    private void build() {
        clearLog();
        appendTextToLog("Build process begin for "+mainController.projectComboBox.getValue());
        try {
            controlApp.build(mainController.getSelectedProject(),buildMode.getValue());
        } catch (InterruptedException e) {
            appendTextToLog(e.toString());
        }
    }

    private void deleteSetup() {
        clearLog();
        try {
            DeleteSetup.delete(mainController.getSelectedProject(),this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateSetup() {
        clearLog();
        System.out.println("update setup invoked");
        LogEntity log = mainController.logManager.getLogById((String) projectLogComboBox.getValue());
        String selectedProjectName = mainController.projectComboBox.getValue();
        if (selectedProjectName == null || selectedProjectName.isEmpty()) {
            System.out.println("No project selected");
            return;
        }

        ProjectEntity updatedProject = mainController.projectManager.getProjectByName(selectedProjectName);

        if(updatedProject == null){
            clearLog();
            appendTextToLog("Please Select a valid project.");
            return;
        }

        updatedProject.setAutoLogin(Boolean.toString(autoLoginCheckBox.isSelected()));
        updatedProject.setJconfigPath(jconfigPath.getText());
        updatedProject.setExePath(webWorkspacePath.getText());
        updatedProject.setLogId(log!=null?log.getId():null);
        updatedProject.setUsername(usernameField.getText());
        updatedProject.setPassword(passwordField.getText());
        updatedProject.setPrevTypeSelected(userTypeComboBox.getValue());
        System.out.println(mainController.projectManager.getProjectWrapper().toString());
        mainController.projectManager.saveData();
        System.out.println("Project updated: " + selectedProjectName);
        if(autoLoginCheckBox.isSelected()){
            try {
            if(!Validation.validateForAutologin(updatedProject)){
                DialogUtil.showWarning("Fields missing required for Setup","Need to perform setup for getting autologin enabled");
                return;
            }
            if(Validation.validateSetup(updatedProject)) return;
            appendTextToLog("Auto login Option selected performing config update in project files");

                boolean res = SetupAutoLogin.execute(mainController.getSelectedProject(), this);
                if (res) {
                    appendTextToLog("Setup Successful");
                    populateUserTypeComboBox(updatedProject);
                    mainController.projectManager.saveData();
                    build();
                } else {
                    appendTextToLog("Setup failed");
                }
            }
            catch (Exception e){
                e.printStackTrace();
                appendTextToLog("Exception raised on setup: "+e.toString());
            }
        }

    }

    private void reloadLogNames() {
        clearLog();
        controlApp.refreshLogNames(); // Refresh log names in ControlApp
        reloadLogNamesCB(); // Update ComboBox with new log names
    }

    private void reloadLogNamesCB() {
        List<LogEntity> logs = mainController.logManager.getLogWrapper().getLogs(); // Get updated logs
        if (logs != null) {
            List<String> logIds = logs.stream()
                    .map(LogEntity::getId)
                    .toList();
            projectLogComboBox.getItems().clear();
            projectLogComboBox.getItems().addAll(logIds);
        } else {
            System.out.println("No logs available to load.");
        }
    }

    public void appendTextToLog(String text) {
        if (buildLog != null && text != null) {
            buildLog.appendText(text + System.lineSeparator());
        }
    }

    // Method to clear the TextArea
    public void clearLog() {
        if (buildLog != null) {
            buildLog.clear();
        }
    }

}
