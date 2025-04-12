package com.nms.support.nms_support.service.globalPack;

import java.io.IOException;
import java.util.Properties;

public class AppDetails {
    public static String getApplicationVersion() {
        Properties properties = new Properties();
        try {
            properties.load(AppDetails.class.getClassLoader().getResourceAsStream("application.properties"));
            String version = properties.getProperty("app.version");
            System.out.println("App Version: " + version);
            return version;
        } catch (IOException e) {
            e.printStackTrace();
            return "NULL";
        }
    }
}
