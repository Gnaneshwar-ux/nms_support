package com.nms.support.nms_support.model;

public class DataStoreRecord {
    private String tool;
    private String dataStore;
    private String column;
    private String type;
    private String value;

    public DataStoreRecord(String tool, String dataStore, String column, String type, String value) {
        this.tool = tool;
        this.dataStore = dataStore;
        this.column = column;
        this.type = type;
        this.value = value;
    }

    // Getters and setters

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getDataStore() {
        return dataStore;
    }

    public void setDataStore(String dataStore) {
        this.dataStore = dataStore;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

