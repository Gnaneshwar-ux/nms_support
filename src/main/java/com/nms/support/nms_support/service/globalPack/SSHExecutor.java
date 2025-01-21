package com.nms.support.nms_support.service.globalPack;

import java.io.IOException;
import com.jcraft.jsch.*;
import java.io.InputStream;
import java.util.HashMap;

/**
 *
 * @author Gnaneshwar
 */
public class SSHExecutor {

    private static HashMap<String, Session> cacheSessions = new HashMap<>();

    public static boolean executeCommand(String host_string, String user, String password, String command) throws IOException {
        try {
            String host = host_string.split(":")[0];
            String port;

            if (host_string.contains(":")) {
                port = host_string.split(":")[1];  // Split by ':' and take the second part (port)
            } else {
                port = "22";  // Default port if no ':' is found
            }
            String sessionKey = host_string+":"+user+":"+password;
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
                session = jsch.getSession(user, host, Integer.parseInt(port));
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
            channelExec.disconnect();
            //session.disconnect(); //this is not closing as sessions were maintained cache
            return result;
        } catch (JSchException e) {
            e.printStackTrace();
            return false;
        }
    }
}

