package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.LogManager;
import com.nms.support.nms_support.service.ProjectManager;
import com.nms.support.nms_support.service.VpnManager;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.IconUtils;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    private static final Logger logger = LoggerUtil.getLogger();

    @FXML
    public ComboBox<String> themeComboBox;
    public Tab buildTab;
    public ComboBox<String> projectComboBox;
    public ProjectManager projectManager;
    public LogManager logManager;
    public VpnManager vpnManager;
    public Button addButton;
    public Button delButton;
    public Tab dataStoreTab;
    public Button openVpnButton;
    @FXML
    private Parent root;
    @FXML
    private ImageView themeIcon;

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
        }
        else{
            buildTab.setDisable(false);
            dataStoreTab.setDisable(false);
        }
    }

    private void loadTabContent() {
        try {
            FXMLLoader buildLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/build-automation.fxml"));
            Parent buildContent = buildLoader.load();
            BuildAutomation buildAutomation = buildLoader.getController();
            buildAutomation.setMainController(this);
            buildTab.setContent(buildContent);

            FXMLLoader dataStoreLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/datastore-dump.fxml"));
            Parent dataStoreContent = dataStoreLoader.load();
            DatastoreDumpController dataStoreAutomation = dataStoreLoader.getController();
            dataStoreAutomation.setMainController(this);
            dataStoreTab.setContent(dataStoreContent);

            logger.info("Tab content loaded successfully.");
        } catch (IOException e) {
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
                "Project Name:"
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
                reloadProjectNamesCB(); // Refresh ComboBox with updated list
                projectComboBox.setValue(projectName);
                DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Project Added", "Project added successfully.");
                logger.info("New project added: " + projectName);
            }
            } else {
                System.out.println("No input provided.");
            }
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
            e.printStackTrace();
        }
    }
}
