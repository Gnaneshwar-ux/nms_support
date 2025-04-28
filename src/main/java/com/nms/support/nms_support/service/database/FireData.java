package com.nms.support.nms_support.service.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FireData {

    static String urlString = "https://firestore.googleapis.com/v1/projects/nms-devtools/databases/(default)/documents/NMS_DevTools/";

    // Read data from Firestore
    public static HashMap<String, String> readVersionData() {
        try {
            URL url = new URL(urlString);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parse the response (JSON) into a HashMap
            String responseBody = response.toString();
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

            // Extract the "version" value from the response
            HashMap<String, String> dataMap = new HashMap<>();
            if (responseMap.containsKey("documents")) {
                // Get the first document
                Map<String, Object> document = (Map<String, Object>) ((ArrayList<Map>)responseMap.get("documents")).get(0);
                Map<String, Object> fields = (Map<String, Object>) document.get("fields");

                for(String field: fields.keySet()){
                    dataMap.put(field, ((Map<String, Object>)fields.get(field)).get("stringValue").toString());
                }
            }

            // Print the HashMap
            System.out.println("Version Data as HashMap: " + dataMap.toString());
            return dataMap;

        } catch (UnknownHostException uhe){
            LoggerUtil.getLogger().info("Unknown Host Exception Raise... System may be on VPN!");
            return null;
        }catch (Exception e) {
            LoggerUtil.error(e);
            return null;
        }
    }
}
