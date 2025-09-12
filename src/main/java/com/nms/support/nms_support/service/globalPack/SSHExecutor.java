package com.nms.support.nms_support.service.globalPack;

import java.io.IOException;
import com.jcraft.jsch.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Gnaneshwar
 */
public class SSHExecutor {

    private static HashMap<String, Session> cacheSessions = new HashMap<>();
    
    // Track active channels for process management
    private static final Map<String, ChannelExec> activeChannels = new ConcurrentHashMap<>();

    public static boolean executeCommand(String host, String user, String password, String command) throws IOException {
        return executeCommand(host, user, password, command, 22);
    }
    
    public static boolean executeCommand(String host, String user, String password, String command, int port) throws IOException {
        return executeCommand(host, user, password, command, port, null);
    }
    
    public static boolean executeCommand(String host, String user, String password, String command, int port, String processKey) throws IOException {
        try {
            String sessionKey = host+":"+user+":"+password;
            Session session = null;
            if(cacheSessions.containsKey(sessionKey)){
                session = cacheSessions.get(sessionKey);
                if(!session.isConnected()) {
                    session = null;
                    cacheSessions.remove(sessionKey);
                }
            }
            if(session == null) {
                JSch jsch = new JSch();
                session = jsch.getSession(user, host, port);
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                cacheSessions.put(sessionKey,session);
            }

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setPty(true);
            channelExec.setCommand(command);

            LoggerUtil.getLogger().info("Command: " + command);

            channelExec.setInputStream(null);
            channelExec.setErrStream(System.err);

            channelExec.connect();
            
            // Track the channel if processKey is provided
            if (processKey != null) {
                activeChannels.put(processKey, channelExec);
                LoggerUtil.getLogger().info("Tracking channel for process: " + processKey);
            }

            InputStream in = channelExec.getInputStream();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    System.out.print(new String(tmp, 0, i));
                }
                if (channelExec.isClosed()) {
                    if (in.available() > 0) continue;
                    System.out.println("exit-status: " + channelExec.getExitStatus());
                    break;
                }

            }
            boolean result = channelExec.getExitStatus() == 0;
            
            // Remove from tracking when done
            if (processKey != null) {
                activeChannels.remove(processKey);
                LoggerUtil.getLogger().info("Removed channel tracking for process: " + processKey);
            }
            
            channelExec.disconnect();
            //session.disconnect(); //this is not closing as sessions were maintained cache
            return result;
        } catch (JSchException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Kill an active process by its key
     */
    public static boolean killProcess(String processKey) {
        ChannelExec channel = activeChannels.get(processKey);
        if (channel != null && !channel.isClosed()) {
            try {
                LoggerUtil.getLogger().info("Killing process: " + processKey);
                channel.disconnect();
                activeChannels.remove(processKey);
                return true;
            } catch (Exception e) {
                LoggerUtil.getLogger().warning("Error killing process " + processKey + ": " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Check if a process is currently running
     */
    public static boolean isProcessRunning(String processKey) {
        ChannelExec channel = activeChannels.get(processKey);
        return channel != null && !channel.isClosed();
    }
    
    /**
     * Get all active process keys
     */
    public static String[] getActiveProcessKeys() {
        return activeChannels.keySet().toArray(new String[0]);
    }
    
    /**
     * Kill all active processes
     */
    public static void killAllProcesses() {
        LoggerUtil.getLogger().info("Killing all active processes");
        for (String processKey : activeChannels.keySet()) {
            killProcess(processKey);
        }
    }
}

