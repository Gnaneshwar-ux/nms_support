
package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import javafx.scene.control.ButtonType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 *
 * @author Gnaneshwar
 */
public class DeleteSetup {
    static String tempFile = "Login.xml";
    static String CommandCode = "<Include name=\"AUTO_LOGIN_COMMANDS.inc\"/>";
    public static void delete(ProjectEntity project, BuildAutomation buildAutomation) throws IOException {

        Optional<ButtonType> confirmation = DialogUtil.showConfirmationDialog("Confirm Delete","Are you sure to delete setup?","This operation undo the changes made by this tool to set autologin.");
        if (confirmation.isEmpty() || confirmation.get() == ButtonType.CANCEL) {
            buildAutomation.appendTextToLog("Delete process canceled.");
            return; // Stop the process if the user cancels
        }

        try{

            buildAutomation.appendTextToLog("Delete process started ... ");

            if(!Validation.validateSetup(project)){
                buildAutomation.appendTextToLog("Delete already completed or setup not performed ");
                return;
            }
            String pathJconfig = project.getJconfigPath();
            String loginPath = pathJconfig +"/global/xml/" + tempFile;
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

            buildAutomation.appendTextToLog("Removed changes in login.xml ... ");

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
                buildAutomation.appendTextToLog("Login.xml rollback done.");
            }
            else{
                buildAutomation.appendTextToLog("Login.xml rollback failed.");
            }


            File targetDirLogin = new File(pathJconfig + "/global/xml/AUTO_LOGIN_COMMANDS.inc");

            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted)
                buildAutomation.appendTextToLog("Succesfully deleted AUTO_LOGIN_COMMANDS.inc");
                else
                    buildAutomation.appendTextToLog("Failed to delete AUTO_LOGIN_COMMANDS.inc");

            }

            targetDirLogin = new File(pathJconfig + "/java/src/custom/LoadCredentialsExternalCommand.java");

            if(targetDirLogin.isFile()){
                boolean isDeleted = targetDirLogin.delete();
                if(isDeleted)
                buildAutomation.appendTextToLog("Successfully deleted LoadCredentialsExternalCommand.java");
                else
                    buildAutomation.appendTextToLog("Failed to delete LoadCredentialsExternalCommand.java ... ");
            }

            buildAutomation.appendTextToLog("Removed data from cred file");

            buildAutomation.appendTextToLog("Delete process completed.");

        }
        catch(Exception e){
            buildAutomation.appendTextToLog("Delete Setup Process Failed.");

        }

    }

}
