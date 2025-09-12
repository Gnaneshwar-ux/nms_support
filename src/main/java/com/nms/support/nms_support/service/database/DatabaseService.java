package com.nms.support.nms_support.service.database;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Service class for handling Oracle database connections
 */
public class DatabaseService {
    private static final Logger logger = Logger.getLogger(DatabaseService.class.getName());
    
    private static final String ORACLE_JDBC_URL_TEMPLATE = "jdbc:oracle:thin:@%s:%d:%s";
    
    /**
     * Establishes a connection to Oracle database using the provided credentials
     * 
     * @param host Database host
     * @param port Database port
     * @param sid Database SID
     * @param username Database username
     * @param password Database password
     * @return Connection object if successful, null otherwise
     */
    public static Connection getConnection(String host, int port, String sid, String username, String password) {
        try {
            // Load Oracle JDBC driver
            Class.forName("oracle.jdbc.driver.OracleDriver");
            
            // Build connection URL
            String url = String.format(ORACLE_JDBC_URL_TEMPLATE, host, port, sid);
            
            // Set connection properties
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("oracle.net.CONNECT_TIMEOUT", "10000"); // 10 seconds timeout
            props.setProperty("oracle.jdbc.ReadTimeout", "30000"); // 30 seconds read timeout
            
            logger.info("Attempting to connect to Oracle database: " + host + ":" + port + "/" + sid);
            
            // Establish connection
            Connection connection = DriverManager.getConnection(url, props);
            
            if (connection != null && !connection.isClosed()) {
                logger.info("Successfully connected to Oracle database");
                return connection;
            } else {
                logger.warning("Failed to establish database connection");
                return null;
            }
            
        } catch (ClassNotFoundException e) {
            logger.severe("Oracle JDBC driver not found: " + e.getMessage());
            LoggerUtil.error(e);
            return null;
        } catch (SQLException e) {
            logger.severe("Database connection failed: " + e.getMessage());
            LoggerUtil.error(e);
            return null;
        } catch (Exception e) {
            logger.severe("Unexpected error during database connection: " + e.getMessage());
            LoggerUtil.error(e);
            return null;
        }
    }
    
    /**
     * Closes the database connection safely
     * 
     * @param connection Connection to close
     */
    public static void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("Database connection closed successfully");
                }
            } catch (SQLException e) {
                logger.warning("Error closing database connection: " + e.getMessage());
                LoggerUtil.error(e);
            }
        }
    }
    
    /**
     * Tests the database connection with the provided credentials
     * 
     * @param host Database host
     * @param port Database port
     * @param sid Database SID
     * @param username Database username
     * @param password Database password
     * @return true if connection is successful, false otherwise
     */
    public static boolean testConnection(String host, int port, String sid, String username, String password) {
        Connection connection = null;
        try {
            connection = getConnection(host, port, sid, username, password);
            return connection != null && !connection.isClosed();
        } catch (Exception e) {
            logger.warning("Connection test failed: " + e.getMessage());
            return false;
        } finally {
            closeConnection(connection);
        }
    }
}
