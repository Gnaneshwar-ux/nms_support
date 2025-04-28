package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.ControlApp;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.CreateInstallerCommand;
import com.nms.support.nms_support.service.globalPack.*;
import com.nms.support.nms_support.service.userdata.LogManager;
import com.nms.support.nms_support.service.userdata.ProjectManager;
import com.nms.support.nms_support.service.userdata.VpnManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
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
    @FXML
    public Tab dataStoreTab;
    public Button openVpnButton;
//    @FXML
//    public Tab projectTab;
    @FXML
    private Parent root;
    @FXML
    private ImageView themeIcon;

    BuildAutomation buildAutomation;


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
        reloadProjectNamesCB();
        loadTabContent();
        projectComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> setTabState(newValue));
         setTabState("None");


    }

    private void setTabState(String newValue) {
        if(newValue != null && newValue.equals("None")){
            buildTab.setDisable(true);
            dataStoreTab.setDisable(true);
            //projectTab.setDisable(true);
        }
        else{
            buildTab.setDisable(false);
            dataStoreTab.setDisable(false);
            //projectTab.setDisable(false);
        }
    }

    private void loadTabContent() {
        try {
            FXMLLoader buildLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/build-automation.fxml"));
            Parent buildContent = buildLoader.load();
            buildAutomation = buildLoader.getController();
            buildAutomation.setMainController(this);
            buildTab.setContent(buildContent);

            FXMLLoader dataStoreLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/datastore-dump.fxml"));
            Parent dataStoreContent = dataStoreLoader.load();
            DatastoreDumpController dataStoreAutomation = dataStoreLoader.getController();
            dataStoreAutomation.setMainController(this);
            dataStoreTab.setContent(dataStoreContent);

//            FXMLLoader projectLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/project-details.fxml"));
//            Parent projectContent = projectLoader.load();
//            ProjectDetailsController projectAutomation = projectLoader.getController();
//            projectAutomation.setMainController(this);
//            projectTab.setContent(projectContent);

            logger.info("Tab content loaded successfully.");
        } catch (IOException e) {
            LoggerUtil.error(e);
            logger.severe("Error loading tab content: " + e.getMessage());
        }
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
        DialogUtil.showTextInputDialog(
                "Add Project",
                "Enter the name of the new project:",
                "Project Name:",
                        ""
        ).thenAccept(result -> {
            if (result.isPresent()) {

            String projectName = result.get().trim();
            if (projectName.isEmpty()) {
                DialogUtil.showAlert(Alert.AlertType.WARNING, "Invalid Input", "Project name cannot be empty.");
                return;
            }

            List<String> existingProjects = projectManager.getProjects() != null ? projectManager.getProjects().stream()
                    .map(ProjectEntity::getName)
                    .collect(Collectors.toList()) : new ArrayList<>();

            if (existingProjects.contains(projectName)) {
                DialogUtil.showAlert(Alert.AlertType.WARNING, "Duplicate Project", "Project with this name already exists.");
            } else {
                ProjectEntity newProject = new ProjectEntity(projectName);
                projectManager.addProject(newProject);
                projectManager.saveData();
                requestProjectSetup(newProject);
                reloadProjectNamesCB(); // Refresh ComboBox with updated list
                projectComboBox.setValue(newProject.getName());
            }
            } else {
                System.out.println("No input provided.");
            }
        });
    }

    private void requestProjectSetup(ProjectEntity newProject) {
        showProjectSetupDialog(newProject.getName() + "_HOME_DEVTOOL", newProject)
                .thenAccept(setupChosen -> {
                    Platform.runLater(() -> {
                        reloadProjectNamesCB();
                        projectComboBox.setValue(newProject.getName());

                        if (setupChosen) {
                            logger.info("User chose to setup.");
                            buildAutomation.appendTextToLog("User chose to Setup");
                            projectManager.saveData();
                             logger.info("New project added: " + newProject.getName());

                            AtomicBoolean statusSVN = new AtomicBoolean(false);
                            Thread tSvn = new Thread(() -> {
                                try {
                                    if (newProject.getSvnRepo() != null && !newProject.getSvnRepo().equals("NULL")) {

                                        File jconfigFolder = new File(newProject.getJconfigPath());

                                        if (jconfigFolder.exists() && jconfigFolder.isDirectory() &&
                                                jconfigFolder.listFiles() != null && jconfigFolder.listFiles().length > 0) {

                                            final boolean[] proceed = {false};
                                            CountDownLatch latch = new CountDownLatch(1);

                                            Platform.runLater(() -> {
                                                Optional<ButtonType> result = DialogUtil.showConfirmationDialog(
                                                        "SVN Checkout",
                                                        "The project folder chosen is not empty. Do you want to proceed with clean checkout?",
                                                        "Ok to delete project directory."
                                                );

                                                if (result.isPresent() && result.get() == ButtonType.OK) {
                                                    proceed[0] = true;
                                                }
                                                latch.countDown();
                                            });

                                            // Wait for the user to respond
                                            latch.await();

                                            if (!proceed[0]) {
                                                statusSVN.set(false);
                                                return;
                                            }
                                        }

                                        // Clean folder before checkout
                                        SVNAutomationTool.deleteFolderContents(jconfigFolder);

                                        // Perform SVN checkout
                                        SVNAutomationTool.performCheckout(newProject.getSvnRepo(), newProject.getJconfigPath(), buildAutomation);

                                        // Replace variables in build files
                                        if(!newProject.getJconfigPath().contains("jconfig")) {
                                            newProject.setJconfigPath(newProject.getJconfigPath() + "\\jconfig");
                                        }
                                        String env_name = newProject.getNmsEnvVar();
                                        ManageFile.replaceTextInFiles(
                                                List.of(newProject.getJconfigPath() + "/build.properties"), "NMS_HOME", env_name);
                                        ManageFile.replaceTextInFiles(
                                                List.of(newProject.getJconfigPath() + "/build.xml"), "NMS_HOME", env_name);

                                        Platform.runLater(() -> {
                                            buildAutomation.appendTextToLog("Replaced NMS_HOME with " + env_name + " in project build files");
                                        });
                                    }

                                    statusSVN.set(true);

                                } catch (Exception e) {
                                    LoggerUtil.error(e);
                                    Platform.runLater(() -> buildAutomation.appendTextToLog("Error during SVN setup: " + e.getMessage()));

                                }
                            });

                            // ✅ Run heavy work in background
                            AtomicBoolean statusTPatch = new AtomicBoolean(false);
                            Thread tPatch = new Thread(() -> {
                                buildAutomation.clearLog();
                                CreateInstallerCommand cic = new CreateInstallerCommand();
                                boolean resp = false;
                                try {
                                    resp = cic.execute(newProject.getNmsAppURL(), newProject.getNmsEnvVar(), newProject, buildAutomation);
                                    if (resp) {
                                        buildAutomation.populateAppNameComboBox(newProject.getExePath());
                                        String env_name = newProject.getNmsEnvVar();
                                        ManageFile.replaceTextInFiles(
                                                List.of(newProject.getExePath() + "/java/ant/build.properties"), "NMS_HOME", env_name);
                                        ManageFile.replaceTextInFiles(
                                                List.of(newProject.getExePath() + "/java/ant/build.xml"), "NMS_HOME", env_name);

                                        Platform.runLater(() -> {
                                            buildAutomation.appendTextToLog("Replaced NMS_HOME with " + env_name + " in Product build files");
                                            buildAutomation.appendTextToLog("\nSetup completed.");
                                        });
                                    } else {
                                        Platform.runLater(() -> buildAutomation.appendTextToLog("\nSetup failed."));
                                    }
                                    statusTPatch.set(true);
                                } catch (Exception e) {
                                    logger.severe(e.getMessage());
                                    LoggerUtil.error(e);
                                    Platform.runLater(() -> buildAutomation.appendTextToLog(e.getMessage()));
                                }
                            });

                            Thread tFinal = new Thread(() -> {
                                try {
                                    tSvn.join();
                                    tPatch.join();
                                    if(statusSVN.get() && statusTPatch.get()){
                                        buildAutomation.appendTextToLog("***********Project setup process completed initiated build process*********");
                                    }
                                    DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Project Added", "Project added successfully. Check the setup status in log and Perform 'ant clean config'.");
                                    buildAutomation.loadProjectDetails();
                                }
                                catch (Exception e){
                                    LoggerUtil.error(e);
                                }
                            });

                            tSvn.start();
                            tPatch.start();
                            tFinal.start();

                        } else {
                            logger.info("User skipped setup.");
                            DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Project Added", "Project added successfully. Setup Skipped.");
                            logger.info("New project added: " + newProject.getName());
                        }
                    });
                });
    }


    public ProjectEntity getSelectedProject() {
        String selectedProjectName = projectComboBox.getValue();
        if ("None".equals(selectedProjectName)) return null;
        logger.info("Retrieved selected project: " + selectedProjectName);
        return projectManager.getProjectByName(selectedProjectName);
    }

    private void reloadProjectNamesCB() {
        List<String> projectNames = projectManager.getProjects() != null ? projectManager.getProjects().stream()
                .map(ProjectEntity::getName)
                .collect(Collectors.toList()) : new ArrayList<>();

        projectNames.add(0, "None"); // Add "None" as the first item
        projectComboBox.getItems().setAll(projectNames);
        Platform.runLater(() -> {
            projectComboBox.requestLayout();
        });
        if (projectComboBox.getValue() == null || !projectNames.contains(projectComboBox.getValue())) {
            projectComboBox.setValue("None"); // Set default value if current selection is not valid
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
            IconUtils.setStageIcon(dialogStage);
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
