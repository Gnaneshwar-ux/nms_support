package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class SetupRestartTool {
    static String tempFile = "MAIN_MENUBAR_BUTTONS.inc";
    static String CommandCode = "<Include name=\"RESTART_TOOLS_COMMANDS.inc\"/>";
    static javax.swing.JTextArea send;

    public static boolean execute(ProjectEntity project, BuildAutomation buildAutomation){
        buildAutomation.appendTextToLog("=== RESTART TOOLS SETUP PROCESS INITIATED ===");
        buildAutomation.appendTextToLog("Timestamp: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        buildAutomation.appendTextToLog("PROJECT CONFIGURATION:");
        buildAutomation.appendTextToLog("• Project Folder: " + (project.getProjectFolderPath() != null ? project.getProjectFolderPath() : "NOT SET"));
        buildAutomation.appendTextToLog("• JConfig Path: " + (project.getJconfigPathForBuild() != null ? project.getJconfigPathForBuild() : "NOT SET"));
        buildAutomation.appendTextToLog("• Executable Path: " + (project.getExePath() != null ? project.getExePath() : "NOT SET"));
        buildAutomation.appendTextToLog("• Username: " + (project.getUsername() != null ? project.getUsername() : "NOT SET"));
        buildAutomation.appendTextToLog("• Auto Login: " + (project.getAutoLogin() != null ? project.getAutoLogin() : "NOT SET"));
        buildAutomation.appendTextToLog("• System User: " + System.getProperty("user.name"));

        // Validate required configuration
        if (project.getJconfigPathForBuild() == null || project.getJconfigPathForBuild().trim().isEmpty()) {
            buildAutomation.appendTextToLog("ERROR: JConfig path configuration missing");
            buildAutomation.appendTextToLog("DETAILS: JConfig path is required for restart tools setup but is null or empty");
            buildAutomation.appendTextToLog("RESOLUTION: Configure JConfig path in Project Configuration tab under 'JConfig Path' field");
            return false;
        }

        if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
            buildAutomation.appendTextToLog("ERROR: Executable path configuration missing");
            buildAutomation.appendTextToLog("DETAILS: Executable path is required for restart tools setup but is null or empty");
            buildAutomation.appendTextToLog("RESOLUTION: Configure executable path in Project Configuration tab under 'Executable Path' field");
            return false;
        }

        try {
            buildAutomation.appendTextToLog("STEP 1: Updating " + tempFile + " configuration file...");
            String primaryPath = project.getJconfigPathForBuild() + "\\global\\xml\\";
            String alternatePath = project.getExePath() + "\\java\\product\\global\\xml\\" + tempFile;
            
            buildAutomation.appendTextToLog("• Primary Path: " + primaryPath);
            buildAutomation.appendTextToLog("• Alternate Path: " + alternatePath);
            
            if(!updateFile(primaryPath, alternatePath, buildAutomation)){
                buildAutomation.appendTextToLog("ERROR: " + tempFile + " update failed");
                buildAutomation.appendTextToLog("DETAILS: updateFile() method returned false");
                buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and ensure paths are accessible");
                return false;
            }
            buildAutomation.appendTextToLog("SUCCESS: " + tempFile + " configuration updated successfully");
            
            buildAutomation.appendTextToLog("STEP 2: Updating properties file for menu items...");
            String propertiesPath = project.getExePath() + "\\java\\product\\global\\properties\\Global_en_US.properties";
            buildAutomation.appendTextToLog("• Properties File Path: " + propertiesPath);
            
            boolean resp = writeLinesIfNotExist(propertiesPath, Arrays.asList(
                    "BTN_RELOAD_TOOLS.text = Manage Tools",
                    "BTN_RELOAD_TOOLS.tooltip = Restart tools for dynamic code changes."
            ));
            
            if(!resp){
                buildAutomation.appendTextToLog("WARNING: Failed to update properties file");
                buildAutomation.appendTextToLog("DETAILS: Properties file update returned false");
                buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and ensure properties file is accessible");
            } else {
                buildAutomation.appendTextToLog("SUCCESS: Properties file updated successfully");
                buildAutomation.appendTextToLog("INFO: Added menu item properties for restart tools functionality");
            }
            
        } catch (InterruptedException e) {
            buildAutomation.appendTextToLog("ERROR: Restart tools setup process interrupted");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Stack Trace: " + e.toString());
            buildAutomation.appendTextToLog("RESOLUTION: Setup process was cancelled by user or system");
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            buildAutomation.appendTextToLog("STEP 3: Copying restart tools configuration files...");
            boolean result = copyFiles(project, buildAutomation);
            
            if (result) {
                buildAutomation.appendTextToLog("SUCCESS: Restart tools setup completed successfully");
                buildAutomation.appendTextToLog("=== RESTART TOOLS SETUP PROCESS COMPLETED ===");
                buildAutomation.appendTextToLog("Timestamp: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } else {
                buildAutomation.appendTextToLog("ERROR: Restart tools setup failed during file copy operation");
                buildAutomation.appendTextToLog("DETAILS: copyFiles() method returned false");
                buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and ensure all required files are accessible");
            }
            
            return result;
        } catch (Exception e) {
            buildAutomation.appendTextToLog("ERROR: EXCEPTION DURING RESTART TOOLS SETUP");
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


    public static boolean updateFile(String xmlPath, String altPathXml, BuildAutomation buildAutomation) throws InterruptedException  {

        try{
            File file = new File(xmlPath+tempFile);

            if (!file.exists()) {
                buildAutomation.appendTextToLog(tempFile+" is not found in project!\n");
                file  = new File(altPathXml);
                if(!file.exists()){
                    buildAutomation.appendTextToLog(tempFile+" is not found in product!\n");
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
//                if (line.contains("<PopupMenu name=\"POPUP_HELP\">")) {
//                    res.append(line+"\n"+text+"\n");
//                }
//                else
                    res.append(line + "\n");
            }
            res.append(text);
            br.close();
            FileWriter fWriter = new FileWriter(tempFile);
            fWriter.write(res.toString());

            fWriter.close();

            File sourceFileLogin = new File(tempFile);
            File targetDirLogin = new File(xmlPath);

            if (!targetDirLogin.exists()) {
                boolean isCreated = targetDirLogin.mkdirs();
                if(!isCreated){
                    buildAutomation.appendTextToLog("Failed to create dir "+targetDirLogin);
                    return false;
                }
            }

            String[] command = {"cmd", "/c", "copy", tempFile, xmlPath};

            ProcessBuilder processBuilder = new ProcessBuilder(command);


            Process process = processBuilder.start();

            BufferedReader reader1 = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line1;
            while ((line1 = reader1.readLine()) != null) {
                buildAutomation.appendTextToLog(line1);
            }

            int exitCode = process.waitFor();

            if(exitCode != 0){
                buildAutomation.appendTextToLog(tempFile+" copy failed.");
                return false;
            }

            buildAutomation.appendTextToLog(tempFile+" file copied successfully!\n");
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

            File sourceFileLogin = getResourceAsTempFile("/nms_configs/RESTART_TOOLS_COMMANDS.inc");
            File targetDirLogin = new File(project.getJconfigPathForBuild() + "/global/xml/RESTART_TOOLS_COMMANDS.inc");
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

            sourceFileLogin = getResourceAsTempFile("/nms_configs/RestartToolsCommand.java");
            targetDirLogin = new File(project.getJconfigPathForBuild() + "/java/src/custom/RestartToolsCommand.java");

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

    public static boolean writeLinesIfNotExist(String filePath, List<String> linesToWrite) {
        try {
            // Check if file exists, and if not, create it
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println(filePath+" File not exist.");
                return false;
            }

            // Read existing lines in the file
            List<String> existingLines = Files.readAllLines(Paths.get(filePath));
            Set<String> uniqueLines = new HashSet<>(existingLines);

            // Append lines that do not exist in the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                for (String line : linesToWrite) {
                    if (!uniqueLines.contains(line)) {
                        writer.write(line);
                        writer.newLine();
                        uniqueLines.add(line); // Ensure it isn't written again
                    }
                }
            }

            System.out.println("Lines written to file successfully.");
            return true; // Success
        } catch (IOException e) {
            System.out.println(e.toString());
            return false; // Failure due to an exception
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
