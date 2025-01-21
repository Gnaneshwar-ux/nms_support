package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

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

        buildAutomation.appendTextToLog("jconfig path : "+project.getJconfigPath()+"\n");
        buildAutomation.appendTextToLog(".exe path : "+project.getExePath()+"\n");
        buildAutomation.appendTextToLog("Username : "+project.getUsername()+"\n");
        buildAutomation.appendTextToLog("Autologin : "+project.getAutoLogin()+"\n");

        String user = System.getProperty("user.name");

        buildAutomation.appendTextToLog("System user : "+user+"\n");

        //String propPath = "C:/Users/" + user + "/Documents";

        try {
            if(!updateFile(project.getJconfigPath() + "\\global\\xml\\", project.getExePath()+"\\java\\product\\global\\xml\\"+tempFile, buildAutomation)){
                buildAutomation.appendTextToLog("Update file failed");
                System.out.println("Output 1");
                return false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


        //System.out.println(SQLParser.parseSQLFile(project.getJconfigPath().replace("jconfig", "sql"), "crew"));

        //loadUserTypes(project);

        try {
            return copyFiles(project,buildAutomation);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


    }

//    public static void loadUserTypes(ProjectEntity project){
//
//        HashMap<String, List<String>> l = SQLParser.parseSQLFile(project.getJconfigPath().replace("jconfig", "sql"));
//        for (Map.Entry<String, List<String>> entry : l.entrySet()) {
//            System.out.println("Key: " + entry.getKey() + " - Value: " + entry.getValue());
//        }
//        project.setTypes(l);
//    }

    public static void loadUserTypes(ProjectEntity project){
        // Show custom dialog to get multi-line SQL data from user
        String filePath = "usertypes/"+project.getName()+".sql";
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("User type data input");
        dialog.setHeaderText("Enter import of env_code_1 table insert statements");

        // Set the dialog pane and TextArea
        TextArea textArea = new TextArea(getDataIfFileExists(filePath));
        textArea.setPrefSize(400, 300); // Adjust size as needed for more input space

        // Place TextArea in VBox to manage layout
        VBox vbox = new VBox(textArea);
        dialog.getDialogPane().setContent(vbox);

        // Add OK and Cancel buttons
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

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
            File targetDirLogin = new File(project.getJconfigPath() + "/global/xml/AUTO_LOGIN_COMMANDS.inc");
            File targetDir = new File(project.getJconfigPath() + "/global/xml/");
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
            targetDirLogin = new File(project.getJconfigPath() + "/java/src/custom/LoadCredentialsExternalCommand.java");

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