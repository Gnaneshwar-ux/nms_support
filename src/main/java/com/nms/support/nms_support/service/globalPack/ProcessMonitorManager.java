package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages process monitor lifecycle and provides warnings for in-progress operations.
 * Supports both JSch (SSHSessionManager) and SSHJ (SSHJSessionManager) implementations.
 */
public class ProcessMonitorManager {
    
    private static final ProcessMonitorManager instance = new ProcessMonitorManager();
    private final Map<String, Object> activeSessions = new ConcurrentHashMap<>(); // Holds both SSHSessionManager and SSHJSessionManager
    private final Map<String, Boolean> operationStates = new ConcurrentHashMap<>();
    private final Map<String, String> sessionPurposes = new ConcurrentHashMap<>();
    
    private ProcessMonitorManager() {}
    
    public static ProcessMonitorManager getInstance() {
        return instance;
    }
    
    /**
     * Register an active session with its operation state and purpose
     */
    public void registerSession(String sessionId, Object session, boolean isInProgress, String purpose) {
        activeSessions.put(sessionId, session);
        operationStates.put(sessionId, isInProgress);
        sessionPurposes.put(sessionId, purpose != null ? purpose : "default");
        LoggerUtil.getLogger().info("Registered session: " + sessionId + " (in-progress: " + isInProgress + ", purpose: " + purpose + ")");
    }
    
    /**
     * Register an active session with its operation state (backward compatibility)
     * @deprecated Use registerSession(String, Object, boolean, String) with purpose
     */
    @Deprecated
    public void registerSession(String sessionId, Object session, boolean isInProgress) {
        registerSession(sessionId, session, isInProgress, "default");
    }
    
    /**
     * Update operation state for a session
     */
    public void updateOperationState(String sessionId, boolean isInProgress) {
        operationStates.put(sessionId, isInProgress);
        LoggerUtil.getLogger().info("Updated operation state for session: " + sessionId + " (in-progress: " + isInProgress + ")");
    }
    
    /**
     * Unregister a session
     */
    public void unregisterSession(String sessionId) {
        activeSessions.remove(sessionId);
        operationStates.remove(sessionId);
        String purpose = sessionPurposes.remove(sessionId);
        LoggerUtil.getLogger().info("Unregistered session: " + sessionId + " (purpose: " + purpose + ")");
    }
    
    /**
     * Immediately clear a specific session and perform cleanup
     * This allows new sessions to start immediately without interference
     */
    public void immediateSessionCleanup(String sessionId) {
        LoggerUtil.getLogger().info("IMMEDIATE CLEANUP: Clearing session " + sessionId + " for immediate new session support");
        
        Object session = activeSessions.get(sessionId);
        if (session != null) {
            try {
                // Cancel any running commands
                if (session instanceof SSHSessionManager) {
                    SSHSessionManager sshSession = (SSHSessionManager) session;
                    sshSession.cancelCommand();
                    sshSession.emergencySessionCleanup();
                } else if (session instanceof com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) {
                    com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager sshjSession = 
                        (com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) session;
                    sshjSession.cancelCommand();
                    sshjSession.emergencySessionCleanup();
                }
                
                LoggerUtil.getLogger().info("Successfully cleaned session: " + sessionId);
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to clean session " + sessionId + ": " + e.getMessage());
            }
        }
        
        // Remove from cache immediately
        activeSessions.remove(sessionId);
        operationStates.remove(sessionId);
        sessionPurposes.remove(sessionId);
        
        LoggerUtil.getLogger().info("Session " + sessionId + " removed from cache - new sessions can start immediately");
    }
    
    /**
     * Check if any operations are in progress
     */
    public boolean hasInProgressOperations() {
        boolean hasInProgress = operationStates.values().stream().anyMatch(Boolean::booleanValue);
        LoggerUtil.getLogger().info("Checking in-progress operations: " + hasInProgress + " (sessions: " + activeSessions.size() + ", states: " + operationStates + ")");
        return hasInProgress;
    }
    
    /**
     * Get count of in-progress operations
     */
    public int getInProgressOperationCount() {
        return (int) operationStates.values().stream().filter(Boolean::booleanValue).count();
    }
    
