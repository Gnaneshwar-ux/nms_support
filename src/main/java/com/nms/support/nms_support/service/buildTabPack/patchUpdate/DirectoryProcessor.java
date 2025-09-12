package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class DirectoryProcessor {
    private static ProgressCallback progressCallback;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second
    
    /**
     * Safely copy a file with retry logic to handle file locks
     */
    private static void safeCopyFile(Path source, Path destination) throws IOException {
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                // Check if destination file is locked by trying to open it
                if (Files.exists(destination)) {
                    try (FileChannel channel = FileChannel.open(destination, StandardOpenOption.WRITE)) {
                        try (FileLock lock = channel.tryLock()) {
                            if (lock == null) {
                                throw new IOException("File is locked by another process: " + destination);
                            }
                        }
                    }
                }
                
                // Perform the copy operation
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                return; // Success, exit retry loop
                
            } catch (IOException e) {
                lastException = e;
                LoggerUtil.error(new IOException("Attempt " + attempt + " failed to copy " + source + " to " + destination + ": " + e.getMessage()));
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                        progressCallback.onProgress(0, "Retrying file copy (attempt " + (attempt + 1) + "/" + MAX_RETRY_ATTEMPTS + ")...");
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Copy operation interrupted", ie);
                    }
                }
            }
        }
        
        // If we get here, all retry attempts failed
        throw new IOException("Failed to copy file after " + MAX_RETRY_ATTEMPTS + " attempts: " + source + " to " + destination, lastException);
    }
    
    public static void processDirectory(String baseDir, ProgressCallback progressCallback) throws IOException {
        DirectoryProcessor.progressCallback = progressCallback;
        progressCallback.onProgress(10, "Starting directory processing...");
        
        File javaLibDir = new File(baseDir, "java/lib");
        File nmsLibDir = new File(baseDir, "nmslib");
        File productDir = new File(baseDir, "java/product");
        File destLogoFile = new File(baseDir, "logo16.ico");
        File destLicenseFile = new File(baseDir, "license.properties");
        File destNSIFile = new File(baseDir, "nms.nsi");

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            progressCallback.onError("Directory processing cancelled by user");
            return;
        }

        // Ensure destination directory exists
        progressCallback.onProgress(15, "Creating nmslib directory...");
        if (!nmsLibDir.exists()) {
            boolean created = nmsLibDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create directory: " + nmsLibDir);
            }
        }

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            progressCallback.onError("Directory processing cancelled by user");
            return;
        }

        // Process .jar files in java/lib directory
        progressCallback.onProgress(20, "Processing JAR files...");
        if (javaLibDir.exists() && javaLibDir.isDirectory()) {
            File[] jarFiles = javaLibDir.listFiles((_, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                int totalJars = jarFiles.length;
                int processedJars = 0;
                
                for (File jarFile : jarFiles) {
                    // Check for cancellation
                    if (progressCallback.isCancelled()) {
                        progressCallback.onError("Directory processing cancelled by user");
                        return;
                    }
                    
                    File destFile = new File(nmsLibDir, jarFile.getName());
                    try {
                        safeCopyFile(jarFile.toPath(), destFile.toPath());
                        processedJars++;
                        
                        int progress = 20 + (int)((processedJars * 20.0) / totalJars); // 20-40% range
                        progressCallback.onProgress(progress, "Copied: " + jarFile.getName() + " to " + nmsLibDir.getPath());
                    } catch (IOException e) {
                        LoggerUtil.error(new IOException("Failed to copy JAR file: " + jarFile.getName() + " - " + e.getMessage()));
                        progressCallback.onError("Directory processing failed: " + e.getMessage());
                        throw e;
                    }
                }
            }
        }

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            progressCallback.onError("Directory processing cancelled by user");
            return;
        }

        // Search and copy logo16.ico file
        progressCallback.onProgress(45, "Searching for logo16.ico file...");
        File logoFile = findFile(productDir, "logo16.ico");
        if (logoFile != null) {
            safeCopyFile(logoFile.toPath(), destLogoFile.toPath());
            progressCallback.onProgress(50, "Copied logo16.ico to " + destLogoFile.getPath());
        } else {
            throw new IOException("logo16.ico not found in any subdirectory of " + productDir.getPath());
        }

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            progressCallback.onError("Directory processing cancelled by user");
            return;
        }

        // Search and copy license.properties file
        progressCallback.onProgress(55, "Searching for license.properties file...");
        File licenseFile = findFile(productDir, "license.properties");
        if (licenseFile != null) {
            safeCopyFile(licenseFile.toPath(), destLicenseFile.toPath());
            progressCallback.onProgress(60, "Copied license.properties to " + destLicenseFile.getPath());
        } else {
            throw new IOException("license.properties not found in any subdirectory of " + productDir.getPath());
        }

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            progressCallback.onError("Directory processing cancelled by user");
            return;
        }

        // Search and copy nms.nsi file
        progressCallback.onProgress(65, "Searching for nms.nsi file...");
        File nsiFile = findFile(productDir, "nms.nsi");
        if (nsiFile != null) {
            safeCopyFile(nsiFile.toPath(), destNSIFile.toPath());
            progressCallback.onProgress(70, "Copied nms.nsi to " + destNSIFile.getPath());
        } else {
            throw new IOException("nms.nsi not found in any subdirectory of " + productDir.getPath());
        }

        progressCallback.onProgress(100, "Directory processing completed successfully");
        progressCallback.onComplete("Directory processing completed successfully");
    }

    private static File findFile(File dir, String fileName) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File result = findFile(file, fileName);
                    if (result != null) {
                        return result;
                    }
                } else if (file.isFile() && file.getName().equals(fileName)) {
                    return file;
                }
            }
        }

        return null;
    }

    public static void copyDirectory(File source, File destination, ProgressCallback progressCallback) throws IOException {
        DirectoryProcessor.progressCallback = progressCallback;
        
        if (!source.exists()) {
            throw new IOException("Source directory does not exist: " + source.getAbsolutePath());
        }

        if (source.isDirectory()) {
            // Ensure destination directory exists
            if (!destination.exists()) {
                if (!destination.mkdirs()) {
                    throw new IOException("Failed to create directory: " + destination.getAbsolutePath());
                }
            }

            // List all files and directories in the source directory
            String[] files = source.list();
            if (files != null) {
                int totalFiles = files.length;
                int processedFiles = 0;
                
                for (String file : files) {
                    // Check for cancellation
                    if (progressCallback.isCancelled()) {
                        progressCallback.onError("Directory copying cancelled by user");
                        return;
                    }
                    
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);
                    if (srcFile.isDirectory()) {
                        copyDirectory(srcFile, destFile, progressCallback); // Recursive call
                    } else {
                        safeCopyFile(srcFile.toPath(), destFile.toPath());
                        processedFiles++;
                        
                        int progress = (int)((processedFiles * 100.0) / totalFiles);
                        progressCallback.onProgress(progress, "Copied: " + file);
                    }
                }
            }
        } else {
            throw new IOException("Source is not a directory: " + source.getAbsolutePath());
        }
    }

    public static boolean cleanDirectory(Path dir, ProgressCallback progressCallback) throws IOException {
        DirectoryProcessor.progressCallback = progressCallback;
        
        if (!Files.exists(dir)) {
            progressCallback.onProgress(0, "Directory does not exist: " + dir);
            return false;
        }
        if (!Files.isDirectory(dir)) {
            progressCallback.onProgress(0, "Not a directory: " + dir);
            return false;
        }

        progressCallback.onProgress(10, "Starting directory cleanup...");

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // Check for cancellation
                if (progressCallback.isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }
                
                Files.delete(file);
                progressCallback.onProgress(50, "Deleted file: " + file.getFileName());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // Check for cancellation
                if (progressCallback.isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }
                
                if (exc == null) {
                    Files.delete(dir);
                    progressCallback.onProgress(75, "Deleted directory: " + dir.getFileName());
                    return FileVisitResult.CONTINUE;
                } else {
                    throw exc;
                }
            }
        });

        progressCallback.onProgress(100, "Directory cleanup completed");
        progressCallback.onComplete("Directory cleanup completed successfully");
        return true;
    }
}
