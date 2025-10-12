# Decompile Status Label Fix

## Issues Fixed

### Issue 1: No Class Progress Display
**Problem**: During decompilation, users could not see the progress of individual classes being decompiled. The status only showed JAR-level progress (e.g., "Processed 1 of 3 JARs"), which didn't provide granular feedback for large JARs with many classes.

**Expected Behavior**: Status label should show `Decompiling: <completed classes>/<total classes>` (e.g., "Decompiling: 150/300 classes").

### Issue 2: Status Label Changes When Opening VS Code During Decompilation
**Problem**: When users clicked "Open VS Code" while decompilation was running, the status label would change from showing decompilation progress to "Opened VS Code..." and never return to showing the decompilation progress.

**Expected Behavior**: Status label should continue showing decompilation progress even if the user opens VS Code during the process.

---

## Solutions Implemented

### Solution 1: Track and Display Class-Level Progress

#### Changes to `JarDecompilerService.java`

**1. Added Class Tracking Variables**
```java
// Progress tracking
private int totalJars;
private int processedJars;
private int totalClasses;      // ✓ NEW
private int completedClasses;  // ✓ NEW
```

**2. Initialize Class Counters**
```java
public void setDecompilationParams(String jarPath, String projectName, List<String> selectedJars) {
    this.jarPath = jarPath;
    this.projectName = projectName;
    this.selectedJars = selectedJars != null ? new ArrayList<>(selectedJars) : new ArrayList<>();
    this.totalJars = selectedJars.size();
    this.processedJars = 0;
    this.totalClasses = 0;       // ✓ NEW
    this.completedClasses = 0;   // ✓ NEW
}
```

**3. Count Total Classes Before Decompilation**

Added a first pass to count all classes in all selected JARs:

```java
// First pass: Count total classes in all selected JARs
updateMessage("Scanning JAR files...");
for (String jarName : selectedJars) {
    String jarFilePath = Paths.get(jarPath, jarName).toString();
    try (JarFile jarFile = new JarFile(jarFilePath)) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                totalClasses++;
            }
        }
    } catch (Exception e) {
        logger.warning("Error counting classes in JAR " + jarName + ": " + e.getMessage());
    }
}
logger.info("Total classes to decompile: " + totalClasses);
updateMessage("Found " + totalClasses + " classes in " + totalJars + " JARs");
```

**4. Update Message During Decompilation**

Added message updates as each class is decompiled:

```java
processedCount++;
completedClasses++;

// Update message every 10 classes or every 10% of total, whichever is less frequent
int updateInterval = Math.max(10, totalClasses / 100);
if (completedClasses % updateInterval == 0 || completedClasses == totalClasses) {
    try {
        // Call the public updateTaskMessage method defined in the anonymous Task class
        task.getClass().getMethod("updateTaskMessage", String.class)
            .invoke(task, "Decompiling: " + completedClasses + "/" + totalClasses + " classes");
    } catch (Exception e) {
        // Silently ignore if method not found (shouldn't happen)
        logger.warning("Could not update task message: " + e.getMessage());
    }
}
```

**5. Added Public Method in Task for Message Updates**

Since `updateMessage()` is protected in the `Task` class, we created a public wrapper method:

```java
@Override
protected Task<Void> createTask() {
    return new Task<Void>() {
        // Public method to update message from helper methods
        public void updateTaskMessage(String message) {
            updateMessage(message);
        }
        
        @Override
        protected Void call() throws Exception {
            // ... decompilation logic
        }
    };
}
```

**6. Final Completion Message**
```java
updateMessage("Decompilation completed: " + completedClasses + "/" + totalClasses + " classes");
```

---

### Solution 2: Prevent Status Updates During Decompilation

#### Changes to `EnhancedJarDecompilerController.java`

**1. Added Helper Method to Check Decompilation Status**
```java
/**
 * Check if decompilation is currently running
 */
private boolean isDecompilationRunning() {
    return decompilerService != null && decompilerService.isRunning();
}
```

**2. Modified `openInVSCode()` to Preserve Status**

Added conditional status updates that only happen when decompilation is NOT running:

```java
private void openInVSCode() {
    String extractedPath = getExtractedPath();
    if (extractedPath == null || !new File(extractedPath).exists()) {
        DialogUtil.showAlert(Alert.AlertType.WARNING, "No Extracted Files", "Please decompile JARs first.");
        return;
    }
    
    try {
        // Get currently selected class
        String selectedClass = classListView.getSelectionModel().getSelectedItem();
        if (selectedClass != null && !selectedClass.isEmpty()) {
            // Open VS Code with the selected class file
            JarDecompilerService.openInVSCode(extractedPath, selectedClass);
            // Only update status if decompilation is not running ✓ NEW
            if (!isDecompilationRunning()) {
                updateStatus("Opened VS Code with class: " + selectedClass, "fa-code");
            }
        } else {
            // No class selected, open directory
            JarDecompilerService.openInVSCode(extractedPath);
            // Only update status if decompilation is not running ✓ NEW
            if (!isDecompilationRunning()) {
                updateStatus("Opened VS Code with extracted files", "fa-code");
            }
        }
    } catch (Exception e) {
        logger.severe("Error opening VS Code: " + e.getMessage());
        DialogUtil.showAlert(Alert.AlertType.ERROR, "Error", "Failed to open VS Code: " + e.getMessage());
        // Only update status if decompilation is not running ✓ NEW
        if (!isDecompilationRunning()) {
            updateStatus("Error opening VS Code", "fa-exclamation-triangle");
        }
    }
}
```

