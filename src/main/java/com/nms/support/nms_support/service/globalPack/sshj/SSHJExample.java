package com.nms.support.nms_support.service.globalPack.sshj;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;

/**
 * Example usage of the SSHJ-based SSH session manager.
 * 
 * This class demonstrates various features and common usage patterns.
 * Uncomment and modify the main method to test with your SSH server.
 */
public class SSHJExample {
    
    /**
     * Basic example: connect, execute command, close.
     */
    public static void basicExample() throws Exception {
        LoggerUtil.getLogger().info("=== Basic Example ===");
        
        SSHJSessionManager manager = new SSHJSessionManager(
            "server.example.com",  // host
            "user",                // SSH username
            "password",            // SSH password
            22,                    // port
            null,                  // no sudo
            "basic_example"        // session purpose
        );
        
        try {
            manager.initialize();
            
            SSHJSessionManager.CommandResult result = 
                manager.executeCommand("echo 'Hello from SSHJ!'", 10);
            
            if (result.isSuccess()) {
                LoggerUtil.getLogger().info("Output: " + result.getOutput());
            } else {
                LoggerUtil.getLogger().warning("Command failed with exit code: " + result.getExitCode());
            }
            
        } finally {
            manager.close();
        }
    }
    
    /**
     * Sudo example: connect as one user, run commands as another.
     */
    public static void sudoExample() throws Exception {
        LoggerUtil.getLogger().info("=== Sudo Example ===");
        
        SSHJSessionManager manager = new SSHJSessionManager(
            "server.example.com",  // host
            "deploy",              // SSH user
            "deploy_pass",         // SSH password
            22,                    // port
            "appuser",             // sudo to this user
            "sudo_example"         // session purpose
        );
        
        try {
            manager.initialize(); // This performs sudo once
            
            // All commands now run as 'appuser'
            SSHJSessionManager.CommandResult result = 
                manager.executeCommand("whoami", 10);
            
            LoggerUtil.getLogger().info("Running as: " + result.getOutput().trim());
            // Should output: appuser
            
            // Can now access appuser files without permission issues
            result = manager.executeCommand("ls -la /home/appuser", 30);
            LoggerUtil.getLogger().info("Files:\n" + result.getOutput());
            
        } finally {
            manager.close();
        }
    }
    
    /**
     * Progress callback example: monitor long-running command.
     */
    public static void progressCallbackExample() throws Exception {
        LoggerUtil.getLogger().info("=== Progress Callback Example ===");
        
        SSHJSessionManager manager = new SSHJSessionManager(
            "server.example.com",
            "user",
            "password",
            22,
            null,
            "progress_example"
        );
        
        ProgressCallback callback = new ProgressCallback() {
            @Override
            public void onProgress(int percentage, String message) {
                LoggerUtil.getLogger().info(
                    String.format("[%d%%] %s", percentage, message)
                );
            }
            
            @Override
            public void onComplete(String message) {
                LoggerUtil.getLogger().info("Completed: " + message);
            }
            
            @Override
            public void onError(String error) {
                LoggerUtil.getLogger().severe("Error: " + error);
            }
            
            @Override
            public boolean isCancelled() {
                return false; // Check your cancellation flag here
            }
        };
        
        try {
            manager.initialize();
            
            // Long-running command with progress updates
            SSHJSessionManager.CommandResult result = manager.executeCommand(
                "for i in {1..10}; do echo \"Step $i\"; sleep 1; done",
                60,
                callback
            );
            
            if (result.isSuccess()) {
                LoggerUtil.getLogger().info("Final output:\n" + result.getOutput());
            }
            
        } finally {
            manager.close();
        }
    }
    
    /**
     * Session caching example: multiple managers reuse same connection.
     */
    public static void sessionCachingExample() throws Exception {
        LoggerUtil.getLogger().info("=== Session Caching Example ===");
        
        // First manager - creates new session
        SSHJSessionManager manager1 = new SSHJSessionManager(
            "server.example.com", "user", "password", 22, null, "cache_example"
        );
        
        try {
            manager1.initialize(); // Creates new SSH connection
            LoggerUtil.getLogger().info("Manager 1 initialized");
            
            manager1.executeCommand("echo 'Manager 1'", 10);
            
            // Second manager with same parameters - reuses session
            SSHJSessionManager manager2 = new SSHJSessionManager(
                "server.example.com", "user", "password", 22, null, "cache_example"
            );
            
            try {
                manager2.initialize(); // Reuses cached connection (check logs)
                LoggerUtil.getLogger().info("Manager 2 initialized");
                
                manager2.executeCommand("echo 'Manager 2'", 10);
                
                // Both managers share the same underlying SSH connection
                LoggerUtil.getLogger().info(
                    "Cache stats: " + SSHJSessionManager.getCacheStatistics()
                );
                
            } finally {
                manager2.close();
            }
            
        } finally {
            manager1.close();
        }
    }
    
