package com.nms.support.nms_support.util;

import java.io.File;
import java.util.logging.Logger;

/**
 * Utility class for handling NMS project path normalization.
 * 
 * This class provides methods to handle the transition from storing jconfig paths
 * to storing project folder paths while maintaining backward compatibility.
 * 
 * Key concepts:
 * - Project Folder: The main project directory (e.g., /path/to/MyProject)
 * - JConfig Path: The jconfig subdirectory within the project (e.g., /path/to/MyProject/jconfig)
 * 
 * The system should:
 * - Display project folder to users
 * - Store jconfig path internally for build operations
 * - Handle both old and new path formats gracefully
 */
public class ProjectPathUtil {
    
    private static final Logger logger = Logger.getLogger(ProjectPathUtil.class.getName());
    
    /**
     * Normalizes a path to always return the project folder path.
     * This method handles backward compatibility with existing data.
     * 
     * @param storedPath The path as stored in the database (could be project folder or jconfig path)
     * @return The project folder path (without /jconfig suffix)
     */
    public static String getProjectFolderPath(String storedPath) {
        if (storedPath == null || storedPath.trim().isEmpty()) {
            return "";
        }
        
        String normalizedPath = storedPath.replace('\\', '/').trim();
        
        // If path ends with /jconfig, return the parent directory
        if (normalizedPath.endsWith("/jconfig") || normalizedPath.endsWith("\\jconfig")) {
            String projectFolder = normalizedPath.substring(0, normalizedPath.lastIndexOf("/jconfig"));
            if (projectFolder.isEmpty()) {
                projectFolder = normalizedPath.substring(0, normalizedPath.lastIndexOf("\\jconfig"));
            }
            logger.fine("Converted jconfig path to project folder: " + storedPath + " -> " + projectFolder);
            return projectFolder;
        }
        
        // Path is already a project folder
        logger.fine("Path is already project folder: " + storedPath);
        return storedPath;
    }
    
    /**
     * Converts a project folder path to the jconfig path for internal operations.
     * This method ensures build operations always use the correct jconfig path.
     * 
     * @param projectFolderPath The project folder path
     * @return The jconfig path (project folder + /jconfig)
     */
    public static String getJconfigPath(String projectFolderPath) {
        if (projectFolderPath == null || projectFolderPath.trim().isEmpty()) {
            return "";
        }
        
        String normalizedPath = projectFolderPath.replace('\\', '/').trim();
        
        // Remove trailing slash if present
        if (normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }
        
        String jconfigPath = normalizedPath + "/jconfig";
        logger.fine("Converted project folder to jconfig path: " + projectFolderPath + " -> " + jconfigPath);
        return jconfigPath;
    }
    
    /**
     * Determines the appropriate path for checkout operations.
     * For checkout, we always use the project folder (not jconfig) to avoid conflicts.
     * 
     * @param storedPath The path as stored in the database
     * @return The project folder path for checkout operations
     */
    public static String getCheckoutPath(String storedPath) {
        return getProjectFolderPath(storedPath);
    }
    
    /**
     * Determines the appropriate path for build operations.
     * For build operations, we always use the jconfig path.
     * 
     * @param storedPath The path as stored in the database
     * @return The jconfig path for build operations
     */
    public static String getBuildPath(String storedPath) {
        String projectFolder = getProjectFolderPath(storedPath);
        return getJconfigPath(projectFolder);
    }
    
    /**
     * Validates that a project folder contains a jconfig subdirectory.
     * This method is used to ensure the project structure is correct.
     * 
     * @param projectFolderPath The project folder path to validate
     * @return true if the jconfig subdirectory exists, false otherwise
     */
    public static boolean validateProjectStructure(String projectFolderPath) {
        if (projectFolderPath == null || projectFolderPath.trim().isEmpty()) {
            return false;
        }
        
        String jconfigPath = getJconfigPath(projectFolderPath);
        File jconfigDir = new File(jconfigPath);
        
        boolean exists = jconfigDir.exists() && jconfigDir.isDirectory();
        logger.fine("Validating project structure: " + projectFolderPath + " -> jconfig exists: " + exists);
        return exists;
    }
    
    /**
     * Finds the jconfig directory within a project folder, searching recursively if needed.
     * This method helps locate jconfig directories that might be in subdirectories.
     * 
     * @param projectFolderPath The project folder path to search
     * @return The full path to the jconfig directory, or null if not found
     */
    public static String findJconfigDirectory(String projectFolderPath) {
        if (projectFolderPath == null || projectFolderPath.trim().isEmpty()) {
            return null;
        }
        
        File projectDir = new File(projectFolderPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return null;
        }
        
        // First, check if jconfig exists directly in the project folder
        String directJconfigPath = getJconfigPath(projectFolderPath);
        File directJconfig = new File(directJconfigPath);
        if (directJconfig.exists() && directJconfig.isDirectory()) {
            logger.fine("Found jconfig directory directly: " + directJconfigPath);
            return directJconfigPath;
        }
        
        // Search recursively for jconfig directories
        return findJconfigRecursively(projectDir);
    }
    
    /**
     * Recursively searches for jconfig directories within a project folder.
     * 
     * @param dir The directory to search
     * @return The full path to the jconfig directory, or null if not found
     */
    private static String findJconfigRecursively(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                if ("jconfig".equals(file.getName())) {
                    logger.fine("Found jconfig directory recursively: " + file.getAbsolutePath());
                    return file.getAbsolutePath();
                }
                
                // Recursively search subdirectories (but limit depth to avoid infinite loops)
                String result = findJconfigRecursively(file);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Normalizes a path for storage, ensuring it's stored as a jconfig path internally.
     * This method is used when saving project data to maintain consistency.
     * 
     * @param userInputPath The path as entered by the user (could be project folder or jconfig)
     * @return The path to store internally (always jconfig path)
     */
    public static String normalizeForStorage(String userInputPath) {
        if (userInputPath == null || userInputPath.trim().isEmpty()) {
            return "";
        }
        
        String projectFolder = getProjectFolderPath(userInputPath);
        return getJconfigPath(projectFolder);
    }
    
    /**
     * Gets the display path for the user interface.
     * This method always returns the project folder path for display purposes.
     * 
     * @param storedPath The path as stored in the database
     * @return The project folder path for display
     */
    public static String getDisplayPath(String storedPath) {
        return getProjectFolderPath(storedPath);
    }
}
