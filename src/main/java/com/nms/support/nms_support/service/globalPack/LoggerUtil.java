package com.nms.support.nms_support.service.globalPack;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggerUtil {
    private static final Logger logger = Logger.getLogger(LoggerUtil.class.getName());
    private static boolean isInitialized = false;

    static {
        initializeLogger();
    }

    private static void initializeLogger() {
        if (!isInitialized) {
            try {
                // Create a ConsoleHandler
                ConsoleHandler consoleHandler = new ConsoleHandler();
                consoleHandler.setLevel(Level.ALL);

                // Create a FileHandler
                String user = System.getProperty("user.name");
                String logPath = "C:\\Users\\" + user +"\\Documents\\nms_support_data\\nms_support.log";
                FileHandler fileHandler = new FileHandler(logPath, false); // 'true' to append to the existing file
                fileHandler.setLevel(Level.ALL);
                fileHandler.setFormatter(new SimpleFormatter());

                // Add the handlers to the logger
                logger.addHandler(consoleHandler);
                logger.addHandler(fileHandler);

                // Set the logger level
                logger.setLevel(Level.ALL);

                // Prevent the logger from outputting to the default system handlers
                logger.setUseParentHandlers(false);

                isInitialized = true;

            } catch (IOException e) {
                logger.severe("Failed to set up logger handlers: " + e.getMessage());
            }
        }
    }

    public static Logger getLogger() {
        return logger;
    }

    public static void error(Exception e) {

        // Convert stack trace to string
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();

        // Log it
        logger.severe("Exception occurred: " + exceptionAsString);
    }
}
