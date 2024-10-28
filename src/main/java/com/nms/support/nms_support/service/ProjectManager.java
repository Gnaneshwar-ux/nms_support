package com.nms.support.nms_support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.model.ProjectWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProjectManager implements IManager {

    public ProjectWrapper getProjectWrapper() {
        return projectWrapper;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    private ProjectWrapper projectWrapper;
    private LogManager logManager;
    private File source;

    public ProjectManager(String sourcePath) {
        this.source = new File(sourcePath);
        ensureFileExists(this.source);
        initManager(this.source);

    }

    private void ensureFileExists(File source) {
        try {
            File parentDir = source.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    System.out.println("Directory created: " + parentDir.getAbsolutePath());
                } else {
                    System.out.println("Failed to create directory: " + parentDir.getAbsolutePath());
                }
            }

            if (!source.exists()) {
                if (source.createNewFile()) {
                    System.out.println("File created: " + source.getAbsolutePath());
                } else {
                    System.out.println("Failed to create file: " + source.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initManager(File source) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            projectWrapper = objectMapper.readValue(source, ProjectWrapper.class);
            if (projectWrapper == null) {
                projectWrapper = new ProjectWrapper();
            }
        } catch (IOException e) {
            e.printStackTrace();
            projectWrapper = new ProjectWrapper();
        }
    }


    // Project management methods
    public void addProject(ProjectEntity project) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null) {
            projects = new ArrayList<>();
            projectWrapper.setProjects(projects);
        }
        projects.add(project);
    }

    public void updateProject(String projectName, ProjectEntity updatedProject) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects != null) {
            for (int i = 0; i < projects.size(); i++) {
                if (projects.get(i).getName().equals(projectName)) {
                    projects.set(i, updatedProject);
                    return;
                }
            }
        }
        System.out.println("Project not found: " + projectName);
    }

    public void deleteProject(String projectName) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects != null) {
            projects.removeIf(project -> project.getName().equals(projectName));
        }
    }

    public List<String> getListProject() {
        List<String> projectNames = new ArrayList<>();
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects != null) {
            for (ProjectEntity project : projects) {
                projectNames.add(project.getName());
            }
        }
        return projectNames;
    }

    public List<ProjectEntity> getProjects() {
        return projectWrapper != null ? projectWrapper.getProjects() : new ArrayList<>();
    }

    public void setProjects(List<ProjectEntity> projects) {
        if (projectWrapper == null) {
            projectWrapper = new ProjectWrapper();
        }
        projectWrapper.setProjects(projects);
    }

    public boolean saveData() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (projectWrapper == null) {
                projectWrapper = new ProjectWrapper();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.source, projectWrapper);
            return  true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Delegate log operations to LogManager
    public void addLog(LogEntity log) {
        logManager.addLog(log);
    }

    public void updateLog(String logId, LogEntity updatedLog) {
        logManager.updateLog(logId, updatedLog);
    }

    public void saveLogs() {
        logManager.saveData();
    }

    public ProjectEntity getProjectByName(String projectName) {
        if(getProjects()!=null) {
            for (ProjectEntity pe : getProjects()) {
                if (pe.getName().equals(projectName)) {
                    return pe;
                }
            }
        }
        return null;
    }
}