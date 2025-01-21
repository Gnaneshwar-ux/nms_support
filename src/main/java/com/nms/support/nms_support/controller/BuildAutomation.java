package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.*;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.CreateInstallerCommand;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ManageFile;
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
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.nms.support.nms_support.service.globalPack.Checker.isEmpty;

public class BuildAutomation implements Initializable {
    private static final Logger logger = LoggerUtil.getLogger();

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
    public Button patchButtom;
    public Button reloadUsersButton;
    public Button replaceProjectBuild;
    public Button replaceProductBuild;

    private MainController mainController;
    private ControlApp controlApp;

    @FXML
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing BuildAutomation");

        reloadButton.setOnAction(event -> reloadLogNames());
        updateButton.setOnAction(event -> save());
        patchButtom.setOnAction(event-> patchUpgrade());
        deleteButton.setOnAction(event -> deleteSetup());
        buildButton.setOnAction(event -> build());
        stopButton.setOnAction(event -> stop());
        startButton.setOnAction(event -> start(mainController.getSelectedProject(), getSelectedAppName()));
        openNMSLogButton.setOnAction(event -> openNMSLog());
        openBuildLogButton.setOnAction(event -> openBuildLog());
        restartButton.setOnAction(event -> restart());
        reloadUsersButton.setOnAction(event -> initializeUserTypes());
        replaceProjectBuild.setOnAction(event -> {
            DialogUtil.showTextInputDialog("ENV INPUT","Enter env var name created for this project:","Ex: OPAL_HOME").thenAccept(result->{
                if(result.isPresent()){
                    String env_name = result.get().trim();
                    ManageFile.replaceTextInFiles(List.of(jconfigPath.getText()+"/build.properties"),"NMS_HOME", env_name);
                    ManageFile.replaceTextInFiles(List.of(jconfigPath.getText()+"/build.xml"),"NMS_HOME", env_name);
                    appendTextToLog("Replaced NMS_HOME with "+env_name+" in build.xml and build.properties files");
                }else {
                    appendTextToLog("Input of env var name empty or cancelled");
                }
            });

        });

        replaceProductBuild.setOnAction(event -> {
            DialogUtil.showTextInputDialog("ENV INPUT","Enter env var name created for this product:","Ex: OPAL_HOME").thenAccept(result->{
                if(result.isPresent()){
                    String env_name = result.get().trim();
                    ManageFile.replaceTextInFiles(List.of(webWorkspacePath.getText()+"/java/ant/build.properties"),"NMS_HOME", env_name);
                    ManageFile.replaceTextInFiles(List.of(webWorkspacePath.getText()+"/java/ant/build.xml"),"NMS_HOME", env_name);
                    appendTextToLog("Replaced NMS_HOME with "+env_name+" in build.xml and build.properties files");
                }else {
                    appendTextToLog("Input of env var name empty or cancelled");
                }
            });

        });

