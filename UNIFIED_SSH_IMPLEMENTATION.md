# Unified SSH Implementation

This document describes the unified SSH connection logic implemented across the NMS Support application.

## Overview

The application now uses a consistent SSH connection approach that automatically handles both LDAP and basic authentication based on the project configuration. This eliminates the need for different SSH connection logic in different parts of the application.

## Key Components

### 1. UnifiedSSHService
The main service class that provides a unified interface for all SSH operations.

**Location**: `src/main/java/com/nms/support/nms_support/service/globalPack/UnifiedSSHService.java`

**Key Methods**:
- `createSSHSession(ProjectEntity)` - Creates SSH session with unified auth logic
- `executeCommand(ProjectEntity, String)` - Execute single command
- `executeSudoCommand(ProjectEntity, String, int)` - Execute sudo command
- `createPersistentSession(ProjectEntity)` - Create session for multiple operations
- `validateProjectAuth(ProjectEntity)` - Validate authentication configuration
- `getAuthDescription(ProjectEntity)` - Get human-readable auth description

### 2. Updated Classes
All existing classes have been updated to use the unified approach:

- **ServerProjectService** - Now uses `UnifiedSSHService.createSSHSession()`
- **SFTPDownloadAndUnzip** - Now uses unified authentication logic
- **SSHExecutor** - Updated with new methods while maintaining backward compatibility

## Authentication Logic

### LDAP Authentication (useLdap = true)
```
1. Connect to host using LDAP credentials (ldapUser, ldapPassword)
2. If targetUser is specified and not empty:
   - Execute: sudo su - targetUser
   - Handle sudo password prompt automatically
   - Verify user switch was successful
3. If targetUser is null/empty:
   - Stay connected as ldapUser (no sudo switching)
```

### Basic Authentication (useLdap = false)
```
1. Connect to host using host credentials (hostUser, hostPass)
2. No sudo switching (direct connection as hostUser)
3. If targetUser is specified (rare case):
   - Could potentially use sudo su - targetUser
   - But typically not used in basic auth mode
```

## Usage Examples

### Basic Command Execution
```java
// Simple command execution
CommandResult result = UnifiedSSHService.executeCommand(project, "whoami");

// With timeout
CommandResult result = UnifiedSSHService.executeCommand(project, "ls -la", 60);

// With progress callback
CommandResult result = UnifiedSSHService.executeCommand(project, "zip -r backup.zip /data", 300, progressCallback);
```

### Sudo Commands
```java
// Sudo command (automatically handles sudo based on auth method)
CommandResult result = UnifiedSSHService.executeSudoCommand(project, "systemctl restart nms", 30);
```

### Persistent Sessions
```java
// For multiple commands on same connection
SSHSessionManager ssh = UnifiedSSHService.createPersistentSession(project);
try {
    CommandResult result1 = ssh.executeCommand("pwd");
    CommandResult result2 = ssh.executeCommand("whoami");
    // ... more commands
} finally {
    ssh.close();
}
```

### Validation
```java
// Validate before attempting connection
if (UnifiedSSHService.validateProjectAuth(project)) {
    CommandResult result = UnifiedSSHService.executeCommand(project, "echo 'Hello'");
} else {
    // Handle invalid auth configuration
}
```

## Migration Guide

### From Direct SSHSessionManager Usage
**Before**:
```java
SSHSessionManager ssh = new SSHSessionManager(host, user, password, port, targetUser);
ssh.initialize();
CommandResult result = ssh.executeCommand("whoami");
ssh.close();
```

**After**:
```java
CommandResult result = UnifiedSSHService.executeCommand(project, "whoami");
```

### From SSHExecutor Usage
**Before**:
```java
boolean success = SSHExecutor.executeCommand(host, user, password, command);
```

**After**:
```java
boolean success = SSHExecutor.executeCommand(project, command);
```

## Project Configuration

The unified SSH service reads authentication details from the `ProjectEntity`:

### LDAP Configuration
```java
project.setUseLdap(true);
project.setLdapUser("ldap_username");
project.setLdapPassword("ldap_password");
project.setTargetUser("target_user"); // Optional, for sudo switching
```

### Basic Authentication Configuration
```java
project.setUseLdap(false);
project.setHostUser("host_username");
project.setHostPass("host_password");
```

## Benefits

1. **Consistency**: All SSH connections use the same logic
2. **Maintainability**: Single place to update SSH connection behavior
3. **LDAP Support**: Automatic handling of LDAP authentication with sudo switching
4. **Backward Compatibility**: Existing code continues to work
5. **Validation**: Built-in validation of authentication configuration
6. **Logging**: Consistent logging of authentication methods used

## Error Handling

The unified service provides comprehensive error handling:

- **Invalid Project**: Throws `IllegalArgumentException` for null or invalid project
- **Missing Credentials**: Validates required fields before attempting connection
- **Connection Failures**: Proper exception handling with descriptive messages
- **Sudo Failures**: Handles sudo password prompts and user switching failures

## Logging

The service provides detailed logging:

```
🔐 Using LDAP authentication for SSH connection
   LDAP User: john.doe
   Target User: nmsadmin
   Host: server.example.com:22

🔐 Using basic authentication for SSH connection
   Host User: admin
   Host: server.example.com:22
```

## Testing

Use the `SSHUsageExample` class to test different scenarios:

```java
// Test basic command execution
SSHUsageExample.exampleSimpleCommand(project);

// Test with validation
SSHUsageExample.exampleWithValidation(project);

// Test authentication behavior
SSHUsageExample.exampleAuthBehavior(project);
```

## Future Enhancements

1. **SSH Key Authentication**: Add support for SSH key-based authentication
2. **Connection Pooling**: Implement connection pooling for better performance
3. **Retry Logic**: Add automatic retry for failed connections
4. **Health Checks**: Add periodic health checks for persistent connections
