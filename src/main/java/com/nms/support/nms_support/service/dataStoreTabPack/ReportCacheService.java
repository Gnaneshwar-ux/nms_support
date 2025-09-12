package com.nms.support.nms_support.service.dataStoreTabPack;

import com.nms.support.nms_support.model.DataStoreRecord;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Service to manage caching of datastore reports and their metadata
 * 
 * @author Generated
 */
public class ReportCacheService {
    private static final Logger logger = LoggerUtil.getLogger();
    private static ReportCacheService instance;
    
    // Cache for report data by project name
    private final Map<String, ObservableList<DataStoreRecord>> reportDataCache = new HashMap<>();
    
    // Cache for report metadata by project name
    private final Map<String, ReportMetadata> reportMetadataCache = new HashMap<>();
    
    private ReportCacheService() {
        // Private constructor for singleton
    }
    
    public static synchronized ReportCacheService getInstance() {
        if (instance == null) {
            instance = new ReportCacheService();
        }
        return instance;
    }
    
    /**
     * Cache report data for a project
     */
    public void cacheReportData(String projectName, ObservableList<DataStoreRecord> data) {
        logger.info("Caching report data for project: " + projectName);
        reportDataCache.put(projectName, FXCollections.observableArrayList(data));
    }
    
    /**
     * Get cached report data for a project
     */
    public ObservableList<DataStoreRecord> getCachedReportData(String projectName) {
        ObservableList<DataStoreRecord> cached = reportDataCache.get(projectName);
        if (cached != null) {
            logger.info("Retrieved cached report data for project: " + projectName);
            return FXCollections.observableArrayList(cached);
        }
        return null;
    }
    
    /**
     * Cache report metadata for a project
     */
    public void cacheReportMetadata(String projectName, ReportMetadata metadata) {
        logger.info("Caching report metadata for project: " + projectName);
        reportMetadataCache.put(projectName, metadata);
    }
    
    /**
     * Get cached report metadata for a project
     */
    public ReportMetadata getCachedReportMetadata(String projectName) {
        return reportMetadataCache.get(projectName);
    }
    
    /**
     * Check if report data is cached for a project
     */
    public boolean hasCachedReportData(String projectName) {
        return reportDataCache.containsKey(projectName);
    }
    
    /**
     * Check if report metadata is cached for a project
     */
    public boolean hasCachedReportMetadata(String projectName) {
        return reportMetadataCache.containsKey(projectName);
    }
    
    /**
     * Clear cache for a specific project
     */
    public void clearProjectCache(String projectName) {
        logger.info("Clearing cache for project: " + projectName);
        reportDataCache.remove(projectName);
        reportMetadataCache.remove(projectName);
    }
    
    /**
     * Clear all cached data
     */
    public void clearAllCache() {
        logger.info("Clearing all cached report data");
        reportDataCache.clear();
        reportMetadataCache.clear();
    }
    
    /**
     * Get the report file path for a project
     */
    public String getReportFilePath(ProjectEntity project) {
        String user = System.getProperty("user.name");
        return "C:/Users/" + user + "/Documents/nms_support_data/datastore_reports/report_" + project.getName() + ".txt";
    }
    
    /**
     * Check if a report file exists and get its metadata
     */
    public ReportMetadata getReportFileMetadata(ProjectEntity project) {
        String reportPath = getReportFilePath(project);
        Path path = Paths.get(reportPath);
        
        if (!Files.exists(path)) {
            return null;
        }
        
        try {
            FileTime lastModified = Files.getLastModifiedTime(path);
            long fileSize = Files.size(path);
            
            LocalDateTime lastModifiedDateTime = LocalDateTime.ofInstant(
                lastModified.toInstant(), 
                ZoneId.systemDefault()
            );
            
            return new ReportMetadata(
                reportPath,
                lastModifiedDateTime,
                fileSize,
                true
            );
        } catch (IOException e) {
            logger.warning("Error reading report file metadata: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Load report data from file and cache it
     */
    public ObservableList<DataStoreRecord> loadAndCacheReportData(ProjectEntity project) {
        String reportPath = getReportFilePath(project);
        Path path = Paths.get(reportPath);
        
        if (!Files.exists(path)) {
            logger.warning("Report file does not exist: " + reportPath);
            return null;
        }
        
        try {
            ObservableList<DataStoreRecord> data = ParseDataStoreReport.parseDSReport(reportPath);
            ReportMetadata metadata = getReportFileMetadata(project);
            
            // Cache the data and metadata
            cacheReportData(project.getName(), data);
            if (metadata != null) {
                cacheReportMetadata(project.getName(), metadata);
            }
            
            logger.info("Loaded and cached report data for project: " + project.getName());
            return data;
        } catch (Exception e) {
            logger.severe("Error loading report data: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Metadata class for report information
     */
    public static class ReportMetadata {
        private final String filePath;
        private final LocalDateTime lastGenerated;
        private final long fileSize;
        private final boolean exists;
        
        public ReportMetadata(String filePath, LocalDateTime lastGenerated, long fileSize, boolean exists) {
            this.filePath = filePath;
            this.lastGenerated = lastGenerated;
            this.fileSize = fileSize;
            this.exists = exists;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public LocalDateTime getLastGenerated() {
            return lastGenerated;
        }
        
        public String getFormattedLastGenerated() {
            if (lastGenerated == null) return "Never";
            return lastGenerated.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        public String getRelativeTimeAgo() {
            if (lastGenerated == null) return "Never generated";
            
            LocalDateTime now = LocalDateTime.now();
            java.time.Duration duration = java.time.Duration.between(lastGenerated, now);
            
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;
            
            if (days > 0) {
                return String.format("Generated %d day%s, %d hr%s ago", 
                    days, days == 1 ? "" : "s", 
                    hours, hours == 1 ? "" : "s");
            } else if (hours > 0) {
                return String.format("Generated %d hr%s, %d min%s ago", 
                    hours, hours == 1 ? "" : "s", 
                    minutes, minutes == 1 ? "" : "s");
            } else if (minutes > 0) {
                return String.format("Generated %d min%s, %d sec%s ago", 
                    minutes, minutes == 1 ? "" : "s", 
                    seconds, seconds == 1 ? "" : "s");
            } else {
                return String.format("Generated %d sec%s ago", 
                    seconds, seconds == 1 ? "" : "s");
            }
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public String getFormattedFileSize() {
            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format("%.1f KB", fileSize / 1024.0);
            } else {
                return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
            }
        }
        
        public boolean exists() {
            return exists;
        }
        
        public boolean isRecent(long maxAgeMinutes) {
            if (lastGenerated == null) return false;
            return lastGenerated.isAfter(LocalDateTime.now().minusMinutes(maxAgeMinutes));
        }
    }
}
