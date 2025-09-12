package com.nms.support.nms_support.service.dataStoreTabPack;

import com.nms.support.nms_support.model.ProjectEntity;

import java.io.IOException;

import static com.nms.support.nms_support.service.globalPack.SSHExecutor.executeCommand;

/**
 *
 * @author Gnaneshwar
 */
public class ReportGenerator {
    public static boolean execute(ProjectEntity project, String dataStorePath) throws IOException {
        return execute(project, dataStorePath, null);
    }
    
    public static boolean execute(ProjectEntity project, String dataStorePath, String processKey) throws IOException {
        String host = project.getHost();
        String user = project.getHostUser();
        String password = project.getHostPass();
        int port = project.getHostPort() > 0 ? project.getHostPort() : 22;
        String ex_command = "Action any.publisher* ejb client "+project.getDataStoreUser()+" jbot_report "+dataStorePath.replace("\\","/");
        System.out.println(ex_command);
        String command = "bash --login -c '"+ex_command+"'";
        return executeCommand(host, user, password, command, port, processKey);
    }
}
