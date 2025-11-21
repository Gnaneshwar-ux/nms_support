package com.nms.support.nms_support.service.globalPack;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.application.Platform;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.api.printer.Printer;
import org.jd.core.v1.api.Decompiler;
import org.jd.core.v1.ClassFileToJavaSourceDecompiler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Service for handling JAR decompilation operations
 */
public class JarDecompilerService extends Service<Void> {
    
    private static final Logger logger = LoggerUtil.getLogger();
    
    private String jarPath;
    private String projectName;
    private List<String> selectedJars;
    private String extractedPath;
    
    // Progress tracking
    private int totalJars;
    private int processedJars;
    private int totalClasses;
    private int completedClasses;
    
    // Message property for dynamic updates
    private final StringProperty currentMessage = new SimpleStringProperty();
    
    public JarDecompilerService() {
        // Initialize with default values
    }
    
    /**
     * Set the JAR path and project name for decompilation
     */
    public void setDecompilationParams(String jarPath, String projectName, List<String> selectedJars) {
        this.jarPath = jarPath;
        this.projectName = projectName;
        this.selectedJars = selectedJars != null ? new ArrayList<>(selectedJars) : new ArrayList<>();
        this.totalJars = selectedJars.size();
        this.processedJars = 0;
        this.totalClasses = 0;
        this.completedClasses = 0;
    }
    
    /**
     * Get the extracted path where decompiled files are stored
     */
    public String getExtractedPath() {
        return extractedPath;
    }
    
    /**
     * Get progress percentage
     */
    public double getProgressPercentage() {
        if (totalJars == 0) return 0.0;
        return (double) processedJars / totalJars;
    }
    
    /**
     * Get the current message property for dynamic updates
     */
    public StringProperty currentMessageProperty() {
        return currentMessage;
    }
    
    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (jarPath == null || jarPath.trim().isEmpty()) {
                    throw new IllegalArgumentException("JAR path cannot be empty");
                }
                
                if (projectName == null || projectName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Project name cannot be empty");
                }
                
                if (selectedJars == null || selectedJars.isEmpty()) {
                    throw new IllegalArgumentException("No JAR files selected for decompilation");
                }
                
                logger.info("Starting JAR decompilation for project: " + projectName);
                logger.info("JAR path: " + jarPath);
                logger.info("Selected JARs: " + selectedJars);
                

                // Create extraction directory
                createExtractionDirectory();
                
                // Initialize progress to 0%
                updateProgress(0, totalJars > 0 ? totalJars : 1);
                
                // First pass: Count total classes in all selected JARs
                updateMessage("Scanning JAR files...");
                Platform.runLater(() -> currentMessage.set("Scanning JAR files..."));
                
