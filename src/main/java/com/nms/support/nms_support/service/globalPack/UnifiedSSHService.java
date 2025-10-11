package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult;

/**
 * Unified SSH Service that handles both LDAP and basic authentication consistently
 * across the entire application.
 * 
 * This service provides a single point of entry for all SSH operations and ensures
 * consistent authentication logic based on the project's LDAP configuration.
 * 
 * Now using SSHJ-based implementation for better reliability and performance.
 */
public class UnifiedSSHService {
    
    /**
     * Creates an SSH session manager with unified authentication logic and purpose-based isolation
     * 
     * @param project The project entity containing authentication details
     * @param purpose Purpose of the session for isolation (e.g., "project_only", "product_only", "datastore_dump")
     * @return Configured SSHJSessionManager instance
     */
    public static SSHJSessionManager createSSHSession(ProjectEntity project, String purpose) {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null");
        }
        
        String host = project.getHost();
        int port = project.getHostPort() > 0 ? project.getHostPort() : 22;
        boolean useLdap = project.isUseLdap();
        
        String sshUser;
        String sshPassword;
        String targetUser;
        
        if (useLdap) {
            // LDAP Authentication: Use LDAP credentials and switch to target user
            sshUser = project.getLdapUser();
            sshPassword = project.getLdapPassword();
            targetUser = project.getTargetUser(); // Will be used for sudo su - targetUser
            
            LoggerUtil.getLogger().info("Using LDAP authentication for SSH connection (purpose: " + purpose + ")");
            LoggerUtil.getLogger().info("   LDAP User: " + sshUser);
            LoggerUtil.getLogger().info("   Target User: " + (targetUser != null && !targetUser.trim().isEmpty() ? targetUser : "NONE (will run as LDAP user)"));
            LoggerUtil.getLogger().info("   Host: " + host + ":" + port);
        } else {
            // Basic Authentication: Use host credentials directly
            sshUser = project.getHostUser();
            sshPassword = project.getHostPass();
            targetUser = null; // No user switching needed
            
            LoggerUtil.getLogger().info("Using basic authentication for SSH connection (purpose: " + purpose + ")");
            LoggerUtil.getLogger().info("   Host User: " + sshUser);
            LoggerUtil.getLogger().info("   Host: " + host + ":" + port);
        }
        
