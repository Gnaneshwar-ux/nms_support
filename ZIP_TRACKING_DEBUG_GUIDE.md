# Zip Tracking Debug Guide - How to Test & Verify

## Why Your Zip Wasn't Tracked

Looking at your logs and projects.json:

```json
"serverZipFiles" : [ ]  ← Empty!
```

### The setup was cancelled during VALIDATION, not during zip creation!

Your log shows:
```
Line 1007: ✓ Project data saved in finally block (ensures zip tracking is persisted)
```

But there's no log for:
```
✓ Tracked zip file in project entity: /tmp/xxx.zip  ← MISSING!
```

**This means:** Setup never reached the zip creation step. It was cancelled too early!

---

## Setup Flow & When Tracking Happens

```
Step 1: Validation                    ← You got here ✓
Step 2: Environment setup              
Step 3: Process cleanup
Step 4: ZIP CREATION                   ← Tracking happens here! ⚠️
        └─ createZipFile()
           └─ project.addServerZipFile()  ← This line adds to JSON
Step 5: Download
Step 6: Extraction
```

**You cancelled at Step 1-3, before zip was created!**

---

## Complete Test Procedure

### Test 1: Track a Zip File (Proper Cancellation)

1. **Start Setup:**
   - Select project: VM
   - Click "Local Setup / Upgrade"
   - Choose any mode that downloads from server (e.g., "PROJECT_AND_PRODUCT_FROM_SERVER")

2. **Wait for Zip Creation:**
   - **Don't cancel yet!**
   - Wait until you see: `"Zipping files... (X files processed)"`
   - This confirms zip creation started

3. **Cancel During Zipping:**
   - Click "Cancel" button
   - Wait for cleanup to complete

4. **Verify Tracking:**
   - Check application logs for:
     ```
     ✓ Tracked project zip file in entity: /tmp/nms_project_XXX.zip
     ✓ Project data saved in finally block
     ```
   - Check projects.json:
     ```bash
     type "C:\Users\Gnaneshwar\Documents\nms_support_data\projects.json" | findstr /A /C:"serverZipFiles"
     ```
   - Should show: `"serverZipFiles" : [ { "path": "/tmp/nms_project_XXX.zip", ... } ]`

5. **Verify Button:**
   - Go to Project Configuration tab
   - **Cleanup button should be VISIBLE**

---

## Which Setup Modes Create Zips?

| Setup Mode | Creates Zip? | Service Used | Tracking Added? |
|------------|--------------|--------------|-----------------|
| FULL_CHECKOUT | ✅ Yes | SFTPDownloadAndUnzip | ✅ Yes |
| PRODUCT_ONLY | ✅ Yes | SFTPDownloadAndUnzip | ✅ Yes |
| PROJECT_AND_PRODUCT_FROM_SERVER | ✅ Yes (2 zips!) | Both services | ✅ Yes |
| PROJECT_ONLY_SVN | ❌ No | SVN only | N/A |
| PROJECT_ONLY_SERVER | ✅ Yes | ServerProjectService | ✅ Yes (just added) |
| HAS_JAVA_MODE | ❌ No | Skips download | N/A |
| PATCH_UPGRADE | ❌ No | No download | N/A |

---

## How to Manually Add a Zip for Testing

If you want to test with an existing zip file on the server:

### Option A: Use ZipCleanupDialog's Manual Add Feature (Future Enhancement)
Not implemented yet - would need to add "Add File" button

### Option B: Manually Edit projects.json

1. **Stop the application**

2. **Edit projects.json:**
```json
"serverZipFiles" : [
    {
        "path": "/tmp/nms_project_1760192171811.zip",
        "purpose": "Manual test",
        "createdTimestamp": 1760192171811
    }
]
```

3. **Start the application**

4. **Select the project**

5. **Button should appear!**

---

## How to Find Actual Zip Files on Server

Since you have a zip on the server, let's find it:

### Option 1: Use the Cleanup Dialog (Even Without Tracking)

I'll create a quick utility method for you. Or:

### Option 2: Check Server Manually via Application

1. Open Application Management tab
2. Run custom SSH command:
   ```bash
   ls -lh /tmp/*.zip
   ```

### Option 3: Add to Tracking Manually

Once you find the zip path, edit projects.json and add it as shown above.

---

## Proper Test Sequence

### To See Everything Working:

**Test A: Full Flow with Cancellation**
```
1. Start setup (PROJECT_ONLY_SERVER mode)
2. Watch logs for: "Creating project zip on server..."
3. Wait for: "Zipping files... (100 files processed)"  ← Zip creation happening!
4. Click Cancel
5. Check logs for:
   ✓ Tracked project zip file in entity: /tmp/nms_project_XXX.zip
   ✓ Project data saved in finally block
6. Check projects.json - should have the zip
7. Cleanup button should appear
8. Click cleanup button - should show the zip
9. Delete the zip
10. Button should disappear
```

**Test B: Full Flow with Success**
```
1. Start setup (PROJECT_ONLY_SERVER mode)
2. Let it complete successfully
3. Check logs for:
   ✓ Tracked project zip file in entity: /tmp/nms_project_XXX.zip
   ✓ Remote zip file cleaned up and untracked
   ✓ Project data saved in finally block
4. Check projects.json - serverZipFiles should be empty
5. Cleanup button should NOT appear
```

---

## Debug Commands for You

### Check Current Tracked Zips:
```powershell
Get-Content "C:\Users\Gnaneshwar\Documents\nms_support_data\projects.json" | Select-String -Pattern "serverZipFiles" -Context 3,3
```

### Find All Zips in JSON:
```powershell
Get-Content "C:\Users\Gnaneshwar\Documents\nms_support_data\projects.json" | ConvertFrom-Json | ForEach-Object { $_.projects | ForEach-Object { Write-Host "Project: $($_.name)"; Write-Host "Zips: $($_.serverZipFiles.Count)" } }
```

### Verify Application Loads JSON Correctly:
Look in logs for:
```
Cleanup button visibility updated: true   ← Should be true if zips exist
```

---

## What I Just Fixed

I added tracking to **ServerProjectService** (lines 247-248):
```java
// CRITICAL: Track immediately in project entity
project.addServerZipFile(zipPath, "Project download - " + sshManager.getPurpose());
logger.info("✓ Tracked project zip file in entity: " + zipPath);
```

Now **BOTH** services track zips:
- ✅ SFTPDownloadAndUnzip (for product/java downloads)
- ✅ ServerProjectService (for project downloads)

---

## Key Takeaway

**The zip file on your server is likely an OLD zip from before tracking was implemented.**

To properly test:
1. **Start a NEW setup**
2. **Wait until you see "Zipping files..."** (this confirms zip creation started)
3. **Then cancel**
4. **Check for the log:** `"✓ Tracked project zip file in entity"`
5. **Verify button appears**

Try it now with a fresh setup! 🚀

