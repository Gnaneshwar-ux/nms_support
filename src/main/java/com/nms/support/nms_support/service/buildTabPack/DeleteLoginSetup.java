
package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import javafx.scene.control.ButtonType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.logging.Logger;

/**
 *
 * @author Gnaneshwar
 */
public class DeleteLoginSetup {
    private static final Logger logger = LoggerUtil.getLogger();
    static String tempFile = "Login.xml";
    static String CommandCode = "<Include name=\"AUTO_LOGIN_COMMANDS.inc\"/>";
    
    public static void delete(ProjectEntity project, BuildAutomation buildAutomation, boolean doConfirm) throws IOException {
        logger.info("Starting auto-login setup deletion process for project: " + project.getName());
        buildAutomation.appendTextToLog("=== AUTO-LOGIN SETUP DELETE PROCESS INITIATED ===");
        buildAutomation.appendTextToLog("Timestamp: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        if(doConfirm) {
            logger.info("Showing confirmation dialog for auto-login setup deletion");
            Optional<ButtonType> confirmation = DialogUtil.showConfirmationDialog("Confirm Delete", "Are you sure to delete auto-login setup?", "This operation will undo the changes made by this tool to set auto-login functionality.");
            if (confirmation.isEmpty() || confirmation.get() == ButtonType.CANCEL) {
                logger.info("Auto-login setup deletion cancelled by user");
                buildAutomation.appendTextToLog("INFO: Auto-login setup deletion cancelled by user");
                return; // Stop the process if the user cancels
            }
            logger.info("User confirmed auto-login setup deletion");
        }

        try{
            logger.info("STEP 1: Starting auto-login setup deletion process");
            buildAutomation.appendTextToLog("STEP 1: Starting auto-login setup deletion process...");

            if (project.getJconfigPathForBuild() == null || project.getJconfigPathForBuild().trim().isEmpty()) {
                logger.severe("JConfig path configuration missing for project: " + project.getName());
                buildAutomation.appendTextToLog("ERROR: JConfig path configuration missing");
                buildAutomation.appendTextToLog("DETAILS: Cannot proceed with deletion without valid JConfig path");
                buildAutomation.appendTextToLog("RESOLUTION: Configure JConfig path in Project Configuration tab");
                return;
            }

            String pathJconfig = project.getJconfigPathForBuild();
            String loginPath = pathJconfig + "\\global\\xml\\" + tempFile;
            logger.info("JConfig path: " + pathJconfig + ", Login.xml path: " + loginPath);
            
            File tempfile = new File(tempFile);
            FileReader r = new FileReader(loginPath);
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempfile));

            BufferedReader reader = new BufferedReader(r);

            String line;
            int linesProcessed = 0;
            int linesRemoved = 0;

            logger.info("STEP 2: Processing Login.xml file");
            buildAutomation.appendTextToLog("STEP 2: Processing Login.xml file...");
            while ((line = reader.readLine()) != null) {
                linesProcessed++;
                if (!line.contains(CommandCode)) {
                    // If the line doesn't contain the specified text, write it to the temporary file
                    writer.write(line);
                    writer.newLine();
                } else {
                    linesRemoved++;
                    logger.info("Removed auto-login include line: " + line.trim());
                }
            }

            r.close();
            reader.close();
            writer.close();

            logger.info("STEP 3: File processing completed - Lines processed: " + linesProcessed + ", Lines removed: " + linesRemoved);
            buildAutomation.appendTextToLog("STEP 3: File processing completed");

            File sourceFileLogin = new File(tempFile);
            String[] command = {"cmd", "/c", "copy", tempFile, loginPath};

            logger.info("STEP 4: Restoring original Login.xml file");
            buildAutomation.appendTextToLog("STEP 4: Restoring original Login.xml file...");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            BufferedReader reader1 = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line1;
            while ((line1 = reader1.readLine()) != null) {
                // Suppress verbose output during cleanup but log it
                logger.fine("Copy command output: " + line1);
            }

            int exitCode = process.waitFor();
            logger.info("Copy command completed with exit code: " + exitCode);

