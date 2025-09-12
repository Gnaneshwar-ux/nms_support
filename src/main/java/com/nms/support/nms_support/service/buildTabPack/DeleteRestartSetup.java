package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import javafx.scene.control.ButtonType;

import java.io.*;
import java.util.Optional;
import java.util.logging.Logger;

public class DeleteRestartSetup {
    private static final Logger logger = LoggerUtil.getLogger();
    static String tempFile = "MAIN_MENUBAR_BUTTONS.inc";
    static String CommandCode = "<Include name=\"RESTART_TOOLS_COMMANDS.inc\"/>";
    
    public static void delete(ProjectEntity project, BuildAutomation buildAutomation, boolean doConfirm) throws IOException {
        logger.info("Starting restart tools setup deletion process for project: " + project.getName());

        if(doConfirm) {
            logger.info("Showing confirmation dialog for restart tools setup deletion");
            Optional<ButtonType> confirmation = DialogUtil.showConfirmationDialog("Confirm Delete", "Are you sure to delete restart tools setup?", "This operation undo the changes made by this tool to add restart tool config.");
            if (confirmation.isEmpty() || confirmation.get() == ButtonType.CANCEL) {
                logger.info("Restart tools setup deletion cancelled by user");
                buildAutomation.appendTextToLog("Delete Restart tools process canceled.");
                return; // Stop the process if the user cancels
            }
            logger.info("User confirmed restart tools setup deletion");
        }

        try{
            logger.info("Delete Restart tools process started");
            buildAutomation.appendTextToLog("Delete Restart tools process started ... ");

            String pathJconfig = project.getJconfigPathForBuild();
            String loginPath = pathJconfig +"\\global\\xml\\" + tempFile;
            logger.info("JConfig path: " + pathJconfig + ", MAIN_MENUBAR_BUTTONS.inc path: " + loginPath);
            
            File tempfile = new File(tempFile);
            FileReader r = new FileReader(loginPath);
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempfile));

            BufferedReader reader = new BufferedReader(r);

            String line;
            int linesProcessed = 0;
            int linesRemoved = 0;

            logger.info("Processing MAIN_MENUBAR_BUTTONS.inc file to remove restart tools configuration");
            while ((line = reader.readLine()) != null) {
                linesProcessed++;
                if (!line.contains(CommandCode)) {
                    // If the line doesn't contain the specified text, write it to the temporary file
                    writer.write(line);
                    writer.newLine();
                } else {
                    linesRemoved++;
                    logger.info("Removed restart tools include line: " + line.trim());
                }
            }

            r.close();
            reader.close();
            writer.close();

            logger.info("File processing completed - Lines processed: " + linesProcessed + ", Lines removed: " + linesRemoved);
            buildAutomation.appendTextToLog("Removed changes in WorkSpaceMenuBarTool.xml ... ");

            File sourceFileLogin = new File(tempFile);
            String[] command = {"cmd", "/c", "copy", tempFile, loginPath};

            logger.info("Restoring original MAIN_MENUBAR_BUTTONS.inc file");
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
                logger.info("SUCCESS: MAIN_MENUBAR_BUTTONS.inc rollback completed successfully");
                buildAutomation.appendTextToLog("MAIN_MENUBAR_BUTTONS.inc rollback done.");
            }
            else{
                logger.severe("ERROR: MAIN_MENUBAR_BUTTONS.inc rollback failed with exit code: " + exitCode);
                buildAutomation.appendTextToLog("MAIN_MENUBAR_BUTTONS.xml rollback failed.");
            }


            logger.info("Removing restart tools configuration files");
            
            // Delete RESTART_TOOLS_COMMANDS.inc file
            File targetDirLogin = new File(pathJconfig + "\\global\\xml\\RESTART_TOOLS_COMMANDS.inc");
            logger.info("Attempting to delete RESTART_TOOLS_COMMANDS.inc from: " + targetDirLogin.getAbsolutePath());

            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted) {
                    logger.info("SUCCESS: Deleted RESTART_TOOLS_COMMANDS.inc");
                    buildAutomation.appendTextToLog("Succesfully deleted RESTART_TOOLS_COMMANDS.inc");
                } else {
                    logger.severe("ERROR: Failed to delete RESTART_TOOLS_COMMANDS.inc");
                    buildAutomation.appendTextToLog("Failed to delete RESTART_TOOLS_COMMANDS.inc");
                }
            } else {
                logger.info("RESTART_TOOLS_COMMANDS.inc file not found (may have been already deleted)");
            }

            // Delete RestartToolsCommand.java file
            targetDirLogin = new File(pathJconfig + "\\java\\src\\custom\\RestartToolsCommand.java");
            logger.info("Attempting to delete RestartToolsCommand.java from: " + targetDirLogin.getAbsolutePath());

            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted) {
                    logger.info("SUCCESS: Deleted RestartToolsCommand.java");
                    buildAutomation.appendTextToLog("Successfully deleted RestartToolsCommand.java");
                } else {
                    logger.severe("ERROR: Failed to delete RestartToolsCommand.java");
                    buildAutomation.appendTextToLog("Failed to delete RestartToolsCommand.java ... ");
                }
            } else {
                logger.info("RestartToolsCommand.java file not found (may have been already deleted)");
            }

            logger.info("SUCCESS: Restart tools setup deletion completed successfully for project: " + project.getName());
            buildAutomation.appendTextToLog("Delete process completed.");

        }
        catch(Exception e){
            logger.severe("ERROR: RESTART TOOLS SETUP DELETE PROCESS FAILED for project: " + project.getName());
            logger.severe("Exception Type: " + e.getClass().getSimpleName());
            logger.severe("Error Message: " + e.getMessage());
            LoggerUtil.error(e);
            
            buildAutomation.appendTextToLog("Delete Restart tools Setup Process Failed.");
        }

    }
}
