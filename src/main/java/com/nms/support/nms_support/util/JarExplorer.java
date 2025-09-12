package com.nms.support.nms_support.util;

import java.io.*;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Enumeration;

/**
 * Utility class to explore jar contents and find version files
 */
public class JarExplorer {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java JarExplorer <jar-file-path>");
            System.out.println("Example: java JarExplorer \"C:\\Oracle NMS\\VM\\nmslib\\nms_common.jar\"");
            return;
        }
        
        String jarPath = args[0];
        exploreJar(jarPath);
    }
    
    public static void exploreJar(String jarPath) {
        System.out.println("Exploring jar: " + jarPath);
        
        try (JarFile jarFile = new JarFile(jarPath)) {
            System.out.println("Jar file opened successfully");
            System.out.println("Total entries: " + jarFile.size());
            System.out.println("\nListing all entries:");
            
            Enumeration<JarEntry> entries = jarFile.entries();
            int count = 0;
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                count++;
                
                System.out.println(count + ". " + entryName + " (size: " + entry.getSize() + " bytes)");
                
                // If it's an XML file, let's check its content
                if (entryName.endsWith(".xml")) {
                    System.out.println("   -> XML file found: " + entryName);
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        String content = readInputStreamAsString(inputStream);
                        if (content.contains("cvs_tag") || content.contains("version_info")) {
                            System.out.println("   -> CONTAINS VERSION INFO!");
                            System.out.println("   -> Content preview:");
                            String[] lines = content.split("\n");
                            for (int i = 0; i < Math.min(10, lines.length); i++) {
                                System.out.println("      " + lines[i]);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("   -> Error reading content: " + e.getMessage());
                    }
                }
            }
            
            System.out.println("\nExploration complete. Found " + count + " entries.");
            
        } catch (Exception e) {
            System.err.println("Error exploring jar: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
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
}