            if(exitCode == 0){
                logger.info("SUCCESS: Login.xml rollback completed successfully");
                buildAutomation.appendTextToLog("SUCCESS: Login.xml rollback completed successfully");
            } else {
                logger.severe("ERROR: Login.xml rollback failed with exit code: " + exitCode);
                buildAutomation.appendTextToLog("ERROR: Login.xml rollback failed");
                buildAutomation.appendTextToLog("DETAILS: Copy command returned exit code: " + exitCode);
                buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and ensure Login.xml is not locked");
            }

            logger.info("STEP 5: Removing auto-login configuration files");
            buildAutomation.appendTextToLog("STEP 5: Removing auto-login configuration files...");
            
            // Delete AUTO_LOGIN_COMMANDS.inc file
            File targetDirLogin = new File(pathJconfig + "\\global\\xml\\AUTO_LOGIN_COMMANDS.inc");
            logger.info("Attempting to delete AUTO_LOGIN_COMMANDS.inc from: " + targetDirLogin.getAbsolutePath());
            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted) {
                    logger.info("SUCCESS: Deleted AUTO_LOGIN_COMMANDS.inc");
                    buildAutomation.appendTextToLog("SUCCESS: Deleted AUTO_LOGIN_COMMANDS.inc");
                } else {
                    logger.severe("ERROR: Failed to delete AUTO_LOGIN_COMMANDS.inc");
                    buildAutomation.appendTextToLog("ERROR: Failed to delete AUTO_LOGIN_COMMANDS.inc");
                }
            } else {
                logger.info("AUTO_LOGIN_COMMANDS.inc file not found (may have been already deleted)");
            }

            // Delete LoadCredentialsExternalCommand.java file
            targetDirLogin = new File(pathJconfig + "\\java\\src\\custom\\LoadCredentialsExternalCommand.java");
            logger.info("Attempting to delete LoadCredentialsExternalCommand.java from: " + targetDirLogin.getAbsolutePath());
            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted) {
                    logger.info("SUCCESS: Deleted LoadCredentialsExternalCommand.java");
                    buildAutomation.appendTextToLog("SUCCESS: Deleted LoadCredentialsExternalCommand.java");
                } else {
                    logger.severe("ERROR: Failed to delete LoadCredentialsExternalCommand.java");
                    buildAutomation.appendTextToLog("ERROR: Failed to delete LoadCredentialsExternalCommand.java");
                }
            } else {
                logger.info("LoadCredentialsExternalCommand.java file not found (may have been already deleted)");
            }

            logger.info("STEP 6: Cleaning up temporary files");
            buildAutomation.appendTextToLog("STEP 6: Cleaning up temporary files...");
            if (tempfile.exists()) {
                boolean tempDeleted = tempfile.delete();
                if (tempDeleted) {
                    logger.info("SUCCESS: Cleaned up temporary file: " + tempfile.getAbsolutePath());
                    buildAutomation.appendTextToLog("SUCCESS: Cleaned up temporary file");
                } else {
                    logger.warning("WARNING: Failed to clean up temporary file: " + tempfile.getAbsolutePath());
                    buildAutomation.appendTextToLog("WARNING: Failed to clean up temporary file");
                }
            } else {
                logger.info("Temporary file does not exist: " + tempfile.getAbsolutePath());
            }

            logger.info("SUCCESS: Auto-login setup deletion completed successfully for project: " + project.getName());
            buildAutomation.appendTextToLog("SUCCESS: Auto-login setup deletion completed successfully");

        } catch(Exception e){
            logger.severe("ERROR: AUTO-LOGIN SETUP DELETE PROCESS FAILED for project: " + project.getName());
            logger.severe("Exception Type: " + e.getClass().getSimpleName());
            logger.severe("Error Message: " + e.getMessage());
            LoggerUtil.error(e);
            
            buildAutomation.appendTextToLog("ERROR: AUTO-LOGIN SETUP DELETE PROCESS FAILED");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Stack Trace: " + e.toString());
            buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
            buildAutomation.appendTextToLog("• File system access permissions");
            buildAutomation.appendTextToLog("• Files locked by other processes");
            buildAutomation.appendTextToLog("• Invalid file paths or missing files");
            buildAutomation.appendTextToLog("RESOLUTION: Check file permissions, ensure files are not in use, and verify file paths");
        }
    }

}
