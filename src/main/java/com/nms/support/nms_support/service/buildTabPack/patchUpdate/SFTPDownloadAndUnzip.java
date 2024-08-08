package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import com.jcraft.jsch.*;
import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SFTPDownloadAndUnzip {
    private static BuildAutomation buildAutomation;
    private static final int THREAD_POOL_SIZE = 4;
    private static final int BUFFER_SIZE = 1024*5;
    private static final long PROGRESS_UPDATE_INTERVAL = 5000; // 5 seconds

    public static void start(String localExtractDir, ProjectEntity project, BuildAutomation buildAutomation) {
        SFTPDownloadAndUnzip.buildAutomation = buildAutomation;
    	buildAutomation.appendTextToLog("Getting java folder...");
//        String remoteHost = "ugbu-ash-120.sniadprshared1.gbucdsint02iad.oraclevcn.com";
//        String username = "evergy1";
//        String password = "Plannine1!";
        String remoteHost = project.getHost();
        String username = project.getHostUser();
        String password = project.getHostPass();
        String remoteDir = "java/";
        //String localExtractDir = "C:\\Oracle NMS\\results\\";
        String remoteZipFilePath = "/tmp/downloaded_java_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
        String localZipFilePath = "downloaded_java.zip";

        try {
            buildAutomation.appendTextToLog("Connecting to server...");
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, remoteHost, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            buildAutomation.appendTextToLog("Connected to server.");

            ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
            buildAutomation.appendTextToLog("Creating zip file on server (excluding java/working/*)...");
            execChannel.setCommand("zip -r " + remoteZipFilePath + " " + remoteDir + " -x " + "\"*working*\"");
            execChannel.connect();

            long startTime = System.currentTimeMillis();
            try (InputStream in = execChannel.getInputStream()) {
                byte[] tmp = new byte[BUFFER_SIZE];
                long lastProgressTime = System.currentTimeMillis();
                while (true) {
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, BUFFER_SIZE);
                        if (i < 0) break;
                        System.out.print(new String(tmp, 0, i));
                    }
                    if (execChannel.isClosed()) {
                        if (in.available() > 0) continue;
                        buildAutomation.appendTextToLog("Exit status: " + execChannel.getExitStatus());
                        break;
                    }
                    if (System.currentTimeMillis() - lastProgressTime >= PROGRESS_UPDATE_INTERVAL) {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        buildAutomation.appendTextToLog(String.format("Zip creation in progress... Elapsed Time: %.2f mins%n", elapsedTime / 60000.0));
                        lastProgressTime = System.currentTimeMillis();
                    }

                }
            }
            execChannel.disconnect();

            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            buildAutomation.appendTextToLog("SFTP channel opened and connected.");

            buildAutomation.appendTextToLog("Downloading zip file from server...");
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(localZipFilePath))) {
                long totalBytes = sftpChannel.lstat(remoteZipFilePath).getSize();
                long bytesDownloaded = 0;
                long lastProgressTime = System.currentTimeMillis();
                startTime = System.currentTimeMillis();
                try (InputStream inputStream = sftpChannel.get(remoteZipFilePath)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;

                        if (System.currentTimeMillis() - lastProgressTime >= PROGRESS_UPDATE_INTERVAL) {
                            long elapsedTime = (System.currentTimeMillis() - startTime)/(1000);//Seconds
                            double downloadSpeed = bytesDownloaded / (1024.0 * 1024.0 * elapsedTime); // MB/s
                            double remainingTime = ((totalBytes - bytesDownloaded) / (1024.0 * 1024.0)) / (downloadSpeed*60); // minutes

                            buildAutomation.appendTextToLog(String.format("Downloaded: %.3f MB / %.3f MB, Remaining: %.3f MB, Avg. Speed: %.3f MB/s, Estimated Time: %.2f mins%n",
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

            buildAutomation.appendTextToLog("Removing zip file from server...");
            sftpChannel.rm(remoteZipFilePath);

            sftpChannel.disconnect();
            session.disconnect();
            buildAutomation.appendTextToLog("Disconnected from server.");

            buildAutomation.appendTextToLog("Extracting the downloaded zip file...");
            unzipFile(localZipFilePath, localExtractDir);

            buildAutomation.appendTextToLog("Java Download and extraction completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void unzipFile(String zipFilePath, String destDir) throws IOException {
        buildAutomation.appendTextToLog("Unzipping file: " + zipFilePath + " to directory: " + destDir);
        long startTime = System.currentTimeMillis();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
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
