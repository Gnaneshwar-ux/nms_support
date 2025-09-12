package com.nms.support.nms_support.service.database;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.Mappings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service class for fetching Usertype data from Oracle database
 */
public class DatabaseUserTypeService {
    private static final Logger logger = Logger.getLogger(DatabaseUserTypeService.class.getName());
    
    // SQL query to fetch usertype data from ENV_CODE table
    private static final String FETCH_USERTYPES_QUERY_ENV_CODE = 
        "SELECT PRODUCT, CODE_NAME FROM ENV_CODE WHERE PRODUCT IS NOT NULL AND CODE_NAME IS NOT NULL ORDER BY PRODUCT, CODE_NAME";
    
    // SQL query to fetch usertype data from ENV_CODE_1 table
    private static final String FETCH_USERTYPES_QUERY_ENV_CODE_1 = 
        "SELECT PRODUCT, CODE_NAME FROM ENV_CODE_1 WHERE PRODUCT IS NOT NULL AND CODE_NAME IS NOT NULL ORDER BY PRODUCT, CODE_NAME";
    
    /**
     * Fetches Usertype data from the database and returns it in the same format as the SQL parser
     * 
     * @param host Database host
     * @param port Database port
     * @param sid Database SID
     * @param username Database username
     * @param password Database password
     * @return HashMap with app names as keys and lists of usertypes as values
     */
    public static HashMap<String, List<String>> fetchUserTypesFromDatabase(String host, int port, String sid, 
                                                                          String username, String password) {
        HashMap<String, List<String>> resultMap = new HashMap<>();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        
        try {
            logger.info("Fetching usertypes from database: " + host + ":" + port + "/" + sid);
            
            // Get database connection
            connection = DatabaseService.getConnection(host, port, sid, username, password);
            if (connection == null) {
                logger.severe("Failed to establish database connection");
                return resultMap;
            }
            
            // Determine which table exists and prepare query
            String query = determineTableAndQuery(connection);
            if (query == null) {
                logger.warning("Neither ENV_CODE nor ENV_CODE_1 table found");
                return resultMap;
            }
            
            statement = connection.prepareStatement(query);
            resultSet = statement.executeQuery();
            
            // Process results
            while (resultSet.next()) {
                String product = resultSet.getString("PRODUCT");
                String codeName = resultSet.getString("CODE_NAME");
                
                if (product != null && codeName != null && !product.trim().isEmpty() && !codeName.trim().isEmpty()) {
                    String productUpper = product.trim().toUpperCase();
                    
                    // Check if this is a valid product code
                    if (isValidProductCode(productUpper)) {
                        String appName = Mappings.getAppFromCode(productUpper);
                        
                        // Skip if app name is "Unknown"
                        if (!"Unknown".equals(appName)) {
                            if (!resultMap.containsKey(appName)) {
                                resultMap.put(appName, new ArrayList<>());
                            }
                            resultMap.get(appName).add(codeName.trim());
                        }
                    }
                }
            }
            
            logger.info("Successfully fetched " + resultMap.size() + " app types from database");
            
            // Log the results for debugging
            for (String appName : resultMap.keySet()) {
                List<String> types = resultMap.get(appName);
                logger.info("App: " + appName + " - Types: " + types.size());
                for (String type : types) {
                    logger.fine("  - " + type);
                }
            }
            
        } catch (SQLException e) {
            logger.severe("Database query failed: " + e.getMessage());
            LoggerUtil.error(e);
        } catch (Exception e) {
            logger.severe("Unexpected error while fetching usertypes: " + e.getMessage());
            LoggerUtil.error(e);
        } finally {
            // Close resources
            closeResources(resultSet, statement, connection);
        }
        
        return resultMap;
    }
    
    /**
     * Tests the database connection and validates that the ENV_CODE table exists
     * 
     * @param host Database host
     * @param port Database port
     * @param sid Database SID
     * @param username Database username
     * @param password Database password
     * @return true if connection and table validation is successful, false otherwise
     */
    public static boolean validateDatabaseSetup(String host, int port, String sid, 
                                               String username, String password) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        
        try {
            logger.info("Validating database setup: " + host + ":" + port + "/" + sid);
            
            // Test connection
            connection = DatabaseService.getConnection(host, port, sid, username, password);
            if (connection == null) {
                logger.warning("Database connection failed");
                return false;
            }
            
            // Check if either ENV_CODE or ENV_CODE_1 table exists
            String tableName = determineTableName(connection);
            if (tableName != null) {
                logger.info(tableName + " table validation successful");
                return true;
            } else {
                logger.warning("Neither ENV_CODE nor ENV_CODE_1 table found");
                return false;
            }
            
        } catch (Exception e) {
            logger.warning("Unexpected error during database validation: " + e.getMessage());
            return false;
        } finally {
            closeResources(resultSet, statement, connection);
        }
    }
    
    /**
     * Determines which table exists and returns the table name
     * 
     * @param connection Database connection
     * @return Table name or null if neither table exists
     */
    private static String determineTableName(Connection connection) {
        // First try ENV_CODE table
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM ENV_CODE WHERE ROWNUM = 1")) {
            stmt.executeQuery();
            return "ENV_CODE";
        } catch (SQLException e) {
            // Table doesn't exist, continue to next
        }
        
        // Then try ENV_CODE_1 table
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM ENV_CODE_1 WHERE ROWNUM = 1")) {
            stmt.executeQuery();
            return "ENV_CODE_1";
        } catch (SQLException e) {
            // Table doesn't exist
        }
        
        return null;
    }
    
    /**
     * Determines which table exists and returns the appropriate query
     * 
     * @param connection Database connection
     * @return Query string or null if neither table exists
     */
    private static String determineTableAndQuery(Connection connection) {
        // First try ENV_CODE table
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM ENV_CODE WHERE ROWNUM = 1")) {
            stmt.executeQuery();
            logger.info("Using ENV_CODE table");
            return FETCH_USERTYPES_QUERY_ENV_CODE;
        } catch (SQLException e) {
            logger.info("ENV_CODE table not found, trying ENV_CODE_1");
        }
        
        // Then try ENV_CODE_1 table
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM ENV_CODE_1 WHERE ROWNUM = 1")) {
            stmt.executeQuery();
            logger.info("Using ENV_CODE_1 table");
            return FETCH_USERTYPES_QUERY_ENV_CODE_1;
        } catch (SQLException e) {
            logger.warning("ENV_CODE_1 table also not found");
        }
        
        return null;
    }
    
    /**
     * Validates if the given product code is supported by the application
     * 
     * @param productCode Product code to validate
     * @return true if valid, false otherwise
     */
    private static boolean isValidProductCode(String productCode) {
        String[] validCodes = {"CREW", "SERVICE_ALERT", "WCE", "OMS_CONFIG_TOOL", "WCB", "MODEL", "STORM"};
        for (String code : validCodes) {
            if (code.equals(productCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Safely closes database resources
     * 
     * @param resultSet ResultSet to close
     * @param statement PreparedStatement to close
     * @param connection Connection to close
     */
    private static void closeResources(ResultSet resultSet, PreparedStatement statement, Connection connection) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                logger.warning("Error closing ResultSet: " + e.getMessage());
            }
        }
        
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                logger.warning("Error closing PreparedStatement: " + e.getMessage());
            }
        }
        
        DatabaseService.closeConnection(connection);
    }
}
