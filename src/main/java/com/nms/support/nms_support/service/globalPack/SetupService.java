package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.controller.MainController;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.CreateInstallerCommand;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.SFTPDownloadAndUnzip;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.FileFetcher;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.DirectoryProcessor;
import com.nms.support.nms_support.service.globalPack.ProcessMonitorAdapter;
import com.nms.support.nms_support.service.userdata.ProjectManager;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import org.tmatesoft.svn.core.SVNException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/**
 * Business-level service for handling setup operations
 */
public class SetupService {
    private static final Logger logger = LoggerUtil.getLogger();


    private static String validation = "VALIDATION";
    public enum SetupMode {
        FULL_CHECKOUT,
        PRODUCT_ONLY
    }
    
    public enum ValidationResult {
        SUCCESS,
        MISSING_ENV_VAR,
        MISSING_PRODUCT_FOLDER,
        MISSING_SVN_URL,
        MISSING_PROJECT_FOLDER,
        MISSING_LAUNCH4J,
        MISSING_NSIS,
        MISSING_JAVA,
        MISSING_TOOLS,
        MISSING_SFTP_HOST,
        MISSING_SFTP_USER,
        MISSING_SFTP_PASSWORD,
        MISSING_APP_URL,
        SFTP_CONNECTION_FAILED,
        VPN_CONNECTION_FAILED,
        NETWORK_CONNECTION_FAILED,
        EXCEPTION, NOT_ALLOWED, INVALID_PATHS
    }
    
    private final ProjectEntity project;
    private final ProcessMonitor processMonitor;
    private final SetupMode setupMode;
    
    /**
     * Determines the appropriate folder to clean up based on the jconfig path.
     * If the path ends with 'jconfig', returns the parent folder.
     * Otherwise, returns the original path.
     * 
     * @param jconfigPath The jconfig path to analyze
     * @return The folder that should be cleaned up
     */
    private File determineCleanupFolder(String jconfigPath) {
        if (jconfigPath == null || jconfigPath.trim().isEmpty()) {
            return new File(jconfigPath);
        }
        
        // Normalize the path to handle different separators
        String normalizedPath = jconfigPath.replace('\\', '/').trim();
        
        // Check if the path ends with 'jconfig'
        if (normalizedPath.endsWith("jconfig") || normalizedPath.endsWith("jconfig/")) {
            File jconfigFile = new File(jconfigPath);
            File parentFolder = jconfigFile.getParentFile();
            
            if (parentFolder != null) {
                logger.info("Path ends with 'jconfig', cleaning parent folder: " + parentFolder.getAbsolutePath());
                return parentFolder;
            } else {
                logger.warning("Path ends with 'jconfig' but parent folder is null, using original path: " + jconfigPath);
                return jconfigFile;
            }
        } else {
            logger.info("Path does not end with 'jconfig', cleaning original path: " + jconfigPath);
            return new File(jconfigPath);
        }
    }
    
    public SetupService(ProjectEntity project, ProcessMonitor processMonitor, Boolean isFull) {
        this.project = project;
        this.processMonitor = processMonitor;
        this.setupMode = isFull ? SetupMode.FULL_CHECKOUT : SetupMode.PRODUCT_ONLY;
    }
    
    /**
     * Main setup execution method
     */
    public void executeSetup(MainController mc) {
        try {
            // Validate inputs and required tools
            processMonitor.addStep(validation, "Running Pre Validation");
            ValidationResult validationRes = validateInputs();
            if (validationRes != ValidationResult.SUCCESS) {
                processMonitor.markFailed(validation, validationRes.toString());
                return;
            } else {
                processMonitor.markComplete(validation, "All required fields provided.");
            }
            if(!processMonitor.isRunning()) return;

            
            // Run comprehensive tool and connectivity validation
            processMonitor.addStep("tool_validation", "Validating Tools");
            ValidationResult toolValidationRes = validateRequiredToolsAndConnectivity();
            if (toolValidationRes != ValidationResult.SUCCESS) {
                processMonitor.markFailed("tool_validation", toolValidationRes.toString());
                return;
            } else {
                processMonitor.markComplete("tool_validation", "All required tools and connectivity validated successfully");
            }
            
            processMonitor.markComplete(validation, "Pre-validation completed successfully");
            if(!processMonitor.isRunning()) return;

            // Environment variable creation and validation
            processMonitor.addStep("env_validation", "Creating environment variables");
            if (!validateAndCreateEnvironmentVariable(project)) {
                processMonitor.markFailed("env_validation", "Environment variable validation and creation failed");
                return;
            }
            processMonitor.markComplete("env_validation", "Environment variable validation completed successfully");
            
            if(!processMonitor.isRunning()) return;
            
            if (setupMode == SetupMode.FULL_CHECKOUT) {
                // SVN checkout
                // Clean folder before checkout
                processMonitor.addStep("cleanup", "Cleaning project directory");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "cleanup");
                    
                    // Determine the folder to clean up using the project folder path
                    String projectFolderPath = project.getProjectFolderPathForCheckout();
                    File folderToClean = new File(projectFolderPath);
                    
                    processMonitor.logMessage("cleanup", "Cleaning folder: " + folderToClean.getAbsolutePath());
                    
                    SVNAutomationTool.deleteFolderContents(folderToClean, callback);
                } catch (IOException e) {
                    processMonitor.markFailed("cleanup", "Failed to clean directory: " + e.getMessage());
                    return;
                }

                if(!processMonitor.isRunning()) return;
                //SVN checkout
                processMonitor.addStep("checkout", "SVN project checkout");
                try {

                    String projectFolderPath = project.getProjectFolderPathForCheckout();
                    File folderToCheckout = new File(projectFolderPath);

                    processMonitor.logMessage("checkout", "Checking out to folder: " + folderToCheckout.getAbsolutePath());

                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "checkout");
                    SVNAutomationTool.performCheckout(project.getSvnRepo(), folderToCheckout.getAbsolutePath(), callback);
                } catch (SVNException e) {
                    processMonitor.markFailed("checkout", "Failed to checkout: " + e.getMessage());
                    return;
                }
                if(!processMonitor.isRunning()) return;

                //Here call a method to validate project folder if project folder contains jconfig update the project folder path with this new jconfig path, if jconfig missing report error stop the process 
                processMonitor.addStep("validation", "Validating project structure");
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                String validatedJconfigPath = validateProjectFolder(projectFolderPath);
                if (validatedJconfigPath == null) {
                    processMonitor.markFailed("validation", "Project validation failed: jconfig folder not found in project folder");
                    return;
                }
                // Update the project with the validated jconfig path
                project.setJconfigPath(validatedJconfigPath);
                processMonitor.markComplete("validation", "Project structure validated successfully");

                // Process build files with proper validation and monitoring
                processBuildFiles(project);
                if (!processMonitor.isRunning()) return;

