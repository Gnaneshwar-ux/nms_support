package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.VpnDetails;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class VpnController {
    public PasswordField passwordField;
    public ComboBox<String> hostComboBox;
    public Label statusLabel;
    public Button connectButton;
    public Button disconnectButton;
    private MainController mainController;
    public TextField usernameField;

    private static boolean commandStatus=false;

    private String vpncliPath;
    private static String vpncliPathCache = null;  // Cache for vpncli.exe path

    @FXML
    private StackPane loadingOverlay;  // Loading overlay for animation

    @FXML
    public void loadData() {
        // Show loading overlay and set initial status before starting the task
        showLoadingOverlay(true);
        setStatusRunning();

        // Create a Task to perform data loading in the background
        Task<Void> loadDataTask = new Task<>() {
            @Override
            protected Void call() {
                // Retrieve VPN details from the manager
                VpnDetails vpn = mainController.vpnManager.getVpnDetails();

                // Update UI fields on the JavaFX Application Thread
                Platform.runLater(() -> {
                    usernameField.setText(vpn.getUsername());
                    passwordField.setText(vpn.getPassword());

                    // Convert regular List to ObservableList and set in ComboBox
                    ObservableList<String> hosts = FXCollections.observableArrayList(vpn.getHosts());
                    hostComboBox.setItems(hosts);

                    // Select the previously used host, if any
                    String previousHost = vpn.getPrevHost();
                    if (previousHost != null && !previousHost.isEmpty()) {
                        hostComboBox.setValue(previousHost);
                    }
                });

                // Check if the vpncliPath is cached
                if (vpncliPathCache == null) {
                    vpncliPath = findVpnCliPath();
                    vpncliPathCache = vpncliPath; // Cache the result
                    System.out.println("searched for vpncli.exe");
                } else {
                    vpncliPath = vpncliPathCache; // Use cached result
                    System.out.println("Loaded from Cache vpncli.exe");
                }

                System.out.println(vpncliPath);
                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                // After loading data, update status and hide overlay on the JavaFX Application Thread
                setStatus();
                showLoadingOverlay(false);
            }

            @Override
            protected void failed() {
                super.failed();
                // Handle any error that occurred during loadData execution
                setStatus();  // Optional method to handle errors
                showLoadingOverlay(false);
            }
        };

        // Start the task in a new thread
        new Thread(loadDataTask).start();
    }


    private void setStatus(){
        Platform.runLater(() -> setVpnStatus(getConnectionStatus().contains("Connected")));
        setDelay(50);
    }
    private void setStatusRunning(){

        Platform.runLater(() ->{statusLabel.setStyle("-fx-text-fill: brown;");
            statusLabel.setText("Status: Running...");}
        );
        setDelay(50);
    }
    private void setDelay(int milliseconds){
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleConnectAction(ActionEvent actionEvent) {
        // Show loading overlay and disable input fields
        setStatusRunning();
        showLoadingOverlay(true);
        saveVpn();

        // Simulate a delay or loading process
        new Thread(() -> {
            try {
                VpnDetails vpn = mainController.vpnManager.getVpnDetails();
                createVpnCredsFile(vpn.getUsername(),vpn.getPassword(), hostComboBox.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                setStatus();
                Platform.runLater(() -> showLoadingOverlay(false));
            }
        }).start();
    }

    public void saveVpn() {
        VpnDetails vpn = mainController.vpnManager.getVpnDetails();
        vpn.setUsername(usernameField.getText());
        vpn.setPassword(passwordField.getText());
        vpn.setPrevHost(hostComboBox.getValue());
        mainController.vpnManager.addHost(hostComboBox.getValue());
        mainController.vpnManager.saveData();
    }

    public void setMainController(MainController main) {
        this.mainController = main;
        // Set the main controller asynchronously to allow the UI to load first
        new Thread(() -> {
            // Once setMainController finishes, you can load the data asynchronously
            Platform.runLater(this::loadData);
        }).start();
    }

    public void handleDisconnectAction(ActionEvent actionEvent) {
        // Show loading overlay and set initial status before starting the task
        showLoadingOverlay(true);
        setStatusRunning();

        // Create a Task to run vpnDisconnect() on a separate thread
        Task<Void> disconnectTask = new Task<>() {
            @Override
            protected Void call() {
                vpnDisconnect();
                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                // After disconnect, update status and hide overlay on the JavaFX Application Thread
                setStatus();
                showLoadingOverlay(false);
            }

            @Override
            protected void failed() {
                super.failed();
                // Handle any error that occurred during vpnDisconnect execution
                setStatus();  // Optional method to handle errors
                showLoadingOverlay(false);
            }
        };

        // Start the task in a new thread
        new Thread(disconnectTask).start();
    }


    public void closeDialog() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }

    // Method to control the loading overlay visibility
    private void showLoadingOverlay(boolean isLoading) {
        loadingOverlay.setVisible(isLoading);
        usernameField.setDisable(isLoading);
        passwordField.setDisable(isLoading);
        hostComboBox.setDisable(isLoading);
        connectButton.setDisable(isLoading);
        disconnectButton.setDisable(isLoading);
    }

    public void setVpnStatus(boolean status){
        if (status){
            statusLabel.setText("Status: Connected.");
            statusLabel.setStyle("-fx-text-fill: green;");
        } else {
            statusLabel.setText("Status: Disconnected.");
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    public static String findVpnCliPath() {
        String[] searchDirectories = {
                "C:\\Program Files (x86)\\Cisco",
                "C:\\Program Files\\Cisco"
        };

        for (String dir : searchDirectories) {
            File baseDir = new File(dir);

            // Check if directory exists before searching
            if (baseDir.exists() && baseDir.isDirectory()) {
                String result = searchForVpnCli(baseDir);
                if (result != null) {
                    return result; // Return immediately on finding the file
                }
            }
        }
        return null; // If no matching path is found
    }

    private static String searchForVpnCli(File dir) {
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursive search in subdirectories
                    String result = searchForVpnCli(file);
                    if (result != null) {
                        return result;
                    }
                } else if (file.isFile() && file.getName().equalsIgnoreCase("vpncli.exe")) {
                    // Return the parent directory path, excluding the file name
                    return file.getParent();
                }
            }
        }
        return null; // Return null if not found in this branch
    }

    public String executeCommand(String command) {
        commandStatus = false;
        StringBuilder output = new StringBuilder();

        try {
            // Start the process with the provided command
            Process process = Runtime.getRuntime().exec(command);

            // Capture standard output
            boolean forState = command.contains("state");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if(forState && line.contains("VPN")){
                        return output.toString();
                    }
                    LoggerUtil.getLogger().info(line);
                    output.append(line).append(System.lineSeparator());
                    Thread.sleep(20);
                }
            }

            // Capture error output
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {

                    output.append("ERROR: ").append(line).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(3, TimeUnit.MINUTES); // Timeout after 3 minutes
            if (!finished) {
                process.destroy();  // If the process didn't finish within the timeout, destroy it
                output.append("Process timed out after 3 minutes.").append(System.lineSeparator());
                commandStatus = false;
            } else {
                int exitCode = process.exitValue();
                commandStatus = exitCode == 0;
                output.append("Exit Code: ").append(exitCode).append(System.lineSeparator());
            }
        } catch (IOException | InterruptedException e) {
            output.append("Exception: ").append(e.getMessage()).append(System.lineSeparator());
            commandStatus = false;
        }

        return output.toString();
    }

    private String getConnectionStatus(){
        String resp = executeCommand(vpncliPath+"\\vpncli.exe state");

        String[] lines = resp.split(System.lineSeparator());

        String lastState = "State not found"; // Default value if no state is found

        // Iterate through each line and check for the "state:" keyword
        for (String line : lines) {
            if (line.toLowerCase().contains("state:")) { // Case-insensitive search
                lastState = line.split(":")[1].trim(); // Extract the state and update lastState
            }
        }

        return lastState;
    }

    public void createVpnCredsFile(String user, String password, String host) {
        // Get the full path to the Temp directory
        String tempDir = System.getenv("TEMP");

        // Create the vpncreds.txt file in the Temp folder
        File vpnCredsFile = new File(tempDir, "vpncreds.txt");

        try {
            // Ensure the file doesn't exist before creating it
            if (vpnCredsFile.exists()) {
                vpnCredsFile.delete();
            }
            vpnCredsFile.createNewFile();

            try (FileWriter writer = new FileWriter(vpnCredsFile)) {
                // Write the credentials to the file in the required format
                writer.write("connect " + host + "\n");
                writer.write(user + "\n");
                writer.write(password + "\n");
//                writer.write("exit" + "\n");
                System.out.println("vpncreds.txt created at: " + vpnCredsFile.getAbsolutePath());
            }

            // Execute the VPN connection command
            executeVpnCommand(vpnCredsFile.getAbsolutePath());

        } catch (IOException e) {
            System.out.println("Error creating vpncreds.txt: " + e.getMessage());
        }
    }

    public void executeVpnCommand(String vpnCredsFilePath) {
        vpnDisconnect();
        String command = "cmd.exe /c \"\"" + vpncliPath + "\\vpncli.exe\" -s < \"" + vpnCredsFilePath.replace("\\", "/") + "\"\"";

        try {
            // Execute the command
            System.out.println("Executing command: " + command);
            String resp = executeCommand(command);
            //System.out.println(resp);
            if(!commandStatus){
                DialogUtil.showError("Error vpn command",resp);
            }
            // After execution, delete the temporary vpncreds.txt file
            deleteVpnCredsFile(vpnCredsFilePath);
        } catch (Exception e) {
            System.out.println("Error executing VPN command: " + e.getMessage());
            DialogUtil.showError("Error vpn command",e.getMessage());
        }
    }

    public static void deleteVpnCredsFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                // Delete the file after command execution
                boolean deleted = file.delete();
                if (deleted) {
                    System.out.println("vpncreds.txt deleted from: " + filePath);
                } else {
                    System.out.println("Failed to delete vpncreds.txt");
                }
            }
        } catch (Exception e) {
            System.out.println("Error deleting vpncreds.txt: " + e.getMessage());
        }
    }

    private void vpnDisconnect(){
        executeCommand(vpncliPath+"\\vpncli.exe disconnect");
    }

    @FXML
    public void handleEnterKey(KeyEvent event) {
        // Check if the Enter key is pressed
        if (event.getCode() == KeyCode.ENTER) {
            handleConnectAction(new ActionEvent());  // Trigger the connect action
        }
    }
}
