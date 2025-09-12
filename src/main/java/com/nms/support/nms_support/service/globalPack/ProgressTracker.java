package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Enhanced progress tracking system for detailed operation progress
 */
public class ProgressTracker {
    
    public interface ProgressCallback {
        void updateProgress(int percent, String message);
        void updateStatus(String status);
    }
    
    private final ProgressCallback callback;
    private final AtomicInteger currentProgress = new AtomicInteger(0);
    private final String operationName;
    
    public ProgressTracker(String operationName, ProgressCallback callback) {
        this.operationName = operationName;
        this.callback = callback;
    }
    
    public void updateProgress(int percent, String message) {
        currentProgress.set(percent);
        if (callback != null) {
            Platform.runLater(() -> callback.updateProgress(percent, message));
        }
    }
    
    public void updateStatus(String status) {
        if (callback != null) {
            Platform.runLater(() -> callback.updateStatus(status));
        }
    }
    
    public void incrementProgress(int increment, String message) {
        int newProgress = Math.min(100, currentProgress.get() + increment);
        updateProgress(newProgress, message);
    }
    
    public int getCurrentProgress() {
        return currentProgress.get();
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    // Predefined progress ranges for different operations
    public static class ProgressRanges {
        public static final int VALIDATION_START = 0;
        public static final int VALIDATION_END = 10;
        
        public static final int SVN_CHECK_START = 10;
        public static final int SVN_DELETE_START = 20;
        public static final int SVN_CHECKOUT_START = 30;
        public static final int SVN_CHECKOUT_END = 80;
        public static final int SVN_CONFIG_START = 85;
        public static final int SVN_CONFIG_END = 100;
        
        public static final int DOWNLOAD_START = 10;
        public static final int DOWNLOAD_CONNECT = 20;
        public static final int DOWNLOAD_TRANSFER_START = 30;
        public static final int DOWNLOAD_TRANSFER_END = 85;
        public static final int DOWNLOAD_EXTRACT_START = 90;
        public static final int DOWNLOAD_EXTRACT_END = 100;
        
        public static final int CONFIG_START = 25;
        public static final int CONFIG_END = 100;
        
        public static final int FINALIZE_START = 10;
        public static final int FINALIZE_WAIT = 50;
        public static final int FINALIZE_LOAD = 95;
        public static final int FINALIZE_END = 100;
    }
}