                // Clean product directory
                processMonitor.addStep("product_cleanup", "Cleaning product directory");
                if (!cleanProductDirectory(project.getExePath())) {
                    processMonitor.markFailed("product_cleanup", "Failed to clean product directory");
                    return;
                }
                processMonitor.markComplete("product_cleanup", "Product directory cleaned successfully");
                if (!processMonitor.isRunning()) return; 

                // SFTP download and unzip
                processMonitor.addStep("sftp_download", "SFTP download and extraction");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "sftp_download");
                    String dir_temp = project.getExePath();
                    SFTPDownloadAndUnzip.start(dir_temp, project, callback);
                } catch (Exception e) {
                    processMonitor.markFailed("sftp_download", "SFTP download failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Load additional resources
                processMonitor.addStep("resource_loading", "Loading additional resources");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "resource_loading");
                    String dir_temp = project.getExePath();
                    String serverURL = adjustUrl(project.getNmsAppURL()); // Using SVN repo as server URL
                    FileFetcher.loadResources(dir_temp, serverURL, callback);
                } catch (Exception e) {
                    processMonitor.markFailed("resource_loading", "Resource loading failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return; 
                
                // Process directory structure
                processMonitor.addStep("directory_processing", "Processing directory structure");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "directory_processing");
                    String dir_temp = project.getExePath();
                    DirectoryProcessor.processDirectory(dir_temp, callback);
                } catch (Exception e) {
                    processMonitor.markFailed("directory_processing", "Directory processing failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Now call the create installer command execute method
                processMonitor.addStep("installer_creation", "Creating executables");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "installer_creation");
                    CreateInstallerCommand cic = new CreateInstallerCommand();
                    String appURL = project.getNmsAppURL();
                    String envVarName = project.getNmsEnvVar();
                    boolean success = cic.execute(appURL, envVarName, project, callback);
                    if (!success) {
                        processMonitor.markFailed("installer_creation", "Installer creation failed");
                        return;
                    }
                } catch (Exception e) {
                    processMonitor.markFailed("installer_creation", "Installer creation failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Update build files with environment variable
                processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
                try {
                    String env_name = project.getNmsEnvVar();
                    String exePath = project.getExePath();
                    
                    // Update build.properties file
                    String buildPropertiesPath = exePath + "/java/ant/build.properties";
                    File buildPropertiesFile = new File(buildPropertiesPath);
                    if (buildPropertiesFile.exists() && buildPropertiesFile.isFile() && buildPropertiesFile.canRead()) {
                        ManageFile.replaceTextInFiles(List.of(buildPropertiesPath), "NMS_HOME", env_name);
                        processMonitor.updateState("build_file_updates", 50);
                        logger.info("Updated build.properties with environment variable: " + env_name);
                    } else {
                        logger.warning("build.properties file not found or not accessible: " + buildPropertiesPath);
                    }
                    
                    // Update build.xml file
                    String buildXmlPath = exePath + "/java/ant/build.xml";
                    File buildXmlFile = new File(buildXmlPath);
                    if (buildXmlFile.exists() && buildXmlFile.isFile() && buildXmlFile.canRead()) {
                        ManageFile.replaceTextInFiles(List.of(buildXmlPath), "NMS_HOME", env_name);
                        processMonitor.updateState("build_file_updates", 100);
                        logger.info("Updated build.xml with environment variable: " + env_name);
                    } else {
                        logger.warning("build.xml file not found or not accessible: " + buildXmlPath);
                    }
                    
                    processMonitor.markComplete("build_file_updates", "Build files updated successfully with environment variable: " + env_name);
                } catch (Exception e) {
                    logger.severe("Failed to update build files: " + e.getMessage());
                    processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

            }
            else if(setupMode == SetupMode.PRODUCT_ONLY) {
                //Here call a method to validate project folder if project folder contains jconfig update the project folder path with this new jconfig path, if jconfig missing report error stop the process 
                processMonitor.addStep("validation", "Validating project structure");
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                String validatedJconfigPath = validateProjectFolder(projectFolderPath);
                if (validatedJconfigPath == null) {
                    processMonitor.markFailed("validation", "Project validation failed: jconfig folder not found in project folder");
                    return;
                }
                // Update the project with the validated jconfig path
                project.setJconfigPath(validatedJconfigPath);
                processMonitor.markComplete("validation", "Project structure validated successfully");
                
                // Process build files with proper validation and monitoring
                processBuildFiles(project);
                if (!processMonitor.isRunning()) return;

                // Clean product directory
                processMonitor.addStep("product_cleanup", "Cleaning product directory");
                if (!cleanProductDirectory(project.getExePath())) {
                    processMonitor.markFailed("product_cleanup", "Failed to clean product directory");
                    return;
                }
                processMonitor.markComplete("product_cleanup", "Product directory cleaned successfully");
                if (!processMonitor.isRunning()) return;

                // SFTP download and unzip
                processMonitor.addStep("sftp_download", "SFTP download and extraction");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "sftp_download");
                    String dir_temp = project.getExePath();
                    SFTPDownloadAndUnzip.start(dir_temp, project, callback);
                } catch (Exception e) {
                    processMonitor.markFailed("sftp_download", "SFTP download failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Load additional resources
                processMonitor.addStep("resource_loading", "Loading additional resources");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "resource_loading");
                    String dir_temp = project.getExePath();
                    String serverURL = adjustUrl(project.getNmsAppURL()); // Using SVN repo as server URL
                    FileFetcher.loadResources(dir_temp, serverURL, callback);
                } catch (Exception e) {
                    processMonitor.markFailed("resource_loading", "Resource loading failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return; 
                
                // Process directory structure
                processMonitor.addStep("directory_processing", "Processing directory structure");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "directory_processing");
                    String dir_temp = project.getExePath();
                    DirectoryProcessor.processDirectory(dir_temp, callback);
                } catch (Exception e) {
                    processMonitor.markFailed("directory_processing", "Directory processing failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Now call the create installer command execute method
                processMonitor.addStep("installer_creation", "Creating executables");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "installer_creation");
                    CreateInstallerCommand cic = new CreateInstallerCommand();
                    String appURL = project.getNmsAppURL();
                    String envVarName = project.getNmsEnvVar();
                    boolean success = cic.execute(appURL, envVarName, project, callback);
                    if (!success) {
                        processMonitor.markFailed("installer_creation", "Installer creation failed");
                        return;
                    }
                } catch (Exception e) {
                    processMonitor.markFailed("installer_creation", "Installer creation failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Update build files with environment variable
                processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
                try {
                    String env_name = project.getNmsEnvVar();
                    String exePath = project.getExePath();
                    
                    // Update build.properties file
                    String buildPropertiesPath = exePath + "/java/ant/build.properties";
                    File buildPropertiesFile = new File(buildPropertiesPath);
                    if (buildPropertiesFile.exists() && buildPropertiesFile.isFile() && buildPropertiesFile.canRead()) {
                        ManageFile.replaceTextInFiles(List.of(buildPropertiesPath), "NMS_HOME", env_name);
                        processMonitor.updateState("build_file_updates", 50);
                        logger.info("Updated build.properties with environment variable: " + env_name);
                    } else {
                        logger.warning("build.properties file not found or not accessible: " + buildPropertiesPath);
                    }
                    
                    // Update build.xml file
                    String buildXmlPath = exePath + "/java/ant/build.xml";
                    File buildXmlFile = new File(buildXmlPath);
                    if (buildXmlFile.exists() && buildXmlFile.isFile() && buildXmlFile.canRead()) {
                        ManageFile.replaceTextInFiles(List.of(buildXmlPath), "NMS_HOME", env_name);
                        processMonitor.updateState("build_file_updates", 100);
                        logger.info("Updated build.xml with environment variable: " + env_name);
                    } else {
                        logger.warning("build.xml file not found or not accessible: " + buildXmlPath);
                    }
                    
                    processMonitor.markComplete("build_file_updates", "Build files updated successfully with environment variable: " + env_name);
                } catch (Exception e) {
                    logger.severe("Failed to update build files: " + e.getMessage());
                    processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;
            } else {

            }
            
            // Finalize setup
            if (processMonitor.isRunning()) {
                processMonitor.addStep("finalize", "Finalizing setup");
                processMonitor.updateState("finalize", 50);
                processMonitor.markComplete("finalize", "Setup completed successfully");
                
                // Mark the entire process as completed successfully
                project.setManageTool("true");
                mc.performGlobalSave();
                processMonitor.markProcessCompleted("Setup completed successfully");
            }
            
        } catch (Exception e) {
            logger.severe("Setup failed: " + e.getMessage());
        }
    }

    /**
     * Cleans the product directory by removing all files and subdirectories
     * 
     * @param directoryPath The path to the directory to clean
     * @return true if the directory was cleaned successfully, false otherwise
     */
    private boolean cleanProductDirectory(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            
            // Check if directory exists
            if (!Files.exists(dir)) {
                logger.info("Directory does not exist: " + dir);
                processMonitor.updateState("product_cleanup", 100);
                return false; // Not an error if directory doesn't exist
            }
            
            // Check if it's actually a directory
            if (!Files.isDirectory(dir)) {
                logger.severe("Not a directory: " + dir);
                return false;
            }
            
            processMonitor.updateState("product_cleanup", 25);
            
            // Walk through the directory tree and delete all files and directories
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Check for cancellation before each file deletion
                    if (!processMonitor.isRunning()) {
                        logger.info("Product cleanup cancelled by user");
                        return FileVisitResult.TERMINATE;
                    }
                    
                    try {
                        Files.delete(file);
                        logger.fine("Deleted file: " + file);
                    } catch (IOException e) {
                        logger.warning("Failed to delete file: " + file + " - " + e.getMessage());
                        // Continue with other files even if one fails
                    }
                    
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // Check for cancellation before each directory deletion
                    if (!processMonitor.isRunning()) {
                        logger.info("Product cleanup cancelled by user");
                        return FileVisitResult.TERMINATE;
                    }
                    
                    if (exc == null) {
                        try {
                            // Don't delete the root directory, only its contents
                            if (!dir.equals(Paths.get(directoryPath))) {
                                Files.delete(dir);
                                logger.fine("Deleted directory: " + dir);
                            }
                            return FileVisitResult.CONTINUE;
                        } catch (IOException e) {
                            logger.warning("Failed to delete directory: " + dir + " - " + e.getMessage());
                            // Continue with other directories even if one fails
                            return FileVisitResult.CONTINUE;
                        }
                    } else {
                        logger.warning("Error visiting directory: " + dir + " - " + exc.getMessage());
                        return FileVisitResult.CONTINUE;
                    }
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    logger.warning("Failed to visit file: " + file + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE; // Continue with other files
                }
            });
            
            // Check if the process was cancelled
            if (!processMonitor.isRunning()) {
                logger.info("Product cleanup was cancelled by user");
                return false;
            }
            
            processMonitor.updateState("product_cleanup", 100);
            logger.info("Product directory cleaned successfully: " + directoryPath);
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to clean product directory: " + e.getMessage());
            return false;
        }
    }

    /**
     * Simple file visitor implementation for directory cleanup
     */
