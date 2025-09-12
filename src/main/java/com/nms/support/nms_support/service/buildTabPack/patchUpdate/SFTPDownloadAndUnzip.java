package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import com.jcraft.jsch.*;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SFTPDownloadAndUnzip {
    private static ProgressCallback progressCallback;
    private static final int BUFFER_SIZE = 1024*5;
    private static final long PROGRESS_UPDATE_INTERVAL = 5000; // 5 seconds

    public static void start(String localExtractDir, ProjectEntity project, ProgressCallback progressCallback) {
        SFTPDownloadAndUnzip.progressCallback = progressCallback;
        progressCallback.onProgress(10, "Getting java folder...");
//        String remoteHost = "ugbu-ash-120.sniadprshared1.gbucdsint02iad.oraclevcn.com";
//        String username = "evergy1";
//        String password = "Plannine1!";
        String remoteHost = project.getHost();
        String port = String.valueOf(project.getHostPort() > 0 ? project.getHostPort() : 22);
        String username = project.getHostUser();
        String password = project.getHostPass();
        String remoteDir = "java/";
        //String localExtractDir = "C:\\Oracle NMS\\results\\";
        String remoteZipFilePath = "/tmp/downloaded_java_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
        String localZipFilePath = "downloaded_java.zip";

        Session session = null;
        ChannelSftp sftpChannel = null;
        ChannelExec execChannel = null;
        
        try {
            // Check for cancellation before starting
            if (progressCallback.isCancelled()) {
                progressCallback.onError("SFTP download cancelled by user before starting");
                return;
            }
            
            progressCallback.onProgress(20, "Connecting to server...");
            JSch jsch = new JSch();
            session = jsch.getSession(username, remoteHost, Integer.parseInt(port));
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            progressCallback.onProgress(30, "Connected to server.");

            execChannel = (ChannelExec) session.openChannel("exec");
            progressCallback.onProgress(40, "Creating zip file on server (excluding java/working/*)...");
            execChannel.setCommand("zip -r " + remoteZipFilePath + " " + remoteDir + " -x " + "\"*working*\"");
            execChannel.connect();

            long startTime = System.currentTimeMillis();
            try (InputStream in = execChannel.getInputStream()) {
                byte[] tmp = new byte[BUFFER_SIZE];
                long lastProgressTime = System.currentTimeMillis();
                while (true) {
                    // Check for cancellation
                    if (progressCallback.isCancelled()) {
                        cleanupResources(execChannel, sftpChannel, session, remoteZipFilePath, localZipFilePath);
                        progressCallback.onError("Zip creation cancelled by user");
                        return;
                    }
                    
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, BUFFER_SIZE);
                        if (i < 0) break;
                        System.out.print(new String(tmp, 0, i));
                    }
                    if (execChannel.isClosed()) {
                        if (in.available() > 0) continue;
                        progressCallback.onProgress(50, "Exit status: " + execChannel.getExitStatus());
                        break;
                    }
                    if (System.currentTimeMillis() - lastProgressTime >= PROGRESS_UPDATE_INTERVAL) {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        progressCallback.onProgress(45, String.format("Zip creation in progress... Elapsed Time: %.2f mins%n", elapsedTime / 60000.0));
                        lastProgressTime = System.currentTimeMillis();
                    }

                }
            }
            execChannel.disconnect();

            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            progressCallback.onProgress(55, "SFTP channel opened and connected.");

            progressCallback.onProgress(60, "Downloading zip file from server...");
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(localZipFilePath))) {
                long totalBytes = sftpChannel.lstat(remoteZipFilePath).getSize();
                long bytesDownloaded = 0;
                long lastProgressTime = System.currentTimeMillis();
                startTime = System.currentTimeMillis();
                try (InputStream inputStream = sftpChannel.get(remoteZipFilePath)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        // Check for cancellation
                        if (progressCallback.isCancelled()) {
                            cleanupResources(execChannel, sftpChannel, session, remoteZipFilePath, localZipFilePath);
                            progressCallback.onError("SFTP download cancelled by user");
                            return;
                        }
                        
                        outputStream.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;

                        if (System.currentTimeMillis() - lastProgressTime >= PROGRESS_UPDATE_INTERVAL) {
                            long elapsedTime = (System.currentTimeMillis() - startTime)/(1000);//Seconds
                            double downloadSpeed = bytesDownloaded / (1024.0 * 1024.0 * elapsedTime); // MB/s
                            double remainingTime = ((totalBytes - bytesDownloaded) / (1024.0 * 1024.0)) / (downloadSpeed*60); // minutes
                            int progress = (int)(60 + (bytesDownloaded * 30 / totalBytes)); // 60-90% range

                            progressCallback.onProgress(progress, String.format("Downloaded: %.3f MB / %.3f MB, Remaining: %.3f MB, Avg. Speed: %.3f MB/s, Estimated Time: %.2f mins%n",
                                    bytesDownloaded / (1024.0 * 1024.0),
                                    totalBytes / (1024.0 * 1024.0),
                                    (totalBytes - bytesDownloaded) / (1024.0 * 1024.0),
                                    downloadSpeed,
                                    remainingTime));
                            lastProgressTime = System.currentTimeMillis();
                        }
                    }
                }
            }

            progressCallback.onProgress(90, "Removing zip file from server...");
            sftpChannel.rm(remoteZipFilePath);

            sftpChannel.disconnect();
            session.disconnect();
            progressCallback.onProgress(92, "Disconnected from server.");

            progressCallback.onProgress(95, "Extracting the downloaded zip file...");
            unzipFile(localZipFilePath, localExtractDir);

            progressCallback.onComplete("Java Download and extraction completed successfully.");
        } catch (Exception e) {
            LoggerUtil.getLogger().severe(e.getMessage());
            LoggerUtil.error(e);
            // Clean up resources on error
            cleanupResources(execChannel, sftpChannel, session, remoteZipFilePath, localZipFilePath);
            progressCallback.onError("Error: " + e.getMessage());
        }
    }
    
    /**
     * Clean up server and local resources
     */
    private static void cleanupResources(ChannelExec execChannel, ChannelSftp sftpChannel, Session session, 
                                       String remoteZipFilePath, String localZipFilePath) {
        try {
            // Clean up server-side zip file if it exists
            if (sftpChannel != null && sftpChannel.isConnected()) {
                try {
                    sftpChannel.rm(remoteZipFilePath);
                    progressCallback.onProgress(0, "Cleaned up server zip file: " + remoteZipFilePath);
                } catch (Exception e) {
                    LoggerUtil.error(new Exception("Failed to remove server zip file: " + remoteZipFilePath, e));
                }
            }
            
            // Clean up local zip file if it exists
            File localZipFile = new File(localZipFilePath);
            if (localZipFile.exists()) {
                if (localZipFile.delete()) {
                    progressCallback.onProgress(0, "Cleaned up local zip file: " + localZipFilePath);
                } else {
                    LoggerUtil.error(new Exception("Failed to delete local zip file: " + localZipFilePath));
                }
            }
            
        } catch (Exception e) {
            LoggerUtil.error(new Exception("Error during cleanup", e));
        } finally {
            // Disconnect channels and session
            try {
                if (execChannel != null && execChannel.isConnected()) {
                    execChannel.disconnect();
                }
            } catch (Exception e) {
                LoggerUtil.error(new Exception("Error disconnecting exec channel", e));
            }
            
            try {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                }
            } catch (Exception e) {
                LoggerUtil.error(new Exception("Error disconnecting SFTP channel", e));
            }
            
            try {
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception e) {
                LoggerUtil.error(new Exception("Error disconnecting session", e));
            }
        }
    }

    private static void unzipFile(String zipFilePath, String destDir) throws IOException {
        progressCallback.onProgress(95, "Unzipping file: " + zipFilePath + " to directory: " + destDir);
        long startTime = System.currentTimeMillis();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                // Check for cancellation
                if (progressCallback.isCancelled()) {
                    // Clean up local zip file on cancellation
                    File localZipFile = new File(zipFilePath);
                    if (localZipFile.exists()) {
                        localZipFile.delete();
                        progressCallback.onProgress(0, "Cleaned up local zip file after cancellation");
                    }
                    progressCallback.onError("Unzip process cancelled by user");
                    return;
                }
                
                File newFile = newFile(new File(destDir), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            // Check for cancellation during file extraction
                            if (progressCallback.isCancelled()) {
                                // Clean up local zip file on cancellation
                                File localZipFile = new File(zipFilePath);
                                if (localZipFile.exists()) {
                                    localZipFile.delete();
                                    progressCallback.onProgress(0, "Cleaned up local zip file after cancellation");
                                }
                                progressCallback.onError("Unzip process cancelled by user");
                                return;
                            }
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.printf("Extraction completed in %.2f minutes%n", elapsedTime / 60000.0);
        
        // Clean up local zip file after successful extraction
        File localZipFile = new File(zipFilePath);
        if (localZipFile.exists()) {
            if (localZipFile.delete()) {
                progressCallback.onProgress(0, "Cleaned up local zip file after successful extraction");
            } else {
                LoggerUtil.error(new Exception("Failed to delete local zip file after extraction: " + zipFilePath));
            }
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }
}
