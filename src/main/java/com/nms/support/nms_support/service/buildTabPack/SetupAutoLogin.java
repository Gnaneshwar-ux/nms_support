package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.nms.support.nms_support.service.globalPack.IconUtils;
import javafx.geometry.Insets;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class SetupAutoLogin {

    static String tempFile = "Login.xml";
    static String CommandCode = "<Include name=\"AUTO_LOGIN_COMMANDS.inc\"/>";
    static javax.swing.JTextArea send;

    public static boolean execute(ProjectEntity project, BuildAutomation buildAutomation){
        buildAutomation.appendTextToLog("=== AUTO-LOGIN SETUP PROCESS INITIATED ===");
        buildAutomation.appendTextToLog("Timestamp: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        buildAutomation.appendTextToLog("PROJECT CONFIGURATION:");
        buildAutomation.appendTextToLog("• JConfig Path: " + (project.getJconfigPathForBuild() != null ? project.getJconfigPathForBuild() : "NOT SET"));
        buildAutomation.appendTextToLog("• Executable Path: " + (project.getExePath() != null ? project.getExePath() : "NOT SET"));
        buildAutomation.appendTextToLog("• Username: " + (project.getUsername() != null ? project.getUsername() : "NOT SET"));
        buildAutomation.appendTextToLog("• Auto Login: " + (project.getAutoLogin() != null ? project.getAutoLogin() : "NOT SET"));
        buildAutomation.appendTextToLog("• System User: " + System.getProperty("user.name"));

        // Validate required configuration
        if (project.getJconfigPathForBuild() == null || project.getJconfigPathForBuild().trim().isEmpty()) {
            buildAutomation.appendTextToLog("ERROR: JConfig path configuration missing");
            buildAutomation.appendTextToLog("DETAILS: JConfig path is required for auto-login setup but is null or empty");
            buildAutomation.appendTextToLog("RESOLUTION: Configure JConfig path in Project Configuration tab under 'JConfig Path' field");
            return false;
        }

        if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
            buildAutomation.appendTextToLog("ERROR: Executable path configuration missing");
            buildAutomation.appendTextToLog("DETAILS: Executable path is required for auto-login setup but is null or empty");
            buildAutomation.appendTextToLog("RESOLUTION: Configure executable path in Project Configuration tab under 'Executable Path' field");
            return false;
        }

        try {
            buildAutomation.appendTextToLog("STEP 1: Updating Login.xml configuration file...");
            String primaryPath = project.getJconfigPathForBuild() + "\\global\\xml\\";
            String alternatePath = project.getExePath() + "\\java\\product\\global\\xml\\" + tempFile;
            
            buildAutomation.appendTextToLog("• Primary Path: " + primaryPath);
            buildAutomation.appendTextToLog("• Alternate Path: " + alternatePath);
            
            if(!updateFile(primaryPath, alternatePath, buildAutomation)){
                buildAutomation.appendTextToLog("ERROR: Login.xml update failed");
                buildAutomation.appendTextToLog("DETAILS: updateFile() method returned false");
                buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and ensure paths are accessible");
                return false;
            }
            buildAutomation.appendTextToLog("SUCCESS: Login.xml configuration updated successfully");
            
        } catch (InterruptedException e) {
            buildAutomation.appendTextToLog("ERROR: Auto-login setup process interrupted");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Stack Trace: " + e.toString());
            buildAutomation.appendTextToLog("RESOLUTION: Setup process was cancelled by user or system");
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            buildAutomation.appendTextToLog("STEP 2: Copying auto-login configuration files...");
            boolean result = copyFiles(project, buildAutomation);
            
            if (result) {
                buildAutomation.appendTextToLog("SUCCESS: Auto-login setup completed successfully");
                buildAutomation.appendTextToLog("=== AUTO-LOGIN SETUP PROCESS COMPLETED ===");
                buildAutomation.appendTextToLog("Timestamp: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } else {
                buildAutomation.appendTextToLog("ERROR: Auto-login setup failed during file copy operation");
                buildAutomation.appendTextToLog("DETAILS: copyFiles() method returned false");
                buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and ensure all required files are accessible");
            }
            
            return result;
        } catch (Exception e) {
            buildAutomation.appendTextToLog("ERROR: EXCEPTION DURING AUTO-LOGIN SETUP");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Stack Trace: " + e.toString());
            buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
            buildAutomation.appendTextToLog("• File system access permissions");
            buildAutomation.appendTextToLog("• Invalid file paths or missing directories");
            buildAutomation.appendTextToLog("• System resource limitations");
            buildAutomation.appendTextToLog("RESOLUTION: Check file permissions, verify paths, and ensure system resources are available");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

//    public static void loadUserTypes(ProjectEntity project){
//
//        HashMap<String, List<String>> l = SQLParser.parseSQLFile(project.getJconfigPathForBuild().replace("jconfig", "sql"));
//        for (Map.Entry<String, List<String>> entry : l.entrySet()) {
//            System.out.println("Key: " + entry.getKey() + " - Value: " + entry.getValue());
//        }
//        project.setTypes(l);
//    }

    public static void loadUserTypes(ProjectEntity project){
        // Show custom dialog to get multi-line SQL data from user
        String filePath = "usertypes/"+project.getName()+".sql";
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("User Type Data Input");
        dialog.setHeaderText("Enter import of env_code_1 table insert statements");
        
        // Set dialog styling and app icon
        dialog.getDialogPane().setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 16;");
        IconUtils.setStageIcon((Stage) dialog.getDialogPane().getScene().getWindow());

        // Professional typography constants
        String professionalFontFamily = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
        
        // Create main content container
        VBox mainContent = new VBox(16);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-background-color: #FFFFFF;");

        // Header section
        VBox header = new VBox(6);
        Label titleLabel = new Label("SQL Data Input");
        titleLabel.setStyle("-fx-font-family: " + professionalFontFamily + "; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1F2937; -fx-text-alignment: center;");
        Label subtitleLabel = new Label("Enter the SQL insert statements for env_code_1 table");
        subtitleLabel.setStyle("-fx-font-family: " + professionalFontFamily + "; -fx-font-size: 12px; -fx-font-weight: normal; -fx-text-fill: #6B7280; -fx-text-alignment: center;");
        header.getChildren().addAll(titleLabel, subtitleLabel);

        // TextArea with professional styling
        TextArea textArea = new TextArea(getDataIfFileExists(filePath));
        textArea.setPrefSize(480, 280);
        textArea.setStyle("""
            -fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            -fx-font-size: 12px;
            -fx-background-color: #F9FAFB;
            -fx-border-color: #D1D5DB;
            -fx-border-radius: 6;
            -fx-padding: 12;
            -fx-text-fill: #374151;
            """);
        textArea.setPromptText("Enter your SQL INSERT statements here...");

        // Instructions label
        Label instructionsLabel = new Label("Instructions: Enter one INSERT statement per line. Example:\nINSERT INTO env_code_1 (code, description) VALUES ('CODE1', 'Description 1');");
        instructionsLabel.setStyle("-fx-font-family: " + professionalFontFamily + "; -fx-font-size: 11px; -fx-text-fill: #6B7280; -fx-wrap-text: true;");
        instructionsLabel.setMaxWidth(480);

        mainContent.getChildren().addAll(header, textArea, instructionsLabel);
        dialog.getDialogPane().setContent(mainContent);

        // Add OK and Cancel buttons with professional styling
        ButtonType okButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

        // Apply professional styling to buttons
        dialog.getDialogPane().getButtonTypes().forEach(buttonType -> {
            Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
            if (button != null) {
                if (buttonType == okButtonType) {
                    button.setStyle("""
                        -fx-font-family: %s;
                        -fx-font-size: 12px;
                        -fx-font-weight: 600;
                        -fx-background-color: #3B82F6;
                        -fx-text-fill: white;
                        -fx-padding: 8 16;
                        -fx-background-radius: 6;
                        -fx-border-radius: 6;
                        -fx-cursor: hand;
                        -fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 3, 0, 0, 1);
                        """.formatted(professionalFontFamily));
                    button.setOnMouseEntered(e -> button.setStyle("""
                        -fx-font-family: %s;
                        -fx-font-size: 12px;
                        -fx-font-weight: 600;
                        -fx-background-color: #2563EB;
                        -fx-text-fill: white;
                        -fx-padding: 8 16;
                        -fx-background-radius: 6;
                        -fx-border-radius: 6;
                        -fx-cursor: hand;
                        -fx-effect: dropshadow(gaussian, rgba(37, 99, 235, 0.4), 4, 0, 0, 2);
                        """.formatted(professionalFontFamily)));
                    button.setOnMouseExited(e -> button.setStyle("""
                        -fx-font-family: %s;
                        -fx-font-size: 12px;
                        -fx-font-weight: 600;
                        -fx-background-color: #3B82F6;
                        -fx-text-fill: white;
                        -fx-padding: 8 16;
                        -fx-background-radius: 6;
                        -fx-border-radius: 6;
                        -fx-cursor: hand;
                        -fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 3, 0, 0, 1);
                        """.formatted(professionalFontFamily)));
                } else {
                    button.setStyle("""
                        -fx-font-family: %s;
                        -fx-font-size: 12px;
                        -fx-font-weight: 600;
                        -fx-background-color: #6B7280;
                        -fx-text-fill: white;
                        -fx-padding: 8 16;
                        -fx-background-radius: 6;
                        -fx-border-radius: 6;
                        -fx-cursor: hand;
                        -fx-effect: dropshadow(gaussian, rgba(107, 114, 128, 0.3), 3, 0, 0, 1);
                        """.formatted(professionalFontFamily));
                    button.setOnMouseEntered(e -> button.setStyle("""
                        -fx-font-family: %s;
                        -fx-font-size: 12px;
                        -fx-font-weight: 600;
                        -fx-background-color: #4B5563;
                        -fx-text-fill: white;
                        -fx-padding: 8 16;
                        -fx-background-radius: 6;
                        -fx-border-radius: 6;
                        -fx-cursor: hand;
                        -fx-effect: dropshadow(gaussian, rgba(75, 85, 99, 0.4), 4, 0, 0, 2);
                        """.formatted(professionalFontFamily)));
                    button.setOnMouseExited(e -> button.setStyle("""
                        -fx-font-family: %s;
                        -fx-font-size: 12px;
                        -fx-font-weight: 600;
                        -fx-background-color: #6B7280;
                        -fx-text-fill: white;
                        -fx-padding: 8 16;
                        -fx-background-radius: 6;
                        -fx-border-radius: 6;
                        -fx-cursor: hand;
                        -fx-effect: dropshadow(gaussian, rgba(107, 114, 128, 0.3), 3, 0, 0, 1);
                        """.formatted(professionalFontFamily)));
                }
            }
        });

        // Convert result to string when OK is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return textArea.getText();
            }
            return null;
        });

        // Capture the user input
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(sqlData -> {
             // specify your directory path here
            boolean status = createSQLFileIfNotExist(filePath, sqlData);
            if(!status){
                return;
            }
            HashMap<String, List<String>> l = SQLParser.parseSQLFile(filePath);
            for (Map.Entry<String, List<String>> entry : l.entrySet()) {
                System.out.println("Key: " + entry.getKey() + " - Value: " + entry.getValue());
            }
            project.setTypes(l);
            System.out.println("Process " + (status ? "passed" : "failed"));
        });
    }

    public static boolean createSQLFileIfNotExist(String filePath, String sqlData) {
        try {
            // Ensure the directory structure exists
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("Failed to create the directory structure.");
                    return false;
                }
            }

            // Create the file if it does not exist
            if (!file.exists() && !file.createNewFile()) {
                System.err.println("Failed to create the SQL file.");
                return false;
            }

            // Append the provided SQL data into the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.write(sqlData);
                writer.newLine();
            }

            System.out.println("SQL data written to file successfully.");
            return true; // Success
        } catch (IOException e) {
            e.printStackTrace();
            return false; // Failure due to an exception
        }
    }

    public static String getDataIfFileExists(String filePath) {
        File file = new File(filePath);

        // Check if the file exists
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return null;
        }

        // Read data from the file
        StringBuilder data = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                data.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return data.toString(); // Return the file contents as a string
    }


    public static boolean updateFile(String pathLogin, String altPathLogin, BuildAutomation buildAutomation) throws InterruptedException  {

        try{
            File file = new File(pathLogin+tempFile);

            if (!file.exists()) {
                buildAutomation.appendTextToLog("Login.xml is not found in project!\n");


                file  = new File(altPathLogin);

                if(!file.exists()){
                    buildAutomation.appendTextToLog("Login.xml is not found in product!\n");
                    return false;
                }

            }

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            boolean found = false;
            String text = CommandCode;
            StringBuilder res = new StringBuilder();
            while ((line = br.readLine()) != null) {
//
//			if (line.contains("windowOpened")) {
//				found = true;
//			}
//                        if(line.contains("AUTO_LOGIN_COMMANDS.inc")){
//                            return true;
//                        }
                if (line.contains("<ToolBehavior>")) {
                    res.append(line+"\n"+text+"\n");
                }
                else
                    res.append(line + "\n");
            }

            br.close();
            FileWriter fWriter = new FileWriter(tempFile);
            fWriter.write(res.toString());

            fWriter.close();

            File sourceFileLogin = new File(tempFile);
            File targetDirLogin = new File(pathLogin);

            if (!targetDirLogin.exists()) {
                boolean isCreated = targetDirLogin.mkdirs();
                if(!isCreated){
                    buildAutomation.appendTextToLog("Failed to create dir "+targetDirLogin);
                    return false;
                }
            }


            String[] command = {"cmd", "/c", "copy", tempFile, pathLogin};

            System.out.println(Arrays.toString(command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);


            Process process = processBuilder.start();

            BufferedReader reader1 = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line1;
            while ((line1 = reader1.readLine()) != null) {
                buildAutomation.appendTextToLog(line1);
            }

            int exitCode = process.waitFor();

            if(exitCode != 0){
                buildAutomation.appendTextToLog("Login.xml copy failed.");
                return false;
            }

            buildAutomation.appendTextToLog("Login.xml file copied successfully!\n");
            return true;
        }
        catch(IOException e){
            e.printStackTrace();
            buildAutomation.appendTextToLog(e+"\n");
            return false;
        }
    }

    public static boolean copyFiles(ProjectEntity project, BuildAutomation buildAutomation) throws Exception {

        // copying inc files
        try{

            File sourceFileLogin = getResourceAsTempFile("/nms_configs/AUTO_LOGIN_COMMANDS.inc");
            File targetDirLogin = new File(project.getJconfigPathForBuild() + "/global/xml/AUTO_LOGIN_COMMANDS.inc");
            File targetDir = new File(project.getJconfigPathForBuild() + "/global/xml/");
            if (!targetDir.exists()) {
                boolean isCreated = targetDir.mkdirs();
                if(!isCreated){
                    buildAutomation.appendTextToLog("Failed to create dir "+targetDir);
                    return false;
                }
            }

            Files.copy(sourceFileLogin.toPath(), targetDirLogin.toPath(), StandardCopyOption.REPLACE_EXISTING);

            //System.out.println("Inc file copied successfully!");
            buildAutomation.appendTextToLog("Inc file copied successfully!\n");

            // copying command files

            sourceFileLogin = getResourceAsTempFile("/nms_configs/LoadCredentialsExternalCommand.java");
            targetDirLogin = new File(project.getJconfigPathForBuild() + "/java/src/custom/LoadCredentialsExternalCommand.java");

            if (!targetDirLogin.exists()) {
                boolean isCreated = targetDirLogin.mkdirs();
                if(!isCreated){
                    buildAutomation.appendTextToLog("Failed to create dir "+targetDirLogin);
                    return false;
                }
            }

            Files.copy(sourceFileLogin.toPath(), targetDirLogin.toPath(), StandardCopyOption.REPLACE_EXISTING);

            //System.out.println("Java command file copied successfully!");
            buildAutomation.appendTextToLog("Java command file copied successfully!\n");
            return true;
        }
        catch(IOException e){
            e.printStackTrace();
            buildAutomation.appendTextToLog(e+"\n");
            return false;
        }
    }

    public static File getResourceAsTempFile(String resourcePath) {
        try (InputStream inputStream = SetupAutoLogin.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }

            // Create a temporary file with the same name
            String tempFileName = Paths.get(resourcePath).getFileName().toString();
            Path tempFile = Files.createTempFile(tempFileName, null);
            tempFile.toFile().deleteOnExit();

            // Copy the resource to the temporary file
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            return tempFile.toFile();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}