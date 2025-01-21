package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class DialogUtil {

    private static void setAlertIcon(Alert a){
        IconUtils.setStageIcon((Stage)a.getDialogPane().getScene().getWindow());
    }
    // Show an error dialog with the specified title and message
    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            setAlertIcon(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            int height = 150;
            int count = 0;
            for (String line : message.split("\n")) {
                count += (int) Math.ceil(line.length() / 64.0);
            }
            if (count > 3) {
                height += (count - 3) * 20;
            }
            //System.out.println(height);
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
            alert.setHeaderText(null); // Optional: Can set to null to hide the header
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Show a confirmation dialog with the specified title, header, and content
    public static Optional<ButtonType> showConfirmationDialog(String title, String header, String content) {
        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        setAlertIcon(confirmationAlert);
        confirmationAlert.setTitle(title);
        confirmationAlert.setHeaderText(header);
        confirmationAlert.setContentText(content);
        confirmationAlert.getDialogPane().setPrefSize(400, 200); // Adjust width and height as needed
        return confirmationAlert.showAndWait();
    }

    // Show a text input dialog with the specified title, header, and content
    public static CompletableFuture<Optional<String>> showTextInputDialog(String title, String header, String content) {
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog();
            IconUtils.setStageIcon((Stage)dialog.getDialogPane().getScene().getWindow());
            dialog.setTitle(title);
            dialog.setHeaderText(header);
            dialog.setContentText(content);

            Optional<String> result = dialog.showAndWait();
            future.complete(result);
        });

        return future;
    }

    // Show an alert dialog with the specified type, title, and content
    public static void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            setAlertIcon(alert);
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

    public static CompletableFuture<Optional<String[]>> showTwoInputDialog(String title, String message1, String message2, String desc1, String desc2) {
        CompletableFuture<Optional<String[]>> future = new CompletableFuture<>();

        // Ensure that the dialog runs on the JavaFX application thread
        Platform.runLater(() -> {
            // Create the dialog
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle(title);
            IconUtils.setStageIcon((Stage) dialog.getDialogPane().getScene().getWindow());
            // Set the button types
            ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

            // Create the text fields
            TextField field1 = new TextField();
            field1.prefWidth(100);
            TextField field2 = new TextField();
            field2.prefWidth(100);

            // Create a grid pane and add the fields with labels
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);

            grid.add(new Label(message1), 0, 0);
            grid.add(field1, 1, 0);
            grid.add(new Label(desc1),1,1);
            grid.add(new Label(message2), 0, 2);
            grid.add(field2, 1, 2);
            grid.add(new Label(desc2),1,3);

            dialog.getDialogPane().setContent(grid);

            // Request focus on the first field by default
            Platform.runLater(() -> field1.requestFocus());

            // Convert the result to a String array when the OK button is clicked
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == okButtonType) {
                    return new String[]{field1.getText(), field2.getText()};
                }
                return null;
            });

            // Show the dialog and handle the result
            Optional<String[]> result = dialog.showAndWait();
            future.complete(result);
        });

        return future;
    }
}
