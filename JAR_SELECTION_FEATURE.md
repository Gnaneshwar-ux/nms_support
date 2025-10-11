# Smart JAR Selection with Decompilation Info

## Overview
JAR selection is now intelligently stored in the decompilation info file (`.decompilation_info`). The system maintains user's JAR selections and automatically restores them when decompiled data exists, or reverts to defaults for fresh decompilations.

## Key Behavior

### **Scenario 1: Has Decompiled Data**
✅ System automatically restores saved JAR selection
- User's previous choices are preserved
- Selection is loaded from `.decompilation_info` file
- No manual selection needed

### **Scenario 2: No Decompiled Data**
✅ System uses default selection
- Only `nms_*.jar` files are selected
- Clean slate for new decompilations
- User can customize as needed

## Storage Location

### Single File Approach
All decompilation metadata is stored in one file:
```
%USERPROFILE%\Documents\nms_support_data\extracted\{projectName}\.decompilation_info
```

### File Format
```
1696876543210
v5.2.1_BUILD123
# JAR Selection
nms_common.jar=true
nms_server.jar=true
nms_client.jar=false
nms_web.jar=true
third_party.jar=false
```

**Format explanation:**
- **Line 1**: Timestamp of decompilation
- **Line 2**: Project code / CVS tag
- **Line 3+**: JAR selection (jarname=true/false)

## Technical Implementation

### DecompilationInfo Class
```java
class DecompilationInfo {
    long timestamp;              // When decompiled
    String projectCode;          // CVS tag / version
    Map<String, Boolean> jarSelection;  // JAR selections
}
```

### Save Logic
Selection is saved **only** after successful decompilation:
```java
// After decompilation completes:
saveDecompilationInfo() {
    - Save timestamp
    - Save project code
    - Save all JAR selections (selected and unselected)
}
```

### Load Logic
Selection is loaded when JAR directory is opened:
```java
loadJarFiles() {
    - Read .decompilation_info file
    - If file exists and has JAR selection data:
        → Use saved selections
    - Else:
        → Use default selections (nms_*.jar only)
}
```

## Benefits

### **1. Simpler Storage**
- ✅ Single file per project
- ✅ Co-located with decompiled data
- ✅ Automatically cleaned up with decompiled data
- ❌ No separate config files needed

### **2. Guaranteed Consistency**
- ✅ Selection always matches decompiled JARs
- ✅ Can't have stale selection data
- ✅ Selection is saved atomically with decompilation

### **3. Better Performance**
- ✅ Fewer file I/O operations
- ✅ Single file read/write
- ✅ No hash calculations needed

### **4. Easier Maintenance**
- ✅ Clear relationship: decompilation → selection
- ✅ Delete decompiled data = delete selection
- ✅ No orphaned selection files

## User Experience

### **First Time (No Decompilation)**
```
1. User opens project
2. System checks for .decompilation_info
3. File doesn't exist
4. Default selection applied: nms_*.jar files
5. User can decompile or adjust selection
```

### **Returning User (Has Decompilation)**
```
1. User opens project  
2. System reads .decompilation_info
3. JAR selection restored automatically
4. User sees their previous selection
5. Ready to use immediately
```

### **After Decompilation**
```
1. User selects specific JARs
2. User clicks Decompile
3. Decompilation completes successfully
4. Selection saved automatically in .decompilation_info
5. Next time: selection is restored
```

### **New Build/Version**
```
1. User clears old decompiled data
2. .decompilation_info is deleted
3. System reverts to default selection
4. Clean start for new version
```

## Example File Content

### Complete `.decompilation_info` File
```
1730409600000
v5.3.0_RELEASE_2025
# JAR Selection
nms_common.jar=true
nms_server.jar=true
nms_client.jar=true
nms_web.jar=false
nms_api.jar=true
nms_batch.jar=false
third_party_lib.jar=false
```

### Minimal `.decompilation_info` File (Backward Compatible)
```
1730409600000
v5.3.0_RELEASE_2025
```
*Note: Without JAR selection section, defaults are used*

## Comparison with Previous Approach

### **Old: Separate Selection Files**
```
Documents\nms_support_data\
└── config\
    └── jar_selections\
        ├── ProjectA_12345678.txt    # Separate files
        ├── ProjectA_87654321.txt    # Per folder path
        └── ProjectB_11223344.txt    # Hash-based names
```

### **New: Integrated with Decompilation Info**
```
Documents\nms_support_data\
└── extracted\
    └── ProjectName\
        └── .decompilation_info      # Single file
```

### **Advantages of New Approach**
| Aspect | Old Approach | New Approach |
|--------|-------------|--------------|
| **Storage** | Multiple files | Single file |
| **Location** | Separate config dir | With decompiled data |
| **Consistency** | Can diverge | Always in sync |
| **Cleanup** | Manual | Automatic |
| **Complexity** | Path hashing | Direct |

## Migration

### **Automatic Migration**
- Old selection files are ignored (no migration needed)
- On first decompilation with new version:
  - Uses defaults or existing `.decompilation_info`
  - Saves selection to `.decompilation_info`
- Old selection files can be safely deleted

### **No Data Loss**
- If `.decompilation_info` exists: selection is preserved
- If only old files exist: defaults are used (safe fallback)
- User simply re-decompiles with desired selection once

## Troubleshooting

### **Selection Not Restored**
**Check:**
1. Does `.decompilation_info` file exist?
2. Does it have JAR selection lines (line 3+)?
3. Are JAR names exactly matching current JARs?

**Solution:** Simply decompile again with desired selection

### **Want to Reset Selection**
**Options:**
1. Delete `.decompilation_info` file
2. Delete entire extracted folder
3. Manually change selections and re-decompile

### **JAR Added/Removed**
**Behavior:**
- New JARs: Use default selection (nms_* = true)
- Missing JARs: Selection entry ignored
- Existing JARs: Saved selection applied

## File Lifecycle

```
Project Created
    ↓
No .decompilation_info exists
    ↓
Default selection (nms_*.jar)
    ↓
User decompiles JARs
    ↓
.decompilation_info created/updated
    ↓
Selection saved
    ↓
User returns later
    ↓
Selection restored automatically
    ↓
User can re-decompile or clear data
```

## Conclusion

The integrated approach provides a simpler, more reliable way to maintain JAR selections by storing them directly in the decompilation info file. This ensures consistency, reduces complexity, and provides a better user experience with automatic cleanup and guaranteed sync between decompiled data and selection state.

