package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        disableSSLVerification();
        
        progressCallback.onProgress(20, "Fetching JAR file list from server...");
        try {
            List<String> jarFiles = fetchJarFiles(baseUrl);
            progressCallback.onProgress(25, "Found " + jarFiles.size() + " JAR files to download");

            List<String> Failed = new ArrayList<String>();
            int totalFiles = jarFiles.size();
            int downloadedFiles = 0;

            for (String jarFile : jarFiles) {
                try {
                    // Check for cancellation
                    if (progressCallback.isCancelled()) {
                        progressCallback.onError("Resource loading cancelled by user");
                        return;
                    }
                    
                    // Construct the URL for the JAR file
                    URL fileUrl = new URL(baseUrl + jarFile);

                    // Download the file
                    downloadFile(fileUrl, downloadDir+"\\nmslib");
                    downloadedFiles++;
                    
                    int progress = 30 + (int)((downloadedFiles * 60.0) / totalFiles); // 30-90% range
                    progressCallback.onProgress(progress, "Downloaded " + downloadedFiles + "/" + totalFiles + " files");
                    
                } catch (IOException e) {
                    Failed.add(jarFile);
                    e.printStackTrace();
                    LoggerUtil.error(e);
                    progressCallback.onProgress(0, "Failed to download: " + jarFile);
                }
            }
            
                    progressCallback.onProgress(95, "Resource loading completed");
        if (!Failed.isEmpty()) {
            progressCallback.onProgress(95, "Resources Failed to Download: " + Failed.toString());
            // Mark as failed if any downloads failed
            progressCallback.onError("Resource loading failed: " + Failed.size() + " files failed to download. Failed files: " + Failed.toString());
        } else {
            progressCallback.onComplete("Resource loading completed successfully. All " + totalFiles + " files downloaded.");
        }
        } catch (IOException e) {
            progressCallback.onError("Failed to load resources: " + e.getMessage());
        }
    }

    private static List<String> fetchJarFiles(String baseUrl) throws IOException {
        String jnlpUrl = baseUrl + "/ConfigurationAssistant.jnlp";
        List<String> jarFiles = new ArrayList<>();
        HttpURLConnection connection = null;
        
        try {
            // Open connection to the JNLP URL
            URL url = new URL(jnlpUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 second timeout
            connection.setReadTimeout(60000); // 60 second timeout

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
                Pattern argumentPattern = Pattern.compile("<argument>(.*?)</argument>", Pattern.DOTALL);
                Matcher argumentMatcher = argumentPattern.matcher(responseText);
                while (argumentMatcher.find()) {
                    String argumentContent = argumentMatcher.group(1);
                    // If the argument content contains jar files
                    if (argumentContent.contains(".jar")) {
                        String[] jars = argumentContent.split(";");
                        for (String jar : jars) {
                            jarFiles.add(jar.trim());
                        }
                    }
                }
            }
            
            // If no JAR files found, throw an exception
            if (jarFiles.isEmpty()) {
                throw new IOException("No JAR files found in the JNLP configuration");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            progressCallback.onError("Failed to fetch JAR file list: " + e.getMessage());
            throw e; // Re-throw to ensure the process fails
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
            HostnameVerifier allHostsValid = (_, _) -> true;
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
                progressCallback.onProgress(0, "Downloaded file: " + fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
            progressCallback.onProgress(0,"Failed to download file: " + e.getMessage());
        } finally {
            // Ensure connection is properly closed
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
