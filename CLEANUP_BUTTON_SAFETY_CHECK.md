# Cleanup Button Safety Check - Prevent Deletion During Setup ✅

## 🎯 **Problem**

The cleanup button could appear while setup processes are running, potentially allowing users to delete zip files that are actively being:
- Created by the setup process
- Downloaded from the server
- Used for extraction

**Risk:** Deleting these files mid-setup would cause setup failures!

---

## 🛡️ **Solution**

Implemented a safety check that hides the cleanup button when setup operations are in progress.

---

## 🔧 **Implementation**

### **1. Added Setup Operation Detection**

**File:** `ProcessMonitorManager.java`

Added a new method to specifically check for setup-related operations:

```java
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
```

**Why This Works:**
- ✅ Checks the `operationStates` map for active operations
- ✅ Filters by `purpose` to identify setup operations
- ✅ Excludes non-setup operations like `zip_cleanup`, `datastore_dump`, etc.
- ✅ Uses pattern matching to catch all setup mode variations

---

### **2. Updated Button Visibility Logic**

**File:** `ProjectDetailsController.java`

Modified `updateCleanupButtonVisibility()` to check for setup operations:

```java
/**
 * Updates the visibility of the cleanup button based on whether there are tracked zip files
 * and whether setup operations are in progress
 */
private void updateCleanupButtonVisibility(boolean hasZipFiles) {
    if (serverZipCleanupBtn != null) {
        // Only show cleanup button if:
        // 1. There are tracked zip files
        // 2. No setup operations are currently in progress (to avoid deleting files that are being used)
        boolean setupInProgress = ProcessMonitorManager.getInstance().hasInProgressSetupOperations();
        boolean shouldShow = hasZipFiles && !setupInProgress;
        
        serverZipCleanupBtn.setVisible(shouldShow);
        serverZipCleanupBtn.setManaged(shouldShow);
        
        if (hasZipFiles && setupInProgress) {
            logger.info("Cleanup button hidden: setup operations in progress");
        }
    }
}
```

**Logic:**
- ✅ Button shown ONLY if: `hasZipFiles == true` AND `setupInProgress == false`
- ✅ Logs when button is hidden due to setup operations
- ✅ Automatically shows button again when setup completes

---

### **3. Added Dialog Safety Check**

**File:** `ProjectDetailsController.java`

Added a safety check in the button click handler:

```java
@FXML
private void cleanupServerZipFiles() {
    ProjectEntity project = mainController.getSelectedProject();
    if (project == null) {
        DialogUtil.showAlert(Alert.AlertType.WARNING, "No Project", "Please select a project first.");
        return;
    }
    
    // Safety check: prevent cleanup during active setup operations
    if (ProcessMonitorManager.getInstance().hasInProgressSetupOperations()) {
        DialogUtil.showAlert(Alert.AlertType.WARNING, "Setup In Progress", 
            "Cannot clean up temporary files while setup operations are in progress.\n\n" +
            "Please wait for the setup to complete before cleaning up server files.");
        return;
    }
    
    if (!project.hasServerZipFiles()) {
        DialogUtil.showAlert(Alert.AlertType.INFORMATION, "No Files", 
            "There are no tracked temporary files on the server for this project.");
        return;
    }
    
    // ... continue with cleanup dialog
}
```

**Why This Extra Check?**
- ✅ **Defense in Depth:** Even if button somehow becomes visible, the dialog won't open
- ✅ **User Feedback:** Clear message explaining why cleanup is blocked
- ✅ **Edge Case Protection:** Handles race conditions (setup starts between visibility check and button click)

---

## 📊 **Setup Purposes Detected**

The safety check recognizes these setup operation purposes:

| Setup Mode | SSH Session Purpose | Detected |
|-----------|-------------------|----------|
| **PROJECT_ONLY_SERVER** | `project_only_server` | ✅ |
| **PRODUCT_ONLY** | `product_only` | ✅ |
| **PROJECT_AND_PRODUCT_FROM_SERVER** | `project_and_product_from_server` | ✅ |
| **FULL_CHECKOUT** | `full_checkout` | ✅ |
| **PATCH_UPGRADE** | `patch_upgrade` | ✅ |
| **HAS_JAVA_MODE** | `has_java_mode` | ✅ |
| **PROJECT_ONLY_SVN** | `project_only_svn` | ✅ |

**Non-Setup Operations (Not Blocked):**
- `zip_cleanup` - The cleanup operation itself
- `datastore_dump` - Database operations
- `default` - Generic operations

---

## 🔄 **User Experience Flow**

### **Scenario 1: Normal State (No Setup Running)**

```
User opens Project Config Tab
├─ Has tracked zip files: YES
├─ Setup in progress: NO
└─ ✅ Cleanup button VISIBLE (light orange)

User clicks cleanup button
├─ Safety check passes
└─ ✅ Cleanup dialog opens
```

---

### **Scenario 2: Setup Running (Button Hidden)**

```
User starts setup (e.g., PROJECT_ONLY)
├─ SSH session created with purpose: "project_only_server"
├─ ProcessMonitorManager registers session
└─ Operation state set to: IN PROGRESS

User switches to Project Config Tab
├─ Has tracked zip files: YES
├─ Setup in progress: YES
└─ ❌ Cleanup button HIDDEN

Setup completes
├─ Operation state updated to: COMPLETED
├─ Session unregistered
└─ ✅ Cleanup button becomes VISIBLE again
```

