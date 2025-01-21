package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.model.ProjectEntity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Validation {
    public static boolean validate(ProjectEntity project, String app) {
        String jconfigPath = project.getJconfigPath();
        String exePath = project.getExePath();
        String username = project.getUsername();
        String password = project.getPassword();
        String autoLogin = project.getAutoLogin();
        String typeSelected = project.getPrevTypeSelected(app);
        if (jconfigPath == null || jconfigPath.isEmpty()) {
            return false;
        }
        if (exePath == null || exePath.isEmpty()) {
            return false;
        }
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (password == null || password.isEmpty()) {
            return false;
        }
        if (autoLogin == null || autoLogin.isEmpty()) {
            return false;
        }
        if(typeSelected == null || typeSelected.isEmpty()){
            return false;
        }

        return true;
    }

    public static boolean validateForAutologin(ProjectEntity project) {
        String jconfigPath = project.getJconfigPath();
        String exePath = project.getExePath();
        String username = project.getUsername();
        String password = project.getPassword();
        String autoLogin = project.getAutoLogin();
        String loginId = project.getLogId();

        if (jconfigPath == null || jconfigPath.isEmpty()) {
            return false;
        }
        if (exePath == null || exePath.isEmpty()) {
            return false;
        }
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (password == null || password.isEmpty()) {
            return false;
        }
        if (autoLogin == null || autoLogin.isEmpty()) {
            return false;
        }
        if(loginId == null || loginId.isEmpty()) {
            return false;
        }

        return true;

    }

    public static boolean validateForRestartTools(ProjectEntity project) {
        String jconfigPath = project.getJconfigPath();
        String exePath = project.getExePath();

        if (jconfigPath == null || jconfigPath.isEmpty()) {
            return false;
        }
        if (exePath == null || exePath.isEmpty()) {
            return false;
        }
        return true;
    }

    public static boolean validateLoginSetup(ProjectEntity project) {

        try {

            String pathLogin = project.getJconfigPath() + "/global/xml/Login.xml";

            File file = new File(pathLogin);

            if (!file.exists()) {
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            boolean b = true;
            while ((line = br.readLine()) != null) {
                if (line.contains("AUTO_LOGIN_COMMANDS.inc")) {
                    b = false;
                }
            }
            if (b) {
                return false;
            }
            File targetDirLogin = new File(project.getJconfigPath() + "/java/src/custom/LoadCredentialsExternalCommand.java");
            if (!targetDirLogin.exists()) {
                return false;
            }
            targetDirLogin = new File(project.getJconfigPath() + "/global/xml/AUTO_LOGIN_COMMANDS.inc");

            return targetDirLogin.exists();

        } catch (IOException e) {
            return false;
        }
    }

    public static boolean validateRestartToolsSetup(ProjectEntity project) {

        try {

            String pathLogin = project.getJconfigPath() + "/global/xml/MAIN_MENUBAR_BUTTONS.inc";

            File file = new File(pathLogin);

            if (!file.exists()) {
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            boolean b = true;
            while ((line = br.readLine()) != null) {
                if (line.contains("RESTART_TOOLS_COMMANDS.inc")) {
                    b = false;
                }
            }
            if (b) {
                return false;
            }
            File targetDirLogin = new File(project.getJconfigPath() + "/java/src/custom/RestartToolsCommand.java");
            if (!targetDirLogin.exists()) {
                return false;
            }
            targetDirLogin = new File(project.getJconfigPath() + "/global/xml/RESTART_TOOLS_COMMANDS.inc");

            return targetDirLogin.exists();

        } catch (IOException e) {
            return false;
        }
    }

}
