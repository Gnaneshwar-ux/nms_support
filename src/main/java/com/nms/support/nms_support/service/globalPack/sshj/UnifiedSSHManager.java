package com.nms.support.nms_support.service.globalPack.sshj;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Unified SSH Manager using SSHJ for robust, production-ready SSH session management.
 * 
 * Features:
 * - Session caching per (host, user, purpose) to avoid repeated connections
 * - Automatic cleanup of stale/disconnected sessions
 * - Thread-safe access to cached sessions
 * - Heartbeat checks to validate session liveness
 * - Support for SSH key authentication and password authentication
 * 
 * This manager maintains a pool of active SSH connections and associated
 * PersistentSudoSession instances for command execution.
 */
public class UnifiedSSHManager {
    
    // Session cache: key = "host:port:user:targetUser:purpose"
    private static final Map<String, CachedSSHSession> sessionCache = new ConcurrentHashMap<>();
    
    // Lock for thread-safe session operations
    private static final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Session timeout: 30 minutes of inactivity
    private static final long SESSION_TIMEOUT_MS = 60 * 60 * 1000;
    
    // Connection timeout: 30 seconds
    private static final int CONNECTION_TIMEOUT_MS = 30_000;
    
    /**
     * Get or create a PersistentSudoSession for the given connection parameters.
     * 
     * This method checks the cache first. If a valid session exists, it returns it after
     * validating liveness. If not, it creates a new SSH connection and persistent session.
     * 
     * @param host SSH host to connect to
     * @param port SSH port
     * @param sshUser SSH username for initial authentication
     * @param sshPassword SSH password (can be null if using key auth)
     * @param keyFilePath Path to SSH private key file (can be null if using password auth)
     * @param targetUser Target user to sudo to (null if no sudo needed)
     * @param sudoPassword Password for sudo (can be null if passwordless sudo or no sudo)
     * @param purpose Session purpose for isolation (e.g., "project_only", "product_only")
     * @return A PersistentSudoSession ready for command execution
     * @throws IOException If connection fails
     */
    public static PersistentSudoSession getOrCreateSession(
            String host,
            int port,
            String sshUser,
            String sshPassword,
            String keyFilePath,
            String targetUser,
            String sudoPassword,
            String purpose
    ) throws IOException {
        
        String cacheKey = generateCacheKey(host, port, sshUser, targetUser, purpose);
        
        cacheLock.readLock().lock();
        try {
            CachedSSHSession cached = sessionCache.get(cacheKey);
            
            // Check if cached session exists and is still valid
            if (cached != null && !isSessionExpired(cached)) {
                if (cached.persistentSession.isAlive()) {
                    LoggerUtil.getLogger().info("♻️ Reusing cached SSHJ session: " + cacheKey);
                    cached.updateLastActivity();
                    return cached.persistentSession;
                } else {
                    LoggerUtil.getLogger().warning("⚠️ Cached session is dead, will recreate: " + cacheKey);
                }
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        // Need to create new session
        cacheLock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            CachedSSHSession cached = sessionCache.get(cacheKey);
            if (cached != null && !isSessionExpired(cached) && cached.persistentSession.isAlive()) {
                LoggerUtil.getLogger().info("♻️ Another thread created session, reusing: " + cacheKey);
                cached.updateLastActivity();
                return cached.persistentSession;
            }
            
            // Remove old session if exists
            if (cached != null) {
                LoggerUtil.getLogger().info("🗑️ Removing stale session: " + cacheKey);
                closeSessionQuietly(cached);
                sessionCache.remove(cacheKey);
            }
            
            // Create new SSH connection
            LoggerUtil.getLogger().info("🔐 Creating new SSHJ session: " + cacheKey);
            SSHClient sshClient = null;
            try {
                sshClient = createSSHClient(host, port, sshUser, sshPassword, keyFilePath);
                LoggerUtil.getLogger().info("✓ SSH client created successfully");
            } catch (IOException e) {
                LoggerUtil.getLogger().severe("Failed to create SSH client: " + e.getMessage());
                LoggerUtil.error(e);
                throw e;
            }
            
            // Create persistent sudo session
            PersistentSudoSession persistentSession = null;
            try {
                persistentSession = new PersistentSudoSession(
                    sshClient, targetUser, sudoPassword, purpose
                );
                LoggerUtil.getLogger().info("✓ PersistentSudoSession created successfully");
            } catch (IOException e) {
                LoggerUtil.getLogger().severe("Failed to create PersistentSudoSession: " + e.getMessage());
                LoggerUtil.error(e);
                // Clean up SSH client on failure
                try {
                    if (sshClient != null && sshClient.isConnected()) {
                        sshClient.disconnect();
                    }
                } catch (Exception cleanupError) {
                    LoggerUtil.getLogger().fine("Error during SSH client cleanup: " + cleanupError.getMessage());
                }
                throw e;
            }
            
            // Cache the session
            CachedSSHSession newCached = new CachedSSHSession(sshClient, persistentSession, cacheKey);
            sessionCache.put(cacheKey, newCached);
            
            LoggerUtil.getLogger().info("✅ New SSHJ session created and cached: " + cacheKey);
            return persistentSession;
            
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Close a specific session by cache key.
     * 
     * @param host SSH host
     * @param port SSH port
     * @param sshUser SSH username
     * @param targetUser Target user (null if none)
     * @param purpose Session purpose
     */
    public static void closeSession(String host, int port, String sshUser, String targetUser, String purpose) {
        String cacheKey = generateCacheKey(host, port, sshUser, targetUser, purpose);
        
        cacheLock.writeLock().lock();
        try {
            CachedSSHSession cached = sessionCache.remove(cacheKey);
            if (cached != null) {
                LoggerUtil.getLogger().info("🗑️ Closing session: " + cacheKey);
                closeSessionQuietly(cached);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Close all cached sessions.
     * Should be called during application shutdown.
     */
    public static void closeAllSessions() {
        LoggerUtil.getLogger().info("🗑️ Closing all SSHJ sessions...");
        
        cacheLock.writeLock().lock();
        try {
            int count = sessionCache.size();
            for (CachedSSHSession cached : sessionCache.values()) {
                closeSessionQuietly(cached);
            }
            sessionCache.clear();
            LoggerUtil.getLogger().info("✅ Closed " + count + " SSHJ sessions");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Clean up expired sessions from the cache.
     * This can be called periodically to prevent memory leaks.
     */
    public static void cleanupExpiredSessions() {
        LoggerUtil.getLogger().info("🧹 Cleaning up expired SSHJ sessions...");
        
        cacheLock.writeLock().lock();
        try {
            int removed = 0;
            for (Map.Entry<String, CachedSSHSession> entry : sessionCache.entrySet()) {
                if (isSessionExpired(entry.getValue())) {
                    LoggerUtil.getLogger().info("🗑️ Removing expired session: " + entry.getKey());
                    closeSessionQuietly(entry.getValue());
                    sessionCache.remove(entry.getKey());
                    removed++;
                }
            }
            LoggerUtil.getLogger().info("✅ Cleaned up " + removed + " expired sessions");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Get cache statistics for monitoring.
     * 
     * @return Statistics string
     */
    public static String getCacheStatistics() {
        cacheLock.readLock().lock();
        try {
            int total = sessionCache.size();
            int active = 0;
            int expired = 0;
            
            for (CachedSSHSession cached : sessionCache.values()) {
                if (isSessionExpired(cached)) {
                    expired++;
                } else {
                    active++;
                }
            }
            
            return String.format("SSHJ Cache: %d total, %d active, %d expired", total, active, expired);
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    /**
     * Create and configure an SSH client connection.
     */
    private static SSHClient createSSHClient(
            String host,
            int port,
            String sshUser,
            String sshPassword,
            String keyFilePath
    ) throws IOException {
        
        SSHClient client = new SSHClient();
        
        // Configure client
        client.addHostKeyVerifier(new PromiscuousVerifier()); // TODO: Replace with proper host key verification in production
        client.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        client.setTimeout(CONNECTION_TIMEOUT_MS);
        
        // Connect
        client.connect(host, port);
        LoggerUtil.getLogger().info("✓ SSHJ connected to " + host + ":" + port);
        
        // Authenticate
        if (keyFilePath != null && !keyFilePath.trim().isEmpty()) {
            File keyFile = new File(keyFilePath);
            if (keyFile.exists()) {
                LoggerUtil.getLogger().info("🔑 Authenticating with SSH key: " + keyFilePath);
                KeyProvider keyProvider = client.loadKeys(keyFilePath);
                client.authPublickey(sshUser, keyProvider);
            } else {
                throw new IOException("SSH key file not found: " + keyFilePath);
            }
        } else if (sshPassword != null && !sshPassword.trim().isEmpty()) {
            LoggerUtil.getLogger().info("🔑 Authenticating with password for user: " + sshUser);
            client.authPassword(sshUser, sshPassword);
        } else {
            throw new IOException("No authentication method provided (password or key)");
        }
        
        LoggerUtil.getLogger().info("✓ SSHJ authenticated as " + sshUser);
        return client;
    }
    
    /**
     * Generate cache key for session identification.
     */
    private static String generateCacheKey(String host, int port, String sshUser, String targetUser, String purpose) {
        String target = (targetUser == null || targetUser.trim().isEmpty()) ? "-" : targetUser;
        String sessionPurpose = (purpose == null || purpose.trim().isEmpty()) ? "default" : purpose;
        return host + ":" + port + ":" + sshUser + ":" + target + ":" + sessionPurpose;
    }
    
    /**
     * Check if a cached session has expired.
     */
    private static boolean isSessionExpired(CachedSSHSession cached) {
        return (System.currentTimeMillis() - cached.lastActivityTime) > SESSION_TIMEOUT_MS;
    }
    
    /**
     * Close a session quietly without throwing exceptions.
     */
    private static void closeSessionQuietly(CachedSSHSession cached) {
        try {
            if (cached.persistentSession != null) {
                cached.persistentSession.close();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Error closing persistent session: " + e.getMessage());
        }
        
        try {
            if (cached.sshClient != null && cached.sshClient.isConnected()) {
                cached.sshClient.disconnect();
            }
        } catch (Exception e) {
            LoggerUtil.getLogger().warning("Error disconnecting SSH client: " + e.getMessage());
        }
    }
    
    // ===== INNER CLASS =====
    
    /**
     * Container for cached SSH session information.
     */
    private static class CachedSSHSession {
        final SSHClient sshClient;
        final PersistentSudoSession persistentSession;
        volatile long lastActivityTime;
        
        CachedSSHSession(SSHClient sshClient, PersistentSudoSession persistentSession, String cacheKey) {
            this.sshClient = sshClient;
            this.persistentSession = persistentSession;
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        void updateLastActivity() {
            this.lastActivityTime = System.currentTimeMillis();
        }
    }
}