        // Validate required fields
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host address is required");
        }
        if (sshUser == null || sshUser.trim().isEmpty()) {
            throw new IllegalArgumentException("SSH user is required");
        }
        if (sshPassword == null || sshPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("SSH password is required");
        }
        
        // For LDAP, validate target user is provided
        if (useLdap && (targetUser == null || targetUser.trim().isEmpty())) {
            LoggerUtil.getLogger().warning("LDAP authentication selected but no target user specified. " +
                "SSH will connect as LDAP user without sudo switching.");
        }
        
        LoggerUtil.getLogger().info("Creating SSHJSessionManager instance...");
        try {
            SSHJSessionManager manager = new SSHJSessionManager(host, sshUser, sshPassword, port, targetUser, purpose);
            LoggerUtil.getLogger().info("SSHJSessionManager instance created successfully");
            return manager;
        } catch (Exception e) {
            LoggerUtil.getLogger().severe("Failed to create SSHJSessionManager: " + e.getMessage());
            LoggerUtil.error(e);
            throw e;
        }
    }
    
    /**
     * Creates an SSH session manager with unified authentication logic (backward compatibility)
     * @deprecated Use createSSHSession(ProjectEntity, String) with purpose for proper session isolation
     * 
     * @param project The project entity containing authentication details
     * @return Configured SSHJSessionManager instance
     */
    @Deprecated
    public static SSHJSessionManager createSSHSession(ProjectEntity project) {
        return createSSHSession(project, "default");
    }
    
    /**
     * Executes a command using the unified SSH service
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeCommand(ProjectEntity project, String command) throws Exception {
        return executeCommand(project, command, 120); // 2 minutes default timeout
    }
    
    /**
     * Executes a command using the unified SSH service with timeout
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeCommand(ProjectEntity project, String command, int timeoutSeconds) throws Exception {
        SSHJSessionManager ssh = createSSHSession(project);
        try {
            ssh.initialize();
            return ssh.executeCommand(command, timeoutSeconds);
        } finally {
            ssh.close();
        }
    }
    
    /**
     * Executes a command using the unified SSH service with progress callback
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @param timeoutSeconds Timeout in seconds
     * @param progressCallback Progress callback for real-time updates
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeCommand(ProjectEntity project, String command, int timeoutSeconds, ProgressCallback progressCallback) throws Exception {
        SSHJSessionManager ssh = createSSHSession(project);
        try {
            ssh.initialize();
            return ssh.executeCommand(command, timeoutSeconds, progressCallback);
        } finally {
            ssh.close();
        }
    }
    
    /**
     * Executes a sudo command using the unified SSH service
     * This method automatically handles sudo based on the authentication method:
     * - LDAP: Uses sudo su - targetUser (if target user is specified)
     * - Basic: Uses sudo directly (if target user is specified)
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute with sudo
     * @param timeoutSeconds Timeout in seconds
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeSudoCommand(ProjectEntity project, String command, int timeoutSeconds) throws Exception {
        SSHJSessionManager ssh = createSSHSession(project);
        try {
            ssh.initialize();
            return ssh.executeSudoCommand(command, timeoutSeconds);
        } finally {
            ssh.close();
        }
    }
    
    /**
     * Creates a persistent SSH session for multiple operations with purpose-based isolation
     * Use this when you need to perform multiple commands on the same connection
     * 
     * @param project The project entity containing authentication details
     * @param purpose Purpose of the session for isolation (e.g., "project_only", "product_only")
     * @return Configured and initialized SSHJSessionManager instance
     * @throws Exception if connection fails
     */
    public static SSHJSessionManager createPersistentSession(ProjectEntity project, String purpose) throws Exception {
        SSHJSessionManager ssh = createSSHSession(project, purpose);
        ssh.initialize();
        return ssh;
    }
    
    /**
     * Creates a persistent SSH session for multiple operations (backward compatibility)
     * @deprecated Use createPersistentSession(ProjectEntity, String) with purpose
     * 
     * @param project The project entity containing authentication details
     * @return Configured and initialized SSHJSessionManager instance
     * @throws Exception if connection fails
     */
    @Deprecated
    public static SSHJSessionManager createPersistentSession(ProjectEntity project) throws Exception {
        return createPersistentSession(project, "default");
    }
    
    /**
     * Gets or creates a persistent SSH session for a project with purpose-based isolation
     * This method maintains session caching to avoid repeated logins
     * 
     * @param project The project entity containing authentication details
     * @param purpose Purpose of the session for isolation
     * @return Configured and initialized SSHJSessionManager instance
     * @throws Exception if connection fails
     */
    public static SSHJSessionManager getOrCreatePersistentSession(ProjectEntity project, String purpose) throws Exception {
        // The SSHJSessionManager already handles caching internally
        // This method provides a clean interface for getting persistent sessions
        return createPersistentSession(project, purpose);
    }
    
    /**
     * Gets or creates a persistent SSH session for a project (backward compatibility)
     * @deprecated Use getOrCreatePersistentSession(ProjectEntity, String) with purpose
     * 
     * @param project The project entity containing authentication details
     * @return Configured and initialized SSHJSessionManager instance
     * @throws Exception if connection fails
     */
    @Deprecated
    public static SSHJSessionManager getOrCreatePersistentSession(ProjectEntity project) throws Exception {
        return getOrCreatePersistentSession(project, "default");
    }
    
    /**
     * Executes a command using a persistent session
     * This method reuses existing sessions when possible to improve performance
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeCommandWithPersistentSession(ProjectEntity project, String command) throws Exception {
        return executeCommandWithPersistentSession(project, command, 120);
    }
    
    /**
     * Executes a command using a persistent session with timeout
     * This method reuses existing sessions when possible to improve performance
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeCommandWithPersistentSession(ProjectEntity project, String command, int timeoutSeconds) throws Exception {
        return executeCommandWithPersistentSession(project, command, timeoutSeconds, "default");
    }
    
    /**
     * Executes a command using a persistent session with timeout and purpose
     * This method reuses existing sessions when possible to improve performance
     * 
     * @param project The project entity containing authentication details
     * @param command The command to execute
     * @param timeoutSeconds Timeout in seconds
     * @param purpose Session purpose for cache isolation
     * @return Command result
     * @throws Exception if command execution fails
     */
    public static CommandResult executeCommandWithPersistentSession(ProjectEntity project, String command, int timeoutSeconds, String purpose) throws Exception {
        SSHJSessionManager ssh = null;
        try {
            ssh = createSSHSession(project, purpose);
            LoggerUtil.getLogger().info("Created SSH session manager, initializing...");
            ssh.initialize();
            LoggerUtil.getLogger().info("SSH session initialized, executing command...");
            return ssh.executeCommand(command, timeoutSeconds);
        } catch (Exception e) {
            LoggerUtil.getLogger().severe("Error in executeCommandWithPersistentSession: " + e.getMessage());
            LoggerUtil.error(e);
            throw e;
        } finally {
            // Don't close the session - let it be cached for reuse
            // The session will be automatically cleaned up when it expires
        }
    }
    
    /**
     * Validates project authentication configuration based on LDAP setting.
     * Checks LDAP fields (ldapUser, ldapPassword) if useLdap is true,
     * or basic auth fields (hostUser, hostPassword) if useLdap is false.
     * 
     * @param project The project entity to validate
     * @return true if authentication is properly configured, false otherwise
     */
    public static boolean validateProjectAuth(ProjectEntity project) {
        if (project == null) {
            LoggerUtil.getLogger().severe("Project is null - cannot validate authentication");
            return false;
        }
        
        String host = project.getHost();
        if (host == null || host.trim().isEmpty()) {
            LoggerUtil.getLogger().severe("Server host is empty - cannot validate authentication");
            return false;
        }
        
        boolean useLdap = project.isUseLdap();
        
        if (useLdap) {
            // Validate LDAP fields
            String ldapUser = project.getLdapUser();
            String ldapPassword = project.getLdapPassword();
            
            boolean isValid = ldapUser != null && !ldapUser.trim().isEmpty() && 
                             ldapPassword != null && !ldapPassword.trim().isEmpty();
            
            if (!isValid) {
                LoggerUtil.getLogger().severe("LDAP authentication validation failed - " +
                    "LDAP User: " + (ldapUser == null || ldapUser.trim().isEmpty() ? "MISSING" : "OK") + ", " +
                    "LDAP Password: " + (ldapPassword == null || ldapPassword.trim().isEmpty() ? "MISSING" : "OK"));
            }
            
            return isValid;
        } else {
            // Validate basic auth fields
            String hostUser = project.getHostUser();
            String hostPassword = project.getHostPass();
            
            boolean isValid = hostUser != null && !hostUser.trim().isEmpty() && 
                             hostPassword != null && !hostPassword.trim().isEmpty();
            
            if (!isValid) {
                LoggerUtil.getLogger().severe("Basic authentication validation failed - " +
                    "Host User: " + (hostUser == null || hostUser.trim().isEmpty() ? "MISSING" : "OK") + ", " +
                    "Host Password: " + (hostPassword == null || hostPassword.trim().isEmpty() ? "MISSING" : "OK"));
            }
            
            return isValid;
        }
    }
    
    /**
     * Gets a human-readable description of the authentication method being used
     * 
     * @param project The project entity
     * @return Description of the authentication method
     */
    public static String getAuthDescription(ProjectEntity project) {
        if (project == null) {
            return "No project";
        }
        
        boolean useLdap = project.isUseLdap();
        String host = project.getHost();
        int port = project.getHostPort() > 0 ? project.getHostPort() : 22;
        
        if (useLdap) {
            String ldapUser = project.getLdapUser();
            String targetUser = project.getTargetUser();
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                return String.format("LDAP (%s) -> sudo su - %s @ %s:%d", ldapUser, targetUser, host, port);
            } else {
                return String.format("LDAP (%s) @ %s:%d", ldapUser, host, port);
            }
        } else {
            String hostUser = project.getHostUser();
            return String.format("Basic Auth (%s) @ %s:%d", hostUser, host, port);
        }
    }
}
