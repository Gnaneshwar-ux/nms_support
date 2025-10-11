# Process PID Management Implementation Summary

## Overview
This implementation adds comprehensive PID tracking and killing functionality to prevent demon processes from causing issues during restart, build, and setup operations.

## Problem Statement
Sometimes before creating log files, a few processes go to demon state which causes issues when:
- Restarting applications
- Building projects (especially with `ant clean config`)
- Setting up product folders

## Solution Components

### 1. ProcessPidManager Service
**File**: `src/main/java/com/nms/support/nms_support/service/buildTabPack/ProcessPidManager.java`

A new service that manages Process IDs (PIDs) for exe applications by:
- Recording PIDs when processes start
- Storing PID information in a file (`.nms_running_pids.txt`) in the exe directory
- Tracking app name, project name, PID, and timestamp
- Providing methods to kill processes by app or kill all processes
- Cleaning up dead/old processes automatically

**Key Methods**:
- `recordPid()`: Records PID when an exe starts
- `killProcessesForApp()`: Kills specific app processes (for stop/restart)
- `killAllProcesses()`: Kills all exe processes (for setup/build)
- `cleanupOldEntries()`: Removes stale entries

### 2. Enhanced Process Killing in ControlApp
**File**: `src/main/java/com/nms/support/nms_support/service/buildTabPack/ControlApp.java`

#### Changes Made:

**A. Process Recording (start method)**
- After successfully starting an exe, records the PID using `ProcessPidManager.recordPid()`
- Logs success/failure of PID recording

**B. Enhanced Stop/Restart Logic (stopProject method)**
- **STEP 1**: Use existing log-based killing (maintains JNLP filtering)
  - Parse log files to find running processes
  - Kill only EXE-launched processes (not JNLP)
  - Track killed PIDs in a Set
- **STEP 2**: Kill remaining processes from PID file
  - Uses `ProcessPidManager.killProcessesForApp()`
  - Only kills the selected app
  - Skips PIDs already killed in Step 1
  - Removes killed PIDs from tracking file
- **STEP 3**: Summary reporting

**C. Build Process Enhancement (build method)**
- For `ant clean config` only:
  - **PRE-BUILD CLEANUP** step added before build starts
  - Kills ALL exe processes for the project (irrespective of app name)
  - Uses same 2-step approach:
    1. Kill from logs (excluding JNLP)
    2. Kill remaining from PID file
  - Reports total processes killed

### 3. Setup Service Integration
**File**: `src/main/java/com/nms/support/nms_support/service/globalPack/SetupService.java`

#### Changes Made:

**A. Process Cleanup Step**
- Added after environment validation, before any product installation
- Only runs for product-containing setup modes:
  - `PATCH_UPGRADE`
  - `PRODUCT_ONLY`
  - `FULL_CHECKOUT`
  - `PROJECT_AND_PRODUCT_FROM_SERVER`
- Excluded from project-only modes:
  - `PROJECT_ONLY_SVN`
  - `PROJECT_ONLY_SERVER`

**B. killAllExeProcesses Method**
- Comprehensive process killing before setup
- Two-step approach:
  1. Scan log files and kill matching EXE processes
  2. Kill remaining processes from PID tracking file
- Detailed progress logging through ProcessMonitor
- Doesn't fail setup if process killing has errors (graceful degradation)

## Behavior Summary

### Scenario 1: Stop/Restart Selected App
**When**: User clicks Stop or Restart button
**Behavior**:
1. Find and kill processes from log files (selected app only, EXE only)
2. Find and kill remaining processes from PID file (selected app only)
3. Remove killed processes from PID tracking file
4. JNLP processes are NOT killed (existing behavior maintained)

### Scenario 2: Build with Ant Clean Config
**When**: User selects "Ant clean config" build type
**Behavior**:
1. **Before build starts**: Kill ALL exe processes for project
   - Find and kill from log files (all apps, EXE only)
   - Find and kill remaining from PID file (all apps)
2. Proceed with build

### Scenario 3: Product Setup/Installation
**When**: User performs any product-containing setup mode
**Behavior**:
1. **First step after validation**: Kill ALL exe processes
   - Find and kill from log files (all apps, EXE only)
   - Find and kill remaining from PID file (all apps)
2. Continue with product installation steps

### Scenario 4: Regular Build (Ant Config)
**When**: User selects "Ant config" build type (not clean)
**Behavior**:
- No process killing (existing behavior)
- Build proceeds normally

## Key Features

### 1. Two-Stage Killing Logic
- **Stage 1**: Log-based killing (existing proven method)
- **Stage 2**: PID-file-based killing (catches demon processes)
- Prevents duplicate killing (Stage 2 skips PIDs killed in Stage 1)

### 2. JNLP Protection
- Existing JNLP detection and filtering is preserved
- Only EXE-launched processes are killed
- JNLP processes continue to run safely

### 3. Automatic Cleanup
- Dead processes are automatically removed from PID file
- Old entries (>24 hours) can be cleaned up
- File remains clean and accurate

### 4. Error Handling
- Graceful failure handling (doesn't break main operations)
- Detailed logging at all stages
- User-friendly progress messages

### 5. File Format
**File Location**: `<exePath>/.nms_running_pids.txt`
**Format**:
```
# NMS Support - Running Process PIDs
# Format: appName|projectName|pid|timestamp
# This file is automatically managed - do not edit manually

WebWorkspace.exe|MyProject|12345|1728567890123
ManageTool.exe|MyProject|12346|1728567890456
```

## Testing Recommendations

1. **Test Stop/Restart**:
   - Start an exe
   - Immediately click stop before log file is created
   - Verify process is killed

2. **Test Ant Clean Config**:
   - Start multiple exes
   - Run ant clean config
   - Verify all exes are killed before build

3. **Test Product Setup**:
   - Start multiple exes
   - Run any product-containing setup
   - Verify all exes are killed at the start

4. **Test JNLP Protection**:
   - Start JNLP processes
   - Perform stop/build/setup operations
   - Verify JNLP processes remain running

5. **Test Multiple Apps**:
   - Start WebWorkspace.exe and ManageTool.exe
   - Click stop for only WebWorkspace
   - Verify only WebWorkspace is killed, ManageTool continues

## Benefits

1. **Eliminates Demon Process Issues**: Catches processes that haven't created log files yet
2. **Maintains Existing Behavior**: All existing JNLP and log-based logic is preserved
3. **Comprehensive Coverage**: Handles stop, restart, build, and setup scenarios
4. **Selective vs. Broad Killing**: Stop/restart kills selected app only; build/setup kills all
5. **Self-Cleaning**: PID tracking file automatically removes dead processes
6. **Non-Intrusive**: Doesn't fail operations if process killing has issues

## Implementation Notes

- Uses WMIC to find PIDs by process name and path
- Falls back gracefully if PID recording/killing fails
- All operations are Windows-specific (uses `taskkill` command)
- Compatible with existing log-based process management
- Minimal performance impact (PID operations are fast)

## Future Enhancements (Optional)

1. Add cleanup method that runs periodically in background
2. Add UI indicator showing tracked processes
3. Add manual "kill all" button in UI
4. Support for other platforms (Linux/Mac) if needed
5. Enhanced PID detection using multiple methods

