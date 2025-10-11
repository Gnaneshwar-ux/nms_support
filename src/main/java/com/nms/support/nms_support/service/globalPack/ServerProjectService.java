package com.nms.support.nms_support.service.globalPack;

import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.FileAttributes;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult;

import java.io.*;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.logging.Logger;

/**
 * Service for handling server-based project operations
 * Handles zipping $NMS_CONFIG on server, downloading, and extracting locally
 */
public class ServerProjectService {
    private static final Logger logger = LoggerUtil.getLogger();
    
    private final ProjectEntity project;
    private final SSHJSessionManager sshManager;  // Now using SSHJ implementation
    
    /**
     * Constructor with purpose-based session isolation
     * 
     * @param project Project configuration
     * @param purpose Purpose for session isolation (e.g., "project_only_server", "server_project_download")
     */
    public ServerProjectService(ProjectEntity project, String purpose) {
        this.project = project;
        this.sshManager = UnifiedSSHService.createSSHSession(project, purpose);
        
        // Register this service with ProcessMonitorManager
        ProcessMonitorManager.getInstance().registerSession(
            sshManager.getSessionId(), 
            sshManager, 
            false,
            purpose
        );
    }
    
    /**
     * Constructor with default purpose (backward compatibility)
     * @deprecated Use ServerProjectService(ProjectEntity, String) with purpose parameter
     */
    @Deprecated
    public ServerProjectService(ProjectEntity project) {
        this(project, "server_project");
    }
    
    /**
     * Downloads project from server by zipping $NMS_CONFIG and downloading it
     * 
     * @param processMonitor Process monitor for progress updates
     * @return true if successful, false otherwise
     */
    public boolean downloadProjectFromServer(ProcessMonitor processMonitor) {
        return downloadProjectFromServer(processMonitor, "server_project_download");
    }
    
    /**
     * Downloads project from server by zipping $NMS_CONFIG and downloading it
     * 
     * @param processMonitor Process monitor for progress updates
     * @param stepName The step name to use for progress tracking
     * @return true if successful, false otherwise
     */
    public boolean downloadProjectFromServer(ProcessMonitor processMonitor, String stepName) {
        try {
            // Mark operation as in progress
            ProcessMonitorManager.getInstance().updateOperationState(sshManager.getSessionId(), true);
            
            // Check for cancellation before starting
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before starting");
                ProcessMonitorManager.getInstance().updateOperationState(sshManager.getSessionId(), false);
                return false;
            }
            
            // Step 1: Connect to server and create zip of $NMS_CONFIG
            processMonitor.logMessage(stepName, "Connecting to server and creating project zip...");
            String remoteZipPath = createProjectZipOnServer(processMonitor, stepName);
            if (remoteZipPath == null) {
                processMonitor.markFailed(stepName, "Failed to create project zip on server");
                return false;
            }
            processMonitor.logMessage(stepName, "Project zip created successfully on server: " + remoteZipPath);
            
            // Check for cancellation after zip creation
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after zip creation");
                // Clean up remote zip file
                try {
                    processMonitor.logMessage(stepName, "Cleaning up remote zip file: " + remoteZipPath);
                    sshManager.executeCommandWithoutCancellation("rm -f " + remoteZipPath, 30);
                    processMonitor.logMessage(stepName, "Remote zip file cleaned up");
                } catch (Exception cleanupEx) {
                    logger.warning("Failed to cleanup remote zip: " + cleanupEx.getMessage());
                }
                return false;
            }
            
            // Step 2: Download the zip file
            processMonitor.logMessage(stepName, "Downloading project zip from server...");
            String localZipPath = downloadProjectZip(remoteZipPath, processMonitor, stepName);
            if (localZipPath == null) {
                processMonitor.markFailed(stepName, "Failed to download project zip");
                return false;
            }
            processMonitor.logMessage(stepName, "Project zip downloaded successfully to: " + localZipPath);
            