    /**
     * File tracking example: automatic cleanup of temporary files.
     */
    public static void fileTrackingExample() throws Exception {
        LoggerUtil.getLogger().info("=== File Tracking Example ===");
        
        SSHJSessionManager manager = new SSHJSessionManager(
            "server.example.com", "user", "password", 22, null, "file_example"
        );
        
        try {
            manager.initialize();
            
            // Create temporary file
            String tempFile = "/tmp/sshj_example_" + System.currentTimeMillis() + ".txt";
            
            manager.executeCommand(
                "echo 'Temporary data' > " + tempFile,
                10
            );
            
            // Track it for cleanup
            manager.trackRemoteFile(tempFile);
            
            // Verify file exists
            SSHJSessionManager.CommandResult result = 
                manager.executeCommand("cat " + tempFile, 10);
            
            LoggerUtil.getLogger().info("File contents: " + result.getOutput());
            
            // File will be automatically deleted when manager.close() is called
            
        } finally {
            manager.close(); // Cleans up tracked files
            LoggerUtil.getLogger().info("Temporary files cleaned up");
        }
    }
    
    /**
     * Error handling example: handling failures gracefully.
     */
    public static void errorHandlingExample() throws Exception {
        LoggerUtil.getLogger().info("=== Error Handling Example ===");
        
        SSHJSessionManager manager = new SSHJSessionManager(
            "server.example.com", "user", "password", 22, null, "error_example"
        );
        
        try {
            manager.initialize();
            
            // Command that fails
            SSHJSessionManager.CommandResult result = 
                manager.executeCommand("nonexistent_command", 10);
            
            if (!result.isSuccess()) {
                LoggerUtil.getLogger().warning(
                    "Command failed with exit code: " + result.getExitCode()
                );
                LoggerUtil.getLogger().warning("Error: " + result.getOutput());
            }
            
            // Command that times out
            try {
                manager.executeCommand("sleep 60", 5);
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Command timed out: " + e.getMessage());
            }
            
            // Session is still usable after errors
            result = manager.executeCommand("echo 'Still working'", 10);
            LoggerUtil.getLogger().info("Recovery: " + result.getOutput());
            
        } finally {
            manager.close();
        }
    }
    
    /**
     * SSH key authentication example.
     */
    public static void sshKeyAuthExample() throws Exception {
        LoggerUtil.getLogger().info("=== SSH Key Authentication Example ===");
        
        SSHJSessionManager manager = new SSHJSessionManager(
            "server.example.com",           // host
            "user",                         // SSH username
            "/home/user/.ssh/id_rsa",       // path to private key
            22,                             // port
            "appuser",                      // sudo to this user
            "sudo_password",                // sudo password
            "key_auth_example"              // session purpose
        );
        
        try {
            manager.initialize();
            
            SSHJSessionManager.CommandResult result = 
                manager.executeCommand("whoami", 10);
            
            LoggerUtil.getLogger().info("Authenticated with SSH key, running as: " + 
                result.getOutput().trim());
            
        } finally {
            manager.close();
        }
    }
    
    /**
     * Multiple hosts example: manage sessions to different servers.
     */
    public static void multipleHostsExample() throws Exception {
        LoggerUtil.getLogger().info("=== Multiple Hosts Example ===");
        
        String[] hosts = {"server1.example.com", "server2.example.com", "server3.example.com"};
        
        for (String host : hosts) {
            SSHJSessionManager manager = new SSHJSessionManager(
                host, "user", "password", 22, null, "multi_host_example"
            );
            
            try {
                manager.initialize();
                
                SSHJSessionManager.CommandResult result = 
                    manager.executeCommand("hostname", 10);
                
                LoggerUtil.getLogger().info(
                    "Connected to " + host + ": " + result.getOutput().trim()
                );
                
            } finally {
                manager.close();
            }
        }
        
        // Show cache stats - all sessions should be cached
        LoggerUtil.getLogger().info("Cache: " + SSHJSessionManager.getCacheStatistics());
    }
    
    /**
     * Uncomment to run examples.
     * Make sure to update with your actual SSH server details.
     */
    /*
    public static void main(String[] args) {
        try {
            // Basic examples
            basicExample();
            sudoExample();
            
            // Advanced examples
            progressCallbackExample();
            sessionCachingExample();
            fileTrackingExample();
            errorHandlingExample();
            
            // Authentication
            sshKeyAuthExample();
            
            // Multiple hosts
            multipleHostsExample();
            
            // Cleanup
            SSHJSessionManager.cleanupExpiredSessions();
            SSHJSessionManager.closeAllSessions();
            
        } catch (Exception e) {
            LoggerUtil.error(e);
        }
    }
    */
}

