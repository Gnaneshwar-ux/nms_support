package com.nms.support.nms_support.service.userdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nms.support.nms_support.model.LogEntity;
import com.nms.support.nms_support.model.LogWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LogManager implements IManager{

    private LogWrapper logWrapper;

    public LogWrapper getLogWrapper() {
        return logWrapper;
    }

    private File source;

    public LogManager(String sourcePath) {
        this.source = new File(sourcePath);
        ensureFileExists(this.source);
        initManager(this.source);
    }

    private void ensureFileExists(File source) {
        try {
            File parentDir = source.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    System.out.println("Directory created: " + parentDir.getAbsolutePath());
                } else {
                    System.out.println("Failed to create directory: " + parentDir.getAbsolutePath());
                }
            }

            if (!source.exists()) {
                if (source.createNewFile()) {
                    System.out.println("File created: " + source.getAbsolutePath());
                } else {
                    System.out.println("Failed to create file: " + source.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initManager(File source) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (source.length() == 0) {
                logWrapper = new LogWrapper();
                return;
            }
            logWrapper = objectMapper.readValue(source, LogWrapper.class);
            if (logWrapper == null) {
                logWrapper = new LogWrapper();
            }
        } catch (Exception e) {
            e.printStackTrace();
            logWrapper = new LogWrapper();
        }
    }

    public void addLog(LogEntity log) {
        List<LogEntity> logs = logWrapper.getLogs();
        if (logs == null) {
            logs = new ArrayList<>();
            logWrapper.setLogs(logs);
        }
        logs.add(log);
    }

    public void updateLog(String logId, LogEntity updatedLog) {
        List<LogEntity> logs = logWrapper.getLogs();
        if (logs != null) {
            for (int i = 0; i < logs.size(); i++) {
                if (logs.get(i).getId().equals(logId)) {
                    logs.set(i, updatedLog);
                    return;
                }
            }
        }
        System.out.println("Log not found: " + logId);
    }

    public void removeLog(String logId) {
        List<LogEntity> logs = logWrapper.getLogs();
        if (logs != null) {
            logs.removeIf(log -> log.getId().equals(logId));
        }
    }

    public LogEntity getLogById(String logId) {
        List<LogEntity> logs = logWrapper.getLogs();
        if (logs != null) {
            for (LogEntity log : logs) {
                if (log.getId().equals(logId)) {
                    return log;
                }
            }
        }
        return null;
    }

    public List<String> getLogIds() {
        List<String> logIds = new ArrayList<>();
        List<LogEntity> logs = logWrapper.getLogs();
        if (logs != null) {
            for (LogEntity log : logs) {
                logIds.add(log.getId());
            }
        }
        return logIds;
    }

    public boolean saveData() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (logWrapper == null) {
                logWrapper = new LogWrapper();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.source, logWrapper);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean contains(LogEntity LogEntity){
        if(logWrapper.getLogs() != null){
            for(LogEntity le: logWrapper.getLogs()){
                if(le.equals(LogEntity)){
                    return true;
                }
            }
        }
        return false;
    }

    public void clearAll(){
        logWrapper.clearlogs();
    }
}
