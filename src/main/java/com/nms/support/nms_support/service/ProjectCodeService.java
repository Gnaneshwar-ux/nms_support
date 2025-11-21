package com.nms.support.nms_support.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.logging.Logger;

/**
 * Service class for improved project code detection
 * Replaces the old log-based approach with more reliable methods
 */
public class ProjectCodeService {
    private static final Logger logger = Logger.getLogger(ProjectCodeService.class.getName());

    /**
     * Gets the project code using the improved approach
     * @param jconfigPath The JConfig path for the project
     * @param productFolderPath The product folder path
     * @return Project code in format "projectName#cvsTag" or null if not found
     */
    public static String getProjectCode(String jconfigPath, String productFolderPath) {
        logger.info("Attempting to get project code using improved method");
        logger.info("JConfig Path: " + (jconfigPath != null ? jconfigPath : "null"));
        logger.info("Product Folder Path: " + (productFolderPath != null ? productFolderPath : "null"));
        
        String projectName = getProjectNameFromBuildProperties(jconfigPath);
        String cvsTag = getCvsTagFromNmsCommonJar(productFolderPath+File.separator+"nmslib");
        
        logger.info("Project Name from build.properties: " + (projectName != null ? projectName : "null"));
        logger.info("CVS Tag from nms_common.jar: " + (cvsTag != null ? cvsTag : "null"));
        
        if (projectName != null && cvsTag != null) {
            String projectCode = projectName + "#" + cvsTag;
            logger.info("Successfully retrieved project code: " + projectCode);
            return projectCode;
        }
        
        logger.warning("Failed to retrieve project code using improved method");
        if (projectName == null) {
            logger.warning("Reason: Could not get project name from build.properties");
        }
        if (cvsTag == null) {
            logger.warning("Reason: Could not get CVS tag from nms_common.jar");
        }
        return null;
    }

