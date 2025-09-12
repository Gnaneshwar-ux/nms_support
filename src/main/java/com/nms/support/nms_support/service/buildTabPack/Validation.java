package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.model.ProjectEntity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Validation {
    
    /**
     * Validation result class to hold detailed validation information
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
            this.isValid = isValid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public boolean isValid() { return isValid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>(), new ArrayList<>());
        }
        
        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, new ArrayList<>());
        }
        
        public static ValidationResult failure(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            return new ValidationResult(false, errors, new ArrayList<>());
        }
    }
    
    public static boolean validate(ProjectEntity project, String app) {
        return validateDetailed(project, app).isValid();
    }
    
    public static ValidationResult validateDetailed(ProjectEntity project, String app) {
        List<String> errors = new ArrayList<>();
        
        String jconfigPath = project.getJconfigPathForBuild();
        String exePath = project.getExePath();
        String username = project.getUsername();
        String password = project.getPassword();
        String autoLogin = project.getAutoLogin();
        String typeSelected = project.getPrevTypeSelected(app);
        
        if (jconfigPath == null || jconfigPath.isEmpty()) {
            errors.add("JConfig Path is missing or empty");
        }
        if (exePath == null || exePath.isEmpty()) {
            errors.add("Executable Path is missing or empty");
        }
        if (username == null || username.isEmpty()) {
            errors.add("Username is missing or empty");
        }
        if (password == null || password.isEmpty()) {
            errors.add("Password is missing or empty");
        }
        if (autoLogin == null || autoLogin.isEmpty()) {
            errors.add("Auto login setting is missing or empty");
        }
        if(typeSelected == null || typeSelected.isEmpty()){
            errors.add("User type selection is missing or empty for application: " + app);
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public static boolean validateForAutologin(ProjectEntity project) {
        return validateForAutologinDetailed(project).isValid();
    }
    
    public static ValidationResult validateForAutologinDetailed(ProjectEntity project) {
        List<String> errors = new ArrayList<>();
        
        String jconfigPath = project.getJconfigPathForBuild();
        String exePath = project.getExePath();
        String username = project.getUsername();
        String password = project.getPassword();
        String autoLogin = project.getAutoLogin();
        String loginId = project.getLogId();

        if (jconfigPath == null || jconfigPath.isEmpty()) {
            errors.add("JConfig Path is missing or empty");
        }
        if (exePath == null || exePath.isEmpty()) {
            errors.add("Executable Path is missing or empty");
        }
        if (username == null || username.isEmpty()) {
            errors.add("Username is missing or empty");
        }
        if (password == null || password.isEmpty()) {
            errors.add("Password is missing or empty");
        }
        if (autoLogin == null || autoLogin.isEmpty()) {
            errors.add("Auto login setting is missing or empty");
        }
        if(loginId == null || loginId.isEmpty()) {
            errors.add("Project Code is missing or empty");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public static boolean validateForRestartTools(ProjectEntity project) {
        return validateForRestartToolsDetailed(project).isValid();
    }
    
    public static ValidationResult validateForRestartToolsDetailed(ProjectEntity project) {
        List<String> errors = new ArrayList<>();
        
        String jconfigPath = project.getJconfigPathForBuild();
        String exePath = project.getExePath();

        if (jconfigPath == null || jconfigPath.isEmpty()) {
            errors.add("JConfig Path is missing or empty");
        }
        if (exePath == null || exePath.isEmpty()) {
            errors.add("Executable Path is missing or empty");
        }
        
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public static boolean validateLoginSetup(ProjectEntity project) {
        return validateLoginSetupDetailed(project).isValid();
    }
    
    public static ValidationResult validateLoginSetupDetailed(ProjectEntity project) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            String pathLogin = project.getJconfigPath() + "/global/xml/Login.xml";
            File file = new File(pathLogin);

            if (!file.exists()) {
                errors.add("Login.xml file does not exist at: " + pathLogin);
                return ValidationResult.failure(errors);
            }

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            boolean hasAutoLoginCommands = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("AUTO_LOGIN_COMMANDS.inc")) {
                    hasAutoLoginCommands = true;
                    break;
                }
            }
            br.close();
            
            if (!hasAutoLoginCommands) {
                errors.add("Login.xml does not contain AUTO_LOGIN_COMMANDS.inc reference");
            }
            
            File targetDirLogin = new File(project.getJconfigPath() + "/java/src/custom/LoadCredentialsExternalCommand.java");
            if (!targetDirLogin.exists()) {
                errors.add("LoadCredentialsExternalCommand.java does not exist at: " + targetDirLogin.getAbsolutePath());
            }
            
            targetDirLogin = new File(project.getJconfigPath() + "/global/xml/AUTO_LOGIN_COMMANDS.inc");
            if (!targetDirLogin.exists()) {
                errors.add("AUTO_LOGIN_COMMANDS.inc file does not exist at: " + targetDirLogin.getAbsolutePath());
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);

        } catch (IOException e) {
            errors.add("IOException while validating login setup: " + e.getMessage());
            return ValidationResult.failure(errors);
        }
    }

    public static boolean validateRestartToolsSetup(ProjectEntity project) {
        return validateRestartToolsSetupDetailed(project).isValid();
    }
    
    public static ValidationResult validateRestartToolsSetupDetailed(ProjectEntity project) {
        List<String> errors = new ArrayList<>();

        try {
            String pathLogin = project.getJconfigPath() + "/global/xml/MAIN_MENUBAR_BUTTONS.inc";
            File file = new File(pathLogin);

            if (!file.exists()) {
                errors.add("MAIN_MENUBAR_BUTTONS.inc file does not exist at: " + pathLogin);
                return ValidationResult.failure(errors);
            }

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            boolean hasRestartToolsCommands = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("RESTART_TOOLS_COMMANDS.inc")) {
                    hasRestartToolsCommands = true;
                    break;
                }
            }
            br.close();
            
            if (!hasRestartToolsCommands) {
                errors.add("MAIN_MENUBAR_BUTTONS.inc does not contain RESTART_TOOLS_COMMANDS.inc reference");
            }
            
            File targetDirLogin = new File(project.getJconfigPath() + "/java/src/custom/RestartToolsCommand.java");
            if (!targetDirLogin.exists()) {
                errors.add("RestartToolsCommand.java does not exist at: " + targetDirLogin.getAbsolutePath());
            }
            
            targetDirLogin = new File(project.getJconfigPath() + "/global/xml/RESTART_TOOLS_COMMANDS.inc");
            if (!targetDirLogin.exists()) {
                errors.add("RESTART_TOOLS_COMMANDS.inc file does not exist at: " + targetDirLogin.getAbsolutePath());
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);

        } catch (IOException e) {
            errors.add("IOException while validating restart tools setup: " + e.getMessage());
            return ValidationResult.failure(errors);
        }
    }
}
