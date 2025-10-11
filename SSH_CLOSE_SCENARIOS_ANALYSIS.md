# SSH Connection Close Scenarios - Complete Analysis

## 📋 Overview

Your application has **TWO types** of SSH session management patterns:

1. **🔄 Persistent Sessions** (Cached) - Should NOT be closed immediately
2. **⚡ One-Time Sessions** (Non-cached) - Should be closed immediately

---

## 🔍 All Scenarios Where SSH Connections Are Closed

### ✅ CORRECT - Should Be Closed (One-Time Operations)

#### 1️⃣ **UnifiedSSHService - Single Command Execution**

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/UnifiedSSHService.java`

**Lines 96-103** - Execute command with timeout:
```java
public static CommandResult executeCommand(ProjectEntity project, String command, int timeoutSeconds) throws Exception {
    SSHSessionManager ssh = createSSHSession(project);
    try {
        ssh.initialize();
        return ssh.executeCommand(command, timeoutSeconds);
    } finally {
        ssh.close();  // ✅ CORRECT - one-time command, close after use
    }
}
```

**Lines 116-123** - Execute with progress callback:
```java
public static CommandResult executeCommand(ProjectEntity project, String command, int timeoutSeconds, ProgressCallback progressCallback) throws Exception {
    SSHSessionManager ssh = createSSHSession(project);
    try {
        ssh.initialize();
        return ssh.executeCommand(command, timeoutSeconds, progressCallback);
    } finally {
        ssh.close();  // ✅ CORRECT - one-time command, close after use
    }
}
```

**Lines 138-145** - Execute sudo command:
```java
public static CommandResult executeSudoCommand(ProjectEntity project, String command, int timeoutSeconds) throws Exception {
    SSHSessionManager ssh = createSSHSession(project);
    try {
        ssh.initialize();
        return ssh.executeSudoCommand(command, timeoutSeconds);
    } finally {
        ssh.close();  // ✅ CORRECT - one-time command, close after use
    }
}
```

**Why Correct?**
- These are **utility methods** for one-time commands
- Each call creates a new instance
- No expectation of reuse
- Calling code doesn't hold reference to session

---

#### 2️⃣ **SSHExecutor (Deprecated) - Legacy One-Time Commands**

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHExecutor.java`

**Lines 37-49**:
```java
public static boolean executeCommand(String host, String user, String password, String command, int port, String processKey) throws IOException {
    SSHSessionManager manager = new SSHSessionManager(host, user, password, port, null);
    try {
        manager.initialize();
        if (processKey != null) activeProcesses.put(processKey, true);
        SSHSessionManager.CommandResult result = manager.execute(command);
        return result.isSuccess();
    } catch (Exception e) {
        throw new IOException(e);
    } finally {
        if (processKey != null) activeProcesses.remove(processKey);
        manager.close();  // ✅ CORRECT - deprecated method, one-time use
    }
}
```

**Why Correct?**
- Deprecated method for backward compatibility
- One-time command execution
- No session reuse expected

---

### ❌ PROBLEMATIC - Should NOT Be Closed (Multi-Operation Sessions)

#### 3️⃣ **DB Import from SSH - MAIN ISSUE**

**File**: `src/main/java/com/nms/support/nms_support/controller/ProjectDetailsController.java`

**Line 1090**:
```java
private void importDbDataFromSSHAsync(ProjectEntity project) {
    javafx.concurrent.Task<Void> sshImportTask = new javafx.concurrent.Task<Void>() {
        @Override
        protected Void call() throws Exception {
            // Create session
            SSHSessionManager ssh = UnifiedSSHService.createSSHSession(project);
            ssh.initialize();
            
            // Execute multiple queries
            String rdbmsHost = getEnvironmentVariable(ssh, "RDBMS_HOST");
            String oracleUser = getEnvironmentVariable(ssh, "ORACLE_READ_ONLY_USER");
            String oracleSid = getEnvironmentVariable(ssh, "ORACLE_SERVICE_NAME");
            String tnsContent = getTnsNamesContent(ssh);
            
            // Update UI...
            
            ssh.close();  // ❌ WRONG - Breaks cache for repeated operations!
            return null;
        }
    };
}
```

**Why Wrong?**
- User may import DB data **multiple times**
- Each import creates 3-5 SSH commands
- Session should be cached for reuse
- Cache exists but this breaks it!

**Impact**:
- First import: 15 seconds
- Second import: 15 seconds (should be 2s!)
- Third import: 15 seconds (should be 2s!)

