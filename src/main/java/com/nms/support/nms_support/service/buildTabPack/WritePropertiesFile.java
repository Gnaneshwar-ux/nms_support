package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.Mappings;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public class WritePropertiesFile {

    public static boolean updateFile(ProjectEntity project, String app) {
        Properties properties = new Properties();
        String prefix = project.getLogId() + "_" + Mappings.getCodeFromApp(app);

        // Define the file path
        String user = System.getProperty("user.name");
        String propPath = "C:/Users/" + user + "/Documents/nms_support_data";
        String filePath = propPath + "/cred.properties";

        // Load existing properties if the file exists
        Path filePathObj = Paths.get(filePath);
        if (Files.exists(filePathObj)) {
            try (FileInputStream input = new FileInputStream(filePath)) {
                properties.load(input);
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to load existing properties: " + e.getMessage());
                return false;
            }
        }

        // Update properties with new values
        properties.setProperty(prefix + "_username",
                Optional.ofNullable(project.getUsername()).orElse(""));
        properties.setProperty(prefix + "_password",
                Optional.ofNullable(project.getPassword()).orElse(""));
        properties.setProperty(prefix + "_selectedUserType",
                Optional.ofNullable(project.getPrevTypeSelected(app)).orElse(""));
        properties.setProperty(prefix + "_autoLogin",
                Optional.ofNullable(project.getAutoLogin()).orElse("false"));

        // Create the directory if it doesn't exist
        try {
            Path directoryPath = Paths.get(propPath);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                System.out.println("Directory created: " + directoryPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to create directory: " + e.getMessage());
            return false;
        }

        // Write updated properties to file, overwriting existing content
        try (OutputStream output = new FileOutputStream(filePath)) {
            properties.store(output, "Configuration Properties");
            System.out.println("Properties file updated successfully.");
            return true;
        } catch (IOException io) {
            io.printStackTrace();
            System.err.println("Failed to update properties file: " + io.getMessage());
            return false;
        }
    }



}

