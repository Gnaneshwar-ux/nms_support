package com.nms.support.nms_support.service.userdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.model.ProjectWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Attach immediate-save callback to a single project
    private void attachSaveCallback(ProjectEntity project) {
        if (project != null) {
            project.setSaveCallback(() -> {
                try {
                    // Persist projects.json immediately when zip list changes
                    saveData();
                } catch (Exception ignored) {
                    // Avoid propagating any persistence issues to callers
                }
            });
        }
    }

    // Attach callbacks for all projects currently loaded
    private void attachCallbacksForAllProjects() {
        List<ProjectEntity> projects = projectWrapper != null ? projectWrapper.getProjects() : null;
        if (projects != null) {
            for (ProjectEntity p : projects) {
                attachSaveCallback(p);
            }
        }
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
        // Ensure all loaded projects will save immediately on zip add/remove
        attachCallbacksForAllProjects();
    }


    // Project management methods
    public void addProject(ProjectEntity project) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null) {
            projects = new ArrayList<>();
            projectWrapper.setProjects(projects);
        }
        
        // Set order to be the highest (most recent) - projects with higher order appear first
        // Use a more reliable ordering system that resets periodically to prevent overflow
        int maxOrder = projects.stream()
                .mapToInt(ProjectEntity::getOrder)
                .max()
                .orElse(-1);
        
        // If we're getting close to Integer.MAX_VALUE, reset all orders to prevent overflow
        if (maxOrder > Integer.MAX_VALUE - 1000) {
            resetProjectOrders();
            maxOrder = projects.stream()
                    .mapToInt(ProjectEntity::getOrder)
                    .max()
                    .orElse(-1);
        }
        
        project.setOrder(maxOrder + 1);
        projects.add(project);
        // Ensure immediate-save wiring for new project
        attachSaveCallback(project);
    }

    public void updateProject(String projectName, ProjectEntity updatedProject) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects != null) {
            for (int i = 0; i < projects.size(); i++) {
                if (projects.get(i).getName().equals(projectName)) {
                    projects.set(i, updatedProject);
                    // Ensure updated instance has immediate-save wiring
                    attachSaveCallback(updatedProject);
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
        List<ProjectEntity> projects = projectWrapper != null ? projectWrapper.getProjects() : new ArrayList<>();
        if (projects != null) {
            // Remove duplicates based on project name to prevent data corruption
            projects = removeDuplicateProjects(projects);
            // Sort by order in descending order (highest order first - most recent first)
            projects.sort(Comparator.comparingInt(ProjectEntity::getOrder).reversed());
        }
        return projects;
    }

    public void setProjects(List<ProjectEntity> projects) {
        if (projectWrapper == null) {
            projectWrapper = new ProjectWrapper();
        }
        projectWrapper.setProjects(projects);
        // Ensure all projects are wired for immediate-save behavior
        attachCallbacksForAllProjects();
    }

    public synchronized boolean saveData() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (projectWrapper == null) {
                projectWrapper = new ProjectWrapper();
            }
            
            // Clean up any duplicate projects before saving
            List<ProjectEntity> projects = projectWrapper.getProjects();
            if (projects != null) {
                projects = removeDuplicateProjects(projects);
                projectWrapper.setProjects(projects);
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

    /**
     * Gets a project by name (alias for getProjectByName for consistency)
     */
    public ProjectEntity getProject(String projectName) {
        return getProjectByName(projectName);
    }

    /**
     * Checks if a project with the given name exists
     */
    public boolean projectExists(String projectName) {
        return getProjectByName(projectName) != null;
    }
    
    /**
     * Reorders projects based on the provided list of project names
     * @param projectNamesInOrder List of project names in the desired order
     */
    public void reorderProjects(List<String> projectNamesInOrder) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null || projectNamesInOrder == null) {
            return;
        }
        
        // Create a map for quick lookup
        java.util.Map<String, ProjectEntity> projectMap = new java.util.HashMap<>();
        for (ProjectEntity project : projects) {
            projectMap.put(project.getName(), project);
        }
        
        // Update order based on the provided list (first item gets highest order)
        for (int i = 0; i < projectNamesInOrder.size(); i++) {
            String projectName = projectNamesInOrder.get(i);
            ProjectEntity project = projectMap.get(projectName);
            if (project != null) {
                // Higher order means appears first (most recent)
                project.setOrder(projectNamesInOrder.size() - i);
            }
        }
    }
    
    /**
     * Moves a project to the top (most recent position)
     * @param projectName Name of the project to move to top
     */
    public void moveProjectToTop(String projectName) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null) {
            return;
        }
        
        // Remove duplicates first to prevent issues
        projects = removeDuplicateProjects(projects);
        projectWrapper.setProjects(projects);
        
        ProjectEntity targetProject = null;
        int maxOrder = -1;
        
        // Find the target project and current max order
        for (ProjectEntity project : projects) {
            if (project.getName().equals(projectName)) {
                targetProject = project;
            }
            maxOrder = Math.max(maxOrder, project.getOrder());
        }
        
        if (targetProject != null) {
            // Check if we need to reset orders to prevent overflow
            if (maxOrder > Integer.MAX_VALUE - 1000) {
                resetProjectOrders();
                maxOrder = projects.stream()
                        .mapToInt(ProjectEntity::getOrder)
                        .max()
                        .orElse(-1);
            }
            targetProject.setOrder(maxOrder + 1);
        }
    }
    
    /**
     * Resets all project orders to prevent integer overflow
     * Maintains relative order but uses smaller numbers
     */
    private void resetProjectOrders() {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null || projects.isEmpty()) {
            return;
        }
        
        // Sort projects by current order to maintain relative order
        projects.sort(Comparator.comparingInt(ProjectEntity::getOrder).reversed());
        
        // Reset orders starting from 0
        for (int i = 0; i < projects.size(); i++) {
            projects.get(i).setOrder(i);
        }
    }
    
    /**
     * Removes duplicate projects based on project name, keeping the one with the highest order
     * This prevents data corruption and duplicate entries
     * @param projects List of projects to deduplicate
     * @return Deduplicated list of projects
     */
    private List<ProjectEntity> removeDuplicateProjects(List<ProjectEntity> projects) {
        if (projects == null || projects.isEmpty()) {
            return projects;
        }
        
        Map<String, ProjectEntity> uniqueProjects = new HashMap<>();
        
        for (ProjectEntity project : projects) {
            String projectName = project.getName();
            if (projectName != null && !projectName.trim().isEmpty()) {
                if (!uniqueProjects.containsKey(projectName)) {
                    uniqueProjects.put(projectName, project);
                } else {
                    // Keep the project with the higher order (more recent)
                    ProjectEntity existingProject = uniqueProjects.get(projectName);
                    if (project.getOrder() > existingProject.getOrder()) {
                        uniqueProjects.put(projectName, project);
                    }
                }
            }
        }
        
        return new ArrayList<>(uniqueProjects.values());
    }
}
