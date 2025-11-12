package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.controller.MainController;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.CreateInstallerCommand;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.SFTPDownloadAndUnzip;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.FileFetcher;
import com.nms.support.nms_support.service.buildTabPack.patchUpdate.DirectoryProcessor;
import com.nms.support.nms_support.service.globalPack.sshj.UnifiedSSHManager;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager;
import net.schmizz.sshj.sftp.SFTPClient;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNCancelException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
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
        PATCH_UPGRADE,          // Product installation with project validation and build files update
        PRODUCT_ONLY,           // Product installation only, no project validation
        FULL_CHECKOUT,
        PROJECT_AND_PRODUCT_FROM_SERVER,
        PROJECT_ONLY_SVN,
        PROJECT_ONLY_SERVER,
        HAS_JAVA_MODE,          // Java already extracted, perform loading resources, exe creation, and build file updates
        BUILD_FILES_ONLY        // Standalone build file download and update
    }
    
    public enum ValidationResult {
        SUCCESS,
        MISSING_ENV_VAR,
        MISSING_PRODUCT_FOLDER,
        MISSING_SVN_URL,
        MISSING_SVN_CREDENTIALS,
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
        SVN_CONNECTIVITY_FAILED,
        EXCEPTION, NOT_ALLOWED, INVALID_PATHS,
        MISSING_SERVER_DETAILS,
        SERVER_CONNECTION_FAILED,
        PROJECT_DOWNLOAD_FAILED,
        PROJECT_EXTRACTION_FAILED,
        MISSING_NMS_CONFIG,
        MISSING_NMS_HOME,
        SERVER_ENV_VALIDATION_FAILED,
        PRODUCT_ENV_VALIDATION_FAILED,
        SERVER_PATH_VALIDATION_FAILED,
        APP_URL_NOT_RECHABLE, PRODUCT_PATH_VALIDATION_FAILED,
        CANCELLED
    }
    
    private final ProjectEntity project;
    private final ProcessMonitor processMonitor;
    private final SetupMode setupMode;
    private final String sshSessionPurpose; // SSH session purpose based on setup mode
    private MainController mainController; // Reference to main controller for dialog access
    
    /**
     * Helper method to check if a boolean validation failure is due to user cancellation
     * ONLY use this for boolean validations where we can't distinguish cancellation from failure
     * 
     * @return true if the process was cancelled by user, false otherwise
     */
    private boolean wasCancelledByUser() {
        // Only return true if explicitly cancelled, not just "not running"
        // ProcessMonitor.isRunning() is false for BOTH cancellation AND failures
        // We should only treat it as cancelled if no other failure was already marked
        return !processMonitor.isRunning() && !processMonitor.hasFailed();
    }
    
    /**
     * Close the SSH session for this setup
     * Call this only on validation failure to clean up
     */
    private void closeSSHSession() {
        try {
            logger.info("Closing SSH session for purpose: " + sshSessionPurpose);
            UnifiedSSHManager.closeSession(
                project.getHost(),
                project.getHostPort(),
                project.isUseLdap() ? project.getLdapUser() : project.getHostUser(),
                project.getTargetUser(),
                sshSessionPurpose
            );
        } catch (Exception e) {
            logger.warning("Failed to close SSH session: " + e.getMessage());
        }
    }
    
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
        this.setupMode = isFull ? SetupMode.FULL_CHECKOUT : SetupMode.PATCH_UPGRADE;
        this.sshSessionPurpose = setupMode.name().toLowerCase(); // e.g., "full_checkout", "patch_upgrade"
    }
    
    public SetupService(ProjectEntity project, ProcessMonitor processMonitor, SetupMode setupMode) {
        this.project = project;
        this.processMonitor = processMonitor;
        this.setupMode = setupMode;
        this.sshSessionPurpose = setupMode.name().toLowerCase(); // e.g., "project_only_server", "product_only"
    }
    
    /**
     * Main setup execution method
     */
    public void executeSetup(MainController mc) {
        this.mainController = mc; // Store reference for dialog access
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
            ProgressCallback toolValidationCallback = new ProcessMonitorAdapter(processMonitor, "tool_validation");
            ValidationResult toolValidationRes = validateRequiredToolsAndConnectivity(toolValidationCallback);
            if (toolValidationRes != ValidationResult.SUCCESS) {
                processMonitor.markFailed("tool_validation", toolValidationRes.toString());
                return;
            } else {
                processMonitor.markComplete("tool_validation", "All required tools and connectivity validated successfully");
            }
            if(!processMonitor.isRunning()) return;

            // Validate server environment variables for server-based operations
            ValidationResult serverEnvValidationRes = validateServerEnvironmentVariables(setupMode);
            if (serverEnvValidationRes != ValidationResult.SUCCESS) {
                processMonitor.markFailed("server_env_validation", serverEnvValidationRes.toString());
                return;
            }
            
            processMonitor.markComplete(validation, "Pre-validation completed successfully");
            if(!processMonitor.isRunning()) return;

            // Environment variable creation and validation
            processMonitor.addStep("env_validation", "Creating environment variables");
            if (!validateAndCreateEnvironmentVariable(project)) {
                // Check if failure is due to cancellation
                if (wasCancelledByUser()) {
                    processMonitor.markFailed("env_validation", "Environment variable validation cancelled by user");
                    return;
                }
                processMonitor.markFailed("env_validation", "Environment variable validation and creation failed");
                return;
            }
            processMonitor.markComplete("env_validation", "Environment variable validation completed successfully");
            
            if(!processMonitor.isRunning()) return;
            
            // Kill running exe processes before product setup (first step in product-containing setups)
            // This prevents issues with demon processes that haven't created log files yet
            if (setupMode != SetupMode.PROJECT_ONLY_SVN && setupMode != SetupMode.PROJECT_ONLY_SERVER) {
                processMonitor.addStep("process_cleanup", "Killing running exe processes");
                try {
                    killAllExeProcessesFromLogs(project);
                    processMonitor.markComplete("process_cleanup", "All exe processes terminated successfully");
                } catch (Exception e) {
                    // Don't fail the setup if process killing fails, just warn
                    logger.warning("Error killing processes during setup: " + e.getMessage());
                    processMonitor.markComplete("process_cleanup", "Process cleanup completed with warnings");
                }
                if(!processMonitor.isRunning()) return;
            }
            
            // Handle different setup modes
            switch (setupMode) {
                case FULL_CHECKOUT: {
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
                } catch (SVNCancelException e) {
                    processMonitor.markFailed("checkout", "SVN checkout cancelled by user");
                    return;
                } catch (SVNException e) {
                    processMonitor.markFailed("checkout", "Failed to checkout: " + e.getMessage());
                    return;
                }
                if(!processMonitor.isRunning()) return;

                // Check and handle missing build files
                processMonitor.addStep("build_file_check", "Checking for build files");
                try {
                    String projectFolderPath = project.getProjectFolderPathForCheckout();
                    File projectFolder = new File(projectFolderPath);
                    
                    if (!checkBuildFilesExist(projectFolder)) {
                        processMonitor.logMessage("build_file_check", "Build files missing, asking user for action...");
                        
                        // Show dialog to user asking what to do
                        MissingBuildFilesDialog.BuildFilesAction userAction = showMissingBuildFilesDialog();
                        
                        switch (userAction) {
                            case DOWNLOAD_AND_CONTINUE:
                                processMonitor.logMessage("build_file_check", "User chose to download build files from server...");
                                if (!downloadBuildFilesFromServer(projectFolder, processMonitor)) {
                                    processMonitor.markFailed("build_file_check", "Failed to download build files from server");
                                    return;
                                }
                                // Process build files after download
                                processBuildFiles(project);
                                if(!processMonitor.isRunning()) return;
                                break;
                                
                            case SKIP_AND_CONTINUE:
                                processMonitor.logMessage("build_file_check", "User chose to skip build files download");
                                processMonitor.markComplete("build_file_check", "Build files check completed - skipped by user");
                                break;
                                
                            case CANCEL_SETUP:
                                processMonitor.logMessage("build_file_check", "User chose to cancel setup");
                                processMonitor.markFailed("build_file_check", "Setup cancelled by user due to missing build files");
                                return;
                        }
                    } else {
                        processMonitor.logMessage("build_file_check", "Build files found, skipping download");
                    }
                } catch (Exception e) {
                    processMonitor.markFailed("build_file_check", "Build file check failed: " + e.getMessage());
                    return;
                }
                if(!processMonitor.isRunning()) return;

                //Here call a method to validate project folder if project folder contains jconfig update the project folder path with this new jconfig path, if jconfig missing report error stop the process 
                processMonitor.addStep("validation", "Validating project structure");
                ProgressCallback validationCallback = new ProcessMonitorAdapter(processMonitor, "validation");
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                String validatedJconfigPath = validateProjectFolder(projectFolderPath, validationCallback);
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
                processMonitor.addStep("sftp_download", "Java download and extraction");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "sftp_download");
                    String dir_temp = project.getExePath();
                    // Use setupMode as purpose for session isolation
                    SFTPDownloadAndUnzip.start(dir_temp, project, callback, setupMode.name().toLowerCase());
                } catch (Exception e) {
                    processMonitor.markFailed("sftp_download", "Java download failed: " + e.getMessage());
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
                    e.printStackTrace();
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

                // Update build files with environment variable using automated logic
                processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
                try {
                    String env_name = project.getNmsEnvVar();
                    String exePath = project.getExePath();
                    
                    // Use automated replacement logic
                    boolean replacementSuccess = performAutomatedBuildFileReplacement(exePath, env_name, processMonitor);
                    
                    if (replacementSuccess) {
                        processMonitor.markComplete("build_file_updates", "Build files updated successfully with environment variable: " + env_name);
                    } else {
                        processMonitor.markFailed("build_file_updates", "Automated build file replacement failed - manual replacement required");
                        // Show warning dialog about manual replacement needed
                        showManualReplacementWarning(env_name);
                    }
                } catch (Exception e) {
                    logger.severe("Failed to update build files: " + e.getMessage());
                    processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;
                }
                    break;
                    
                case PATCH_UPGRADE: {
                // Patch upgrade mode: validate project structure and update build files
                processMonitor.addStep("validation", "Validating project structure");
                ProgressCallback validationCallback2 = new ProcessMonitorAdapter(processMonitor, "validation");
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                String validatedJconfigPath = validateProjectFolder(projectFolderPath, validationCallback2);
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
                processMonitor.addStep("sftp_download", "Java download and extraction");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "sftp_download");
                    String dir_temp = project.getExePath();
                    // Use setupMode as purpose for session isolation
                    SFTPDownloadAndUnzip.start(dir_temp, project, callback, setupMode.name().toLowerCase());
                } catch (Exception e) {
                    processMonitor.markFailed("sftp_download", "Java download failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;

                // Load additional resources
                processMonitor.addStep("resource_loading", "Loading additional resources");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "resource_loading");
                    String dir_temp = project.getExePath();
                    String serverURL = adjustUrl(project.getNmsAppURL());
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

                // Create installer
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
                    processMonitor.markComplete("installer_creation", "Setup completed successfully");
                } catch (Exception e) {
                    processMonitor.markFailed("installer_creation", "Installer creation failed: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;
                    // Update build files with environment variable using automated logic
                processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
                try {
                    String env_name = project.getNmsEnvVar();
                    String exePath = project.getExePath();

                    // Use automated replacement logic
                    boolean replacementSuccess = performAutomatedBuildFileReplacement(exePath, env_name, processMonitor);

                    if (replacementSuccess) {
                        processMonitor.markComplete("build_file_updates", "Build files updated successfully with environment variable: " + env_name);
                    } else {
                        processMonitor.markFailed("build_file_updates", "Automated build file replacement failed - manual replacement required");
                        // Show warning dialog about manual replacement needed
                        showManualReplacementWarning(env_name);
                    }
                } catch (Exception e) {
                    logger.severe("Failed to update build files: " + e.getMessage());
                    processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;
                }
                    break;
                    
                case PRODUCT_ONLY: {
                // Product only mode: skip project validation and build files update
                processMonitor.addStep("validation", "Skipping project validation (Product Only mode)");
                processMonitor.markComplete("validation", "Project validation skipped");
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
                processMonitor.addStep("sftp_download", "Java download and extraction");
                try {
                    ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "sftp_download");
                    String dir_temp = project.getExePath();
                    // Use setupMode as purpose for session isolation
                    SFTPDownloadAndUnzip.start(dir_temp, project, callback, setupMode.name().toLowerCase());
                } catch (Exception e) {
                    processMonitor.markFailed("sftp_download", "Java download failed: " + e.getMessage());
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

                // Update build files with environment variable using automated logic
                processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
                try {
                    String env_name = project.getNmsEnvVar();
                    String exePath = project.getExePath();
                    
                    // Use automated replacement logic
                    boolean replacementSuccess = performAutomatedBuildFileReplacement(exePath, env_name, processMonitor);
                    
                    if (replacementSuccess) {
                        processMonitor.markComplete("build_file_updates", "Build files updated successfully with environment variable: " + env_name);
                    } else {
                        processMonitor.markFailed("build_file_updates", "Automated build file replacement failed - manual replacement required");
                        // Show warning dialog about manual replacement needed
                        showManualReplacementWarning(env_name);
                    }
                } catch (Exception e) {
                    logger.severe("Failed to update build files: " + e.getMessage());
                    processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                    return;
                }
                if (!processMonitor.isRunning()) return;
                }
                    break;
                    
                case PROJECT_AND_PRODUCT_FROM_SERVER:
                    // Server-based project download + Product installation
                    if (!performServerProjectAndProductInstallation(processMonitor)) {
                        return;
                    }
                    break;
                    
                case PROJECT_ONLY_SVN:
                    // SVN checkout only (no product installation)
                    if (!performSVNCheckoutOnly(processMonitor)) {
                        return;
                    }
                    break;
                    
                case PROJECT_ONLY_SERVER:
                    // Server project download only (no product installation)
                    if (!performServerProjectOnly(processMonitor)) {
                        return;
                    }
                    break;
                    
                case HAS_JAVA_MODE:
                    // Java already extracted, perform loading resources, exe creation, and build file updates
                    if (!performHasJavaModeSetup(processMonitor)) {
                        return;
                    }
                    break;
                case BUILD_FILES_ONLY:
                    // Standalone build file download and update
                    if (!performBuildFileDownload(processMonitor)) {
                        return;
                    }
                    break;
            }
            
            // Finalize setup
            if (processMonitor.isRunning()) {
                processMonitor.addStep("finalize", "Finalizing setup");
                processMonitor.updateState("finalize", 50);
                processMonitor.markComplete("finalize", "Setup completed successfully");
                
                // Mark the entire process as completed successfully
                project.setManageTool("true");
                
                // Automatically detect and update project code (logId) after successful setup
                try {
                    processMonitor.logMessage("finalize", "Auto-detecting project code...");
                    String projectCode = com.nms.support.nms_support.service.ProjectCodeService.getProjectCode(
                        project.getJconfigPathForBuild(), 
                        project.getExePath()
                    );
                    
                    if (projectCode != null && !projectCode.trim().isEmpty()) {
                        project.setLogId(projectCode);
                        processMonitor.logMessage("finalize", "Project code auto-detected: " + projectCode);
                        logger.info("Successfully auto-detected project code: " + projectCode);
                        if (mc != null && mc.getBuildAutomation() != null) {
                            mc.getBuildAutomation().updateProjectCodeFromSetup(projectCode);
                        }
                    } else {
                        processMonitor.logMessage("finalize", "Project code auto-detection returned null - skipping update");
                        logger.info("Project code auto-detection returned null - will need manual setup");
                    }
                } catch (Exception e) {
                    // Don't fail the setup if project code detection fails
                    processMonitor.logMessage("finalize", "Project code auto-detection failed: " + e.getMessage());
                    logger.warning("Failed to auto-detect project code: " + e.getMessage());
                }
                
                if (mc != null) {
                    Platform.runLater(() -> {
                        if (mc.getBuildAutomation() != null) {
                            mc.getBuildAutomation().loadProjectDetails();
                            logger.info("✓ Build Automation UI refreshed to display auto-detected project code");
                        }
                        mc.performGlobalSave();
                    });
                }
                
                processMonitor.markProcessCompleted("Setup completed successfully");
            }
            
        } catch (Exception e) {
            logger.severe("Setup failed: " + e.getMessage());
        } finally {
            // CRITICAL: Always save project data, even on cancellation or failure
            // This ensures zip file tracking is persisted
            try {
                if (mc != null && mc.projectManager != null) {
                    mc.projectManager.saveData();
                    logger.info("✓ Project data saved in finally block (ensures zip tracking is persisted)");
                }
            } catch (Exception saveEx) {
                logger.warning("Failed to save project data in finally block: " + saveEx.getMessage());
            }
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
     * Validate all required inputs based on setup mode
     */
    private ValidationResult validateInputs() {
        try {
            if (project == null) {
                return ValidationResult.MISSING_PROJECT_FOLDER;
            }
            processMonitor.updateState(validation, 5);

            // Common validations for all modes
            if (project.getNmsEnvVar() == null || project.getNmsEnvVar().trim().isEmpty()) {
                return ValidationResult.MISSING_ENV_VAR;
            }
            processMonitor.updateState(validation, 10);

            // Validate based on setup mode
            switch (setupMode) {
                case PATCH_UPGRADE:
                    return validatePatchUpgradeMode();
                case PRODUCT_ONLY:
                    return validateProductOnlyMode();
                case FULL_CHECKOUT:
                    return validateFullCheckoutMode();
                case PROJECT_AND_PRODUCT_FROM_SERVER:
                    return validateProjectAndProductFromServerMode();
                case PROJECT_ONLY_SVN:
                    return validateProjectOnlySvnMode();
                case PROJECT_ONLY_SERVER:
                    return validateProjectOnlyServerMode();
                case HAS_JAVA_MODE:
                    return validateHasJavaMode();
                case BUILD_FILES_ONLY:
                    return validateBuildFilesOnlyMode();
                default:
                    logger.severe("Unknown setup mode: " + setupMode);
                    return ValidationResult.EXCEPTION;
            }
        } catch (Exception e) {
            logger.severe("Validation failed with exception: " + e.getMessage());
            return ValidationResult.EXCEPTION;
        }
    }
    
    /**
     * Validate for Build Files Only mode (Standalone build file download)
     */
    private ValidationResult validateBuildFilesOnlyMode() {
        // Project folder validation
        if (project.getProjectFolderPathForCheckout() == null || project.getProjectFolderPathForCheckout().trim().isEmpty()) {
            logger.severe("Project folder path is not configured");
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        
        // Verify project folder exists
        File projectFolder = new File(project.getProjectFolderPathForCheckout());
        if (!projectFolder.exists()) {
            logger.severe("Project folder does not exist: " + project.getProjectFolderPathForCheckout());
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        
        // Server details validation (required for SFTP download)
        ValidationResult serverResult = validateServerDetails();
        if (serverResult != ValidationResult.SUCCESS) {
            return serverResult;
        }
        
        // Validate NMS_CONFIG environment variable on server
        try {
            processMonitor.logMessage("validation", "Validating NMS_CONFIG on server...");
            SSHJSessionManager ssh = UnifiedSSHService.createSSHSession(project, sshSessionPurpose);
            ssh.initialize();
            
            String nmsConfigPath = ssh.resolveEnvVar("NMS_CONFIG");
            if (nmsConfigPath == null || nmsConfigPath.trim().isEmpty()) {
                processMonitor.markFailed("validation", "NMS_CONFIG environment variable is not set on server");
                return ValidationResult.MISSING_NMS_CONFIG;
            }
            
            // Check if jconfig directory exists
            String jconfigPath = nmsConfigPath + "/jconfig";
            SSHJSessionManager.CommandResult dirCheck = ssh.executeCommand("test -d " + jconfigPath, 30);
            if (!dirCheck.isSuccess()) {
                processMonitor.markFailed("validation", "jconfig directory not found on server: " + jconfigPath);
                return ValidationResult.MISSING_NMS_CONFIG;
            }
            
            processMonitor.logMessage("validation", "NMS_CONFIG and jconfig directory validated successfully");
            return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("Failed to validate NMS_CONFIG on server: " + e.getMessage());
            processMonitor.markFailed("validation", "Failed to validate NMS_CONFIG: " + e.getMessage());
            return ValidationResult.SERVER_CONNECTION_FAILED;
        }
    }
    
    /**
     * Validate for Patch Upgrade mode (Product installation with project validation)
     */
    private ValidationResult validatePatchUpgradeMode() {
        // Product folder validation
        if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
            logger.severe("Product folder is not configured");
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        processMonitor.updateState(validation, 10);
        
        // Project folder validation (required for build files update)
        if (project.getProjectFolderPathForCheckout() == null || project.getProjectFolderPathForCheckout().trim().isEmpty()) {
            logger.severe("Project folder path is not configured");
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        processMonitor.updateState(validation, 15);

        // App URL validation
        if (project.getNmsAppURL() == null || project.getNmsAppURL().trim().isEmpty()) {
            logger.severe("App URL is not configured");
            return ValidationResult.MISSING_APP_URL;
        }
        processMonitor.updateState(validation, 20);

        return ValidationResult.SUCCESS;
    }
    
    /**
     * Validate for Product Only mode (Product installation only, no project validation)
     */
    private ValidationResult validateProductOnlyMode() {
        // Product folder validation
        if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        processMonitor.updateState(validation, 15);

        // App URL validation
        if (project.getNmsAppURL() == null || project.getNmsAppURL().trim().isEmpty()) {
            logger.severe("App URL is not configured");
            return ValidationResult.MISSING_APP_URL;
        }
        processMonitor.updateState(validation, 20);

        return ValidationResult.SUCCESS;
    }
    
    /**
     * Validate for Full Checkout mode (SVN + Product)
     */
    private ValidationResult validateFullCheckoutMode() {
        // Product folder validation
        if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
            logger.severe("Product folder is not configured");
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        processMonitor.updateState(validation, 10);
        
        // Project folder validation (required for SVN checkout)
        if (project.getProjectFolderPathForCheckout() == null || project.getProjectFolderPathForCheckout().trim().isEmpty()) {
            logger.severe("Project folder path is not configured");
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        processMonitor.updateState(validation, 15);

        // SVN URL validation
        if (project.getSvnRepo() == null || project.getSvnRepo().trim().isEmpty() || project.getSvnRepo().trim().equals("NULL")) {
            logger.severe("SVN repository URL is not configured");
            return ValidationResult.MISSING_SVN_URL;
        }
        processMonitor.updateState(validation, 20);

        // SVN connectivity validation
        ValidationResult svnConnectivityResult = validateSVNConnectivity();
        if (svnConnectivityResult != ValidationResult.SUCCESS) {
            return svnConnectivityResult;
        }
        processMonitor.updateState(validation, 25);

        // App URL validation
        if (project.getNmsAppURL() == null || project.getNmsAppURL().trim().isEmpty()) {
            logger.severe("App URL is not configured");
            return ValidationResult.MISSING_APP_URL;
        }
        processMonitor.updateState(validation, 30);

        // Check for existing project folder and ask for confirmation
        ValidationResult projectFolderConfirmation = checkFolderCleanupConfirmation(
            project.getProjectFolderPathForCheckout(), 
            "SVN Checkout"
        );
        if (projectFolderConfirmation != ValidationResult.SUCCESS) {
            return projectFolderConfirmation;
        }
        processMonitor.updateState(validation, 30);

        return ValidationResult.SUCCESS;
    }
    
    /**
     * Validate for Project and Product from Server mode
     */
    private ValidationResult validateProjectAndProductFromServerMode() {
        // Server details validation (includes host and auth validation)
        ValidationResult serverValidation = validateServerDetails();
        if (serverValidation != ValidationResult.SUCCESS) {
            return serverValidation;
        }
        processMonitor.updateState(validation, 10);
        
        // Project folder validation (required for server project download)
        if (project.getProjectFolderPath() == null || project.getProjectFolderPath().trim().isEmpty()) {
            logger.severe("Project folder path is not configured");
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        processMonitor.updateState(validation, 15);

        // Product folder validation (required for product operations)
        if (project.getExePath() == null || project.getExePath().trim().isEmpty()) {
            logger.severe("Product folder is not configured");
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        processMonitor.updateState(validation, 20);

        // App URL validation
        if (project.getNmsAppURL() == null || project.getNmsAppURL().trim().isEmpty()) {
            logger.severe("App URL is not configured");
            return ValidationResult.MISSING_APP_URL;
        }
        processMonitor.updateState(validation, 25);

        // Check for existing project folder and ask for confirmation
        ValidationResult projectFolderConfirmation = checkFolderCleanupConfirmation(
            project.getProjectFolderPath(), 
            "Server Project Download"
        );
        if (projectFolderConfirmation != ValidationResult.SUCCESS) {
            return projectFolderConfirmation;
        }
        processMonitor.updateState(validation, 30);

        return ValidationResult.SUCCESS;
    }
    
    
    /**
     * Validate for Project Only SVN mode
     */
    private ValidationResult validateProjectOnlySvnMode() {
        // Project folder validation (required for SVN checkout)
        if (project.getProjectFolderPathForCheckout() == null || project.getProjectFolderPathForCheckout().trim().isEmpty()) {
            logger.severe("Project folder path is not configured");
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        processMonitor.updateState(validation, 10);
        
        // SVN URL validation
        if (project.getSvnRepo() == null || project.getSvnRepo().trim().isEmpty() || project.getSvnRepo().trim().equals("NULL")) {
            logger.severe("SVN repository URL is not configured");
            return ValidationResult.MISSING_SVN_URL;
        }
        processMonitor.updateState(validation, 15);

        // SVN connectivity validation
        ValidationResult svnConnectivityResult = validateSVNConnectivity();
        if (svnConnectivityResult != ValidationResult.SUCCESS) {
            return svnConnectivityResult;
        }
        processMonitor.updateState(validation, 25);

        // Check for existing project folder and ask for confirmation
        ValidationResult projectFolderConfirmation = checkFolderCleanupConfirmation(
            project.getProjectFolderPathForCheckout(), 
            "SVN Checkout"
        );
        if (projectFolderConfirmation != ValidationResult.SUCCESS) {
            return projectFolderConfirmation;
        }
        processMonitor.updateState(validation, 30);

        return ValidationResult.SUCCESS;
    }
    
    /**
     * Validate for Project Only Server mode
     */
    private ValidationResult validateProjectOnlyServerMode() {
        // Server details validation (includes host and auth validation)
        ValidationResult serverValidation = validateServerDetails();
        if (serverValidation != ValidationResult.SUCCESS) {
            return serverValidation;
        }
        processMonitor.updateState(validation, 10);
        
        // Project folder validation (required for server project download)
        if (project.getProjectFolderPath() == null || project.getProjectFolderPath().trim().isEmpty()) {
            logger.severe("Project folder path is not configured");
            return ValidationResult.MISSING_PROJECT_FOLDER;
        }
        processMonitor.updateState(validation, 15);

        // Check for existing project folder and ask for confirmation
        ValidationResult projectFolderConfirmation = checkFolderCleanupConfirmation(
            project.getProjectFolderPath(), 
            "Server Project Download"
        );
        if (projectFolderConfirmation != ValidationResult.SUCCESS) {
            return projectFolderConfirmation;
        }
        processMonitor.updateState(validation, 20);

        return ValidationResult.SUCCESS;
    }
    
    /**
     * Validate server details (SFTP credentials)
     */
    private ValidationResult validateServerDetails() {
        // Validate host
        if (project.getHost() == null || project.getHost().trim().isEmpty()) {
            logger.severe("Server host is not configured");
            return ValidationResult.MISSING_SFTP_HOST;
        }

        // Validate authentication based on LDAP setting
        boolean useLdap = project.isUseLdap();
        
        if (useLdap) {
            // Validate LDAP authentication
            String ldapUser = project.getLdapUser();
            String ldapPassword = project.getLdapPassword();
            
            if (ldapUser == null || ldapUser.trim().isEmpty()) {
                logger.severe("LDAP User is not configured (LDAP authentication is enabled)");
                return ValidationResult.MISSING_SFTP_USER;
            }
            
            if (ldapPassword == null || ldapPassword.trim().isEmpty()) {
                logger.severe("LDAP Password is not configured (LDAP authentication is enabled)");
                return ValidationResult.MISSING_SFTP_PASSWORD;
            }
        } else {
            // Validate basic authentication
            String hostUser = project.getHostUser();
            String hostPassword = project.getHostPass();
            
            if (hostUser == null || hostUser.trim().isEmpty()) {
                logger.severe("Host User is not configured (Basic authentication is enabled)");
                return ValidationResult.MISSING_SFTP_USER;
            }
            
            if (hostPassword == null || hostPassword.trim().isEmpty()) {
                logger.severe("Host Password is not configured (Basic authentication is enabled)");
                return ValidationResult.MISSING_SFTP_PASSWORD;
            }
        }

        // Validate SFTP host format
        if (!isValidHostFormat(project.getHost())) {
            logger.severe("Invalid SFTP host format: " + project.getHost());
            return ValidationResult.INVALID_PATHS;
        }

        // Validate SFTP port if specified
        if (project.getHostPort() > 0 && (project.getHostPort() < 1 || project.getHostPort() > 65535)) {
            logger.severe("Invalid SFTP port number: " + project.getHostPort() + " (must be between 1-65535)");
            return ValidationResult.INVALID_PATHS;
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validates server environment variables and paths required for server operations
     * @param setupMode The setup mode to determine which validations are needed
     * @return ValidationResult indicating success or specific failure
     */
    private ValidationResult validateServerEnvironmentVariables(SetupMode setupMode) {
        try {
            processMonitor.addStep("server_env_validation", "Validating server environment variables");
            processMonitor.updateState("server_env_validation", 10, "Initializing server environment validation...");

            processMonitor.updateState("server_env_validation", 20, "Connecting to server...");
            
            if (!processMonitor.isRunning()) {
                processMonitor.markFailed("server_env_validation", "Operation cancelled during server connection");
                return ValidationResult.SERVER_CONNECTION_FAILED;
            }

            processMonitor.updateState("server_env_validation", 30, "Server connection established");

            // Validate $NMS_CONFIG for project operations and build file downloads
            if (setupMode == SetupMode.PROJECT_AND_PRODUCT_FROM_SERVER || 
                setupMode == SetupMode.PROJECT_ONLY_SERVER ||
                setupMode == SetupMode.BUILD_FILES_ONLY) {
                
                processMonitor.updateState("server_env_validation", 40, "Validating server project structure...");
                
                // For BUILD_FILES_ONLY, we only need to validate NMS_CONFIG exists
                if (setupMode == SetupMode.BUILD_FILES_ONLY) {
                    processMonitor.logMessage("server_env_validation", "Validating NMS_CONFIG for build file download...");
                    // NMS_CONFIG validation will be done in the download method
                    processMonitor.updateState("server_env_validation", 60, "Build file download validation ready");
                } else {
                ServerProjectService serverProjectService = new ServerProjectService(project);
                
                // Use existing ServerProjectService validation method
                boolean projectStructureValid = serverProjectService.validateServerProjectStructure(processMonitor);
                if (!projectStructureValid) {
                    // Check if it failed due to cancellation or actual validation failure
                    if (!processMonitor.isRunning()) {
                        processMonitor.markFailed("server_env_validation", "Operation cancelled by user");
                        return ValidationResult.CANCELLED;
                    }
                    // Actual validation failure
                    processMonitor.markFailed("server_env_validation", "Server project structure validation failed");
                    return ValidationResult.MISSING_NMS_CONFIG;
                }
                processMonitor.updateState("server_env_validation", 60, "Server project structure validated successfully");
                
                // Check if process was cancelled
                if (!processMonitor.isRunning()) {
                    processMonitor.markFailed("server_env_validation", "Operation cancelled by user");
                    return ValidationResult.CANCELLED;
                }
                
                // Validate paths used in zipping operations
                ValidationResult pathResult = validateServerPaths();
                if (pathResult != ValidationResult.SUCCESS) {
                    processMonitor.markFailed("server_env_validation", "Server path validation failed: " + pathResult);
                    return pathResult;
                }
                processMonitor.updateState("server_env_validation", 70, "Server paths validated successfully");
                }
            }

            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.markFailed("server_env_validation", "Operation cancelled by user");
                return ValidationResult.CANCELLED;
            }

            // Validate $NMS_HOME for product operations (all product-related modes)
            if (setupMode == SetupMode.PROJECT_AND_PRODUCT_FROM_SERVER || 
                setupMode == SetupMode.PATCH_UPGRADE ||
                setupMode == SetupMode.PRODUCT_ONLY ||
                setupMode == SetupMode.FULL_CHECKOUT) {
                
                ValidationResult nmsHomeResult = validateNmsHomeOnServer();
                if (nmsHomeResult != ValidationResult.SUCCESS) {
                    processMonitor.markFailed("server_env_validation", "NMS_HOME validation failed: " + nmsHomeResult);
                    return nmsHomeResult;
                }
                processMonitor.updateState("server_env_validation", 80, "NMS_HOME validated successfully");
                
                // Check if process was cancelled
                if (!processMonitor.isRunning()) {
                    processMonitor.markFailed("server_env_validation", "Operation cancelled by user");
                    return ValidationResult.CANCELLED;
                }
                
                // Validate $NMS_HOME/java for product operations
                ValidationResult javaPathResult = validateNmsHomeJavaPath();
                if (javaPathResult != ValidationResult.SUCCESS) {
                    processMonitor.markFailed("server_env_validation", "NMS_HOME/java validation failed: " + javaPathResult);
                    return javaPathResult;
                }
                processMonitor.updateState("server_env_validation", 90, "NMS_HOME/java validated successfully");
            }

            processMonitor.updateState("server_env_validation", 100, "All server environment variables validated successfully");
            processMonitor.markComplete("server_env_validation", "Server environment validation completed successfully");
            
            return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("Server environment validation failed with exception: " + e.getMessage());
            processMonitor.markFailed("server_env_validation", "Server environment validation failed: " + e.getMessage());
            return ValidationResult.SERVER_ENV_VALIDATION_FAILED;
        }
    }


    /**
     * Validates $NMS_HOME environment variable and directory on server
     */
    private ValidationResult validateNmsHomeOnServer() {
        try {
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            processMonitor.logMessage("server_env_validation", "Checking NMS_HOME environment variable...");
            
            // Check if NMS_HOME environment variable is set
            SSHJSessionManager.CommandResult envResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "echo \"NMS_HOME: $NMS_HOME\"", 30, sshSessionPurpose);
            
            if (!envResult.isSuccess()) {
                processMonitor.logMessage("server_env_validation", "Failed to check NMS_HOME environment variable");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.MISSING_NMS_HOME;
            }
            
            String output = envResult.getOutput().trim();
            processMonitor.logMessage("server_env_validation", "Environment check output: " + output);
            
            // Check if NMS_HOME is empty or unset
            // Valid output: "NMS_HOME: /path/to/nms"
            // Invalid output: "NMS_HOME:" (empty) or "NMS_HOME: $NMS_HOME" (unset)
            if (output.equals("NMS_HOME:") || output.equals("NMS_HOME: $NMS_HOME")) {
                processMonitor.logMessage("server_env_validation", "NMS_HOME environment variable is not set or empty");
                return ValidationResult.MISSING_NMS_HOME;
            }
            
            // Extract the actual path from the output
            String nmsHomePath = output.substring(output.indexOf(":") + 1).trim();
            if (nmsHomePath.isEmpty()) {
                processMonitor.logMessage("server_env_validation", "NMS_HOME environment variable is empty");
                return ValidationResult.MISSING_NMS_HOME;
            }
            
            processMonitor.logMessage("server_env_validation", "NMS_HOME resolved to: " + nmsHomePath);
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // Check if NMS_HOME directory exists and is accessible
            processMonitor.logMessage("server_env_validation", "Verifying NMS_HOME directory exists...");
            SSHJSessionManager.CommandResult dirResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "test -d $NMS_HOME && echo 'EXISTS' || echo 'NOT_FOUND'", 30, sshSessionPurpose);
            
            if (!dirResult.isSuccess() || !"EXISTS".equals(dirResult.getOutput().trim())) {
                processMonitor.logMessage("server_env_validation", "NMS_HOME directory not found or not accessible");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.MISSING_NMS_HOME;
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check directory permissions and contents
            processMonitor.logMessage("server_env_validation", "Checking NMS_HOME directory permissions...");
            SSHJSessionManager.CommandResult lsResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "ls -ld $NMS_HOME", 30, sshSessionPurpose);
            
            if (lsResult.isSuccess()) {
                processMonitor.logMessage("server_env_validation", "Directory permissions: " + lsResult.getOutput().trim());
            }
            
            processMonitor.logMessage("server_env_validation", "NMS_HOME validated successfully");
            return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("NMS_HOME validation failed: " + e.getMessage());
            return ValidationResult.SERVER_ENV_VALIDATION_FAILED;
        }
    }

    /**
     * Validates server paths used in zipping and download operations
     */
    private ValidationResult validateServerPaths() {
        try {
            processMonitor.logMessage("server_env_validation", "Validating server paths for zipping operations...");
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // Check if /tmp directory exists and is writable (used for zip files)
            processMonitor.logMessage("server_env_validation", "Checking /tmp directory...");
            SSHJSessionManager.CommandResult tmpResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "test -d /tmp && test -w /tmp && echo 'WRITABLE' || echo 'NOT_WRITABLE'", 30, sshSessionPurpose);
            
            if (!tmpResult.isSuccess() || !"WRITABLE".equals(tmpResult.getOutput().trim())) {
                processMonitor.logMessage("server_env_validation", "/tmp directory is not accessible or not writable");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.SERVER_PATH_VALIDATION_FAILED;
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check available space in /tmp (at least 100MB free)
            processMonitor.logMessage("server_env_validation", "Checking available space in /tmp...");
            SSHJSessionManager.CommandResult spaceResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "df /tmp | tail -1 | awk '{print $4}'", 30, sshSessionPurpose);
            
            if (spaceResult.isSuccess()) {
                try {
                    long freeKB = Long.parseLong(spaceResult.getOutput().trim());
                    long freeMB = freeKB / 1024;
                    processMonitor.logMessage("server_env_validation", "Available space in /tmp: " + freeMB + " MB");
                    
                    if (freeMB < 100) {
                        processMonitor.logMessage("server_env_validation", "Warning: Less than 100MB free space in /tmp (" + freeMB + " MB)");
                        // Don't fail, just warn - operations might still work
                    }
                } catch (NumberFormatException e) {
                    processMonitor.logMessage("server_env_validation", "Could not parse available space, continuing...");
                }
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // Test creating a temporary file in /tmp
            processMonitor.logMessage("server_env_validation", "Testing write access to /tmp...");
            SSHJSessionManager.CommandResult testWriteResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, 
                    "touch /tmp/nms_validation_test_$$ && rm -f /tmp/nms_validation_test_$$ && echo 'WRITE_OK' || echo 'WRITE_FAILED'", 
                    30, "server_env_validation");
            
            if (!testWriteResult.isSuccess() || !"WRITE_OK".equals(testWriteResult.getOutput().trim())) {
                processMonitor.logMessage("server_env_validation", "Write test failed in /tmp directory");
                return ValidationResult.SERVER_PATH_VALIDATION_FAILED;
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // Check if zip command is available
            processMonitor.logMessage("server_env_validation", "Checking if zip command is available...");
            SSHJSessionManager.CommandResult zipResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "which zip", 30, sshSessionPurpose);
            
            if (!zipResult.isSuccess() || zipResult.getOutput().trim().isEmpty()) {
                processMonitor.logMessage("server_env_validation", "zip command not found on server");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.SERVER_PATH_VALIDATION_FAILED;
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check if chmod command is available
            processMonitor.logMessage("server_env_validation", "Checking if chmod command is available...");
            SSHJSessionManager.CommandResult chmodResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "which chmod", 30, sshSessionPurpose);
            
            if (!chmodResult.isSuccess() || chmodResult.getOutput().trim().isEmpty()) {
                processMonitor.logMessage("server_env_validation", "chmod command not found on server");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.SERVER_PATH_VALIDATION_FAILED;
            }
            
            processMonitor.logMessage("server_env_validation", "All server paths validated successfully");
            return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("Server path validation failed: " + e.getMessage());
            return ValidationResult.SERVER_PATH_VALIDATION_FAILED;
        }
    }

    /**
     * Validates $NMS_HOME/java path for product operations
     */
    private ValidationResult validateNmsHomeJavaPath() {
        try {
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            processMonitor.logMessage("server_env_validation", "Validating NMS_HOME/java path...");
            
            // First check if NMS_HOME is set (this should already be validated, but double-check)
            SSHJSessionManager.CommandResult envCheckResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "echo \"NMS_HOME: $NMS_HOME\"", 30, sshSessionPurpose);
            
            if (envCheckResult.isSuccess()) {
                String envOutput = envCheckResult.getOutput().trim();
                if (envOutput.equals("NMS_HOME:") || envOutput.equals("NMS_HOME: $NMS_HOME")) {
                    processMonitor.logMessage("server_env_validation", "NMS_HOME is not set, cannot validate NMS_HOME/java");
                    closeSSHSession(); // Close session on validation failure
                    return ValidationResult.MISSING_NMS_HOME;
                }
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check if NMS_HOME/java directory exists and is accessible
            processMonitor.logMessage("server_env_validation", "Verifying NMS_HOME/java directory exists...");
            SSHJSessionManager.CommandResult dirResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "test -d $NMS_HOME/java && echo 'EXISTS' || echo 'NOT_FOUND'", 30, sshSessionPurpose);
            
            if (!dirResult.isSuccess() || !"EXISTS".equals(dirResult.getOutput().trim())) {
                processMonitor.logMessage("server_env_validation", "NMS_HOME/java directory not found or not accessible");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.PRODUCT_PATH_VALIDATION_FAILED;
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check directory permissions and contents
            processMonitor.logMessage("server_env_validation", "Checking NMS_HOME/java directory permissions...");
            SSHJSessionManager.CommandResult lsResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "ls -ld $NMS_HOME/java", 30, sshSessionPurpose);
            
            if (lsResult.isSuccess()) {
                processMonitor.logMessage("server_env_validation", "Directory permissions: " + lsResult.getOutput().trim());
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check if directory is readable (for product operations)
            SSHJSessionManager.CommandResult readResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "test -r $NMS_HOME/java && echo 'READABLE' || echo 'NOT_READABLE'", 30, sshSessionPurpose);
            
            if (!readResult.isSuccess() || !"READABLE".equals(readResult.getOutput().trim())) {
                processMonitor.logMessage("server_env_validation", "NMS_HOME/java directory is not readable");
                closeSSHSession(); // Close session on validation failure
                return ValidationResult.PRODUCT_PATH_VALIDATION_FAILED;
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage("server_env_validation", "Validation cancelled by user");
                closeSSHSession(); // Close session on cancellation
                return ValidationResult.CANCELLED;
            }
            
            // Check if directory contains Java files or subdirectories
            processMonitor.logMessage("server_env_validation", "Checking NMS_HOME/java contents...");
            SSHJSessionManager.CommandResult contentsResult = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, "find $NMS_HOME/java -maxdepth 1 -type f -name '*.jar' | wc -l", 30, sshSessionPurpose);
            
            if (contentsResult.isSuccess()) {
                try {
                    int jarCount = Integer.parseInt(contentsResult.getOutput().trim());
                    processMonitor.logMessage("server_env_validation", "NMS_HOME/java contains " + jarCount + " jar files");
                    if (jarCount == 0) {
                        processMonitor.logMessage("server_env_validation", "Warning: NMS_HOME/java directory appears to be empty");
                    }
                } catch (NumberFormatException e) {
                    processMonitor.logMessage("server_env_validation", "Could not count jar files in NMS_HOME/java");
                }
            }
            
            processMonitor.logMessage("server_env_validation", "NMS_HOME/java validated successfully");
            return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("NMS_HOME/java validation failed: " + e.getMessage());
            return ValidationResult.PRODUCT_PATH_VALIDATION_FAILED;
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
    private String validateProjectFolder(String projectPath, ProgressCallback progressCallback) {
        progressCallback.onProgress(10, "Starting project folder validation...");
        progressCallback.onProgress(15, "Project path: " + projectPath);
        
        if (projectPath == null || projectPath.trim().isEmpty()) {
            logger.warning("Project path is null or empty");
            progressCallback.onError("✗ Project path is null or empty");
            return null;
        }

        File projectFolder = new File(projectPath);
        progressCallback.onProgress(20, "Checking if project folder exists...");
        
        if (!projectFolder.exists() || !projectFolder.isDirectory()) {
            logger.warning("Project folder does not exist or is not a directory: " + projectPath);
            progressCallback.onError("✗ Project folder does not exist: " + projectPath);
            return null;
        }
        
        progressCallback.onProgress(30, "✓ Project folder exists: " + projectPath);

        // Check if the provided path itself ends with jconfig folder, return it if it does
        if (projectFolder.getName().equals("jconfig")) {
            logger.info("Provided path itself is a jconfig folder: " + projectPath);
            progressCallback.onProgress(100, "✓ Provided path is already jconfig folder");
            return projectPath;
        }

        // First, check if jconfig exists directly in the project folder
        progressCallback.onProgress(40, "Searching for jconfig folder...");
        File jconfigFolder = new File(projectFolder, "jconfig");
        
        if (jconfigFolder.exists() && jconfigFolder.isDirectory()) {
            String jconfigFullPath = jconfigFolder.getAbsolutePath();
            logger.info("Found jconfig folder directly in project folder: " + jconfigFullPath);
            progressCallback.onProgress(100, "✓ Found jconfig folder: " + jconfigFullPath);
            return jconfigFullPath;
        }

        // If not found directly, search recursively in subdirectories
        progressCallback.onProgress(50, "jconfig not found directly, searching subdirectories...");
        String jconfigPath = findJconfigRecursively(projectFolder, progressCallback, 50);
        
        if (jconfigPath != null) {
            logger.info("Found jconfig folder in subdirectory: " + jconfigPath);
            progressCallback.onProgress(100, "✓ Found jconfig folder in subdirectory: " + jconfigPath);
            return jconfigPath;
        }

        logger.warning("jconfig folder not found in project folder or any subdirectories: " + projectPath);
        progressCallback.onError("✗ jconfig folder not found in project folder or subdirectories");
        return null;
    }

    /**
     * Recursively searches for jconfig folder in the given directory and its subdirectories
     * 
     * @param directory The directory to search in
     * @param progressCallback Progress callback for logging
     * @param currentProgress Current progress percentage (incremented as we search)
     * @return The path to the directory containing jconfig, or null if not found
     */
    private String findJconfigRecursively(File directory, ProgressCallback progressCallback, int currentProgress) {
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
        int progressIncrement = Math.min(5, (90 - currentProgress) / Math.max(1, files.length));
        for (File file : files) {
            if (file.isDirectory()) {
                currentProgress = Math.min(90, currentProgress + progressIncrement);
                progressCallback.onProgress(currentProgress, "  Searching in: " + file.getName());
                String result = findJconfigRecursively(file, progressCallback, currentProgress);
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
        
        processMonitor.addStep("build_files", "Processing project build files");
        
        try {
            // First validate that required project build files exist
            List<String> missingFiles = BuildFileParser.validateBuildFiles(project, false); // false for project files
            
            if (!missingFiles.isEmpty()) {
                processMonitor.markFailed("build_files", "Required project build files are missing: " + missingFiles);
                return;
            }
            
            // Parse environment variables from project build files
            List<String> existingEnvVars = BuildFileParser.parseEnvironmentVariablesFromJconfig(project);
            
            if (existingEnvVars.isEmpty()) {
                processMonitor.markFailed("build_files", "No environment variables found in project build files");
                return;
            }
            
            // Determine which variable to replace based on priority rules
            String variableToReplace = determineReplacementVariable(existingEnvVars);
            
            if (variableToReplace == null) {
                processMonitor.markFailed("build_files", "Could not determine which variable to replace from: " + existingEnvVars);
                return;
            }
            
            logger.info("Processing project build files - replacing: " + variableToReplace + " with: " + env_name);
            
            // Use BuildFileParser to perform the replacement
            List<String> updatedFiles = BuildFileParser.replaceEnvironmentVariable(project, false, variableToReplace, env_name);
            
            if (!updatedFiles.isEmpty()) {
                processMonitor.updateState("build_files", 100);
                processMonitor.markComplete("build_files", "Successfully updated " + updatedFiles.size() + " project build files: " + updatedFiles);
            } else {
                processMonitor.markFailed("build_files", "No project build files were updated during replacement");
            }
            
        } catch (Exception e) {
            logger.severe("Error processing project build files: " + e.getMessage());
            processMonitor.markFailed("build_files", "Failed to process project build files: " + e.getMessage());
        }
    }


    /**
     * Comprehensive validation of required tools, files, and connectivity
     * 
     * @return ValidationResult indicating success or specific failure
     */
    private ValidationResult validateRequiredToolsAndConnectivity(ProgressCallback progressCallback) {
        try {
            progressCallback.onProgress(10, "Starting tool and connectivity validation...");
            
            // 1. Validate Java installation
            if (setupMode != SetupMode.PROJECT_ONLY_SVN && setupMode != SetupMode.PROJECT_ONLY_SERVER) {
                progressCallback.onProgress(15, "Validating Java installation...");
                if (!validateJavaInstallation()) {
                    // Check if failure is due to cancellation
                    if (wasCancelledByUser()) {
                        logger.info("Java validation cancelled by user");
                        progressCallback.onError("✗ Validation cancelled by user");
                        return ValidationResult.CANCELLED;
                    }
                    logger.severe("Java installation validation failed");
                    progressCallback.onError("✗ Java installation validation failed");
                    return ValidationResult.MISSING_JAVA;
                }
                progressCallback.onProgress(20, "✓ Java installation validated");
            } else {
                logger.info("Skipping java validation for project-only mode");
                progressCallback.onProgress(50, "⊘ Skipping JAVA validation (project-only mode)");
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                logger.info("Tool validation cancelled by user");
                progressCallback.onError("✗ Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // 2. Validate Launch4j installation
            if (setupMode != SetupMode.PROJECT_ONLY_SVN && setupMode != SetupMode.PROJECT_ONLY_SERVER) {
                progressCallback.onProgress(25, "Validating Launch4j installation...");
                if (!validateLaunch4jInstallation()) {
                    // Check if failure is due to cancellation
                    if (wasCancelledByUser()) {
                        logger.info("Launch4j validation cancelled by user");
                        progressCallback.onError("✗ Validation cancelled by user");
                        return ValidationResult.CANCELLED;
                    }
                    logger.severe("Launch4j installation validation failed");
                    progressCallback.onError("✗ Launch4j installation validation failed");
                    return ValidationResult.MISSING_LAUNCH4J;
                }
                progressCallback.onProgress(30, "✓ Launch4j installation validated");
            }else {
                logger.info("Skipping Launch4j validation for project-only mode");
                progressCallback.onProgress(50, "⊘ Skipping Launch4j validation (project-only mode)");
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                logger.info("Tool validation cancelled by user");
                progressCallback.onError("✗ Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // 3. Validate NSIS installation
//            if (!validateNSISInstallation()) {
//                logger.severe("NSIS installation validation failed");
//                return ValidationResult.MISSING_NSIS;
//            }
//            progressCallback.onProgress(40, "✓ NSIS installation validated");
            
             //4. Validate network connectivity (skip for project-only modes)
             if (setupMode != SetupMode.PROJECT_ONLY_SVN && setupMode != SetupMode.PROJECT_ONLY_SERVER) {
                 progressCallback.onProgress(40, "Validating NMS Apps URL connectivity...");
                 if (!appUrlConnectivity()) {
                     // Check if failure is due to cancellation
                     if (wasCancelledByUser()) {
                         logger.info("NMS Apps URL connectivity validation cancelled by user");
                         progressCallback.onError("✗ Validation cancelled by user");
                         return ValidationResult.CANCELLED;
                     }
                     logger.severe("NMS Apps URL connectivity validation failed");
                     progressCallback.onError("✗ NMS Apps URL connectivity validation failed");
                     return ValidationResult.NETWORK_CONNECTION_FAILED;
                 }
                progressCallback.onProgress(50, "✓ NMS Apps URL connectivity validated");
            } else {
                logger.info("Skipping NMS Apps URL connectivity validation for project-only mode");
                progressCallback.onProgress(50, "⊘ Skipping NMS Apps URL validation (project-only mode)");
            }
            
            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                logger.info("Tool validation cancelled by user");
                progressCallback.onError("✗ Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // 5. Validate SFTP connectivity (skip for project-only modes without server)
            if (setupMode != SetupMode.PROJECT_ONLY_SVN && setupMode != SetupMode.HAS_JAVA_MODE) {
                progressCallback.onProgress(55, "Validating SFTP connectivity...");
                if (!validateSFTPConnectivity()) {
                    // Check if failure is due to cancellation
                    if (wasCancelledByUser()) {
                        logger.info("SFTP connectivity validation cancelled by user");
                        progressCallback.onError("✗ Validation cancelled by user");
                        return ValidationResult.CANCELLED;
                    }
                    logger.severe("SFTP connectivity validation failed");
                    progressCallback.onError("✗ SFTP connectivity validation failed");
                    return ValidationResult.SFTP_CONNECTION_FAILED;
                }
                progressCallback.onProgress(70, "✓ SFTP connectivity validated");
            } else {
                logger.info("Skipping SFTP connectivity validation for project-only mode");
                progressCallback.onProgress(70, "⊘ Skipping SFTP validation (project-only mode)");
            }

            // Check if process was cancelled
            if (!processMonitor.isRunning()) {
                logger.info("Tool validation cancelled by user");
                progressCallback.onError("✗ Validation cancelled by user");
                return ValidationResult.CANCELLED;
            }
            
            // 6. Validate VPN connectivity (basic check)
//            if (!validateVPNConnectivity()) {
//                logger.warning("VPN connectivity validation failed - continuing with warning");
//                // Don't fail the process for VPN, just log a warning
//            }
//            progressCallback.onProgress(80, "✓ VPN connectivity validated");
            
            // 7. Validate required directories and permissions
            progressCallback.onProgress(75, "Validating directory permissions...");
            if (!validateDirectoryPermissions()) {
                // Check if failure is due to cancellation
                if (wasCancelledByUser()) {
                    logger.info("Directory permissions validation cancelled by user");
                    progressCallback.onError("✗ Validation cancelled by user");
                    return ValidationResult.CANCELLED;
                }
                logger.severe("Directory permissions validation failed");
                progressCallback.onError("✗ Directory permissions validation failed");
                return ValidationResult.INVALID_PATHS;
            }
            progressCallback.onProgress(90, "✓ Directory permissions validated");
            
            // 8. Validate external tools availability
//            progressCallback.onProgress(92, "Validating external tools...");
//            if (!validateExternalTools()) {
//                logger.severe("External tools validation failed");
//                progressCallback.onError("✗ External tools validation failed");
//                return ValidationResult.MISSING_TOOLS;
//            }
//            progressCallback.onProgress(100, "✓ External tools validated");
            
            logger.info("All required tools and connectivity validated successfully");
            progressCallback.onProgress(100, "✓ All validations completed successfully");
        return ValidationResult.SUCCESS;
            
        } catch (Exception e) {
            logger.severe("Tool validation failed with exception: " + e.getMessage());
            progressCallback.onError("✗ Validation failed with exception: " + e.getMessage());
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
            HostnameVerifier allHostsValid = (hostname, session) -> true;
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
            // Use unified SSH service validation
            if (!UnifiedSSHService.validateProjectAuth(project)) {
                logger.severe("SFTP credentials not properly configured in project");
                return false;
            }
            
            // Test SFTP connection using unified SSH service with proper session purpose
            try {
                SSHJSessionManager.CommandResult result = UnifiedSSHService.executeCommandWithPersistentSession(project, "echo 'SFTP connectivity test'", 10, sshSessionPurpose);
                boolean connected = result.isSuccess();
                
                if (connected) {
                    logger.info("SFTP connectivity validated successfully to: " + project.getHost());
                    return true;
                } else {
                    logger.severe("SFTP connection failed to: " + project.getHost());
                    return false;
                }
            } catch (Exception e) {
                logger.severe("SFTP connectivity test failed: " + e.getMessage());
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
    
    /**
     * Performs server-based project download and product installation
     */
    private boolean performServerProjectAndProductInstallation(ProcessMonitor processMonitor) {
        try {
            // Clean project folder before download
            processMonitor.addStep("project_cleanup", "Cleaning project directory");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "project_cleanup");
                String projectFolderPath = project.getProjectFolderPath();
                File folderToClean = new File(projectFolderPath);
                processMonitor.logMessage("project_cleanup", "Cleaning folder: " + folderToClean.getAbsolutePath());
                SVNAutomationTool.deleteFolderContents(folderToClean, callback);
                processMonitor.markComplete("project_cleanup", "Project directory cleaned successfully");
            } catch (IOException e) {
                processMonitor.markFailed("project_cleanup", "Failed to clean directory: " + e.getMessage());
                return false;
            }
            
            if (!processMonitor.isRunning()) return false;
            
            // Download project from server
            processMonitor.addStep("server_project_download", "Downloading project(server)");
            // Use setupMode as purpose for session isolation
            ServerProjectService serverProjectService = new ServerProjectService(project, setupMode.name().toLowerCase());
            
            // Validate server project structure first
            if (!serverProjectService.validateServerProjectStructure(processMonitor)) {
                processMonitor.markFailed("server_project_download", "Server project validation failed");
                return false;
            }
            
            // Download project from server
            if (!serverProjectService.downloadProjectFromServer(processMonitor)) {
                processMonitor.markFailed("server_project_download", "Failed to download project from server");
                return false;
            }
            processMonitor.markComplete("server_project_download", "Project downloaded from server successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Validate project structure
            processMonitor.addStep("validation", "Validating project structure");
            ProgressCallback validationCallback7 = new ProcessMonitorAdapter(processMonitor, "validation");
            String projectFolderPath = project.getProjectFolderPathForCheckout();
            String validatedJconfigPath = validateProjectFolder(projectFolderPath, validationCallback7);
            if (validatedJconfigPath == null) {
                processMonitor.markFailed("validation", "Project validation failed: jconfig folder not found in project folder");
                return false;
            }
            project.setJconfigPath(validatedJconfigPath);
            processMonitor.markComplete("validation", "Project structure validated successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Process build files
            processBuildFiles(project);
            if (!processMonitor.isRunning()) return false;
            
            // Perform product installation
            return performProductInstallation(processMonitor);
            
        } catch (Exception e) {
            logger.severe("Error in server project and product installation: " + e.getMessage());
            processMonitor.markFailed("server_project_download", "Exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Performs SVN checkout only (no product installation)
     */
    private boolean performSVNCheckoutOnly(ProcessMonitor processMonitor) {
        try {
            // Clean folder before checkout
            processMonitor.addStep("cleanup", "Cleaning project directory");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "cleanup");
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                File folderToClean = new File(projectFolderPath);
                processMonitor.logMessage("cleanup", "Cleaning folder: " + folderToClean.getAbsolutePath());
                SVNAutomationTool.deleteFolderContents(folderToClean, callback);
            } catch (IOException e) {
                processMonitor.markFailed("cleanup", "Failed to clean directory: " + e.getMessage());
                return false;
            }
            
            if (!processMonitor.isRunning()) return false;
            
            // SVN checkout
            processMonitor.addStep("checkout", "SVN project checkout");
            try {
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                File folderToCheckout = new File(projectFolderPath);
                processMonitor.logMessage("checkout", "Checking out to folder: " + folderToCheckout.getAbsolutePath());
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "checkout");
                SVNAutomationTool.performCheckout(project.getSvnRepo(), folderToCheckout.getAbsolutePath(), callback);
            } catch (SVNCancelException e) {
                processMonitor.markFailed("checkout", "SVN checkout cancelled by user");
                return false;
            } catch (SVNException e) {
                processMonitor.markFailed("checkout", "Failed to checkout: " + e.getMessage());
                return false;
            }
            
            if (!processMonitor.isRunning()) return false;
            // Check and handle missing build files
            processMonitor.addStep("build_file_check", "Checking for build files");
            try {
                String projectFolderPath = project.getProjectFolderPathForCheckout();
                File projectFolder = new File(projectFolderPath);
                
                if (!checkBuildFilesExist(projectFolder)) {
                    processMonitor.logMessage("build_file_check", "Build files missing, asking user for action...");
                    
                    // Show dialog to user asking what to do
                    MissingBuildFilesDialog.BuildFilesAction userAction = showMissingBuildFilesDialog();
                    
                    switch (userAction) {
                        case DOWNLOAD_AND_CONTINUE:
                            processMonitor.logMessage("build_file_check", "User chose to download build files from server...");
                            if (!downloadBuildFilesFromServer(projectFolder, processMonitor)) {
                                processMonitor.markFailed("build_file_check", "Failed to download build files from server");
                                return false;
                            }
                            // Process build files after download
                            processBuildFiles(project);
                            if (!processMonitor.isRunning()) return false;
                            break;
                            
                        case SKIP_AND_CONTINUE:
                            processMonitor.logMessage("build_file_check", "User chose to skip build files download");
                            processMonitor.markComplete("build_file_check", "Build files check completed - skipped by user");
                            break;
                            
                        case CANCEL_SETUP:
                            processMonitor.logMessage("build_file_check", "User chose to cancel setup");
                            processMonitor.markFailed("build_file_check", "Setup cancelled by user due to missing build files");
                            return false;
                    }
                } else {
                    processMonitor.logMessage("build_file_check", "Build files found, skipping download");
                }
            } catch (Exception e) {
                processMonitor.markFailed("build_file_check", "Build file check failed: " + e.getMessage());
                return false;
            }
            
            if (!processMonitor.isRunning()) return false;
            
            // Validate project structure
            processMonitor.addStep("validation", "Validating project structure");
            ProgressCallback validationCallback4 = new ProcessMonitorAdapter(processMonitor, "validation");
            String projectFolderPath = project.getProjectFolderPathForCheckout();
            String validatedJconfigPath = validateProjectFolder(projectFolderPath, validationCallback4);
            if (validatedJconfigPath == null) {
                processMonitor.markFailed("validation", "Project validation failed: jconfig folder not found in project folder");
                return false;
            }
            project.setJconfigPath(validatedJconfigPath);
            processMonitor.markComplete("validation", "Project structure validated successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Process build files
            processBuildFiles(project);
            if (!processMonitor.isRunning()) return false;
            
            processMonitor.markComplete("svn_checkout_only", "SVN checkout completed successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Error in SVN checkout only: " + e.getMessage());
            processMonitor.markFailed("svn_checkout_only", "Exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Performs server project download only (no product installation)
     */
    private boolean performServerProjectOnly(ProcessMonitor processMonitor) {
        try {
            // Clean project folder before download
            processMonitor.addStep("project_cleanup", "Cleaning project directory");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "project_cleanup");
                String projectFolderPath = project.getProjectFolderPath();
                File folderToClean = new File(projectFolderPath);
                processMonitor.logMessage("project_cleanup", "Cleaning folder: " + folderToClean.getAbsolutePath());
                SVNAutomationTool.deleteFolderContents(folderToClean, callback);
                processMonitor.markComplete("project_cleanup", "Project directory cleaned successfully");
            } catch (IOException e) {
                processMonitor.markFailed("project_cleanup", "Failed to clean directory: " + e.getMessage());
                return false;
            }
            
            if (!processMonitor.isRunning()) return false;
            
            // Use setupMode as purpose for session isolation
            ServerProjectService serverProjectService = new ServerProjectService(project, setupMode.name().toLowerCase());
            
            // Validate server project structure first
            processMonitor.addStep("server_validation", "Validating server project structure");
            if (!serverProjectService.validateServerProjectStructure(processMonitor)) {
                processMonitor.markFailed("server_validation", "Server project validation failed");
                return false;
            }
            processMonitor.markComplete("server_validation", "Server project structure validated successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Download project from server
            processMonitor.addStep("project_download", "Downloading project(server)");
            if (!serverProjectService.downloadProjectFromServer(processMonitor, "project_download")) {
                processMonitor.markFailed("project_download", "Failed to download project from server");
                return false;
            }
            processMonitor.markComplete("project_download", "Project downloaded from server successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Validate project structure
            processMonitor.addStep("validation", "Validating project structure");
            ProgressCallback validationCallback5 = new ProcessMonitorAdapter(processMonitor, "validation");
            String projectFolderPath = project.getProjectFolderPathForCheckout();
            String validatedJconfigPath = validateProjectFolder(projectFolderPath, validationCallback5);
            if (validatedJconfigPath == null) {
                processMonitor.markFailed("validation", "Project validation failed: jconfig folder not found in project folder");
                return false;
            }
            project.setJconfigPath(validatedJconfigPath);
            processMonitor.markComplete("validation", "Project structure validated successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Process build files
            processBuildFiles(project);
            if (!processMonitor.isRunning()) return false;
            
            processMonitor.markComplete("server_project_only", "Server project download completed successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Error in server project only: " + e.getMessage());
            processMonitor.markFailed("server_project_only", "Exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Performs product installation only (no project download)
     */
    private boolean performProductInstallationOnly(ProcessMonitor processMonitor) {
        try {
            // Validate project structure
            processMonitor.addStep("validation", "Validating project structure");
            ProgressCallback validationCallback9 = new ProcessMonitorAdapter(processMonitor, "validation");
            String projectFolderPath = project.getProjectFolderPathForCheckout();
            String validatedJconfigPath = validateProjectFolder(projectFolderPath, validationCallback9);
            if (validatedJconfigPath == null) {
                processMonitor.markFailed("validation", "Project validation failed: jconfig folder not found in project folder");
                return false;
            }
            project.setJconfigPath(validatedJconfigPath);
            processMonitor.markComplete("validation", "Project structure validated successfully");
            
            if (!processMonitor.isRunning()) return false;
            
            // Process build files
            processBuildFiles(project);
            if (!processMonitor.isRunning()) return false;
            
            // Perform product installation
            return performProductInstallation(processMonitor);
            
        } catch (Exception e) {
            logger.severe("Error in product installation only: " + e.getMessage());
            processMonitor.markFailed("product_installation_only", "Exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Performs product installation steps
     */
    private boolean performProductInstallation(ProcessMonitor processMonitor) {
        try {
            // Clean product directory
            processMonitor.addStep("product_cleanup", "Cleaning product directory");
            if (!cleanProductDirectory(project.getExePath())) {
                processMonitor.markFailed("product_cleanup", "Failed to clean product directory");
                return false;
            }
            processMonitor.markComplete("product_cleanup", "Product directory cleaned successfully");
            if (!processMonitor.isRunning()) return false;
            
            // SFTP download and unzip
            processMonitor.addStep("sftp_download", "Java download and extraction");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "sftp_download");
                String dir_temp = project.getExePath();
                // Use setupMode as purpose for session isolation - fallback to setupMode if available
                String purpose = (setupMode != null) ? setupMode.name().toLowerCase() : "product_installation";
                SFTPDownloadAndUnzip.start(dir_temp, project, callback, purpose);
            } catch (Exception e) {
                processMonitor.markFailed("sftp_download", "Java download failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Load additional resources
            processMonitor.addStep("resource_loading", "Loading additional resources");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "resource_loading");
                String dir_temp = project.getExePath();
                String serverURL = adjustUrl(project.getNmsAppURL());
                FileFetcher.loadResources(dir_temp, serverURL, callback);
            } catch (Exception e) {
                processMonitor.markFailed("resource_loading", "Resource loading failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Process directory structure
            processMonitor.addStep("directory_processing", "Processing directory structure");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "directory_processing");
                String dir_temp = project.getExePath();
                DirectoryProcessor.processDirectory(dir_temp, callback);
            } catch (Exception e) {
                processMonitor.markFailed("directory_processing", "Directory processing failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Create installer
            processMonitor.addStep("installer_creation", "Creating executables");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "installer_creation");
                CreateInstallerCommand cic = new CreateInstallerCommand();
                String appURL = project.getNmsAppURL();
                String envVarName = project.getNmsEnvVar();
                boolean success = cic.execute(appURL, envVarName, project, callback);
                if (!success) {
                    processMonitor.markFailed("installer_creation", "Installer creation failed");
                    return false;
                }
            } catch (Exception e) {
                processMonitor.markFailed("installer_creation", "Installer creation failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Update build files with environment variable using automated logic
            processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
            try {
                String env_name = project.getNmsEnvVar();
                
                // Use automated replacement logic
                boolean replacementSuccess = performAutomatedBuildFileReplacement(project.getExePath(), env_name, processMonitor);
                
                if (replacementSuccess) {
                    processMonitor.markComplete("build_file_updates", "Build files updated successfully with environment variable: " + env_name);
                } else {
                    processMonitor.markFailed("build_file_updates", "Automated build file replacement failed - manual replacement required");
                    // Show warning dialog about manual replacement needed
                    showManualReplacementWarning(env_name);
                }
            } catch (Exception e) {
                logger.severe("Failed to update build files: " + e.getMessage());
                processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            processMonitor.markComplete("product_installation", "Product installation completed successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("Error in product installation: " + e.getMessage());
            processMonitor.markFailed("product_installation", "Exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Performs automated build file replacement using intelligent variable detection
     * 
     * @param exePath The execution path containing build files
     * @param newEnvVar The new environment variable name to use
     * @param processMonitor Process monitor for progress updates
     * @return true if replacement was successful, false otherwise
     */
    private boolean performAutomatedBuildFileReplacement(String exePath, String newEnvVar, ProcessMonitor processMonitor) {
        try {
            // First validate that required files exist
            List<String> missingFiles = BuildFileParser.validateBuildFiles(project, true); // true for product files
            
            if (!missingFiles.isEmpty()) {
                logger.warning("Required product build files are missing: " + missingFiles);
                return false;
            }
            
            // Parse environment variables from product build files
            List<String> existingEnvVars = BuildFileParser.parseEnvironmentVariablesFromProduct(project);
            
            if (existingEnvVars.isEmpty()) {
                logger.warning("No environment variables found in product build files");
                return false;
            }
            
            // Determine which variable to replace based on priority rules
            String variableToReplace = determineReplacementVariable(existingEnvVars);
            
            if (variableToReplace == null) {
                logger.warning("Could not determine which variable to replace from: " + existingEnvVars);
                return false;
            }
            
            logger.info("Automatically replacing variable: " + variableToReplace + " with: " + newEnvVar);
            
            // Use BuildFileParser to perform the replacement
            List<String> updatedFiles = BuildFileParser.replaceEnvironmentVariable(project, true, variableToReplace, newEnvVar);
            
            if (!updatedFiles.isEmpty()) {
                processMonitor.updateState("build_file_updates", 100);
                logger.info("Successfully updated " + updatedFiles.size() + " product build files: " + updatedFiles);
                return true;
            } else {
                logger.warning("No files were updated during replacement");
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("Error in automated build file replacement: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Determines which environment variable to replace based on priority rules
     * 
     * @param existingEnvVars List of existing environment variables
     * @return The variable to replace, or null if none can be determined
     */
    private String determineReplacementVariable(List<String> existingEnvVars) {
        // Rule 1: If there's NMS_HOME, use that
        for (String var : existingEnvVars) {
            if ("NMS_HOME".equals(var)) {
                return var;
            }
        }
        
        // Rule 2: If there's any variable starting with OPAL_HOME, use the first one
        for (String var : existingEnvVars) {
            if (var.startsWith("OPAL_HOME")) {
                return var;
            }
        }
        
        // Rule 3: If there's only one variable containing HOME, use that
        List<String> homeVars = new ArrayList<>();
        for (String var : existingEnvVars) {
            if (var.toUpperCase().contains("HOME")) {
                homeVars.add(var);
            }
        }
        
        if (homeVars.size() == 1) {
            return homeVars.get(0);
        }
        
        // Rule 4: If multiple HOME variables, prefer the first one (already sorted by BuildFileParser)
        if (!homeVars.isEmpty()) {
            return homeVars.get(0);
        }
        
        // Rule 5: If no HOME variables, use the first variable in the list
        if (!existingEnvVars.isEmpty()) {
            return existingEnvVars.get(0);
        }
        
        return null;
    }
    
    /**
     * Shows a warning dialog about manual replacement being required
     * 
     * @param envVarName The environment variable name that was supposed to be set
     */
    private void showManualReplacementWarning(String envVarName) {
        Platform.runLater(() -> {
            String message = "The automated replacement of environment variables in build files failed.\n\n" +
                           "Please use the 'Replace' option next to the project and product folder path fields\n" +
                           "to manually update the environment variables.\n\n" +
                           "Expected environment variable: " + envVarName;
            
            DialogUtil.showAlert(Alert.AlertType.WARNING, "Manual Replacement Required", message);
        });
    }
    
    /**
     * Kill all running exe processes for the project before setup
     * Uses existing log-based killing logic
     * This prevents issues with demon processes that may not have log files yet
     */
    private void killAllExeProcessesFromLogs(ProjectEntity project) {
        processMonitor.logMessage("process_cleanup", "Searching for running exe processes...");
        
        int totalKilled = 0;
        
        try {
            // Get log directory and scan for processes (reuse ControlApp logic)
            String logDirPath = com.nms.support.nms_support.service.buildTabPack.ControlApp.getLogDirectoryPath();
            File logDir = new File(logDirPath);
            
            if (logDir.exists()) {
                File[] logFiles = logDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".log") && new File(dir, name).isFile());
                
                if (logFiles != null && logFiles.length > 0) {
                    processMonitor.logMessage("process_cleanup", "Scanning " + logFiles.length + " log files...");
                    
                    // Load process map using jps with custom JDK env (reuse ControlApp strategy)
                    Map<String, String> jpsMap = new HashMap<>();
                    try {
                        ProcessBuilder pb;
                        // Prefer absolute jps from configured JDK, if available
                        String jdkHome = project != null ? project.getJdkHome() : null;
                        File jpsExe = (jdkHome != null && !jdkHome.trim().isEmpty())
                                ? new File(new File(jdkHome.trim(), "bin"), "jps.exe")
                                : null;
                        if (jpsExe != null && jpsExe.isFile()) {
                            pb = new ProcessBuilder(jpsExe.getAbsolutePath());
                        } else {
                            pb = new ProcessBuilder("jps");
                        }
                        // Inject JAVA_HOME/PATH so jps resolves even if only custom JDK is present
                        com.nms.support.nms_support.service.globalPack.JavaEnvUtil.applyJavaOverride(pb.environment(), jdkHome);
                        Process jpsProcess = pb.start();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jpsProcess.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String[] parts = line.split(" ");
                                if (parts.length >= 2) {
                                    jpsMap.put(parts[0], parts[1]);
                                }
                            }
                        }
                        jpsProcess.waitFor();
                    } catch (Exception e) {
                        logger.warning("Error loading process map (jps): " + e.getMessage());
                    }
                    
                    // Scan log files for matching processes (reuse ControlApp logic)
                    for (File logFile : logFiles) {
                        try {
                            Map<String, String> logData = com.nms.support.nms_support.service.buildTabPack.ControlApp.parseLog(logFile);
                            if (logData != null && 
                                logData.get("PROJECT_KEY").equals(project.getLogId()) && 
                                "EXE".equalsIgnoreCase(logData.get("LAUNCHER")) && 
                                jpsMap.containsKey(logData.get("PID"))) {
                                
                                String pid = logData.get("PID");
                                try {
                                    Process killProcess = Runtime.getRuntime().exec("cmd /c taskkill /F /pid " + pid);
                                    int exitCode = killProcess.waitFor();
                                    if (exitCode == 0) {
                                        totalKilled++;
                                        processMonitor.logMessage("process_cleanup", "Killed process from log: PID " + pid);
                                    }
                                } catch (Exception e) {
                                    // Process might already be dead
                                    logger.warning("Error killing PID " + pid + ": " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            // Skip problematic log files
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error killing processes from logs: " + e.getMessage());
            processMonitor.logMessage("process_cleanup", "Warning: Error scanning log files - " + e.getMessage());
        }
        
        // Summary
        if (totalKilled > 0) {
            processMonitor.logMessage("process_cleanup", 
                "Process cleanup complete: Killed " + totalKilled + " exe processes from logs");
            logger.info("Killed " + totalKilled + " exe processes before setup for project: " + project.getName());
        } else {
            processMonitor.logMessage("process_cleanup", "No running exe processes found in logs");
        }
    }
    
    /**
     * Standardized method to check if a folder is not empty and ask for user confirmation to proceed with cleanup
     * @param folderPath The path to the folder to check
     * @param operationName The name of the operation (e.g., "SVN Checkout", "Project Download", "Product Installation")
     * @return ValidationResult indicating success, user cancellation, or not allowed
     */
    private ValidationResult checkFolderCleanupConfirmation(String folderPath, String operationName) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return ValidationResult.SUCCESS; // No folder to check
        }
        
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            return ValidationResult.SUCCESS; // Folder doesn't exist, no cleanup needed
        }
        
        // Check if folder is not empty
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return ValidationResult.SUCCESS; // Folder is empty, no cleanup needed
        }
        
        // Folder is not empty, ask for confirmation
        final boolean[] proceed = {false};
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            Optional<ButtonType> result = DialogUtil.showConfirmationDialog(
                    operationName,
                    "The " + (folderPath.contains("project") ? "project" : "product") + " folder chosen is not empty. Do you want to proceed with clean " + operationName.toLowerCase() + "?\nWarning: This will delete entire local " + (folderPath.contains("project") ? "project" : "product") + " folder, unsaved changes will be lost.",
                    "Ok to delete " + (folderPath.contains("project") ? "project" : "product") + " directory."
            );
            
            if (result.isPresent() && result.get() == ButtonType.OK) {
                proceed[0] = true;
            }
            latch.countDown();
        });
        
        // Wait for the user to respond
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Thread interrupted while waiting for user response");
            return ValidationResult.EXCEPTION;
        }
        
        if (!proceed[0]) {
            return ValidationResult.NOT_ALLOWED;
        }
        
        return ValidationResult.SUCCESS;
    }

    /**
     * Validate HAS_JAVA_MODE setup requirements
     */
    private ValidationResult validateHasJavaMode() {
        logger.info("Validating HAS_JAVA_MODE setup requirements");
        
        // Check if product folder exists and contains Java
        if (project.getExePath() == null || project.getExePath().isEmpty()) {
            logger.warning("Product folder path not configured for HAS_JAVA_MODE");
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        
        File productFolder = new File(project.getExePath());
        if (!productFolder.exists() || !productFolder.isDirectory()) {
            logger.warning("Product folder does not exist: " + project.getExePath());
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        
        // Check if Java folder exists in product directory
        File javaFolder = new File(productFolder, "java");
        if (!javaFolder.exists() || !javaFolder.isDirectory()) {
            logger.warning("Java folder not found in product directory: " + javaFolder.getAbsolutePath());
            return ValidationResult.MISSING_PRODUCT_FOLDER;
        }
        
        // Check if NMS App URL is configured
        if (project.getNmsAppURL() == null || project.getNmsAppURL().isEmpty()) {
            logger.warning("NMS App URL not configured");
            return ValidationResult.MISSING_APP_URL;
        }
        
        // Use existing method to check if NMS App URL is reachable (includes SSL disable)
        if (!appUrlConnectivity()) {
            logger.warning("NMS App URL is not reachable: " + project.getNmsAppURL());
            return ValidationResult.APP_URL_NOT_RECHABLE;
        }
        
        logger.info("HAS_JAVA_MODE validation successful");
        return ValidationResult.SUCCESS;
    }
    
    /**
     * Perform HAS_JAVA_MODE setup: clean product dir, then same as PRODUCT_ONLY process
     */
    private boolean performHasJavaModeSetup(ProcessMonitor processMonitor) {
        logger.info("Starting HAS_JAVA_MODE setup");
        
        try {
            // Step 1: Clean product directory (except Java folder)
            processMonitor.addStep("clean_product_dir", "Cleaning product directory");
            if (!cleanProductDirectoryExceptJava(processMonitor)) {
                processMonitor.markFailed("clean_product_dir", "Failed to clean product directory");
                return false;
            }
            processMonitor.markComplete("clean_product_dir", "Product directory cleaned successfully");
            if (!processMonitor.isRunning()) return false;
            
            // Step 2: Load resources - same as PRODUCT_ONLY
            processMonitor.addStep("resource_loading", "Loading additional resources");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "resource_loading");
                String dir_temp = project.getExePath();
                String serverURL = adjustUrl(project.getNmsAppURL());
                FileFetcher.loadResources(dir_temp, serverURL, callback);
            } catch (Exception e) {
                processMonitor.markFailed("resource_loading", "Resource loading failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Step 3: Process directory structure - same as PRODUCT_ONLY
            processMonitor.addStep("directory_processing", "Processing directory structure");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "directory_processing");
                String dir_temp = project.getExePath();
                DirectoryProcessor.processDirectory(dir_temp, callback);
            } catch (Exception e) {
                processMonitor.markFailed("directory_processing", "Directory processing failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Step 4: Create installer/executables - same as PRODUCT_ONLY
            processMonitor.addStep("installer_creation", "Creating executables");
            try {
                ProgressCallback callback = new ProcessMonitorAdapter(processMonitor, "installer_creation");
                CreateInstallerCommand cic = new CreateInstallerCommand();
                String appURL = project.getNmsAppURL();
                String envVarName = project.getNmsEnvVar();
                boolean success = cic.execute(appURL, envVarName, project, callback);
                if (!success) {
                    processMonitor.markFailed("installer_creation", "Installer creation failed");
                    return false;
                }
            } catch (Exception e) {
                processMonitor.markFailed("installer_creation", "Installer creation failed: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            // Step 5: Update build files - same as PRODUCT_ONLY
            processMonitor.addStep("build_file_updates", "Updating build files with environment variable");
            try {
                String env_name = project.getNmsEnvVar();
                String exePath = project.getExePath();
                
                boolean replacementSuccess = performAutomatedBuildFileReplacement(exePath, env_name, processMonitor);
                
                if (replacementSuccess) {
                    processMonitor.markComplete("build_file_updates", "Build files updated successfully");
                } else {
                    processMonitor.markComplete("build_file_updates", "Build files update completed with manual intervention required");
                }
            } catch (Exception e) {
                processMonitor.markFailed("build_file_updates", "Failed to update build files: " + e.getMessage());
                return false;
            }
            if (!processMonitor.isRunning()) return false;
            
            logger.info("HAS_JAVA_MODE setup completed successfully");
            return true;
            
        } catch (Exception e) {
            logger.severe("HAS_JAVA_MODE setup failed: " + e.getMessage());
            processMonitor.markFailed("has_java_mode_setup", "Setup failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clean product directory except Java folder
     */
    private boolean cleanProductDirectoryExceptJava(ProcessMonitor processMonitor) {
        try {
            File productFolder = new File(project.getExePath());
            File javaFolder = new File(productFolder, "java");
            
            processMonitor.logMessage("clean_product_dir", "Cleaning product directory: " + productFolder.getAbsolutePath());
            
            // Delete all files and folders except the Java folder
            File[] files = productFolder.listFiles();
            if (files != null) {
                int deletedCount = 0;
                for (File file : files) {
                    // Skip the Java folder
                    if (file.equals(javaFolder)) {
                        processMonitor.logMessage("clean_product_dir", "Preserving Java folder: " + file.getName());
                        continue;
                    }
                    
                    // Delete everything else
                    if (deleteRecursively(file)) {
                        deletedCount++;
                        processMonitor.logMessage("clean_product_dir", "Deleted: " + file.getName());
                    } else {
                        logger.warning("Failed to delete: " + file.getAbsolutePath());
                    }
                }
                
                processMonitor.logMessage("clean_product_dir", 
                    "Cleanup complete: Deleted " + deletedCount + " items, preserved Java folder");
            }
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to clean product directory: " + e.getMessage());
            processMonitor.logMessage("clean_product_dir", "ERROR: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Recursively delete a file or directory
     */
    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }
    
    
    
    
    /**
     * Show dialog to user when build files are missing
     * @return The user's chosen action
     */
    private MissingBuildFilesDialog.BuildFilesAction showMissingBuildFilesDialog() {
        try {
            // Create a new stage for the dialog
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            
            MissingBuildFilesDialog dialog = new MissingBuildFilesDialog();
            return dialog.showDialog(dialogStage);
            
                    } catch (Exception e) {
            logger.severe("Error showing missing build files dialog: " + e.getMessage());
            // Default to cancel if dialog fails
            return MissingBuildFilesDialog.BuildFilesAction.CANCEL_SETUP;
        }
    }
    
    /**
     * TEMPORARY METHOD FOR TESTING - Delete build files to test missing build files dialog
     * This method should be removed after testing is complete
     * @param projectFolder The project folder containing build files
     * @return true if files were deleted successfully, false otherwise
     */
    public boolean tempDeleteBuildFilesForTesting(File projectFolder) {
        try {
            logger.info("TEMPORARY: Deleting build files for testing purposes...");
            
            File jconfigDir = new File(projectFolder, "jconfig");
            if (!jconfigDir.exists()) {
                logger.warning("jconfig directory does not exist: " + jconfigDir.getAbsolutePath());
                return false;
            }
            
            File buildXml = new File(jconfigDir, "build.xml");
            File buildProperties = new File(jconfigDir, "build.properties");
            
            boolean deleted = true;
            
            if (buildXml.exists()) {
                if (buildXml.delete()) {
                    logger.info("TEMPORARY: Deleted build.xml for testing");
                } else {
                    logger.warning("TEMPORARY: Failed to delete build.xml");
                    deleted = false;
                }
            } else {
                logger.info("TEMPORARY: build.xml does not exist, nothing to delete");
            }
            
            if (buildProperties.exists()) {
                if (buildProperties.delete()) {
                    logger.info("TEMPORARY: Deleted build.properties for testing");
                } else {
                    logger.warning("TEMPORARY: Failed to delete build.properties");
                    deleted = false;
                }
            } else {
                logger.info("TEMPORARY: build.properties does not exist, nothing to delete");
            }
            
            if (deleted) {
                logger.info("TEMPORARY: Build files deleted successfully for testing");
            } else {
                logger.warning("TEMPORARY: Some build files could not be deleted");
            }
            
            return deleted;
                
        } catch (Exception e) {
            logger.severe("TEMPORARY: Error deleting build files for testing: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if build files exist in the project folder's jconfig directory
     * @param projectFolder The project folder to check
     * @return true if both build.xml and build.properties exist in jconfig subfolder, false otherwise
     */
    private boolean checkBuildFilesExist(File projectFolder) {
        File jconfigDir = new File(projectFolder, "jconfig");
        File buildXml = new File(jconfigDir, "build.xml");
        File buildProperties = new File(jconfigDir, "build.properties");
        
        boolean buildXmlExists = buildXml.exists() && buildXml.isFile();
        boolean buildPropertiesExists = buildProperties.exists() && buildProperties.isFile();
        
        logger.info("Build files check in jconfig directory - build.xml: " + (buildXmlExists ? "EXISTS" : "MISSING") + 
                   ", build.properties: " + (buildPropertiesExists ? "EXISTS" : "MISSING"));
        
        return buildXmlExists && buildPropertiesExists;
    }
    
    /**
     * Download build files from server if they don't exist
     * @param projectFolder The project folder to download build files to
     * @param processMonitor Process monitor for progress updates
     * @return true if build files were downloaded successfully, false otherwise
     */
    private boolean downloadBuildFilesFromServer(File projectFolder, ProcessMonitor processMonitor) {
        try {
            processMonitor.logMessage("build_file_download", "Checking if build files need to be downloaded...");
            
            // Check if build files already exist
            if (checkBuildFilesExist(projectFolder)) {
                processMonitor.logMessage("build_file_download", "Build files already exist, skipping download");
                processMonitor.markComplete("build_file_download", "Build files already exist");
                return true;
            }
            
            processMonitor.logMessage("build_file_download", "Build files missing, downloading from server...");
            
            // Create SSH session for build file download
            SSHJSessionManager ssh = null;
            SFTPClient sftpClient = null;
            
            try {
                // Create SSH session with purpose-based isolation
                ssh = UnifiedSSHService.createSSHSession(project, sshSessionPurpose);
                ssh.initialize();
                
                // Register session with ProcessMonitorManager for proper cleanup
                ProcessMonitorManager.getInstance().registerSession(
                    ssh.getSessionId(), 
                    ssh, 
                    true, // Mark as in progress
                    sshSessionPurpose // Include purpose for proper session tracking
                );
                
                processMonitor.logMessage("build_file_download", "Connected to server for build file download");
                
                // Resolve NMS_CONFIG path using existing method
                processMonitor.logMessage("build_file_download", "Resolving NMS_CONFIG environment variable...");
                String nmsConfigPath = ssh.resolveEnvVar("NMS_CONFIG");
                if (nmsConfigPath == null || nmsConfigPath.trim().isEmpty()) {
                    processMonitor.markFailed("build_file_download", "NMS_CONFIG environment variable is not set");
                    return false;
                }
                
                processMonitor.logMessage("build_file_download", "NMS_CONFIG resolved to: " + nmsConfigPath);
                
                // Check if jconfig directory exists
                String jconfigPath = nmsConfigPath + "/jconfig";
                processMonitor.logMessage("build_file_download", "Checking jconfig directory: " + jconfigPath);
                
                SSHJSessionManager.CommandResult dirCheck = ssh.executeCommand("test -d " + jconfigPath, 30);
                if (!dirCheck.isSuccess()) {
                    processMonitor.markFailed("build_file_download", "jconfig directory not found: " + jconfigPath);
                    return false;
                }
                
                // Copy build files to /tmp/ with proper permissions
                String[] tempFilePaths = copyBuildFilesToTemp(ssh, jconfigPath, processMonitor);
                if (tempFilePaths == null) {
                    processMonitor.markFailed("build_file_download", "Failed to copy build files to /tmp/");
                    return false;
                }
                
                String tmpBuildXml = tempFilePaths[0];
                String tmpBuildProperties = tempFilePaths[1];
                
                // Check if /tmp/ files exist before trying to download
                processMonitor.logMessage("build_file_download", "Checking if /tmp/ files exist...");
                SSHJSessionManager.CommandResult fileExistsCheck = ssh.executeCommand("ls -la " + tmpBuildXml + " " + tmpBuildProperties, 30);
                processMonitor.logMessage("build_file_download", "File existence check result: " + fileExistsCheck.getOutput());
                
                if (!fileExistsCheck.isSuccess()) {
                    processMonitor.markFailed("build_file_download", "/tmp/ build files do not exist or are not accessible");
                    return false;
                }
                
                // Open SFTP client for file download
                sftpClient = ssh.openSftp();
                processMonitor.logMessage("build_file_download", "SFTP client opened, starting file download");
                
                // Create local jconfig directory if it doesn't exist
                File localJconfigDir = new File(projectFolder, "jconfig");
                if (!localJconfigDir.exists()) {
                    if (!localJconfigDir.mkdirs()) {
                        processMonitor.markFailed("build_file_download", "Failed to create local jconfig directory");
                        return false;
                    }
                }
                
                processMonitor.logMessage("build_file_download", "Local jconfig directory ready: " + localJconfigDir.getAbsolutePath());
                
                // Download build.xml from /tmp/
                String localBuildXml = localJconfigDir.getAbsolutePath() + File.separator + "build.xml";
                
                processMonitor.logMessage("build_file_download", "Downloading build.xml from: " + tmpBuildXml);
                processMonitor.logMessage("build_file_download", "Local path: " + localBuildXml);
                
                try {
                    sftpClient.get(tmpBuildXml, localBuildXml);
                    processMonitor.logMessage("build_file_download", "build.xml downloaded successfully");
                } catch (Exception e) {
                    logger.severe("Failed to download build.xml: " + e.getMessage());
                    processMonitor.markFailed("build_file_download", "Failed to download build.xml: " + e.getMessage());
                    return false;
                }
                
                // Download build.properties from /tmp/
                String localBuildProperties = localJconfigDir.getAbsolutePath() + File.separator + "build.properties";
                
                processMonitor.logMessage("build_file_download", "Downloading build.properties from: " + tmpBuildProperties);
                processMonitor.logMessage("build_file_download", "Local path: " + localBuildProperties);
                
                try {
                    sftpClient.get(tmpBuildProperties, localBuildProperties);
                    processMonitor.logMessage("build_file_download", "build.properties downloaded successfully");
                } catch (Exception e) {
                    logger.severe("Failed to download build.properties: " + e.getMessage());
                    processMonitor.markFailed("build_file_download", "Failed to download build.properties: " + e.getMessage());
                    return false;
                }
                
                // Verify files were downloaded
                if (checkBuildFilesExist(projectFolder)) {
                    processMonitor.logMessage("build_file_download", "Build files downloaded successfully, cleaning up /tmp/ files...");
                    
                    // Clean up /tmp/ files
                    try {
                        ssh.executeCommand("rm -f " + tmpBuildXml + " " + tmpBuildProperties, 30);
                        processMonitor.logMessage("build_file_download", "Temporary files cleaned up");
                    } catch (Exception cleanupException) {
                        logger.warning("Failed to clean up /tmp/ files: " + cleanupException.getMessage());
                        // Don't fail the entire operation for cleanup issues
                    }
                    
                    processMonitor.markComplete("build_file_download", "Build files downloaded successfully");
                    return true;
                } else {
                    processMonitor.markFailed("build_file_download", "Build files download verification failed");
                    return false;
                }
                
            } catch (Exception e) {
                logger.severe("Failed to download build files: " + e.getMessage());
                processMonitor.markFailed("build_file_download", "Build files download failed: " + e.getMessage());
                return false;
            } finally {
                // Clean up resources
                if (sftpClient != null) {
                    try {
                        sftpClient.close();
                    } catch (Exception ignored) {}
                }
                if (ssh != null) {
                    try {
                        // Mark operation as completed
                        ProcessMonitorManager.getInstance().updateOperationState(ssh.getSessionId(), false);
                    } catch (Exception ignored) {}
                }
            }
            
        } catch (Exception e) {
            logger.severe("Error in downloadBuildFilesFromServer: " + e.getMessage());
            processMonitor.markFailed("build_file_download", "Build files download error: " + e.getMessage());
            return false;
        }
    }
    
    
    /**
     * Check and grant permissions for build files
     * @param ssh SSH session manager
     * @param jconfigPath Path to jconfig directory
     * @param processMonitor Process monitor for progress updates
     * @return true if permissions are set successfully, false otherwise
     */
    private String[] copyBuildFilesToTemp(SSHJSessionManager ssh, String jconfigPath, ProcessMonitor processMonitor) {
        try {
            processMonitor.logMessage("build_file_download", "Checking build file permissions...");
            
            // Check if build files exist and are readable
            String buildXmlPath = jconfigPath + "/build.xml";
            String buildPropertiesPath = jconfigPath + "/build.properties";
            
            // Copy build files to /tmp/ with proper permissions for SFTP access
            String timestamp = String.valueOf(System.currentTimeMillis());
            String tmpBuildXml = "/tmp/build_" + timestamp + ".xml";
            String tmpBuildProperties = "/tmp/build_" + timestamp + ".properties";
            
            processMonitor.logMessage("build_file_download", "Copying build files to /tmp/ for SFTP access...");
            
            // Copy build.xml to /tmp/
            SSHJSessionManager.CommandResult copyXmlResult = ssh.executeCommand("cp " + buildXmlPath + " " + tmpBuildXml, 30);
            if (!copyXmlResult.isSuccess()) {
                processMonitor.markFailed("build_file_download", "Failed to copy build.xml to /tmp/: " + copyXmlResult.getOutput());
                return null;
            }
            processMonitor.logMessage("build_file_download", "build.xml copied to: " + tmpBuildXml);
            
            // Copy build.properties to /tmp/
            SSHJSessionManager.CommandResult copyPropsResult = ssh.executeCommand("cp " + buildPropertiesPath + " " + tmpBuildProperties, 30);
            if (!copyPropsResult.isSuccess()) {
                processMonitor.markFailed("build_file_download", "Failed to copy build.properties to /tmp/: " + copyPropsResult.getOutput());
                return null;
            }
            processMonitor.logMessage("build_file_download", "build.properties copied to: " + tmpBuildProperties);
            
            // Set proper permissions on /tmp/ files
            SSHJSessionManager.CommandResult chmodTmpXmlResult = ssh.executeCommand("chmod 644 " + tmpBuildXml, 30);
            SSHJSessionManager.CommandResult chmodTmpPropsResult = ssh.executeCommand("chmod 644 " + tmpBuildProperties, 30);
            
            if (!chmodTmpXmlResult.isSuccess() || !chmodTmpPropsResult.isSuccess()) {
                processMonitor.markFailed("build_file_download", "Failed to set permissions on /tmp/ files");
                return null;
            }
            processMonitor.logMessage("build_file_download", "Build files copied to /tmp/ with proper permissions");
            
            // Store the /tmp/ file paths for download
            processMonitor.logMessage("build_file_download", "Temporary files ready for download: " + tmpBuildXml + ", " + tmpBuildProperties);
            return new String[]{tmpBuildXml, tmpBuildProperties};
            
        } catch (Exception e) {
            logger.severe("Failed to copy build files to /tmp/: " + e.getMessage());
            processMonitor.markFailed("build_file_download", "File copy failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Perform standalone build file download and update
     * This method can be called independently to download and update build files
     * @param processMonitor Process monitor for progress updates
     * @return true if successful, false otherwise
     */
    public boolean performBuildFileDownload(ProcessMonitor processMonitor) {
        try {
            processMonitor.addStep("build_file_download", "Downloading build files from server");
            
            // Get project folder path
            String projectFolderPath = project.getProjectFolderPathForCheckout();
            File projectFolder = new File(projectFolderPath);
            
            if (!projectFolder.exists()) {
                processMonitor.markFailed("build_file_download", "Project folder does not exist: " + projectFolderPath);
                return false;
            }
            
            // Download build files from server
            if (!downloadBuildFilesFromServer(projectFolder, processMonitor)) {
                return false;
            }
            
            // Use existing processBuildFiles method for updating build files
            processBuildFiles(project);
            if (!processMonitor.isRunning()) return false;
            
            processMonitor.markComplete("build_file_download", "Build file download and update completed successfully");
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Error in performBuildFileDownload: " + e.getMessage());
            processMonitor.markFailed("build_file_download", "Build file download failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validate SVN repository connectivity
     * Tests if the SVN host is reachable
     */
    private ValidationResult validateSVNConnectivity() {
        try {
            processMonitor.updateState(validation, 5);
            processMonitor.logMessage(validation, "Testing SVN host connectivity...");
            
            // Extract host from SVN URL
            String svnUrl = project.getSvnRepo();
            if (svnUrl == null || svnUrl.trim().isEmpty()) {
                processMonitor.logMessage(validation, "SVN URL is empty");
                return ValidationResult.MISSING_SVN_URL;
            }
            
            // Parse the URL to extract host and port
            String host;
            int port = 80; // Default port
            
            try {
                if (svnUrl.startsWith("http://")) {
                    svnUrl = svnUrl.substring(7);
                    port = 80;
                } else if (svnUrl.startsWith("https://")) {
                    svnUrl = svnUrl.substring(8);
                    port = 443;
                } else if (svnUrl.startsWith("svn://")) {
                    svnUrl = svnUrl.substring(6);
                    port = 3690;
                }
                
                // Extract host and port
                if (svnUrl.contains("/")) {
                    host = svnUrl.substring(0, svnUrl.indexOf("/"));
                } else {
                    host = svnUrl;
                }
                
                if (host.contains(":")) {
                    String[] parts = host.split(":");
                    host = parts[0];
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        // Use default port if parsing fails
                    }
                }
                
            } catch (Exception e) {
                logger.warning("Failed to parse SVN URL: " + svnUrl);
                processMonitor.logMessage(validation, "Failed to parse SVN URL");
                return ValidationResult.SVN_CONNECTIVITY_FAILED;
            }
            
            processMonitor.updateState(validation, 10);
            processMonitor.logMessage(validation, "Testing connectivity to " + host + ":" + port);
            
            // Test host connectivity with timeout
            boolean isReachable = testHostConnectivity(host, port, 5000); // 5 second timeout
            
            if (isReachable) {
                processMonitor.updateState(validation, 15);
                processMonitor.logMessage(validation, "SVN host connectivity test successful");
                return ValidationResult.SUCCESS;
            } else {
                processMonitor.logMessage(validation, "SVN host is not reachable - check network/VPN connection");
                return ValidationResult.SVN_CONNECTIVITY_FAILED;
            }
            
        } catch (Exception e) {
            logger.severe("Unexpected error during SVN connectivity test: " + e.getMessage());
            processMonitor.logMessage(validation, "SVN connectivity test failed with unexpected error");
            return ValidationResult.SVN_CONNECTIVITY_FAILED;
        }
    }
    
    /**
     * Test if a host is reachable on a specific port
     */
    private boolean testHostConnectivity(String host, int port, int timeoutMs) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
