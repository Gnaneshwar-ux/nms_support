package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.FileAttributes;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager;
import com.nms.support.nms_support.service.globalPack.ProcessMonitorManager;
import com.nms.support.nms_support.service.globalPack.UnifiedSSHService;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Professional SFTP download and unzip service
 * Handles remote zip creation and local extraction with proper error handling
 */
public class SFTPDownloadAndUnzip {
    private static final int BUFFER_SIZE = 8192;
    private static final int ZIP_TIMEOUT_SECONDS = 600; // 5 minutes for zip operations
    private static final int COMMAND_TIMEOUT_SECONDS = 30; // 30 seconds for other commands

    /**
     * Download and extract Java files from remote server with purpose-based session isolation
     * 
     * @param localExtractDir Directory to extract files to
     * @param project Project configuration
     * @param progressCallback Progress callback for updates
     * @param purpose Purpose for session isolation (e.g., "project_only", "product_only", "project_and_product")
     */
    public static void start(String localExtractDir, ProjectEntity project, ProgressCallback progressCallback, String purpose) {
        SSHJSessionManager ssh = null;
        SFTPClient sftpClient = null;
        String remoteZipFilePath = null;

        try {
            // Check for cancellation
            if (progressCallback.isCancelled()) {
                progressCallback.onError("SFTP download cancelled by user");
                return;
            }

            // Validate project authentication
            if (!UnifiedSSHService.validateProjectAuth(project)) {
                progressCallback.onError("Invalid project authentication configuration");
                return;
            }

            progressCallback.onProgress(10, "Connecting to server...");
            LoggerUtil.getLogger().info("🔐 Using unified SSH authentication (purpose: " + purpose + "): " + UnifiedSSHService.getAuthDescription(project));
            ssh = UnifiedSSHService.createSSHSession(project, purpose);
            
            // Register session with ProcessMonitorManager for proper cleanup
            ProcessMonitorManager.getInstance().registerSession(
                ssh.getSessionId(), 
                ssh, 
                true, // Mark as in progress
                purpose // Include purpose for proper session tracking
            );
            
            // Set up cancellation handler - we'll check for cancellation in the zip process
            ssh.initialize();
            progressCallback.onProgress(20, "Connected to server");

            // Resolve NMS_HOME and determine remote directory
            String remoteDir = resolveRemoteDirectory(ssh, progressCallback);
            progressCallback.onProgress(30, "Remote directory resolved: " + remoteDir);

            // Check for cancellation before creating zip
            if (progressCallback.isCancelled()) {
                progressCallback.onError("SFTP download cancelled by user before zip creation");
                ProcessMonitorManager.getInstance().immediateSessionCleanup(ssh.getSessionId());
                return;
            }

            // Create zip file on server
            remoteZipFilePath = createZipFile(ssh, remoteDir, progressCallback, project);
            progressCallback.onProgress(60, "Zip file created successfully");

            // Check for cancellation before download
            if (progressCallback.isCancelled()) {
                progressCallback.onError("SFTP download cancelled by user before download");
                // Clean up remote zip file
                try {
                    LoggerUtil.getLogger().info("Cleaning up remote zip after cancellation: " + remoteZipFilePath);
                    ssh.executeCommandWithoutCancellation("rm -f " + remoteZipFilePath, 30);
                    LoggerUtil.getLogger().info("Remote zip file cleaned up successfully");
                } catch (Exception cleanupEx) {
                    LoggerUtil.getLogger().warning("Failed to cleanup remote zip: " + cleanupEx.getMessage());
                }
                ProcessMonitorManager.getInstance().immediateSessionCleanup(ssh.getSessionId());
                return;
            }

            // Download zip file with detailed progress tracking to temp folder
            sftpClient = ssh.openSftp();
            progressCallback.onProgress(70, "Starting download...");
            
            // Create temp directory for download
            String tempDir = System.getProperty("java.io.tmpdir");
            String localZipFilePath = tempDir + File.separator + "downloaded_java_" + System.currentTimeMillis() + ".zip";
            
            // Track local file for cleanup
            ssh.trackLocalFile(localZipFilePath);
            LoggerUtil.getLogger().info("Downloading to temp file: " + localZipFilePath);
            
            // Get file size first
            FileAttributes attrs = sftpClient.stat(remoteZipFilePath);
            long fileSize = attrs.getSize();
            LoggerUtil.getLogger().info("Remote zip file size: " + fileSize + " bytes (" + (fileSize / 1024 / 1024) + " MB)");
            
            // Create progress tracking variables
            final long[] bytesDownloaded = {0};
            final long[] startTime = {System.currentTimeMillis()};
            final long[] lastUpdateTime = {System.currentTimeMillis()};
            
        // Start a background thread to monitor download progress BEFORE starting download
        LoggerUtil.getLogger().info("Starting SFTP download to: " + localZipFilePath);
        progressCallback.onProgress(70, "Starting SFTP download...");
        
        // Start progress monitoring thread BEFORE download
        Thread progressMonitor = new Thread(() -> {
            try {
                LoggerUtil.getLogger().info("Starting download progress monitoring...");
                progressCallback.onProgress(70, "Starting download progress monitoring...");
                
                while (true) {
                    // Check for cancellation
                    if (progressCallback.isCancelled()) {
                        LoggerUtil.getLogger().info("Download progress monitoring cancelled");
                        return;
                    }
                    
                    // Check if file exists and get its current size
                    File localFile = new File(localZipFilePath);
                    if (localFile.exists()) {
                        long currentSize = localFile.length();
                        
                        // Update progress every 2 seconds for real-time updates (instead of 5 seconds)
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateTime[0] >= 2000 || currentSize >= fileSize) {
                            LoggerUtil.getLogger().info("Download progress update: " + currentSize + " / " + fileSize + " bytes");
                            
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
                            LoggerUtil.getLogger().info("Download progress: " + progressMessage);
                            System.out.println("[DOWNLOAD] " + progressMessage);
                            
                            // Calculate progress percentage for UI (70-85% range)
                            int uiProgress = 70 + (int)(progressPercent * 0.15);
                            
                            // Send SINGLE progress update to avoid UI flooding (removed duplicate calls)
                            progressCallback.onProgress(uiProgress, progressMessage);
                            
                            lastUpdateTime[0] = currentTime;
                            
                            // If download is complete, break
                            if (currentSize >= fileSize) {
                                LoggerUtil.getLogger().info("Download completed, stopping progress monitoring");
                                progressCallback.onProgress(85, "Download completed successfully");
                                break;
                            }
                        }
                    } else {
                        // File doesn't exist yet, show initial progress every 2 seconds
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateTime[0] >= 2000) {
                            String message = "Download starting... (0 / " + formatSize(fileSize) + ")";
                            LoggerUtil.getLogger().info(message);
                            progressCallback.onProgress(70, message);
                            lastUpdateTime[0] = currentTime;
                        }
                    }
                    
                    // Sleep for 500ms for more frequent updates
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                // Thread interrupted, stop monitoring
                LoggerUtil.getLogger().info("Download progress monitoring interrupted");
                Thread.currentThread().interrupt();
            }
        });
        
        // Start the progress monitoring thread
        progressMonitor.start();
        
        // Now start the actual SFTP download
        LoggerUtil.getLogger().info("Starting actual SFTP download...");
        progressCallback.onProgress(71, "Downloading file from server...");
        
        try {
            sftpClient.get(remoteZipFilePath, localZipFilePath);
            LoggerUtil.getLogger().info("SFTP download completed");
        } catch (Exception e) {
            LoggerUtil.getLogger().severe("SFTP download failed: " + e.getMessage());
            throw new RuntimeException("SFTP download failed: " + e.getMessage(), e);
        }
        
        // Wait for progress monitoring to complete
        try {
            progressMonitor.join();
            LoggerUtil.getLogger().info("Download progress monitoring completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Download monitoring interrupted");
        }
        
            progressCallback.onProgress(85, "Download completed successfully");

            // Check for cancellation before cleanup and extraction
            if (progressCallback.isCancelled()) {
                progressCallback.onError("SFTP download cancelled by user before extraction");
                // Clean up both remote and local zip files
                try {
                    LoggerUtil.getLogger().info("Cleaning up remote zip after cancellation: " + remoteZipFilePath);
                    ssh.executeCommandWithoutCancellation("rm -f " + remoteZipFilePath, 30);
                } catch (Exception cleanupEx) {
                    LoggerUtil.getLogger().warning("Failed to cleanup remote zip: " + cleanupEx.getMessage());
                }
                try {
                    LoggerUtil.getLogger().info("Cleaning up local zip after cancellation: " + localZipFilePath);
                    File localFile = new File(localZipFilePath);
                    if (localFile.exists() && localFile.delete()) {
                        LoggerUtil.getLogger().info("Local zip file cleaned up successfully");
                    }
                } catch (Exception cleanupEx) {
                    LoggerUtil.getLogger().warning("Failed to cleanup local zip: " + cleanupEx.getMessage());
                }
                ProcessMonitorManager.getInstance().immediateSessionCleanup(ssh.getSessionId());
                return;
            }

            // Clean up remote zip file using SSH (more reliable than SFTP)
            try {
                LoggerUtil.getLogger().info("Deleting remote zip file: " + remoteZipFilePath);
                SSHJSessionManager.CommandResult deleteResult = ssh.executeCommandWithoutCancellation("rm -f " + remoteZipFilePath, 30);
                if (deleteResult.isSuccess()) {
                    LoggerUtil.getLogger().info("Remote zip file deleted successfully: " + remoteZipFilePath);
                } else {
                    LoggerUtil.getLogger().warning("Failed to delete remote zip file: " + deleteResult.getOutput());
                }
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to remove remote zip file: " + e.getMessage());
            }

            // Extract zip file to java folder
            progressCallback.onProgress(90, "Preparing extraction directory...");
            
            // Create java directory (delete and recreate if exists)
            String productJavaDir = localExtractDir + File.separator + "java";
            File productJavaFolder = new File(productJavaDir);
            
            if (productJavaFolder.exists()) {
                LoggerUtil.getLogger().info("Deleting existing java folder: " + productJavaDir);
                progressCallback.onProgress(91, "Cleaning existing java folder...");
                deleteDirectory(productJavaFolder);
            }
            
            // Create the directory
            if (!productJavaFolder.mkdirs()) {
                throw new IOException("Failed to create java directory: " + productJavaDir);
            }
            
            LoggerUtil.getLogger().info("Created java directory: " + productJavaDir);
            progressCallback.onProgress(92, "Extracting to java folder...");
            
            // Extract zip file to java directory
            unzipFile(localZipFilePath, productJavaDir, progressCallback);
            progressCallback.onProgress(95, "Extraction completed to java folder");

            // Clean up local zip file after successful extraction
            LoggerUtil.getLogger().info("Cleaning up temp zip file: " + localZipFilePath);
            if (new File(localZipFilePath).delete()) {
                LoggerUtil.getLogger().info("Temp zip file deleted successfully");
                ssh.untrackLocalFile(localZipFilePath); // Stop tracking since it's deleted
            } else {
                LoggerUtil.getLogger().warning("Failed to delete temp zip file: " + localZipFilePath);
            }

            progressCallback.onComplete("Java download and extraction completed successfully");

        } catch (Exception e) {
            LoggerUtil.getLogger().severe("SFTP download failed: " + e.getMessage());
            LoggerUtil.error(e);
            progressCallback.onError("Error: " + e.getMessage());
            
            // Clean up any created files on error
            if (ssh != null && remoteZipFilePath != null) {
                try {
                    LoggerUtil.getLogger().info("Cleaning up remote zip on error: " + remoteZipFilePath);
                    ssh.executeCommandWithoutCancellation("rm -f " + remoteZipFilePath, 30);
                } catch (Exception cleanupEx) {
                    LoggerUtil.getLogger().warning("Failed to cleanup remote zip: " + cleanupEx.getMessage());
                }
            }
            
            // Immediate session cleanup on failure
            if (ssh != null) {
                ProcessMonitorManager.getInstance().immediateSessionCleanup(ssh.getSessionId());
            }
        } finally {
            // Clean up resources
            if (sftpClient != null) {
                try { sftpClient.close(); } catch (Exception ignored) {}
            }
            if (ssh != null) {
                try { 
                    // Mark operation as completed
                    ProcessMonitorManager.getInstance().updateOperationState(ssh.getSessionId(), false);
                    // ssh.close() will automatically clean up any tracked files
                    ssh.close(); 
                } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * Download and extract Java files from remote server (backward compatibility)
     * @deprecated Use start(String, ProjectEntity, ProgressCallback, String) with purpose parameter
     * 
     * @param localExtractDir Directory to extract files to
     * @param project Project configuration
     * @param progressCallback Progress callback for updates
     */
    @Deprecated
    public static void start(String localExtractDir, ProjectEntity project, ProgressCallback progressCallback) {
        start(localExtractDir, project, progressCallback, "default");
    }

    /**
     * Resolve the remote directory path for java files
     * REQUIRES: NMS_HOME environment variable must be set and java directory must exist
     * Throws RuntimeException if requirements are not met
     */
    private static String resolveRemoteDirectory(SSHJSessionManager ssh, ProgressCallback progressCallback) throws Exception {
        LoggerUtil.getLogger().info("Resolving remote java directory...");
        progressCallback.onProgress(10, "Resolving remote java directory...");
        
        // REQUIRED: Resolve NMS_HOME environment variable
        String nmsHome;
        try {
            nmsHome = ssh.resolveEnvVar("NMS_HOME");
            LoggerUtil.getLogger().info("NMS_HOME resolved to: '" + nmsHome + "'");
            
            if (nmsHome == null || nmsHome.trim().isEmpty()) {
                String errorMsg = "NMS_HOME environment variable is not set or empty";
                LoggerUtil.getLogger().severe(errorMsg);
                progressCallback.onError(errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = "Failed to resolve NMS_HOME environment variable: " + e.getMessage();
            LoggerUtil.getLogger().severe(errorMsg);
            progressCallback.onError(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        // Construct java path from NMS_HOME
        String javaPath = nmsHome + "/java";
        LoggerUtil.getLogger().info("Checking java directory: " + javaPath);
        progressCallback.onProgress(15, "Verifying java directory exists...");
        
        // REQUIRED: Verify the java directory exists
        try {
            SSHJSessionManager.CommandResult result = ssh.executeCommand("test -d " + javaPath, 30);
            if (!result.isSuccess()) {
                String errorMsg = "Java directory not found at: " + javaPath + " (NMS_HOME: " + nmsHome + ")";
                LoggerUtil.getLogger().severe(errorMsg);
                progressCallback.onError(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            LoggerUtil.getLogger().info("Java directory verified: " + javaPath);
            progressCallback.onProgress(20, "Java directory verified: " + javaPath);
            return javaPath;
            
        } catch (Exception e) {
            String errorMsg = "Failed to verify java directory at " + javaPath + ": " + e.getMessage();
            LoggerUtil.getLogger().severe(errorMsg);
            progressCallback.onError(errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Create zip file on remote server excluding working folder
     * Follows the same pattern as ServerProjectService for consistency
     */
    private static String createZipFile(SSHJSessionManager ssh, String remoteDir, ProgressCallback progressCallback, ProjectEntity project) throws Exception {
        // Use timestamp for unique file naming (same pattern as ServerProjectService)
        long timestamp = System.currentTimeMillis();
        String remoteZipFilePath = String.format("/tmp/downloaded_java_%d.zip", timestamp);
        
        // Track this file for cleanup
        ssh.trackRemoteFile(remoteZipFilePath);
        
        // Normalize the remote directory path
        String normalizedRemoteDir = remoteDir.endsWith("/") ? remoteDir : remoteDir + "/";
        
        // Build zip command - same format as ServerProjectService
        String zipCommand = String.format(
            "cd %s && zip -rv %s . -x 'working/*' 2>&1 && chmod 644 %s && echo 'ZIP_COMPLETED_SUCCESSFULLY'",
            normalizedRemoteDir, remoteZipFilePath, remoteZipFilePath
        );
        
        LoggerUtil.getLogger().info("=== ZIP FILE CREATION ===");
        LoggerUtil.getLogger().info("Remote directory: " + normalizedRemoteDir);
        LoggerUtil.getLogger().info("Zip file path: " + remoteZipFilePath);
        LoggerUtil.getLogger().info("Zip command: " + zipCommand);
        
        progressCallback.onProgress(40, "Creating zip file (excluding working folder)...");
        
        // Verify directory exists
        progressCallback.onProgress(45, "Verifying directory exists...");
        SSHJSessionManager.CommandResult dirExists = ssh.executeCommand("test -d " + normalizedRemoteDir, COMMAND_TIMEOUT_SECONDS);
        if (dirExists.getExitCode() != 0) {
            throw new Exception("Directory does not exist: " + normalizedRemoteDir);
        }
        progressCallback.onProgress(50, "Directory verified: " + normalizedRemoteDir);
        
        // Check for cancellation before executing zip
        if (progressCallback.isCancelled()) {
            throw new RuntimeException("Zip operation cancelled by user");
        }
        
        progressCallback.onProgress(55, "Creating zip on server (this may take several minutes)...");
        LoggerUtil.getLogger().info("Starting zip command execution...");
        
        // Create simple progress callback that counts files (same as ServerProjectService)
        ProgressCallback zipProgressCallback = new ProgressCallback() {
            private int fileCount = 0;
            private int lastProgressUpdate = 55;
            private long lastProgressTime = System.currentTimeMillis();
            
            @Override
            public void onProgress(int progress, String message) {
                // Check for cancellation
                if (progressCallback.isCancelled()) {
                    LoggerUtil.getLogger().info("Zip cancelled by user");
                    try {
                        ssh.forceKillCurrentCommand();
                    } catch (Exception e) {
                        LoggerUtil.getLogger().warning("Failed to kill command: " + e.getMessage());
                    }
                    return;
                }
                
                // Count files being added
                if (message != null && (message.contains("adding:") || message.contains("updating:"))) {
                    fileCount++;
                    
                    // Update every 10 files OR every 2 seconds
                    long currentTime = System.currentTimeMillis();
                    boolean shouldUpdate = (fileCount % 10 == 0) || ((currentTime - lastProgressTime) >= 2000);
                    
                    if (shouldUpdate) {
                        int dynamicProgress = Math.min(55 + (fileCount / 2), 85);
                        progressCallback.onProgress(dynamicProgress, "Zipping files... (" + fileCount + " files processed)");
                        lastProgressUpdate = dynamicProgress;
                        lastProgressTime = currentTime;
                        LoggerUtil.getLogger().fine("Zip progress: " + fileCount + " files");
                    }
                }
            }
            
            @Override
            public void onComplete(String message) {
                // Not used in this simplified approach
            }
            
            @Override
            public void onError(String error) {
                LoggerUtil.getLogger().warning("Zip error: " + error);
            }
            
            @Override
            public boolean isCancelled() {
                return progressCallback.isCancelled();
            }
        };
        
        // Execute zip command with 10 minute timeout (same as ServerProjectService)
        SSHJSessionManager.CommandResult result = null;
        try {
            result = ssh.executeCommand(zipCommand, 600, zipProgressCallback);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("cancelled by user")) {
                LoggerUtil.getLogger().info("Zip cancelled during execution, cleaning up...");
                try {
                    ssh.executeCommandWithoutCancellation("rm -f " + remoteZipFilePath, 30);
                } catch (Exception cleanupEx) {
                    LoggerUtil.getLogger().warning("Cleanup failed: " + cleanupEx.getMessage());
                }
                throw new RuntimeException("Zip operation cancelled by user");
            } else {
                throw e;
            }
        }
        
        // Check for cancellation after zip execution
        if (progressCallback.isCancelled()) {
            LoggerUtil.getLogger().info("Cancelled after zip execution, cleaning up...");
            try {
                ssh.executeCommandWithoutCancellation("rm -f " + remoteZipFilePath, 30);
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Cleanup failed: " + e.getMessage());
            }
            throw new RuntimeException("Zip operation cancelled by user");
        }
        
        // Verify command succeeded
        if (!result.isSuccess()) {
            throw new Exception("Zip command failed with exit code " + result.getExitCode());
        }
        
        // CRITICAL: Check for completion marker AFTER command finishes (not during)
        // This prevents premature completion detection
        if (!result.getOutput().contains("ZIP_COMPLETED_SUCCESSFULLY")) {
            LoggerUtil.getLogger().severe("Zip missing completion marker");
            throw new Exception("Zip command did not complete successfully - missing completion marker");
        }
        
        LoggerUtil.getLogger().info("=== ZIP COMMAND RESULT ===");
        LoggerUtil.getLogger().info("Zip exit code: " + result.getExitCode());
        LoggerUtil.getLogger().info("Output length: " + result.getOutput().length());
        LoggerUtil.getLogger().info("Completion marker found: YES");
        LoggerUtil.getLogger().info("=== END ZIP COMMAND RESULT ===");
        
        progressCallback.onProgress(90, "Zip completed successfully");
        
        // File is ready immediately - no artificial delays needed
        LoggerUtil.getLogger().info("=== ZIP FILE CREATION COMPLETED ===");
        progressCallback.onProgress(95, "Zip file ready for download");
        
        // Stop tracking this file since it will be explicitly deleted after download
        ssh.untrackRemoteFile(remoteZipFilePath);
        
        return remoteZipFilePath;
    }

    /**
     * Check if zip command was successful based on output content
     */
    private static boolean isZipSuccessful(String output) {
        if (output == null || output.trim().isEmpty()) {
            return false;
        }
        
        String lowerOutput = output.toLowerCase();
        
        // Must have the completion marker
        boolean hasCompletionMarker = lowerOutput.contains("zip_completed_successfully");
        
        // Check for error indicators
        boolean hasError = lowerOutput.contains("error") || 
                          lowerOutput.contains("failed") ||
                          lowerOutput.contains("no such file") || 
                          lowerOutput.contains("permission denied");
        
        // Return true only if we have completion marker and no errors
        return hasCompletionMarker && !hasError;
    }

    /**
     * Extract zip file to local directory
     */
    private static void unzipFile(String zipFilePath, String destDir, ProgressCallback progressCallback) throws IOException {
        File destDirFile = new File(destDir);
        if (!destDirFile.exists()) {
            destDirFile.mkdirs();
        }

        LoggerUtil.getLogger().info("Starting extraction from: " + zipFilePath + " to: " + destDir);
        progressCallback.onProgress(90, "Starting extraction...");

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            ZipEntry zipEntry = zis.getNextEntry();
            int fileCount = 0;
            long startTime = System.currentTimeMillis();
            long lastUpdateTime = startTime;
            
            while (zipEntry != null) {
                if (progressCallback.isCancelled()) {
                    throw new IOException("Extraction cancelled by user");
                }
                
                File newFile = newFile(destDirFile, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            if (progressCallback.isCancelled()) {
                                throw new IOException("Extraction cancelled by user");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                
                zipEntry = zis.getNextEntry();
                fileCount++;
                
                // Update progress every 5 seconds with simple status (no random increments)
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= 5000) {
                    progressCallback.onProgress(90, "Extracting files... (" + fileCount + " files processed)");
                    lastUpdateTime = currentTime;
                }
            }
            zis.closeEntry();
            
            LoggerUtil.getLogger().info("Extraction completed. Total files extracted: " + fileCount);
            progressCallback.onProgress(95, "Extraction completed - " + fileCount + " files extracted");
        }
    }

    /**
     * Create new file with security checks
     */
    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }
    
    /**
     * Update download progress with detailed information
     */
    private static void updateDownloadProgress(ProgressCallback progressCallback, long bytesDownloaded, 
            long totalBytes, long elapsedTime, long timeSinceLastUpdate) {
        
        // Calculate progress percentage
        int progressPercent = (int) ((bytesDownloaded * 100) / totalBytes);
        
        // Calculate download speed (bytes per second)
        double speedBps = bytesDownloaded / (elapsedTime / 1000.0);
        String speedText = formatSpeed(speedBps);
        
        // Calculate estimated time remaining
        long remainingBytes = totalBytes - bytesDownloaded;
        long estimatedTimeRemaining = 0;
        if (speedBps > 0) {
            estimatedTimeRemaining = (long) (remainingBytes / speedBps);
        }
        String timeRemainingText = formatTime(estimatedTimeRemaining);
        
        // Format downloaded and total sizes
        String downloadedText = formatSize(bytesDownloaded);
        String totalText = formatSize(totalBytes);
        
        // Create detailed progress message
        String progressMessage = String.format("Downloading: %s / %s (%.1f%%) - Speed: %s - ETA: %s", 
            downloadedText, totalText, (bytesDownloaded * 100.0 / totalBytes), speedText, timeRemainingText);
        
        // Log to console for debugging
        LoggerUtil.getLogger().info("Download progress: " + progressMessage);
        System.out.println("[DOWNLOAD] " + progressMessage);
        
        // Calculate progress percentage for UI (70-85% range)
        int uiProgress = 70 + (int)(progressPercent * 0.15);
        
        // Send detailed progress message to UI
        progressCallback.onProgress(uiProgress, progressMessage);
        
        // Also send a simple progress update for UI
        String simpleMessage = String.format("Download: %.1f%% (%s/%s)", 
            (bytesDownloaded * 100.0 / totalBytes), downloadedText, totalText);
        progressCallback.onProgress(uiProgress, simpleMessage);
        
        // Force UI update by sending a status message
        progressCallback.onProgress(uiProgress, "Download in progress... " + simpleMessage);
    }
    
    /**
     * Format speed in appropriate units
     */
    private static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format("%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024);
        } else {
            return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
        }
    }
    
    /**
     * Format size in appropriate units
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return String.format("%d B", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Format time in appropriate units
     */
    private static String formatTime(long seconds) {
        if (seconds < 60) {
            return String.format("%ds", seconds);
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        }
    }
    
    /**
     * Recursively delete a directory and all its contents
     */
    private static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }
        
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        
        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
        
        LoggerUtil.getLogger().info("Deleted: " + directory.getAbsolutePath());
    }
    
    /**
     * Generates the expected user@host pattern based on the project authentication setup
     * This method determines the correct pattern to look for in console output based on:
     * - If LDAP is used and targetUser is set: looks for targetUser@hostname-prefix
     * - If LDAP is used but no targetUser: looks for ldapUser@hostname-prefix
     * - If basic auth is used: looks for hostUser@hostname-prefix
     * 
     * @param project The project entity containing authentication details
     * @return The expected user@host pattern for filtering console output
     */
    private static String generateUserHostPattern(ProjectEntity project) {
        if (project == null) {
            return "nmsadmin@nms-host"; // Default fallback
        }
        
        String host = project.getHost();
        if (host == null || host.trim().isEmpty()) {
            return "nmsadmin@nms-host"; // Default fallback
        }
        
        // Extract hostname prefix (part before first dot)
        String hostnamePrefix = extractHostnamePrefix(host);
        String hostToUse = hostnamePrefix != null ? hostnamePrefix : host;
        
        String userToCheck;
        boolean useLdap = project.isUseLdap();
        
        if (useLdap) {
            // LDAP authentication: use target user if available, otherwise LDAP user
            String targetUser = project.getTargetUser();
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                userToCheck = targetUser;
            } else {
                userToCheck = project.getLdapUser();
            }
        } else {
            // Basic authentication: use host user
            userToCheck = project.getHostUser();
        }
        
        // Fallback to default if user is null or empty
        if (userToCheck == null || userToCheck.trim().isEmpty()) {
            userToCheck = "nmsadmin";
        }
        
        return userToCheck + "@" + hostToUse;
    }
    
    /**
     * Extracts the hostname prefix (part before first dot) for filtering console output
     * Example: ugbu-phx-674.snphxprshared1.gbucdsint02phx.oraclevcn.com -> ugbu-phx-674
     * 
     * @param fullHostname The full hostname
     * @return The hostname prefix or null if no dot found
     */
    private static String extractHostnamePrefix(String fullHostname) {
        if (fullHostname == null || fullHostname.trim().isEmpty()) {
            return null;
        }
        
        String trimmedHostname = fullHostname.trim();
        int dotIndex = trimmedHostname.indexOf('.');
        
        if (dotIndex > 0) {
            return trimmedHostname.substring(0, dotIndex);
        }
        
        return null;
    }
}