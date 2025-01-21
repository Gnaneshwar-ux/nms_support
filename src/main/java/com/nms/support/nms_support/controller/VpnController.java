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
                    LoggerUtil.getLogger().info("searched for vpncli.exe");
                    if(vpncliPath == null){
                        DialogUtil.showError("Error","Cannot find vpncli.exe file");
                        closeDialog();
                        return null;
                    }
                } else {
                    vpncliPath = vpncliPathCache; // Use cached result
                    LoggerUtil.getLogger().info("Loaded from Cache vpncli.exe");
                }

                killVpnTasks();

                LoggerUtil.getLogger().info(vpncliPath);
                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();

                showLoadingOverlay(false);
            }

            @Override
            protected void failed() {
                super.failed();

                showLoadingOverlay(false);
            }
        };

        // Start the task in a new thread
        new Thread(loadDataTask).start();
    }


    private void setStatus(){
        while (true){
            String status = getConnectionStatus();
            if(status.contains("Reconnecting")){
                setVpnStatus(status);
                setDelay(1000);
                continue;
            }
            setVpnStatus(status);
            break;
        }
    }
    private void setStatusRunning(){
            statusLabel.setStyle("-fx-text-fill: brown;");
            statusLabel.setText("Status: Running...");
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

        showLoadingOverlay(true);
        saveVpn();
        // Simulate a delay or loading process
        new Thread(() -> {
            try {
                VpnDetails vpn = mainController.vpnManager.getVpnDetails();
                killVpnTasks();
                createVpnCredsFile(vpn.getUsername(),vpn.getPassword(), hostComboBox.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                showLoadingOverlay(false);
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
        // Create a Task to run vpnDisconnect() on a separate thread
        Task<Void> disconnectTask = new Task<>() {
            @Override
            protected Void call() {
                killVpnTasks();
                vpnDisconnect();
                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();

                showLoadingOverlay(false);
            }

            @Override
            protected void failed() {
                super.failed();

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
        Platform.runLater(() -> {

            if (isLoading) {
                setStatusRunning();
            } else {
                setStatus();
            }
            loadingOverlay.setVisible(isLoading);
            usernameField.setDisable(isLoading);
            passwordField.setDisable(isLoading);
            hostComboBox.setDisable(isLoading);
            connectButton.setDisable(isLoading);
            disconnectButton.setDisable(isLoading);

        });
        setDelay(50);
    }

    public void setVpnStatus(String status){
        if (status.contains("Connected")){
            statusLabel.setStyle("-fx-text-fill: green;");
        }
        else if (status.contains("Reconnecting") || status.contains("Connecting")){
            statusLabel.setStyle("-fx-text-fill: blue;");
        }
        else {
            statusLabel.setStyle("-fx-text-fill: red;");
        }
        statusLabel.setText("Status: "+status);
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
            String prevLine = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if(forState && line.contains("VPN")){
                        commandStatus = true;
                        process.destroy();
                        killVpnTasks();
                        while (process.isAlive());
                        return output.toString();
                    }
                    if(line.contains("error:")){
                        commandStatus = false;
                        process.destroy();
                        killVpnTasks();
                        while (process.isAlive());
                        return line;
                    }
                    if(line.contains("Login failed")){
                        commandStatus = false;
                        process.destroy();
                        killVpnTasks();
                        while (process.isAlive());
                        return line + " Please check username and password.";
                    }
                    prevLine = line;
                    LoggerUtil.getLogger().info(line);
                    output.append(line).append(System.lineSeparator());
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

        LoggerUtil.getLogger().info(output.toString());
        return "Unknown Error Occurred see logs";
    }

    private String getConnectionStatus(){
        String resp = executeCommand(vpncliPath+"\\vpncli.exe state");
        LoggerUtil.getLogger().info(resp);
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
                LoggerUtil.getLogger().info("vpncreds.txt created at: " + vpnCredsFile.getAbsolutePath());
            }

            // Execute the VPN connection command
            executeVpnCommand(vpnCredsFile.getAbsolutePath());

        } catch (IOException e) {
            LoggerUtil.getLogger().severe("Error creating vpncreds.txt: " + e.getMessage());
        }
    }

    public void executeVpnCommand(String vpnCredsFilePath) {
        vpnDisconnect();
        String command = "cmd.exe /c \"\"" + vpncliPath + "\\vpncli.exe\" -s < \"" + vpnCredsFilePath.replace("\\", "/") + "\"\"";

        try {
            // Execute the command
            LoggerUtil.getLogger().info("Executing command: " + command);
            String resp = executeCommand(command);
            LoggerUtil.getLogger().info(resp);

            if(!commandStatus){
                DialogUtil.showError("Error VPN Connection",resp);
            }
            // After execution, delete the temporary vpncreds.txt file
            //deleteVpnCredsFile(vpnCredsFilePath);
        } catch (Exception e) {
            LoggerUtil.getLogger().info("Error executing VPN command: " + e.getMessage());
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
                    LoggerUtil.getLogger().info("vpncreds.txt deleted from: " + filePath);
                } else {
                    LoggerUtil.getLogger().info("Failed to delete vpncreds.txt");
                }
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().info("Error deleting vpncreds.txt: " + e.getMessage());
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

    public void killVpnTasks(){
        killTask("csc_ui.exe");
        killTask("vpncli.exe");
    }

    public static boolean killTask(String exeName) {
        try {
            // Execute the taskkill command for the given exe name
            String command = "taskkill /IM " + exeName + " /F";
            Process process = Runtime.getRuntime().exec(command);

            // Capture and display the output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LoggerUtil.getLogger().info(line);
                }
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LoggerUtil.getLogger().severe("Error killing task: " + e.getMessage());
            return false;
        }
    }
}
