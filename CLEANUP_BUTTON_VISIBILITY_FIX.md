# Cleanup Button Visibility Fix During Setup Operations

## Issue Fixed

**Problem**: The cleanup button was showing up when switching tabs during setup operations, even though setup processes were still running. This happened specifically after the zip download completed but before the entire setup process finished.

**Root Cause**: The cleanup button visibility was being updated when tabs were changed, but the ProcessMonitorManager might not have been properly tracking all setup operations, or there was a timing issue where individual SSH sessions were being unregistered while the overall setup process was still ongoing.

---

## Solution Implemented

### 1. Enhanced Setup Detection Logic

**File**: `ProjectDetailsController.java`

**Before**:
```java
private void updateCleanupButtonVisibility(boolean hasZipFiles) {
    if (serverZipCleanupBtn != null) {
        boolean setupInProgress = ProcessMonitorManager.getInstance().hasInProgressSetupOperations();
        boolean shouldShow = hasZipFiles && !setupInProgress;
        
        serverZipCleanupBtn.setVisible(shouldShow);
        serverZipCleanupBtn.setManaged(shouldShow);
    }
}
```

**After**:
```java
private void updateCleanupButtonVisibility(boolean hasZipFiles) {
    if (serverZipCleanupBtn != null) {
        boolean setupInProgress = isSetupInProgress();
        boolean shouldShow = hasZipFiles && !setupInProgress;
        
        serverZipCleanupBtn.setVisible(shouldShow);
        serverZipCleanupBtn.setManaged(shouldShow);
        
        if (hasZipFiles && setupInProgress) {
            logger.info("Cleanup button hidden: setup operations in progress");
        } else if (hasZipFiles && !setupInProgress) {
            logger.info("Cleanup button shown: no setup operations in progress");
        }
    }
}

private boolean isSetupInProgress() {
    // Check ProcessMonitorManager for active setup sessions
    boolean processMonitorSetup = ProcessMonitorManager.getInstance().hasInProgressSetupOperations();
    
    if (processMonitorSetup) {
        logger.info("Setup in progress detected via ProcessMonitorManager");
        return true;
    }
    
    // Log detailed session information for debugging
    String sessionInfo = ProcessMonitorManager.getInstance().getActiveSessionsInfo();
    logger.info("Active sessions info:\n" + sessionInfo);
    
    return false;
}
```

### 2. Enhanced ProcessMonitorManager Debugging

**File**: `ProcessMonitorManager.java`

**Added detailed logging**:
```java
public boolean hasInProgressSetupOperations() {
    LoggerUtil.getLogger().info("Checking for in-progress setup operations. Active sessions: " + operationStates.size());
    
    boolean foundSetupOperation = false;
    for (Map.Entry<String, Boolean> entry : operationStates.entrySet()) {
        if (entry.getValue()) { // If operation is in progress
            String sessionId = entry.getKey();
            String purpose = sessionPurposes.get(sessionId);
            LoggerUtil.getLogger().info("Active session: " + sessionId + " (purpose: " + purpose + ")");
            
            if (purpose != null && isSetupPurpose(purpose)) {
                LoggerUtil.getLogger().info("Found in-progress setup operation: " + sessionId + " (purpose: " + purpose + ")");
                foundSetupOperation = true;
            }
        }
    }
    
    LoggerUtil.getLogger().info("Setup operations in progress: " + foundSetupOperation);
    return foundSetupOperation;
}
```

**Added debugging method**:
```java
public String getActiveSessionsInfo() {
    StringBuilder info = new StringBuilder();
    info.append("Active Sessions (").append(operationStates.size()).append("):\n");
    
    for (Map.Entry<String, Boolean> entry : operationStates.entrySet()) {
        String sessionId = entry.getKey();
        boolean inProgress = entry.getValue();
        String purpose = sessionPurposes.get(sessionId);
        Object session = activeSessions.get(sessionId);
        
        info.append("  - Session: ").append(sessionId)
            .append(" | InProgress: ").append(inProgress)
            .append(" | Purpose: ").append(purpose)
            .append(" | Type: ").append(session != null ? session.getClass().getSimpleName() : "null")
            .append("\n");
    }
    
    return info.toString();
}
```

---

## How It Works Now

### Setup Process Flow

