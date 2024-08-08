package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.model.ProjectEntity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class WritePropertiesFile {

    public static boolean updateFile(ProjectEntity project, String app) {
        // Create a Properties object
        Properties properties = new Properties();
        if(!project.getAutoLogin().equals("true")) return false;
        // Set key-value pairs
        properties.setProperty("selectedProject",project.getName());
        properties.setProperty(project.getName()+"_username",project.getUsername());
        properties.setProperty(project.getName()+"_password",project.getPassword());
        properties.setProperty(project.getName()+"_selectedUserType",project.getPrevTypeSelected(app));
        properties.setProperty(project.getName()+"_autoLogin",project.getAutoLogin());

        // Define the file path (cread.properties in the current directory)
        String user = System.getProperty("user.name");
        String propPath = "C:/Users/" + user + "/Documents/nms_support_data";
        String filePath = propPath + "/cred.properties";

        try {
            // Create the directory if it doesn't exist
            Path directoryPath = Paths.get(propPath);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                System.out.println("Directory created: " + directoryPath);
            } else {
                System.out.println("Directory already exists: " + directoryPath);
            }

            // Create the file if it doesn't exist
            Path filePathObj = Paths.get(filePath);
            if (!Files.exists(filePathObj)) {
                Files.createFile(filePathObj);
                System.out.println("File created: " + filePathObj);
            } else {
                System.out.println("File already exists: " + filePathObj);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to create directory or file: " + e.getMessage());
            return false;
        }

        // Write properties to the file
        try (OutputStream output = new FileOutputStream(filePath)) {
            properties.store(output, "Configuration Properties");
            System.out.println("Properties file created/updated successfully.");
            return true;
        } catch (IOException io) {
            io.printStackTrace();
            return false;
        }
    }
}

