package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetupAutoLogin {

    static String tempFile = "Login.xml";
    static String CommandCode = "\n<Perform name=\"Window\" category=\"windowActivated\"><Include name=\"AUTO_LOGIN_COMMANDS.inc\"/></Perform>\n";
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
            if(!updateFile(project.getJconfigPath() + "/global/xml/", project.getExePath()+"/java/product/global/xml/"+tempFile, buildAutomation)){
                buildAutomation.appendTextToLog("Update file failed");
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        //System.out.println(SQLParser.parseSQLFile(project.getJconfigPath().replace("jconfig", "sql"), "crew"));

        loadUserTypes(project);

        try {
            return copyFiles(project,buildAutomation);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


    }

    public static void loadUserTypes(ProjectEntity project){
        HashMap<String, List<String>> l = SQLParser.parseSQLFile(project.getJconfigPath().replace("jconfig", "sql"));
        for (Map.Entry<String, List<String>> entry : l.entrySet()) {
            System.out.println("Key: " + entry.getKey() + " - Value: " + entry.getValue());
        }
        project.setTypes(l);
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
                    res.append(line+"\n"+text);
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

            File sourceFileLogin = new File("AUTO_LOGIN_COMMANDS.inc");
            File targetDirLogin = new File(project.getJconfigPath() + "/global/xml/AUTO_LOGIN_COMMANDS.inc");

            if (!targetDirLogin.exists()) {
                boolean isCreated = targetDirLogin.mkdirs();
                if(!isCreated){
                    buildAutomation.appendTextToLog("Failed to create dir "+targetDirLogin);
                    return false;
                }
            }

            Files.copy(sourceFileLogin.toPath(), targetDirLogin.toPath(), StandardCopyOption.REPLACE_EXISTING);

            //System.out.println("Inc file copied successfully!");
            buildAutomation.appendTextToLog("Inc file copied successfully!\n");

            // copying command files

            sourceFileLogin = new File("LoadCredentialsExternalCommand.java");
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

}