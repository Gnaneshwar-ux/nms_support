package com.nms.support.nms_support.service.globalPack;

/**
 * Adapter class that implements ProgressCallback to work with ProcessMonitor
 * Enhanced to support progress message logging for better visualization
 */
public class ProcessMonitorAdapter implements ProgressCallback {
    private final ProcessMonitor processMonitor;
    private final String stepId;

    public ProcessMonitorAdapter(ProcessMonitor processMonitor, String stepId) {
        this.processMonitor = processMonitor;
        this.stepId = stepId;
    }

    @Override
    public void onProgress(int percentage, String message) {
        // Use the new method that supports both progress and message updates
        processMonitor.updateState(stepId, percentage, message);
    }

    @Override
    public void onComplete(String message) {
        processMonitor.markComplete(stepId, message);
    }

    @Override
    public void onError(String error) {
        processMonitor.markFailed(stepId, error);
    }

    @Override
    public boolean isCancelled() {
        return !processMonitor.isRunning();
    }
    
    /**
     * Additional method to log messages without changing progress
     * This can be used for informational messages during processing
     */
    public void logMessage(String message) {
        processMonitor.logMessage(stepId, message);
    }
}
