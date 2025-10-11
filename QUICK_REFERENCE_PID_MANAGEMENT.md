# Quick Reference: Process PID Management

## What Was Implemented

### ✅ PID Tracking
- When you start an exe (e.g., WebWorkspace.exe), the system now records its PID in a file
- File location: `<ProductFolder>/.nms_running_pids.txt`
- Tracks: app name, project name, PID, timestamp

### ✅ Enhanced Process Killing

#### 1. Stop/Restart (Selected App Only)
**Location**: Build Automation Tab → Stop/Restart buttons

**What happens**:
1. Searches log files for processes ✓
2. Kills found processes (EXE only, not JNLP) ✓
3. Checks PID tracking file for missed processes ✓
4. Kills demon processes that don't have logs yet ✓
5. Removes killed processes from tracking file ✓

**Result**: Only the selected app is killed

#### 2. Build with Ant Clean Config
**Location**: Build Automation Tab → Build button → "Ant clean config"

**What happens**:
1. **BEFORE BUILD**: Kills ALL exe processes for the project
   - Searches log files ✓
   - Checks PID tracking file ✓
   - Removes from tracking ✓
2. Proceeds with build

**Result**: All exe processes for the project are killed before build

#### 3. Product Setup/Installation
**Location**: Project Details Tab → Setup button → Any product-containing mode

**Modes that kill processes**:
- Patch Upgrade
- Product Only
- Full Checkout
- Project and Product from Server

**What happens**:
1. **FIRST STEP**: Kills ALL exe processes for the project
   - Searches log files ✓
   - Checks PID tracking file ✓
   - Removes from tracking ✓
2. Continues with setup

**Result**: Clean slate before product installation

## File Structure

### PID Tracking File
```
Location: C:\YourProductFolder\.nms_running_pids.txt

Content:
# NMS Support - Running Process PIDs
# Format: appName|projectName|pid|timestamp
WebWorkspace.exe|MyProject|12345|1728567890123
ManageTool.exe|MyProject|12346|1728567890456
```

## Log Messages to Look For

### When Starting App
```
SUCCESS: Application process started successfully
INFO: Application is now running in background
 → Process PID recorded for tracking
```

### When Stopping App
```
STEP 1: Searching for processes via log files...
INFO: Found X running processes in logs
SUCCESS: Process stopped successfully - PID: XXXXX
STEP 2: Checking PID tracking file for missed processes...
INFO: Killed X additional processes from PID tracking (demon processes)
SUCCESS: Stop operation completed
```

### When Building with Ant Clean Config
```
PRE-BUILD CLEANUP: Killing all running exe processes for project...
  → Killed process from log: PID XXXXX
  → Killed X additional processes from PID tracking
PRE-BUILD CLEANUP: Killed X exe process(es) before build
```

### When Running Setup
```
Step: Killing running exe processes
Scanning X log files...
Killed process from log: PID XXXXX
Killed X additional processes from PID tracking (demon processes)
Process cleanup complete: X from logs, X from PID tracking
Status: All exe processes terminated successfully
```

## Troubleshooting

### Problem: Process not killed during stop
**Solution**: Check if:
1. The process was started through the tool (should have PID recorded)
2. The `.nms_running_pids.txt` file exists in product folder
3. Check log output for warnings

### Problem: PID not recorded when starting
**Solution**: 
1. Check log for "WARNING: Could not record PID"
2. Verify product folder path is correct
3. Check write permissions on product folder

### Problem: Old entries in PID file
**Solution**: 
- File automatically cleans dead processes
- Manually delete `.nms_running_pids.txt` to reset (safe to do)

## Important Notes

### ✅ JNLP Processes Are Safe
- JNLP-launched processes are **NEVER** killed
- Existing JNLP filtering logic is maintained
- Only EXE-launched processes are affected

### ✅ Selective vs. Broad Killing
- **Stop/Restart**: Kills selected app only
- **Ant Clean Config**: Kills ALL exe apps for project
- **Product Setup**: Kills ALL exe apps for project

### ✅ Two-Stage Approach
- Stage 1: Log-based killing (existing proven method)
- Stage 2: PID-file-based killing (catches demon processes)
- No duplicate killing (Stage 2 skips PIDs killed in Stage 1)

### ✅ Error Handling
- Process killing failures don't break main operations
- Warnings are logged but operations continue
- Graceful degradation if PID file is unavailable

## Testing Checklist

- [ ] Start exe → Check PID recorded in log
- [ ] Stop immediately (before log file) → Process killed?
- [ ] Start multiple exes → Stop one → Only that one killed?
- [ ] Ant clean config → All exes killed before build?
- [ ] Product setup → All exes killed at start?
- [ ] JNLP process running → Remains safe after operations?

## Files Modified

1. `ProcessPidManager.java` - New service for PID management
2. `ControlApp.java` - Enhanced stop and build logic
3. `SetupService.java` - Added process cleanup to setup

## Questions?

Check the detailed implementation document: `PROCESS_PID_MANAGEMENT_IMPLEMENTATION.md`

