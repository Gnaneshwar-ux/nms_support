package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.userdata.LogManager;
import com.nms.support.nms_support.service.userdata.ProjectManager;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.ManageFile;
import com.nms.support.nms_support.service.globalPack.JavaEnvUtil;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;

import com.nms.support.nms_support.service.globalPack.ProcessSelectionDialog;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ControlApp {
    private static final Logger logger = LoggerUtil.getLogger();
    private final BuildAutomation buildAutomation;
    private final LogManager logManager;
    private ProjectManager projectManager;
    private Map<String, String> jpsMap;

    public ControlApp(BuildAutomation buildAutomation, LogManager logManager, ProjectManager projectManager){
        this.buildAutomation = buildAutomation;
        this.logManager=logManager;
        this.projectManager=projectManager;
    }

    public void refreshLogNames() {
        buildAutomation.clearLog();
        buildAutomation.appendTextToLog("Reloading projects from logs..");
        File[] files = getLogFiles("");
        if (files == null || files.length == 0) {
            buildAutomation.appendTextToLog("No projects found in the system.");
            return;
        }
        logManager.clearAll();
        for (File f : files) {
            try {
                Map<String, String> m = parseLog(f);
                if (m != null) {
                    LogEntity l = new LogEntity(m.get("PROJECT"), m.get("VERSION"));
                    if (!logManager.contains(l)) {
                        logManager.addLog(l);
                        buildAutomation.appendTextToLog("added project - " + m.get("PROJECT_KEY"));
                    }
                }

            } catch (IOException ex) {
                buildAutomation.appendTextToLog("Reloading exited with exception - " + ex.toString());
            }
            logManager.saveData();
        }
        buildAutomation.appendTextToLog("Reload completed successfully");
    }

    //returns the list of log files as array in sorted order
    public File[] getLogFiles(String app) {
        String user = System.getProperty("user.name");
        //propPath = "C:/Users/" + user + "/Documents";
        File directory = null;
        try {
            directory = new File(getLogDirectoryPath());
        } catch (Exception e) {
            buildAutomation.appendTextToLog(e.toString());
            return null;
        }
        buildAutomation.appendTextToLog("Searching Logs at : "+directory.getAbsolutePath());

        File[] logfiles = null;
        if (directory.exists()) {
            logfiles = directory.listFiles((dir, name) -> name.toLowerCase().contains(app.toLowerCase()) && new File(dir, name).isFile());
        }

        if (logfiles != null) {
            Arrays.sort(logfiles, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    long lastModified1 = file1.lastModified();
                    long lastModified2 = file2.lastModified();

                    if (lastModified1 < lastModified2) {
                        return 1; // For descending order
                    } else if (lastModified1 > lastModified2) {
                        return -1; // For descending order
                    } else {
                        return 0;
                    }
                }
            });
        } else {
            buildAutomation.appendTextToLog("WARNING: No log files found in the system.");
            buildAutomation.appendTextToLog("DETAILS: Before using this application you must run the WebWorkspace at least once manually.");
            buildAutomation.appendTextToLog("RESOLUTION: Start the WebWorkspace application manually to generate log files");
            return null;
        }

        if (logfiles.length <= 100) {
            return logfiles;
        }

        return Arrays.copyOf(logfiles,100);
    }

    //This method parse the log files and create map for required fields
    public static Map<String, String> parseLog(File file) throws FileNotFoundException, IOException {

        if (file == null) {
            return null;
        }

        // Use try-with-resources to ensure proper file handle cleanup
        try (FileReader fr = new FileReader(file);
             BufferedReader br = new BufferedReader(fr)) {
            
            String pidLine = "";
            String projectLine = "";
            String launcher = "";
            String version = "";
            String username = "";
            String line;
            boolean a = false, b = false, c = false, d = false;
            while ((line = br.readLine()) != null) {

                //System.out.println(line);
                if (line.startsWith("PID")) {
                    pidLine = line;
                    a = true;
                }
                if (line.startsWith("CLIENT_TOOL_PROJECT_NAME")) {
                    projectLine = line.split("=")[1].trim().replaceAll("\"", "");
                    b = true;
                }

                if(line.startsWith("CLIENT_TOOL_CVS_TAG")){
                    version = line.split("=")[1].trim().replaceAll("\"", "");
                    c = true;
                }

                if (line.startsWith("USERNAME")) {
                    username = line;
                    d = true;
                }

                if (line.contains("/version.xml")) {
                    if (!line.contains("working")) {
                        launcher = "JNLP";
                    } else {
                        launcher = "EXE";
                    }
                    c = true;
                }
                if (a && b && c && d) {
                    break;
                }
            }

            if (!(a && b)) {
                return null;
            }
            String time;
            long lastModifiedTimestamp = file.lastModified();
            Date lastModifiedDate = new Date(lastModifiedTimestamp);

            SimpleDateFormat outputDateFormat = new SimpleDateFormat("hh:mm a dd-MM-yyyy");
            time = outputDateFormat.format(lastModifiedDate).replaceAll("am", "AM").replaceAll("pm", "PM");

            Map<String, String> mp = new HashMap<>();

            mp.put("PID", pidLine.split("=")[1].trim());
            mp.put("PROJECT", projectLine);
            mp.put("VERSION", version);
            mp.put("PROJECT_KEY", projectLine+"#"+version);
            mp.put("USER", username.equals("") ? "N/A" : username.split("=")[1].trim().replaceAll("\"", ""));
            mp.put("TIME", time);
            mp.put("FILE", file.getName());
            mp.put("LAUNCHER", launcher);
            return mp;
        }
    }

    public static Thread restartThread = null;

    public Task<Boolean> build(ProjectEntity project, String typeSelected) throws InterruptedException {
        logger.info("Starting build process for project: " + project.getName() + " with build type: " + typeSelected);
        String path = "";
        String jconfigPath = project.getJconfigPathForBuild();
        
        logger.info("BUILD PROCESS DETAILS - Project: " + project.getName() + ", Type: " + typeSelected + 
                   ", Folder: " + (project.getProjectFolderPath() != null ? project.getProjectFolderPath() : "NOT SET") +
                   ", JConfig: " + (jconfigPath != null ? jconfigPath : "NOT SET"));
        
        buildAutomation.appendTextToLog("BUILD PROCESS DETAILS:");
        buildAutomation.appendTextToLog("• Project Name: " + project.getName());
        buildAutomation.appendTextToLog("• Build Type: " + typeSelected);
        buildAutomation.appendTextToLog("• Project Folder: " + (project.getProjectFolderPath() != null ? project.getProjectFolderPath() : "NOT SET"));
        buildAutomation.appendTextToLog("• JConfig Path: " + (jconfigPath != null ? jconfigPath : "NOT SET"));
        
        if (jconfigPath == null || jconfigPath.equals("")) {
            logger.severe("ERROR: JConfig path configuration missing for project: " + project.getName());
            buildAutomation.appendTextToLog("ERROR: JConfig path configuration missing");
            buildAutomation.appendTextToLog("DETAILS: JConfig path is required for build process but is null or empty");
            buildAutomation.appendTextToLog("RESOLUTION: Configure Project Folder path in Project Configuration tab under 'Project Folder' field");
            return null;
        } else {
            path = jconfigPath;
            logger.info("JConfig path validation passed for project: " + project.getName());
            buildAutomation.appendTextToLog("INFO: JConfig path validation passed");
        }

        String finalPath = path;
        String type = typeSelected.equals("Ant config") ? "ant config" : "ant clean config";
        
        logger.info("BUILD COMMAND CONFIGURATION - Command: " + type + ", Working Directory: " + finalPath + 
                   ", Environment Variable: " + (project.getNmsEnvVar() != null ? project.getNmsEnvVar() : "NOT SET"));
        
        buildAutomation.appendTextToLog("BUILD COMMAND CONFIGURATION:");
        buildAutomation.appendTextToLog("• Command: " + type);
        buildAutomation.appendTextToLog("• Working Directory: " + finalPath);
        buildAutomation.appendTextToLog("• Environment Variable: " + (project.getNmsEnvVar() != null ? project.getNmsEnvVar() : "NOT SET"));

        Task<Boolean> buildTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                // Store build output for potential failure display (declared outside try block for exception handling)
                StringBuilder buildOutput = new StringBuilder();
                
                try {
                    logger.info("Step 1: Initializing build environment for project: " + project.getName());
                    buildAutomation.appendTextToLog("Step 1: Initializing build environment...");
                    
                    // Validate working directory
                    File workingDir = new File(finalPath);
                    if (!workingDir.exists()) {
                        logger.severe("ERROR: Working directory does not exist: " + finalPath);
                        buildAutomation.appendTextToLog("  → ERROR: Working directory does not exist: " + finalPath);
                        buildAutomation.appendTextToLog("  → Resolution: Verify JConfig path in Project Configuration");
                        return false;
                    }
                    if (!workingDir.isDirectory()) {
                        logger.severe("ERROR: Working directory is not a valid directory: " + finalPath);
                        buildAutomation.appendTextToLog("  → ERROR: Working directory is not a valid directory: " + finalPath);
                        buildAutomation.appendTextToLog("  → Resolution: Verify JConfig path points to a directory, not a file");
                        return false;
                    }
                    logger.info("Working directory validated: " + finalPath);
                    buildAutomation.appendTextToLog("  → Working directory validated: " + finalPath);
                    
                    // Check for build.xml file
                    File buildXml = new File(finalPath, "build.xml");
                    if (!buildXml.exists()) {
                        logger.warning("WARNING: build.xml not found in working directory: " + finalPath);
                        buildAutomation.appendTextToLog("  → WARNING: build.xml not found in working directory");
                        buildAutomation.appendTextToLog("  → This may cause build failures if Ant requires this file");
                    } else {
                        logger.info("build.xml found in working directory: " + finalPath);
                        buildAutomation.appendTextToLog("  → build.xml found in working directory");
                    }
                    
                    
                    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", type + " && exit 0 || exit 1");
                    builder.directory(workingDir);
                    builder.redirectErrorStream(true); // Merge stderr into stdout so we capture all output including errors
                    Map<String, String> env = builder.environment();
                    // Apply per-project Java override if provided (log to build file only)
                    JavaEnvUtil.applyJavaOverride(env, project.getJdkHome(), msg -> addToBuildLog(project, msg, false));
                    String resolvedEnvPath = resolveAndInjectEnvVar(env, project);
                    addToBuildLog(project, "Resolved " + project.getNmsEnvVar() + " for build env: " + (resolvedEnvPath != null ? resolvedEnvPath : "null"), false);

                    // Force Ant/Javac to use our Java explicitly
                    if (project.getJdkHome() != null && !project.getJdkHome().isBlank()) {
                        File javaExe = new File(project.getJdkHome(), "bin" + File.separator + "java.exe");
                        env.put("JAVACMD", javaExe.getAbsolutePath());
                    }

                    // Prefer project-local Ant if present: <exePath>\java\ant
                    try {
                        if (project.getExePath() != null && !project.getExePath().isBlank()) {
                            File antHome = new File(project.getExePath(), "java" + File.separator + "ant");
                            File antBin = new File(antHome, "bin");
                            if (antBin.exists()) {
                                env.put("ANT_HOME", antHome.getAbsolutePath());
                                String existingPath = env.getOrDefault("Path", "");
                                String newPath = antBin.getAbsolutePath() + File.pathSeparator + existingPath;
                                env.put("Path", newPath);
                            }
                        }
                    } catch (Exception ignore) { }

                    // Diagnostics
                    try {
                        addToBuildLog(project, "=== JAVA DIAGNOSTICS (before Ant) ===", false);
                        addToBuildLog(project, "JAVA_HOME=" + env.getOrDefault("JAVA_HOME", ""), false);
                        addToBuildLog(project, "ANT_HOME=" + env.getOrDefault("ANT_HOME", ""), false);
                        addToBuildLog(project, "JAVACMD=" + env.getOrDefault("JAVACMD", ""), false);
                        addToBuildLog(project, "Path=" + env.getOrDefault("Path", ""), false);

                        ProcessBuilder diagWhere = new ProcessBuilder("cmd.exe", "/c", "where ant && where java && where javac");
                        diagWhere.directory(workingDir);
                        diagWhere.environment().putAll(env);
                        Process pw = diagWhere.start();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(pw.getInputStream()))) {
                            String line; while ((line = r.readLine()) != null) addToBuildLog(project, line, false);
                        }
                        pw.waitFor();

                        ProcessBuilder diagAnt = new ProcessBuilder("cmd.exe", "/c", "ant -diagnostics");
                        diagAnt.directory(workingDir);
                        diagAnt.environment().putAll(env);
                        diagAnt.redirectErrorStream(true);
                        Process pa = diagAnt.start();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(pa.getInputStream()))) {
                            String line; int c=0; while ((line = r.readLine()) != null && c < 80) { addToBuildLog(project, line, false); c++; }
                        }
                        pa.waitFor();

                        ProcessBuilder diagJava = new ProcessBuilder("cmd.exe", "/c", "java -version");
                        diagJava.directory(workingDir);
                        diagJava.environment().putAll(env);
                        diagJava.redirectErrorStream(true);
                        Process pj = diagJava.start();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(pj.getInputStream()))) {
                            String line; while ((line = r.readLine()) != null) addToBuildLog(project, line, false);
                        }
                        pj.waitFor();

                        ProcessBuilder diagJavac = new ProcessBuilder("cmd.exe", "/c", "javac -version");
                        diagJavac.directory(workingDir);
                        diagJavac.environment().putAll(env);
                        Process pc = diagJavac.start();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(pc.getInputStream()))) {
                            String line; while ((line = r.readLine()) != null) addToBuildLog(project, line, false);
                        }
                        pc.waitFor();

                        // Verify that the child process will see the resolved env var value
                        String nmsVarName = project.getNmsEnvVar();
                        if (nmsVarName != null && !nmsVarName.isBlank()) {
                            ProcessBuilder diagEnvVar = new ProcessBuilder("cmd.exe", "/c", "echo %" + nmsVarName + "%");
                            diagEnvVar.directory(workingDir);
                            diagEnvVar.environment().putAll(env);
                            diagEnvVar.redirectErrorStream(true);
                            Process pev = diagEnvVar.start();
                            try (BufferedReader r = new BufferedReader(new InputStreamReader(pev.getInputStream()))) {
                                String line; while ((line = r.readLine()) != null) addToBuildLog(project, nmsVarName + "=" + line, false);
                            }
                            pev.waitFor();
                        }

                        addToBuildLog(project, "=== END JAVA DIAGNOSTICS ===", false);
                    } catch (Exception diagEx) {
                        addToBuildLog(project, "WARNING: Diagnostics failed: " + diagEx.getMessage(), false);
                    }
                    
                    logger.info("Step 2: Starting build process for project: " + project.getName());
                    buildAutomation.appendTextToLog("Step 2: Starting build process...");
                    Process process = builder.start();

                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String s;
                    logger.info("SUCCESS: Build process started successfully for project: " + project.getName());
                    buildAutomation.appendTextToLog("SUCCESS: Build process started successfully");
                    buildAutomation.appendTextToLog("INFO: Build execution in progress... (" + type + ")");
                    
                    logger.info("Initializing build log file for project: " + project.getName());
                    addToBuildLog(project, "=== BUILD LOG START ===", true);
                    addToBuildLog(project, "Project: " + project.getName(), false);
                    addToBuildLog(project, "Build Type: " + typeSelected, false);
                    addToBuildLog(project, "Timestamp: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), false);
                    addToBuildLog(project, "Jconfig Directory Using: " + finalPath, false);
                    addToBuildLog(project, "=== BUILD OUTPUT ===", false);
                    
                    int lineCount = 0;
                    try {
                        while ((s = stdInput.readLine()) != null) {
                            updateMessage(s);
                            addToBuildLog(project, s, false);
                            buildOutput.append(s).append("\n"); // Store output for failure display
                            lineCount++;
                            
                            // Log progress every 100 lines
                            if (lineCount % 100 == 0) {
                                logger.info("Build progress for project " + project.getName() + " - processed " + lineCount + " output lines");
                                buildAutomation.appendTextToLog("INFO: Build progress - processed " + lineCount + " output lines");
                            }
                        }
                    } catch (Exception outputException) {
                        logger.warning("Exception while reading build output for project: " + project.getName() + " - " + outputException.getMessage());
                        // Continue with whatever output we managed to capture
                    }

                    int exitCode = process.waitFor();
                    
                    // After process completes, make one final attempt to read any remaining buffered output
                    // This ensures we capture output that might be written right before process termination
                    // and not yet flushed when readLine() returned null
                    try {
                        // Try to read any remaining characters that might be buffered
                        char[] buffer = new char[1024];
                        int charsRead;
                        while ((charsRead = stdInput.read(buffer)) > 0) {
                            String remaining = new String(buffer, 0, charsRead);
                            String[] remainingLines = remaining.split("\r?\n");
                            for (String line : remainingLines) {
                                if (!line.trim().isEmpty()) {
                                    updateMessage(line);
                                    addToBuildLog(project, line, false);
                                    buildOutput.append(line).append("\n");
                                    lineCount++;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors when trying to read final output - stream may already be closed
                        logger.fine("No additional output available after process completion: " + e.getMessage());
                    }
                    logger.info("Build process completed for project: " + project.getName() + " with exit code: " + exitCode + ", total lines: " + lineCount);
                    
                    // Add build completion summary to build log file
                    addToBuildLog(project, "=== BUILD PROCESS COMPLETION ===", false);
                    addToBuildLog(project, "Exit Code: " + exitCode, false);
                    addToBuildLog(project, "Total Output Lines: " + lineCount, false);
                    addToBuildLog(project, "Duration: Process completed", false);
                    
                    if (exitCode == 1) {
                        logger.severe("ERROR: BUILD PROCESS FAILED for project: " + project.getName() + " with exit code: " + exitCode);
                        
                        // Log error details to build log file first
                        addToBuildLog(project, "ERROR: BUILD PROCESS FAILED", false);
                        addToBuildLog(project, "DETAILS: Build process returned exit code 1 indicating failure", false);
                        addToBuildLog(project, "POSSIBLE CAUSES:", false);
                        addToBuildLog(project, "• Missing dependencies or libraries", false);
                        addToBuildLog(project, "• Incorrect JConfig path or configuration", false);
                        addToBuildLog(project, "• Environment variable issues", false);
                        addToBuildLog(project, "• Permission or access rights problems", false);
                        addToBuildLog(project, "RESOLUTION: Check build logs for specific error messages and verify configuration", false);
                        addToBuildLog(project, "=== BUILD LOG END ===", false);
                        
                        // Now show error info in main log (this will appear at the end)
                        buildAutomation.appendTextToLog("BUILD PROCESS COMPLETION:");
                        buildAutomation.appendTextToLog("• Exit Code: " + exitCode);
                        buildAutomation.appendTextToLog("• Total Output Lines: " + lineCount);
                        buildAutomation.appendTextToLog("• Duration: Process completed");
                        buildAutomation.appendTextToLog("ERROR: BUILD PROCESS FAILED");
                        buildAutomation.appendTextToLog("DETAILS: Build process returned exit code 1 indicating failure");
                        buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
                        buildAutomation.appendTextToLog("• Missing dependencies or libraries");
                        buildAutomation.appendTextToLog("• Incorrect JConfig path or configuration");
                        buildAutomation.appendTextToLog("• Environment variable issues");
                        buildAutomation.appendTextToLog("• Permission or access rights problems");
                        buildAutomation.appendTextToLog("RESOLUTION: Check build logs for specific error messages and verify configuration");
                        
                        // Display build command output at the end when build fails
                        logger.info("Displaying build command output for failed build - Project: " + project.getName() + ", Output length: " + buildOutput.length() + " characters");
                        buildAutomation.appendTextToLog("=== BUILD COMMAND OUTPUT (for debugging) ===");
                        buildAutomation.appendTextToLog("The following is the complete output from the build command:");
                        buildAutomation.appendTextToLog("Command executed: " + type);
                        buildAutomation.appendTextToLog("Working directory: " + finalPath);
                        buildAutomation.appendTextToLog("--- START OF BUILD OUTPUT ---");
                        
                        // Split the stored output into lines and display each line
                        String[] outputLines = buildOutput.toString().split("\n");
                        logger.info("Displaying " + outputLines.length + " lines of build output for project: " + project.getName());
                        for (String outputLine : outputLines) {
                            if (!outputLine.trim().isEmpty()) {
                                buildAutomation.appendTextToLog(outputLine);
                            }
                        }
                        
                        buildAutomation.appendTextToLog("--- END OF BUILD OUTPUT ---");
                        buildAutomation.appendTextToLog("=== END BUILD COMMAND OUTPUT ===");
                        
                        updateMessage("\nERROR: BUILD FAILED.\n");
                        return false;
                    } else {
                        logger.info("SUCCESS: BUILD PROCESS COMPLETED SUCCESSFULLY for project: " + project.getName() + " with exit code: " + exitCode);
                        
                        // Log success details to build log file first
                        addToBuildLog(project, "SUCCESS: BUILD PROCESS COMPLETED SUCCESSFULLY", false);
                        addToBuildLog(project, "DETAILS: Build process completed with exit code 0", false);
                        addToBuildLog(project, "RESULT: Project has been built successfully and is ready for deployment", false);
                        addToBuildLog(project, "=== BUILD LOG END ===", false);
                        
                        // Show success info in main log
                        buildAutomation.appendTextToLog("BUILD PROCESS COMPLETION:");
                        buildAutomation.appendTextToLog("• Exit Code: " + exitCode);
                        buildAutomation.appendTextToLog("• Total Output Lines: " + lineCount);
                        buildAutomation.appendTextToLog("• Duration: Process completed");
                        buildAutomation.appendTextToLog("SUCCESS: BUILD PROCESS COMPLETED SUCCESSFULLY");
                        buildAutomation.appendTextToLog("DETAILS: Build process completed with exit code 0");
                        buildAutomation.appendTextToLog("RESULT: Project has been built successfully. ");
                        updateMessage("\nBUILD SUCCESSFUL.\n");
                        return true;
                    }
                } catch (Exception e) {
                    logger.severe("ERROR: Exception during build process for project: " + project.getName());
                    logger.severe("Exception Type: " + e.getClass().getSimpleName());
                    logger.severe("Error Message: " + e.getMessage());
                    LoggerUtil.error(e);
                    
                    // Log exception details to build log file first
                    addToBuildLog(project, "ERROR: Exception during build process", false);
                    addToBuildLog(project, "Exception Type: " + e.getClass().getSimpleName(), false);
                    addToBuildLog(project, "Error Message: " + e.getMessage(), false);
                    
                    // Provide specific error details based on exception type
                    if (e instanceof java.io.FileNotFoundException) {
                        addToBuildLog(project, "Specific Issue: File not found", false);
                        addToBuildLog(project, "Possible causes:", false);
                        addToBuildLog(project, "• Invalid JConfig path", false);
                        addToBuildLog(project, "• Missing build.xml file", false);
                        addToBuildLog(project, "• Incorrect working directory", false);
                        addToBuildLog(project, "Resolution: Verify JConfig path and ensure build files exist", false);
                    } else if (e instanceof java.io.IOException) {
                        addToBuildLog(project, "Specific Issue: Input/Output error", false);
                        addToBuildLog(project, "Possible causes:", false);
                        addToBuildLog(project, "• File system access issues", false);
                        addToBuildLog(project, "• Permission denied errors", false);
                        addToBuildLog(project, "• Disk space issues", false);
                        addToBuildLog(project, "Resolution: Check file permissions and disk space", false);
                    } else if (e instanceof java.lang.SecurityException) {
                        addToBuildLog(project, "Specific Issue: Security/permission error", false);
                        addToBuildLog(project, "Possible causes:", false);
                        addToBuildLog(project, "• Insufficient permissions to access directory", false);
                        addToBuildLog(project, "• Antitvirus blocking execution", false);
                        addToBuildLog(project, "• Windows security restrictions", false);
                        addToBuildLog(project, "Resolution: Run as administrator or check security settings", false);
                    } else {
                        addToBuildLog(project, "Possible causes:", false);
                        addToBuildLog(project, "• Invalid JConfig path or directory", false);
                        addToBuildLog(project, "• Missing build tools (Ant, Java, etc.)", false);
                        addToBuildLog(project, "• File system access issues", false);
                        addToBuildLog(project, "• Environment configuration problems", false);
                        addToBuildLog(project, "Resolution: Verify JConfig path, build environment, and system configuration", false);
                    }
                    addToBuildLog(project, "=== BUILD LOG END ===", false);
                    
                    // Now show error info in main log (this will appear at the end)
                    buildAutomation.appendTextToLog("ERROR: Exception during build process");
                    buildAutomation.appendTextToLog("  → Exception Type: " + e.getClass().getSimpleName());
                    buildAutomation.appendTextToLog("  → Error Message: " + e.getMessage());
                    
                    // Display any captured build output at the end when exception occurs
                    if (buildOutput.length() > 0) {
                        logger.info("Displaying captured build output for exception case - Project: " + project.getName() + ", Output length: " + buildOutput.length() + " characters");
                        buildAutomation.appendTextToLog("=== BUILD COMMAND OUTPUT (captured before exception) ===");
                        buildAutomation.appendTextToLog("The following is the output captured from the build command before the exception occurred:");
                        buildAutomation.appendTextToLog("Command executed: " + type);
                        buildAutomation.appendTextToLog("Working directory: " + finalPath);
                        buildAutomation.appendTextToLog("--- START OF CAPTURED BUILD OUTPUT ---");
                        
                        // Split the stored output into lines and display each line
                        String[] outputLines = buildOutput.toString().split("\n");
                        logger.info("Displaying " + outputLines.length + " lines of captured build output for project: " + project.getName());
                        for (String outputLine : outputLines) {
                            if (!outputLine.trim().isEmpty()) {
                                buildAutomation.appendTextToLog(outputLine);
                            }
                        }
                        
                        buildAutomation.appendTextToLog("--- END OF CAPTURED BUILD OUTPUT ---");
                        buildAutomation.appendTextToLog("=== END BUILD COMMAND OUTPUT ===");
                    } else {
                        logger.info("No build output was captured before exception for project: " + project.getName());
                        buildAutomation.appendTextToLog("INFO: No build output was captured before the exception occurred");
                    }
                    
                    // Provide specific error details based on exception type
                    if (e instanceof java.io.FileNotFoundException) {
                        buildAutomation.appendTextToLog("  → Specific Issue: File not found");
                        buildAutomation.appendTextToLog("  → Possible causes:");
                        buildAutomation.appendTextToLog("    • Invalid JConfig path");
                        buildAutomation.appendTextToLog("    • Missing build.xml file");
                        buildAutomation.appendTextToLog("    • Incorrect working directory");
                        buildAutomation.appendTextToLog("  → Resolution: Verify JConfig path and ensure build files exist");
                    } else if (e instanceof java.io.IOException) {
                        buildAutomation.appendTextToLog("  → Specific Issue: Input/Output error");
                        buildAutomation.appendTextToLog("  → Possible causes:");
                        buildAutomation.appendTextToLog("    • File system access issues");
                        buildAutomation.appendTextToLog("    • Permission denied errors");
                        buildAutomation.appendTextToLog("    • Disk space issues");
                        buildAutomation.appendTextToLog("  → Resolution: Check file permissions and disk space");
                    } else if (e instanceof java.lang.SecurityException) {
                        buildAutomation.appendTextToLog("  → Specific Issue: Security/permission error");
                        buildAutomation.appendTextToLog("  → Possible causes:");
                        buildAutomation.appendTextToLog("    • Insufficient permissions to access directory");
                        buildAutomation.appendTextToLog("    • Antitvirus blocking execution");
                        buildAutomation.appendTextToLog("    • Windows security restrictions");
                        buildAutomation.appendTextToLog("  → Resolution: Run as administrator or check security settings");
                    } else {
                        buildAutomation.appendTextToLog("  → Possible causes:");
                        buildAutomation.appendTextToLog("    • Invalid JConfig path or directory");
                        buildAutomation.appendTextToLog("    • Missing build tools (Ant, Java, etc.)");
                        buildAutomation.appendTextToLog("    • File system access issues");
                        buildAutomation.appendTextToLog("    • Environment configuration problems");
                        buildAutomation.appendTextToLog("  → Resolution: Verify JConfig path, build environment, and system configuration");
                    }
                    
                    updateMessage("ERROR: Build process failed - " + e.getMessage() + "\n");
                    LoggerUtil.error(e);
                    return false;
                }
            }
        };

        buildTask.messageProperty().addListener((observable, oldValue, newValue) -> buildAutomation.appendTextToLog(newValue));

        Thread t = new Thread(buildTask);
        buildAutomation.appendTextToLog("INFO: Build thread initialized and starting...");
        t.start();
        restartThread = t;
        return buildTask;
    }

    public void start(String app, ProjectEntity project) throws IOException, InterruptedException {
        String path = "";
        
        buildAutomation.appendTextToLog("Starting application: " + app + " (project: " + project.getName() + ")");
        
        if (project.getExePath() == null || project.getExePath().equals("")) {
            buildAutomation.appendTextToLog("Executable path not configured");
            return;
        } else {
            path = project.getExePath();
            buildAutomation.appendTextToLog("Executable path set: " + path);
        }
        
        String exeFullPath = new File(path, app).getAbsolutePath();
        try {
            File f = new File(path + "/" + app);
            buildAutomation.appendTextToLog("Validating executable: " + f.getAbsolutePath());
            
            if (!f.exists()) {
                buildAutomation.appendTextToLog("Executable not found: " + f.getAbsolutePath());
                return;
            }
            
            buildAutomation.appendTextToLog("Executable validated");
            buildAutomation.appendTextToLog("Launching application...");

            // Launch off the JavaFX/UI thread to avoid UI freeze
            final String launchDir = path;
            final String launchExe = exeFullPath;
            Thread launcherThread = new Thread(() -> {
                try {
                    // Build a ProcessBuilder that starts the EXE fully detached on Windows
                    ProcessBuilder pb;
                    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                    if (isWindows) {
                        // cmd /c start "" /D "<dir>" "<exe>"
                        pb = new ProcessBuilder(
                                "cmd.exe", "/c", "start", "", "/D", launchDir, launchExe
                        );
                    } else {
                        // Non-Windows fallback: just start without inheriting IO
                        pb = new ProcessBuilder(launchExe);
                    }
                    pb.directory(new File(launchDir));

                    Map<String, String> env = pb.environment();
                    // Preserve Java/NMS dynamic injections
                    JavaEnvUtil.applyJavaOverride(env, project.getJdkHome(), buildAutomation::appendTextToLog);
                    env.put("L4J_DEBUG", "1");
                    String resolvedStartEnvPath = resolveAndInjectEnvVar(env, project);
                    buildAutomation.appendTextToLog("Resolved " + project.getNmsEnvVar() + " for launch env: " + (resolvedStartEnvPath != null ? resolvedStartEnvPath : "null"));

                    // Discard child IO to prevent potential blocking on pipes
                    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);

                    pb.start(); // do not hold reference; detached on Windows via 'start'
                    buildAutomation.appendTextToLog("Application launched");
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE, "Failed to launch NMS process", ioe);
                    buildAutomation.appendTextToLog("Failed to launch application: " + ioe.getMessage());
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Unexpected error while launching process", ex);
                    buildAutomation.appendTextToLog("Failed to launch application: " + ex.getMessage());
                }
            }, "DetachedExeLauncher");
            launcherThread.setDaemon(true);
            launcherThread.start();


        } catch (Exception e) {
            buildAutomation.appendTextToLog("Failed to start application: " + e.getMessage());
        }
    }

    public boolean stopProject(String app, ProjectEntity project) throws IOException {
        buildAutomation.appendTextToLog("PROCESS STOP DETAILS:");
        buildAutomation.appendTextToLog("• Application: " + app);
        buildAutomation.appendTextToLog("• Project: " + project.getName());
        
        List<Map<String, String>> processList = getRunningProcessList(project, app, true, false);

        if (processList == null || processList.isEmpty()) {
            buildAutomation.appendTextToLog("INFO: No running processes found for project: " + project.getName());
            buildAutomation.appendTextToLog("DETAILS: No active processes detected for the specified application and project");
            buildAutomation.appendTextToLog("RESULT: Application appears to be already stopped");
            return true;
        }

        buildAutomation.appendTextToLog("INFO: Found " + processList.size() + " running processes");
        processList.removeIf(p -> !"EXE".equalsIgnoreCase(p.get("LAUNCHER")));

        if (processList.isEmpty()) {
            buildAutomation.appendTextToLog("INFO: No executable processes found to stop");
            buildAutomation.appendTextToLog("DETAILS: All found processes are JNLP-based and cannot be stopped via this method");
            buildAutomation.appendTextToLog("RESULT: No executable processes to terminate");
            return true;
        } else {
            buildAutomation.appendTextToLog("INFO: Found " + processList.size() + " executable processes to stop");

            if (processList.size() == 1) {
                Map<String, String> process = processList.get(0);
                String pid = process.get("PID");
                buildAutomation.appendTextToLog("STEP 1: Stopping single process...");
                buildAutomation.appendTextToLog("• Process ID: " + pid);
                buildAutomation.appendTextToLog("• Process Type: " + process.get("LAUNCHER"));
                
                try {
                    stop(pid);
                    buildAutomation.appendTextToLog("SUCCESS: Process stopped successfully - PID: " + pid);
                    return true;
                } catch (InterruptedException ex) {
                    buildAutomation.appendTextToLog("ERROR: Failed to stop process");
                    buildAutomation.appendTextToLog("DETAILS: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    buildAutomation.appendTextToLog("RESOLUTION: Process may have already terminated or access denied");
                    return false;
                }
            } else {
                buildAutomation.appendTextToLog("STEP 1: Multiple processes detected - user selection required");
                processList = ProcessSelectionDialog.selectProcess(project, processList, ProcessSelectionDialog.DialogPurpose.STOP_PROCESS, null);
                if (processList == null) {
                    buildAutomation.appendTextToLog("WARNING: No processes selected for termination");
                    buildAutomation.appendTextToLog("DETAILS: User cancelled process selection");
                    buildAutomation.appendTextToLog("RESULT: No processes were stopped");
                    return false;
                }
                
                buildAutomation.appendTextToLog("STEP 2: Stopping " + processList.size() + " selected processes...");
                for (Map<String, String> process : processList) {
                    String pid = process.get("PID");
                    buildAutomation.appendTextToLog("• Stopping process - PID: " + pid + ", Type: " + process.get("LAUNCHER"));
                    
                    try {
                        stop(pid);
                        buildAutomation.appendTextToLog("SUCCESS: Process stopped - PID: " + pid);
                    } catch (InterruptedException ex) {
                        buildAutomation.appendTextToLog("ERROR: Failed to stop process - PID: " + pid);
                        buildAutomation.appendTextToLog("DETAILS: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        buildAutomation.appendTextToLog("RESOLUTION: Process may have already terminated or access denied");
                        return false;
                    }
                }
                
                buildAutomation.appendTextToLog("SUCCESS: All selected processes stopped successfully");
                return true;
            }
        }
    }

    public List<Map<String, String>> getRunningProcessList(ProjectEntity project, String app, boolean running, boolean allLogs) {
        if(project == null) {
            buildAutomation.appendTextToLog("ERROR: Project parameter is null");
            buildAutomation.appendTextToLog("DETAILS: Cannot retrieve process list without a valid project");
            buildAutomation.appendTextToLog("RESOLUTION: Ensure a project is selected before attempting to get process list");
            return new ArrayList<>();
        }
        
        if (app == null || app.trim().isEmpty()) {
            buildAutomation.appendTextToLog("ERROR: Application name parameter is null or empty");
            buildAutomation.appendTextToLog("DETAILS: Cannot retrieve process list without a valid application name");
            buildAutomation.appendTextToLog("RESOLUTION: Ensure an application is selected before attempting to get process list");
            return new ArrayList<>();
        }
        
        buildAutomation.appendTextToLog("PROCESS LIST RETRIEVAL:");
        buildAutomation.appendTextToLog("• Project: " + project.getName());
        buildAutomation.appendTextToLog("• Application: " + app);
        buildAutomation.appendTextToLog("• Running Only: " + running);
        
        try {
            buildAutomation.appendTextToLog("STEP 1: Scanning log files for application: " + app.split("\\.")[0]);
            File logs[] = getLogFiles(app.split("\\.")[0]);

            List<Map<String, String>> processes = new ArrayList<>();
            
            buildAutomation.appendTextToLog("STEP 2: Loading current process map...");
            loadProcessMap(project);

            if (logs == null || logs.length == 0) {
                buildAutomation.appendTextToLog("INFO: No log files found for application: " + app);
                buildAutomation.appendTextToLog("DETAILS: No log files exist for the specified application");
                buildAutomation.appendTextToLog("RESOLUTION: Ensure the application has been started at least once to generate log files");
                return processes;
            }

            buildAutomation.appendTextToLog("STEP 3: Analyzing " + logs.length + " log files...");
            int processedFiles = 0;
            int matchingProcesses = 0;
            
            for (File log : logs) {
                try {
                    processedFiles++;
                    Map<String, String> mp = parseLog(log);
                    if (mp != null && !mp.isEmpty() && (allLogs || mp.get("PROJECT_KEY").equals(project.getLogId())) && (!running || jpsMap.containsKey(mp.get("PID")))) {
                        processes.add(mp);
                        matchingProcesses++;
                    }
                } catch (IOException ex) {
                    buildAutomation.appendTextToLog("WARNING: Error reading log file: " + log.getName());
                    buildAutomation.appendTextToLog("DETAILS: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    buildAutomation.appendTextToLog("RESOLUTION: Log file may be corrupted or inaccessible");
                }
            }

            buildAutomation.appendTextToLog("PROCESS ANALYSIS COMPLETE:");
            buildAutomation.appendTextToLog("• Log Files Processed: " + processedFiles);
            buildAutomation.appendTextToLog("• Matching Processes Found: " + matchingProcesses);
            buildAutomation.appendTextToLog("• Project Key: " + project.getLogId());
            
            return processes;
        } catch (Exception e) {
            buildAutomation.appendTextToLog("ERROR: EXCEPTION DURING PROCESS LIST RETRIEVAL");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Stack Trace: " + e.toString());
            buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
            buildAutomation.appendTextToLog("• File system access issues");
            buildAutomation.appendTextToLog("• Log directory permissions");
            buildAutomation.appendTextToLog("• System resource limitations");
            buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and system resources");
            return new ArrayList<>();
        }
    }

    //Local copy launched "jps" gives as JWSLauncher
    //JNLP copy launched "jps" gives as Launcher
    //creates a map that helps to find which process is local executed and which from jnlp executed
    public void loadProcessMap(ProjectEntity project) {
        buildAutomation.appendTextToLog("Loading process list ...");
        jpsMap = new HashMap<>();
        try {
			// Prefer absolute jps from configured JDK. Fall back to JAVA_HOME/bin, then plain "jps".
			ProcessBuilder pb;
			String jdkHome = (project != null) ? project.getJdkHome() : null;
			File jpsFromProject = (jdkHome != null && !jdkHome.trim().isEmpty())
					? new File(new File(jdkHome.trim(), "bin"), "jps.exe")
					: null;
			if (jpsFromProject != null && jpsFromProject.isFile()) {
				pb = new ProcessBuilder(jpsFromProject.getAbsolutePath());
			} else {
				// Try JAVA_HOME/bin/jps.exe from current environment
				String envJavaHome = System.getenv("JAVA_HOME");
				File jpsFromEnv = (envJavaHome != null && !envJavaHome.trim().isEmpty())
						? new File(new File(envJavaHome.trim(), "bin"), "jps.exe")
						: null;
				if (jpsFromEnv != null && jpsFromEnv.isFile()) {
					pb = new ProcessBuilder(jpsFromEnv.getAbsolutePath());
				} else {
					// Last resort: rely on PATH
					pb = new ProcessBuilder("jps");
				}
			}
			// Inject JAVA_HOME/PATH for the chosen JDK to ensure tools resolve
			JavaEnvUtil.applyJavaOverride(pb.environment(), jdkHome);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    jpsMap.put(parts[0], parts[1]);
                }
            }
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            buildAutomation.appendTextToLog(">>> Exception while executing jsp command\n\n");
            buildAutomation.appendTextToLog(e.toString());
        }
        buildAutomation.appendTextToLog("Loading process list ... completed");
    }

    public void stop(String PID) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec("cmd /c " + "taskkill /F /pid " + PID + " && exit 0 || exit 1");

        buildAutomation.appendTextToLog("PID : " + PID + "\n");

        if (process.waitFor() == 1) {
            buildAutomation.appendTextToLog("No opened webWorkspace found..\n");
        } else {
            buildAutomation.appendTextToLog("Closed webWorkspace...\n");
        }
    }

    public void viewLog(String app, ProjectEntity project) {
        try {
            buildAutomation.appendTextToLog("STEP 1: Retrieving process list for log viewing...");
            List<Map<String, String>> processList = getRunningProcessList(project, app, false, false);

            if (processList == null || processList.isEmpty()) {
                buildAutomation.appendTextToLog("INFO: No logs found for the project.");
                buildAutomation.appendTextToLog("DETAILS: No log files exist for the specified application and project");
                buildAutomation.appendTextToLog("RESOLUTION: Ensure the application has been started at least once to generate log files");
                return;
            }

            buildAutomation.appendTextToLog("STEP 2: Opening process selection dialog...");
            buildAutomation.appendTextToLog("INFO: Found " + processList.size() + " log entries to choose from");
            
            List<Map<String, String>> selectedProcesses = ProcessSelectionDialog.selectProcess(project, processList, ProcessSelectionDialog.DialogPurpose.VIEW_LOG, null);

            if (selectedProcesses == null || selectedProcesses.isEmpty()) {
                buildAutomation.appendTextToLog("INFO: No log file selected for viewing");
                buildAutomation.appendTextToLog("DETAILS: User cancelled the log selection process");
                return;
            }

            buildAutomation.appendTextToLog("STEP 3: Opening selected log file...");
            String pathLog = getLogDirectoryPath();
            String filePath = pathLog + selectedProcesses.get(0).get("FILE");
            
            buildAutomation.appendTextToLog("INFO: Selected log file: " + filePath);
            
            // Verify file exists before trying to open it
            File logFile = new File(filePath);
            if (!logFile.exists()) {
                buildAutomation.appendTextToLog("ERROR: Selected log file does not exist");
                buildAutomation.appendTextToLog("DETAILS: File not found at path: " + filePath);
                buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
                buildAutomation.appendTextToLog("• Log file was deleted or moved");
                buildAutomation.appendTextToLog("• File system access issues");
                buildAutomation.appendTextToLog("• Incorrect file path");
                buildAutomation.appendTextToLog("RESOLUTION: Try refreshing the log list or check file permissions");
                return;
            }

            buildAutomation.appendTextToLog("SUCCESS: Opening log file with default application");
            ManageFile.open(filePath);

        } catch (Exception e) {
            buildAutomation.appendTextToLog("ERROR: FAILED TO VIEW LOG");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
            buildAutomation.appendTextToLog("• UI thread blocking or timeout");
            buildAutomation.appendTextToLog("• File system access issues");
            buildAutomation.appendTextToLog("• Dialog creation problems");
            buildAutomation.appendTextToLog("RESOLUTION: Try again or restart the application if the issue persists");
        }
    }

    public void addToBuildLog(ProjectEntity project, String logMessage, boolean clearLog) {
        // Get user and build log file path
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/build_logs/" + "buildlog_" + project.getName() + ".log";
        
        try {
            File logFile = new File(dataStorePath);
            
            // Check if log file exists, create if it does not
            if (!logFile.exists()) {
                buildAutomation.appendTextToLog("INFO: Creating build log file...");
                buildAutomation.appendTextToLog("• Log File Path: " + dataStorePath);
                
                boolean isDirCreated = logFile.getParentFile().mkdirs(); // Create directories if they do not exist
                if(logFile.getParentFile().exists() || isDirCreated) {
                    boolean isFileCreated = logFile.createNewFile(); // Create the file
                    if(!isFileCreated){
                        buildAutomation.appendTextToLog("ERROR: Failed to create build log file");
                        buildAutomation.appendTextToLog("DETAILS: File creation returned false for: " + logFile.getAbsolutePath());
                        buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
                        buildAutomation.appendTextToLog("• File already exists but is not accessible");
                        buildAutomation.appendTextToLog("• Insufficient permissions to create file");
                        buildAutomation.appendTextToLog("• Disk space issues");
                        buildAutomation.appendTextToLog("RESOLUTION: Check file permissions and disk space");
                        throw new RuntimeException("Failed to create log file: " + logFile.getAbsolutePath());
                    } else {
                        buildAutomation.appendTextToLog("SUCCESS: Build log file created successfully");
                    }
                } else {
                    buildAutomation.appendTextToLog("ERROR: Failed to create build log directory");
                    buildAutomation.appendTextToLog("DETAILS: Directory creation failed for: " + logFile.getParentFile().getAbsolutePath());
                    buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
                    buildAutomation.appendTextToLog("• Insufficient permissions to create directory");
                    buildAutomation.appendTextToLog("• Disk space issues");
                    buildAutomation.appendTextToLog("• Path contains invalid characters");
                    buildAutomation.appendTextToLog("RESOLUTION: Check directory permissions and disk space");
                    throw new RuntimeException("Failed to create directory: " + logFile.getParentFile().getAbsolutePath());
                }
            }
            
            // Clear the file if clearLog is true
            if (clearLog) {
                buildAutomation.appendTextToLog("INFO: Clearing existing build log content...");
                BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false));
                writer.write(""); // Clear the file by writing an empty string
                writer.close();
                buildAutomation.appendTextToLog("SUCCESS: Build log cleared successfully");
            }

            // Append the log message to the file
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(logMessage);
            writer.newLine(); // Add a new line
            writer.close();

        } catch (IOException e) {
            buildAutomation.appendTextToLog("ERROR: FAILED TO WRITE TO BUILD LOG");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Log File Path: " + dataStorePath);
            buildAutomation.appendTextToLog("POSSIBLE CAUSES:");
            buildAutomation.appendTextToLog("• File system access permissions");
            buildAutomation.appendTextToLog("• Disk space limitations");
            buildAutomation.appendTextToLog("• File locked by another process");
            buildAutomation.appendTextToLog("• Invalid file path or characters");
            buildAutomation.appendTextToLog("RESOLUTION: Check file permissions, disk space, and ensure file is not locked by another application");
        } catch (RuntimeException e) {
            buildAutomation.appendTextToLog("ERROR: RUNTIME EXCEPTION IN BUILD LOG OPERATION");
            buildAutomation.appendTextToLog("EXCEPTION DETAILS:");
            buildAutomation.appendTextToLog("• Exception Type: " + e.getClass().getSimpleName());
            buildAutomation.appendTextToLog("• Error Message: " + e.getMessage());
            buildAutomation.appendTextToLog("• Log File Path: " + dataStorePath);
            buildAutomation.appendTextToLog("RESOLUTION: " + e.getMessage());
        }
    }


    private boolean isValidDirectory(String p) {
        try {
            if (p == null) return false;
            String s = p.trim();
            if (s.isEmpty()) return false;
            File f = new File(s);
            return f.exists() && f.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    // Read persisted env var from registry (User, then Machine) via PowerShell
    private String getPersistedEnvVarValue(String varName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "[Environment]::GetEnvironmentVariable('" + varName + "','User')");
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!out.isEmpty() && !out.equalsIgnoreCase(varName)) return out;
        } catch (Exception ignored) { }
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "[Environment]::GetEnvironmentVariable('" + varName + "','Machine')");
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!out.isEmpty() && !out.equalsIgnoreCase(varName)) return out;
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * Resolve the value to inject for the project's env var into a child process:
     * 1) Persisted User value if valid dir
     * 2) Persisted Machine value if valid dir
     * 3) Current System.getenv snapshot if valid dir
     * 4) project.getExePath() if valid dir
     * Returns the chosen path or null if nothing valid.
     */
    private String resolveAndInjectEnvVar(Map<String,String> env, ProjectEntity project) {
        try {
            String varName = project.getNmsEnvVar();
            if (varName == null || varName.isBlank()) return null;

            String userVal = getPersistedEnvVarValue(varName); // will check User then Machine internally
            String machineVal = null;
            if (userVal == null) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                            "[Environment]::GetEnvironmentVariable('" + varName + "','Machine')");
                    Process p = pb.start();
                    machineVal = new String(p.getInputStream().readAllBytes()).trim();
                    p.waitFor();
                    if (machineVal.isEmpty() || machineVal.equalsIgnoreCase(varName)) machineVal = null;
                } catch (Exception ignored) {}
            }
            String sysSnapshot = System.getenv(varName);
            String chosen = null;
            if (isValidDirectory(userVal)) chosen = userVal;
            else if (isValidDirectory(machineVal)) chosen = machineVal;
            else if (isValidDirectory(sysSnapshot)) chosen = sysSnapshot;
            else if (isValidDirectory(project.getExePath())) chosen = project.getExePath();

            if (chosen != null) {
                env.put(varName, chosen);
                logger.info("Injected " + varName + "=" + chosen);
            } else {
                logger.warning("No valid value found to inject for " + varName);
            }
            return chosen;
        } catch (Exception e) {
            logger.warning("Failed to resolve/inject env var for child process: " + e.getMessage());
            return null;
        }
    }

    public static String getLogDirectoryPath() {
        String path;
        File dir = null;

        if (System.getProperty("os.name").startsWith("Windows")) {
            path = System.getProperty("java.io.tmpdir");
        } else {
            path = System.getenv("APPDATA");

            if (path == null) {
                path = System.getenv("NMS_LOG_DIR");
                if (path != null) {
                    dir = new File(path);
                } else {
                    path = System.getProperty("java.io.tmpdir") + "/" + System.getProperty("user.name");
                }
            }
        }

        if (dir == null) {
            dir = new File(path, "OracleNMS");
        }

        return dir.getAbsolutePath()+"\\";
    }

    /**
     * Check if a build is currently running for the given project
     * This is a stub method to maintain compatibility with BuildAutomation controller
     */
    public boolean isBuildRunning(String projectName) {
        // For now, return false since we're not implementing the full thread management
        // This prevents compilation errors while maintaining the existing functionality
        return false;
    }

    /**
     * Cancel a running build for the given project
     * This is a stub method to maintain compatibility with BuildAutomation controller
     */
    public void cancelBuild(String projectName) {
        // For now, just interrupt the restart thread if it exists
        if (restartThread != null && restartThread.isAlive()) {
            restartThread.interrupt();
            restartThread = null;
        }
    }

    /**
     * Cancel all running builds
     * This is a static method to maintain compatibility with BuildAutomation controller
     */
    public static void cancelAllBuilds() {
        // For now, just interrupt the restart thread if it exists
        if (restartThread != null && restartThread.isAlive()) {
            restartThread.interrupt();
            restartThread = null;
        }
    }

}
