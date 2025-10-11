package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;

public class FileFetcher {
    private static ProgressCallback progressCallback;
    
    public static void loadResources(String downloadDir, String baseUrl, ProgressCallback progressCallback) {
        FileFetcher.progressCallback = progressCallback;
        
        progressCallback.onProgress(10, "Starting resource loading process...");
        
        // Base URL of the website to fetch files from
        // Directory where files will be saved
        //String downloadDir = "C:\\Oracle NMS\\temp";

        // Disable SSL verification
        progressCallback.onProgress(15, "Configuring SSL settings...");
        try {
            disableSSLVerification();
            progressCallback.onProgress(16, "SSL settings configured successfully");
        } catch (Exception e) {
            progressCallback.onProgress(16, "Warning: SSL configuration failed, continuing with default settings");
            LoggerUtil.getLogger().warning("SSL configuration failed: " + e.getMessage());
        }
        
        progressCallback.onProgress(20, "Fetching JAR file list from server...");
        progressCallback.onProgress(21, "Base URL: " + baseUrl);
        LoggerUtil.getLogger().info("FileFetcher: Starting resource loading with base URL: " + baseUrl);
        try {
            List<String> jarFiles = fetchJarFiles(baseUrl);
            LoggerUtil.getLogger().info("FileFetcher: Successfully fetched " + jarFiles.size() + " JAR files");
            progressCallback.onProgress(25, "Found " + jarFiles.size() + " JAR files to download");

            List<String> Failed = new ArrayList<String>();
            int totalFiles = jarFiles.size();
            int downloadedFiles = 0;

            for (String jarFile : jarFiles) {
                try {
                    // Check for cancellation
                    if (progressCallback.isCancelled()) {
                        LoggerUtil.getLogger().info("FileFetcher: Resource loading cancelled by user");
                        progressCallback.onError("Resource loading cancelled by user");
                        return;
                    }
                    
                    // Construct the URL for the JAR file
                    URL fileUrl = new URL(baseUrl + jarFile);
                    LoggerUtil.getLogger().info("FileFetcher: Downloading file: " + jarFile + " from " + fileUrl.toString());

                    // Download the file
                    downloadFile(fileUrl, downloadDir+"\\nmslib");
                    downloadedFiles++;
                    LoggerUtil.getLogger().info("FileFetcher: Successfully downloaded file " + downloadedFiles + "/" + totalFiles + ": " + jarFile);
                    
                    int progress = 30 + (int)((downloadedFiles * 60.0) / totalFiles); // 30-90% range
                    progressCallback.onProgress(progress, "Downloaded " + downloadedFiles + "/" + totalFiles + " files");
                    
                } catch (IOException e) {
                    Failed.add(jarFile);
                    e.printStackTrace();
                    LoggerUtil.error(e);
                    LoggerUtil.getLogger().severe("FileFetcher: Failed to download file " + jarFile + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    progressCallback.onProgress(0, "Failed to download: " + jarFile);
                }
            }
            
            progressCallback.onProgress(95, "Resource loading completed");
            if (!Failed.isEmpty()) {
                LoggerUtil.getLogger().severe("FileFetcher: Resource loading failed - " + Failed.size() + " files failed to download. Failed files: " + Failed.toString());
                progressCallback.onProgress(95, "Resources Failed to Download: " + Failed.toString());
                // Mark as failed if any downloads failed
                progressCallback.onError("Resource loading failed: " + Failed.size() + " files failed to download. Failed files: " + Failed.toString());
            } else {
                LoggerUtil.getLogger().info("FileFetcher: Resource loading completed successfully. All " + totalFiles + " files downloaded.");
                progressCallback.onComplete("Resource loading completed successfully. All " + totalFiles + " files downloaded.");
            }
        } catch (IOException e) {
            System.out.println("FileFetcher IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            LoggerUtil.error(e);
            LoggerUtil.getLogger().severe("FileFetcher IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Unknown error occurred during resource loading";
            }
            progressCallback.onError("Failed to load resources: " + errorMessage);
        } catch (Exception e) {
            System.out.println("FileFetcher Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            LoggerUtil.error(e);
            LoggerUtil.getLogger().severe("FileFetcher Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Unexpected error occurred during resource loading: " + e.getClass().getSimpleName();
            }
            progressCallback.onError("Failed to load resources: " + errorMessage);
        }
    }

    private static List<String> fetchJarFiles(String baseUrl) throws IOException {
        String jnlpUrl = baseUrl + "/ConfigurationAssistant.jnlp";
        List<String> jarFiles = new ArrayList<>();
        HttpURLConnection connection = null;
        
        LoggerUtil.getLogger().info("Fetching JAR files from: " + jnlpUrl);
        
        try {
            // Open connection to the JNLP URL
            LoggerUtil.getLogger().info("Creating URL object for: " + jnlpUrl);
            URL url = new URL(jnlpUrl);
            LoggerUtil.getLogger().info("Opening connection to: " + url.toString());
            connection = (HttpURLConnection) url.openConnection();
            LoggerUtil.getLogger().info("Connection opened successfully");
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 second timeout
            connection.setReadTimeout(60000); // 60 second timeout

            // Check response code
            int responseCode = connection.getResponseCode();
            LoggerUtil.getLogger().info("HTTP Response Code: " + responseCode);
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorMsg = "HTTP error: " + responseCode + " - " + connection.getResponseMessage();
                LoggerUtil.getLogger().severe("HTTP Error: " + errorMsg);
                throw new IOException(errorMsg);
            }

            LoggerUtil.getLogger().info("HTTP response OK, reading response content");
            // Read the response with proper resource management
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    response.append("\n");
                }

                // Find the argument portion and extract jar files
                String responseText = response.toString();
                LoggerUtil.getLogger().info("Response content length: " + responseText.length() + " characters");
                LoggerUtil.getLogger().info("Response content preview: " + responseText.substring(0, Math.min(200, responseText.length())));
                
                Pattern argumentPattern = Pattern.compile("<argument>(.*?)</argument>", Pattern.DOTALL);
                Matcher argumentMatcher = argumentPattern.matcher(responseText);
                int argumentCount = 0;
                while (argumentMatcher.find()) {
                    argumentCount++;
                    String argumentContent = argumentMatcher.group(1);
                    LoggerUtil.getLogger().info("Found argument " + argumentCount + ": " + argumentContent);
                    // If the argument content contains jar files
                    if (argumentContent.contains(".jar")) {
                        String[] jars = argumentContent.split(";");
                        LoggerUtil.getLogger().info("Found " + jars.length + " JAR files in argument: " + argumentContent);
                        for (String jar : jars) {
                            jarFiles.add(jar.trim());
                        }
                    }
                }
                LoggerUtil.getLogger().info("Total arguments found: " + argumentCount + ", JAR files extracted: " + jarFiles.size());
            }
            
            // If no JAR files found, throw an exception
            if (jarFiles.isEmpty()) {
                throw new IOException("No JAR files found in the JNLP configuration");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            LoggerUtil.getLogger().severe("fetchJarFiles IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Failed to connect to server or parse JNLP file";
            }
            progressCallback.onError("Failed to fetch JAR file list: " + errorMessage);
            throw e; // Re-throw to ensure the process fails
        } catch (Exception e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            LoggerUtil.getLogger().severe("fetchJarFiles Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Unexpected error while fetching JAR files: " + e.getClass().getSimpleName();
            }
            progressCallback.onError("Failed to fetch JAR file list: " + errorMessage);
            throw new IOException("Failed to fetch JAR file list: " + errorMessage, e);
        } finally {
            // Ensure connection is properly closed
            if (connection != null) {
                connection.disconnect();
            }
        }
        
        return jarFiles;
    }

    private static void disableSSLVerification() {
        try {
            // Install a trust manager that trusts all certificates
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Set up a context that uses the trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create an all-trusting host name verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            progressCallback.onError("Failed to configure SSL: " + e.getMessage());
        }
    }

    private static void downloadFile(URL fileUrl, String downloadDir) {
        HttpURLConnection connection = null;
        try {
            // Open a connection to the file URL
            connection = (HttpURLConnection) fileUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 second timeout
            connection.setReadTimeout(120000); // 2 minute timeout for large files

            // Get the file name from the URL
            String fileName = new File(fileUrl.getPath()).getName();
            Path filePath = Paths.get(downloadDir, fileName);

            // Create the download directory if it doesn't exist
            Files.createDirectories(filePath.getParent());

            // Open input stream to read the file
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(filePath, StandardOpenOption.CREATE)) {

                // Read the file data from the input stream and write it to the output stream
                byte[] buffer = new byte[8192]; // Increased buffer size for better performance
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            LoggerUtil.getLogger().severe("downloadFile IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage() + " for URL: " + fileUrl.toString());
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Failed to download file from " + fileUrl.toString();
            }
            progressCallback.onProgress(0, "Failed to download file: " + errorMessage);
        } catch (Exception e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            LoggerUtil.getLogger().severe("downloadFile Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage() + " for URL: " + fileUrl.toString());
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Unexpected error downloading file from " + fileUrl.toString() + ": " + e.getClass().getSimpleName();
            }
            progressCallback.onProgress(0, "Failed to download file: " + errorMessage);
        } finally {
            // Ensure connection is properly closed
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}