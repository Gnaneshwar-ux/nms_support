# File Restructuring Script
$baseDir = "src\main\java\com\nms\support\nms_support"

# Define file movements
$movements = @{
    # Build Feature
    "controller\BuildAutomation.java" = "features\build\controller\BuildAutomation.java"
    
    # Datastore Feature
    "controller\DatastoreDumpController.java" = "features\datastore\controller\DatastoreDumpController.java"
    
    # JAR Decompiler Feature
    "controller\JarDecompilerController.java" = "features\jardecompiler\controller\JarDecompilerController.java"
    "controller\EnhancedJarDecompilerController.java" = "features\jardecompiler\controller\EnhancedJarDecompilerController.java"
    
    # VPN Feature
    "controller\VpnController.java" = "features\vpn\controller\VpnController.java"
    
    # Process Monitor Feature
    "controller\ProcessMonitorController.java" = "features\processmonitor\controller\ProcessMonitorController.java"
    
    # Project Details Feature
    "controller\ProjectDetailsController.java" = "features\projectdetails\controller\ProjectDetailsController.java"
    
    # Entity Card (part of project details)
    "controller\EntityCardController.java" = "features\projectdetails\controller\EntityCardController.java"
}

Write-Host "File movements defined: $($movements.Count) files" -ForegroundColor Green
Write-Host "This script is for reference only. Files will be moved using individual commands." -ForegroundColor Yellow

