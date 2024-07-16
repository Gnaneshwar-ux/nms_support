package com.nms.support.nms_support.model;

import java.util.List;
import java.util.Objects;

public class LogEntity {
    String id;
    String projectName;
    String version;

    public LogEntity(){}
    public LogEntity(String projectName, String version) {
        this.projectName = projectName;
        this.version = version;
        this.id = generateId(projectName, version);
    }

    private String generateId(String projectName, String version) {
        return projectName + "#" + version;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
        this.id = generateId(projectName, version); // Update ID when projectName changes
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        this.id = generateId(projectName, version); // Update ID when version changes
    }

    @Override
    public String toString() {
        return "LogEntity{" +
                "id='" + id + '\'' +
                ", projectName='" + projectName + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntity logEntity = (LogEntity) o;
        return Objects.equals(id, logEntity.id) &&
                Objects.equals(projectName, logEntity.projectName) &&
                Objects.equals(version, logEntity.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, projectName, version);
    }
}