    /**
     * Gets the project name from build.properties file
     * @param jconfigPath The JConfig path
     * @return Project name or null if not found
     */
    private static String getProjectNameFromBuildProperties(String jconfigPath) {
        if (jconfigPath == null || jconfigPath.trim().isEmpty()) {
            logger.warning("JConfig path is null or empty");
            return null;
        }

        try {
            String buildPropertiesPath = jconfigPath + File.separator + "build.properties";
            File buildPropertiesFile = new File(buildPropertiesPath);
            
            logger.info("Looking for build.properties at: " + buildPropertiesPath);
            logger.info("File exists: " + buildPropertiesFile.exists());
            
            if (!buildPropertiesFile.exists()) {
                logger.warning("build.properties file not found at: " + buildPropertiesPath);
                return null;
            }

            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(buildPropertiesFile)) {
                properties.load(fis);
            }

            // Log all properties for debugging
            logger.info("Properties found in build.properties:");
            for (String key : properties.stringPropertyNames()) {
                logger.info("  " + key + " = " + properties.getProperty(key));
            }

            String projectName = properties.getProperty("project.name");
            if (projectName != null && !projectName.trim().isEmpty()) {
                logger.info("Found project name in build.properties: " + projectName);
                return projectName.trim();
            } else {
                logger.warning("project.name property not found or empty in build.properties");
                return null;
            }
        } catch (Exception e) {
            logger.severe("Error reading build.properties: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the CVS tag from nms_common.jar
     * @param productFolderPath The product folder path
     * @return CVS tag or null if not found
     */
    public static String getCvsTagFromNmsCommonJar(String productFolderPath) {
        if (productFolderPath == null || productFolderPath.trim().isEmpty()) {
            logger.warning("Path(s) for nms_common.jar search is null or empty");
            return null;
        }

        try {
            // Build candidate directories:
            // - If semicolon-separated, consider each as a directory to search for nms_common.jar
            // - Also consider that the provided path may be a product folder (so append 'nmslib')
            java.util.List<String> candidateDirs = new java.util.ArrayList<>();
            String raw = productFolderPath.trim();

            if (raw.contains(";")) {
                for (String p : raw.split(";")) {
                    String s = p.trim();
                    if (!s.isEmpty()) {
                        candidateDirs.add(s);
                    }
                }
            } else {
                candidateDirs.add(raw);
            }

            // For each candidate directory, try:
            // 1) <dir>/nms_common.jar
            // 2) <dir>/nmslib/nms_common.jar (in case <dir> is product folder)
            for (String dir : candidateDirs) {
                // First assume dir points to the jar folder (e.g., nmslib)
                String directPath = dir + File.separator + "nms_common.jar";
                File directJar = new File(directPath);
                logger.info("Looking for nms_common.jar at: " + directPath + " | exists=" + directJar.exists());

                File jarToUse = null;
                if (directJar.exists()) {
                    jarToUse = directJar;
                } else {
                    // Next assume dir is product folder; append nmslib
                    String nmslibPath = dir + File.separator + "nmslib" + File.separator + "nms_common.jar";
                    File nmslibJar = new File(nmslibPath);
                    logger.info("Looking for nms_common.jar at: " + nmslibPath + " | exists=" + nmslibJar.exists());
                    if (nmslibJar.exists()) {
                        jarToUse = nmslibJar;
                    }
                }

                if (jarToUse != null && jarToUse.exists()) {
                    try (JarFile jarFile = new JarFile(jarToUse)) {
                        // Explore jar contents to find the version file
                        logger.info("Exploring jar contents to find version file in: " + jarToUse.getAbsolutePath());
                        String versionFilePath = findVersionFileInJar(jarFile);

                        if (versionFilePath == null) {
                            logger.warning("No version file found in nms_common.jar at: " + jarToUse.getAbsolutePath());
                            // Try next candidate dir
                            continue;
                        }

                        logger.info("Found version file at: " + versionFilePath);
                        ZipEntry versionXmlEntry = jarFile.getEntry(versionFilePath);

                        if (versionXmlEntry == null) {
                            logger.warning("Version file entry not found: " + versionFilePath);
                            // Try next candidate dir
                            continue;
                        }

                        try (InputStream inputStream = jarFile.getInputStream(versionXmlEntry)) {
                            String tag = parseCvsTagFromXml(inputStream);
                            if (tag != null && !tag.trim().isEmpty()) {
                                return tag;
                            }
                        }
                    }
                }
            }

            logger.warning("nms_common.jar not found in any provided path(s): " + productFolderPath);
            return null;

        } catch (Exception e) {
            logger.severe("Error reading nms_common.jar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Finds the version file in the jar by exploring its contents
     * @param jarFile The jar file to explore
     * @return Path to the version file or null if not found
     */
    private static String findVersionFileInJar(JarFile jarFile) {
        try {
            // List all entries in the jar
            logger.info("Listing all entries in nms_common.jar:");
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                logger.info("  Entry: " + entryName);
                
                // Look for various possible version file names and locations
                if (entryName.contains("version") && entryName.endsWith(".xml")) {
                    logger.info("Found potential version file: " + entryName);
                    return entryName;
                }
                
                // Also check for version files in common locations
                if (entryName.equals("nms-version.xml") || 
                    entryName.equals("version.xml") ||
                    entryName.equals("version_info.xml") ||
                    entryName.endsWith("/nms-version.xml") ||
                    entryName.endsWith("/version.xml") ||
                    entryName.endsWith("/version_info.xml")) {
                    logger.info("Found version file: " + entryName);
                    return entryName;
                }
            }
            
            // If no version file found, let's look for any XML files that might contain version info
            logger.info("No specific version file found, looking for any XML files...");
            entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.endsWith(".xml")) {
                    logger.info("Found XML file: " + entryName);
                    // Try to read this XML file to see if it contains version info
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        String content = readInputStreamAsString(inputStream);
                        if (content.contains("cvs_tag") || content.contains("version_info")) {
                            logger.info("Found XML file with version info: " + entryName);
                            return entryName;
                        }
                    } catch (Exception e) {
                        logger.warning("Error reading XML file " + entryName + ": " + e.getMessage());
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.severe("Error exploring jar contents: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Reads an InputStream as a String
     * @param inputStream The input stream to read
     * @return The content as a string
     */
    private static String readInputStreamAsString(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    /**
     * Parses the CVS tag from the nms-version.xml content
     * @param inputStream InputStream containing the XML content
     * @return CVS tag or null if not found
     */
    private static String parseCvsTagFromXml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            
            NodeList cvsTagNodes = document.getElementsByTagName("cvs_tag");
            if (cvsTagNodes.getLength() > 0) {
                Element cvsTagElement = (Element) cvsTagNodes.item(0);
                String cvsTag = cvsTagElement.getTextContent();
                if (cvsTag != null && !cvsTag.trim().isEmpty()) {
                    logger.info("Found CVS tag in nms-version.xml: " + cvsTag);
                    return cvsTag.trim();
                }
            }
            
            logger.warning("cvs_tag element not found or empty in nms-version.xml");
            return null;
        } catch (Exception e) {
            logger.severe("Error parsing nms-version.xml: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets project codes from log files (fallback method)
     * Uses existing ControlApp methods for consistency
     * @return List of project codes found in logs
     */
    public static java.util.List<String> getProjectCodesFromLogs() {
        java.util.List<String> projectCodes = new java.util.ArrayList<>();
        
        try {
            // Get log directory path
            String user = System.getProperty("user.name");
            String logDirectoryPath = "C:/Users/" + user + "/AppData/Local/Temp/OracleNMS";
            File directory = new File(logDirectoryPath);
            
            if (!directory.exists()) {
                logger.warning("Log directory does not exist: " + logDirectoryPath);
                return projectCodes;
            }

            // Get log files
            File[] logFiles = directory.listFiles((dir, name) -> 
                name.toLowerCase().contains(".log") && new File(dir, name).isFile());
            
            if (logFiles != null) {
                for (File logFile : logFiles) {
                    try {
                        // Use existing ControlApp parseLog method
                        Map<String, String> logData = com.nms.support.nms_support.service.buildTabPack.ControlApp.parseLog(logFile);
                        if (logData != null && logData.containsKey("PROJECT_KEY")) {
                            String projectCode = logData.get("PROJECT_KEY");
                            if (projectCode != null && !projectCode.trim().isEmpty() && !projectCodes.contains(projectCode)) {
                                projectCodes.add(projectCode);
                                logger.info("Found project code from log: " + projectCode);
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Error parsing log file " + logFile.getName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Error getting project codes from logs: " + e.getMessage());
        }
        
        return projectCodes;
    }
}
