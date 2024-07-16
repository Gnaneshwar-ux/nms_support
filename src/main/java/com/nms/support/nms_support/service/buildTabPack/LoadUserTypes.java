/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.nms.support.nms_support.service.buildTabPack;

/**
 *
 * @author Gnaneshwar
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SQLParser {

    public static List<String> parseSQLFile(String filePath, String targetProduct) {

        System.out.println("parseSQLFile invoked");
        List<String> resultList = new ArrayList<>();

        filePath = findLatestFilePath(filePath);

        System.out.println(filePath);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            StringBuilder sqlContent = new StringBuilder();


            boolean comment  = false;
            while ((line = reader.readLine()) != null) {
                if(line.contains("/*")) comment = true;
                if(line.contains("*/")){
                    comment = false;
                    continue;
                }
                if(comment) continue;
                sqlContent.append(line).append("\n");
                //System.out.println(line);
            }

            String sqlPattern = "INSERT[^;]*;";
            Pattern pattern = Pattern.compile(sqlPattern, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sqlContent.toString().replace("\n", ""));

            while (matcher.find()) {

                if(!matcher.group().contains("env_code"))continue;

                String valuesPattern = "VALUES\\s*\\(([^)]*)\\);";
                Matcher valuesMatcher = Pattern.compile(valuesPattern).matcher(matcher.group());

                if (valuesMatcher.find()) {
                    //System.out.println(valuesMatcher.group(1));
                    String[] values = valuesMatcher.group(1).split(",");
                    String product = values[0].trim().replaceAll("[\"']", "").toUpperCase();
                    String codeName = values[1].trim().replaceAll("[\"']", "");

                    if (targetProduct.toUpperCase().equals(product.toUpperCase())) {
                        resultList.add(codeName);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }


    // Method to find the complete file path of the latest file
    private static String findLatestFilePath(String directoryPath) {
        // Create a File object for the directory
        File directory = new File(directoryPath);
        System.out.println(directoryPath);
        // Get all files in the directory
        File[] files = directory.listFiles((dir, name) ->
                name.endsWith("_ceslogin.sql") &&
                        !name.matches(".*_(schema|evergy)_ceslogin\\.sql"));
        if (files != null && files.length > 0) {
            // Sort the files by last modified time to get the latest one
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));

            // Return the complete file path of the latest file
            return files[files.length - 1].getPath();
        } else {
            return null;
        }
    }

}
