# Server Zip File Cleanup Feature - Implementation Summary

## Overview
Implemented a comprehensive zip file tracking and cleanup system to prevent temporary zip files from accumulating on the server during project setup operations.

## Features Implemented

### 1. **Zip File Tracking in ProjectEntity**
- Added `ServerZipFile` inner class to store:
  - File path on server
  - Purpose/description
  - Creation timestamp
- Added list of tracked zip files with synchronized access methods
- Methods to add, remove, and check for zip files

**Files Modified:**
- `src/main/java/com/nms/support/nms_support/model/ProjectEntity.java`

### 2. **Automatic Tracking During Operations**
- Tracks zip files when created in `SFTPDownloadAndUnzip.createZipFile()`
- Automatically removes from tracking when:
  - Successfully deleted after download
  - Cancelled during operation
  - Error occurs during process
- Saves zip metadata including purpose (e.g., "Java download - project_only")

**Files Modified:**
- `src/main/java/com/nms/support/nms_support/service/buildTabPack/patchUpdate/SFTPDownloadAndUnzip.java`

### 3. **Cleanup Button UI**
- Added "Cleanup Server Temp Files" button below "Local Setup / Upgrade"
- Button visibility:
  - **Visible**: Only when tracked zip files exist
  - **Hidden**: When no zip files are tracked
- Auto-updates visibility when:
  - Loading project details
  - After cleanup operation completes

**Files Modified:**
- `src/main/resources/com/nms/support/nms_support/view/tabs/project-details.fxml`
- `src/main/java/com/nms/support/nms_support/controller/ProjectDetailsController.java`

### 4. **ZipCleanupDialog**
Professional dialog that:
1. **Scans Server**: Connects via SSH and checks existence of each tracked zip file
2. **Displays Files**: Shows each file with:
   - Full path
   - Purpose/description
   - Creation timestamp
   - File size
   - Status (exists/missing)
3. **Verification**: 
   - Green indicator for existing files
   - Red indicator for missing files (auto-removed from tracking)
4. **Selective Cleanup**:
   - Checkboxes to select files for deletion
   - Confirmation dialog before deletion
5. **Progress Tracking**: Real-time progress during scan and cleanup
6. **Auto-Save**: Persists changes to project data after cleanup

**Files Created:**
- `src/main/java/com/nms/support/nms_support/service/globalPack/ZipCleanupDialog.java`

## User Workflow

### Normal Setup Process
1. User runs "Local Setup / Upgrade"
2. System creates zip file on server (e.g., `/tmp/downloaded_java_1234567890.zip`)
3. Zip file is **automatically tracked** in project entity
4. After successful download, zip is **automatically deleted and untracked**

### Manual Cleanup Process
1. If zip files remain (due to errors/cancellations):
   - "Cleanup Server Temp Files" button **appears** automatically
2. User clicks the cleanup button
3. Dialog opens showing:
   - All tracked zip files
   - Which ones still exist on server
   - File details (size, date, purpose)
4. User selects files to delete (existing files pre-selected)
5. Confirms deletion
6. System:
   - Connects to server
   - Deletes selected files
   - Updates tracking
   - Saves project data
7. Button **hides** if no more zip files exist

## Safety Features

### 1. **Existence Verification**
- Always checks file existence before showing to user
- Shows actual file size from server
- Prevents errors from trying to delete non-existent files

### 2. **Confirmation Dialog**
- Requires user confirmation before deletion
- Shows count of files to be deleted
- Warns that action cannot be undone

### 3. **Automatic Cleanup**
- Missing files automatically removed from tracking
- No manual intervention needed for normal operations
- Stale references automatically cleared

### 4. **Tracking Persistence**
- Zip file list saved with project data
- Survives application restarts
- Jackson-compatible serialization

## Technical Details

### Zip File Tracking
```java
public static class ServerZipFile {
    private String path;              // Full server path
    private String purpose;           // Description
    private long createdTimestamp;    // When created
}
```

### Key Methods
- `project.addServerZipFile(path, purpose)` - Track new zip
- `project.removeServerZipFile(path)` - Untrack zip
- `project.hasServerZipFiles()` - Check if any exist
- `updateCleanupButtonVisibility(boolean)` - Show/hide button

### SSH Commands Used
```bash
# Check existence and get size
test -f <path> && stat -c '%s' <path> || echo 'NOT_FOUND'

# Delete file
rm -f <path> && echo 'DELETED' || echo 'FAILED'
```

## Benefits

1. **Prevents Junk Accumulation**: No temporary files left on server
2. **Automatic Management**: Normal operations require no user intervention
3. **Visual Feedback**: Button only appears when cleanup is needed
4. **Safe Operations**: Verification and confirmation before deletion
5. **Complete Audit Trail**: Purpose and timestamp for each file
6. **Error Recovery**: Handles missing files gracefully

## Future Enhancements (Optional)

1. **Auto-cleanup on startup**: Check for old zip files on project load
2. **Age-based warnings**: Alert if zip files are older than X days
3. **Bulk operations**: Clean all projects at once
4. **Size monitoring**: Alert if total zip file size exceeds threshold
5. **Scheduled cleanup**: Periodic automatic cleanup of old files

## Testing Checklist

- [x] Zip tracking during normal setup
- [x] Automatic deletion after successful download
- [x] Cleanup button visibility (show/hide)
- [x] Dialog opens and scans server
- [x] File existence verification
- [x] Missing files auto-removed from tracking
- [x] Manual deletion via dialog
- [x] Progress indicators work
- [x] Confirmation dialog appears
- [x] Project data persists after cleanup
- [x] Multiple zip files handling
- [x] Error handling for SSH failures
- [x] Button updates after cleanup

## Files Changed Summary

### New Files (1)
- `ZipCleanupDialog.java` - Cleanup dialog implementation

### Modified Files (4)
- `ProjectEntity.java` - Added zip tracking fields and methods
- `SFTPDownloadAndUnzip.java` - Added tracking on create/delete
- `ProjectDetailsController.java` - Added cleanup button handler
- `project-details.fxml` - Added cleanup button to UI

## Conclusion

The zip cleanup feature provides a complete solution for managing temporary server files, with automatic tracking, manual cleanup capability, and a professional user interface. The implementation follows best practices for error handling, user feedback, and data persistence.