    /**
     * Check if any setup-related operations are in progress
     * Setup operations include: project_only, product_only, full_checkout, patch_upgrade, etc.
     * This excludes zip_cleanup and other non-setup operations.
     */
    public boolean hasInProgressSetupOperations() {
        for (Map.Entry<String, Boolean> entry : operationStates.entrySet()) {
            if (entry.getValue()) { // If operation is in progress
                String sessionId = entry.getKey();
                String purpose = sessionPurposes.get(sessionId);
                if (purpose != null && isSetupPurpose(purpose)) {
                    LoggerUtil.getLogger().info("Found in-progress setup operation: " + sessionId + " (purpose: " + purpose + ")");
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if a session purpose is setup-related
     */
    private boolean isSetupPurpose(String purpose) {
        if (purpose == null) {
            return false;
        }
        String lowerPurpose = purpose.toLowerCase();
        return lowerPurpose.contains("project_only") ||
               lowerPurpose.contains("product_only") ||
               lowerPurpose.contains("full_checkout") ||
               lowerPurpose.contains("patch_upgrade") ||
               lowerPurpose.contains("has_java_mode") ||
               lowerPurpose.contains("project_and_product");
    }
    
    
    /**
     * Force cleanup all active sessions
     */
    public void forceCleanupAllSessions() {
        LoggerUtil.getLogger().warning("FORCE CLEANUP: Terminating all active sessions");
        
        for (Map.Entry<String, Object> entry : activeSessions.entrySet()) {
            String sessionId = entry.getKey();
            Object session = entry.getValue();
            
            try {
                LoggerUtil.getLogger().info("Force cleaning session: " + sessionId);
                if (session instanceof SSHSessionManager) {
                    ((SSHSessionManager) session).emergencySessionCleanup();
                } else if (session instanceof com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) {
                    ((com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) session).emergencySessionCleanup();
                }
                LoggerUtil.getLogger().info("Successfully cleaned session: " + sessionId);
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to clean session " + sessionId + ": " + e.getMessage());
            }
        }
        
        // Clear all registrations
        activeSessions.clear();
        operationStates.clear();
        sessionPurposes.clear();
        
        LoggerUtil.getLogger().info("FORCE CLEANUP COMPLETED: All sessions terminated and cleaned");
    }
    
    /**
     * Graceful cleanup - try to cancel operations first, then force cleanup
     */
    public void gracefulCleanupAllSessions() {
        LoggerUtil.getLogger().info("GRACEFUL CLEANUP: Attempting to cancel all operations");
        
        for (Map.Entry<String, Object> entry : activeSessions.entrySet()) {
            String sessionId = entry.getKey();
            Object session = entry.getValue();
            
            try {
                LoggerUtil.getLogger().info("Gracefully cancelling session: " + sessionId);
                
                // First try graceful cancellation
                if (session instanceof SSHSessionManager) {
                    SSHSessionManager sshSession = (SSHSessionManager) session;
                    sshSession.cancelCommand();
                    Thread.sleep(2000); // Wait 2 seconds for graceful cancellation
                    
                    // If still running, force kill
                    if (sshSession.isCommandRunning()) {
                        LoggerUtil.getLogger().warning("Session " + sessionId + " still running, force killing");
                        sshSession.forceKillCurrentCommand();
                    }
                } else if (session instanceof com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) {
                    com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager sshjSession =
                        (com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) session;
                    sshjSession.cancelCommand();
                    Thread.sleep(2000); // Wait 2 seconds for graceful cancellation
                    
                    // If still running, force kill
                    if (sshjSession.isCommandRunning()) {
                        LoggerUtil.getLogger().warning("Session " + sessionId + " still running, force killing");
                        sshjSession.forceKillCurrentCommand();
                    }
                }
                
                LoggerUtil.getLogger().info("Successfully cleaned session: " + sessionId);
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Failed to gracefully clean session " + sessionId + ": " + e.getMessage());
                // Fallback to emergency cleanup
                try {
                    if (session instanceof SSHSessionManager) {
                        ((SSHSessionManager) session).emergencySessionCleanup();
                    } else if (session instanceof com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) {
                        ((com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager) session).emergencySessionCleanup();
                    }
                } catch (Exception ex) {
                    LoggerUtil.getLogger().severe("Emergency cleanup also failed for session " + sessionId + ": " + ex.getMessage());
                }
            }
        }
        
        // Clear all registrations
        activeSessions.clear();
        operationStates.clear();
        sessionPurposes.clear();
        
        LoggerUtil.getLogger().info("GRACEFUL CLEANUP COMPLETED: All sessions cancelled and cleaned");
    }
    
    /**
     * Get status information for display
     */
    public String getStatusInfo() {
        int totalSessions = activeSessions.size();
        int inProgressCount = getInProgressOperationCount();
        
        if (totalSessions == 0) {
            return "No active sessions - Ready to close";
        } else if (inProgressCount == 0) {
            return totalSessions + " session(s) ready - Safe to close";
        } else {
            return totalSessions + " session(s), " + inProgressCount + " operation(s) in progress - Cannot close";
        }
    }
}