        buildMode.getItems().addAll("Ant config", "Ant clean config");
        buildMode.getSelectionModel().select(0);
        appName.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> appNameChange(newValue));

        appendTextToLog("Hi " + System.getProperty("user.name") + "!\uD83E\uDD17");

    }

    private void patchUpgrade() {
        ProjectEntity p = mainController.getSelectedProject();

        if (isEmpty(p.getHost()) || isEmpty(p.getHostUser()) || isEmpty(p.getHostPass())) {
            DialogUtil.showError("Required Fields Missing",
                    "Please make sure the following fields are filled in the DataStore Tab:\nHost Address\nUsername\nPassword\n");
            return;
        }
        if (isEmpty(p.getExePath()) ) {
            DialogUtil.showError("Required Fields Missing",
                    "Please make sure the following fields are filled in the Build Automation Tab:\nWebWorkspace.exe path\n");
            return;
        }

        DialogUtil.showTwoInputDialog(
                "Patch Upgrade",
                "Provide Application URL:",
                "Provide Environment Variable Name:",
                "Ex: https://ugbu-ash-147...com:7057/nms/",
                "Required field"
        ).thenAccept(result -> {
            if (result.isPresent()) {
                String[] inputs = result.get();
                String url = inputs[0];
                String envVarName = inputs[1];

                CreateInstallerCommand cic = new CreateInstallerCommand();

                // Create a Task to run the long-running process
                Task<Void> task = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        try {
                            logger.info("Starting CreateInstallerCommand execution.");
                            appendTextToLog("Patch Upgrade process begin");
                            boolean resp = cic.execute(url, envVarName, mainController.getSelectedProject(), BuildAutomation.this);
                            if(resp){
                                appendTextToLog("\nPatch upgrade completed.");
                            }else{
                                appendTextToLog("\nPatch upgrade failed.");
                            }
                            logger.info("CreateInstallerCommand execution completed.");
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Error executing CreateInstallerCommand", e);
                            throw e;
                        }
                        return null;
                    }
                };

                // Handle task success and failure
                task.setOnSucceeded(event -> {
                    logger.info("Patch Upgrade task completed successfully.");

                });

                task.setOnFailed(event -> {
                    Throwable throwable = task.getException();
                    logger.log(Level.SEVERE, "Error executing Patch Upgrade", throwable);

                    DialogUtil.showError("Execution Failed", "An error occurred while executing CreateInstallerCommand: " + throwable.getMessage());
                });

                // Start the task on a new thread
                new Thread(task).start();
            } else {
                LoggerUtil.getLogger().info("No input provided.");
            }
        });
    }


    private void appNameChange(String newValue) {
        logger.fine("App name changed to: " + newValue);
        populateUserTypeComboBox(mainController.getSelectedProject());
    }

    public void setMainController(MainController mainController) {
        logger.info("Setting main controller");
        this.mainController = mainController;
        controlApp = new ControlApp(this, this.mainController.logManager, mainController.projectManager);

        this.mainController.projectComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> loadProjectDetails(newValue));
        initializeProjectLogComboBox();
    }

    private void initializeProjectLogComboBox() {
        logger.fine("Initializing project log combo box");
        List<LogEntity> logs = mainController.logManager.getLogWrapper().getLogs();

        if (logs != null && !logs.isEmpty()) {
            List<String> logIds = logs.stream()
                    .map(LogEntity::getId)
                    .collect(Collectors.toList());
            projectLogComboBox.getItems().setAll(logIds);
        } else {
            logger.warning("No logs available to load.");
        }
    }

    private String getSelectedAppName() {
        return appName.getValue();
    }

    public void populateUserTypeComboBox(ProjectEntity project) {
        if (project == null) {
            userTypeComboBox.getItems().clear();
            return;
        }

        List<String> types = project.getTypes(getSelectedAppName()) != null ? new ArrayList<>(project.getTypes(getSelectedAppName())) : new ArrayList<>();

        Platform.runLater(() -> {
            userTypeComboBox.getItems().setAll(types);
            String prevType = project.getPrevTypeSelected(getSelectedAppName());
            if (prevType != null && types.contains(prevType)) {
                userTypeComboBox.setValue(prevType);
            } else {
                String adminType = types.stream()
                        .filter(type -> type.toLowerCase().contains("admin"))
                        .findFirst()
                        .orElse(null);
                userTypeComboBox.setValue(adminType != null ? adminType : (types.isEmpty() ? null : types.get(0)));
            }
        });
    }

    public void populateAppNameComboBox(String folderPath) {
        if (folderPath == null) return;

        String previousSelection = appName.getSelectionModel().getSelectedItem();

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
            if (previousSelection != null && exeFiles.contains(previousSelection)) {
                appName.getSelectionModel().select(previousSelection);
            } else if (!exeFiles.isEmpty()) {
                if (exeFiles.contains("WebWorkspace.exe")) {
                    appName.getSelectionModel().select("WebWorkspace.exe");
                }
                else {
                    appName.getSelectionModel().select(exeFiles.getFirst());
                }
            }
        });
    }

    private void loadProjectDetails(String projectName) {
        logger.fine("Loading project details for: " + projectName);
        clearFields();
        ProjectEntity project = mainController.getSelectedProject();
        if (project != null) {
            jconfigPath.setText(project.getJconfigPath());
            webWorkspacePath.setText(project.getExePath());
            usernameField.setText(project.getUsername());
            passwordField.setText(project.getPassword());
            autoLoginCheckBox.setSelected(Boolean.parseBoolean(project.getAutoLogin()));
            projectLogComboBox.setValue(project.getLogId() != null ? project.getLogId() : "None");
            populateAppNameComboBox(project.getExePath());
            populateUserTypeComboBox(project);
        }
    }

    private void clearFields() {
        logger.fine("Clearing fields");
        jconfigPath.clear();
        webWorkspacePath.clear();
        usernameField.clear();
        passwordField.clear();
        autoLoginCheckBox.setSelected(false);
        projectLogComboBox.setValue("None");
        userTypeComboBox.getItems().clear();
    }

    private void restart() {
        clearLog();
        logger.info("Restarting application");
        if(ControlApp.restartThread != null){
            ControlApp.restartThread.interrupt();
            appendTextToLog("Killed running task");
            ControlApp.restartThread = null;
        }
        ProjectEntity project = mainController.getSelectedProject();
        String app = getSelectedAppName();
        if (!stop()) return;
        try {
            boolean res = setupAutoLogin(project);
            if(!autoLoginCheckBox.isSelected()){
                appendTextToLog("Auto Login check box not selected");
            }
            else if(res)
                appendTextToLog("Auto Login Setup process successful");
            else {
                appendTextToLog("Auto Login Setup process failed");
                return;
            }
            res = setupRestartTools(project);
            if(res)
                appendTextToLog("RestartTools Setup process successful");
            else {
                appendTextToLog("RestartTools Setup process failed");
                return;
            }
            logger.info("Building project: " + project.getName());
            Task<Boolean> buildTask = controlApp.build(project, buildMode.getValue());

            attachDeleteProcess(buildTask, project,app, true);

        } catch (InterruptedException e) {
            logger.severe("Exception in build() method: " + e.getMessage());
            appendTextToLog("Exception in build() method");
        }
    }

    private void attachDeleteProcess(Task<Boolean> buildTask, ProjectEntity project, String app, boolean isRestart){
        buildTask.setOnSucceeded(event -> {
            Boolean result = buildTask.getValue();
            try {
                DeleteLoginSetup.delete(project, this, false);
                DeleteRestartSetup.delete(project, this, false);
                //this.appendTextToLog("Delete process completed\n");
            } catch (IOException e) {
                e.printStackTrace();
                appendTextToLog(e.toString());
                throw new RuntimeException(e);
            }
            if (result) {
                appendTextToLog("Build Success. ");
                if(isRestart)start(project, app);
            } else {
                appendTextToLog("Build failed. ");
            }
        });

        buildTask.setOnFailed(event -> {
            Boolean result = buildTask.getValue();
            try {
                DeleteLoginSetup.delete(project, this, false);
                DeleteRestartSetup.delete(project, this, false);
                //this.appendTextToLog("Delete process completed\n");
            } catch (IOException e) {
                e.printStackTrace();
                appendTextToLog(e.toString());
                throw new RuntimeException(e);
            }
            if (result!=null && result) {
                appendTextToLog("Build Success. ");
                if(isRestart)start(project, app);
            } else {
                appendTextToLog("Build failed. ");
            }
        });

        buildTask.setOnCancelled(event -> {
            Boolean result = buildTask.getValue();
            try {
                DeleteLoginSetup.delete(project, this, false);
                DeleteRestartSetup.delete(project, this, false);
                //this.appendTextToLog("Delete process completed\n");
            } catch (IOException e) {
                e.printStackTrace();
                appendTextToLog(e.toString());
                throw new RuntimeException(e);
            }
            if (result!=null && result) {
                appendTextToLog("Build Success. ");
                if(isRestart)start(project, app);
            } else {
                appendTextToLog("Build cancelled");
            }
        });
    }

    private void openBuildLog() {
        logger.info("Opening build log");
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/build_logs/" + "buildlog_" + mainController.getSelectedProject().getName() + ".log";
        ManageFile.open(dataStorePath);
    }

    private void openNMSLog() {
        logger.info("Opening NMS log");
        clearLog();
        controlApp.viewLog(appName.getValue(), mainController.getSelectedProject());
    }

    private void start(ProjectEntity project, String app) {
        logger.info("Starting application");
        clearLog();
        try {
            if (Validation.validate(project, app) && WritePropertiesFile.updateFile(project, app)) {
                appendTextToLog("Update cred.properties file completed.");
            } else {
                appendTextToLog("Update cred.properties file failed.");
            }
            controlApp.start(app, project);
        } catch (IOException | InterruptedException e) {
            logger.severe("Exception in start() method: " + e.getMessage());
        }
    }

    private boolean stop() {
        logger.info("Stopping application");
        clearLog();
        try {
            return controlApp.stopProject(appName.getValue(), mainController.getSelectedProject());
        } catch (IOException e) {
            logger.severe("IOException in stop() method: " + e.getMessage());
            return false;
        }
    }

    private void build() {
        ProjectEntity project  = mainController.getSelectedProject();
        String app = getSelectedAppName();
        try {
            clearLog();
        boolean res = setupAutoLogin(project);
        if(!autoLoginCheckBox.isSelected()){
            appendTextToLog("Auto Login check box not selected");
        }
        else if(res)
            appendTextToLog("Auto Login Setup process successful");
        else {
            appendTextToLog("Auto Login Setup process failed");
            return;
        }
        res = setupRestartTools(project);
        if(res)
            appendTextToLog("RestartTools Setup process successful");
        else {
            appendTextToLog("RestartTools Setup process failed");
            return;
        }
        logger.info("Building project: " + project.getName());

        appendTextToLog("Build process begins for " +project.getName());
            Task<Boolean> buildTask = controlApp.build(project, buildMode.getValue());
            attachDeleteProcess(buildTask, project, app,false);
        } catch (InterruptedException e) {
            logger.severe("InterruptedException in build() method: " + e.getMessage());
            appendTextToLog(e.toString());
        }
    }

    private void deleteSetup() {
        logger.info("Deleting setup for project: " + mainController.getSelectedProject());
        //clearLog();
        try {
            DeleteLoginSetup.delete(mainController.getSelectedProject(), this,true);
            DeleteRestartSetup.delete(mainController.getSelectedProject(), this,true);
        } catch (IOException e) {
            logger.severe("IOException in deleteSetup() method: " + e.getMessage());
        }
    }

    private void save() {
        logger.info("Saving project: " + mainController.getSelectedProject().toString());
        clearLog();
        LogEntity log = mainController.logManager.getLogById(projectLogComboBox.getValue());
        String selectedProjectName = mainController.projectComboBox.getValue();
        if (selectedProjectName == null || selectedProjectName.isEmpty()) {
            logger.warning("No project selected");
            appendTextToLog("No project selected");

            return;
        }

        ProjectEntity updatedProject = mainController.projectManager.getProjectByName(selectedProjectName);
        if (updatedProject == null) {
            logger.warning("Invalid project selected");
            appendTextToLog("Please select a valid project.");
            return;
        }

        if(autoLoginCheckBox.isSelected() && userTypeComboBox.getValue() == null){
            appendTextToLog("Issue: Selected autoLogin option without user type loaded or selected");
            appendTextToLog("Failed to save project details");
            DialogUtil.showError("Missing User Type","To use Auto Login - user types must be loaded and selected.");
            return;
        }

        updatedProject.setAutoLogin(Boolean.toString(autoLoginCheckBox.isSelected()));
        updatedProject.setJconfigPath(jconfigPath.getText());
        updatedProject.setExePath(webWorkspacePath.getText());
        updatedProject.setLogId(log != null ? log.getId() : null);
        updatedProject.setUsername(usernameField.getText());
        updatedProject.setPassword(passwordField.getText());
        if (userTypeComboBox.getValue() != null) {
            updatedProject.setPrevTypeSelected(getSelectedAppName(), userTypeComboBox.getValue());
        }
        populateAppNameComboBox(updatedProject.getExePath());
        boolean res = mainController.projectManager.saveData();
        if(res){
            appendTextToLog("Save project details successful.");
        }
        else{
            appendTextToLog("Save project details failed.");
        }


        logger.info("Project updated: " + selectedProjectName);
        if (WritePropertiesFile.updateFile(updatedProject, getSelectedAppName())) {
            appendTextToLog("Updated cred.properties file");
        } else {
            appendTextToLog("Failed to update cred.properties file");
        }

    }

    private void initializeUserTypes(){
        SetupAutoLogin.loadUserTypes(mainController.getSelectedProject());
        populateUserTypeComboBox(mainController.getSelectedProject());
    }

    private boolean setupAutoLogin(ProjectEntity updatedProject){
        if (autoLoginCheckBox.isSelected()) {
            try {
                if (!Validation.validateForAutologin(updatedProject)) {
                    DialogUtil.showWarning("Fields missing required for Setup", "Need to perform setup for getting autologin enabled");
                    return false;
                }
                if (Validation.validateLoginSetup(updatedProject)){
                    return true;
                }
                appendTextToLog("Auto-login option selected, performing config update in project files");

                boolean res = SetupAutoLogin.execute(mainController.getSelectedProject(), this);
                mainController.projectManager.saveData();
                appendTextToLog(res ? "Setup successful" : "Setup failed");
                return res;
            } catch (Exception e) {
                logger.severe("Exception in setup: " + e.getMessage());
                appendTextToLog("Exception in setup: " + e.toString());
                return false;
            }
        }
        return false;
    }


    private boolean setupRestartTools(ProjectEntity updatedProject){
            try {
                if (!Validation.validateForRestartTools(updatedProject)) {
                    DialogUtil.showWarning("Fields missing required for restart tools Setup", "Need to perform restart tools setup for getting autologin enabled");
                    return false;
                }
                if (Validation.validateRestartToolsSetup(updatedProject)){
                    return true;
                }
                appendTextToLog("Auto-login option selected, performing config update in project files");

                boolean res = SetupRestartTool.execute(mainController.getSelectedProject(), this);
                mainController.projectManager.saveData();
                appendTextToLog(res ? "Restart tools Setup successful" : "Restart tools Setup failed");
                return res;
            } catch (Exception e) {
                logger.severe("Exception in Restart tools setup: " + e.getMessage());
                appendTextToLog("Exception in Restart tools setup: " + e.toString());
                return false;
            }
    }

    private void reloadLogNames() {
        logger.info("Reloading log names");
        clearLog();
        controlApp.refreshLogNames();
        reloadLogNamesCB();
    }

    private void reloadLogNamesCB() {
        logger.fine("Reloading log names in ComboBox");
        List<LogEntity> logs = mainController.logManager.getLogWrapper().getLogs();
        if (logs != null) {
            List<String> logIds = logs.stream()
                    .map(LogEntity::getId)
                    .collect(Collectors.toList());
            projectLogComboBox.getItems().setAll(logIds);
        } else {
            logger.warning("No logs available to load.");
        }
    }


    public void appendTextToLog(String text) {
        if (text != null) {
            Platform.runLater(() -> {
                if (buildLog != null) {
//                    String p = buildLog.getText();
//                    if(buildLog.getText().length() > 2000){
//                        List<String> previousLines = getLastNLines(p, 30);
//                        buildLog.clear();
//                        buildLog.appendText(String.join("\n", previousLines) + "\n");
//                    }
                    buildLog.appendText(text+"\n");
                }
            });
        }
    }
    private List<String> getLastNLines(String text, int n) {
        LinkedList<String> lines = new LinkedList<>();
        String[] allLines = text.split("\n");
        int start = Math.max(0, allLines.length - n);
        for (int i = start; i < allLines.length; i++) {
            lines.add(allLines[i]);
        }
        return lines;
    }

    public void clearLog() {
        Platform.runLater(() -> {
            if (buildLog != null) {
                buildLog.clear();
            }
        });
    }
}
