package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.tmatesoft.svn.core.SVNException;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DialogUtil {

    // Global inline style for all dialog content
    private static final String GLOBAL_STYLE = "-fx-font-family: 'Arial'; -fx-font-size: 14px;";

    // Apply the global inline style to any Node
    private static void applyGlobalStyle(Node node) {
        if (node != null) node.setStyle(GLOBAL_STYLE);
    }

    private static void setAlertIcon(Alert alert) {
        IconUtils.setStageIcon((Stage) alert.getDialogPane().getScene().getWindow());
        applyGlobalStyle(alert.getDialogPane());
    }

    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            setAlertIcon(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            int height = 150, count = 0;
            for (String line : message.split("\n")) count += Math.ceil(line.length() / 64.0);
            if (count > 3) height += (count - 3) * 20;
            alert.setWidth(450);
            alert.setHeight(height);
            alert.showAndWait();
        });
    }

    public static void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.WARNING);
            setAlertIcon(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static Optional<ButtonType> showConfirmationDialog(String title, String header, String content) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        setAlertIcon(alert);
        applyGlobalStyle(alert.getDialogPane());
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setPrefSize(400, 200);
        return alert.showAndWait();
    }

    public static CompletableFuture<Optional<String>> showTextInputDialog(String title, String header, String content, String defaultValue) {
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog(defaultValue);
            IconUtils.setStageIcon((Stage) dialog.getDialogPane().getScene().getWindow());
            applyGlobalStyle(dialog.getDialogPane());
            dialog.setTitle(title);
            dialog.setHeaderText(header);
            dialog.setContentText(content);
            future.complete(dialog.showAndWait());
        });
        return future;
    }

    public static void showAlert(AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            setAlertIcon(alert);
            applyGlobalStyle(alert.getDialogPane());
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public static List<Map<String, String>> selectProcess(List<Map<String, String>> process) {
        Stage dialogStage = new Stage();
        IconUtils.setStageIcon(dialogStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Select process");

        BorderPane root = new BorderPane();
        Label label = new Label("Select from the list below:");
        applyGlobalStyle(label);
        root.setTop(label);

        TableView<Map<String, String>> tableView = new TableView<>();
        tableView.setEditable(false);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<Map<String, String>, String> col1 = new TableColumn<>("S.NO");
        col1.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                String.valueOf(tableView.getItems().indexOf(data.getValue()) + 1)));
        col1.setPrefWidth(50);
        TableColumn<Map<String, String>, String> col2 = new TableColumn<>("PROCESS");
        col2.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("LAUNCHER")));
        col2.setPrefWidth(150);
        TableColumn<Map<String, String>, String> col3 = new TableColumn<>("MODIFIED TIME");
        col3.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("TIME")));
        col3.setPrefWidth(150);
        TableColumn<Map<String, String>, String> col4 = new TableColumn<>("PID");
        col4.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("PID")));
        col4.setPrefWidth(100);
        tableView.getColumns().addAll(col1, col2, col3, col4);

        FilteredList<Map<String, String>> filtered = new FilteredList<>(
                FXCollections.observableArrayList(process), p -> true);
        tableView.setItems(filtered);

        CheckBox filterCheckBox = new CheckBox("Filter local processes");
        filterCheckBox.setSelected(true);
        applyGlobalStyle(filterCheckBox);
        filterCheckBox.setOnAction(e -> filtered.setPredicate(
                filterCheckBox.isSelected() ? p -> "EXE".equalsIgnoreCase(p.get("LAUNCHER")) : p -> true));
        filtered.setPredicate(p -> "EXE".equalsIgnoreCase(p.get("LAUNCHER")));

        HBox filterBox = new HBox(filterCheckBox);
        filterBox.setPadding(new Insets(10)); applyGlobalStyle(filterBox);
        Button okButton = new Button("OK"); applyGlobalStyle(okButton);
        okButton.setOnAction(e -> dialogStage.close());
        Button cancelButton = new Button("Cancel"); applyGlobalStyle(cancelButton);
        cancelButton.setOnAction(e -> { tableView.getSelectionModel().clearSelection(); dialogStage.close(); });
        HBox buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER); buttonBox.setPadding(new Insets(10)); applyGlobalStyle(buttonBox);

        root.setCenter(tableView);
        root.setBottom(new VBox(filterBox, buttonBox));

        Scene scene = new Scene(root, 500, 400);
        applyGlobalStyle(scene.getRoot());
        dialogStage.setScene(scene);
        dialogStage.showAndWait();

        List<Map<String, String>> selected = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        return selected.isEmpty() ? null : selected;
    }

    public static CompletableFuture<Optional<String[]>> showTwoInputDialog(
            String title, String message1, String message2,
            String desc1, String desc2, String v1, String v2) {
        CompletableFuture<Optional<String[]>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle(title);
            IconUtils.setStageIcon((Stage) dialog.getDialogPane().getScene().getWindow());
            applyGlobalStyle(dialog.getDialogPane());
            ButtonType okType = new ButtonType("OK", ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(10); applyGlobalStyle(grid);
            Label lbl1 = new Label(message1); applyGlobalStyle(lbl1);
            TextField fld1 = new TextField(v1); applyGlobalStyle(fld1); Label d1 = new Label(desc1); applyGlobalStyle(d1);
            Label lbl2 = new Label(message2); applyGlobalStyle(lbl2);
            TextField fld2 = new TextField(v2); applyGlobalStyle(fld2); Label d2 = new Label(desc2); applyGlobalStyle(d2);
            grid.add(lbl1, 0, 0); grid.add(fld1, 1, 0); grid.add(d1, 1, 1);
            grid.add(lbl2, 0, 2); grid.add(fld2, 1, 2); grid.add(d2, 1, 3);

            dialog.getDialogPane().setContent(grid);
            Platform.runLater(() -> fld1.requestFocus());
            dialog.setResultConverter(btn -> btn == okType ? new String[]{fld1.getText(), fld2.getText()} : null);
            future.complete(dialog.showAndWait());
        });
        return future;
    }

    public static CompletableFuture<Boolean> showProjectSetupDialog(
            String defaultEnv, ProjectEntity project) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Stage dialogStage = new Stage();
            IconUtils.setStageIcon(dialogStage);
            dialogStage.setTitle("Initial Project Setup");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
            ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(30);
            ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50);
            ColumnConstraints c3 = new ColumnConstraints(); c3.setPercentWidth(20);
            grid.getColumnConstraints().addAll(c1, c2, c3); applyGlobalStyle(grid);

            TextField projFld = new TextField(); projFld.setPromptText("Path without /jconfig"); applyGlobalStyle(projFld);

            directoryChooserListener(dialogStage, projFld);

            TextField prodFld = new TextField(); applyGlobalStyle(prodFld);

            directoryChooserListener(dialogStage, prodFld);

            TextField urlFld = new TextField(); applyGlobalStyle(urlFld);
            TextField envFld = new TextField(defaultEnv); applyGlobalStyle(envFld);
            TextField hostFld = new TextField(); applyGlobalStyle(hostFld);
            TextField userFld = new TextField(); applyGlobalStyle(userFld);
            PasswordField passFld = new PasswordField(); applyGlobalStyle(passFld);
            TextField svnFld = new TextField("https://adc4110315.us.oracle.com/svn/nms-projects/trunk/projects");
            svnFld.setDisable(true); applyGlobalStyle(svnFld);
            Button browseBtn = new Button("Find"); applyGlobalStyle(browseBtn);
            browseBtn.setDisable(true);
            browseBtn.setOnAction(e -> {
                browseBtn.setDisable(true); // disable immediately in FX thread
                Window fxWindow = browseBtn.getScene().getWindow();
                new Thread(() -> {
                    LoggerUtil.getLogger().info("Invoked SVN Browser for project dir");

                    try {
                        String picked = new SVNAutomationTool().browseAndSelectFolder(fxWindow, svnFld.getText());

                        if (picked != null) {
                            Platform.runLater(() -> svnFld.setText(picked));
                        }
                    } catch (SVNException ex) {
                        LoggerUtil.error(ex);
                        Platform.runLater(() -> showError("SVN Browse Failed", ex.getMessage()));
                    } finally {
                        Platform.runLater(() -> browseBtn.setDisable(false));
                    }
                }).start();
            });


            int row = 0;
            grid.add(new Label("Project Folder*:"), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(projFld, 1, row++, 2, 1);
            grid.add(new Label("Product Dir*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(prodFld, 1, row++, 2, 1);
            grid.add(new Label("NMS App URL*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(urlFld, 1, row++, 2, 1);
            grid.add(new Label("ENV Var: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(envFld, 1, row++, 2, 1);
            grid.add(new Label("NMS HOST*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(hostFld, 1, row++, 2, 1);
            grid.add(new Label("HOST USER*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(userFld, 1, row++, 2, 1);
            grid.add(new Label("HOST PASS*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(passFld, 1, row++, 2, 1);
            grid.add(new Label("SVN Repo URL: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(svnFld, 1, row); grid.add(browseBtn, 2, row++);

            CheckBox svnChk = new CheckBox("Enable SVN Checkout"); applyGlobalStyle(svnChk);
            svnChk.selectedProperty().addListener((obs, oldV, newV) -> {
                svnFld.setDisable(!newV); browseBtn.setDisable(!newV);
            });
            grid.add(svnChk, 1, row++, 2, 1);

            Button setupBtn = new Button("Setup"); applyGlobalStyle(setupBtn);
            Button skipBtn = new Button("Skip"); applyGlobalStyle(skipBtn);
            HBox btnBox = new HBox(10, setupBtn, skipBtn);
            btnBox.setAlignment(Pos.CENTER_RIGHT); btnBox.setPadding(new Insets(10,0,0,0)); applyGlobalStyle(btnBox);

            setupBtn.setOnAction(evt -> {
                String pDir = projFld.getText().trim(); String pdDir = prodFld.getText().trim();
                String nmUrl = urlFld.getText().trim(); String env = envFld.getText().trim();
                String hst = hostFld.getText().trim(); String usr = userFld.getText().trim(); String pw = passFld.getText().trim();
                if (pDir.isEmpty()||pdDir.isEmpty()||nmUrl.isEmpty()||hst.isEmpty()||usr.isEmpty()||pw.isEmpty()) {
                    showError("Missing Fields", "Please fill in all mandatory (*) fields."); return; }
                File pd = new File(pDir); if (!pd.exists()||!pd.isDirectory()) { showError("Invalid Directory", "Project Dir does not exist or is not a directory."); return; }
                File prod = new File(pdDir);
                if (!prod.isAbsolute()) {
                    showError("Invalid Directory Path", "Provided path is not an absolute path: " + pdDir);
                    return;
                }
                if (!prod.exists()) { if (!prod.mkdirs()) { showError("Failed to create product dir","System not able to create specified product dir. please create dir manually."); return; }}
                if (svnChk.isSelected() && !svnFld.getText().trim().isEmpty()) project.setSvnRepo(svnFld.getText().trim());
                else {
                    project.setSvnRepo("NULL");
                    if (!searchFile(pd, "build.xml")||!searchFile(pd, "build.properties")) {
                        showError("Build Files Missing","Project Dir must contain 'build.xml' and 'build.properties'. Make sure given project jconfig path."); return; }
                }
                project.setJconfigPath(pDir); project.setExePath(pdDir); project.setNmsEnvVar(env); project.setNmsAppURL(nmUrl);
                project.setHost(hst); project.setHostUser(usr); project.setHostPass(pw);
                future.complete(true); dialogStage.close();
            });
            skipBtn.setOnAction(evt -> { project.setSvnRepo("NULL"); future.complete(false); dialogStage.close(); });

            VBox root = new VBox(10, grid, btnBox);
            root.setPadding(new Insets(15)); applyGlobalStyle(root);
            Scene scene = new Scene(root);
            applyGlobalStyle(scene.getRoot());
            dialogStage.setScene(scene);
            Platform.runLater(() -> scene.getRoot().requestFocus());
            dialogStage.showAndWait();
        });
        return future;
    }

    private static boolean searchFile(File root, String fileName) {
        if (root == null || !root.exists()) return false;
        File[] files = root.listFiles(); if (files == null) return false;
        for (File file : files) {
            if (file.getName().equalsIgnoreCase(fileName)) return true;
        }
        return false;
    }

    public static void directoryChooserListener(Stage stage, TextField path){
        path.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                DirectoryChooser directoryChooser = new DirectoryChooser();

                File existingPath = new File(path.getText() != null ? path.getText():"");
                if (existingPath.exists() && existingPath.isDirectory()) {
                    directoryChooser.setInitialDirectory(existingPath);
                }

                File selectedDirectory = directoryChooser.showDialog(stage);
                if (selectedDirectory != null) {
                    path.setText(selectedDirectory.getAbsolutePath());
                }

                event.consume(); // prevent space input
            }
        });
    }
}
