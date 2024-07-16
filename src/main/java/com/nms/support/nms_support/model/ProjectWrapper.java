package com.nms.support.nms_support.model;

import java.util.List;

public class ProjectWrapper {
    private List<ProjectEntity> projects;

    // Getters and setters
    public List<ProjectEntity> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectEntity> projects) {
        this.projects = projects;
    }

    @Override
    public String toString() {
        // If projects is null, represent it as "null"
        if (projects == null) {
            return "ProjectWrapper{projects=null}";
        }

        // Convert each ProjectEntity in the list to a string
        StringBuilder sb = new StringBuilder("ProjectWrapper{projects=[");
        for (int i = 0; i < projects.size(); i++) {
            sb.append(projects.get(i).toString()); // Calls toString() on each ProjectEntity
            if (i < projects.size() - 1) {
                sb.append(", "); // Separator between elements
            }
        }
        sb.append("]}");

        return sb.toString();
    }
}
