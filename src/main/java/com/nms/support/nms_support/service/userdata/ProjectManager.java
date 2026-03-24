package com.nms.support.nms_support.service.userdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.model.ProjectWrapper;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ProjectManager implements IManager {

    private static final Logger logger = LoggerUtil.getLogger();

    private ProjectWrapper projectWrapper;
    private LogManager logManager;
    private final File source;

    public ProjectManager(String sourcePath) {
        this.source = new File(sourcePath);
        ensureParentDirectoryExists(this.source);
        initManager(this.source);
    }

    public ProjectWrapper getProjectWrapper() {
        return projectWrapper;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    private void attachSaveCallback(ProjectEntity project) {
        if (project != null) {
            project.setSaveCallback(() -> {
                try {
                    saveData();
                } catch (Exception ignored) {
                    // Avoid propagating persistence failures to callers.
                }
            });
        }
    }

    private void attachCallbacksForAllProjects() {
        List<ProjectEntity> projects = projectWrapper != null ? projectWrapper.getProjects() : null;
        if (projects != null) {
            for (ProjectEntity project : projects) {
                attachSaveCallback(project);
            }
        }
    }

    private void ensureParentDirectoryExists(File source) {
        File parentDir = source.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                logger.info("Directory created: " + parentDir.getAbsolutePath());
            } else {
                logger.warning("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }
    }

    @Override
    public void initManager(File source) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (!source.exists()) {
                projectWrapper = new ProjectWrapper();
                saveData();
                attachCallbacksForAllProjects();
                return;
            }

            if (source.length() == 0) {
                logger.warning("projects.json is zero bytes. Attempting recovery from backup.");
                projectWrapper = tryRecoverFromBackup(objectMapper, source);
                if (projectWrapper == null) {
                    projectWrapper = new ProjectWrapper();
                }
                saveData();
                attachCallbacksForAllProjects();
                return;
            }

            projectWrapper = objectMapper.readValue(source, ProjectWrapper.class);
            if (projectWrapper == null) {
                projectWrapper = new ProjectWrapper();
            }
        } catch (IOException e) {
            logger.warning("Failed to read projects.json, attempting recovery: " + e.getMessage());
            projectWrapper = tryRecoverFromBackup(objectMapper, source);
            if (projectWrapper == null) {
                projectWrapper = new ProjectWrapper();
            }
        }

        attachCallbacksForAllProjects();
    }

    public void addProject(ProjectEntity project) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null) {
            projects = new ArrayList<>();
            projectWrapper.setProjects(projects);
        }

        int maxOrder = projects.stream()
                .mapToInt(ProjectEntity::getOrder)
                .max()
                .orElse(-1);

        if (maxOrder > Integer.MAX_VALUE - 1000) {
            resetProjectOrders();
            maxOrder = projects.stream()
                    .mapToInt(ProjectEntity::getOrder)
                    .max()
                    .orElse(-1);
        }

        project.setOrder(maxOrder + 1);
        projects.add(project);
        attachSaveCallback(project);
    }

    public void updateProject(String projectName, ProjectEntity updatedProject) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects != null) {
            for (int i = 0; i < projects.size(); i++) {
                if (projects.get(i).getName().equals(projectName)) {
                    projects.set(i, updatedProject);
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
            projects = removeDuplicateProjects(projects);
            projects.sort(Comparator.comparingInt(ProjectEntity::getOrder).reversed());
        }
        return projects;
    }

    public void setProjects(List<ProjectEntity> projects) {
        if (projectWrapper == null) {
            projectWrapper = new ProjectWrapper();
        }
        projectWrapper.setProjects(projects);
        attachCallbacksForAllProjects();
    }

    public synchronized boolean saveData() {
        ObjectMapper objectMapper = new ObjectMapper();
        Path sourcePath = this.source.toPath();
        try {
            if (projectWrapper == null) {
                projectWrapper = new ProjectWrapper();
            }

            List<ProjectEntity> projects = projectWrapper.getProjects();
            if (projects != null) {
                projects = removeDuplicateProjects(projects);
                projectWrapper.setProjects(projects);
            }

            Path parentDir = sourcePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Path tempPath = parentDir.resolve(sourcePath.getFileName() + ".tmp");
            Path backupPath = parentDir.resolve(sourcePath.getFileName() + ".bak");

            logger.info("Saving projects.json to " + sourcePath);

            try (RandomAccessFile lockFile = new RandomAccessFile(sourcePath.toFile(), "rw");
                 FileChannel lockChannel = lockFile.getChannel();
                 FileLock lock = lockChannel.lock()) {

                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), projectWrapper);

                try (FileChannel tempChannel = FileChannel.open(tempPath, StandardOpenOption.WRITE)) {
                    tempChannel.force(true);
                }

                if (Files.exists(sourcePath) && Files.size(sourcePath) > 0) {
                    Files.copy(sourcePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }

                try {
                    Files.move(tempPath, sourcePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tempPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            return true;
        } catch (IOException e) {
            logger.severe("Failed to save projects.json: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

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
        if (getProjects() != null) {
            for (ProjectEntity projectEntity : getProjects()) {
                if (projectEntity.getName().equals(projectName)) {
                    return projectEntity;
                }
            }
        }
        return null;
    }

    public ProjectEntity getProject(String projectName) {
        return getProjectByName(projectName);
    }

    public boolean projectExists(String projectName) {
        return getProjectByName(projectName) != null;
    }

    public void reorderProjects(List<String> projectNamesInOrder) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null || projectNamesInOrder == null) {
            return;
        }

        Map<String, ProjectEntity> projectMap = new HashMap<>();
        for (ProjectEntity project : projects) {
            projectMap.put(project.getName(), project);
        }

        for (int i = 0; i < projectNamesInOrder.size(); i++) {
            String projectName = projectNamesInOrder.get(i);
            ProjectEntity project = projectMap.get(projectName);
            if (project != null) {
                project.setOrder(projectNamesInOrder.size() - i);
            }
        }
    }

    public void moveProjectToTop(String projectName) {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null) {
            return;
        }

        projects = removeDuplicateProjects(projects);
        projectWrapper.setProjects(projects);

        ProjectEntity targetProject = null;
        int maxOrder = -1;

        for (ProjectEntity project : projects) {
            if (project.getName().equals(projectName)) {
                targetProject = project;
            }
            maxOrder = Math.max(maxOrder, project.getOrder());
        }

        if (targetProject != null) {
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

    private void resetProjectOrders() {
        List<ProjectEntity> projects = projectWrapper.getProjects();
        if (projects == null || projects.isEmpty()) {
            return;
        }

        projects.sort(Comparator.comparingInt(ProjectEntity::getOrder).reversed());
        for (int i = 0; i < projects.size(); i++) {
            projects.get(i).setOrder(i);
        }
    }

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
                    ProjectEntity existingProject = uniqueProjects.get(projectName);
                    if (project.getOrder() > existingProject.getOrder()) {
                        uniqueProjects.put(projectName, project);
                    }
                }
            }
        }

        return new ArrayList<>(uniqueProjects.values());
    }

    private ProjectWrapper tryRecoverFromBackup(ObjectMapper objectMapper, File source) {
        try {
            Path sourcePath = source.toPath();
            Path parentDir = sourcePath.getParent();
            if (parentDir == null) {
                return new ProjectWrapper();
            }

            if (Files.exists(sourcePath)) {
                String corruptName = source.getName() + ".corrupt." + System.currentTimeMillis();
                Files.copy(sourcePath, parentDir.resolve(corruptName), StandardCopyOption.REPLACE_EXISTING);
            }

            Path backupPath = parentDir.resolve(source.getName() + ".bak");
            if (Files.exists(backupPath) && Files.size(backupPath) > 0) {
                ProjectWrapper recovered = objectMapper.readValue(backupPath.toFile(), ProjectWrapper.class);
                if (recovered != null) {
                    logger.warning("Recovered projects.json from backup: " + backupPath);
                    return recovered;
                }
            }
        } catch (Exception ignored) {
            // Fall back to empty wrapper when recovery fails.
        }
        return new ProjectWrapper();
    }
}