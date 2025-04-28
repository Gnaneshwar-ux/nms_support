package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileFetcher {
    private static BuildAutomation buildAutomation;
    public static void loadResources(String downloadDir, String baseUrl, BuildAutomation buildAutomation) {
        FileFetcher.buildAutomation = buildAutomation;
        // Base URL of the website to fetch files from

        // Directory where files will be saved
        //String downloadDir = "C:\\Oracle NMS\\temp";

        
        // List of JAR files to be downloaded


        // Disable SSL verification
        disableSSLVerification();
        List<String> jarFiles = fetchJarFiles(baseUrl);
        buildAutomation.appendTextToLog(jarFiles.toString());

        List<String> Failed = new ArrayList<String>();

        for (String jarFile : jarFiles) {
            try {
                // Construct the URL for the JAR file
                URL fileUrl = new URL(baseUrl + jarFile);

                // Download the file
                downloadFile(fileUrl, downloadDir+"\\nmslib");
            } catch (IOException e) {
            	Failed.add(jarFile);
                e.printStackTrace();
                LoggerUtil.error(e);
            }
        }
        
        buildAutomation.appendTextToLog("Download resources completed");
        buildAutomation.appendTextToLog("Resources Failed to Download : "+Failed.toString());
    }

    private static List<String> fetchJarFiles(String baseUrl) {
        String jnlpUrl = baseUrl + "/ConfigurationAssistant.jnlp";
        List<String> jarFiles = new ArrayList<>();
        try {
            // Open connection to the JNLP URL
            URL url = new URL(jnlpUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Read the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                response.append("\n");
            }
            reader.close();

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
        } catch (IOException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
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
        }
    }

    private static void downloadFile(URL fileUrl, String downloadDir) {
        try {
            // Open a connection to the file URL
            HttpURLConnection connection = (HttpURLConnection) fileUrl.openConnection();
            connection.setRequestMethod("GET");

            // Get the file name from the URL
            String fileName = new File(fileUrl.getPath()).getName();
            Path filePath = Paths.get(downloadDir, fileName);

            // Create the download directory if it doesn't exist
            Files.createDirectories(filePath.getParent());

            // Open input stream to read the file
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(filePath, StandardOpenOption.CREATE)) {

                // Read the file data from the input stream and write it to the output stream
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                buildAutomation.appendTextToLog("\nDownloaded file: " + fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
            LoggerUtil.error(e);
        }
    }
}
