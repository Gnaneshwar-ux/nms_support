package com.nms.support.nms_support.service.globalPack.sshj;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProcessMonitorManager;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * SSHJ-based SSH Session Manager - Drop-in replacement for JSch-based SSHSessionManager.
 * 
 * This class provides the same API as the original SSHSessionManager but uses SSHJ
 * underneath for better reliability, performance, and modern SSH feature support.
 * 
 * Key Features:
 * - Session caching per host/user/purpose
 * - Pre-sudoed shell sessions for elevated command execution
 * - Timeout handling and command interruption
 * - Progress callback support
 * - File tracking for cleanup
 * - Thread-safe command execution
 * 
 * Migration from JSch to SSHJ:
 * - Replace `SSHSessionManager` with `SSHJSessionManager` in your code
 * - All existing method calls remain the same
 * - Improved reliability and performance
 */
public class SSHJSessionManager {
    
    // Connection parameters
    private final String host;
    private final int port;
    private final String sshUser;
    private final String sshPassword;
    private final String keyFilePath;
    private final String targetUser;
    private final String sudoPassword;
    private final String purpose;
    
    // Session state
    private PersistentSudoSession persistentSession;
    private String sessionId;
    
    // File tracking for cleanup
    private final Set<String> createdRemoteFiles = Collections.synchronizedSet(new java.util.HashSet<>());
    private final Set<String> createdLocalFiles = Collections.synchronizedSet(new java.util.HashSet<>());
    
    // Command execution state
    private volatile boolean commandInProgress = false;
    private final Object commandLock = new Object();
    
    /**
     * Constructor with password authentication and purpose-based session isolation.
     * 
     * @param host SSH host
     * @param sshUser SSH username
     * @param sshPassword SSH password
     * @param port SSH port
     * @param targetUser Target user for sudo (null if not needed)
     * @param purpose Purpose of the session for isolation (e.g., "project_only", "product_only")
     */
    public SSHJSessionManager(String host, String sshUser, String sshPassword, int port, String targetUser, String purpose) {
        this(host, sshUser, sshPassword, null, port, targetUser, sshPassword, purpose);
    }
    
    /**
     * Constructor with SSH key authentication.
     * 
     * @param host SSH host
     * @param sshUser SSH username
     * @param keyFilePath Path to SSH private key file
     * @param port SSH port
     * @param targetUser Target user for sudo (null if not needed)
     * @param sudoPassword Password for sudo (can be null if passwordless sudo)
     * @param purpose Purpose of the session for isolation
     */
    public SSHJSessionManager(String host, String sshUser, String keyFilePath, int port, String targetUser, String sudoPassword, String purpose) {
        this(host, sshUser, null, keyFilePath, port, targetUser, sudoPassword, purpose);
    }
    
    /**
     * Full constructor with all options.
     */
    private SSHJSessionManager(
            String host,
            String sshUser,
            String sshPassword,
            String keyFilePath,
            int port,
            String targetUser,
            String sudoPassword,
            String purpose
    ) {
        this.host = host;
        this.port = port;
        this.sshUser = sshUser;
        this.sshPassword = sshPassword;
        this.keyFilePath = keyFilePath;
        this.targetUser = targetUser;
        this.sudoPassword = sudoPassword;
        this.purpose = purpose != null ? purpose : "default";
        this.sessionId = "sshj_session_" + System.currentTimeMillis();
        
        // Register with ProcessMonitorManager (pass 'this' reference)
        ProcessMonitorManager.getInstance().registerSession(sessionId, this, false, this.purpose);
        
        LoggerUtil.getLogger().info("🎯 SSHJSessionManager created with purpose: " + this.purpose);
    }
    
    /**
     * Legacy constructor for backward compatibility (uses "default" purpose).
     * 
     * @deprecated Use constructor with purpose parameter for proper session isolation
     */
    @Deprecated
    public SSHJSessionManager(String host, String sshUser, String sshPassword, int port, String targetUser) {
        this(host, sshUser, sshPassword, port, targetUser, "default");
    }
    
