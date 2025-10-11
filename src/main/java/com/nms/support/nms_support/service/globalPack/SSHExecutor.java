package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backwards-compatible wrapper using UnifiedSSHService under the hood.
 * This class maintains backward compatibility while using the new unified SSH approach.
 */
public class SSHExecutor {

    // Track active processes logically (no direct channels now)
    private static final Map<String, Boolean> activeProcesses = new ConcurrentHashMap<>();

    /**
     * @deprecated Use UnifiedSSHService.executeCommand(ProjectEntity, String) instead
     */
    @Deprecated
    public static boolean executeCommand(String host, String user, String password, String command) throws IOException {
        return executeCommand(host, user, password, command, 22);
    }

    /**
     * @deprecated Use UnifiedSSHService.executeCommand(ProjectEntity, String) instead
     */
    @Deprecated
    public static boolean executeCommand(String host, String user, String password, String command, int port) throws IOException {
        return executeCommand(host, user, password, command, port, null);
    }

    /**
     * @deprecated Use UnifiedSSHService.executeCommand(ProjectEntity, String) instead
     */
    @Deprecated
    public static boolean executeCommand(String host, String user, String password, String command, int port, String processKey) throws IOException {
        com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager manager = 
            new com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager(host, user, password, port, null);
        try {
            manager.initialize();
            if (processKey != null) activeProcesses.put(processKey, true);
            com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult result = manager.execute(command);
            return result.isSuccess();
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            if (processKey != null) activeProcesses.remove(processKey);
            manager.close();
        }
    }
    
    /**
     * Execute command using ProjectEntity with persistent session support
     * This method provides backward compatibility while using the new persistent session approach
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @param processKey The process key for tracking (optional)
     * @return true if execution was successful, false otherwise
     * @throws IOException if command execution fails
     */
    public static boolean executeCommand(ProjectEntity project, String command, String processKey) throws IOException {
        try {
            if (processKey != null) activeProcesses.put(processKey, true);
            
            // Use persistent session for better performance
            com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult result = 
                UnifiedSSHService.executeCommandWithPersistentSession(project, command, 120);
            
            return result.isSuccess();
        } catch (Exception e) {
            throw new IOException("Command execution failed: " + e.getMessage(), e);
        } finally {
            if (processKey != null) activeProcesses.remove(processKey);
        }
    }

    /**
     * Execute command using unified SSH service with project entity
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @return true if successful, false otherwise
     * @throws IOException if command execution fails
     */
    public static boolean executeCommand(ProjectEntity project, String command) throws IOException {
        return executeCommand(project, command, null);
    }


    public static boolean killProcess(String processKey) {
        // Not supported with SSHJ shell reuse; we only stop tracking
        return activeProcesses.remove(processKey) != null;
    }

    public static boolean isProcessRunning(String processKey) {
        return activeProcesses.containsKey(processKey);
    }

    public static String[] getActiveProcessKeys() {
        return activeProcesses.keySet().toArray(new String[0]);
    }

    public static void killAllProcesses() {
        activeProcesses.clear();
    }
}

