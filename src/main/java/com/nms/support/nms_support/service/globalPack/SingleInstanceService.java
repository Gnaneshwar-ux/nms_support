package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class SingleInstanceService {

    private static final Logger logger = LoggerUtil.getLogger();
    private static final int PORT = 44567;
    private static final String FOCUS_MESSAGE = "FOCUS";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private static FileChannel lockChannel;
    private static FileLock fileLock;
    private static ServerSocket serverSocket;
    private static volatile Stage primaryStage;

    private SingleInstanceService() {
    }

    public static boolean initializeAsPrimaryInstance() {
        if (initialized.get()) {
            return true;
        }

        try {
            Path dataDir = Paths.get(System.getProperty("user.home"), "Documents", "nms_support_data");
            Files.createDirectories(dataDir);
            Path lockFile = dataDir.resolve("nms_support.lock");

            lockChannel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            fileLock = lockChannel.tryLock();

            if (fileLock == null) {
                logger.warning("Another NMS Support instance is already holding the application lock.");
                closeQuietly();
                return false;
            }

            serverSocket = new ServerSocket(PORT, 50, InetAddress.getLoopbackAddress());
            startFocusListener();
            initialized.set(true);
            logger.info("Single-instance guard initialized successfully.");
            return true;
        } catch (IOException e) {
            logger.warning("Failed to initialize single-instance guard: " + e.getMessage());
            closeQuietly();
            return true;
        }
    }

    public static boolean notifyExistingInstance() {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), PORT);
             Writer writer = new OutputStreamWriter(socket.getOutputStream())) {
            writer.write(FOCUS_MESSAGE + System.lineSeparator());
            writer.flush();
            return true;
        } catch (IOException e) {
            logger.info("Could not signal existing instance: " + e.getMessage());
            return false;
        }
    }

    public static void registerPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static void shutdown() {
        closeQuietly();
        initialized.set(false);
    }

    private static void startFocusListener() {
        Thread listener = new Thread(() -> {
            while (serverSocket != null && !serverSocket.isClosed()) {
                try (Socket client = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                    String message = reader.readLine();
                    if (FOCUS_MESSAGE.equalsIgnoreCase(message)) {
                        focusPrimaryStage();
                    }
                } catch (IOException e) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        logger.warning("Single-instance listener error: " + e.getMessage());
                    }
                }
            }
        }, "nms-support-single-instance-listener");
        listener.setDaemon(true);
        listener.start();
    }

    private static void focusPrimaryStage() {
        Platform.runLater(() -> {
            Stage stage = primaryStage;
            if (stage == null) {
                return;
            }

            try {
                if (stage.isIconified()) {
                    stage.setIconified(false);
                }
                if (!stage.isShowing()) {
                    stage.show();
                }
                stage.toFront();
                stage.requestFocus();
            } catch (Exception e) {
                logger.warning("Failed to bring primary stage to front: " + e.getMessage());
            }
        });
    }

    private static void closeQuietly() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }

        if (fileLock != null) {
            try {
                fileLock.release();
            } catch (IOException ignored) {
            }
            fileLock = null;
        }

        if (lockChannel != null) {
            try {
                lockChannel.close();
            } catch (IOException ignored) {
            }
            lockChannel = null;
        }
    }
}