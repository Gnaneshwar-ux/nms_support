package com.nms.support.nms_support.service.globalPack.sshj;

/**
 * Container for SSH command execution results.
 * 
 * Holds stdout, stderr, and exit code from a command execution.
 */
public class SSHCommandResult {
    
    private final String output;
    private final String error;
    private final int exitCode;
    
    /**
     * Create a command result.
     * 
     * @param output Standard output from the command
     * @param error Standard error from the command
     * @param exitCode Exit code (0 typically means success)
     */
    public SSHCommandResult(String output, String error, int exitCode) {
        this.output = output != null ? output : "";
        this.error = error != null ? error : "";
        this.exitCode = exitCode;
    }
    
    /**
     * Create a command result with only stdout (stderr empty).
     * 
     * @param output Standard output
     * @param exitCode Exit code
     */
    public SSHCommandResult(String output, int exitCode) {
        this(output, "", exitCode);
    }
    
    /**
     * Get standard output.
     * 
     * @return Standard output string
     */
    public String getOutput() {
        return output;
    }
    
    /**
     * Get standard error.
     * 
     * @return Standard error string
     */
    public String getError() {
        return error;
    }
    
    /**
     * Get exit code.
     * 
     * @return Exit code (0 typically means success)
     */
    public int getExitCode() {
        return exitCode;
    }
    
    /**
     * Check if command was successful (exit code 0).
     * 
     * @return true if exit code is 0, false otherwise
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
    
    /**
     * Get combined output (stdout + stderr).
     * 
     * @return Combined output
     */
    public String getCombinedOutput() {
        StringBuilder combined = new StringBuilder();
        if (!output.isEmpty()) {
            combined.append(output);
        }
        if (!error.isEmpty()) {
            if (combined.length() > 0) {
                combined.append("\n");
            }
            combined.append(error);
        }
        return combined.toString();
    }
    
    @Override
    public String toString() {
        return String.format("SSHCommandResult{exitCode=%d, outputLen=%d, errorLen=%d}", 
            exitCode, output.length(), error.length());
    }
}