    /**
     * Initialize SSH connection and session.
     * This method retrieves or creates a cached persistent session.
     * 
     * @throws Exception If initialization fails
     */
    public void initialize() throws Exception {
        if (persistentSession != null && persistentSession.isAlive()) {
            LoggerUtil.getLogger().fine("⚡ Session already initialized and alive");
            return;
        }
        
        LoggerUtil.getLogger().info("🔐 Initializing SSHJ session: " + host + ":" + port);
        
        try {
            persistentSession = UnifiedSSHManager.getOrCreateSession(
                host, port, sshUser, sshPassword, keyFilePath,
                targetUser, sudoPassword, purpose
            );
            
            LoggerUtil.getLogger().info("✅ SSHJ session initialized successfully");
            
        } catch (IOException e) {
            LoggerUtil.getLogger().severe("Failed to initialize SSHJ session: " + e.getMessage());
            LoggerUtil.error(e);
            throw new Exception("SSH session initialization failed: " + e.getMessage(), e);
        } catch (Exception e) {
            LoggerUtil.getLogger().severe("Unexpected error during SSHJ session initialization: " + e.getMessage());
            LoggerUtil.error(e);
            throw new Exception("SSH session initialization failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute command with default 2-minute timeout.
     * 
     * @param command Command to execute
     * @return Command result
     * @throws Exception If execution fails
     */
    public CommandResult execute(String command) throws Exception {
        return executeCommand(command, 120);
    }
    
    /**
     * Execute command with specified timeout.
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Command result
     * @throws Exception If execution fails
     */
    public CommandResult executeCommand(String command, int timeoutSeconds) throws Exception {
        return executeCommand(command, timeoutSeconds, null);
    }
    
    /**
     * Execute command with timeout and progress callback.
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @param progressCallback Progress callback (can be null)
     * @return Command result
     * @throws Exception If execution fails
     */
    public CommandResult executeCommand(String command, int timeoutSeconds, ProgressCallback progressCallback) throws Exception {
        
        // Ensure session is initialized
        if (persistentSession == null || !persistentSession.isAlive()) {
            initialize();
        }
        
        synchronized (commandLock) {
            // Check if another command is in progress
            if (commandInProgress) {
                LoggerUtil.getLogger().warning("Another command is already in progress. Waiting...");
                int waitCount = 0;
                while (commandInProgress && waitCount < 30) {
                    try {
                        commandLock.wait(1000);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for command completion", e);
                    }
                }
                
                if (commandInProgress) {
                    LoggerUtil.getLogger().severe("Command still in progress after 30 seconds. Cancelling.");
                    cancelCommand();
                    Thread.sleep(2000);
                }
            }
            
            commandInProgress = true;
            ProcessMonitorManager.getInstance().updateOperationState(sessionId, true);
            
            try {
                // Check for cancellation
                if (progressCallback != null && progressCallback.isCancelled()) {
                    throw new RuntimeException("Command execution cancelled by user");
                }
                
                // Execute command via persistent session
                SSHCommandResult result = persistentSession.runCommand(command, timeoutSeconds, progressCallback);
                
                // Convert to legacy CommandResult format
                return new CommandResult(result.getOutput(), result.getExitCode());
                
            } catch (IOException e) {
                LoggerUtil.getLogger().severe("Command execution failed: " + e.getMessage());
                
                // Try to recover by reinitializing session
                LoggerUtil.getLogger().info("Attempting to recover session...");
                try {
                    UnifiedSSHManager.closeSession(host, port, sshUser, targetUser, purpose);
                    persistentSession = null;
                    initialize();
                } catch (Exception recoveryError) {
                    LoggerUtil.getLogger().severe("Session recovery failed: " + recoveryError.getMessage());
                }
                
                throw new Exception("Command execution failed: " + e.getMessage(), e);
                
            } finally {
                commandInProgress = false;
                ProcessMonitorManager.getInstance().updateOperationState(sessionId, false);
                commandLock.notifyAll();
            }
        }
    }
    
    /**
     * Execute command without cancellation checks (for cleanup operations).
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Command result
     * @throws Exception If execution fails
     */
    public CommandResult executeCommandWithoutCancellation(String command, int timeoutSeconds) throws Exception {
        LoggerUtil.getLogger().info("Executing command without cancellation checks: " + command);
        
        if (persistentSession == null || !persistentSession.isAlive()) {
            initialize();
        }
        
        try {
            SSHCommandResult result = persistentSession.runCommandWithoutCancellation(command, timeoutSeconds);
            return new CommandResult(result.getOutput(), result.getExitCode());
        } catch (IOException e) {
            throw new Exception("Command execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute sudo command.
     * 
     * @param command Command to execute with sudo
     * @param timeoutSeconds Timeout in seconds
     * @return Command result
     * @throws Exception If execution fails
     */
    public CommandResult executeSudoCommand(String command, int timeoutSeconds) throws Exception {
        if (persistentSession == null || !persistentSession.isAlive()) {
            initialize();
        }
        
        String sudoCommand = "sudo " + command;
        LoggerUtil.getLogger().info("Executing sudo command: " + sudoCommand);
        
        return executeCommand(sudoCommand, timeoutSeconds);
    }
    
    /**
     * Resolve environment variable.
     * 
     * @param varName Variable name (with or without $)
     * @return Variable value or empty string
     * @throws Exception If resolution fails
     */
    public String resolveEnvVar(String varName) throws Exception {
        if (varName == null || varName.trim().isEmpty()) {
            return "";
        }
        
        String formatted = varName.startsWith("$") ? varName.substring(1) : varName;
        LoggerUtil.getLogger().info("Resolving environment variable: " + varName);
        
        CommandResult result = executeCommand("printenv " + formatted, 30);
        String output = result.getOutput().trim();
        
        if (!output.isEmpty() && result.getExitCode() == 0) {
            LoggerUtil.getLogger().info("Resolved " + varName + " to: '" + output + "'");
            return output;
        }
        
        LoggerUtil.getLogger().warning("Could not resolve " + varName + ", returning empty string");
        return "";
    }
    
    /**
     * Open SFTP client for file transfers.
     * Note: This creates a separate SSH connection for SFTP operations.
     * Make sure to close the SFTPClient and its parent SSHClient when done.
     * 
     * @return SFTP client
     * @throws Exception If SFTP setup fails
     */
    @SuppressWarnings("resource") // sshClient intentionally left open for SFTP operations
    public SFTPClient openSftp() throws Exception {
        if (persistentSession == null || !persistentSession.isAlive()) {
            throw new IllegalStateException("SSH session not connected");
        }
        
        LoggerUtil.getLogger().info("Opening SFTP channel...");
        
        SSHClient sshClient = null;
        try {
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier(new net.schmizz.sshj.transport.verification.PromiscuousVerifier());
            sshClient.connect(host, port);
            
            if (keyFilePath != null && !keyFilePath.trim().isEmpty()) {
                sshClient.authPublickey(sshUser, keyFilePath);
            } else if (sshPassword != null) {
                sshClient.authPassword(sshUser, sshPassword);
            }
            
            SFTPClient sftpClient = sshClient.newSFTPClient();
            LoggerUtil.getLogger().info("✓ SFTP channel opened");
            
            // Note: Caller is responsible for closing both sftpClient and sshClient.
            // To close properly: sftpClient.close(); then sshClient.disconnect();
            return sftpClient;
            
        } catch (IOException e) {
            // Clean up on error
            if (sshClient != null && sshClient.isConnected()) {
                try {
                    sshClient.disconnect();
                } catch (IOException closeError) {
                    LoggerUtil.getLogger().fine("Error closing SSH client after SFTP failure: " + closeError.getMessage());
                }
            }
            LoggerUtil.getLogger().severe("Failed to open SFTP channel: " + e.getMessage());
            throw new Exception("SFTP connection failed", e);
        }
    }
    
    /**
     * Cancel current command execution.
     */
    public void cancelCommand() {
        LoggerUtil.getLogger().info("🛑 Command cancellation requested");
        
        ProcessMonitorManager.getInstance().unregisterSession(getSessionId());
        LoggerUtil.getLogger().info("🗑️ Session removed from ProcessMonitor cache");
        
        // Close the session
        UnifiedSSHManager.closeSession(host, port, sshUser, targetUser, purpose);
        persistentSession = null;
        
        // Interrupt command if session is available
        if (persistentSession != null) {
            try {
                persistentSession.interruptCommand();
            } catch (IOException e) {
                LoggerUtil.getLogger().warning("Failed to interrupt command: " + e.getMessage());
            }
        }
        
        // Clean up tracked files
        try {
            cleanupCreatedRemoteFiles();
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to cleanup remote files: " + e.getMessage());
        }
        
        cleanupLocalTemporaryFiles();
    }
    
    /**
     * Check if command is currently running.
     * 
     * @return true if command is in progress
     */
    public boolean isCommandRunning() {
        return commandInProgress;
    }
    
    /**
     * Wait for command completion.
     * 
     * @param maxWaitSeconds Maximum seconds to wait
     * @throws InterruptedException If interrupted while waiting
     */
    public void waitForCommandCompletion(int maxWaitSeconds) throws InterruptedException {
        if (!commandInProgress) {
            return;
        }
        
        LoggerUtil.getLogger().info("Waiting for command completion (max " + maxWaitSeconds + " seconds)...");
        int waitCount = 0;
        
        while (commandInProgress && waitCount < maxWaitSeconds) {
            Thread.sleep(1000);
            waitCount++;
        }
        
        if (commandInProgress) {
            LoggerUtil.getLogger().warning("Command still running after " + maxWaitSeconds + " seconds");
        } else {
            LoggerUtil.getLogger().info("Command completed successfully");
        }
    }
    
    /**
     * Get session ID.
     * 
     * @return Session ID
     */
    public String getSessionId() {
        if (sessionId == null) {
            sessionId = host + ":" + port + "_" + System.currentTimeMillis();
        }
        return sessionId;
    }
    
    /**
     * Get the purpose of this session.
     * 
     * @return Session purpose
     */
    public String getPurpose() {
        return purpose;
    }
    
    /**
     * Track a remote file created by this session for cleanup.
     * 
     * @param remoteFilePath Remote file path
     */
    public void trackRemoteFile(String remoteFilePath) {
        if (remoteFilePath != null && !remoteFilePath.trim().isEmpty()) {
            createdRemoteFiles.add(remoteFilePath);
            LoggerUtil.getLogger().fine("Tracking remote file for cleanup: " + remoteFilePath);
        }
    }
    
    /**
     * Track a local file created by this session for cleanup.
     * 
     * @param localFilePath Local file path
     */
    public void trackLocalFile(String localFilePath) {
        if (localFilePath != null && !localFilePath.trim().isEmpty()) {
            createdLocalFiles.add(localFilePath);
            LoggerUtil.getLogger().fine("Tracking local file for cleanup: " + localFilePath);
        }
    }
    
    /**
     * Remove a file from remote tracking.
     * 
     * @param remoteFilePath Remote file path
     */
    public void untrackRemoteFile(String remoteFilePath) {
        createdRemoteFiles.remove(remoteFilePath);
        LoggerUtil.getLogger().fine("Stopped tracking remote file: " + remoteFilePath);
    }
    
    /**
     * Remove a file from local tracking.
     * 
     * @param localFilePath Local file path
     */
    public void untrackLocalFile(String localFilePath) {
        createdLocalFiles.remove(localFilePath);
        LoggerUtil.getLogger().fine("Stopped tracking local file: " + localFilePath);
    }
    
    /**
     * Close this session instance.
     * Note: This does NOT destroy the cached session - it only cleans up local resources.
     */
    public void close() {
        try {
            ProcessMonitorManager.getInstance().unregisterSession(sessionId);
            
            // Clean up tracked files
            try {
                cleanupCreatedRemoteFiles();
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to cleanup remote files on close: " + e.getMessage());
            }
            
            cleanupLocalTemporaryFiles();
            
            LoggerUtil.getLogger().info("📝 Session instance closed (cache preserved): " + sessionId);
            
        } finally {
            commandInProgress = false;
        }
    }
    
    /**
     * Forcefully close and destroy this session's connections.
     * This will invalidate and remove the cached session.
     */
    public void forceClose() {
        try {
            ProcessMonitorManager.getInstance().unregisterSession(sessionId);
            
            // Remove from cache and close
            UnifiedSSHManager.closeSession(host, port, sshUser, targetUser, purpose);
            persistentSession = null;
            
            // Clean up tracked files
            try {
                cleanupCreatedRemoteFiles();
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to cleanup remote files on force close: " + e.getMessage());
            }
            
            cleanupLocalTemporaryFiles();
            
            LoggerUtil.getLogger().info("🔒 Session forcefully closed and removed from cache: " + sessionId);
            
        } finally {
            commandInProgress = false;
        }
    }
    
    /**
     * Close all cached sessions.
     */
    public static void closeAllSessions() {
        UnifiedSSHManager.closeAllSessions();
    }
    
    /**
     * Get cache statistics.
     * 
     * @return Cache statistics string
     */
    public static String getCacheStatistics() {
        return UnifiedSSHManager.getCacheStatistics();
    }
    
    /**
     * Clean up expired sessions.
     */
    public static void cleanupExpiredSessions() {
        UnifiedSSHManager.cleanupExpiredSessions();
    }
    
    /**
     * Emergency cleanup for all sessions.
     */
    public static void emergencyCleanupAllSessions() {
        UnifiedSSHManager.cleanupExpiredSessions();
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    /**
     * Clean up remote files created by this session.
     */
    private void cleanupCreatedRemoteFiles() throws Exception {
        synchronized (createdRemoteFiles) {
            for (String remoteFile : createdRemoteFiles) {
                try {
                    executeCommandWithoutCancellation("rm -f " + remoteFile, 30);
                    LoggerUtil.getLogger().info("Cleaned up remote file: " + remoteFile);
                } catch (Exception e) {
                    LoggerUtil.getLogger().warning("Failed to delete remote file " + remoteFile + ": " + e.getMessage());
                }
            }
            createdRemoteFiles.clear();
        }
    }
    
    /**
     * Clean up local temporary files created during operations.
     */
    private void cleanupLocalTemporaryFiles() {
        try {
            synchronized (createdLocalFiles) {
                for (String filePath : createdLocalFiles) {
                    File file = new File(filePath);
                    if (file.exists()) {
                        if (file.delete()) {
                            LoggerUtil.getLogger().info("Cleaned up local temp file: " + file.getName());
                        } else {
                            LoggerUtil.getLogger().warning("Failed to delete local temp file: " + file.getName());
                        }
                    }
                }
                createdLocalFiles.clear();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to clean up local temporary files: " + e.getMessage());
        }
    }
    
    // ===== LEGACY COMPATIBILITY METHODS =====
    
    /**
     * @deprecated Legacy method for compatibility
     */
    @Deprecated
    public void safeKillCurrentSessionProcesses() {
        try {
            if (persistentSession != null && persistentSession.isAlive()) {
                LoggerUtil.getLogger().info("Safely killing current session processes");
                executeCommandWithoutCancellation("pkill -P $$", 30);
                cleanupCreatedRemoteFiles();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to kill session processes: " + e.getMessage());
        }
    }
    
    /**
     * @deprecated Legacy method for compatibility
     */
    @Deprecated
    public void forceKillZipProcesses() {
        try {
            if (persistentSession != null && persistentSession.isAlive()) {
                LoggerUtil.getLogger().info("Forcefully killing zip processes");
                persistentSession.interruptCommand();
                Thread.sleep(500);
                executeCommandWithoutCancellation("pkill -f zip", 30);
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to kill zip processes: " + e.getMessage());
        }
    }
    
    /**
     * @deprecated Legacy method for compatibility
     */
    @Deprecated
    public void forceKillCurrentCommand() {
        try {
            if (persistentSession != null && persistentSession.isAlive()) {
                LoggerUtil.getLogger().info("Forcefully killing current command");
                persistentSession.interruptCommand();
                Thread.sleep(500);
                executeCommandWithoutCancellation("pkill -P $$", 30);
                commandInProgress = false;
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to kill current command: " + e.getMessage());
        }
    }
    
    /**
     * @deprecated Legacy method for compatibility
     */
    @Deprecated
    public void emergencySessionCleanup() {
        try {
            if (persistentSession != null && persistentSession.isAlive()) {
                LoggerUtil.getLogger().info("EMERGENCY SESSION CLEANUP");
                persistentSession.interruptCommand();
                Thread.sleep(1000);
                executeCommandWithoutCancellation("kill -9 -$$", 30);
                commandInProgress = false;
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to perform emergency cleanup: " + e.getMessage());
        }
    }
    
    // ===== INNER CLASSES =====
    
    /**
     * Command result container for backward compatibility.
     */
    public static class CommandResult {
        private final String output;
        private final int exitCode;
        
        public CommandResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
        
        public String getOutput() {
            return output;
        }
        
        public int getExitCode() {
            return exitCode;
        }
        
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}