**Fix**: Remove `ssh.close()` → Let it stay in cache

---

### 🚨 SESSION TERMINATION - Manual Cancellation

#### 4️⃣ **Command Cancellation - Removes from Cache**

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHSessionManager.java`

**Lines 118-139** - cancelCommand():
```java
public void cancelCommand() {
    commandCancelled = true;
    LoggerUtil.getLogger().info("🛑 Command cancellation requested - SAFE KILL");
    
    // Get cache key for this session
    String cacheKey = generateCacheKey();
    
    // Immediately remove this session from cache to allow new sessions
    ProcessMonitorManager.getInstance().unregisterSession(getSessionId());
    
    // Remove from SSH session cache as well
    CachedSession removedSession = sessionCache.remove(cacheKey);  // ✅ Remove from cache
    if (removedSession != null) {
        closeSessionQuietly(removedSession);  // ✅ Close the cached session
    }
    
    // Send Ctrl+C to kill the running command
    // ... termination logic ...
}
```

**Why Correct?**
- User explicitly cancelled operation
- Session might be in bad state
- Better to create fresh session next time
- Cache removal is intentional

---

#### 5️⃣ **Close All Cached Sessions - Application Shutdown**

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHSessionManager.java`

**Lines 537-544** - closeAllSessions():
```java
public static void closeAllSessions() {
    LoggerUtil.getLogger().info("🗑️ Closing all cached SSH sessions...");
    int sessionCount = sessionCache.size();
    for (CachedSession cachedSession : sessionCache.values()) {
        closeSessionQuietly(cachedSession);  // ✅ Close each cached session
    }
    sessionCache.clear();  // ✅ Clear the cache
    LoggerUtil.getLogger().info("✅ Closed " + sessionCount + " cached SSH sessions");
}
```

**When Called?**
- Application shutdown
- Cleanup operations
- Memory management

**Why Correct?**
- Cleanup for app termination
- Releases server resources
- Prevents dangling connections

---

#### 6️⃣ **Cleanup Expired Sessions**

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHSessionManager.java`

**Lines 570-585** - cleanupExpiredSessions():
```java
public static void cleanupExpiredSessions() {
    LoggerUtil.getLogger().info("🧹 Cleaning up expired SSH sessions...");
    final int[] removedCount = {0};
    
    sessionCache.entrySet().removeIf(entry -> {
        if (isSessionExpired(entry.getValue())) {
            closeSessionQuietly(entry.getValue());  // ✅ Close expired session
            removedCount[0]++;
            return true;
        }
        return false;
    });
    
    LoggerUtil.getLogger().info("✅ Cleaned up " + removedCount[0] + " expired sessions");
}
```

**When Called?**
- Periodic cleanup (every 30 min timeout)
- Memory management
- Automatic housekeeping

**Why Correct?**
- Sessions older than 30 minutes
- Server-side connection likely stale
- Free up resources

---

### 🔧 INTERNAL - Session Instance Close (Not Cache Close)

#### 7️⃣ **SSHSessionManager.close() - Instance Only**

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHSessionManager.java`

**Lines 516-532**:
```java
public void close() {
    try {
        // Unregister from ProcessMonitorManager
        ProcessMonitorManager.getInstance().unregisterSession(sessionId);
        
        if (shell != null) {
            try { shell.close(); } catch (Exception ignored) {}  // Close shell
        }
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}  // Close session
        }
        // Don't close sshClient as it might be cached  ← IMPORTANT!
    } finally {
        commandInProgress = false;
        commandCancelled = false;
    }
}
```

**Important Note**:
- Closes **shell** and **session** objects
- Does **NOT** close **sshClient** (intentionally preserved for cache)
- **But** closing shell/session makes the cached session "dead"
- Next cache lookup finds "dead" session → recreates everything

**This is why closing breaks the cache!**

---

## 📊 Summary Table

| Scenario | File | Line | Action | Should Close? | Cache Impact |
|----------|------|------|--------|---------------|--------------|
| **One-time command** | UnifiedSSHService.java | 102 | `ssh.close()` | ✅ Yes | None (not intended for cache) |
| **One-time sudo** | UnifiedSSHService.java | 144 | `ssh.close()` | ✅ Yes | None (not intended for cache) |
| **Legacy command** | SSHExecutor.java | 48 | `manager.close()` | ✅ Yes | None (deprecated) |
| **DB Import** | ProjectDetailsController.java | 1090 | `ssh.close()` | ❌ NO | **BREAKS CACHE** |
| **User cancellation** | SSHSessionManager.java | 136 | `closeSessionQuietly()` | ✅ Yes | Removes from cache (intentional) |
| **App shutdown** | SSHSessionManager.java | 541 | `closeSessionQuietly()` | ✅ Yes | Clears entire cache |
| **Expired cleanup** | SSHSessionManager.java | 576 | `closeSessionQuietly()` | ✅ Yes | Removes expired only |
| **Instance close** | SSHSessionManager.java | 522-525 | `shell/session.close()` | ⚠️ Depends | Makes cached session "dead" |

