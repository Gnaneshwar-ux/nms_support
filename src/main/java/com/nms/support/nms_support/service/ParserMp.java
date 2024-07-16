package com.nms.support.nms_support.service;

import com.nms.support.nms_support.model.Coordinate;
import com.nms.support.nms_support.model.Diagram;
import com.nms.support.nms_support.model.Entity;
import com.nms.support.nms_support.model.Geometry;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserMp implements Parser{

    @Override
    public List<Entity> parseFile(String file) {
        List<Entity> entities = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                entities = processFile(reader);
        } catch (IOException e) {
            e.printStackTrace(); // Handle or log the IOException
        }
        System.out.println("Processed = "+entities.size()+" entities");
        return entities;
    }

    private List<Entity> processFile(BufferedReader reader) throws IOException {
        String line = null;
        List<Entity> eList = new ArrayList<>();
        while((line = reader.readLine()) != null){
            if(hasUnquotedCurlyBrace(line,'{')){
                Entity e = new Entity(extractEntityId(line), extractEntityName(line));
                System.out.println("processing = "+line);
                processEntity(reader, e);
                eList.add(e);
            }
        }
        return eList;
    }

    private void processEntity(BufferedReader reader, Entity e) throws IOException {
        String line = null;
        while((line = reader.readLine()) != null){
            if(hasUnquotedCurlyBrace(line,'{')){
                Diagram d = new Diagram(extractDiagramType(line));
                processDiagram(reader, d);
                e.setDiagram(d);
            }
            else{
                attachEntityAttribute(line, e);
            }
            if(hasUnquotedCurlyBrace(line,'}')){
                return;
            }
        }

    }

    public void attachEntityAttribute(String line, Entity entity) {
        // Define regex patterns for different attribute formats
        Pattern keyValuePattern = Pattern.compile("^(\\w+)\\s*=\\s*(.+);");
        Pattern portPattern = Pattern.compile("^(PORT_[AB])\\s*=\\s*(\\d+);");
        Pattern attributePattern = Pattern.compile("^ATTRIBUTE\\[(\\w+\\.\\w+)]=\"([^\"]*)\";$");

        Matcher matcher;

        // Check and match against different patterns
        matcher = keyValuePattern.matcher(line);
        if (matcher.matches()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            entity.addAttribute(key, value);
            return;
        }

        matcher = portPattern.matcher(line);
        if (matcher.matches()) {
            String portName = matcher.group(1);
            String portValue = matcher.group(2);
            if (portName.equals("PORT_A")) {
                entity.setPORT_A(portValue);
            } else if (portName.equals("PORT_B")) {
                entity.setPORT_B(portValue);
            }
            return;
        }

        matcher = attributePattern.matcher(line);
        if (matcher.matches()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            entity.addAttribute(key, value);
            return;
        }

        // If no pattern matches, print a warning
        //System.out.println("Provided line cannot be processed:\n" + line);
    }

    private void processDiagram(BufferedReader reader, Diagram d) throws IOException {
        String line = null;
        while((line = reader.readLine()) != null){
            if(hasUnquotedCurlyBrace(line,'{')){
                Geometry g = new Geometry();
                processGeometry(reader, g);
                d.setGeometry(g);
            }
            else{
                attachDiagramData(line, d);
            }
            if(hasUnquotedCurlyBrace(line,'}')){
                return;
            }
        }
    }

    public static void attachDiagramData(String line, Diagram d) {
        // Define the regex pattern to match key-value pairs
        Pattern pattern = Pattern.compile("([\\w]+)\\s*=\\s*([^;]+);");
        Matcher matcher = pattern.matcher(line);

        // Iterate over matches and populate diagram properties
        while (matcher.find()) {
            String key = matcher.group(1);   // Extract the key
            String value = matcher.group(2); // Extract the value
            d.setData(key, value);
        }
    }

    private void processGeometry(BufferedReader reader, Geometry g) throws IOException {
        String line = null;
        while((line = reader.readLine()) != null){
            if(hasUnquotedCurlyBrace(line,'{')) {
                System.out.println("MP file has undefined depth of objects");
            }
            else{
                attachCoordinates(line,g);
            }
            if(hasUnquotedCurlyBrace(line,'}')){
                return;
            }
        }
    }

    private void attachCoordinates(String line, Geometry g){
        Pattern pattern = Pattern.compile("\\(\\s*(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)\\s*\\)");
        Matcher matcher = pattern.matcher(line);

        ArrayList<Coordinate> coordinates = new ArrayList<>();

        // Find all matches of coordinates in the input line
        while (matcher.find()) {
            String xCoord = matcher.group(1); // Extract x coordinate
            String yCoord = matcher.group(2); // Extract y coordinate

            boolean b = g.addCoordinate(xCoord,yCoord);
            if(!b)System.out.println("Failed to attach coordinates");
        }

    }


    private static boolean hasUnquotedCurlyBrace(String line, char curlyBrace) {
        boolean insideQuotes = false;
        char quoteChar = '"';

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);
            if (currentChar == quoteChar) {
                // Toggle the insideQuotes flag when encountering a quote character
                insideQuotes = !insideQuotes;
            } else if (currentChar == curlyBrace && !insideQuotes) {
                // Found a curly brace outside of quoted string
                return true;
            }
        }

        // No unquoted curly brace found
        return false;
    }

    public static String extractEntityName(String input) {
        // Define the regex pattern to match the name
        Pattern pattern = Pattern.compile("ADD\\s+(.+?)\\s+([\\w.@#]+)\\s+\\{");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(1); // Extract the first capturing group (name)
        }

        return null; // Name not found
    }

    public static String extractEntityId(String input) {
        // Define the regex pattern to match the ID
        Pattern pattern = Pattern.compile("ADD\\s+.+?\\s+([\\w.@#]+)\\s+\\{");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(1); // Extract the first capturing group (ID)
        }

        return null; // ID not found
    }

    public static String extractDiagramType(String input) {
        // Define the regex pattern to match the diagram type
        Pattern pattern = Pattern.compile("DIAGRAM\\[(.+?)]\\s*=\\s*\\{");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(1); // Extract the first capturing group (type)
        }

        return null; // Diagram type not found
    }
}
