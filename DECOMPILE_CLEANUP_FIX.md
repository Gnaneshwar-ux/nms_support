# Decompile Cleanup Fix

## Issue
When users performed decompilation multiple times for the same project, old decompiled files were not deleted before creating new ones. This caused several problems:

1. **Stale Files**: Old decompiled files from previous decompilations remained in the directory
2. **Mixed Versions**: If different JARs were selected in subsequent decompilations, users could see files from multiple versions mixed together
3. **Confusion**: Users viewing code might see outdated classes that were no longer part of the selected JARs
4. **Disk Space**: Old files accumulated over time, wasting disk space

## Root Cause
In `JarDecompilerService.java`, the `createExtractionDirectory()` method checked if the extraction directory existed, but if it did, it simply logged "Using existing extraction directory" and continued without cleaning it.

```java
// OLD CODE
private void createExtractionDirectory() throws IOException {
    String userHome = System.getProperty("user.home");
    extractedPath = Paths.get(userHome, "Documents", "nms_support_data", "extracted", projectName).toString();
    
    Path extractionDir = Paths.get(extractedPath);
    if (!Files.exists(extractionDir)) {
        Files.createDirectories(extractionDir);
        logger.info("Created extraction directory: " + extractedPath);
    } else {
        logger.info("Using existing extraction directory: " + extractedPath);  // ❌ No cleanup!
    }
}
```

## Solution
Modified the `createExtractionDirectory()` method to **delete all existing decompiled files** before starting a new decompilation:

### Changes Made

#### 1. Updated `createExtractionDirectory()` Method
```java
/**
 * Create the extraction directory structure
 * Cleans existing decompiled files before creating new ones
 */
private void createExtractionDirectory() throws IOException {
    String userHome = System.getProperty("user.home");
    extractedPath = Paths.get(userHome, "Documents", "nms_support_data", "extracted", projectName).toString();
    
    Path extractionDir = Paths.get(extractedPath);
    if (Files.exists(extractionDir)) {
        logger.info("Cleaning existing extraction directory: " + extractedPath);
        // Delete all existing decompiled files before decompiling again
        deleteDirectoryContents(extractionDir.toFile());
        logger.info("Deleted all existing decompiled files from: " + extractedPath);
    }
    
    // Create or recreate the extraction directory
    Files.createDirectories(extractionDir);
    logger.info("Created extraction directory: " + extractedPath);
}
```

#### 2. Added `deleteDirectoryContents()` Helper Method
This method recursively deletes all files and subdirectories within a directory, but preserves the directory itself:

```java
/**
 * Delete all contents of a directory recursively (but keep the directory itself)
 */
private void deleteDirectoryContents(File directory) throws IOException {
    if (!directory.exists() || !directory.isDirectory()) {
        return;
    }
    
    File[] files = directory.listFiles();
    if (files != null) {
        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively delete subdirectory and its contents
                deleteDirectoryRecursive(file);
            } else {
                // Delete file
                if (!file.delete()) {
                    logger.warning("Failed to delete file: " + file.getAbsolutePath());
                }
            }
        }
    }
}
```

#### 3. Added `deleteDirectoryRecursive()` Helper Method
This method recursively deletes a directory and all its contents:

```java
/**
 * Delete a directory and all its contents recursively
 */
private void deleteDirectoryRecursive(File directory) throws IOException {
    if (!directory.exists()) {
        return;
    }
    
    if (directory.isDirectory()) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectoryRecursive(file);
            }
        }
    }
    
    if (!directory.delete()) {
        logger.warning("Failed to delete directory: " + directory.getAbsolutePath());
    }
}
```

## How It Works Now

1. **User Starts Decompilation**: User selects JARs and clicks "Decompile"

2. **Check for Existing Directory**: 
   - If the extraction directory exists, it means previous decompilations exist
   - The system logs: "Cleaning existing extraction directory: {path}"

3. **Delete All Old Files**:
   - Recursively delete all `.java` files from previous decompilations
   - Delete all subdirectories (package structures) from previous decompilations
   - Delete metadata files (`.decompilation_info`, `.decompilation_metadata`)
   - The extraction directory itself is preserved (empty)

4. **Fresh Decompilation**:
   - Decompile only the currently selected JARs
   - Create clean directory structure with only the new files
   - Save new metadata for this decompilation

5. **Result**: Users see only the decompiled files from the current decompilation, with no old/stale files

## Benefits

1. **Clean State**: Every decompilation starts with a clean slate
2. **No Confusion**: Users only see files from the currently selected JARs
3. **Correct Code**: No risk of viewing outdated code from previous decompilations
4. **Disk Space**: Old files are removed automatically, preventing accumulation
5. **Consistent Results**: Each decompilation produces consistent, predictable results

## Safety Features

1. **Recursive Deletion**: Safely handles nested directory structures (package hierarchies)
2. **Error Handling**: Logs warnings if any files cannot be deleted, but continues with decompilation
3. **Directory Preservation**: Keeps the main extraction directory, only cleans its contents
4. **Per-Project Isolation**: Only cleans files for the current project (based on project name)

## Example Scenario

### Before Fix:
```
User performs first decompilation (selects nms_common.jar, nms_server.jar):
  - Creates: extracted/MyProject/nms_common/...
  - Creates: extracted/MyProject/nms_server/...

User performs second decompilation (selects only nms_common.jar):
  - Updates: extracted/MyProject/nms_common/...
  - OLD FILES REMAIN: extracted/MyProject/nms_server/... ❌ (STALE!)

Result: User sees files from BOTH decompilations mixed together
```

### After Fix:
```
User performs first decompilation (selects nms_common.jar, nms_server.jar):
  - Creates: extracted/MyProject/nms_common/...
  - Creates: extracted/MyProject/nms_server/...

User performs second decompilation (selects only nms_common.jar):
  - DELETES: extracted/MyProject/* (all old files) ✓
  - Creates: extracted/MyProject/nms_common/... (fresh copy) ✓

Result: User sees ONLY files from the current decompilation
```

## Files Modified

- `src/main/java/com/nms/support/nms_support/service/globalPack/JarDecompilerService.java`
  - Modified `createExtractionDirectory()` to clean existing files
  - Added `deleteDirectoryContents()` helper method
  - Added `deleteDirectoryRecursive()` helper method

## Testing Recommendations

1. **Basic Test**: 
   - Decompile some JARs
   - Verify files are created
   - Decompile again with same JARs
   - Verify old files are deleted and recreated

2. **Different JARs Test**:
   - Decompile JAR set A (e.g., nms_common, nms_server)
   - Decompile JAR set B (e.g., only nms_common)
   - Verify only JAR set B files exist (no nms_server files from first decompilation)

3. **Error Handling Test**:
   - Create read-only files in the extraction directory
   - Decompile again
   - Verify warnings are logged but decompilation continues

4. **Package Structure Test**:
   - Decompile JARs with deep package hierarchies
   - Verify all subdirectories are cleaned properly
   - Decompile again with different JARs
   - Verify old package structures are removed

## Notes

- This fix ensures that users always see an accurate representation of the currently selected JARs
- The cleanup happens automatically before each decompilation - no manual intervention needed
- Metadata files (`.decompilation_info`) are also cleaned, ensuring fresh project code tags
- The fix is backward compatible - works with existing extraction directories

