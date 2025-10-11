package com.nms.support.nms_support.service.globalPack;

import com.jcraft.jsch.*;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Professional SSH Session Manager with TTY-enabled sessions for proper sudo/su execution.
 * Optimized for production stability with caching, dynamic timeout, and real-time output streaming.
 * 
 * Key Features:
 * - Clean command execution with reliable exit code detection
 * - Intelligent prompt detection based on user context
 * - Minimal output filtering - no hardcoded patterns
 * - Proper TTY handling for sudo/su commands
 * - Session caching with automatic expiration
 */
public class SSHSessionManager {
    private static final int DEFAULT_TIMEOUT = 30_000; // 30 seconds
    private static final int LONG_TIMEOUT = 600_000; // 10 minutes for long-running commands
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    private static final ConcurrentHashMap<String, CachedSession> sessionCache = new ConcurrentHashMap<>();
    private static final AtomicLong commandIdGenerator = new AtomicLong(System.currentTimeMillis());

    private final String host;
    private final int port;
    private final String sshUser;
    private final String sshPassword;
    private final String targetUser;
    private final String purpose; // Purpose of the session for isolation (e.g., "project_only", "product_only")

    private com.jcraft.jsch.Session jschSession;
    private ChannelShell shellChannel;
    private PipedInputStream inputPipe;
    private PipedOutputStream outputPipe;
    private PrintWriter commandWriter;
    private ByteArrayOutputStream shellOutput;
    private long lastActivityTime;
    
    // Prompt detection
    private String detectedPrompt = null;
    private String currentUserContext = null;
    private boolean shellInitialized = false;
    
    // Track files created by this session for precise cleanup
    private final java.util.Set<String> createdRemoteFiles = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final java.util.Set<String> createdLocalFiles = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    // Command execution state
    private volatile boolean commandInProgress = false;
    private volatile boolean commandCancelled = false;
    private String sessionId = "session_" + System.currentTimeMillis();
    private final Object commandLock = new Object();

    /**
     * Constructor with purpose-based session isolation
     * 
     * @param host SSH host
     * @param sshUser SSH username
     * @param sshPassword SSH password
     * @param port SSH port
     * @param targetUser Target user for sudo (null if not needed)
     * @param purpose Purpose of the session for isolation (e.g., "project_only", "product_only", "datastore_dump")
     */
    public SSHSessionManager(String host, String sshUser, String sshPassword, int port, String targetUser, String purpose) {
        this.host = host;
        this.port = port;
        this.sshUser = sshUser;
        this.sshPassword = sshPassword;
        this.targetUser = targetUser;
        this.purpose = purpose != null ? purpose : "default";
        this.lastActivityTime = System.currentTimeMillis();
        
        // Register with ProcessMonitorManager
        ProcessMonitorManager.getInstance().registerSession(sessionId, this, false, this.purpose);
        
        LoggerUtil.getLogger().info("🎯 SSHSessionManager created with purpose: " + this.purpose);
    }
    
    /**
     * Legacy constructor for backward compatibility (uses "default" purpose)
     * @deprecated Use constructor with purpose parameter for proper session isolation
     */
    @Deprecated
    public SSHSessionManager(String host, String sshUser, String sshPassword, int port, String targetUser) {
        this(host, sshUser, sshPassword, port, targetUser, "default");
    }

    /**
     * Initialize SSH connection and session with TTY support
     * Optimized to skip initialization if already initialized with a live session
     */
    public void initialize() throws Exception {
        // Quick check - if we're already initialized with a live session, skip
        if (jschSession != null && jschSession.isConnected() && 
            shellChannel != null && shellChannel.isConnected() && shellInitialized) {
            LoggerUtil.getLogger().fine("⚡ Already initialized with live session, skipping re-initialization");
            lastActivityTime = System.currentTimeMillis();
            return;
        }
        
        String cacheKey = generateCacheKey();
        CachedSession cachedSession = sessionCache.get(cacheKey);

        if (cachedSession != null && !isSessionExpired(cachedSession)) {
            LoggerUtil.getLogger().info("♻️ Reusing cached SSH session with TTY: " + cacheKey);
            LoggerUtil.getLogger().info("📊 Cache Stats: " + sessionCache.size() + " sessions cached, " + 
                (System.currentTimeMillis() - cachedSession.lastActivityTime) / 1000 + "s since last activity");
            
            this.jschSession = cachedSession.jschSession;
            this.shellChannel = cachedSession.shellChannel;
            this.inputPipe = cachedSession.inputPipe;
            this.outputPipe = cachedSession.outputPipe;
            this.commandWriter = cachedSession.commandWriter;
            this.shellOutput = cachedSession.shellOutput;
            this.detectedPrompt = cachedSession.detectedPrompt;
            this.currentUserContext = cachedSession.currentUserContext;
            this.shellInitialized = cachedSession.shellInitialized;
            this.lastActivityTime = System.currentTimeMillis();

            if (!isSessionAlive()) {
                LoggerUtil.getLogger().warning("⚠️ Cached session is dead, creating new session...");
                createNewSession();
                updateCache(cacheKey);
                LoggerUtil.getLogger().info("✅ New TTY-enabled session created and cached");
            } else {
                LoggerUtil.getLogger().info("✅ Cached session is alive and ready");
            }
        } else {
            if (cachedSession != null) {
                LoggerUtil.getLogger().info("⏰ SSH CACHE EXPIRED: Session expired for " + cacheKey);
            } else {
                LoggerUtil.getLogger().info("🆕 SSH CACHE MISS: No existing session for " + cacheKey);
            }
            
            LoggerUtil.getLogger().info("🔐 Creating new SSH session with TTY enabled: " + cacheKey);
            createNewSession();
            updateCache(cacheKey);
            LoggerUtil.getLogger().info("✅ New TTY-enabled session established and cached");
        }
    }

