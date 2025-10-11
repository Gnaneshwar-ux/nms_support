/**
 * SSHJ-based SSH session management package.
 * 
 * <p>This package provides a modern, robust SSH session management implementation
 * using the SSHJ library. It is designed as a drop-in replacement for the JSch-based
 * {@code SSHSessionManager} with improved reliability, performance, and feature support.</p>
 * 
 * <h2>Main Classes:</h2>
 * <ul>
 *   <li>{@link com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager} - 
 *       Main interface for SSH operations, maintains API compatibility with old implementation</li>
 *   <li>{@link com.nms.support.nms_support.service.globalPack.sshj.UnifiedSSHManager} - 
 *       Session caching and lifecycle management</li>
 *   <li>{@link com.nms.support.nms_support.service.globalPack.sshj.PersistentSudoSession} - 
 *       Pre-sudoed shell session for elevated command execution</li>
 *   <li>{@link com.nms.support.nms_support.service.globalPack.sshj.SSHCommandResult} - 
 *       Command execution result container</li>
 * </ul>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Session caching per host/user/purpose</li>
 *   <li>Pre-sudoed shell sessions</li>
 *   <li>Command timeout and interruption support</li>
 *   <li>Progress callback support for long-running operations</li>
 *   <li>Concurrent stdout/stderr capture</li>
 *   <li>Thread-safe session access</li>
 *   <li>Automatic cleanup of stale sessions</li>
 *   <li>SFTP support</li>
 * </ul>
 * 
 * <h2>Basic Usage Example:</h2>
 * <pre>{@code
 * SSHJSessionManager manager = new SSHJSessionManager(
 *     "server.example.com",  // host
 *     "user",                // SSH username
 *     "password",            // SSH password
 *     22,                    // port
 *     "root",                // target user (sudo)
 *     "my_app"               // session purpose
 * );
 * 
 * try {
 *     manager.initialize();
 *     
 *     SSHJSessionManager.CommandResult result = 
 *         manager.executeCommand("ls -la /opt/app", 30);
 *     
 *     if (result.isSuccess()) {
 *         System.out.println("Output: " + result.getOutput());
 *     }
 * } finally {
 *     manager.close();
 * }
 * }</pre>
 * 
 * @since 1.0
 * @see com.nms.support.nms_support.service.globalPack.sshj.SSHJSessionManager
 */
package com.nms.support.nms_support.service.globalPack.sshj;

