package com.nms.support.nms_support.service.globalPack;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DialogUtil {

    // Show an error dialog with the specified title and message
    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showWarning(String title, String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null); // Optional: Can set to null to hide the header
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Show a confirmation dialog with the specified title, header, and content
    public static Optional<ButtonType> showConfirmationDialog(String title, String header, String content) {
        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle(title);
        confirmationAlert.setHeaderText(header);
        confirmationAlert.setContentText(content);
        confirmationAlert.getDialogPane().setPrefSize(400, 200); // Adjust width and height as needed
        return confirmationAlert.showAndWait();
    }

    // Show a text input dialog with the specified title, header, and content
    public static Optional<String> showTextInputDialog(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        return dialog.showAndWait();
    }

    // Show an alert dialog with the specified type, title, and content
    public static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static List<Map<String, String>> selectProcess(List<Map<String, String>> process) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Select process");

        BorderPane root = new BorderPane();

        Label label = new Label("Select from the list below:");
        label.setStyle("-fx-font-size: 14px;");
        root.setTop(label);

        // Create TableView
        TableView<Map<String, String>> tableView = new TableView<>();
        tableView.setEditable(false);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<Map<String, String>, String> column1 = new TableColumn<>("S.NO");
        column1.setCellValueFactory(data -> new ReadOnlyStringWrapper(Integer.toString(tableView.getItems().indexOf(data.getValue()) + 1)));
        column1.setPrefWidth(50);

        TableColumn<Map<String, String>, String> column2 = new TableColumn<>("PROCESS");
        column2.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("LAUNCHER")));
        column2.setPrefWidth(150);

        TableColumn<Map<String, String>, String> column3 = new TableColumn<>("MODIFIED TIME");
        column3.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("TIME")));
        column3.setPrefWidth(150);

        TableColumn<Map<String, String>, String> column4 = new TableColumn<>("PID");
        column4.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().get("PID")));
        column4.setPrefWidth(100);

        tableView.getColumns().addAll(column1, column2, column3, column4);

        // Create a filtered list
        FilteredList<Map<String, String>> filteredData = new FilteredList<>(FXCollections.observableArrayList(process), p -> true);
        tableView.setItems(filteredData);

        CheckBox filterCheckBox = new CheckBox("Filter local processes");
        filterCheckBox.setSelected(true);
        filterCheckBox.setOnAction(e -> {
            if (filterCheckBox.isSelected()) {
                filteredData.setPredicate(p -> "Local".equalsIgnoreCase(p.get("LAUNCHER")));
            } else {
                filteredData.setPredicate(p -> true);
            }
        });

        filteredData.setPredicate(p -> "Local".equalsIgnoreCase(p.get("LAUNCHER")));

        HBox hbox = new HBox(filterCheckBox);
        hbox.setStyle("-fx-padding: 10px;");
        root.setBottom(hbox);

        root.setCenter(tableView);

        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");
        HBox buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setStyle("-fx-alignment: center; -fx-padding: 10px;");
        root.setBottom(new VBox(hbox, buttonBox));

        okButton.setOnAction(e -> dialogStage.close());
        cancelButton.setOnAction(e -> {
            tableView.getSelectionModel().clearSelection();
            dialogStage.close();
        });

        Scene scene = new Scene(root, 500, 400);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();

        // Retrieve selected processes
        List<Map<String, String>> selectedProcesses = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());

        // Return null if Cancel was clicked
        if (selectedProcesses.isEmpty()) {
            return null;
        }

        return selectedProcesses;
    }
}
