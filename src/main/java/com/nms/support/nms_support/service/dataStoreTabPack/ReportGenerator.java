package com.nms.support.nms_support.service.dataStoreTabPack;

import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.UnifiedSSHService;
import com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager.CommandResult;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * ReportGenerator for datastore reports using persistent SSH sessions
 * This class has been optimized to use persistent SSH sessions to avoid
 * repeated login delays and improve performance.
 * 
 * @author Gnaneshwar
 */
public class ReportGenerator {
    private static final Logger logger = LoggerUtil.getLogger();
    
    /**
     * Executes the datastore report generation command using persistent SSH session
     * 
     * @param project The project entity containing authentication details
     * @param dataStorePath The path where the report should be saved
     * @return true if execution was successful, false otherwise
     * @throws IOException if command execution fails
     */
    public static boolean execute(ProjectEntity project, String dataStorePath) throws IOException {
        return execute(project, dataStorePath, null);
    }
    
    /**
     * Executes the datastore report generation command using persistent SSH session
     * 
     * @param project The project entity containing authentication details
     * @param dataStorePath The path where the report should be saved
     * @param processKey The process key for tracking (optional)
     * @return true if execution was successful, false otherwise
     * @throws IOException if command execution fails
     */
    public static boolean execute(ProjectEntity project, String dataStorePath, String processKey) throws IOException {
        logger.info("Starting datastore report generation for project: " + project.getName());
        logger.info("DataStore User: " + project.getDataStoreUser());
        logger.info("Report Path: " + dataStorePath);
        
        try {
            // Build the command using project-specific data
            String ex_command = "Action any.publisher* ejb client " + 
                project.getDataStoreUser() + " jbot_report " + 
                dataStorePath.replace("\\", "/");
            
            logger.info("Executing command: " + ex_command);
            
            // Wrap in bash login shell for proper environment
            String command = ex_command;
            
            // Use persistent SSH session for better performance with dedicated purpose
            CommandResult result = UnifiedSSHService.executeCommandWithPersistentSession(
                project, command, 300, "datastore_report"  // Dedicated purpose for cache isolation
            ); // 5 minutes timeout
            
            boolean success = result.isSuccess();
            logger.info("Report generation " + (success ? "completed successfully" : "failed"));
            logger.info("Exit code: " + result.getExitCode());
            
            if (!success) {
                String ldapUser = project.getLdapUser();
                String targetUser = project.getTargetUser();
                String actualUser = (targetUser != null && !targetUser.trim().isEmpty()) ? targetUser : ldapUser;
                
                logger.warning("Command execution failed for user: " + actualUser + 
                    " (LDAP: " + ldapUser + 
                    (targetUser != null && !targetUser.trim().isEmpty() ? ", Target: " + targetUser : " (no sudo)") + ")");
                logger.warning("Command output: " + result.getOutput());
                
                // Throw detailed exception for controller to handle
                String errorMsg = "Command failed as user '" + actualUser + "' (Exit code: " + result.getExitCode() + ")\n" +
                    "Output: " + result.getOutput() + "\n\n" +
                    "Possible causes:\n" +
                    "1. User '" + actualUser + "' doesn't have access to 'Action' command\n" +
                    "2. Missing sudo elevation (Target User not configured)\n" +
                    "3. Command not available in user's PATH";
                throw new IOException(errorMsg);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.severe("Failed to execute datastore report generation: " + e.getMessage());
            throw new IOException("Datastore report generation failed: " + e.getMessage(), e);
        }
    }
}
