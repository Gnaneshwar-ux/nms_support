package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.LogManager;
import com.nms.support.nms_support.service.ProjectManager;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MainController implements Initializable {


    @FXML
    public ComboBox<String> themeComboBox;
    public Tab buildTab;
    public ComboBox<String> projectComboBox;
    public ProjectManager projectManager;
    public LogManager logManager;
    public Button addButton;
    public Button delButton;
    public Tab dataStoreTab;
    @FXML
    private Parent root;
    @FXML
    private ImageView themeIcon;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("Main controller initialized");
        if (root != null) {
            root.getStyleClass().add("root");
        } else {
            System.out.println("Root is null");
        }
        String user = System.getProperty("user.name");
        projectManager = new ProjectManager("C:\\Users\\"+user+"\\Documents\\nms_support_data\\projects.json");
        logManager = new LogManager("C:\\Users\\"+user+"\\Documents\\nms_support_data\\logs.json");

        addButton.setOnAction(event -> addProject());
        delButton.setOnAction(event -> removeProject());
        reloadProjectNamesCB();
        loadTabContent();
        setTabState(projectComboBox.getValue());
        projectComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> setTabState(newValue));
    }

    private void setTabState(String newValue) {
        if(newValue.equals("None")){
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

            //Linking Build Automation Tab
            FXMLLoader buildLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/build-automation.fxml"));
            Parent buildContent = buildLoader.load();
            BuildAutomation buildAutomation = buildLoader.getController();
            buildAutomation.setMainController(this);
            buildTab.setContent(buildContent);

            //Linking DataStore Dump Tab
            FXMLLoader dataStoreLoader = new FXMLLoader(getClass().getResource("/com/nms/support/nms_support/view/tabs/datastore-dump.fxml"));
            Parent dataStoreContent = dataStoreLoader.load();
            DatastoreDumpController dataStoreAutomation = dataStoreLoader.getController();
            dataStoreAutomation.setMainController(this);
            dataStoreTab.setContent(dataStoreContent);

        } catch (IOException e) {
            e.printStackTrace();
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
        }
    }

    private void addProject() {
        Optional<String> result = DialogUtil.showTextInputDialog(
                "Add Project",
                "Enter the name of the new project:",
                "Project Name:"
        );

        if (result.isPresent()) {
            String projectName = result.get().trim();
            if (projectName.isEmpty()) {
                DialogUtil.showAlert(Alert.AlertType.WARNING, "Invalid Input", "Project name cannot be empty.");
                return;
            }

            List<String> existingProjects = projectManager.getProjects() != null ? projectManager.getProjects().stream()
                    .map(ProjectEntity::getName)
                    .collect(Collectors.toList()):null;

            if (existingProjects!=null && existingProjects.contains(projectName)) {
                DialogUtil.showAlert(Alert.AlertType.WARNING, "Duplicate Project", "Project with this name already exists.");
            } else {
                // Add the new project with default or empty values
                ProjectEntity newProject = new ProjectEntity(
                        projectName
                );

                projectManager.addProject(newProject);
                projectManager.saveData();
                reloadProjectNamesCB();// Refresh ComboBox with updated list
                projectComboBox.setValue(projectName);
                DialogUtil.showAlert(Alert.AlertType.INFORMATION, "Project Added", "Project added successfully.");
            }
        }
    }

    public ProjectEntity getSelectedProject(){
        if (projectComboBox.getValue().equals("None")) return null;
        return projectManager.getProjectByName(projectComboBox.getValue());
    }

    private void reloadProjectNamesCB() {
        System.out.println(projectManager.getProjectWrapper().toString());
        List<String> projectNames =projectManager.getProjects() != null ? projectManager.getProjects().stream()
                .map(ProjectEntity::getName)
                .collect(Collectors.toList()): new ArrayList<>();
        projectNames.add(0, "None"); // Add "None" as the first item
        projectComboBox.getItems().setAll(projectNames);
        if (projectComboBox.getValue() == null || !projectNames.contains(projectComboBox.getValue())) {
            projectComboBox.setValue("None"); // Set default value if current selection is not valid
        }
    }
}