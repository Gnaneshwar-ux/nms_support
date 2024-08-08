package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

public class Utils {
    public static void copyInputStream(InputStream inputStream, File outputFile) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
    public static String validatePath(String path) {
        Path normalizedPath = Paths.get(path).normalize();
        return normalizedPath.toString();
    }
    
    public static void secureFactory(TransformerFactory trfactory) throws TransformerConfigurationException {
         trfactory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
         trfactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
         trfactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "");
    }
    
}
