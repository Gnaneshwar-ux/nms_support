# Cleanup Button Setup Visibility Fix

## Issue
The cleanup button was appearing intermittently during setup operations, even when the setup process was still running. This was confusing for users and could potentially allow them to delete files that were being used by the setup process.

## Root Cause
The issue occurred because:

1. **ProcessMonitor Registration**: The `ProcessMonitor` itself was not registering with `ProcessMonitorManager` when setup started.

2. **Session-Level Tracking**: Only individual SSH sessions were being registered/unregistered during each setup step.

3. **Gaps Between Steps**: When one setup step's SSH session ended and before the next step's session started, there was a brief gap where:
   - The previous session was unregistered
   - The next session wasn't registered yet
   - `hasInProgressSetupOperations()` returned `false`
   - The cleanup button incorrectly appeared

4. **Tab Switching Trigger**: If the user switched to the Project Configuration tab during this gap, `loadProjectDetails()` would call `updateCleanupButtonVisibility()`, causing the button to appear momentarily.

## Solution

### 1. ProcessMonitor Registration
Modified `ProcessMonitor.java` to register itself with `ProcessMonitorManager` when the monitor window is shown:

```java
// Added fields to track registration
private final String monitorId;
private boolean registeredWithManager = false;

// Generate unique ID for this monitor instance
this.monitorId = "process_monitor_" + System.currentTimeMillis();

// Register when show() is called
private void registerWithManager() {
    if (!registeredWithManager) {
        String purpose = title.toLowerCase().contains("setup") ? "full_setup_process" : "process_operation";
        ProcessMonitorManager.getInstance().registerSession(monitorId, this, true, purpose);
        registeredWithManager = true;
        logger.info("ProcessMonitor registered with manager: " + monitorId + " (purpose: " + purpose + ")");
    }
}
```

### 2. Unregistration on Completion/Failure
Added unregistration calls when the process completes or fails:

```java
public void markProcessCompleted(String completionMessage) {
    // Unregister from ProcessMonitorManager first
    unregisterFromManager();
    
    Platform.runLater(() -> {
        // ... rest of completion logic
    });
}

public void markProcessFailed(String failureReason) {
    // Unregister from ProcessMonitorManager first
    unregisterFromManager();
    
    Platform.runLater(() -> {
        // ... rest of failure logic
    });
}
```

### 3. Window Close Handler
Added safety unregistration when the window is closed:

```java
this.dialogStage.setOnCloseRequest(e -> {
    if (isRunning.get()) {
        e.consume(); // Prevent default close behavior during execution
        dialogStage.setIconified(true); // Minimize the window
    } else {
        // Unregister from manager when window closes (safety check)
        unregisterFromManager();
    }
});
```

### 4. Updated Setup Purpose Detection
Modified `ProcessMonitorManager.java` to recognize the new "full_setup_process" purpose:

```java
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
           lowerPurpose.contains("project_and_product") ||
           lowerPurpose.contains("full_setup_process") ||
           lowerPurpose.contains("setup");
}
```

## How It Works Now

1. **Setup Starts**: When `ProcessMonitor.show()` is called, it immediately registers with `ProcessMonitorManager` with purpose "full_setup_process".

2. **During Setup**: 
   - The ProcessMonitor remains registered for the entire duration
   - Individual SSH sessions are still registered/unregistered for each step
   - Even if there are gaps between steps, the ProcessMonitor itself is still registered

3. **Cleanup Button Check**: When `updateCleanupButtonVisibility()` is called:
   ```java
   boolean setupInProgress = ProcessMonitorManager.getInstance().hasInProgressSetupOperations();
   boolean shouldShow = hasZipFiles && !setupInProgress;
   ```
   - It finds the registered ProcessMonitor with "full_setup_process" purpose
   - Returns `true` for `hasInProgressSetupOperations()`
   - The cleanup button stays hidden

4. **Setup Completes/Fails**: 
   - `markProcessCompleted()` or `markProcessFailed()` is called
   - ProcessMonitor unregisters from ProcessMonitorManager
   - Cleanup button visibility is updated in ProjectDetailsController
   - Button now correctly shows if there are zip files to clean

## Benefits

1. **Continuous Tracking**: The overall setup process is tracked from start to finish, not just individual SSH sessions.

2. **No Gaps**: There are no gaps where the cleanup button can incorrectly appear during setup.

3. **Safety**: Users cannot accidentally delete files while setup is in progress.

4. **Proper Cleanup**: The ProcessMonitor properly unregisters when done, allowing the cleanup button to appear when appropriate.

## Files Modified

1. `src/main/java/com/nms/support/nms_support/service/globalPack/ProcessMonitor.java`
   - Added monitorId field and registration tracking
   - Added registerWithManager() and unregisterFromManager() methods
   - Modified show() methods to register on display
   - Modified markProcessCompleted() and markProcessFailed() to unregister
   - Added unregistration to window close handler

2. `src/main/java/com/nms/support/nms_support/service/globalPack/ProcessMonitorManager.java`
   - Updated isSetupPurpose() to recognize "full_setup_process" and generic "setup" purposes

## Testing Recommendations

1. Start a setup operation (any mode)
2. While setup is running, switch to the Project Configuration tab
3. Verify the cleanup button does NOT appear during setup
4. Wait for setup to complete (success or failure)
5. Return to Project Configuration tab
6. Verify the cleanup button DOES appear if there are tracked zip files
7. Test with different setup modes to ensure all work correctly

