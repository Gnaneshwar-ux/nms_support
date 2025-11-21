    package com.nms.support.nms_support.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nms.support.nms_support.util.ProjectPathUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ProjectEntity {
    private String name;
    private String jconfigPath;
    private String exePath;
    private String logId;
    private String username;
    private String password;
    private String autoLogin;
    private String manageTool;
    private HashMap<String,List<String>> types;
    private HashMap<String,String> prevTypeSelected;
    private String host;
    private String hostUser;
    private String hostPass;
    private int hostPort;
    // LDAP auth
    private boolean useLdap;
    private String ldapUser;
    private String targetUser;
    private String ldapPassword;
    private String dataStoreUser;
    private String nmsAppURL;
    private String nmsEnvVar;
    private String svnRepo;
    private String selectedApplication; // Store the previously selected application
    private String jdkHome; // Optional per-project JDK home override
    // Semicolon-separated JAR directories for Jar Decompiler tab (persisted)
    private String jarDecompilerPaths;

    // Oracle DB details
    private String dbHost;
    private int dbPort;
    private String dbUser;
    private String dbPassword;
    private String dbSid;
    
    // Project ordering
    private int order = 0;
    
    // Server zip file tracking for cleanup
    private List<ServerZipFile> serverZipFiles;

    public List<String> getTypes(String app) {
        if (types.containsKey(app)) return types.get(app);
        return null;
    }

    public void setTypes(String app, List<String> types) {
        this.types.put(app, types);
    }

    public void addType(String app, String type) {
        if(types.containsKey(app)){
            types.get(app).add(type);
        }
        else{
            types.put(app, new ArrayList<>());
            types.get(app).add(type);
        }
    }


    public void setLogId(String logId) {
        this.logId = logId;
    }

    public ProjectEntity() {
        this.types = new HashMap<>();
        this.prevTypeSelected = new HashMap<>();
        this.serverZipFiles = new ArrayList<>();
    }

    public ProjectEntity(String name){
        this.name = name;
        this.types = new HashMap<>();
        this.prevTypeSelected = new HashMap<>();
        this.serverZipFiles = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJconfigPath() {
        return ProjectPathUtil.normalizeForWindows(jconfigPath);
    }

    public void setJconfigPath(String jconfigPath) {
        this.jconfigPath = ProjectPathUtil.normalizeForWindows(jconfigPath);
    }
    
    /**
     * Gets the project folder path for display purposes.
     * This method converts the stored jconfig path to the project folder path.
     * 
     * @return The project folder path (without /jconfig suffix) with Windows-compatible separators
     */
    @JsonIgnore
    public String getProjectFolderPath() {
        return ProjectPathUtil.normalizeForWindows(ProjectPathUtil.getProjectFolderPath(this.jconfigPath));
    }
    
    /**
     * Sets the project folder path, storing it internally as a jconfig path.
     * This method ensures backward compatibility while normalizing the path.
     * 
     * @param projectFolderPath The project folder path to set
     */
    public void setProjectFolderPath(String projectFolderPath) {
        this.jconfigPath = ProjectPathUtil.normalizeForStorage(projectFolderPath);
    }
    
    /**
     * Gets the jconfig path for build operations.
     * This method ensures build operations always use the correct jconfig path.
     * 
     * @return The jconfig path for build operations with Windows-compatible separators
     */
    @JsonIgnore
    public String getJconfigPathForBuild() {
        return ProjectPathUtil.normalizeForWindows(ProjectPathUtil.getBuildPath(this.jconfigPath));
    }
    
    /**
     * Gets the project folder path for checkout operations.
     * This method ensures checkout operations use the project folder, not jconfig.
     * 
     * @return The project folder path for checkout operations with Windows-compatible separators
     */
    @JsonIgnore
    public String getProjectFolderPathForCheckout() {
        return ProjectPathUtil.normalizeForWindows(ProjectPathUtil.getCheckoutPath(this.jconfigPath));
    }
    
    /**
     * Validates that the project structure is correct (contains jconfig subdirectory).
     * 
     * @return true if the project structure is valid, false otherwise
     */
    public boolean validateProjectStructure() {
        return ProjectPathUtil.validateProjectStructure(getProjectFolderPath());
    }

    public String getExePath() {
        return ProjectPathUtil.normalizeForWindows(exePath);
    }

    public void setExePath(String exePath) {
        this.exePath = ProjectPathUtil.normalizeForWindows(exePath);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAutoLogin() {
        return autoLogin;
    }

    public void setAutoLogin(String autoLogin) {
        this.autoLogin = autoLogin;
    }

    public String getManageTool() {
        return manageTool;
    }

    public void setManageTool(String manageTool) {
        this.manageTool = manageTool;
    }

    public String getLogId() {
        return logId;
    }

    public String getPrevTypeSelected(String app) {
        return this.prevTypeSelected.get(app);
    }

    public void setPrevTypeSelected(String app, String prevTypeSelected) {
        this.prevTypeSelected.put(app, prevTypeSelected);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHostUser() {
        return hostUser;
    }

    public void setHostUser(String hostUser) {
        this.hostUser = hostUser;
    }

    public String getHostPass() {
        return hostPass;
    }

    public void setHostPass(String hostPass) {
        this.hostPass = hostPass;
    }

    // ===== LDAP getters/setters =====
    public boolean isUseLdap() {
        return useLdap;
    }

    public void setUseLdap(boolean useLdap) {
        this.useLdap = useLdap;
    }

    public String getLdapUser() {
        return ldapUser;
    }

    public void setLdapUser(String ldapUser) {
        this.ldapUser = ldapUser;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getLdapPassword() {
        return ldapPassword;
    }

    public void setLdapPassword(String ldapPassword) {
        this.ldapPassword = ldapPassword;
    }

    public int getHostPort() {
        return hostPort;
    }

    public void setHostPort(int hostPort) { 
        this.hostPort = hostPort;
    }

    public String getDataStoreUser() {
        return dataStoreUser;
    }

    public void setDataStoreUser(String dataStoreUser) {
        this.dataStoreUser = dataStoreUser;
    }

    public String toString() {
        return "ProjectEntity{" +
                "name='" + name + '\'' +
                ", jconfigPath='" + jconfigPath + '\'' +
                ", exePath='" + exePath + '\'' +
                ", log=Id" + logId +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", autoLogin='" + autoLogin + '\'' +
                ", manageTool='" + manageTool + '\'' +
                ", prevTypeSelected='" + prevTypeSelected + '\'' +
                '}';
    }

    public void setTypes(HashMap<String, List<String>> l) {
        this.types = l;
    }

    public HashMap<String, List<String>> getTypes() {
        return types;
    }

    public HashMap<String, String> getPrevTypeSelected() {
        return prevTypeSelected;
    }

    public void setPrevTypeSelected(HashMap<String, String> prevTypeSelected) {
        this.prevTypeSelected = prevTypeSelected;
    }

    public String getNmsAppURL() {
        return nmsAppURL;
    }

    public void setNmsAppURL(String nmsAppURL) {
        this.nmsAppURL = nmsAppURL;
    }

    public String getNmsEnvVar() {
        return nmsEnvVar;
    }

    public void setNmsEnvVar(String nmsEnvVar) {
        this.nmsEnvVar = nmsEnvVar;
    }

    public void setSvnRepo(String svnRepo) {
        this.svnRepo = svnRepo;
    }
    public String getSvnRepo(){
        return this.svnRepo;
    }

    public String getSelectedApplication() {
        return selectedApplication;
    }

    public void setSelectedApplication(String selectedApplication) {
        this.selectedApplication = selectedApplication;
    }

    public String getJdkHome() { return jdkHome; }
    public void setJdkHome(String jdkHome) { this.jdkHome = jdkHome; }
    
    // ===== Jar Decompiler persisted paths (semicolon-separated) =====
    public String getJarDecompilerPaths() {
        return ProjectPathUtil.normalizeForWindows(jarDecompilerPaths);
    }
    public void setJarDecompilerPaths(String jarDecompilerPaths) {
        this.jarDecompilerPaths = ProjectPathUtil.normalizeForWindows(jarDecompilerPaths);
    }

    // ===== Oracle DB getters/setters =====
    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }

    public int getDbPort() { return dbPort; }
    public void setDbPort(int dbPort) { this.dbPort = dbPort; }

    public String getDbUser() { return dbUser; }
    public void setDbUser(String dbUser) { this.dbUser = dbUser; }

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }

    public String getDbSid() { return dbSid; }
    public void setDbSid(String dbSid) { this.dbSid = dbSid; }
    
    // ===== Project ordering getters/setters =====
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    // ===== Server zip file tracking getters/setters =====
    public List<ServerZipFile> getServerZipFiles() { 
        if (serverZipFiles == null) {
            serverZipFiles = new ArrayList<>();
        }
        return serverZipFiles; 
    }
    
    public void setServerZipFiles(List<ServerZipFile> serverZipFiles) { 
        this.serverZipFiles = serverZipFiles; 
    }
    
    /**
     * Adds a server zip file to tracking
     */
    public synchronized void addServerZipFile(String path, String purpose) {
        if (serverZipFiles == null) {
            serverZipFiles = new ArrayList<>();
        }
        ServerZipFile zipFile = new ServerZipFile(path, purpose, System.currentTimeMillis());
        serverZipFiles.add(zipFile);
    }
    
    /**
     * Removes a server zip file from tracking
     */
    public synchronized void removeServerZipFile(String path) {
        if (serverZipFiles != null) {
            serverZipFiles.removeIf(zip -> zip.getPath().equals(path));
        }
    }
    
    /**
     * Checks if there are any tracked zip files on the server
     */
    public boolean hasServerZipFiles() {
        return serverZipFiles != null && !serverZipFiles.isEmpty();
    }
    
    /**
     * Inner class to represent a server zip file
     */
    public static class ServerZipFile {
        private String path;
        private String purpose;
        private long createdTimestamp;
        
        // Default constructor for Jackson
        public ServerZipFile() {
        }
        
        public ServerZipFile(String path, String purpose, long createdTimestamp) {
            this.path = path;
            this.purpose = purpose;
            this.createdTimestamp = createdTimestamp;
        }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        
        public long getCreatedTimestamp() { return createdTimestamp; }
        public void setCreatedTimestamp(long createdTimestamp) { this.createdTimestamp = createdTimestamp; }
    }
}
