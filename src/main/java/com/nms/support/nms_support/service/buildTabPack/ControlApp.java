package com.nms.support.nms_support.service.buildTabPack;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.LogManager;
import com.nms.support.nms_support.service.ProjectManager;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.ManageFile;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;

import static com.nms.support.nms_support.service.globalPack.DialogUtil.selectProcess;

public class ControlApp {
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
            directory = new File("C:\\Users\\" + user + "\\AppData\\Local\\Temp\\OracleNMS");
        } catch (Exception e) {
            buildAutomation.appendTextToLog(e.toString());

        }
        File[] logfiles = null;
        if (directory != null) {
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
            DialogUtil.showWarning("Warning","Before using this application you must run the WebWorkspace atleast once manually.");
            return null;
        }
        return logfiles;
    }

    //This method parse the log files and create map for required fields
    public static Map<String, String> parseLog(File file) throws FileNotFoundException, IOException {

        if (file == null) {
            return null;
        }

        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
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
                if (line.contains("AppData") && line.contains(".nms")) {
                    launcher = "JNLP";
                } else {
                    launcher = "Local";
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

    public Task<Boolean> build(ProjectEntity project, String typeSelected) throws InterruptedException {
        String path = "";
        String jconfigPath = project.getJconfigPath();
        if (jconfigPath == null || jconfigPath.equals("")) {
            buildAutomation.appendTextToLog("\nError*** Jconfig path not valid.\n");
            return null;
        } else {
            path = jconfigPath;
        }

        String finalPath = path;
        String type = typeSelected.equals("Ant config") ? "ant config" : "ant clean config";

        Task<Boolean> buildTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", type + " && exit 0 || exit 1");
                    builder.directory(new File(finalPath));
                    Process process = builder.start();

                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String s;
                    buildAutomation.appendTextToLog("Build running... (" + type + ")" + "\n");
                    addToBuildLog(project, "Project = "+project.getName(), true);
                    while ((s = stdInput.readLine()) != null) {
                        updateMessage(s);
                        addToBuildLog(project, s+"\n", false);
                    }

                    int exitCode = process.waitFor();
                    if (exitCode == 1) {
                        updateMessage("\nERROR: BUILD FAILED.\n");
                        return false;
                    } else {
                        updateMessage("\nBUILD SUCCESSFUL.\n");
                        return true;
                    }
                } catch (IOException e) {
                    updateMessage("Error** Invalid Jconfig path.\n");
                    updateMessage(e.toString() + "\n");
                    return false;
                }
            }
        };

        buildTask.messageProperty().addListener((observable, oldValue, newValue) -> buildAutomation.appendTextToLog(newValue));

        Thread t = new Thread(buildTask);
        t.start();
        return buildTask; // Indicate that the build process has started
    }

    public void start(String app, ProjectEntity project) throws IOException, InterruptedException {
        String path = "";
        
        if (project.getExePath() == null || project.getExePath().equals("")) {

            buildAutomation.appendTextToLog("\nError*** WebWorkspace.exe path not valid.\n");
            
            return;

        } else {
            path = project.getExePath();
            
        }
        String commandArray1[] = {"cmd.exe", "/c", "start", app};
        try {
            File f = new File(path + "/"+app);
            if (!f.exists()) {
                buildAutomation.appendTextToLog("Application path is incorrect.\n");
                return;
            }
            Process process = Runtime.getRuntime().exec(commandArray1, null, new File(path));

            buildAutomation.appendTextToLog("Application Started\n");
            

        } catch (IOException e) {

            buildAutomation.appendTextToLog("Error** Invalid Application path\n");
            buildAutomation.appendTextToLog(e + "\n");
            
        }
    }

    public boolean stopProject(String app, ProjectEntity project) throws IOException {
        List<Map<String, String>> processList = getRunningProcessList(project, app, true);

        

        if (processList == null || processList.isEmpty()) {
            buildAutomation.appendTextToLog("No running processes found. - " + project.getName());
            
            return true;
        } else {
            if (processList.size() == 1) {
                try {
                    stop(processList.get(0).get("PID"));
                    buildAutomation.appendTextToLog("Successfully stoped process - " + processList.get(0).get("PID"));
                    
                    return true;
                } catch (InterruptedException ex) {
                    buildAutomation.appendTextToLog("Failed to stop process - " + processList.get(0).get("PID"));
                    
                    return false;
                }
            } else {
                processList = selectProcess(processList);
                if (processList == null) {
                    buildAutomation.appendTextToLog("No processes selected. - " + project);
                    return false;
                }
                for (Map<String, String> process : processList) {
                    try {
                        stop(process.get("PID"));
                        buildAutomation.appendTextToLog("Successfully stoped process - " + process.get("PID"));

                    } catch (InterruptedException ex) {
                        buildAutomation.appendTextToLog("Failed to stop process - " + process.get("PID"));
                        
                        return false;
                    }
                }
                
                return true;
            }
        }
    }

    public List<Map<String, String>> getRunningProcessList(ProjectEntity project, String app, boolean running) {
        if(project == null)System.out.println("Project not selected or null");
        if (project != null) {
            System.out.println(project.toString());
        }
        System.out.println(app);
        File logs[] = getLogFiles(app.split("\\.")[0]);

        List<Map<String, String>> processes = new ArrayList<>();
        loadProcessMap();

        if (logs == null || logs.length == 0) {
            return null;
        }

        for (File log : logs) {
            try {
                Map<String, String> mp = parseLog(log);
                //if(mp !=  null)System.out.println(mp.get("PROJECT_KEY"));
                if (mp != null && !mp.isEmpty() && mp.get("PROJECT_KEY").equals(project.getLogId()) && (!running || jpsMap.containsKey(mp.get("PID")))) {

                    processes.add(mp);

                }
            } catch (IOException ex) {
                System.out.println("Exception occured while reading log");
            }
        }

        return processes;
    }

    //Local copy launched "jps" gives as JWSLauncher
    //JNLP copy launched "jps" gives as Launcher
    //creates a map that helps to find which process is local executed and which from jnlp executed
    public void loadProcessMap() {
        buildAutomation.appendTextToLog("Loading process list ...");
        jpsMap = new HashMap<>();
        try {
            Process process = Runtime.getRuntime().exec("jps");
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

        Platform.runLater(() -> {
            List<Map<String, String>> processList = getRunningProcessList(project, app, false);

            if (processList == null || processList.isEmpty()) {
                buildAutomation.appendTextToLog("No logs found for the project.");
                return;
            }

            List<Map<String, String>> selectedProcesses = selectProcess(processList);

            if (selectedProcesses == null || selectedProcesses.isEmpty()) {
                return;
            }

            String user = System.getProperty("user.name");
            String pathLog = "C:\\Users\\" + user + "\\AppData\\Local\\Temp\\OracleNMS\\";
            String filePath = pathLog + selectedProcesses.get(0).get("FILE");

            buildAutomation.appendTextToLog(selectedProcesses.get(0).get("FILE"));

            // Check if Notepad++ exists
            ManageFile.open(filePath);
        });
    }

    public static void addToBuildLog(ProjectEntity project, String logMessage, boolean clearLog) {
        // Get user and build log file path
        String user = System.getProperty("user.name");
        String dataStorePath = "C:/Users/" + user + "/Documents/nms_support_data/build_logs/" + "buildlog_" + project.getName() + ".log";

        try {
            File logFile = new File(dataStorePath);

            // Check if log file exists, create if it does not
            if (!logFile.exists()) {
                boolean isDirCreated = logFile.getParentFile().mkdirs();// Create directories if they do not exist
                if(isDirCreated) {
                    boolean isFileCreated = logFile.createNewFile(); // Create the file
                    if(!isFileCreated){
                        throw new RuntimeException("Failed to create log file "+ logFile);
                    }
                }
                else
                    throw new RuntimeException("Failed to create dir "+ logFile.getParentFile().toString());
            }

            // Clear the file if clearLog is true
            if (clearLog) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false));
                writer.write(""); // Clear the file by writing an empty string
                writer.close();
            }

            // Append the log message to the file
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(logMessage);
            writer.newLine(); // Add a new line
            writer.close();

        } catch (IOException e) {
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Exception while writing to log", e.getMessage());
        }
    }

}
