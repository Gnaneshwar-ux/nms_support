package com.nms.support.nms_support.service.globalPack.sshj;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Persistent pre-sudoed SSH shell session using SSHJ.
 * 
 * This class wraps an SSHJ session with an interactive shell channel that is
 * pre-elevated using sudo. Commands are executed in this elevated shell without
 * requiring sudo for each command.
 * 
 * Features:
 * - Pre-sudoed shell: sudo is performed once at session creation
 * - Command execution with unique markers for completion detection
 * - Concurrent stdout/stderr capture
 * - Per-command timeout with interrupt capability
 * - Heartbeat checks to verify shell responsiveness
 * - Clean output with marker and prompt removal
 * - Thread-safe command execution
 */
public class PersistentSudoSession {
    
    private final SSHClient sshClient;
    private final Session shellSession;
    private final OutputStream shellInput;
    private final InputStream shellOutput;
    private final InputStream shellError;
    private final String targetUser;
    private final String purpose;
    
    // Command execution state
    private final Object commandLock = new Object();
    private volatile boolean commandInProgress = false;
    private volatile boolean shellReady = false;
    
    // Command ID generator for unique markers
    private static final AtomicLong commandIdGenerator = new AtomicLong(System.currentTimeMillis());
    
    // Buffer size for reading output
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * Create a persistent sudo session.
     * 
     * @param sshClient Connected and authenticated SSH client
     * @param targetUser User to sudo to (null if no sudo needed)
     * @param sudoPassword Password for sudo (null if passwordless sudo)
     * @param purpose Session purpose for logging
     * @throws IOException If session creation or sudo fails
     */
    public PersistentSudoSession(
            SSHClient sshClient,
            String targetUser,
            String sudoPassword,
            String purpose
    ) throws IOException {
        
        this.sshClient = sshClient;
        this.targetUser = targetUser;
        this.purpose = purpose != null ? purpose : "default";
        
        LoggerUtil.getLogger().info("🎯 Creating PersistentSudoSession with purpose: " + this.purpose);
        
        // Start a shell session with PTY
        this.shellSession = sshClient.startSession();
        this.shellSession.allocateDefaultPTY(); // Required for sudo to work properly
        
        // Open an interactive shell
        Session.Shell shell = shellSession.startShell();
        
        this.shellInput = shell.getOutputStream();
        this.shellOutput = shell.getInputStream();
        this.shellError = shell.getErrorStream();
        
        LoggerUtil.getLogger().info("✓ Interactive shell started with PTY");
        
        // Wait for shell to be ready
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for shell initialization", e);
        }
        
        // Initialize shell environment
        initializeShellEnvironment();
        
        // Perform sudo if target user specified
        if (targetUser != null && !targetUser.trim().isEmpty()) {
            performSudo(sudoPassword);
            LoggerUtil.getLogger().info("✓ Successfully switched to " + targetUser);
        } else {
            LoggerUtil.getLogger().info("✓ Shell ready (no sudo)");
        }
        
