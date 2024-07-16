package com.nms.support.nms_support.service.dataStoreTabPack;

import com.nms.support.nms_support.model.DataStoreRecord;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ParseDataStoreReport {

    public static ObservableList<DataStoreRecord> parseDSReport(String reportPath) {
        String filePath = reportPath;

        // Read data from the file
        String report = readFile(filePath);

        // Split the report into lines
        String[] lines = report.split("\n");

        // Initialize an ObservableList to store the parsed data
        ObservableList<DataStoreRecord> dataList = FXCollections.observableArrayList();

        // Initialize variables to keep track of previous tool and datastore values
        String prevTool = "";
        String prevDataStore = "";

        // Iterate over the lines and parse the data
        for (String line : lines) {
            // Split each line by comma
            if(line.length() == 0) continue;
            String[] parts = line.split(",");

            // Trim each part to remove leading/trailing spaces
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
            }

            // If the first two columns are empty, populate them with the previous values
            if (parts[0].isEmpty()) {
                parts[0] = prevTool;
            } else {
                prevTool = parts[0];
            }
            if (parts.length >= 2 && parts[1].isEmpty()) {
                parts[1] = prevDataStore;
            } else {
                prevDataStore = parts.length > 1 ? parts[1] : ""; // Handle the case where datastore is missing
            }

            // Skip adding the description column to the dataList
            String tool = parts.length > 0 ? parts[0] : "";
            String dataStore = parts.length > 1 ? parts[1] : "";
            String column = parts.length > 2 ? parts[2] : "";
            String type = parts.length > 4 ? parts[4] : "";
            String value = parts.length > 5 ? parts[5] : "";

            // Add the parsed data to the ObservableList
            dataList.add(new DataStoreRecord(tool, dataStore, column, type, value));
        }

        return dataList;
    }

    private static String readFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();
    }

    public static ObservableList<DataStoreRecord> filterRows(ObservableList<DataStoreRecord> tableData, String[] filterValues) {
        ObservableList<DataStoreRecord> filteredRows = FXCollections.observableArrayList();

        for (DataStoreRecord row : tableData) {
            boolean match = true;
            for (int i = 0; i < filterValues.length; i++) {
                String filterValue = filterValues[i];
                String cellValue;
                switch (i) {
                    case 0:
                        cellValue = row.getTool();
                        break;
                    case 1:
                        cellValue = row.getDataStore();
                        break;
                    case 2:
                        cellValue = row.getColumn();
                        break;
                    case 3:
                        cellValue = row.getType();
                        break;
                    case 4:
                        cellValue = row.getValue();
                        break;
                    default:
                        cellValue = "";
                }

                if (!filterValue.isEmpty() && !cellValue.contains(filterValue)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                filteredRows.add(row);
            }
        }

        return filteredRows;
    }
}