                for (String jarName : selectedJars) {
                    String jarFilePath = resolveJarFilePath(jarName);
                    if (jarFilePath == null) {
                        logger.warning("JAR file not found in configured paths for: " + jarName);
                        continue;
                    }
                    try (JarFile jarFile = new JarFile(jarFilePath)) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                                totalClasses++;
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Error counting classes in JAR " + jarName + ": " + e.getMessage());
                    }
                }
                logger.info("Total classes to decompile: " + totalClasses);
                updateMessage("Found " + totalClasses + " classes in " + totalJars + " JARs");
                Platform.runLater(() -> currentMessage.set("Found " + totalClasses + " classes in " + totalJars + " JARs"));
                
                // Second pass: Decompile each selected JAR
                for (String jarName : selectedJars) {
                    if (isCancelled()) {
                        logger.info("Decompilation cancelled by user");
                        break;
                    }
                    
                    try {
                        decompileJar(jarName, this);  // Pass Task reference
                        processedJars++;
                        // Only update progress if totalJars is greater than 0
                        if (totalJars > 0) {
                            updateProgress(processedJars, totalJars);
                        }
                    } catch (Exception e) {
                        logger.severe("Error decompiling JAR " + jarName + ": " + e.getMessage());
                        // Continue with other JARs even if one fails
                    }
                }
                
                logger.info("JAR decompilation completed. Files extracted to: " + extractedPath);
                updateMessage("Decompilation completed: " + completedClasses + "/" + totalClasses + " classes");
                Platform.runLater(() -> currentMessage.set("Decompilation completed: " + completedClasses + "/" + totalClasses + " classes"));
                
                // Set progress to 100% completion
                updateProgress(totalJars, totalJars);

                return null;
            }
        };
    }
    
    /**
     * Create the extraction directory structure
     * Cleans existing decompiled files before creating new ones
     */
    private void createExtractionDirectory() throws IOException {
        String userHome = System.getProperty("user.home");
        extractedPath = Paths.get(userHome, "Documents", "nms_support_data", "extracted", projectName + "_JAR_DECOMPILED").toString();
        
        Path extractionDir = Paths.get(extractedPath);
        if (Files.exists(extractionDir)) {
            logger.info("Cleaning existing extraction directory: " + extractedPath);
            // Delete all existing decompiled files before decompiling again
            deleteDirectoryContents(extractionDir.toFile());
            logger.info("Deleted all existing decompiled files from: " + extractedPath);
        }
        
        // Create or recreate the extraction directory
        Files.createDirectories(extractionDir);
        logger.info("Created extraction directory: " + extractedPath);
    }
    
    /**
     * Delete all contents of a directory recursively (but keep the directory itself)
     */
    private void deleteDirectoryContents(File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively delete subdirectory and its contents
                    deleteDirectoryRecursive(file);
                } else {
                    // Delete file
                    if (!file.delete()) {
                        logger.warning("Failed to delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }
    
    /**
     * Delete a directory and all its contents recursively
     */
    private void deleteDirectoryRecursive(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }
        
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursive(file);
                }
            }
        }
        
        if (!directory.delete()) {
            logger.warning("Failed to delete directory: " + directory.getAbsolutePath());
        }
    }
    
    /**
     * Decompile a single JAR file
     * Using generic Task type to allow updateTaskMessage() calls
     */
    private void decompileJar(String jarName, Task<?> task) throws Exception {
        String jarFilePath = resolveJarFilePath(jarName);
        if (jarFilePath == null) {
            throw new IOException("JAR file not found in configured paths: " + jarName);
        }
        File jarFile = new File(jarFilePath);
        
        if (!jarFile.exists()) {
            throw new IOException("JAR file not found: " + jarFilePath);
        }
        
        logger.info("Decompiling JAR: " + jarName);
        
        // Create output directory for this JAR
        String jarOutputDir = Paths.get(extractedPath, jarName.replace(".jar", "")).toString();
        Files.createDirectories(Paths.get(jarOutputDir));
        
        // Use JD-Core decompiler for accurate decompilation
        decompileWithJDCore(jarFilePath, jarOutputDir, task);
        
        logger.info("Successfully decompiled JAR: " + jarName);
    }
    
    /**
     * Decompile JAR using JD-Core decompiler
     * Using generic Task type to allow updateTaskMessage() calls
     */
    private void decompileWithJDCore(String jarFilePath, String jarOutputDir, Task<?> task) {
        try {
            logger.info("Starting JD-Core decompilation for: " + jarFilePath);
            
            try (JarFile jarFile = new JarFile(jarFilePath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                List<JarEntry> classEntries = new ArrayList<>();
                
                // Collect all class entries first
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                        classEntries.add(entry);
                    }
                }
                
                logger.info("Found " + classEntries.size() + " class files to decompile");
                
                // Create output directory
                Files.createDirectories(Paths.get(jarOutputDir));
                
                // Decompile each class
                int processedCount = 0;
                for (JarEntry entry : classEntries) {
                    try {
                        String className = entry.getName().replace("/", ".").replace(".class", "");
                        byte[] classBytes = readAllBytes(jarFile.getInputStream(entry));
                        
                        // Create JD-Core components
                        Loader loader = new InMemoryLoader(className, classBytes);
                        Printer printer = new StringBuilderPrinter();
                        Decompiler decompiler = new ClassFileToJavaSourceDecompiler();
                        
                        // Decompile the class
                        decompiler.decompile(loader, printer, className);
                        
                        // Write decompiled source to file
                        String decompiledSource = printer.toString();
                        
                        // Add package declaration if missing
                        String packageName = getPackageName(className);
                        if (packageName != null && !decompiledSource.contains("package " + packageName)) {
                            decompiledSource = "package " + packageName + ";\n\n" + decompiledSource;
                        }
                        
                        Path outFile = Paths.get(jarOutputDir, entry.getName().replace(".class", ".java"));
                        Files.createDirectories(outFile.getParent());
                        Files.write(outFile, decompiledSource.getBytes());
                        
                        processedCount++;
                        completedClasses++;
                        
                        // Update message every 10 classes or every 10% of total, whichever is less frequent
                        int updateInterval = Math.max(10, totalClasses / 100);
                        if (completedClasses % updateInterval == 0 || completedClasses == totalClasses) {
                            // Update the message property directly
                            Platform.runLater(() -> {
                                currentMessage.set("Decompiling: " + completedClasses + "/" + totalClasses + " classes");
                            });
                        }
                        
                        if (processedCount % 50 == 0) {
                            logger.info("Processed " + processedCount + "/" + classEntries.size() + " classes in current JAR (Total: " + completedClasses + "/" + totalClasses + ")");
                        }
                        
                    } catch (Exception e) {
                        logger.warning("Failed to decompile class " + entry.getName() + ": " + e.getMessage());
                        // Continue with other classes
                    }
                }
                
                logger.info("JD-Core decompilation completed. Processed " + processedCount + "/" + classEntries.size() + " classes");
            }
            
        } catch (Exception e) {
            logger.severe("Error running JD-Core decompiler: " + e.getMessage());
            // Fallback to placeholder if JD-Core fails
            createPlaceholderDecompiledFiles(jarFilePath, jarOutputDir);
        }
    }
    
    /**
     * Create placeholder decompiled files as fallback
     */
    private void createPlaceholderDecompiledFiles(String jarFilePath, String jarOutputDir) {
        try {
            try (JarFile jarFile = new JarFile(jarFilePath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    
                    if (entryName.endsWith(".class") && !entry.isDirectory()) {
                        String javaFileName = entryName.replace(".class", ".java");
                        String javaFilePath = Paths.get(jarOutputDir, javaFileName).toString();
                        
                        File javaFile = new File(javaFilePath);
                        javaFile.getParentFile().mkdirs();
                        
                        String className = javaFileName.substring(javaFileName.lastIndexOf('/') + 1).replace(".java", "");
                        String packageName = entryName.substring(0, entryName.lastIndexOf('/')).replace('/', '.');
                        
                        String javaContent = createPlaceholderJavaContent(packageName, className);
                        Files.write(Paths.get(javaFilePath), javaContent.getBytes());
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Error creating placeholder files: " + e.getMessage());
        }
    }
    
    /**
     * Create placeholder Java content
     */
    private String createPlaceholderJavaContent(String packageName, String className) {
        StringBuilder content = new StringBuilder();
        
        if (!packageName.isEmpty()) {
            content.append("package ").append(packageName).append(";\n\n");
        }
        
        content.append("// Decompiled class: ").append(className).append("\n");
        content.append("// CFR decompilation failed, showing placeholder\n\n");
        content.append("public class ").append(className).append(" {\n");
        content.append("    // Placeholder content - CFR decompilation failed\n");
        content.append("}\n");
        
        return content.toString();
    }
    
    /**
     * Get list of JAR files in the specified directory
     */
    public static List<String> getJarFiles(String directoryPath) {
        List<String> jarFiles = new ArrayList<>();
        
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            return jarFiles;
        }
        
        try {
            File directory = new File(directoryPath);
            if (!directory.exists() || !directory.isDirectory()) {
                logger.warning("Directory does not exist or is not a directory: " + directoryPath);
                return jarFiles;
            }
            
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (files != null) {
                jarFiles = Arrays.stream(files)
                    .map(File::getName)
                    .sorted()
                    .collect(Collectors.toList());
            }
            
            logger.info("Found " + jarFiles.size() + " JAR files in directory: " + directoryPath);
            
        } catch (Exception e) {
            logger.severe("Error reading directory " + directoryPath + ": " + e.getMessage());
        }
        
        return jarFiles;
    }
    
    /**
     * Get list of JAR files across multiple directories (semicolon-separated support)
     */
    public static List<String> getJarFilesFromPaths(List<String> directories) {
        List<String> jarNames = new ArrayList<>();
        if (directories == null || directories.isEmpty()) return jarNames;
        try {
            Map<String, File> seen = new HashMap<>();
            for (String dirPath : directories) {
                if (dirPath == null || dirPath.trim().isEmpty()) continue;
                File directory = new File(dirPath.trim());
                if (!directory.exists() || !directory.isDirectory()) {
                    logger.warning("Directory does not exist or is not a directory: " + dirPath);
                    continue;
                }
                File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        if (!seen.containsKey(name)) {
                            seen.put(name, f);
                            jarNames.add(name);
                        }
                    }
                }
            }
            Collections.sort(jarNames);
            logger.info("Found " + jarNames.size() + " JAR files across " + directories.size() + " directories");
        } catch (Exception e) {
            logger.severe("Error reading multiple directories: " + e.getMessage());
        }
        return jarNames;
    }
    
    /**
     * Parse semicolon-separated jarPath into valid directory list
     */
    private List<String> parseJarPathDirs(String jarPath) {
        List<String> dirs = new ArrayList<>();
        if (jarPath == null) return dirs;
        String[] parts = jarPath.split(";");
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            File d = new File(s);
            if (d.exists() && d.isDirectory()) {
                dirs.add(d.getAbsolutePath());
            }
        }
        return dirs;
    }
    
    /**
     * Resolve a JAR file path by searching all configured directories
     */
    private String resolveJarFilePath(String jarName) {
        // Fast path for single directory
        if (jarPath != null && !jarPath.contains(";")) {
            String p = Paths.get(jarPath, jarName).toString();
            if (new File(p).exists()) return p;
        }
        for (String dir : parseJarPathDirs(jarPath)) {
            String candidate = Paths.get(dir, jarName).toString();
            if (new File(candidate).exists()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Resolve a JAR file path by searching a semicolon-separated list of directories.
     * This static helper also tries "<dir>/nmslib/<jarName>" in case a product folder is provided.
     */
    public static String resolveJarFilePath(String jarDirs, String jarName) {
        if (jarDirs == null || jarDirs.trim().isEmpty() || jarName == null || jarName.trim().isEmpty()) {
            return null;
        }

        // Fast path when a single directory is provided
        if (!jarDirs.contains(";")) {
            String direct = Paths.get(jarDirs, jarName).toString();
            if (new File(direct).exists()) return direct;

            // Also try "<dir>/nmslib/<jarName>" when a product folder is provided
            String withNmslib = Paths.get(jarDirs, "nmslib", jarName).toString();
            if (new File(withNmslib).exists()) return withNmslib;
        }

        // Split semicolon-separated directories and try each
        String[] parts = jarDirs.split(";");
        for (String part : parts) {
            String dir = part.trim();
            if (dir.isEmpty()) continue;

            String candidate = Paths.get(dir, jarName).toString();
            if (new File(candidate).exists()) return candidate;

            // Support product folder input by trying nmslib subfolder
            String candidateNmslib = Paths.get(dir, "nmslib", jarName).toString();
            if (new File(candidateNmslib).exists()) return candidateNmslib;
        }

        return null;
    }
    
    /**
     * Get list of class files in a JAR
     */
    public static List<String> getClassFilesInJar(String jarPath, String jarName) {
        List<String> classFiles = new ArrayList<>();
        
        try {
            String fullJarPath = Paths.get(jarPath, jarName).toString();
            try (JarFile jarFile = new JarFile(fullJarPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    
                    if (entryName.endsWith(".class") && !entry.isDirectory()) {
                        // Convert to package.class format for display
                        String className = entryName.replace('/', '.').replace(".class", "");
                        classFiles.add(className);
                    }
                }
            }
            
            // Sort class names
            Collections.sort(classFiles);
            logger.info("Found " + classFiles.size() + " class files in JAR: " + jarName);
            
        } catch (IOException e) {
            logger.severe("Error reading JAR file " + jarName + ": " + e.getMessage());
        }
        
        return classFiles;
    }
    
    /**
     * Get decompiled Java code for a specific class
     */
    public static String getDecompiledCode(String extractedPath, String jarName, String className) {
        try {
            // Convert class name to file path
            String fileName = className.replace('.', File.separatorChar) + ".java";
            String jarOutputDir = Paths.get(extractedPath, jarName.replace(".jar", "")).toString();
            String filePath = Paths.get(jarOutputDir, fileName).toString();
            
            File javaFile = new File(filePath);
            if (javaFile.exists()) {
                return Files.readString(Paths.get(filePath));
            } else {
                logger.warning("Decompiled Java file not found: " + filePath);
                return "// Decompiled Java file not found: " + className;
            }
            
        } catch (IOException e) {
            logger.severe("Error reading decompiled code for " + className + ": " + e.getMessage());
            return "// Error reading decompiled code: " + e.getMessage();
        }
    }
    
    /**
     * Get classes from a JAR file
     */
    public static List<String> getClassesFromJar(String jarPath) {
        List<String> classes = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("$")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    classes.add(className);
                }
            }
        } catch (IOException e) {
            logger.warning("Error reading JAR file " + jarPath + ": " + e.getMessage());
        }
        return classes;
    }
    
    /**
     * Get extraction directory for a project
     */
    public static String getExtractionDirectory(String projectName) {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "Documents", "nms_support_data", "extracted", projectName + "_JAR_DECOMPILED").toString();
    }
    
    /**
     * Load existing decompiled classes and files when project is selected
     */
    public static void loadExistingDecompiledData(String projectName, List<String> existingClasses, List<String> existingJars) {
        try {
            // Get existing decompiled classes
            List<String> classes = getExistingDecompiledClasses(projectName);
            existingClasses.clear();
            existingClasses.addAll(classes);
            
            // Get existing decompiled JARs
            List<String> jars = getExistingDecompiledJars(projectName);
            existingJars.clear();
            existingJars.addAll(jars);
            
            logger.info("Loaded " + classes.size() + " existing decompiled classes and " + jars.size() + " JARs for project: " + projectName);
            
        } catch (Exception e) {
            logger.warning("Error loading existing decompiled data: " + e.getMessage());
        }
    }
    
    /**
     * Build class tree structure for a JAR
     */
    public static ClassTreeNode buildClassTree(String jarPath, String jarName) {
        ClassTreeNode root = new ClassTreeNode("root", "", false);
        
        try {
            String fullJarPath = Paths.get(jarPath, jarName).toString();
            try (JarFile jarFile = new JarFile(fullJarPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    
                    if (entryName.endsWith(".class") && !entry.isDirectory()) {
                        String className = entryName.replace('/', '.').replace(".class", "");
                        addClassToTree(root, className);
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Error building class tree for JAR " + jarName + ": " + e.getMessage());
        }
        
        return root;
    }
    
    /**
     * Add class to tree structure
     */
    private static void addClassToTree(ClassTreeNode root, String className) {
        String[] parts = className.split("\\.");
        ClassTreeNode current = root;
        
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            ClassTreeNode child = current.findChild(part);
            if (child == null) {
                child = new ClassTreeNode(part, String.join(".", Arrays.copyOfRange(parts, 0, i + 1)), false);
                current.addChild(child);
            }
            current = child;
        }
        
        // Add the class file
        String classNameOnly = parts[parts.length - 1];
        String fullClassName = className;
        ClassTreeNode classNode = new ClassTreeNode(classNameOnly, fullClassName, true);
        current.addChild(classNode);
    }
    
    /**
     * Get existing decompiled classes from the extraction directory
     */
    public static List<String> getExistingDecompiledClasses(String projectName) {
        List<String> classes = new ArrayList<>();
        try {
            String extractionDir = getExtractionDirectory(projectName);
            File dir = new File(extractionDir);
            
            if (!dir.exists()) {
                return classes;
            }
            
            // Scan all JAR directories for .java files
            File[] jarDirs = dir.listFiles(File::isDirectory);
            if (jarDirs != null) {
                for (File jarDir : jarDirs) {
                    scanForJavaFiles(jarDir, "", classes);
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error scanning existing decompiled classes: " + e.getMessage());
        }
        
        return classes;
    }
    
    /**
     * Recursively scan directory for .java files
     */
    private static void scanForJavaFiles(File dir, String packagePath, List<String> classes) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String newPackage = packagePath.isEmpty() ? file.getName() : packagePath + "." + file.getName();
                    scanForJavaFiles(file, newPackage, classes);
                } else if (file.getName().endsWith(".java") && !file.getName().contains("$")) {
                    String className = file.getName().substring(0, file.getName().length() - 5);
                    String fullClassName = packagePath.isEmpty() ? className : packagePath + "." + className;
                    classes.add(fullClassName);
                }
            }
        }
    }
    
    /**
     * Get existing decompiled JARs from the extraction directory
     */
    public static List<String> getExistingDecompiledJars(String projectName) {
        List<String> jars = new ArrayList<>();
        try {
            String extractionDir = getExtractionDirectory(projectName);
            File dir = new File(extractionDir);
            
            if (!dir.exists()) {
                return jars;
            }
            
            // Get all JAR directories
            File[] jarDirs = dir.listFiles(File::isDirectory);
            if (jarDirs != null) {
                for (File jarDir : jarDirs) {
                    jars.add(jarDir.getName());
                }
            }
            
        } catch (Exception e) {
            logger.warning("Error scanning existing decompiled JARs: " + e.getMessage());
        }
        
        return jars;
    }

    /**
     * Find VS Code executable
     */
    public static String findVSCodeExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows paths
            String[] winPaths = {
                System.getenv("LOCALAPPDATA") + "\\Programs\\Microsoft VS Code\\Code.exe",
                System.getenv("PROGRAMFILES") + "\\Microsoft VS Code\\Code.exe",
                System.getenv("PROGRAMFILES(X86)") + "\\Microsoft VS Code\\Code.exe"
            };
            
            for (String path : winPaths) {
                if (path != null && new File(path).exists()) {
                    return path;
                }
            }
        } else if (os.contains("mac")) {
            // macOS paths
            String[] macPaths = {
                "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code",
                System.getProperty("user.home") + "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"
            };
            
            for (String path : macPaths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
        } else if (os.contains("nix") || os.contains("nux")) {
            // Linux paths
            String[] linuxPaths = {
                "/usr/bin/code",
                "/usr/local/bin/code",
                System.getProperty("user.home") + "/.local/bin/code"
            };
            
            for (String path : linuxPaths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Open extracted folder in VS Code with detection
     */
    public static void openInVSCode(String extractedPath) {
        try {
            String vsCodePath = findVSCodeExecutable();
            
            if (vsCodePath != null) {
                ProcessBuilder processBuilder = new ProcessBuilder(vsCodePath, extractedPath);
                processBuilder.start();
                logger.info("Opened VS Code with directory: " + extractedPath);
            } else {
                // Try the 'code' command as fallback
                ProcessBuilder processBuilder = new ProcessBuilder("code", extractedPath);
                processBuilder.start();
                logger.info("Opened VS Code with directory: " + extractedPath);
            }
        } catch (IOException e) {
            logger.severe("Error opening VS Code: " + e.getMessage());
            throw new RuntimeException("Failed to open VS Code. Please install VS Code or ensure the 'code' command is available in PATH.", e);
        }
    }
    
    /**
     * Open VS Code with a specific file
     */
    public static void openInVSCode(String extractedPath, String className) {
        try {
            // Find the JAR that contains this class by checking all JAR directories
            File extractedDir = new File(extractedPath);
            if (!extractedDir.exists()) {
                logger.warning("Extracted directory does not exist: " + extractedPath);
                return;
            }
            
            String fileName = className.replace('.', File.separatorChar) + ".java";
            File targetFile = null;
            
            // Search through all JAR directories to find the class file
            File[] jarDirs = extractedDir.listFiles(File::isDirectory);
            if (jarDirs != null) {
                for (File jarDir : jarDirs) {
                    File classFile = new File(jarDir, fileName);
                    if (classFile.exists()) {
                        targetFile = classFile;
                        break;
                    }
                }
            }
            
            if (targetFile == null) {
                logger.warning("Class file not found in any JAR directory: " + className + ", opening directory instead");
                openInVSCode(extractedPath);
                return;
            }
            
            String folderPath = extractedPath;
            String filePath = targetFile.getAbsolutePath();
            String vsCodePath = findVSCodeExecutable();
            
            if (vsCodePath != null) {
                // Open folder and focus on specific file
                ProcessBuilder processBuilder = new ProcessBuilder(vsCodePath, folderPath, filePath);
                processBuilder.start();
                logger.info("Opened VS Code with folder: " + folderPath + " and file: " + filePath);
            } else {
                // Try the 'code' command as fallback
                ProcessBuilder processBuilder = new ProcessBuilder("code", folderPath, filePath);
                processBuilder.start();
                logger.info("Opened VS Code with folder: " + folderPath + " and file: " + filePath);
            }
        } catch (IOException e) {
            logger.severe("Error opening VS Code with file: " + e.getMessage());
            // Fallback to opening directory
            openInVSCode(extractedPath);
        }
    }
    
    /**
     * Class tree node for hierarchical display
     */
    public static class ClassTreeNode {
        private final String name;
        private final String fullName;
        private final boolean isClass;
        private final List<ClassTreeNode> children;
        
        public ClassTreeNode(String name, String fullName, boolean isClass) {
            this.name = name;
            this.fullName = fullName;
            this.isClass = isClass;
            this.children = new ArrayList<>();
        }
        
        public String getName() { return name; }
        public String getFullName() { return fullName; }
        public boolean isClass() { return isClass; }
        public List<ClassTreeNode> getChildren() { return children; }
        
        public void addChild(ClassTreeNode child) {
            children.add(child);
        }
        
        public ClassTreeNode findChild(String name) {
            return children.stream()
                .filter(child -> child.getName().equals(name))
                .findFirst()
                .orElse(null);
        }
        
        public void sortChildren() {
            children.sort((a, b) -> {
                if (a.isClass() != b.isClass()) {
                    return a.isClass() ? 1 : -1; // Packages first, then classes
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            
            for (ClassTreeNode child : children) {
                child.sortChildren();
            }
        }
    }
    
    /**
     * Helper method to read all bytes from an InputStream
     */
    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    
    /**
     * Extract package name from fully qualified class name
     */
    private String getPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return null;
    }
    
    /**
     * In-memory loader for JD-Core
     */
    static class InMemoryLoader implements Loader {
        private final String className;
        private final byte[] classBytes;
        
        InMemoryLoader(String className, byte[] classBytes) {
            this.className = className;
            this.classBytes = classBytes;
        }
        
        @Override
        public byte[] load(String internalName) throws LoaderException {
            if (internalName.equals(className)) return classBytes;
            throw new LoaderException("Class not found: " + internalName);
        }
        
        @Override
        public boolean canLoad(String internalName) {
            return internalName.equals(className);
        }
    }
    
    /**
     * StringBuilder printer for JD-Core
     */
    static class StringBuilderPrinter implements Printer {
        private final StringBuilder sb = new StringBuilder();
        private int indentationCount = 0;
        private static final String TAB = "  ";
        private static final String NEWLINE = "\n";
        
        @Override
        public void start(int maxLineNumber, int majorVersion, int minorVersion) {}
        
        @Override
        public void end() {}
        
        @Override
        public void printText(String text) {
            sb.append(text);
        }
        
        @Override
        public void printNumericConstant(String constant) {
            sb.append(constant);
        }
        
        @Override
        public void printStringConstant(String constant, String ownerInternalName) {
            sb.append(constant);
        }
        
        @Override
        public void printKeyword(String keyword) {
            sb.append(keyword);
        }
        
        @Override
        public void printDeclaration(int type, String internalTypeName, String name, String descriptor) {
            sb.append(name);
        }
        
        @Override
        public void printReference(int type, String internalTypeName, String name, String descriptor, String ownerInternalName) {
            sb.append(name);
        }
        
        @Override
        public void indent() {
            indentationCount++;
        }
        
        @Override
        public void unindent() {
            indentationCount--;
        }
        
        @Override
        public void startLine(int lineNumber) {
            for (int i = 0; i < indentationCount; i++) sb.append(TAB);
        }
        
        @Override
        public void endLine() {
            sb.append(NEWLINE);
        }
        
        @Override
        public void extraLine(int count) {
            while (count-- > 0) sb.append(NEWLINE);
        }
        
        @Override
        public void startMarker(int type) {}
        
        @Override
        public void endMarker(int type) {}
        
        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