**3. Added Message Property Listener**

To ensure the status label continuously updates with decompilation progress:

```java
private void setupServiceHandlers() {
    decompilerService.setOnRunning(event -> {
        Platform.runLater(() -> {
            progressBar.setVisible(true);
            progressLabel.setVisible(true);
            decompileButton.setDisable(true);
            updateStatus("Starting decompilation...", "fa-spinner");
        });
    });
    
    // Listen to message property to update status during decompilation ✓ NEW
    decompilerService.messageProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue != null && !newValue.isEmpty() && decompilerService.isRunning()) {
            Platform.runLater(() -> {
                updateStatus(newValue, "fa-spinner");
            });
        }
    });
    
    // ... rest of handlers
}
```

---

## How It Works Now

### Decompilation Process Flow

1. **User Starts Decompilation**: Clicks "Decompile" button

2. **Scanning Phase**: 
   - Status: "Scanning JAR files..."
   - System counts total classes in all selected JARs
   - Status updates to: "Found X classes in Y JARs"

3. **Decompilation Phase**:
   - Status updates continuously: "Decompiling: X/Y classes"
   - Updates every 10 classes or every 1% (whichever is less frequent)
   - Example progression:
     ```
     Decompiling: 10/300 classes
     Decompiling: 20/300 classes
     Decompiling: 30/300 classes
     ...
     Decompiling: 300/300 classes
     ```

4. **Completion**:
   - Status: "Decompilation completed: X/Y classes"
   - Final alert dialog shown

5. **During Decompilation Actions**:
   - If user clicks "Open VS Code": VS Code opens but status continues showing progress
   - Status label never interrupts the decompilation progress display

---

## Benefits

### For Users

1. **Clear Progress Feedback**: Users can see exactly how many classes have been processed
2. **Better Time Estimation**: With class-level progress, users can estimate remaining time
3. **No Status Confusion**: Actions like opening VS Code don't disrupt the progress display
4. **Responsive UI**: VS Code can be opened while decompilation continues in background

### For Large JARs

- A single JAR with 1000 classes will show meaningful progress instead of appearing stuck
- Users can see the decompilation is actively working, not frozen
- Example: `nms_server.jar` with 500 classes will show 500 updates instead of just 1

---

## Update Frequency

The update interval is intelligent:
```java
int updateInterval = Math.max(10, totalClasses / 100);
```

- **Small projects** (< 1000 classes): Updates every 10 classes
- **Large projects** (> 1000 classes): Updates every 1% of total
- This prevents UI flooding while maintaining responsive feedback

### Examples:
- 100 classes → Update every 10 classes (10 updates total)
- 500 classes → Update every 10 classes (50 updates total)  
- 2000 classes → Update every 20 classes (100 updates total)
- 10000 classes → Update every 100 classes (100 updates total)

---

## Files Modified

1. **src/main/java/com/nms/support/nms_support/service/globalPack/JarDecompilerService.java**
   - Added `totalClasses` and `completedClasses` tracking
   - Added scanning phase to count classes
   - Added public `updateTaskMessage()` wrapper method
   - Updated decompilation loop to track and report class progress
   - Modified method signatures to pass Task reference

2. **src/main/java/com/nms/support/nms_support/controller/EnhancedJarDecompilerController.java**
   - Added `isDecompilationRunning()` helper method
   - Modified `openInVSCode()` to conditionally update status
   - Added message property listener to track progress
   - Enhanced service handler setup

---

## Testing Recommendations

### Test Case 1: Basic Progress Display
1. Select JARs with various sizes (small, medium, large)
2. Start decompilation
3. Verify status shows: "Decompiling: X/Y classes" format
4. Verify numbers increment as decompilation progresses
5. Verify final message shows total: "Decompilation completed: X/Y classes"

### Test Case 2: Open VS Code During Decompilation
1. Start decompilation
2. While decompilation is running (showing "Decompiling: X/Y classes")
3. Click "Open VS Code" button
4. Verify VS Code opens
5. **Verify status continues showing decompilation progress** (not "Opened VS Code...")
6. Verify progress continues updating
7. Wait for completion
8. Now click "Open VS Code" after completion
9. Verify status changes to "Opened VS Code with..." (because decompilation finished)

### Test Case 3: Large JAR Progress
1. Select a large JAR file (e.g., nms_server.jar with 500+ classes)
2. Start decompilation
3. Verify status updates regularly (not appearing frozen)
4. Verify progress numbers increase smoothly
5. Verify UI remains responsive

### Test Case 4: Multiple JARs
1. Select multiple JARs (e.g., nms_common, nms_server, nms_web)
2. Verify scanning shows total from all JARs
3. Verify progress counts classes across all JARs
4. Verify completion shows total from all JARs

---

## Notes

- Progress updates are throttled to prevent UI flooding (max ~100 updates per decompilation)
- The spinner icon (`fa-spinner`) remains during the entire decompilation process
- Message property binding ensures real-time status updates
- Status updates only occur on the JavaFX Application Thread for thread safety
- Reflection is used to call the public `updateTaskMessage()` method from helper methods

