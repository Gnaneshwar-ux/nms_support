# SSH Session Caching Analysis & Optimization Recommendations

## Executive Summary

**Current Issue**: Sessions ARE cached globally, but the DB import functionality is **breaking the cache** by calling `ssh.close()`, forcing complete re-initialization every time.

**Impact**: Each DB import takes ~10-15 seconds due to:
- Creating new SSH connection
- Shell initialization (2 second wait)
- User switching with sudo (5 seconds with password prompts)
- User verification commands (whoami, pwd, echo $USER)
- Multiple Thread.sleep() delays

**Solution**: Remove the `ssh.close()` call and let the cache work as designed.

---

## Current Session Workflow

### 1. Session Cache Architecture

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHSessionManager.java`

```java
// Line 18-19: Global static session cache
private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
private static final ConcurrentHashMap<String, CachedSession> sessionCache = new ConcurrentHashMap<>();
```

**How it works**:
- Sessions are cached globally using a `ConcurrentHashMap`
- Cache key: `host:port:sshUser:targetUser`
- Timeout: **30 minutes** of inactivity
- **Shared across the entire application** for the runtime

### 2. DB Import from SSH Workflow

**File**: `src/main/java/com/nms/support/nms_support/controller/ProjectDetailsController.java` (line 991-1120)

**Current Flow**:
```
User clicks DB Import button
  ↓
1. Create new SSHSessionManager (line 1007)
  ↓
2. Call ssh.initialize() (line 1008)
  ↓
3. Check cache for existing session (SSHSessionManager.java:55-56)
  ↓
4. IF CACHE HIT:
   - Reuse cached session (~100ms)
   - Skip initialization
   ✅ FAST PATH
  ↓
5. IF CACHE MISS:
   - Create new SSH connection
   - Wait 2 seconds for shell
   - Switch to target user (sudo su -)
   - Handle password prompts (5 seconds)
   - Verify user switch (3 commands)
   ❌ SLOW PATH (~10-15 seconds)
  ↓
6. Execute DB queries (environment variables, TNS parsing)
  ↓
7. **🔴 PROBLEM**: ssh.close() (line 1090)
   - Closes the session instance
   - Session stays in cache BUT reference is lost
   - Next call creates NEW SSHSessionManager instance
   - Cache logic checks but may miss due to timing
```

### 3. Session Initialization Details

**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SSHSessionManager.java` (line 605-634)

**Time breakdown**:
```
createNewSession():
  1. SSH connection (sshClient.connect)           ~1-2 seconds
  2. Thread.sleep(2000)                           2 seconds (line 623)
  3. clearShellBuffer()                           ~100ms
  4. switchToTargetUser() (if LDAP):
     - Send sudo su - command                     ~100ms
     - handleSudoPasswordPromptWithTTY():
       * Thread.sleep(2000)                       2 seconds (line 663)
       * Thread.sleep(3000)                       3 seconds (line 678)
     - verifyUserSwitch():
       * executeCommand("whoami")                 ~500ms
       * executeCommand("pwd")                    ~500ms
       * executeCommand("echo $USER")             ~500ms
  
  TOTAL: ~10-15 seconds for LDAP with sudo
  TOTAL: ~3-4 seconds for basic auth
```

---

## Why Sessions Are Being Created Every Time

### The Root Cause

**Line 1090** in `ProjectDetailsController.java`:
```java
ssh.close();
```

**Why this breaks caching**:

1. Each DB import creates a **new** `SSHSessionManager` instance
2. The instance checks the **global cache** and finds a cached session ✅
3. It reuses the cached connection (fast!) ✅
4. **BUT** then calls `ssh.close()` which:
   - Closes the local reference to shell/session
   - Does NOT close the cached sshClient (intentionally)
   - Next call creates a NEW instance
   - New instance has a NEW sessionId
   - Cache lookup works BUT the session might be marked as "in use" or stale

### Evidence from Code

**SSHSessionManager.java (line 516-532)**:
```java
public void close() {
    try {
        // Unregister from ProcessMonitorManager
        ProcessMonitorManager.getInstance().unregisterSession(sessionId);
        
        if (shell != null) {
            try { shell.close(); } catch (Exception ignored) {}
        }
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
        }
        // Don't close sshClient as it might be cached  ← INTENDED FOR CACHING
    } finally {
        commandInProgress = false;
        commandCancelled = false;
    }
}
```

**The Problem**: Even though `sshClient` is preserved in cache, the `shell` and `session` are closed. When the cache check happens (line 70), it calls `isSessionAlive()` which checks if the shell is open, and it's NOT because it was closed!

---

## Optimization Recommendations

### 🎯 Immediate Fix (High Priority)

**File**: `src/main/java/com/nms/support/nms_support/controller/ProjectDetailsController.java`

**Line 1090**: Remove or comment out the `ssh.close()` call

```java
// BEFORE (line 1090):
ssh.close();
return null;

// AFTER:
// Don't close - let the session be cached for reuse (30 min timeout)
// ssh.close();
return null;
```

