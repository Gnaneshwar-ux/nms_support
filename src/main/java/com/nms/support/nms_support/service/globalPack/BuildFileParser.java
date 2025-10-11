package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class to parse environment variables from build files (build.xml and build.properties)
 */
public class BuildFileParser {
    
    // Pattern to match environment variables like ${env.VARIABLE_NAME}
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{env\\.([^}]+)\\}");
    
    /**
     * Parse environment variables from build files in the given directory
     * 
     * @param projectPath The project directory path
     * @return List of unique environment variable names found in build files
     */
    public static List<String> parseEnvironmentVariables(String projectPath) {
        Set<String> envVars = new HashSet<>();
        
        // Try multiple possible locations for build files
        String[] possiblePaths = {
            projectPath + "/build.xml",
            projectPath + "/build.properties",
            projectPath + "/java/ant/build.xml",
            projectPath + "/java/ant/build.properties",
            projectPath + "/jconfig/build.xml",
            projectPath + "/jconfig/build.properties"
        };
        
        for (String filePath : possiblePaths) {
            parseFileForEnvVars(filePath, envVars);
        }
        
        // Sort the results with HOME variables first
        return sortEnvironmentVariables(new ArrayList<>(envVars));
    }
    
    /**
     * Parse environment variables from build files using jconfig path
     * 
     * @param project The project entity containing jconfig path
     * @return List of unique environment variable names found in build files
     */
    public static List<String> parseEnvironmentVariablesFromJconfig(ProjectEntity project) {
        String jconfigPath = project.getJconfigPathForBuild();
        Set<String> envVars = new HashSet<>();
        
        // Exact paths for project build files
        String[] projectPaths = {
            jconfigPath + "/build.xml",
            jconfigPath + "/build.properties"
        };
        
        for (String filePath : projectPaths) {
            parseFileForEnvVars(filePath, envVars);
        }
        
        // Sort the results with HOME variables first
        return sortEnvironmentVariables(new ArrayList<>(envVars));
    }
    
    /**
     * Parse environment variables from product build files using exe path
     * 
     * @param project The project entity containing exe path
     * @return List of unique environment variable names found in build files
     */
    public static List<String> parseEnvironmentVariablesFromProduct(ProjectEntity project) {
        String exePath = project.getExePath();
        Set<String> envVars = new HashSet<>();
        
        // Exact paths for product build files
        String[] productPaths = {
            exePath + "/java/ant/build.xml",
            exePath + "/java/ant/build.properties"
        };
        
        for (String filePath : productPaths) {
            parseFileForEnvVars(filePath, envVars);
        }
        
        // Sort the results with HOME variables first
        return sortEnvironmentVariables(new ArrayList<>(envVars));
    }
    
    /**
     * Validate that required build files exist
     * 
     * @param project The project entity
     * @param isProductReplace Whether this is for product or project replacement
     * @return List of missing files, empty if all files exist
     */
    public static List<String> validateBuildFiles(ProjectEntity project, boolean isProductReplace) {
        List<String> missingFiles = new ArrayList<>();
        
        if (isProductReplace) {
            // Check product build files
            String exePath = project.getExePath();
            String[] productPaths = {
                exePath + "/java/ant/build.xml",
                exePath + "/java/ant/build.properties"
            };
            
            for (String filePath : productPaths) {
                File file = new File(filePath);
                if (!file.exists() || !file.isFile()) {
                    missingFiles.add(filePath);
                }
            }
        } else {
            // Check project build files
            String jconfigPath = project.getJconfigPathForBuild();
            String[] projectPaths = {
                jconfigPath + "/build.xml",
                jconfigPath + "/build.properties"
            };
            
            for (String filePath : projectPaths) {
                File file = new File(filePath);
                if (!file.exists() || !file.isFile()) {
                    missingFiles.add(filePath);
                }
            }
        }
        
        return missingFiles;
    }
    
    /**
     * Replace environment variable in build files
     * 
     * @param project The project entity
     * @param isProductReplace Whether this is for product or project replacement
     * @param oldValue The old environment variable name to replace
     * @param newValue The new environment variable name
     * @return List of files that were successfully updated
     */
    public static List<String> replaceEnvironmentVariable(ProjectEntity project, boolean isProductReplace, String oldValue, String newValue) {
        List<String> updatedFiles = new ArrayList<>();
        
        String[] filePaths;
        if (isProductReplace) {
            // Product build files
            String exePath = project.getExePath();
            filePaths = new String[]{
                exePath + "/java/ant/build.xml",
                exePath + "/java/ant/build.properties"
            };
        } else {
            // Project build files
            String jconfigPath = project.getJconfigPathForBuild();
            filePaths = new String[]{
                jconfigPath + "/build.xml",
                jconfigPath + "/build.properties"
            };
        }
        
        for (String filePath : filePaths) {
            try {
                File file = new File(filePath);
                if (!file.exists() || !file.isFile()) {
                    continue; // Skip missing files
                }
                
                // Read file content
                String content = new String(Files.readAllBytes(Paths.get(filePath)));
                
                // Replace ${env.OLD_VALUE} with ${env.NEW_VALUE}
                String oldPattern = "${env." + oldValue + "}";
                String newPattern = "${env." + newValue + "}";
                
                if (content.contains(oldPattern)) {
                    String updatedContent = content.replace(oldPattern, newPattern);
                    
                    // Write back to file
                    Files.write(Paths.get(filePath), updatedContent.getBytes());
                    updatedFiles.add(filePath);
                }
                
            } catch (IOException e) {
                System.err.println("Error replacing environment variable in file " + filePath + ": " + e.getMessage());
            }
        }
        
        return updatedFiles;
    }
    
    /**
     * Parse environment variables from a specific file
     * 
     * @param filePath The file path to parse
     * @param envVars Set to collect found environment variables
     */
    private static void parseFileForEnvVars(String filePath, Set<String> envVars) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                return;
            }
            
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            Matcher matcher = ENV_VAR_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String envVar = matcher.group(1);
                if (envVar != null && !envVar.trim().isEmpty()) {
                    envVars.add(envVar.trim());
                }
            }
            
        } catch (IOException e) {
            // Log error but continue with other files
            System.err.println("BuildFileParser: Error parsing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Sort environment variables with HOME variables first, then alphabetically
     * 
     * @param envVars List of environment variables to sort
     * @return Sorted list with HOME variables first
     */
    private static List<String> sortEnvironmentVariables(List<String> envVars) {
        return envVars.stream()
                .sorted((var1, var2) -> {
                    boolean var1HasHome = var1.toUpperCase().contains("HOME");
                    boolean var2HasHome = var2.toUpperCase().contains("HOME");
                    
                    // If one has HOME and the other doesn't, HOME comes first
                    if (var1HasHome && !var2HasHome) {
                        return -1;
                    } else if (!var1HasHome && var2HasHome) {
                        return 1;
                    } else {
                        // Both have HOME or both don't, sort alphabetically
                        return var1.compareToIgnoreCase(var2);
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get environment variables with their usage count
     * 
     * @param projectPath The project directory path
     * @return Map of environment variable names to their usage count
     */
    public static Map<String, Integer> getEnvironmentVariablesWithCount(String projectPath) {
        Map<String, Integer> envVarCounts = new HashMap<>();
        
        // Try multiple possible locations for build files
        String[] possiblePaths = {
            projectPath + "/build.xml",
            projectPath + "/build.properties",
            projectPath + "/java/ant/build.xml",
            projectPath + "/java/ant/build.properties"
        };
        
        for (String filePath : possiblePaths) {
            countEnvVarsInFile(filePath, envVarCounts);
        }
        
        return envVarCounts;
    }
    
    /**
     * Count environment variable occurrences in a specific file
     * 
     * @param filePath The file path to parse
     * @param envVarCounts Map to collect environment variable counts
     */
    private static void countEnvVarsInFile(String filePath, Map<String, Integer> envVarCounts) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                return;
            }
            
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            Matcher matcher = ENV_VAR_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String envVar = matcher.group(1);
                if (envVar != null && !envVar.trim().isEmpty()) {
                    String trimmedVar = envVar.trim();
                    envVarCounts.put(trimmedVar, envVarCounts.getOrDefault(trimmedVar, 0) + 1);
                }
            }
            
        } catch (IOException e) {
            // Log error but continue with other files
            System.err.println("Error counting variables in file " + filePath + ": " + e.getMessage());
        }
    }
}
