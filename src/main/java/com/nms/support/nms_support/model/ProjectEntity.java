package com.nms.support.nms_support.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectEntity {
    private String name;
    private String jconfigPath;
    private String exePath;
    private String logId;
    private String username;
    private String password;
    private String autoLogin;
    private String prevTypeSelected;
    private String host;
    private String hostUser;
    private String hostPass;
    private String dataStoreUser;
    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public void addType(String type) {
        types.add(type);
    }

    private List<String> types;

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public ProjectEntity() {
        this.types = new ArrayList<>();
    }

    public ProjectEntity(String name){
        this.name = name;
        this.types = new ArrayList<>();
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

    public String getPrevTypeSelected() {
        return prevTypeSelected;
    }

    public void setPrevTypeSelected(String prevTypeSelected) {
        this.prevTypeSelected = prevTypeSelected;
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
}
