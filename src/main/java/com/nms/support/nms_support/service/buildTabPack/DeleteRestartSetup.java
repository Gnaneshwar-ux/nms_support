package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import javafx.scene.control.ButtonType;

import java.io.*;
import java.util.Optional;

public class DeleteRestartSetup {
    static String tempFile = "MAIN_MENUBAR_BUTTONS.inc";
    static String CommandCode = "<Include name=\"RESTART_TOOLS_COMMANDS.inc\"/>";
    public static void delete(ProjectEntity project, BuildAutomation buildAutomation, boolean doConfirm) throws IOException {

        if(doConfirm) {
            Optional<ButtonType> confirmation = DialogUtil.showConfirmationDialog("Confirm Delete", "Are you sure to delete restart tools setup?", "This operation undo the changes made by this tool to add restart tool config.");
            if (confirmation.isEmpty() || confirmation.get() == ButtonType.CANCEL) {
                buildAutomation.appendTextToLog("Delete Restart tools process canceled.");
                return; // Stop the process if the user cancels
            }
        }

        try{

            buildAutomation.appendTextToLog("Delete Restart tools process started ... ");

//            if(!Validation.validateRestartToolsSetup(project)){
//                buildAutomation.appendTextToLog("Delete already completed or setup not performed ");
//                return;
//            }
            String pathJconfig = project.getJconfigPath();
            String loginPath = pathJconfig +"\\global\\xml\\" + tempFile;
            File tempfile = new File(tempFile);
            FileReader r = new FileReader(loginPath);
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempfile));

            BufferedReader reader = new BufferedReader(r);

            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.contains(CommandCode)) {
                    // If the line doesn't contain the specified text, write it to the temporary file

                    writer.write(line);
                    writer.newLine();
                }
            }

            r.close();
            reader.close();
            writer.close();

            buildAutomation.appendTextToLog("Removed changes in WorkSpaceMenuBarTool.xml ... ");

            File sourceFileLogin = new File(tempFile);


            String[] command = {"cmd", "/c", "copy", tempFile, loginPath};

            ProcessBuilder processBuilder = new ProcessBuilder(command);


            Process process = processBuilder.start();

            BufferedReader reader1 = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line1;
            while ((line1 = reader1.readLine()) != null) {
                buildAutomation.appendTextToLog(line1);
            }

            int exitCode = process.waitFor();

            if(exitCode == 0){
                buildAutomation.appendTextToLog("MAIN_MENUBAR_BUTTONS.inc rollback done.");
            }
            else{
                buildAutomation.appendTextToLog("MAIN_MENUBAR_BUTTONS.xml rollback failed.");
            }


            File targetDirLogin = new File(pathJconfig + "\\global\\xml\\RESTART_TOOLS_COMMANDS.inc");

            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted)
                    buildAutomation.appendTextToLog("Succesfully deleted RESTART_TOOLS_COMMANDS.inc");
                else
                    buildAutomation.appendTextToLog("Failed to delete RESTART_TOOLS_COMMANDS.inc");

            }

            targetDirLogin = new File(pathJconfig + "\\java\\src\\custom\\RestartToolsCommand.java");

            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted)
                    buildAutomation.appendTextToLog("Successfully deleted RestartToolsCommand.java");
                else
                    buildAutomation.appendTextToLog("Failed to delete RestartToolsCommand.java ... ");
            }


            buildAutomation.appendTextToLog("Delete process completed.");

        }
        catch(Exception e){
            buildAutomation.appendTextToLog("Delete Restart tools Setup Process Failed.");
        }

    }
}