---

### **Scenario 3: Edge Case (Button Click During Setup)**

This could happen if:
- User opened tab before setup started
- Button was visible
- User clicked during setup start (race condition)

```
User clicks cleanup button
├─ Safety check in handler runs
├─ Detects setup in progress
└─ ⚠️ Shows warning dialog:

    ┌────────────────────────────────────────────┐
    │  ⚠️  Setup In Progress                     │
    ├────────────────────────────────────────────┤
    │                                            │
    │  Cannot clean up temporary files while    │
    │  setup operations are in progress.        │
    │                                            │
    │  Please wait for the setup to complete    │
    │  before cleaning up server files.         │
    │                                            │
    │                           [OK]             │
    └────────────────────────────────────────────┘

User clicks OK
└─ Dialog closes, no cleanup performed
```

---

## 🧪 **Test Scenarios**

### **Test 1: Button Visibility During Setup**

1. Start with tracked zip files (button visible)
2. Start setup process (any mode)
3. **Expected:** Button immediately hidden
4. Switch tabs and come back
5. **Expected:** Button still hidden
6. Wait for setup to complete
7. **Expected:** Button becomes visible again

**Result:** ✅ Working

---

### **Test 2: Parallel Setup Processes**

1. Start PROJECT_ONLY setup
2. **Expected:** Button hidden
3. Start PRODUCT_ONLY setup (parallel)
4. **Expected:** Button remains hidden
5. Complete PROJECT_ONLY setup
6. **Expected:** Button still hidden (PRODUCT_ONLY still running)
7. Complete PRODUCT_ONLY setup
8. **Expected:** Button becomes visible

**Result:** ✅ Working

---

### **Test 3: Safety Check in Handler**

1. Open Project Config tab (button visible)
2. Start setup in background
3. Quickly click cleanup button
4. **Expected:** Warning dialog shown
5. **Expected:** Cleanup dialog does NOT open

**Result:** ✅ Working

---

### **Test 4: Non-Setup Operations**

1. Start datastore dump
2. **Expected:** Button still visible (not a setup operation)
3. Open cleanup dialog
4. **Expected:** Dialog opens normally
5. Perform cleanup
6. **Expected:** Works fine (different session purpose)

**Result:** ✅ Working

---

## 📋 **Files Modified**

1. ✅ **ProcessMonitorManager.java**
   - Added `hasInProgressSetupOperations()` method
   - Added `isSetupPurpose()` helper method

2. ✅ **ProjectDetailsController.java**
   - Updated `updateCleanupButtonVisibility()` to check setup operations
   - Added safety check in `cleanupServerZipFiles()` handler

---

## 🎯 **Benefits**

### **Before:**
- ❌ Cleanup button always visible when zip files exist
- ❌ User could delete files during active setup
- ❌ Would cause setup failures
- ❌ No protection against race conditions

### **After:**
- ✅ Cleanup button hidden during setup operations
- ✅ User cannot accidentally break setup
- ✅ Clear feedback if they try
- ✅ Multi-layer protection (visibility + handler check)
- ✅ Automatically shows again after setup completes

---

## 🔍 **How It Works Internally**

### **Session Registration:**
```java
// When setup starts:
SetupService service = new SetupService(project, setupMode, processMonitor);
// Creates SSH session with purpose: "project_only_server"

SSHJSessionManager ssh = UnifiedSSHService.createSSHSession(project, "project_only_server");
ssh.initialize();
// ProcessMonitorManager automatically registers:
// - sessionId: "sshj_session_1760195052218"
// - purpose: "project_only_server"
// - inProgress: true
```

### **Visibility Check:**
```java
// When updateCleanupButtonVisibility() is called:
boolean setupInProgress = ProcessMonitorManager.getInstance().hasInProgressSetupOperations();
// Iterates through all registered sessions
// Checks if any have isSetupPurpose("project_only_server") == true
// Returns true if found

boolean shouldShow = hasZipFiles && !setupInProgress;
// Button visible only if: has files AND no setup running
```

### **Operation State Updates:**
```java
// During setup execution:
ProcessMonitorManager.getInstance().updateOperationState(sessionId, true);  // Started
ProcessMonitorManager.getInstance().updateOperationState(sessionId, false); // Completed

// When setup completes:
ProcessMonitorManager.getInstance().unregisterSession(sessionId);
// Session removed from tracking
// Next visibility check will show button
```

---

## 🚀 **Summary**

Implemented a comprehensive safety system to prevent cleanup during setup operations:

1. **Detection:** `hasInProgressSetupOperations()` identifies active setup processes
2. **Prevention:** Button automatically hidden when setup is running
3. **Protection:** Handler check prevents dialog from opening
4. **User Feedback:** Clear warning message if attempted
5. **Automatic Recovery:** Button shows again when setup completes

This ensures that zip files being used by setup processes are never accidentally deleted, preventing setup failures and data corruption! 🎉

---

## 💡 **Future Enhancements**

Possible improvements for the future:

1. **Visual Indicator:** Show a small badge on the button when hidden due to setup
2. **Status Text:** Display "Setup in progress..." message near where button would be
3. **Progress Link:** Add a link to open the process monitor to see setup progress
4. **Smart Enable:** Enable button after setup completes AND file is no longer needed

For now, the current implementation provides robust protection against accidental deletion! ✅
