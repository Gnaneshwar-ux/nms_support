# Decompile Status Label Dynamic Fix

## Issue Fixed

**Problem**: The status label at the bottom left was not showing dynamic class progress during decompilation. Instead of showing "Decompiling: 150/300 classes", it was stuck showing "Decompiling JARs..." with a progress bar showing "-100%".

**Root Cause**: The reflection-based approach to update the Task message was failing silently, so the dynamic class progress messages were never being displayed.

---

## Solution Implemented

### 1. Added StringProperty for Dynamic Messages

**File**: `JarDecompilerService.java`

```java
// Message property for dynamic updates
private final StringProperty currentMessage = new SimpleStringProperty();

/**
 * Get the current message property for dynamic updates
 */
public StringProperty currentMessageProperty() {
    return currentMessage;
}
```

### 2. Updated Message Setting Logic

**Before** (Reflection approach - was failing):
```java
try {
    // Call the public updateTaskMessage method defined in the anonymous Task class
    task.getClass().getMethod("updateTaskMessage", String.class)
        .invoke(task, "Decompiling: " + completedClasses + "/" + totalClasses + " classes");
} catch (Exception e) {
    // Silently ignore if method not found (shouldn't happen)
    logger.warning("Could not update task message: " + e.getMessage());
}
```

**After** (Property binding approach):
```java
// Update message every 10 classes or every 10% of total, whichever is less frequent
int updateInterval = Math.max(10, totalClasses / 100);
if (completedClasses % updateInterval == 0 || completedClasses == totalClasses) {
    // Update the message property directly
    Platform.runLater(() -> {
        currentMessage.set("Decompiling: " + completedClasses + "/" + totalClasses + " classes");
    });
}
```

### 3. Added Property Listener in Controller

**File**: `EnhancedJarDecompilerController.java`

```java
// Listen to current message property for dynamic class progress updates
decompilerService.currentMessageProperty().addListener((observable, oldValue, newValue) -> {
    if (newValue != null && !newValue.isEmpty() && decompilerService.isRunning()) {
        Platform.runLater(() -> {
            updateStatus(newValue, "fa-spinner");
        });
    }
});
```

### 4. Updated All Message Points

Added `Platform.runLater()` calls to update the `currentMessage` property at all key points:

```java
// Scanning phase
Platform.runLater(() -> currentMessage.set("Scanning JAR files..."));

// After counting classes
Platform.runLater(() -> currentMessage.set("Found " + totalClasses + " classes in " + totalJars + " JARs"));

// During decompilation (in the loop)
Platform.runLater(() -> {
    currentMessage.set("Decompiling: " + completedClasses + "/" + totalClasses + " classes");
});

// Completion
Platform.runLater(() -> currentMessage.set("Decompilation completed: " + completedClasses + "/" + totalClasses + " classes"));
```

---

## How It Works Now

### Decompilation Flow

1. **Initial**: Status shows "Starting decompilation..." (from `setOnRunning`)
2. **Scanning**: Status updates to "Scanning JAR files..." via `currentMessage` property
3. **Counting**: Status updates to "Found 1947 classes in 58 JARs" via `currentMessage` property
4. **Decompiling**: Status continuously updates via `currentMessage` property:
   - "Decompiling: 10/1947 classes"
   - "Decompiling: 20/1947 classes"
   - "Decompiling: 30/1947 classes"
   - ... (updates every 10 classes or every 1%)
5. **Completion**: Status shows "Decompilation completed: 1947/1947 classes"

### Thread Safety

- All `currentMessage.set()` calls are wrapped in `Platform.runLater()` to ensure they execute on the JavaFX Application Thread
- The property listener in the controller also uses `Platform.runLater()` for safety
- This prevents any threading issues that could cause UI updates to fail

---

## Key Benefits

### 1. **Reliable Message Updates**
- No more silent failures from reflection
- Direct property binding ensures messages always get through
- Thread-safe updates using `Platform.runLater()`

### 2. **Real-time Progress Feedback**
- Users can see exact class progress: "Decompiling: 150/300 classes"
- Updates are frequent enough to show progress but not so frequent as to flood the UI
- Clear indication that decompilation is actively working

### 3. **Better User Experience**
- No more confusion about whether decompilation is stuck
- Users can estimate remaining time based on class progress
- Status continues showing progress even if user opens VS Code during decompilation

---

## Files Modified

1. **src/main/java/com/nms/support/nms_support/service/globalPack/JarDecompilerService.java**
   - Added `StringProperty currentMessage` field
   - Added `currentMessageProperty()` getter method
   - Added imports for `StringProperty`, `SimpleStringProperty`, and `Platform`
   - Updated message setting logic to use property binding instead of reflection
   - Added `Platform.runLater()` calls at all message update points
   - Removed unused reflection-based `updateTaskMessage()` method

2. **src/main/java/com/nms/support/nms_support/controller/EnhancedJarDecompilerController.java**
   - Added listener for `decompilerService.currentMessageProperty()`
   - The listener updates the status label with dynamic class progress messages

---

## Testing

### Expected Behavior
1. Start decompilation of a large JAR (e.g., nms_server.jar)
2. Status should show: "Starting decompilation..."
3. Then: "Scanning JAR files..."
4. Then: "Found X classes in Y JARs"
5. Then continuous updates: "Decompiling: 10/X classes", "Decompiling: 20/X classes", etc.
6. Finally: "Decompilation completed: X/X classes"

### What Was Fixed
- **Before**: Status stuck at "Decompiling JARs..." with "-100%" progress
- **After**: Status shows real-time class progress with actual numbers

### Verification
- The status label at bottom left should now show dynamic class counts
- Progress bar should still work for JAR-level progress
- Opening VS Code during decompilation should not interrupt the progress display
- Messages should update smoothly without UI freezing

---

## Technical Notes

- **Update Frequency**: Messages update every 10 classes or every 1% of total (whichever is less frequent)
- **Thread Safety**: All UI updates use `Platform.runLater()` for JavaFX thread safety
- **Performance**: Property binding is more efficient than reflection
- **Reliability**: Direct property updates eliminate silent failures
- **Compatibility**: Maintains existing service message property for backward compatibility

The fix ensures that users get clear, real-time feedback about decompilation progress, making the process much more transparent and user-friendly.