        this.shellReady = true;
        LoggerUtil.getLogger().info("✅ PersistentSudoSession ready for command execution");
    }
    
    /**
     * Execute a command in the persistent shell.
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Command result with output and exit code
     * @throws IOException If command execution fails
     */
    public SSHCommandResult runCommand(String command, int timeoutSeconds) throws IOException {
        return runCommand(command, timeoutSeconds, null, true);
    }
    
    /**
     * Execute a command without cancellation checks (for cleanup operations).
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Command result with output and exit code
     * @throws IOException If command execution fails
     */
    public SSHCommandResult runCommandWithoutCancellation(String command, int timeoutSeconds) throws IOException {
        return runCommand(command, timeoutSeconds, null, false);
    }
    
    /**
     * Execute a command with progress callback.
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @param progressCallback Progress callback (can be null)
     * @return Command result with output and exit code
     * @throws IOException If command execution fails
     */
    public SSHCommandResult runCommand(String command, int timeoutSeconds, ProgressCallback progressCallback) throws IOException {
        return runCommand(command, timeoutSeconds, progressCallback, true);
    }
    
    /**
     * Execute a command with optional cancellation checks.
     * 
     * @param command Command to execute
     * @param timeoutSeconds Timeout in seconds
     * @param progressCallback Progress callback (can be null)
     * @param checkCancellation Whether to check for cancellation
     * @return Command result with output and exit code
     * @throws IOException If command execution fails
     */
    private SSHCommandResult runCommand(String command, int timeoutSeconds, ProgressCallback progressCallback, boolean checkCancellation) throws IOException {
        
        if (!shellReady || !isAlive()) {
            throw new IOException("Shell session is not ready or not alive");
        }
        
        synchronized (commandLock) {
            if (commandInProgress) {
                throw new IOException("Another command is already in progress");
            }
            
            commandInProgress = true;
            try {
                return executeCommandInternal(command, timeoutSeconds, progressCallback, checkCancellation);
            } finally {
                commandInProgress = false;
            }
        }
    }
    
    /**
     * Check if the shell is still alive and responsive.
     * Uses a heartbeat echo command to verify.
     * 
     * @return true if alive, false otherwise
     */
    public boolean isAlive() {
        try {
            if (!sshClient.isConnected() || !shellSession.isOpen()) {
                return false;
            }
            
            // Quick heartbeat check
            String marker = "ALIVE_" + System.currentTimeMillis();
            String heartbeatCmd = "echo " + marker + "\n";
            
            shellInput.write(heartbeatCmd.getBytes(StandardCharsets.UTF_8));
            shellInput.flush();
            
            // Wait briefly and check for response
            Thread.sleep(500);
            
            StringBuilder output = new StringBuilder();
            readAvailableOutput(shellOutput, output, 100);
            
            return output.toString().contains(marker);
            
        } catch (Exception e) {
            LoggerUtil.getLogger().fine("Heartbeat check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Interrupt the currently running command (send Ctrl+C).
     * 
     * @throws IOException If interrupt fails
     */
    public void interruptCommand() throws IOException {
        LoggerUtil.getLogger().info("🛑 Sending interrupt signal (Ctrl+C) to shell");
        
        try {
            // Send Ctrl+C multiple times to ensure it's received
            for (int i = 0; i < 3; i++) {
                shellInput.write(3); // ASCII 3 = Ctrl+C
                shellInput.flush();
                Thread.sleep(100);
            }
            
            // Ctrl+C should be sufficient for most cases
            // SSHJ doesn't expose signal() directly on Session.Shell
            
            LoggerUtil.getLogger().info("✓ Interrupt signals sent");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending interrupt signal", e);
        }
    }
    
    /**
     * Close the persistent session and underlying SSH connection.
     */
    public void close() {
        LoggerUtil.getLogger().info("🔒 Closing PersistentSudoSession: " + purpose);
        
        shellReady = false;
        
        try {
            if (shellInput != null) {
                // Try to exit shell gracefully
                shellInput.write("exit\n".getBytes(StandardCharsets.UTF_8));
                shellInput.flush();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().fine("Error during graceful shell exit: " + e.getMessage());
        }
        
        try {
            if (shellSession != null && shellSession.isOpen()) {
                shellSession.close();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Error closing shell session: " + e.getMessage());
        }
        
        LoggerUtil.getLogger().info("✓ PersistentSudoSession closed");
    }
    
    /**
     * Get the purpose of this session.
     */
    public String getPurpose() {
        return purpose;
    }
    
    // ===== PRIVATE IMPLEMENTATION =====
    
    /**
     * Initialize the shell environment.
     * Suppress echo and disable history expansion.
     */
    private void initializeShellEnvironment() throws IOException {
        LoggerUtil.getLogger().info("Initializing shell environment...");
        
        try {
            // Suppress terminal echo to avoid command duplication
            sendCommand("stty -echo");
            Thread.sleep(800);
            
            // Disable history expansion
            sendCommand("set +o histexpand 2>/dev/null || true");
            Thread.sleep(800);
            
            // Drain any initial output
            drainOutput();
            
            LoggerUtil.getLogger().info("✓ Shell environment initialized");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during shell initialization", e);
        }
    }
    
    /**
     * Perform sudo to switch to target user.
     * Simplified approach: always send password after sudo command.
     */
    private void performSudo(String sudoPassword) throws IOException {
        LoggerUtil.getLogger().info("Switching to target user: " + targetUser);
        
        try {
            // Clear any pending output
            drainOutput();
            
            // Temporarily enable echo for sudo password prompt
            sendCommand("stty echo");
            Thread.sleep(500);
            
            // Execute sudo command (same as JSch implementation)
            String sudoCmd = "sudo su - " + targetUser;
            LoggerUtil.getLogger().info("Executing: " + sudoCmd);
            
            shellInput.write((sudoCmd + "\n").getBytes(StandardCharsets.UTF_8));
            shellInput.flush();
            
            // Wait for potential password prompt
            Thread.sleep(2000);
            
            // Read response
            StringBuilder response = new StringBuilder();
            readAvailableOutput(shellOutput, response, 100);
            
            LoggerUtil.getLogger().info("Sudo response output: '" + response.toString() + "'");
            
            // Check if password prompt detected
            if (isPasswordPrompt(response.toString())) {
                LoggerUtil.getLogger().info("Password prompt detected, providing password...");
                if (sudoPassword != null && !sudoPassword.trim().isEmpty()) {
                    shellInput.write((sudoPassword + "\n").getBytes(StandardCharsets.UTF_8));
                    shellInput.flush();
                    LoggerUtil.getLogger().info("Password sent");
                    Thread.sleep(3000);
                } else {
                    throw new IOException("Sudo password required but not provided");
                }
            } else {
                LoggerUtil.getLogger().info("No clear password prompt, sending password anyway to be safe...");
                // Send password anyway - better safe than sorry
                if (sudoPassword != null && !sudoPassword.trim().isEmpty()) {
                    shellInput.write((sudoPassword + "\n").getBytes(StandardCharsets.UTF_8));
                    shellInput.flush();
                    LoggerUtil.getLogger().info("Password sent preemptively");
                    Thread.sleep(3000);
                }
            }
            
            // Re-suppress echo after user switch
            Thread.sleep(1000);
            sendCommand("stty -echo");
            Thread.sleep(800);
            
            // Verify user switch
            verifyUserSwitch();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during sudo operation", e);
        }
    }
    
    /**
     * Verify that sudo/user switch was successful.
     */
    private void verifyUserSwitch() throws IOException {
        LoggerUtil.getLogger().info("Verifying user switch...");
        
        try {
            drainOutput();
            Thread.sleep(1000);
            
            // Call executeCommandInternal directly since shellReady is not yet set during constructor
            SSHCommandResult result = executeCommandInternal("whoami", 30, null, false);
            String whoami = result.getOutput().trim();
            
            LoggerUtil.getLogger().info("Current user: " + whoami);
            
            if (!whoami.contains(targetUser)) {
                throw new IOException("Failed to switch to target user: " + targetUser + ". Current user: " + whoami);
            }
            
            LoggerUtil.getLogger().info("✓ User switch verified");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during user verification", e);
        }
    }
    
    /**
     * Core command execution logic with marker-based completion detection.
     */
    private SSHCommandResult executeCommandInternal(
            String command,
            int timeoutSeconds,
            ProgressCallback progressCallback,
            boolean checkCancellation
    ) throws IOException {
        
        command = cleanCommand(command);
        LoggerUtil.getLogger().info("=== SSHJ COMMAND EXECUTION ===");
        LoggerUtil.getLogger().info("Command: " + command);
        LoggerUtil.getLogger().info("Timeout: " + timeoutSeconds + " seconds");
        
        if (progressCallback != null) {
            progressCallback.onProgress(10, "Executing command: " + command);
        }
        
        // Generate unique command marker
        String commandId = "CMD_" + commandIdGenerator.incrementAndGet();
        String wrappedCommand = command + "; echo \"" + commandId + "_EXIT_CODE:$?\"";
        
        LoggerUtil.getLogger().fine("Wrapped command: " + wrappedCommand);
        
        // Drain any pending output
        drainOutput();
        
        // Send command
        shellInput.write((wrappedCommand + "\n").getBytes(StandardCharsets.UTF_8));
        shellInput.flush();
        
        // Small delay to let command start
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during command execution", e);
        }
        
        if (progressCallback != null) {
            progressCallback.onProgress(20, "Command sent, waiting for response...");
        }
        
        // Read output until marker appears or timeout
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        long lastProgressUpdate = 0;
        
        boolean markerFound = false;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check for cancellation
            if (checkCancellation && progressCallback != null && progressCallback.isCancelled()) {
                LoggerUtil.getLogger().info("Command execution cancelled by user");
                interruptCommand();
                throw new IOException("Command execution cancelled by user");
            }
            
            // Read stdout
            readAvailableOutput(shellOutput, stdout, 50);
            
            // Read stderr
            readAvailableOutput(shellError, stderr, 50);
            
            // Log output periodically for debugging
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed % 5000 < 100) { // Every 5 seconds
                LoggerUtil.getLogger().fine("Command running for " + (elapsed/1000) + "s, stdout length: " + stdout.length() + ", stderr length: " + stderr.length());
                // Show actual output at 10s, 30s, 60s intervals for stuck command debugging
                if (elapsed >= 10000 && (elapsed % 30000 < 100 || elapsed < 11000)) {
                    LoggerUtil.getLogger().warning("Command output so far: '" + stdout.toString() + "'");
                }
            }
            
            // Check for marker
            if (stdout.toString().contains(commandId + "_EXIT_CODE:")) {
                markerFound = true;
                LoggerUtil.getLogger().info("Command completion marker found");
                if (progressCallback != null) {
                    progressCallback.onProgress(90, "Command completed");
                }
                break;
            }
            
            // Throttled progress updates
            long currentTime = System.currentTimeMillis();
            if (progressCallback != null && (currentTime - lastProgressUpdate) >= 2000) {
                String latestOutput = getLastLines(stdout.toString(), 2);
                if (!latestOutput.trim().isEmpty()) {
                    progressCallback.onProgress(30, latestOutput.trim());
                    lastProgressUpdate = currentTime;
                }
            }
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during command execution", e);
            }
        }
        
        if (!markerFound) {
            LoggerUtil.getLogger().severe("Command timeout after " + timeoutSeconds + " seconds");
            LoggerUtil.getLogger().severe("Stdout captured (" + stdout.length() + " bytes): " + stdout.toString().substring(0, Math.min(500, stdout.length())));
            LoggerUtil.getLogger().severe("Stderr captured (" + stderr.length() + " bytes): " + stderr.toString());
            interruptCommand();
            throw new IOException("Command execution timeout after " + timeoutSeconds + " seconds: " + command);
        }
        
        // Extract exit code and clean output
        String fullOutput = stdout.toString();
        int exitCode = extractExitCode(fullOutput, commandId);
        String cleanOutput = cleanCommandOutput(fullOutput, command, commandId);
        String cleanStderr = stderr.toString().trim();
        
        LoggerUtil.getLogger().info("=== COMMAND EXECUTION RESULT ===");
        LoggerUtil.getLogger().info("Exit code: " + exitCode);
        LoggerUtil.getLogger().info("Stdout length: " + cleanOutput.length());
        LoggerUtil.getLogger().info("Stderr length: " + cleanStderr.length());
        LoggerUtil.getLogger().fine("Clean output: " + cleanOutput.substring(0, Math.min(200, cleanOutput.length())));
        LoggerUtil.getLogger().info("=== END COMMAND EXECUTION ===");
        
        return new SSHCommandResult(cleanOutput, cleanStderr, exitCode);
    }
    
    /**
     * Send a simple command to the shell.
     */
    private void sendCommand(String command) throws IOException {
        shellInput.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        shellInput.flush();
    }
    
    /**
     * Read available output from an input stream without blocking.
     */
    private void readAvailableOutput(InputStream inputStream, StringBuilder buffer, int waitMs) throws IOException {
        try {
            if (waitMs > 0) {
                Thread.sleep(waitMs);
            }
            
            int available = inputStream.available();
            if (available > 0) {
                byte[] data = new byte[Math.min(available, BUFFER_SIZE)];
                int read = inputStream.read(data);
                if (read > 0) {
                    buffer.append(new String(data, 0, read, StandardCharsets.UTF_8));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Drain any pending output from stdout and stderr.
     */
    private void drainOutput() throws IOException {
        try {
            Thread.sleep(200);
            
            // Drain stdout
            int available = shellOutput.available();
            if (available > 0) {
                byte[] drain = new byte[available];
                shellOutput.read(drain);
            }
            
            // Drain stderr
            available = shellError.available();
            if (available > 0) {
                byte[] drain = new byte[available];
                shellError.read(drain);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Check if output contains a password prompt.
     */
    private boolean isPasswordPrompt(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        
        String lower = output.toLowerCase();
        return lower.contains("[sudo] password") ||
               lower.contains("password:") ||
               lower.contains("password for");
    }
    
    /**
     * Extract exit code from command output.
     */
    private int extractExitCode(String output, String commandId) {
        String marker = commandId + "_EXIT_CODE:";
        int index = output.lastIndexOf(marker);
        
        if (index == -1) {
            LoggerUtil.getLogger().warning("Exit code marker not found, defaulting to 0");
            return 0;
        }
        
        try {
            String afterMarker = output.substring(index + marker.length());
            String[] parts = afterMarker.split("[^0-9]");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Error parsing exit code: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Clean command output by removing markers, prompts, and command echo.
     */
    private String cleanCommandOutput(String output, String command, String commandId) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        
        // Remove carriage returns
        output = output.replace("\r", "");
        
        // Remove command echo
        output = output.replaceFirst(Pattern.quote(command), "");
        
        // Remove wrapped command
        String wrappedCmd = command + "; echo \"" + commandId + "_EXIT_CODE:$?\"";
        output = output.replaceFirst(Pattern.quote(wrappedCmd), "");
        
        // Remove exit code marker
        output = output.replaceAll(commandId + "_EXIT_CODE:\\d+.*", "");
        
        // Remove common prompt patterns
        output = output.replaceAll("\\[.*?@.*?\\].*?[\\$#>]\\s*", "");
        output = output.replaceAll(".*?@.*?[:\\$#>]\\s*", "");
        
        // Clean up multiple blank lines
        output = output.replaceAll("\n{3,}", "\n\n");
        
        return output.trim();
    }
    
    /**
     * Clean command string from invisible characters.
     */
    private String cleanCommand(String command) {
        if (command == null) {
            return "";
        }
        return command.replaceAll("[\\u0000-\\u0008\\u000B-\\u000C\\u000E-\\u001F\\u007F-\\u009F]", "").trim();
    }
    
    /**
     * Get last N lines from output.
     */
    private String getLastLines(String output, int lines) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        
        String[] parts = output.split("\\r?\\n");
        if (parts.length <= lines) {
            return output.trim();
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, parts.length - lines); i < parts.length; i++) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(parts[i]);
        }
        
        return result.toString().trim();
    }
}