---

## 🎯 The Key Problem

### UnifiedSSHService Has TWO Patterns:

#### Pattern 1: **One-Time Use** (Closes Immediately)
```java
// For single commands that won't be repeated
CommandResult result = UnifiedSSHService.executeCommand(project, "ls -la", 30);
// Session is closed automatically ✅
```

#### Pattern 2: **Persistent Use** (Should NOT Close)
```java
// For multiple operations (DB import, file transfers, etc.)
SSHSessionManager ssh = UnifiedSSHService.createSSHSession(project);
ssh.initialize();

// Execute multiple commands
ssh.executeCommand("cmd1", 30);
ssh.executeCommand("cmd2", 30);
ssh.executeCommand("cmd3", 30);

// DON'T CLOSE - let it cache! ❌ ssh.close()
```

### The DB Import Uses Pattern 2 But Closes Like Pattern 1! ❌

---

## 🔧 Recommendations

### 1. **Fix DB Import** (CRITICAL)

**ProjectDetailsController.java line 1090**:
```java
// REMOVE THIS:
// ssh.close();

// OR use persistent pattern:
// Leave session in cache for reuse
```

### 2. **Add Clear Documentation** (HIGH)

Add comment to `UnifiedSSHService`:
```java
/**
 * NOTE: For operations that may be repeated (like DB imports),
 * use createSSHSession() and DON'T call close().
 * The session will be cached automatically for 30 minutes.
 * 
 * For one-time commands, use the static executeCommand() methods.
 */
```

### 3. **Consider New Method** (MEDIUM)

Add to `UnifiedSSHService`:
```java
/**
 * Execute multiple commands with a persistent session.
 * Session is automatically cached and reused.
 * DON'T call close() on the returned session manager.
 */
public static SSHSessionManager createPersistentSessionForMultipleOps(ProjectEntity project) throws Exception {
    SSHSessionManager ssh = createSSHSession(project);
    ssh.initialize();
    // Return without closing - caller should NOT close either
    return ssh;
}
```

### 4. **Add Cleanup Hook** (LOW)

Ensure all sessions close on app shutdown:
```java
// In main app initialization
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    SSHSessionManager.closeAllSessions();
}));
```

---

## 🧪 Testing Different Scenarios

### Test 1: One-Time Command (Should Close)
```java
// This should be slow every time (no cache)
CommandResult result = UnifiedSSHService.executeCommand(project, "hostname", 30);
// Expected: ~3-5 seconds each time
```

### Test 2: DB Import (Should Cache)
```java
// First import
importDbDataFromSSH();  // 15 seconds

// Second import (immediate)
importDbDataFromSSH();  // Should be 2 seconds ⚡ (if fixed)
```

### Test 3: Manual Session (Should Cache)
```java
// Create persistent session
SSHSessionManager ssh = UnifiedSSHService.createSSHSession(project);
ssh.initialize();  // 15 seconds

// Use it multiple times
ssh.executeCommand("cmd1", 30);  // Fast
ssh.executeCommand("cmd2", 30);  // Fast
ssh.executeCommand("cmd3", 30);  // Fast

// DON'T close - let it cache
// ssh.close();  ❌ DON'T DO THIS
```

### Test 4: Cancellation (Should Remove from Cache)
```java
// Start long operation
SSHSessionManager ssh = UnifiedSSHService.createSSHSession(project);
ssh.initialize();
ssh.executeCommand("sleep 100", 120);

// User clicks cancel
ssh.cancelCommand();  // ✅ Removes from cache

// Next operation should create new session
```

---

## 🎓 Key Takeaways

1. **One-time commands** → Close immediately ✅
2. **Multi-operation sessions** → DON'T close, let cache handle it ✅
3. **User cancellation** → Remove from cache + close ✅
4. **Expired sessions** → Auto-cleanup after 30 min ✅
5. **App shutdown** → Close all cached sessions ✅

**The DB Import is a multi-operation session but is being closed like a one-time command!**

That's why you're not getting the cache benefit! 🎯