//    private static class SimpleFileVisitor<T> extends java.nio.file.SimpleFileVisitor<T> {
//        // Default implementation is sufficient for our needs
//    }

    /**
     * Validates and creates environment variable if it doesn't exist
     * 
     * @param project The project entity containing environment variable configuration
     * @return true if environment variable is valid or successfully created, false otherwise
     */
    private boolean validateAndCreateEnvironmentVariable(ProjectEntity project) {
        try {
            String envVarName = project.getNmsEnvVar();
            
            // Validate environment variable name
            if (envVarName == null || envVarName.trim().isEmpty()) {
                logger.severe("Environment variable name is null or empty");
                processMonitor.logMessage("env_validation", "Please provide valid environment variable name in project configuration tab");
                return false;
            }
            
            processMonitor.updateState("env_validation", 25);
            
            // Check if environment variable already exists
            if (doesEnvVariableExist(envVarName)) {
                logger.info("Environment variable '" + envVarName + "' already exists");
                processMonitor.updateState("env_validation", 100, "Environment variable '" + envVarName + "' already exists no change required");
                return true;
            }
            
            processMonitor.updateState("env_validation", 50);
            
            // Create the environment variable
            logger.info("Environment variable '" + envVarName + "' does not exist. Creating new environment variable.");
            boolean created = createUserEnvVar(envVarName, project.getExePath());
            
            if (created) {
                logger.info("Successfully created environment variable '" + envVarName + "' with value '" + project.getExePath() + "'");
                processMonitor.updateState("env_validation", 100, "Successfully created environment variable '" + envVarName + "' with value '" + project.getExePath() + "'");
                return true;
            } else {
                logger.severe("Failed to create environment variable '" + envVarName + "'");
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("Environment variable validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if an environment variable exists in the system
     * 
     * @param varName The name of the environment variable to check
     * @return true if the environment variable exists, false otherwise
     */
    private boolean doesEnvVariableExist(String varName) {
        try {
            // Get the map of environment variables
            Map<String, String> env = System.getenv();
            // Check if the map contains the specified variable name
            return env.containsKey(varName);
        } catch (Exception e) {
            logger.warning("Failed to check environment variable existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a user environment variable using the setx command
     * 
     * @param variableName The name of the environment variable to create
     * @param variableValue The value to assign to the environment variable
     * @return true if the environment variable was created successfully, false otherwise
     */
    private boolean createUserEnvVar(String variableName, String variableValue) {
        try {
            // Quote the value to support spaces and special characters
            String command = String.format("setx %s \"%s\"", variableName, variableValue);

            // Run using cmd
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            Process process = processBuilder.start();

            // Print output (for debugging)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("ENV OUTPUT: " + line);
                }

                while ((line = errReader.readLine()) != null) {
                    logger.warning("ENV ERROR: " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("User environment variable '" + variableName + "' set successfully.");
                return true;
            } else {
                logger.severe("Failed to set environment variable '" + variableName + "'. Exit code: " + exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("Exception while creating environment variable: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate all required inputs
     */
    private ValidationResult validateInputs() {
        try {
            if (project == null) {
                return ValidationResult.MISSING_PROJECT_FOLDER;
            }
                processMonitor.updateState(validation, 5);

            if (project.getNmsEnvVar() == null || project.getNmsEnvVar().trim().isEmpty()) {
                return ValidationResult.MISSING_ENV_VAR;
            }
                processMonitor.updateState(validation, 10);

            if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
                return ValidationResult.MISSING_PRODUCT_FOLDER;
            }
                processMonitor.updateState(validation, 15);

            // Validate SFTP credentials for both setup modes
            if (project.getHost() == null || project.getHost().trim().isEmpty()) {
                logger.severe("SFTP Host is not configured");
                return ValidationResult.MISSING_SFTP_HOST;
            }
            processMonitor.updateState(validation, 20);

            if (project.getHostUser() == null || project.getHostUser().trim().isEmpty()) {
                logger.severe("SFTP Username is not configured");
                return ValidationResult.MISSING_SFTP_USER;
            }
            processMonitor.updateState(validation, 25);

            if (project.getHostPass() == null || project.getHostPass().trim().isEmpty()) {
                logger.severe("SFTP Password is not configured");
                return ValidationResult.MISSING_SFTP_PASSWORD;
            }
            processMonitor.updateState(validation, 30);

            // Validate App URL
            if (project.getNmsAppURL() == null || project.getNmsAppURL().trim().isEmpty()) {
                logger.severe("App URL is not configured");
                return ValidationResult.MISSING_APP_URL;
            }
            processMonitor.updateState(validation, 32);

            // Validate SFTP host format
            if (!isValidHostFormat(project.getHost())) {
                logger.severe("Invalid SFTP host format: " + project.getHost());
                return ValidationResult.INVALID_PATHS;
            }
            processMonitor.updateState(validation, 37);

            // Validate SFTP port if specified
            if (project.getHostPort() > 0 && (project.getHostPort() < 1 || project.getHostPort() > 65535)) {
                logger.severe("Invalid SFTP port number: " + project.getHostPort() + " (must be between 1-65535)");
                return ValidationResult.INVALID_PATHS;
            }
            processMonitor.updateState(validation, 42);

            if (setupMode == SetupMode.FULL_CHECKOUT) {
                if (project.getSvnRepo() == null || project.getSvnRepo().trim().isEmpty() || project.getSvnRepo().trim().equals("NULL")) {
                    return ValidationResult.MISSING_SVN_URL;
                }
                processMonitor.updateState(validation, 47);

                File jconfigFolder = new File(project.getJconfigPathForBuild());
                if (jconfigFolder.exists() && jconfigFolder.isDirectory() &&
                        jconfigFolder.listFiles() != null && jconfigFolder.listFiles().length > 0) {

                    final boolean[] proceed = {false};
                    CountDownLatch latch = new CountDownLatch(1);

                    Platform.runLater(() -> {
                        Optional<ButtonType> result = DialogUtil.showConfirmationDialog(
                                "SVN Checkout",
                                "The project folder chosen is not empty. Do you want to proceed with clean checkout?\nWarning: This will delete entire local project folder, unsaved changes will be lost.",
                                "Ok to delete project directory."
                        );

                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            proceed[0] = true;
                        }
                        latch.countDown();
                    });

                    // Wait for the user to respond
                    latch.await();

                    if (!proceed[0]) {
                        return ValidationResult.NOT_ALLOWED;
                    }
                }
                processMonitor.updateState(validation, 52);
            } else {
                // For PRODUCT_ONLY mode, still validate SFTP credentials but skip SVN validation
                processMonitor.updateState(validation, 52);
                
            }

            logger.info("All input validations completed successfully");
            return ValidationResult.SUCCESS;
            
        } catch (Exception e){
            LoggerUtil.error(e);
            return ValidationResult.EXCEPTION;
        }
    }

        /**
     * Validates the project folder to ensure it contains a jconfig folder
     * If jconfig is found, returns the path to the folder containing jconfig
     * If jconfig is not found, returns null
     * 
     * @param projectPath The initial project path to validate
     * @return The validated project path containing jconfig, or null if not found
     */
    private String validateProjectFolder(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            logger.warning("Project path is null or empty");
            return null;
        }

        File projectFolder = new File(projectPath);
        if (!projectFolder.exists() || !projectFolder.isDirectory()) {
            logger.warning("Project folder does not exist or is not a directory: " + projectPath);
            return null;
        }

        // Check if the provided path itself ends with jconfig folder, return it if it does
        if (projectFolder.getName().equals("jconfig")) {
            logger.info("Provided path itself is a jconfig folder: " + projectPath);
            return projectPath;
        }

        // First, check if jconfig exists directly in the project folder
        File jconfigFolder = new File(projectFolder, "jconfig");
        if (jconfigFolder.exists() && jconfigFolder.isDirectory()) {
            String jconfigFullPath = jconfigFolder.getAbsolutePath();
            logger.info("Found jconfig folder directly in project folder: " + jconfigFullPath);
            return jconfigFullPath;
        }

        // If not found directly, search recursively in subdirectories
        String jconfigPath = findJconfigRecursively(projectFolder);
        if (jconfigPath != null) {
            logger.info("Found jconfig folder in subdirectory: " + jconfigPath);
            return jconfigPath;
        }

        logger.warning("jconfig folder not found in project folder or any subdirectories: " + projectPath);
        return null;
    }

    /**
     * Recursively searches for jconfig folder in the given directory and its subdirectories
     * 
     * @param directory The directory to search in
     * @return The path to the directory containing jconfig, or null if not found
     */
    private String findJconfigRecursively(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        // First check if jconfig exists in current directory
        for (File file : files) {
            if (file.isDirectory() && "jconfig".equals(file.getName())) {
                return directory.getAbsolutePath();
            }
        }

        // If not found, search in subdirectories
        for (File file : files) {
            if (file.isDirectory()) {
                String result = findJconfigRecursively(file);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Processes build files with comprehensive validation and monitoring
     * 
     * @param project The project entity containing build configuration
     */
    private void processBuildFiles(ProjectEntity project) {
        String env_name = project.getNmsEnvVar();
        String jconfigPath = project.getJconfigPathForBuild();
        
        // Process build.properties file
        processBuildPropertiesFile(jconfigPath, env_name);
        
        // Check if process should continue
        if (!processMonitor.isRunning()) return;
        
        // Process build.xml file
        processBuildXmlFile(jconfigPath, env_name);
    }

    /**
     * Processes build.properties file with validation and monitoring
     * 
     * @param jconfigPath The path to the jconfig directory
     * @param env_name The environment variable name
     */
    private void processBuildPropertiesFile(String jconfigPath, String env_name) {
        String buildPropertiesPath = jconfigPath + "/build.properties";
        
        processMonitor.addStep("build_properties", "Processing build.properties file");
        
        // Validate file exists before processing
        File buildPropertiesFile = new File(buildPropertiesPath);
        if (!buildPropertiesFile.exists()) {
            processMonitor.markFailed("build_properties", "build.properties file not found at: " + buildPropertiesPath);
            return;
        }
        
        if (!buildPropertiesFile.isFile()) {
            processMonitor.markFailed("build_properties", "build.properties path is not a file: " + buildPropertiesPath);
            return;
        }
        
        if (!buildPropertiesFile.canRead()) {
            processMonitor.markFailed("build_properties", "build.properties file is not readable: " + buildPropertiesPath);
            return;
        }
        
        processMonitor.updateState("build_properties", 25);
        
        try {
            // Process the file
            ManageFile.replaceTextInFiles(List.of(buildPropertiesPath), "NMS_HOME", env_name);
            
            // Validate file still exists after processing
            if (!buildPropertiesFile.exists()) {
                processMonitor.markFailed("build_properties", "build.properties file was deleted during processing");
                return;
            }
            
            if (!buildPropertiesFile.canRead()) {
                processMonitor.markFailed("build_properties", "build.properties file became unreadable after processing");
                return;
            }
            
            processMonitor.updateState("build_properties", 100);
            processMonitor.markComplete("build_properties", "build.properties file processed successfully");
            
        } catch (Exception e) {
            logger.severe("Failed to process build.properties: " + e.getMessage());
            processMonitor.markFailed("build_properties", "Failed to process build.properties: " + e.getMessage());
        }
    }

    /**
     * Processes build.xml file with validation and monitoring
     * 
     * @param jconfigPath The path to the jconfig directory
     * @param env_name The environment variable name
     */
    private void processBuildXmlFile(String jconfigPath, String env_name) {
        String buildXmlPath = jconfigPath + "/build.xml";
        
        processMonitor.addStep("build_xml", "Processing build.xml file");
        
        // Validate file exists before processing
        File buildXmlFile = new File(buildXmlPath);
        if (!buildXmlFile.exists()) {
            processMonitor.markFailed("build_xml", "build.xml file not found at: " + buildXmlPath);
            return;
        }
        
        if (!buildXmlFile.isFile()) {
            processMonitor.markFailed("build_xml", "build.xml path is not a file: " + buildXmlPath);
            return;
        }
        
        if (!buildXmlFile.canRead()) {
            processMonitor.markFailed("build_xml", "build.xml file is not readable: " + buildXmlPath);
            return;
        }
        
        processMonitor.updateState("build_xml", 25);
        
        try {
            // Process the file
            ManageFile.replaceTextInFiles(List.of(buildXmlPath), "NMS_HOME", env_name);
            
            // Validate file still exists after processing
            if (!buildXmlFile.exists()) {
                processMonitor.markFailed("build_xml", "build.xml file was deleted during processing");
                return;
            }
            
            if (!buildXmlFile.canRead()) {
                processMonitor.markFailed("build_xml", "build.xml file became unreadable after processing");
                return;
            }
            
            processMonitor.updateState("build_xml", 100);
            processMonitor.markComplete("build_xml", "build.xml file processed successfully");
            
        } catch (Exception e) {
            logger.severe("Failed to process build.xml: " + e.getMessage());
            processMonitor.markFailed("build_xml", "Failed to process build.xml: " + e.getMessage());
        }
    }

    /**
     * Comprehensive validation of required tools, files, and connectivity
     * 
     * @return ValidationResult indicating success or specific failure
     */
    private ValidationResult validateRequiredToolsAndConnectivity() {
        try {
            processMonitor.updateState("tool_validation", 10);
            
            // 1. Validate Java installation
            if (!validateJavaInstallation()) {
                logger.severe("Java installation validation failed");
                return ValidationResult.MISSING_JAVA;
            }
            processMonitor.updateState("tool_validation", 20);
            
            // 2. Validate Launch4j installation
            if (!validateLaunch4jInstallation()) {
                logger.severe("Launch4j installation validation failed");
                return ValidationResult.MISSING_LAUNCH4J;
            }
            processMonitor.updateState("tool_validation", 30);
            
            // 3. Validate NSIS installation
//            if (!validateNSISInstallation()) {
//                logger.severe("NSIS installation validation failed");
//                return ValidationResult.MISSING_NSIS;
//            }
//            processMonitor.updateState("tool_validation", 40);
            
             //4. Validate network connectivity
             if (!appUrlConnectivity()) {
                 logger.severe("NMS Apps URL connectivity validation failed");
                 return ValidationResult.NETWORK_CONNECTION_FAILED;
             }
            processMonitor.updateState("tool_validation", 50);
            
            // 5. Validate SFTP connectivity (if SFTP mode is enabled)

            if (!validateSFTPConnectivity()) {
                logger.severe("SFTP connectivity validation failed");
                return ValidationResult.SFTP_CONNECTION_FAILED;
            }
            processMonitor.updateState("tool_validation", 70);

            
            // 6. Validate VPN connectivity (basic check)
//            if (!validateVPNConnectivity()) {
//                logger.warning("VPN connectivity validation failed - continuing with warning");
//                // Don't fail the process for VPN, just log a warning
//            }
//            processMonitor.updateState("tool_validation", 80);
            
            // 7. Validate required directories and permissions
            if (!validateDirectoryPermissions()) {
                logger.severe("Directory permissions validation failed");
                return ValidationResult.INVALID_PATHS;
            }
            processMonitor.updateState("tool_validation", 90);
            
            // 8. Validate external tools availability
            if (!validateExternalTools()) {
                logger.severe("External tools validation failed");
                return ValidationResult.MISSING_TOOLS;
            }
            processMonitor.updateState("tool_validation", 100);
            
            logger.info("All required tools and connectivity validated successfully");
        return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("Tool validation failed with exception: " + e.getMessage());
            return ValidationResult.EXCEPTION;
        }
    }

    /**
     * Validates Java installation with detailed path checking and error reporting
     */
    private boolean validateJavaInstallation() {
        try {
            StringBuilder validationDetails = new StringBuilder();
            validationDetails.append("Java installation validation details:\n");
            
            // Check JAVA_HOME environment variable
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null || javaHome.trim().isEmpty()) {
                validationDetails.append("✗ JAVA_HOME environment variable is not set\n");
                validationDetails.append("  Expected: JAVA_HOME should point to Java installation directory\n");
                validationDetails.append("  Example: JAVA_HOME=C:\\Program Files\\Java\\jdk-11.0.12\n");
                logger.severe("JAVA_HOME environment variable is not set");
                logger.severe(validationDetails.toString());
                return false;
            }
            
            validationDetails.append("✓ JAVA_HOME environment variable is set: ").append(javaHome).append("\n");
            
            // Check if JAVA_HOME points to a valid directory
            File javaHomeDir = new File(javaHome);
            if (!javaHomeDir.exists()) {
                validationDetails.append("✗ JAVA_HOME directory does not exist: ").append(javaHome).append("\n");
                validationDetails.append("  Expected directory: ").append(javaHomeDir.getAbsolutePath()).append("\n");
                logger.severe("JAVA_HOME directory does not exist: " + javaHome);
                logger.severe(validationDetails.toString());
                return false;
            }
            
            if (!javaHomeDir.isDirectory()) {
                validationDetails.append("✗ JAVA_HOME path is not a directory: ").append(javaHome).append("\n");
                validationDetails.append("  Expected: Directory containing Java installation\n");
                logger.severe("JAVA_HOME path is not a directory: " + javaHome);
                logger.severe(validationDetails.toString());
                return false;
            }
            
            validationDetails.append("✓ JAVA_HOME directory exists: ").append(javaHomeDir.getAbsolutePath()).append("\n");
            
            // Check for Java executable
            File javaExe = new File(javaHomeDir, "bin/java.exe");
            if (!javaExe.exists()) {
                validationDetails.append("✗ Java executable not found at: ").append(javaExe.getAbsolutePath()).append("\n");
                validationDetails.append("  Expected file: java.exe in bin directory\n");
                validationDetails.append("  JAVA_HOME/bin directory contents: ");
                File binDir = new File(javaHomeDir, "bin");
                if (binDir.exists() && binDir.isDirectory()) {
                    File[] files = binDir.listFiles();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            validationDetails.append(file.getName()).append(", ");
                        }
                        validationDetails.setLength(validationDetails.length() - 2); // Remove last comma
                    } else {
                        validationDetails.append("(empty directory)");
                    }
                } else {
                    validationDetails.append("(bin directory does not exist)");
                }
                validationDetails.append("\n");
                logger.severe("Java executable not found at: " + javaExe.getAbsolutePath());
                logger.severe(validationDetails.toString());
                return false;
            }
            
            if (!javaExe.isFile()) {
                validationDetails.append("✗ Java executable path is not a file: ").append(javaExe.getAbsolutePath()).append("\n");
                logger.severe("Java executable path is not a file: " + javaExe.getAbsolutePath());
                logger.severe(validationDetails.toString());
                return false;
            }
            
            validationDetails.append("✓ Java executable found at: ").append(javaExe.getAbsolutePath()).append("\n");
            
            // Check for JRE directory (optional for JDK installations)
            File jreDir = new File(javaHomeDir, "jre");
            if (!jreDir.exists() || !jreDir.isDirectory()) {
                validationDetails.append("⚠ JRE directory not found at: ").append(jreDir.getAbsolutePath()).append("\n");
                validationDetails.append("  Note: This is normal for JDK installations, JRE is included in JDK\n");
            } else {
                validationDetails.append("✓ JRE directory found at: ").append(jreDir.getAbsolutePath()).append("\n");
            }
            
            // Check for javac (JDK indicator)
            File javacExe = new File(javaHomeDir, "bin/javac.exe");
            if (javacExe.exists() && javacExe.isFile()) {
                validationDetails.append("✓ JDK installation detected (javac.exe found)\n");
            } else {
                validationDetails.append("⚠ JRE installation detected (javac.exe not found)\n");
                validationDetails.append("  Note: JDK is recommended for development\n");
            }
            
            // Try to get Java version
            try {
                ProcessBuilder pb = new ProcessBuilder(javaExe.getAbsolutePath(), "-version");
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String versionLine = reader.readLine();
                    if (versionLine != null) {
                        validationDetails.append("✓ Java version: ").append(versionLine).append("\n");
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                validationDetails.append("⚠ Could not determine Java version: ").append(e.getMessage()).append("\n");
            }
            
            logger.info("Java installation validated successfully: " + javaHome);
            logger.info(validationDetails.toString());
            return true;
            
        } catch (Exception e) {
            logger.severe("Java validation failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates Launch4j installation with detailed path checking and error reporting
     */
    private boolean validateLaunch4jInstallation() {
        try {
            StringBuilder validationDetails = new StringBuilder();
            validationDetails.append("Launch4j validation details:\n");
            
            // Check environment variable first
            String launch4jHome = System.getenv("LAUNCH4J_HOME");
            if (launch4jHome != null && !launch4jHome.trim().isEmpty()) {
                validationDetails.append("✓ LAUNCH4J_HOME environment variable is set: ").append(launch4jHome).append("\n");
                
                // Check if LAUNCH4J_HOME points to a directory
                File launch4jHomeDir = new File(launch4jHome);
                if (launch4jHomeDir.exists() && launch4jHomeDir.isDirectory()) {
                    validationDetails.append("✓ LAUNCH4J_HOME directory exists: ").append(launch4jHomeDir.getAbsolutePath()).append("\n");
                    
                    // Check for launch4jc.exe in LAUNCH4J_HOME
                    File exeFile = new File(launch4jHomeDir, "launch4jc.exe");
                    if (exeFile.exists() && exeFile.isFile()) {
                        validationDetails.append("✓ Launch4j executable found at: ").append(exeFile.getAbsolutePath()).append("\n");
                        logger.info("Launch4j found at: " + exeFile.getAbsolutePath());
                        logger.info(validationDetails.toString());
                        return true;
                    } else {
                        validationDetails.append("✗ Expected file not found: ").append(exeFile.getAbsolutePath()).append("\n");
                        validationDetails.append("  Expected file: launch4jc.exe\n");
                        validationDetails.append("  Directory contents: ");
                        File[] files = launch4jHomeDir.listFiles();
                        if (files != null && files.length > 0) {
                            for (File file : files) {
                                validationDetails.append(file.getName()).append(", ");
                            }
                            validationDetails.setLength(validationDetails.length() - 2); // Remove last comma
                        } else {
                            validationDetails.append("(empty directory)");
                        }
                        validationDetails.append("\n");
                    }
                } else {
                    validationDetails.append("✗ LAUNCH4J_HOME directory does not exist or is not a directory: ").append(launch4jHome).append("\n");
                }
            } else {
                validationDetails.append("✗ LAUNCH4J_HOME environment variable is not set\n");
            }
            
            // Check standard installation paths
            String[] standardPaths = {
                "C:\\Program Files\\Launch4j\\launch4jc.exe",
                "C:\\Program Files (x86)\\Launch4j\\launch4jc.exe"
            };
            
            validationDetails.append("\nChecking standard installation paths:\n");
            for (String path : standardPaths) {
                File exeFile = new File(path);
                if (exeFile.exists() && exeFile.isFile()) {
                    validationDetails.append("✓ Launch4j found at: ").append(exeFile.getAbsolutePath()).append("\n");
                    logger.info("Launch4j found at: " + exeFile.getAbsolutePath());
                    logger.info(validationDetails.toString());
                    return true;
                } else {
                    validationDetails.append("✗ Not found: ").append(path).append("\n");
                    
                    // Check if parent directory exists
                    File parentDir = exeFile.getParentFile();
                    if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
                        validationDetails.append("  Parent directory exists: ").append(parentDir.getAbsolutePath()).append("\n");
                        validationDetails.append("  Parent directory contents: ");
                        File[] files = parentDir.listFiles();
                        if (files != null && files.length > 0) {
                            for (File file : files) {
                                validationDetails.append(file.getName()).append(", ");
                            }
                            validationDetails.setLength(validationDetails.length() - 2); // Remove last comma
                        } else {
                            validationDetails.append("(empty directory)");
                        }
                        validationDetails.append("\n");
                    } else {
                        validationDetails.append("  Parent directory does not exist: ").append(parentDir != null ? parentDir.getAbsolutePath() : "null").append("\n");
                    }
                }
            }
            
            // Check PATH environment variable
            validationDetails.append("\nChecking PATH environment variable:\n");
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null && !pathEnv.trim().isEmpty()) {
                String[] pathDirs = pathEnv.split(";");
                boolean foundInPath = false;
                for (String pathDir : pathDirs) {
                    if (pathDir.trim().toLowerCase().contains("launch4j")) {
                        File pathDirFile = new File(pathDir.trim());
                        if (pathDirFile.exists() && pathDirFile.isDirectory()) {
                            File exeFile = new File(pathDirFile, "launch4jc.exe");
                            if (exeFile.exists() && exeFile.isFile()) {
                                validationDetails.append("✓ Launch4j found in PATH at: ").append(exeFile.getAbsolutePath()).append("\n");
                                logger.info("Launch4j found at: " + exeFile.getAbsolutePath());
                                logger.info(validationDetails.toString());
                                return true;
                            }
                        }
                        foundInPath = true;
                        validationDetails.append("  Found Launch4j directory in PATH: ").append(pathDir.trim()).append("\n");
                    }
                }
                if (!foundInPath) {
                    validationDetails.append("  No Launch4j directories found in PATH\n");
                }
            } else {
                validationDetails.append("  PATH environment variable is not set\n");
            }
            
            // Provide installation instructions
            validationDetails.append("\n=== Launch4j Installation Instructions ===\n");
            validationDetails.append("1. Download Launch4j from: https://launch4j.sourceforge.net/\n");
            validationDetails.append("2. Install to default location (C:\\Program Files\\Launch4j\\ or C:\\Program Files (x86)\\Launch4j\\)\n");
            validationDetails.append("3. OR set LAUNCH4J_HOME environment variable to point to Launch4j installation directory\n");
            validationDetails.append("4. Expected executable file: launch4jc.exe\n");
            validationDetails.append("5. Verify installation by running: launch4jc.exe --version\n");
            
            logger.severe("Launch4j not found in any expected location");
            logger.severe(validationDetails.toString());
            return false;
            
        } catch (Exception e) {
            logger.severe("Launch4j validation failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates NSIS installation with detailed path checking and error reporting
     */
    private boolean validateNSISInstallation() {
        try {
            StringBuilder validationDetails = new StringBuilder();
            validationDetails.append("NSIS validation details:\n");
            
            // Check environment variable first
            String nsisHome = System.getenv("NSIS_HOME");
            if (nsisHome != null && !nsisHome.trim().isEmpty()) {
                validationDetails.append("✓ NSIS_HOME environment variable is set: ").append(nsisHome).append("\n");
                
                // Check if NSIS_HOME points to a directory
                File nsisHomeDir = new File(nsisHome);
                if (nsisHomeDir.exists() && nsisHomeDir.isDirectory()) {
                    validationDetails.append("✓ NSIS_HOME directory exists: ").append(nsisHomeDir.getAbsolutePath()).append("\n");
                    
                    // Check for makensisw.exe in NSIS_HOME
                    File exeFile = new File(nsisHomeDir, "makensisw.exe");
                    if (exeFile.exists() && exeFile.isFile()) {
                        validationDetails.append("✓ NSIS executable found at: ").append(exeFile.getAbsolutePath()).append("\n");
                        logger.info("NSIS found at: " + exeFile.getAbsolutePath());
                        logger.info(validationDetails.toString());
                        return true;
                    } else {
                        validationDetails.append("✗ Expected file not found: ").append(exeFile.getAbsolutePath()).append("\n");
                        validationDetails.append("  Expected file: makensisw.exe\n");
                        validationDetails.append("  Directory contents: ");
                        File[] files = nsisHomeDir.listFiles();
                        if (files != null && files.length > 0) {
                            for (File file : files) {
                                validationDetails.append(file.getName()).append(", ");
                            }
                            validationDetails.setLength(validationDetails.length() - 2); // Remove last comma
                        } else {
                            validationDetails.append("(empty directory)");
                        }
                        validationDetails.append("\n");
                    }
                } else {
                    validationDetails.append("✗ NSIS_HOME directory does not exist or is not a directory: ").append(nsisHome).append("\n");
                }
            } else {
                validationDetails.append("✗ NSIS_HOME environment variable is not set\n");
            }
            
            // Check standard installation paths
            String[] standardPaths = {
                "C:\\Program Files\\NSIS\\makensisw.exe",
                "C:\\Program Files (x86)\\NSIS\\makensisw.exe"
            };
            
            validationDetails.append("\nChecking standard installation paths:\n");
            for (String path : standardPaths) {
                File exeFile = new File(path);
                if (exeFile.exists() && exeFile.isFile()) {
                    validationDetails.append("✓ NSIS found at: ").append(exeFile.getAbsolutePath()).append("\n");
                    logger.info("NSIS found at: " + exeFile.getAbsolutePath());
                    logger.info(validationDetails.toString());
                    return true;
                } else {
                    validationDetails.append("✗ Not found: ").append(path).append("\n");
                    
                    // Check if parent directory exists
                    File parentDir = exeFile.getParentFile();
                    if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
                        validationDetails.append("  Parent directory exists: ").append(parentDir.getAbsolutePath()).append("\n");
                        validationDetails.append("  Parent directory contents: ");
                        File[] files = parentDir.listFiles();
                        if (files != null && files.length > 0) {
                            for (File file : files) {
                                validationDetails.append(file.getName()).append(", ");
                            }
                            validationDetails.setLength(validationDetails.length() - 2); // Remove last comma
                        } else {
                            validationDetails.append("(empty directory)");
                        }
                        validationDetails.append("\n");
                    } else {
                        validationDetails.append("  Parent directory does not exist: ").append(parentDir != null ? parentDir.getAbsolutePath() : "null").append("\n");
                    }
                }
            }
            
            // Check PATH environment variable
            validationDetails.append("\nChecking PATH environment variable:\n");
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null && !pathEnv.trim().isEmpty()) {
                String[] pathDirs = pathEnv.split(";");
                boolean foundInPath = false;
                for (String pathDir : pathDirs) {
                    if (pathDir.trim().toLowerCase().contains("nsis")) {
                        File pathDirFile = new File(pathDir.trim());
                        if (pathDirFile.exists() && pathDirFile.isDirectory()) {
                            File exeFile = new File(pathDirFile, "makensisw.exe");
                            if (exeFile.exists() && exeFile.isFile()) {
                                validationDetails.append("✓ NSIS found in PATH at: ").append(exeFile.getAbsolutePath()).append("\n");
                                logger.info("NSIS found at: " + exeFile.getAbsolutePath());
                                logger.info(validationDetails.toString());
                                return true;
                            }
                        }
                        foundInPath = true;
                        validationDetails.append("  Found NSIS directory in PATH: ").append(pathDir.trim()).append("\n");
                    }
                }
                if (!foundInPath) {
                    validationDetails.append("  No NSIS directories found in PATH\n");
                }
            } else {
                validationDetails.append("  PATH environment variable is not set\n");
            }
            
            // Provide installation instructions
            validationDetails.append("\n=== NSIS Installation Instructions ===\n");
            validationDetails.append("1. Download NSIS from: https://nsis.sourceforge.io/Download\n");
            validationDetails.append("2. Install to default location (C:\\Program Files\\NSIS\\ or C:\\Program Files (x86)\\NSIS\\)\n");
            validationDetails.append("3. OR set NSIS_HOME environment variable to point to NSIS installation directory\n");
            validationDetails.append("4. Expected executable file: makensisw.exe\n");
            validationDetails.append("5. Verify installation by running: makensisw.exe --version\n");
            
            logger.severe("NSIS not found in any expected location");
            logger.severe(validationDetails.toString());
            return false;
            
        } catch (Exception e) {
            logger.severe("NSIS validation failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates basic network connectivity
     */
    private boolean appUrlConnectivity() {
        try {
            // Test basic internet connectivity
            URL url = new URL(this.project.getNmsAppURL());
            
            // Bypass SSL certificate verification for development purposes
            if (url.getProtocol().equals("https")) {
                disableSSLVerification();
            }
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            if (responseCode == 200) {
                logger.info("App URL connectivity validated successfully");
                return true;
            } else {
                logger.severe("App URL connectivity test failed with response code: " + responseCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("App URL connectivity validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disables SSL certificate verification for development purposes
     */
    private void disableSSLVerification() {
        try {
            // Install a trust manager that trusts all certificates
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Set up a context that uses the trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create an all-trusting host name verifier
            HostnameVerifier allHostsValid = (_, _) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            logger.info("SSL certificate verification disabled for connectivity test");
        } catch (Exception e) {
            logger.warning("Failed to disable SSL verification: " + e.getMessage());
        }
    }

    /**
     * Validates SFTP connectivity to the project server
     */
    private boolean validateSFTPConnectivity() {
        try {
            if (project.getHost() == null || project.getHostUser() == null || project.getHostPass() == null) {
                logger.severe("SFTP credentials not configured in project");
                return false;
            }
            
            // Test SFTP connection using JSch
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            com.jcraft.jsch.Session session = jsch.getSession(
                project.getHostUser(), 
                project.getHost(), 
                project.getHostPort() > 0 ? project.getHostPort() : 22
            );
            session.setPassword(project.getHostPass());
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(10000); // 10 second timeout
            
            session.connect();
            boolean connected = session.isConnected();
            session.disconnect();
            
            if (connected) {
                logger.info("SFTP connectivity validated successfully to: " + project.getHost());
                return true;
            } else {
                logger.severe("SFTP connection failed to: " + project.getHost());
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("SFTP connectivity validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates VPN connectivity (basic check)
     */
    private boolean validateVPNConnectivity() {
        try {
            // Check if VPN adapter is active by looking for common VPN interfaces
            String[] vpnCommands = {
                "netsh interface show interface",
                "ipconfig /all"
            };
            
            for (String command : vpnCommands) {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.toLowerCase().contains("vpn") || 
                            line.toLowerCase().contains("cisco") || 
                            line.toLowerCase().contains("tunnel")) {
                            logger.info("VPN interface detected: " + line.trim());
                            return true;
                        }
                    }
                }
                
                process.waitFor();
            }
            
            logger.warning("No VPN interface detected - this may be normal for some environments");
            return true; // Don't fail for VPN, just warn
            
        } catch (Exception e) {
            logger.warning("VPN connectivity check failed: " + e.getMessage());
            return true; // Don't fail for VPN, just warn
        }
    }

    /**
     * Validates directory permissions
     */
    private boolean validateDirectoryPermissions() {
        try {
            // Check if we can write to the project directory
            String exePath = project.getExePath();
            if (exePath != null) {
                File exeDir = new File(exePath);
                if (!exeDir.exists()) {
                    if (!exeDir.mkdirs()) {
                        logger.severe("Cannot create project directory: " + exePath);
                        return false;
                    }
                } else if (!exeDir.canWrite()) {
                    logger.severe("Cannot write to project directory: " + exePath);
                    return false;
                }
            }
            
            // Check if we can write to the jconfig directory
            String jconfigPath = project.getJconfigPathForBuild();
            if (jconfigPath != null) {
                File jconfigDir = new File(jconfigPath);
                if (!jconfigDir.exists()) {
                    if (!jconfigDir.mkdirs()) {
                        logger.severe("Cannot create jconfig directory: " + jconfigPath);
                        return false;
                    }
                } else if (!jconfigDir.canWrite()) {
                    logger.severe("Cannot write to jconfig directory: " + jconfigPath);
                    return false;
                }
            }
            
            logger.info("Directory permissions validated successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Directory permissions validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates external tools availability
     */
    private boolean validateExternalTools() {
        try {
            // Check for required external tools
            String[] requiredTools = {
                "java", "javac", "ant", "zip", "unzip"
            };
            
            for (String tool : requiredTools) {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "where " + tool);
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line == null || line.trim().isEmpty()) {
                        logger.warning("External tool not found in PATH: " + tool);
                        // Don't fail for missing external tools, just warn
                    } else {
                        logger.info("External tool found: " + tool + " at " + line.trim());
                    }
                }
                
                process.waitFor();
            }
            
            logger.info("External tools validation completed");
            return true;
            
        } catch (Exception e) {
            logger.warning("External tools validation failed: " + e.getMessage());
            return true; // Don't fail for external tools, just warn
        }
    }

    /**
     * Validates SFTP host format
     * 
     * @param host The host string to validate
     * @return true if the host format is valid, false otherwise
     */
    private boolean isValidHostFormat(String host) {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        
        String trimmedHost = host.trim();
        
        // Check for valid hostname patterns
        // Allow: domain.com, subdomain.domain.com, IP addresses, localhost
        String hostPattern = "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$|" +  // Domain names
                           "^([0-9]{1,3}\\.){3}[0-9]{1,3}$|" +  // IPv4 addresses
                           "^localhost$|" +  // localhost
                           "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$";  // Single hostname
        
        if (!trimmedHost.matches(hostPattern)) {
            logger.warning("Host format validation failed for: " + trimmedHost);
            return false;
        }
        
        // Additional validation for IP addresses
        if (trimmedHost.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
            String[] parts = trimmedHost.split("\\.");
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    logger.warning("Invalid IP address range: " + trimmedHost);
                    return false;
                }
            }
        }
        
        logger.info("Host format validation passed for: " + trimmedHost);
        return true;
    }

    public  String adjustUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            // Construct the base URL up to /nms/
            return url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/nms/";
        } catch (MalformedURLException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            return null;
        }
    }

}
