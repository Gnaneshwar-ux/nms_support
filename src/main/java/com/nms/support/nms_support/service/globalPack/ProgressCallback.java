package com.nms.support.nms_support.service.globalPack;

/**
 * Callback interface for tracking progress of long-running operations
 */
public interface ProgressCallback {
    /**
     * Called when progress is made during the operation
     * @param percentage Progress percentage (0-100)
     * @param message Optional message describing current activity
     */
    void onProgress(int percentage, String message);
    
    /**
     * Called when the operation completes successfully
     * @param message Completion message
     */
    void onComplete(String message);
    
    /**
     * Called when the operation fails
     * @param error Error message describing the failure
     */
    void onError(String error);
    
    /**
     * Check if the operation has been cancelled
     * @return true if cancelled, false otherwise
     */
    boolean isCancelled();
}