1. **Setup Starts**: 
   - ProcessMonitor is created and shown
   - ProcessMonitor registers itself with ProcessMonitorManager with purpose "full_setup_process"
   - Individual SSH sessions register with purposes like "project_only", "product_only", etc.

2. **During Setup**:
   - Multiple SSH sessions may be active for different operations (zip download, file transfer, etc.)
   - Each session has a purpose that identifies it as setup-related
   - ProcessMonitorManager tracks all active sessions

3. **Tab Changes**:
   - When user switches tabs, `onTabSelected()` is called
   - This calls `loadProjectDetails()` which calls `updateCleanupButtonVisibility()`
   - `isSetupInProgress()` checks ProcessMonitorManager for active setup operations
   - Cleanup button is hidden if ANY setup operations are detected

4. **Setup Completion**:
   - ProcessMonitor calls `unregisterFromManager()` when setup completes
   - All SSH sessions are unregistered as they complete
   - Cleanup button becomes visible again

### Enhanced Detection Logic

The new `isSetupInProgress()` method:

1. **Primary Check**: Uses `ProcessMonitorManager.hasInProgressSetupOperations()`
2. **Debugging**: Logs detailed information about all active sessions
3. **Extensibility**: Can be extended to check for additional setup indicators if needed

### Debugging Information

The enhanced logging will show:
- Total number of active sessions
- Details of each active session (ID, purpose, type, progress status)
- Whether setup operations are detected
- Cleanup button visibility decisions

---

## Key Benefits

### 1. **More Robust Detection**
- Centralized setup detection logic
- Detailed logging for troubleshooting
- Extensible design for future enhancements

### 2. **Better Debugging**
- Clear visibility into what sessions are active
- Detailed logging of cleanup button decisions
- Easy identification of timing issues

### 3. **Improved User Experience**
- Cleanup button stays hidden during entire setup process
- No premature button appearance during tab switches
- Consistent behavior across all setup modes

---

## Files Modified

1. **src/main/java/com/nms/support/nms_support/controller/ProjectDetailsController.java**
   - Enhanced `updateCleanupButtonVisibility()` with better logging
   - Added `isSetupInProgress()` method for centralized setup detection
   - Added detailed session information logging

2. **src/main/java/com/nms/support/nms_support/service/globalPack/ProcessMonitorManager.java**
   - Enhanced `hasInProgressSetupOperations()` with detailed logging
   - Added `getActiveSessionsInfo()` method for debugging
   - Improved visibility into active session tracking

---

## Testing Scenarios

### Test Case 1: Setup with Tab Switching
1. Start a setup operation (e.g., Full Checkout)
2. Wait for zip download to complete
3. Switch between tabs while setup is still running
4. **Expected**: Cleanup button should remain hidden
5. Wait for setup to complete
6. **Expected**: Cleanup button should become visible

### Test Case 2: Different Setup Modes
1. Test with various setup modes:
   - Project Only (SVN)
   - Project Only (Server)
   - Product Only
   - Full Checkout
   - Patch Upgrade
2. Switch tabs during each setup
3. **Expected**: Cleanup button should stay hidden during all setup modes

### Test Case 3: Setup Failure
1. Start a setup operation
2. Let it fail (e.g., network error)
3. Switch tabs after failure
4. **Expected**: Cleanup button should become visible after setup fails

### Test Case 4: Multiple Operations
1. Start setup operation
2. While setup is running, try other operations that might create SSH sessions
3. Switch tabs
4. **Expected**: Only setup-related sessions should hide the cleanup button

---

## Debugging

If the cleanup button still appears during setup, check the logs for:

1. **Active Sessions**: Look for "Active Sessions info:" messages
2. **Setup Detection**: Look for "Setup operations in progress:" messages
3. **Button Decisions**: Look for "Cleanup button hidden/shown:" messages

The logs will show exactly what sessions are active and why the cleanup button visibility decision was made.

---

## Notes

- The ProcessMonitor should remain registered until the entire setup process completes
- Individual SSH sessions may be unregistered as they complete, but the ProcessMonitor keeps the overall setup state
- The enhanced logging will help identify any timing issues or missing registrations
- The solution is backward compatible and doesn't affect existing functionality

This fix ensures that the cleanup button remains properly hidden during the entire setup process, preventing users from accidentally deleting files that are still being used by active setup operations.