            // Check for cancellation after download
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after download");
                // Clean up both remote and local zip files
                try {
                    processMonitor.logMessage(stepName, "Cleaning up remote zip file: " + remoteZipPath);
                    sshManager.executeCommandWithoutCancellation("rm -f " + remoteZipPath, 30);
                } catch (Exception cleanupEx) {
                    logger.warning("Failed to cleanup remote zip: " + cleanupEx.getMessage());
                }
                try {
                    processMonitor.logMessage(stepName, "Cleaning up local zip file: " + localZipPath);
                    File localFile = new File(localZipPath);
                    if (localFile.exists() && localFile.delete()) {
                        processMonitor.logMessage(stepName, "Local zip file cleaned up");
                    }
                } catch (Exception cleanupEx) {
                    logger.warning("Failed to cleanup local zip: " + cleanupEx.getMessage());
                }
                return false;
            }
            
            // Step 3: Extract the zip to local project folder
            processMonitor.logMessage(stepName, "Extracting project zip to local folder...");
            boolean extractSuccess = extractProjectZip(localZipPath, processMonitor, stepName);
            if (!extractSuccess) {
                processMonitor.markFailed(stepName, "Failed to extract project zip");
                return false;
            }
            processMonitor.logMessage(stepName, "Project zip extracted successfully to project folder");
            
            // Check for cancellation after extraction
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after extraction");
                // Clean up both remote and local zip files
                try {
                    processMonitor.logMessage(stepName, "Cleaning up remote zip file: " + remoteZipPath);
                    sshManager.executeCommandWithoutCancellation("rm -f " + remoteZipPath, 30);
                } catch (Exception cleanupEx) {
                    logger.warning("Failed to cleanup remote zip: " + cleanupEx.getMessage());
                }
                try {
                    processMonitor.logMessage(stepName, "Cleaning up local zip file: " + localZipPath);
                    File localFile = new File(localZipPath);
                    if (localFile.exists() && localFile.delete()) {
                        processMonitor.logMessage(stepName, "Local zip file cleaned up");
                    }
                } catch (Exception cleanupEx) {
                    logger.warning("Failed to cleanup local zip: " + cleanupEx.getMessage());
                }
                return false;
            }
            
            // Step 4: Clean up remote zip file
            processMonitor.logMessage(stepName, "Cleaning up remote zip file...");
            cleanupRemoteZip(remoteZipPath, processMonitor, stepName);
            
            // Check for cancellation after remote cleanup
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after remote cleanup");
                // Local zip still needs cleanup
                try {
                    File localFile = new File(localZipPath);
                    if (localFile.exists() && localFile.delete()) {
                        processMonitor.logMessage(stepName, "Local zip file cleaned up");
                    }
                } catch (Exception cleanupEx) {
                    logger.warning("Failed to cleanup local zip: " + cleanupEx.getMessage());
                }
                return false;
            }
            
            // Step 5: Clean up local zip file
            processMonitor.logMessage(stepName, "Cleaning up local zip file...");
            cleanupLocalZip(localZipPath, processMonitor, stepName);
            
            processMonitor.markComplete(stepName, "Project downloaded and extracted successfully");
            
            // Mark operation as completed
            ProcessMonitorManager.getInstance().updateOperationState(sshManager.getSessionId(), false);
            return true;
            
        } catch (Exception e) {
            logger.severe("Error downloading project from server: " + e.getMessage());
            processMonitor.markFailed(stepName, "Exception: " + e.getMessage());
            
            // Mark operation as failed and clean up
            ProcessMonitorManager.getInstance().updateOperationState(sshManager.getSessionId(), false);
            // ssh.close() in session cleanup will automatically delete tracked files
            ProcessMonitorManager.getInstance().immediateSessionCleanup(sshManager.getSessionId());
            return false;
        }
    }
    
    /**
     * Creates a zip file of $NMS_CONFIG on the server
     */
    private String createProjectZipOnServer(ProcessMonitor processMonitor, String stepName) {
        try {
            processMonitor.updateState(stepName, 10);
            
            // Check for cancellation before starting
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before SSH connection");
                return null;
            }
            
            // Initialize SSH session (will reuse cached session if available)
            try {
                processMonitor.logMessage(stepName, "Initializing SSH connection...");
                sshManager.initialize();
                processMonitor.logMessage(stepName, "✓ SSH connection ready");
            } catch (Exception e) {
                logger.severe("Failed to establish SSH connection to server: " + e.getMessage());
                processMonitor.logMessage(stepName, "✗ SSH connection failed: " + e.getMessage());
                return null;
            }
            
            // Check for cancellation after SSH connection
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after SSH connection");
                return null;
            }
            
            // Create zip command - zip $NMS_CONFIG content to /tmp/nms_project.zip
            // Use a subshell to ensure the exit code is only captured after zip truly completes
            long timestamp = System.currentTimeMillis();
            String zipPath = String.format("/tmp/nms_project_%d.zip", timestamp);
            
            // Track this file for automatic cleanup
            sshManager.trackRemoteFile(zipPath);
            
            String zipCommand = String.format(
                "cd $NMS_CONFIG && zip -rv %s . 2>&1 && chmod 644 %s && echo 'ZIP_COMPLETED_SUCCESSFULLY'",
                zipPath, zipPath
            );
            
            // Check for cancellation before executing zip command
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before zip command execution");
                return null;
            }
            
            // Use SSHSessionManager to execute the command with progress callback for real-time updates
            processMonitor.logMessage(stepName, "Creating project zip on server...");
            processMonitor.logMessage(stepName, "Executing command: " + zipCommand);
            
            // Create a progress callback that logs zip progress with throttling to prevent UI lag
            ProgressCallback zipProgressCallback = new ProgressCallback() {
                private int fileCount = 0;
                private int lastProgressUpdate = 10;
                private long lastUpdateTime = System.currentTimeMillis();
                
                @Override
                public void onProgress(int percentage, String message) {
                    // Check for cancellation
                    if (!processMonitor.isRunning()) {
                        return;
                    }
                    
                    // Count files being added
                    if (message != null && message.contains("adding:")) {
                        fileCount++;
                        
                        // Update every 10 files OR every 2 seconds (whichever comes first) to prevent UI lag
                        long currentTime = System.currentTimeMillis();
                        if (fileCount % 10 == 0 || (currentTime - lastUpdateTime) >= 2000) {
                            int progress = Math.min(10 + (fileCount / 2), 80);
                            processMonitor.updateState(stepName, progress);
                            processMonitor.logMessage(stepName, "Zipping files... (" + fileCount + " files processed)");
                            lastProgressUpdate = progress;
                            lastUpdateTime = currentTime;
                        }
                    }
                }
                
                @Override
                public void onComplete(String message) {
                    if (message != null && !message.trim().isEmpty() && message.contains("ZIP_COMPLETED_SUCCESSFULLY")) {
                        processMonitor.logMessage(stepName, "✓ Zip creation completed");
                    }
                }
                
                @Override
                public void onError(String error) {
                    if (error != null && !error.trim().isEmpty()) {
                        processMonitor.logMessage(stepName, "Error: " + error);
                    }
                }
                
                @Override
                public boolean isCancelled() {
                    return !processMonitor.isRunning();
                }
            };
            
            CommandResult result = sshManager.executeCommand(zipCommand, 600, zipProgressCallback); // 10 minutes timeout for zip commands
            
            // Check for cancellation after zip command execution
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after zip command execution");
                return null;
            }
            if (!result.isSuccess()) {
                logger.severe("Failed to create zip on server, exit status: " + result.getExitCode());
                processMonitor.logMessage(stepName, "Failed to create zip on server, exit status: " + result.getExitCode());
                return null;
            }
            
            // Check for completion marker in output
            if (!result.getOutput().contains("ZIP_COMPLETED_SUCCESSFULLY")) {
                logger.severe("Zip command did not complete successfully - missing completion marker. Output: " + result.getOutput());
                processMonitor.logMessage(stepName, "Zip command did not complete successfully - missing completion marker");
                return null;
            }
            
            processMonitor.logMessage(stepName, "Project zip created successfully on server with completion marker");
            
            processMonitor.updateState(stepName, 20);
            logger.info("Successfully created project zip on server: " + zipPath);
            return zipPath;
            
        } catch (Exception e) {
            logger.severe("Error creating zip on server: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Downloads the zip file from server to local temp folder
     */
    private String downloadProjectZip(String remoteZipPath, ProcessMonitor processMonitor, String stepName) {
        try {
            processMonitor.updateState(stepName, 30);
            
            // Check for cancellation before starting download
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before download");
                return null;
            }
            
            try {
                processMonitor.logMessage(stepName, "Establishing SSH connection for download...");
                sshManager.initialize();
                processMonitor.logMessage(stepName, "SSH connection established for download");
            } catch (Exception e) {
                logger.severe("Failed to establish SSH connection for download: " + e.getMessage());
                processMonitor.logMessage(stepName, "Failed to establish SSH connection for download: " + e.getMessage());
                return null;
            }
            
            // Check for cancellation after SSH connection
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after SSH connection for download");
                return null;
            }
            
            SFTPClient sftpClient = sshManager.openSftp();
            
            // Create local temp directory
            String tempDir = System.getProperty("java.io.tmpdir");
            String localZipPath = Paths.get(tempDir, "nms_project_" + System.currentTimeMillis() + ".zip").toString();
            
            // Track local file for cleanup
            sshManager.trackLocalFile(localZipPath);
            
            // Check for cancellation before download
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before file download");
                sftpClient.close();
                return null;
            }
            
            // Get file size first for progress tracking
            FileAttributes attrs = sftpClient.stat(remoteZipPath);
            long fileSize = attrs.getSize();
            processMonitor.logMessage(stepName, "Remote project zip file size: " + fileSize + " bytes (" + (fileSize / 1024 / 1024) + " MB)");
            
            // Create progress tracking variables
            final long[] bytesDownloaded = {0};
            final long[] startTime = {System.currentTimeMillis()};
            final long[] lastUpdateTime = {System.currentTimeMillis()};
            
            // Start a background thread to monitor download progress BEFORE starting download
            processMonitor.logMessage(stepName, "Starting SFTP download to: " + localZipPath);
            processMonitor.logMessage(stepName, "Starting download progress monitoring...");
            
            // Start progress monitoring thread BEFORE download
            Thread progressMonitor = new Thread(() -> {
                try {
                    while (true) {
                        // Check for cancellation
                        if (!processMonitor.isRunning()) {
                            logger.info("Download progress monitoring cancelled");
                            return;
                        }
                        
                        // Check if file exists and get its current size
                        File localFile = new File(localZipPath);
                        if (localFile.exists()) {
                            long currentSize = localFile.length();
                            
                            // Update progress every 2 seconds for real-time updates
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastUpdateTime[0] >= 2000 || currentSize >= fileSize) {
                                logger.info("Download progress update: " + currentSize + " / " + fileSize + " bytes");
                                
                                // Calculate progress percentage
                                int progressPercent = (int) ((currentSize * 100) / fileSize);
                                
                                // Calculate download speed
                                double speedBps = currentSize / ((currentTime - startTime[0]) / 1000.0);
                                String speedText = formatSpeed(speedBps);
                                
                                // Calculate ETA
                                long remainingBytes = fileSize - currentSize;
                                long estimatedTimeRemaining = 0;
                                if (speedBps > 0) {
                                    estimatedTimeRemaining = (long) (remainingBytes / speedBps);
                                }
                                String timeRemainingText = formatTime(estimatedTimeRemaining);
                                
                                // Format sizes
                                String downloadedText = formatSize(currentSize);
                                String totalText = formatSize(fileSize);
                                
                                // Create detailed progress message
                                String progressMessage = String.format("Downloading: %s / %s (%.1f%%) - Speed: %s - ETA: %s", 
                                    downloadedText, totalText, (currentSize * 100.0 / fileSize), speedText, timeRemainingText);
                                
                                // Log to console for debugging
                                logger.info("Download progress: " + progressMessage);
                                
                                // Calculate progress percentage for UI (30-50% range)
                                int uiProgress = 30 + (int)(progressPercent * 0.2);
                                
                                // Send SINGLE progress update to prevent UI lag (removed duplicate calls)
                                processMonitor.updateState(stepName, uiProgress);
                                processMonitor.logMessage(stepName, progressMessage);
                                
                                lastUpdateTime[0] = currentTime;
                                
                                // If download is complete, break
                                if (currentSize >= fileSize) {
                                    logger.info("Download completed, stopping progress monitoring");
                                    processMonitor.logMessage(stepName, "Download completed successfully");
                                    break;
                                }
                            }
                        } else {
                            // File doesn't exist yet, show initial progress every 2 seconds
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastUpdateTime[0] >= 2000) {
                                String message = "Download starting... (0 / " + formatSize(fileSize) + ")";
                                logger.info(message);
                                processMonitor.logMessage(stepName, message);
                                lastUpdateTime[0] = currentTime;
                            }
                        }
                        
                        // Sleep for 500ms for more frequent updates
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    // Thread interrupted, stop monitoring
                    logger.info("Download progress monitoring interrupted");
                    Thread.currentThread().interrupt();
                }
            });
            
            // Start the progress monitoring thread
            progressMonitor.start();
            
            // Now start the actual SFTP download
            logger.info("Starting actual SFTP download...");
            processMonitor.logMessage(stepName, "Downloading file from server...");
            
            try {
                sftpClient.get(remoteZipPath, localZipPath);
                logger.info("SFTP download completed");
            } catch (Exception e) {
                logger.severe("SFTP download failed: " + e.getMessage());
                throw new RuntimeException("SFTP download failed: " + e.getMessage(), e);
            }
            
            // Wait for progress monitoring to complete
            try {
                progressMonitor.join();
                logger.info("Download progress monitoring completed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Download monitoring interrupted");
            }
            
            sftpClient.close();
            
            // Check for cancellation after download
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user after file download");
                return null;
            }
            
            processMonitor.logMessage(stepName, "File download completed successfully");
            
            processMonitor.updateState(stepName, 50);
            logger.info("Successfully downloaded project zip to: " + localZipPath);
            return localZipPath;
            
        } catch (Exception e) {
            logger.severe("Error downloading zip from server: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts the downloaded zip to the local project folder
     */
    private boolean extractProjectZip(String localZipPath, ProcessMonitor processMonitor, String stepName) {
        try {
            processMonitor.updateState(stepName, 60);
            
            // Check for cancellation before starting extraction
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before extraction");
                return false;
            }
            
            // Get project folder path
            String projectFolder = project.getProjectFolderPath();
            if (projectFolder == null || projectFolder.trim().isEmpty()) {
                logger.severe("Project folder path is not configured");
                processMonitor.logMessage(stepName, "Project folder path is not configured");
                return false;
            }
            processMonitor.logMessage(stepName, "Extracting to project folder: " + projectFolder);
            
            // Create project folder if it doesn't exist
            File projectDir = new File(projectFolder);
            if (!projectDir.exists()) {
                processMonitor.logMessage(stepName, "Creating project directory: " + projectFolder);
                boolean created = projectDir.mkdirs();
                if (!created) {
                    logger.severe("Failed to create project directory: " + projectFolder);
                    processMonitor.logMessage(stepName, "Failed to create project directory: " + projectFolder);
                    return false;
                }
                processMonitor.logMessage(stepName, "Project directory created successfully");
            }
            
            // Extract zip file
            processMonitor.logMessage(stepName, "Starting extraction of zip file...");
            try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(localZipPath))) {
                ZipEntry entry = zipIn.getNextEntry();
                int fileCount = 0;
                int totalFiles = 0;
                long lastUpdateTime = System.currentTimeMillis();
                
                // First pass: count total files for progress tracking
                try (ZipInputStream countIn = new ZipInputStream(new FileInputStream(localZipPath))) {
                    while (countIn.getNextEntry() != null) {
                        totalFiles++;
                    }
                }
                
                processMonitor.logMessage(stepName, "Extracting " + totalFiles + " files...");
                
                while (entry != null) {
                    // Check for cancellation during extraction loop
                    if (!processMonitor.isRunning()) {
                        processMonitor.logMessage(stepName, "Operation cancelled by user during extraction");
                        return false;
                    }
                    
                    fileCount++;
                    String filePath = projectFolder + File.separator + entry.getName();
                    
                    // Update progress every 2 seconds (similar to java folder extraction)
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= 2000 || fileCount == totalFiles) {
                        int progress = 60 + (int)((fileCount * 20.0) / totalFiles);
                        processMonitor.updateState(stepName, progress);
                        processMonitor.logMessage(stepName, "Extracting files... (" + fileCount + " files processed)");
                        lastUpdateTime = currentTime;
                    }
                    
                    if (!entry.isDirectory()) {
                        // Create parent directories if they don't exist
                        File parentDir = new File(filePath).getParentFile();
                        if (parentDir != null && !parentDir.exists()) {
                            parentDir.mkdirs();
                        }
                        
                        // Extract file
                        try (FileOutputStream fos = new FileOutputStream(filePath)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = zipIn.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    } else {
                        // Create directory
                        new File(filePath).mkdirs();
                    }
                    
                    entry = zipIn.getNextEntry();
                }
            }
            
            processMonitor.updateState(stepName, 80);
            logger.info("Successfully extracted project to: " + projectFolder);
            return true;
            
        } catch (Exception e) {
            logger.severe("Error extracting project zip: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cleans up the remote zip file
     */
    private void cleanupRemoteZip(String remoteZipPath, ProcessMonitor processMonitor, String stepName) {
        try {
            processMonitor.updateState(stepName, 85);
            
            // Check for cancellation before cleanup
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before remote cleanup");
                return;
            }
            
            try {
                sshManager.initialize();
                CommandResult result = sshManager.executeCommand("rm -f " + remoteZipPath, 30);
                if (!result.isSuccess()) {
                    logger.warning("Failed to clean up remote zip file, exit status: " + result.getExitCode());
                }
            } catch (Exception e) {
                logger.warning("Failed to establish SSH connection for cleanup: " + e.getMessage());
                return;
            }
            
            logger.info("Cleaned up remote zip file: " + remoteZipPath);
            
        } catch (Exception e) {
            logger.warning("Error cleaning up remote zip file: " + e.getMessage());
        }
    }
    
    /**
     * Cleans up the local zip file
     */
    private void cleanupLocalZip(String localZipPath, ProcessMonitor processMonitor, String stepName) {
        try {
            processMonitor.updateState(stepName, 90);
            
            // Check for cancellation before cleanup
            if (!processMonitor.isRunning()) {
                processMonitor.logMessage(stepName, "Operation cancelled by user before local cleanup");
                return;
            }
            
            File zipFile = new File(localZipPath);
            if (zipFile.exists()) {
                boolean deleted = zipFile.delete();
                if (deleted) {
                    logger.info("Cleaned up local zip file: " + localZipPath);
                } else {
                    logger.warning("Failed to delete local zip file: " + localZipPath);
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error cleaning up local zip file: " + e.getMessage());
        }
    }
    
    /**
     * Validates that the server has $NMS_CONFIG directory
     */
    public boolean validateServerProjectStructure(ProcessMonitor processMonitor) {
        ProgressCallback progressCallback = new ProcessMonitorAdapter(processMonitor, "server_validation");
        return validateServerProjectStructure(progressCallback);
    }
    
    /**
     * Validates that the server has $NMS_CONFIG directory using ProgressCallback
     */
    public boolean validateServerProjectStructure(ProgressCallback progressCallback) {
        try {
            progressCallback.onProgress(10, "=== Server Project Structure Validation ===");
            progressCallback.onProgress(15, "Host: " + project.getHost());
            
            // Check for cancellation before starting validation
            if (progressCallback.isCancelled()) {
                progressCallback.onError("Operation cancelled by user before validation");
                return false;
            }
            
            try {
                progressCallback.onProgress(20, "Establishing SSH connection to server...");
                progressCallback.onProgress(25, "Connecting as: " + (project.isUseLdap() ? project.getLdapUser() : project.getHostUser()));
                sshManager.initialize();
                progressCallback.onProgress(30, "✓ SSH connection established successfully");
            } catch (Exception e) {
                progressCallback.onError("✗ Failed to connect to server: " + e.getMessage());
                return false;
            }
            
            // Check for cancellation after SSH connection
            if (progressCallback.isCancelled()) {
                progressCallback.onError("Operation cancelled by user after SSH connection");
                return false;
            }
            
            // Check NMS_CONFIG environment variable first
            progressCallback.onProgress(40, "Resolving NMS_CONFIG environment variable...");
            try {
                String nmsConfig = sshManager.resolveEnvVar("NMS_CONFIG");
                if (nmsConfig != null && !nmsConfig.trim().isEmpty()) {
                    progressCallback.onProgress(50, "✓ NMS_CONFIG resolved to: " + nmsConfig);
                } else {
                    progressCallback.onError("✗ NMS_CONFIG environment variable is not set or empty");
                    return false;
                }
            } catch (Exception e) {
                progressCallback.onError("✗ Could not resolve NMS_CONFIG: " + e.getMessage());
                return false;
            }
            
            progressCallback.onProgress(60, "Verifying NMS_CONFIG directory exists on server...");
            CommandResult result = sshManager.executeCommand("test -d $NMS_CONFIG && echo 'EXISTS' || echo 'NOT_FOUND'", 30);
            String output = result.getOutput().trim();
            progressCallback.onProgress(70, "Command executed (exit code: " + result.getExitCode() + ")");
            
            if ("EXISTS".equals(output)) {
                progressCallback.onProgress(80, "✓ NMS_CONFIG directory exists on server");
                
                // Additional validation - check if directory is readable
                progressCallback.onProgress(85, "Checking directory permissions...");
                CommandResult lsResult = sshManager.executeCommand("ls -ld $NMS_CONFIG", 30);
                if (lsResult.isSuccess()) {
                    progressCallback.onProgress(90, "Directory permissions: " + lsResult.getOutput().trim());
                }
                
                progressCallback.onProgress(100, "✓ Server project structure validated successfully");
                progressCallback.onComplete("Server project structure validated");
                return true;
            } else {
                progressCallback.onError("✗ NMS_CONFIG directory not found on server");
                progressCallback.onError("Server response: " + output);
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("Error validating server project structure: " + e.getMessage());
            progressCallback.onError("✗ Exception during validation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Format bytes to human readable format
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Format bytes per second to human readable format
     */
    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) return String.format("%.1f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        if (bytesPerSecond < 1024 * 1024 * 1024) return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        return String.format("%.1f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Format time in seconds to human readable format
     */
    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format("%dm %ds", seconds / 60, seconds % 60);
        return String.format("%dh %dm %ds", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