**Expected Impact**:
- First DB import: ~10-15 seconds (initial connection)
- Subsequent DB imports: **~1-2 seconds** (reuses cached session)
- **80-90% time reduction** for repeated imports

### 🔧 Additional Optimizations (Medium Priority)

#### 1. Reduce Initialization Delays

**File**: `SSHSessionManager.java`

**Line 623**: Reduce initial shell wait
```java
// BEFORE:
Thread.sleep(2000);

// AFTER:
Thread.sleep(500); // Most shells are ready in 500ms
```

**Line 663, 678**: Optimize password prompt handling
```java
// BEFORE:
Thread.sleep(2000);  // Line 663
Thread.sleep(3000);  // Line 678

// AFTER:
Thread.sleep(1000);  // Most password prompts respond in 1s
Thread.sleep(1500);  // Reduce post-password wait
```

**Expected Impact**: Reduces initial connection time from 10-15s to **6-8s**

#### 2. Skip Verification for Cached Sessions

**File**: `SSHSessionManager.java` (line 76-81)

Already implemented! The code skips sudo setup for cached sessions:
```java
if (cachedSession.sudoEnabled && targetUser != null && targetUser.equals(cachedSession.targetUser)) {
    LoggerUtil.getLogger().info("🚀 Skipping sudo setup - already enabled for target user: " + targetUser);
}
```

**But** this only works if the session isn't closed. Fix line 1090 first.

#### 3. Batch Environment Variable Queries

**File**: `ProjectDetailsController.java` (lines 1014-1023)

Instead of 3-4 separate SSH commands, use one:

```java
// BEFORE:
String rdbmsHost = getEnvironmentVariable(ssh, "RDBMS_HOST");
String oracleUser = getEnvironmentVariable(ssh, "ORACLE_READ_ONLY_USER");
if (oracleUser == null || oracleUser.trim().isEmpty()) {
    oracleUser = getEnvironmentVariable(ssh, "ORACLE_READ_WRITE_USER");
}
String oracleSid = getEnvironmentVariable(ssh, "ORACLE_SERVICE_NAME");

// AFTER:
String batchCommand = "echo \"RDBMS_HOST=$RDBMS_HOST\" && " +
                      "echo \"ORACLE_USER=${ORACLE_READ_ONLY_USER:-$ORACLE_READ_WRITE_USER}\" && " +
                      "echo \"ORACLE_SID=$ORACLE_SERVICE_NAME\"";
CommandResult result = ssh.executeCommand(batchCommand, 30);
// Parse the combined output
```

**Expected Impact**: Reduces from 3-4 SSH commands to **1 command** (~2 second savings)

### 📊 Session Cache Management

The cache is already well-designed:
- ✅ Global static cache (shared across app)
- ✅ 30-minute timeout
- ✅ Automatic expiration cleanup
- ✅ Thread-safe (ConcurrentHashMap)
- ✅ Tracks sudo state

**Just need to use it properly!**

---

## Implementation Priority

### Phase 1 (Immediate - 5 minutes)
1. ✅ Remove `ssh.close()` call in `ProjectDetailsController.java:1090`
2. ✅ Test DB import - should be much faster on second attempt

### Phase 2 (Short term - 30 minutes)
1. Reduce Thread.sleep() delays in `SSHSessionManager.java`
2. Test thoroughly to ensure sudo still works

### Phase 3 (Medium term - 1-2 hours)
1. Batch environment variable queries
2. Add connection pooling statistics to logs
3. Add cache hit/miss metrics to UI

---

## Testing Recommendations

### Test Case 1: Cache Working
1. Click DB Import from SSH (first time)
   - **Expected**: 10-15 seconds
   - Check logs for: "🆕 SSH CACHE MISS"
2. Click DB Import from SSH (second time, within 30 min)
   - **Expected**: 1-2 seconds
   - Check logs for: "🔄 SSH CACHE HIT"

### Test Case 2: Cache Expiration
1. Click DB Import from SSH
2. Wait 31 minutes
3. Click DB Import from SSH again
   - **Expected**: 10-15 seconds
   - Check logs for: "⏰ SSH CACHE EXPIRED"

### Test Case 3: Multiple Projects
1. Import DB from Project A
2. Import DB from Project B (different host)
   - **Expected**: 10-15 seconds (different cache key)
3. Import DB from Project A again
   - **Expected**: 1-2 seconds (cache still valid)

---

## Current Cache Statistics

You can check cache statistics at runtime using:
```java
String stats = SSHSessionManager.getCacheStatistics();
// Output: "SSH Cache: 3 total, 2 active, 1 expired"
```

---

## Summary

| Metric | Before Fix | After Fix | Improvement |
|--------|-----------|-----------|-------------|
| First DB Import | 10-15s | 10-15s | - |
| Subsequent Imports (within 30 min) | 10-15s | 1-2s | **85-90%** |
| Memory Usage | Low | Low | No change |
| Session Cleanup | Manual | Automatic | Better |

**The cache IS working, just need to stop breaking it with `ssh.close()`!**

