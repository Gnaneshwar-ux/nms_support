package com.nms.support.nms_support.model;

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
    private HashMap<String,List<String>> types;
    private HashMap<String,String> prevTypeSelected;
    private String host;
    private String hostUser;
    private String hostPass;
    private String dataStoreUser;
    private String nmsAppURL;
    private String nmsEnvVar;

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
    }

    public ProjectEntity(String name){
        this.name = name;
        this.types = new HashMap<>();
        this.prevTypeSelected = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJconfigPath() {
        return jconfigPath;
    }

    public void setJconfigPath(String jconfigPath) {
        this.jconfigPath = jconfigPath;
    }

    public String getExePath() {
        return exePath;
    }

    public void setExePath(String exePath) {
        this.exePath = exePath;
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
}
