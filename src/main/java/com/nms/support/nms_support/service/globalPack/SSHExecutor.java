package com.nms.support.nms_support.service.globalPack;

import java.io.IOException;
import com.jcraft.jsch.*;
import java.io.InputStream;

/**
 *
 * @author Gnaneshwar
 */
public class SSHExecutor {

    public static boolean executeCommand(String host, String user, String password, String command) throws IOException {
        try {

            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setPty(true);
            channelExec.setCommand(command);

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
                try {
                    Thread.sleep(1000);
                } catch (Exception ee) {
                    ee.printStackTrace();
                }
            }
            boolean result = channelExec.getExitStatus() == 0;
            channelExec.disconnect();
            session.disconnect();
            return result;
        } catch (JSchException e) {
            e.printStackTrace();
            return false;
        }
    }
}