    /**
     * Execute command with clean output parsing
     */
    public CommandResult execute(String command) throws Exception {
        return executeCommand(command, 120); // 2 minutes default timeout
    }

    /**
     * Execute command with timeout
     */
    public CommandResult executeCommand(String command, int timeoutSeconds) throws Exception {
        return executeCommand(command, timeoutSeconds, null);
    }
    
    /**
     * Execute command with timeout and progress callback
     */
    public CommandResult executeCommand(String command, int timeoutSeconds, ProgressCallback progressCallback) throws Exception {
        if (shellChannel == null || !isSessionAlive()) {
            initialize();
        }
        lastActivityTime = System.currentTimeMillis();
        
        synchronized (commandLock) {
            if (commandInProgress) {
                LoggerUtil.getLogger().warning("Another command is already in progress. Waiting for completion...");
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
                    LoggerUtil.getLogger().severe("Command still in progress after 30 seconds. Forcing cancellation.");
                    cancelCommand();
                    Thread.sleep(2000);
                }
            }
            
            commandInProgress = true;
            ProcessMonitorManager.getInstance().updateOperationState(sessionId, true);
            try {
                return executeCommandInternal(command, timeoutSeconds, progressCallback);
            } finally {
                commandInProgress = false;
                ProcessMonitorManager.getInstance().updateOperationState(sessionId, false);
                commandLock.notifyAll();
            }
        }
    }
    
    /**
     * Execute command without cancellation checks (for cleanup operations)
     */
    public CommandResult executeCommandWithoutCancellation(String command, int timeoutSeconds) throws Exception {
        LoggerUtil.getLogger().info("Executing command without cancellation checks: " + command);
        if (shellChannel == null || !isSessionAlive()) {
            initialize();
        }
        lastActivityTime = System.currentTimeMillis();
        return executeCommandInternal(command, timeoutSeconds, null, false);
    }
    
    /**
     * Execute sudo command
     */
    public CommandResult executeSudoCommand(String command, int timeoutSeconds) throws Exception {
        if (shellChannel == null || !isSessionAlive()) {
            initialize();
        }
        lastActivityTime = System.currentTimeMillis();
        
        String sudoCommand = "sudo " + command;
        LoggerUtil.getLogger().info("Executing sudo command: " + sudoCommand);
        
        return executeCommand(sudoCommand, timeoutSeconds);
    }
    
    /**
     * Resolve environment variable
     */
    public String resolveEnvVar(String varName) throws Exception {
        if (varName == null || varName.trim().isEmpty()) return "";
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
     * Open SFTP client using JSch
     */
    public ChannelSftp openSftp() throws Exception {
        if (jschSession == null || !jschSession.isConnected()) {
            throw new IllegalStateException("SSH session not connected");
        }
        
        // Create SFTP channel
        ChannelSftp sftpChannel = (ChannelSftp) jschSession.openChannel("sftp");
        sftpChannel.connect(DEFAULT_TIMEOUT);
        LoggerUtil.getLogger().info("✓ SFTP channel opened");
        return sftpChannel;
    }
    
    /**
     * Cancel current command execution
     */
    public void cancelCommand() {
        commandCancelled = true;
        LoggerUtil.getLogger().info("🛑 Command cancellation requested - SAFE KILL");
        
        String cacheKey = generateCacheKey();
        
        ProcessMonitorManager.getInstance().unregisterSession(getSessionId());
        LoggerUtil.getLogger().info("🗑️ Session removed from ProcessMonitor cache");
        
        CachedSession removedSession = sessionCache.remove(cacheKey);
        if (removedSession != null) {
            LoggerUtil.getLogger().info("🗑️ SSH session removed from cache: " + cacheKey);
            closeSessionQuietly(removedSession);
        }
        
        // Send Ctrl+C to kill running command
        try {
            if (shellChannel != null && shellChannel.isConnected()) {
                LoggerUtil.getLogger().info("Sending Ctrl+C to kill running command");
                
                // Send multiple Ctrl+C signals using command writer
                for (int i = 0; i < 3; i++) {
                    outputPipe.write(3); // Ctrl+C
                    outputPipe.flush();
                    Thread.sleep(100);
                }
                
                // Try Ctrl+Z if Ctrl+C doesn't work
                Thread.sleep(500);
                outputPipe.write(26); // Ctrl+Z
                outputPipe.flush();
                
                // Clean up only files created by this session
                try {
                    cleanupCreatedRemoteFiles();
        } catch (Exception e) {
                    LoggerUtil.getLogger().warning("Failed to send cleanup commands: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to send cancellation signals: " + e.getMessage());
        }
        
        cleanupLocalTemporaryFiles();
    }
    
    /**
     * Check if command is currently running
     */
    public boolean isCommandRunning() {
        return commandInProgress;
    }
    
    /**
     * Wait for command completion
     */
    public void waitForCommandCompletion(int maxWaitSeconds) throws InterruptedException {
        if (!commandInProgress) return;
        
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
     * Get session ID
     */
    public String getSessionId() {
        if (sessionId == null) {
            sessionId = host + ":" + port + "_" + System.currentTimeMillis();
        }
        return sessionId;
    }
    
    /**
     * Close this session instance (lightweight cleanup)
     * NOTE: This does NOT destroy the cached session - it only cleans up local resources
     * The actual cached session remains alive for reuse by other SSHSessionManager instances
     */
    public void close() {
        try {
            ProcessMonitorManager.getInstance().unregisterSession(sessionId);
            
            // Clean up tracked files before closing
            try {
                cleanupCreatedRemoteFiles();
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to cleanup remote files on close: " + e.getMessage());
            }
            
            cleanupLocalTemporaryFiles();
            
            // ⚠️ CRITICAL: Do NOT disconnect shellChannel or jschSession here!
            // They are cached and may be used by other SSHSessionManager instances
            // Only nullify local references to indicate this instance is closed
            LoggerUtil.getLogger().info("📝 Session instance closed (cache preserved): " + sessionId);
        } finally {
            commandInProgress = false;
            commandCancelled = false;
        }
    }
    
    /**
     * Forcefully close and destroy this session's channels
     * This should only be called when truly terminating a session (e.g., app shutdown)
     * WARNING: This will invalidate and remove the cached session!
     */
    public void forceClose() {
        try {
            ProcessMonitorManager.getInstance().unregisterSession(sessionId);
            
            // Remove from cache FIRST before destroying channels
            String cacheKey = generateCacheKey();
            CachedSession removedSession = sessionCache.remove(cacheKey);
            if (removedSession != null) {
                LoggerUtil.getLogger().info("🗑️ Removed session from cache: " + cacheKey);
            }
            
            // Clean up tracked files
            try {
                cleanupCreatedRemoteFiles();
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to cleanup remote files on force close: " + e.getMessage());
            }
            
            cleanupLocalTemporaryFiles();
            
            // Disconnect channels
            if (shellChannel != null) {
                try { 
                    shellChannel.disconnect();
                    LoggerUtil.getLogger().info("🔌 Shell channel disconnected");
                } catch (Exception e) {
                    LoggerUtil.getLogger().warning("Failed to disconnect shell channel: " + e.getMessage());
                }
            }
            
            // Optionally close jschSession if no other sessions are using it
            if (jschSession != null) {
                try {
                    jschSession.disconnect();
                    LoggerUtil.getLogger().info("🔌 JSch session disconnected");
                } catch (Exception e) {
                    LoggerUtil.getLogger().warning("Failed to disconnect JSch session: " + e.getMessage());
                }
            }
            
            LoggerUtil.getLogger().info("🔒 Session forcefully closed and removed from cache: " + sessionId);
        } finally {
            commandInProgress = false;
            commandCancelled = false;
        }
    }
    
    /**
     * Close all cached sessions
     */
    public static void closeAllSessions() {
        LoggerUtil.getLogger().info("🗑️ Closing all cached SSH sessions...");
        int sessionCount = sessionCache.size();
        for (CachedSession cachedSession : sessionCache.values()) {
            closeSessionQuietly(cachedSession);
        }
        sessionCache.clear();
        LoggerUtil.getLogger().info("✅ Closed " + sessionCount + " cached SSH sessions");
    }
    
    /**
     * Get cache statistics
     */
    public static String getCacheStatistics() {
        int totalSessions = sessionCache.size();
        int activeSessions = 0;
        int expiredSessions = 0;
        
        for (CachedSession session : sessionCache.values()) {
            if (isSessionExpired(session)) {
                expiredSessions++;
            } else {
                activeSessions++;
            }
        }
        
        return String.format("SSH Cache: %d total, %d active, %d expired", 
            totalSessions, activeSessions, expiredSessions);
    }
    
    /**
     * Clean up expired sessions
     */
    public static void cleanupExpiredSessions() {
        LoggerUtil.getLogger().info("🧹 Cleaning up expired SSH sessions...");
        final int[] removedCount = {0};
        
        sessionCache.entrySet().removeIf(entry -> {
            if (isSessionExpired(entry.getValue())) {
                closeSessionQuietly(entry.getValue());
                removedCount[0]++;
                LoggerUtil.getLogger().info("🗑️ Removed expired session: " + entry.getKey());
                return true;
            }
            return false;
        });
        
        LoggerUtil.getLogger().info("✅ Cleaned up " + removedCount[0] + " expired sessions");
    }

    /**
     * Emergency cleanup for all sessions
     */
    public static void emergencyCleanupAllSessions() {
        int initialSize = sessionCache.size();
        sessionCache.entrySet().removeIf(entry -> {
            boolean expired = isSessionExpired(entry.getValue());
            if (expired) {
                LoggerUtil.getLogger().fine("Cleaning up expired session: " + entry.getKey());
                closeSessionQuietly(entry.getValue());
            }
            return expired;
        });
        LoggerUtil.getLogger().info("Cleaned up " + (initialSize - sessionCache.size()) + " expired sessions");
    }

    // ===== PRIVATE IMPLEMENTATION =====
    
    /**
     * Create new SSH session with TTY enabled using pipe-based streams
     */
    private void createNewSession() throws Exception {
        LoggerUtil.getLogger().info("Creating new SSH session to " + host + " as " + sshUser);

        JSch jsch = new JSch();
        jschSession = jsch.getSession(sshUser, host, port);
        jschSession.setPassword(sshPassword);
        jschSession.setConfig("StrictHostKeyChecking", "no");
        jschSession.connect(DEFAULT_TIMEOUT);
        
        LoggerUtil.getLogger().info("✓ SSH connection established");

        // Create pipe-based streams for input
        inputPipe = new PipedInputStream();
        outputPipe = new PipedOutputStream(inputPipe);
        
        // Create PrintWriter with auto-flush, but we'll use print() + \n for Unix line endings
        commandWriter = new PrintWriter(new OutputStreamWriter(outputPipe, "UTF-8"), true);
        
        // Create output buffer
        shellOutput = new ByteArrayOutputStream();
        
        // Open shell channel with PTY
        shellChannel = (ChannelShell) jschSession.openChannel("shell");
        shellChannel.setPty(true);
        shellChannel.setInputStream(inputPipe);
        shellChannel.setOutputStream(shellOutput);
        shellChannel.connect(DEFAULT_TIMEOUT);
        
        lastActivityTime = System.currentTimeMillis();
        
        LoggerUtil.getLogger().info("✓ TTY-enabled shell channel opened with pipe streams");
        
        // Wait for shell to be ready
        Thread.sleep(2000);
        
        // Initialize shell environment (suppress echo and history expansion)
        initializeShellEnvironment();

        // Detect and store the prompt
        detectPrompt();
        
        // Switch to target user if specified
        if (!stringIsBlank(targetUser)) {
            switchToTargetUser();
            detectPrompt(); // Re-detect prompt after user switch
            LoggerUtil.getLogger().info("✓ Successfully switched to " + targetUser);
        } else {
            LoggerUtil.getLogger().info("✓ Successfully connected (no target user switch)");
        }
        
        shellInitialized = true;
    }
    
    /**
     * Initialize shell environment to suppress echo and history expansion
     */
    private void initializeShellEnvironment() throws Exception {
        LoggerUtil.getLogger().info("Initializing shell environment...");
        
        // Clear any initial output
        shellOutput.reset();
        Thread.sleep(1000);
        
        // Suppress terminal echo to avoid command duplication - use Unix line ending
        sendCommand("stty -echo");
        Thread.sleep(800);
        
        // Disable history expansion to avoid issues with ! character
        sendCommand("set +o histexpand 2>/dev/null || true");
        Thread.sleep(800);
        
        // Clear the output buffer
        shellOutput.reset();
        
        LoggerUtil.getLogger().info("✓ Shell environment initialized (echo suppressed)");
    }
    
    /**
     * Send command with proper Unix line ending (LF only, not CRLF)
     */
    private void sendCommand(String command) throws IOException, InterruptedException {
        // Use print() + \n to ensure Unix line ending, not println() which may add \r\n

        commandWriter.print(command + "\n");
        commandWriter.flush();
    }
    private void debugPipeState(String phase) {
        System.out.println("=== PIPE DEBUG " + phase + " ===");
        System.out.println("Timestamp: " + System.currentTimeMillis());

        // Basic pipe existence checks
        System.out.println("inputPipe: " + (inputPipe != null ? "exists" : "NULL"));
        System.out.println("outputPipe: " + (outputPipe != null ? "exists" : "NULL"));
        System.out.println("commandWriter: " + (commandWriter != null ? "exists" : "NULL"));
        System.out.println("shellOutput: " + (shellOutput != null ? "exists, size=" + shellOutput.size() : "NULL"));

        if (inputPipe != null) {
            try {
                int available = inputPipe.available();
                System.out.println("inputPipe.available(): " + available + " bytes");
            } catch (IOException e) {
                System.out.println("inputPipe.available() FAILED: " + e.getMessage());
            }
        }

        if (outputPipe != null) {
            try {
                // Test if output pipe is writable
                outputPipe.write(0); // Test byte
                outputPipe.flush();
                System.out.println("outputPipe write test: SUCCESS (pipe accepted data)");
            } catch (IOException e) {
                System.out.println("outputPipe write test: FAILED - " + e.getMessage());
            }
        }

        // Channel state
        System.out.println("shellChannel: " + (shellChannel != null ?
                "connected=" + shellChannel.isConnected() + ", closed=" + shellChannel.isClosed() : "NULL"));
        System.out.println("jschSession: " + (jschSession != null ?
                "connected=" + jschSession.isConnected() : "NULL"));

        System.out.println("commandInProgress: " + commandInProgress);
        System.out.println("shellInitialized: " + shellInitialized);
        System.out.println("=====================");
    }
    
    /**
     * Detect the shell prompt to intelligently filter it from output
     */
    private void detectPrompt() throws Exception {
        // Clear output buffer
        shellOutput.reset();
        
        // Send a simple command to capture the prompt - use Unix line ending
        String testMarker = "PROMPT_TEST_" + System.currentTimeMillis();
        sendCommand("echo " + testMarker);
        
        Thread.sleep(1000);
        String output = shellOutput.toString();
        
        // Extract prompt pattern from output
        // Format: [user@hostname dir]$ or user@hostname:dir$ or user@hostname>
        Pattern promptPattern = Pattern.compile("^(.+?[@:]\\S+?)[>\\$#\\]]", Pattern.MULTILINE);
        Matcher matcher = promptPattern.matcher(output);
        
        if (matcher.find()) {
            detectedPrompt = matcher.group(1);
            LoggerUtil.getLogger().info("✓ Detected prompt pattern: " + detectedPrompt);
                } else {
            // Fallback: use user@hostname pattern
            String hostnamePrefix = extractHostnamePrefix(host);
            String user = (targetUser != null && !targetUser.trim().isEmpty()) ? targetUser : sshUser;
            detectedPrompt = user + "@" + (hostnamePrefix != null ? hostnamePrefix : host);
            LoggerUtil.getLogger().info("✓ Using fallback prompt pattern: " + detectedPrompt);
        }
        
        currentUserContext = (targetUser != null && !targetUser.trim().isEmpty()) ? targetUser : sshUser;
        
        // Clear output buffer
        shellOutput.reset();
    }
    
    /**
     * Switch to target user using sudo
     */
    private void switchToTargetUser() throws Exception {
        LoggerUtil.getLogger().info("Switching to target user: " + targetUser);
        
        // Clear output buffer before switching
        shellOutput.reset();
        
        // Temporarily enable echo for sudo password prompt
        sendCommand("stty echo");
        Thread.sleep(500);
        
        String switchCommand = "sudo su - " + targetUser;
        LoggerUtil.getLogger().info("Executing: " + switchCommand);
        
        sendCommand(switchCommand);
        
        // Handle potential password prompt
        Thread.sleep(2000);
        String response = shellOutput.toString();
        
        if (isPasswordPrompt(response)) {
            LoggerUtil.getLogger().info("Password prompt detected, providing password...");
            sendCommand(sshPassword);
            Thread.sleep(3000);
        }
        
        // Re-suppress echo after user switch
        Thread.sleep(1000);
        sendCommand("stty -echo");
        Thread.sleep(800);
        
        // Verify user switch
        verifyUserSwitch();
    }
    
    /**
     * Verify that user switch was successful
     */
    private void verifyUserSwitch() throws Exception {
        clearShellBuffer();
        Thread.sleep(1000);
        
        CommandResult whoamiResult = executeCommandInternal("whoami", 30, null, false);
        String whoamiOutput = whoamiResult.getOutput().trim();
        
        LoggerUtil.getLogger().info("Whoami output: '" + whoamiOutput + "'");
        
        if (!whoamiOutput.contains(targetUser)) {
                LoggerUtil.getLogger().severe("Failed to switch to target user: " + targetUser);
                throw new RuntimeException("Failed to switch to target user: " + targetUser +
                ". Actual user: " + whoamiOutput);
        }
        
        LoggerUtil.getLogger().info("✓ Successfully verified switch to " + targetUser);
    }
    
    /**
     * Execute command internally with TTY context
     */
    private CommandResult executeCommandInternal(String command, int timeoutSeconds, ProgressCallback progressCallback) throws Exception {
        return executeCommandInternal(command, timeoutSeconds, progressCallback, true);
    }
    
    /**
     * Core command execution with clean output and exit code
     */
    private CommandResult executeCommandInternal(String command, int timeoutSeconds, 
                                                ProgressCallback progressCallback, 
                                                boolean checkCancellation) throws Exception {
        command = cleanString(command);
        LoggerUtil.getLogger().info("=== SSH COMMAND EXECUTION ===");
        LoggerUtil.getLogger().info("Command: " + command);
        LoggerUtil.getLogger().info("Timeout: " + timeoutSeconds + " seconds");
        LoggerUtil.getLogger().info("User Context: " + currentUserContext);
        
        if (progressCallback != null) {
            progressCallback.onProgress(10, "Executing command: " + command);
        }
        
        // Ensure terminal is ready
        ensureTerminalReady();
        
        // Generate unique command ID for exit code tracking
        String commandId = "CMD_" + commandIdGenerator.incrementAndGet();
        String wrappedCommand = command + "; echo \"" + commandId + "_EXIT_CODE:$?\"";
        
        LoggerUtil.getLogger().info("Wrapped command: " + wrappedCommand);

        debugPipeState("1");
        Thread.sleep(3000);
        // Send command with Unix line ending
        sendCommand(wrappedCommand);
        
        // Small sleep to allow command to be processed
        Thread.sleep(200);
        
        if (progressCallback != null) {
            progressCallback.onProgress(20, "Command sent, waiting for response...");
        }

        // Read output until exit code marker appears
        StringBuilder output = new StringBuilder();
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        long lastProgressUpdate = 0; // Track last progress callback time
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check for cancellation
            if (checkCancellation && (commandCancelled || (progressCallback != null && progressCallback.isCancelled()))) {
                LoggerUtil.getLogger().info("Command execution cancelled");
                throw new RuntimeException("Command execution cancelled by user");
            }
            
            String chunk = readAvailableOutput(600);

            if (!chunk.isEmpty()) {
                output.append(chunk);

                // Throttle progress updates to every 2 seconds to prevent UI freezing
                long currentTime = System.currentTimeMillis();
                boolean shouldSendUpdate = (currentTime - lastProgressUpdate) >= 2000; // 2 seconds throttle
                
                if (progressCallback != null && !chunk.trim().isEmpty() && shouldSendUpdate) {
                    progressCallback.onProgress(30, chunk.trim());
                    lastProgressUpdate = currentTime;
                    LoggerUtil.getLogger().fine("Progress update sent (throttled): " + chunk.trim().substring(0, Math.min(50, chunk.trim().length())));
                }
                
                // Check for exit code marker
                if (output.toString().contains(commandId + "_EXIT_CODE:")) {
                    LoggerUtil.getLogger().info("Command completion marker found");
                    if (progressCallback != null) {
                        progressCallback.onProgress(90, "Command completed");
                            }
                            break;
                }
            }
            
            Thread.sleep(50);
        }
        
        if (System.currentTimeMillis() - startTime >= timeoutMs) {
            throw new RuntimeException("Command execution timeout after " + timeoutSeconds + " seconds: " + command);
        }

        String fullOutput = command.contains("zip")?getLastLines(output.toString(),30): output.toString();
        int exitCode = extractExitCode(fullOutput, commandId);
        String cleanOutput = cleanCommandOutput(fullOutput, command, commandId);
        
        LoggerUtil.getLogger().info("=== COMMAND EXECUTION RESULT ===");
        LoggerUtil.getLogger().info("Exit code: " + exitCode);
        LoggerUtil.getLogger().info("Output length: " + cleanOutput.length());
        LoggerUtil.getLogger().fine("Clean output: " + cleanOutput.substring(0, Math.min(200, cleanOutput.length())));
        LoggerUtil.getLogger().info("=== END COMMAND EXECUTION ===");
        
        return new CommandResult(cleanOutput, exitCode);
    }

    private String getLastLines(String output, int lines) {
        if (output == null || output.isEmpty()) return "";

        String[] split = output.split("\\r?\\n");
        if (split.length <= lines) return output.trim();

        return Arrays.stream(split)
                .skip(split.length - lines)
                .collect(Collectors.joining(System.lineSeparator()))
                .trim();
    }

    /**
     * Ensure terminal is in proper state to execute commands
     */
    private void ensureTerminalReady() throws Exception {
        if (!isSessionAlive()) {
            LoggerUtil.getLogger().warning("Session not alive, reinitializing...");
            initialize();
            return;
        }
        
        // Send a simple test to ensure shell is responsive
        try {
            // Clear buffer
            shellOutput.reset();
            Thread.sleep(100);
            
            String testMarker = "READY_" + System.currentTimeMillis();
            sendCommand("echo " + testMarker);
            Thread.sleep(500);
            
            String response = shellOutput.toString();
            if (!response.contains(testMarker)) {
                LoggerUtil.getLogger().warning("Terminal not responsive, reinitializing...");
                initialize();
            } else {
                LoggerUtil.getLogger().fine("Terminal is ready and responsive");
                // Clear the test output
                shellOutput.reset();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Terminal readiness test failed: " + e.getMessage());
            initialize();
        }
    }
    
    /**
     * Read available output from shell buffer (not used in pipe-based approach but kept for compatibility)
     */
    @Deprecated
    private String readAvailableOutput(int timeoutMs) throws IOException {
        // In pipe-based approach, we read directly from shellOutput ByteArrayOutputStream
        // This method is deprecated but kept for backward compatibility
        return shellOutput.toString();
    }
    
    /**
     * Extract exit code from command output
     */
    private int extractExitCode(String output, String commandId) {
        String exitCodePattern = commandId + "_EXIT_CODE:";
        int index = output.lastIndexOf(exitCodePattern);
        
        if (index == -1) {
            LoggerUtil.getLogger().warning("Exit code marker not found, defaulting to 0");
            return 0;
        }
        
        try {
            String afterExitCode = output.substring(index + exitCodePattern.length());
            String[] parts = afterExitCode.split("[^0-9]");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Error parsing exit code: " + e.getMessage());
        }
        
        return 0;
    }

    /**
     * Clean command output - minimal filtering, just remove markers and prompts
     */
    private String cleanCommandOutput(String output, String command, String commandId) {
        if (output == null || output.isEmpty()) {
            return "";
        }

        output = output.replace("\r", "");
        // Remove the command echo
        output = output.replaceFirst(Pattern.quote(command), "");
        
        // Remove the wrapped command with exit code
        String wrappedCmd = command + "; echo \"" + commandId + "_EXIT_CODE:$?\"";
        output = output.replaceFirst(Pattern.quote(wrappedCmd), "");
        
        // Remove exit code marker line
        output = output.replaceAll(commandId + "_EXIT_CODE:\\d+.*", "");
        
        // Remove detected prompt pattern if available
        if (detectedPrompt != null) {
            output = output.replaceAll(Pattern.quote(detectedPrompt) + "[>\\$#:\\]]+\\s*", "");
        }
        
        // Remove common prompt patterns
        output = output.replaceAll("\\[.*?@.*?\\].*?[\\$#>]\\s*", "");
        output = output.replaceAll(".*?@.*?[:\\$#>]\\s*", "");
        
        // Clean up multiple blank lines
        output = output.replaceAll("\n{3,}", "\n\n");
        
        return output.trim();
    }
    
    /**
     * Check if output contains password prompt
     */
    private boolean isPasswordPrompt(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        
        String lowerResponse = response.toLowerCase();
        return lowerResponse.contains("[sudo] password") ||
               lowerResponse.contains("password:") ||
               lowerResponse.contains("password for") ||
               (lowerResponse.contains("password") && lowerResponse.contains("for"));
    }
    
    /**
     * Clear shell output buffer
     */
    private void clearShellBuffer() throws IOException {
        shellOutput.reset();
    }
    
    /**
     * Check if session is alive
     * Optimized to avoid false negatives that cause unnecessary reconnections
     */
    private boolean isSessionAlive() {
        try {
            // Primary check - if session and channel are connected, consider it alive
            if (jschSession == null || !jschSession.isConnected()) {
                LoggerUtil.getLogger().fine("Session not alive: jschSession disconnected");
                return false;
            }
            
            if (shellChannel == null || !shellChannel.isConnected()) {
                LoggerUtil.getLogger().fine("Session not alive: shellChannel disconnected");
                return false;
            }
            
            // Secondary check - only test with command if we haven't verified recently
            long timeSinceLastActivity = System.currentTimeMillis() - lastActivityTime;
            if (timeSinceLastActivity < 5000) {
                // Session was active within last 5 seconds, skip test
                LoggerUtil.getLogger().fine("Session alive: recently active (" + timeSinceLastActivity + "ms ago)");
                return true;
            }
            
            // Test session responsiveness with a simple echo
            try {
                shellOutput.reset();
                sendCommand("echo ALIVE_TEST");
                Thread.sleep(500);
                
                String output = shellOutput.toString();
                boolean alive = output.contains("ALIVE_TEST");
                LoggerUtil.getLogger().fine("Session alive test result: " + alive);
                
                if (alive) {
                    // Clear the test output
                    shellOutput.reset();
                }
                
                return alive;
            } catch (Exception e) {
                LoggerUtil.getLogger().fine("Session alive test failed: " + e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().fine("Session alive check error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract hostname prefix (part before first dot)
     */
    private String extractHostnamePrefix(String fullHostname) {
        if (fullHostname == null || fullHostname.trim().isEmpty()) {
            return null;
        }
        
        int dotIndex = fullHostname.indexOf('.');
        if (dotIndex > 0) {
            return fullHostname.substring(0, dotIndex);
        }
        
        return fullHostname;
    }
    
    /**
     * Clean string from invisible characters
     */
    private String cleanString(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\u0000-\\u0008\\u000B-\\u000C\\u000E-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u2028-\\u202E\\u2060-\\u206F\\uFEFF]", "").trim();
    }
    
    /**
     * Check if string is blank
     */
    private static boolean stringIsBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Generate cache key including purpose for proper session isolation
     * Sessions with different purposes will have separate cache entries
     */
    private String generateCacheKey() {
        return host + ":" + port + ":" + sshUser + ":" + (stringIsBlank(targetUser) ? "-" : targetUser) + ":" + purpose;
    }
    
    /**
     * Get the purpose of this session
     */
    public String getPurpose() {
        return purpose;
    }
    
    /**
     * Update session cache with purpose tracking
     */
    private void updateCache(String cacheKey) {
        sessionCache.put(cacheKey, new CachedSession(jschSession, shellChannel, inputPipe, 
            outputPipe, commandWriter, shellOutput, detectedPrompt, currentUserContext, shellInitialized, purpose));
        LoggerUtil.getLogger().info("💾 Session cached with purpose '" + purpose + "': " + cacheKey);
    }
    
    /**
     * Check if session is expired
     */
    private static boolean isSessionExpired(CachedSession cachedSession) {
        return System.currentTimeMillis() - cachedSession.lastActivityTime > SESSION_TIMEOUT_MS;
    }
    
    /**
     * Close session quietly
     */
    private static void closeSessionQuietly(CachedSession cachedSession) {
        try {
            if (cachedSession.shellChannel != null) cachedSession.shellChannel.disconnect();
        } catch (Exception ignored) {}
        try {
            if (cachedSession.jschSession != null) cachedSession.jschSession.disconnect();
        } catch (Exception ignored) {}
    }
    
    /**
     * Clean up local temporary files created during operations
     * Only deletes files that this session explicitly created
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
    
    /**
     * Clean up remote files created by this session
     * Only deletes files that this session explicitly created
     */
    private void cleanupCreatedRemoteFiles() throws IOException {
        synchronized (createdRemoteFiles) {
            for (String remoteFile : createdRemoteFiles) {
                try {
                    sendCommand("rm -f " + remoteFile);
                    LoggerUtil.getLogger().info("Cleaned up remote file: " + remoteFile);
                } catch (Exception e) {
                    LoggerUtil.getLogger().warning("Failed to delete remote file " + remoteFile + ": " + e.getMessage());
                }
            }
            createdRemoteFiles.clear();
        }
    }
    
    /**
     * Track a remote file created by this session
     */
    public void trackRemoteFile(String remoteFilePath) {
        if (remoteFilePath != null && !remoteFilePath.trim().isEmpty()) {
            createdRemoteFiles.add(remoteFilePath);
            LoggerUtil.getLogger().fine("Tracking remote file for cleanup: " + remoteFilePath);
        }
    }
    
    /**
     * Track a local file created by this session
     */
    public void trackLocalFile(String localFilePath) {
        if (localFilePath != null && !localFilePath.trim().isEmpty()) {
            createdLocalFiles.add(localFilePath);
            LoggerUtil.getLogger().fine("Tracking local file for cleanup: " + localFilePath);
        }
    }
    
    /**
     * Remove a file from tracking (e.g., when successfully processed)
     */
    public void untrackRemoteFile(String remoteFilePath) {
        createdRemoteFiles.remove(remoteFilePath);
        LoggerUtil.getLogger().fine("Stopped tracking remote file: " + remoteFilePath);
    }
    
    /**
     * Remove a local file from tracking (e.g., when successfully processed)
     */
    public void untrackLocalFile(String localFilePath) {
        createdLocalFiles.remove(localFilePath);
        LoggerUtil.getLogger().fine("Stopped tracking local file: " + localFilePath);
    }
    
    // ===== LEGACY COMPATIBILITY METHODS =====
    
    /**
     * @deprecated Legacy method for compatibility
     */
    public void safeKillCurrentSessionProcesses() {
        try {
            if (shellChannel != null && shellChannel.isConnected()) {
                LoggerUtil.getLogger().info("Safely killing current session processes");
                sendCommand("pkill -P $$");
                // Clean up only files created by this session
                cleanupCreatedRemoteFiles();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to kill session processes: " + e.getMessage());
        }
    }
    
    /**
     * @deprecated Legacy method for compatibility
     */
    public void forceKillZipProcesses() {
        try {
            if (shellChannel != null && shellChannel.isConnected()) {
                LoggerUtil.getLogger().info("Forcefully killing zip processes");
                
                for (int i = 0; i < 5; i++) {
                    outputPipe.write(3); // Ctrl+C
                    outputPipe.flush();
                    Thread.sleep(200);
                }
                
                outputPipe.write(26); // Ctrl+Z
                outputPipe.flush();
                Thread.sleep(500);
                
                sendCommand("pkill -f zip");
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to kill zip processes: " + e.getMessage());
        }
    }
    
    /**
     * @deprecated Legacy method for compatibility
     */
    public void forceKillCurrentCommand() {
        try {
            if (shellChannel != null && shellChannel.isConnected()) {
                LoggerUtil.getLogger().info("Forcefully killing current command");
                
                for (int i = 0; i < 5; i++) {
                    outputPipe.write(3); // Ctrl+C
                    outputPipe.flush();
                    Thread.sleep(200);
                }
                
                outputPipe.write(26); // Ctrl+Z
                outputPipe.flush();
                Thread.sleep(500);
                
                sendCommand("pkill -P $$");
                
                commandInProgress = false;
                commandCancelled = false;
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to kill current command: " + e.getMessage());
        }
    }
    
    /**
     * @deprecated Legacy method for compatibility
     */
    public void emergencySessionCleanup() {
        try {
            if (shellChannel != null && shellChannel.isConnected()) {
                LoggerUtil.getLogger().info("EMERGENCY SESSION CLEANUP");
                
                for (int i = 0; i < 10; i++) {
                    outputPipe.write(3); // Ctrl+C
                    outputPipe.flush();
                    Thread.sleep(100);
                }
                
                outputPipe.write(26); // Ctrl+Z
                outputPipe.flush();
                Thread.sleep(500);
                
                sendCommand("kill -9 -$$");
                
                commandInProgress = false;
                commandCancelled = false;
                lastActivityTime = System.currentTimeMillis();
                
                LoggerUtil.getLogger().info("EMERGENCY CLEANUP COMPLETED");
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Failed to perform emergency cleanup: " + e.getMessage());
        }
    }
    
    // ===== INNER CLASSES =====
    
    /**
     * Command result container
     */
    public static class CommandResult {
        private final String output;
        private final int exitCode;

        public CommandResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }

        public String getOutput() { return output; }
        public int getExitCode() { return exitCode; }
        public boolean isSuccess() { return exitCode == 0; }
    }
    
    /**
     * Cached session container with pipe-based streams and purpose tracking
     */
    private static class CachedSession {
        final com.jcraft.jsch.Session jschSession;
        final ChannelShell shellChannel;
        final PipedInputStream inputPipe;
        final PipedOutputStream outputPipe;
        final PrintWriter commandWriter;
        final ByteArrayOutputStream shellOutput;
        final long lastActivityTime;
        final String detectedPrompt;
        final String currentUserContext;
        final boolean shellInitialized;
        final String purpose; // Purpose for session isolation
        
        CachedSession(com.jcraft.jsch.Session jschSession, ChannelShell shellChannel,
                     PipedInputStream inputPipe, PipedOutputStream outputPipe,
                     PrintWriter commandWriter, ByteArrayOutputStream shellOutput,
                     String detectedPrompt, String currentUserContext, boolean shellInitialized, String purpose) {
            this.jschSession = jschSession;
            this.shellChannel = shellChannel;
            this.inputPipe = inputPipe;
            this.outputPipe = outputPipe;
            this.commandWriter = commandWriter;
            this.shellOutput = shellOutput;
            this.lastActivityTime = System.currentTimeMillis();
            this.detectedPrompt = detectedPrompt;
            this.currentUserContext = currentUserContext;
            this.shellInitialized = shellInitialized;
            this.purpose = purpose;
        }
    }
}
