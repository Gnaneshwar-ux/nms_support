package com.nms.support.nms_support.service.globalPack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.attribute.DosFileAttributeView;

public class NmsMcpSetupDialog {

    private static final java.util.logging.Logger logger = LoggerUtil.getLogger();
    private static final String REPO_URL = "https://github.com/Gnaneshwar-ux/NMS_MCP.git";
    private static final String SERVER_NAME = "nms-mcp";

    private final Stage dialogStage = new Stage();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Ready to validate and setup NMS_MCP.");
    private final TextArea logArea = new TextArea();
    private final Button startButton = new Button("Start Clean Setup");
    private final Button copyButton = new Button("Copy Codex Prompt");
    private final Button closeButton = new Button("Close");
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String detectedGitCommand = "git";
    private String detectedNodeCommand = "node";
    private String detectedNpmCommand = "npm.cmd";
    private String detectedCodexCommand = "codex.cmd";

    public NmsMcpSetupDialog() {
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("NMS MCP Setup");
        dialogStage.setResizable(true);
        dialogStage.setMinWidth(680);
        dialogStage.setMinHeight(520);
        IconUtils.setStageIcon(dialogStage);
    }

    public void showDialog(Window owner) {
        if (owner != null) {
            dialogStage.initOwner(owner);
        }

        VBox root = new VBox(16);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f8fafc;");

        Label title = new Label("NMS_MCP Setup");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#0f172a"));

        Label subtitle = new Label(
                "Clone or refresh the MCP repository, validate prerequisites, build it, register it for Codex, update Cline MCP settings, and copy the final Codex self-setup prompt.");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(640);
        subtitle.setTextFill(Color.web("#475569"));

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(6);
        infoGrid.setPadding(new Insets(8));
        infoGrid.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-background-radius: 12;");
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(110);
        labelCol.setPrefWidth(130);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        valueCol.setFillWidth(true);
        infoGrid.getColumnConstraints().setAll(labelCol, valueCol);
        addInfoRow(infoGrid, 0, "Source Repo", REPO_URL);
        addInfoRow(infoGrid, 1, "Documents Clone Path", getDocumentsRepoDir().toString());
        addInfoRow(infoGrid, 2, "Detected Cline Config", getClineConfigPath() != null ? getClineConfigPath().toString() : "Not found yet - will create when possible");
        addInfoRow(infoGrid, 3, "Codex MCP Name", SERVER_NAME);

        Label progressTitle = new Label("Setup Progress");
        progressTitle.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 14));
        progressTitle.setTextFill(Color.web("#0f172a"));
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #2563eb;");
        statusLabel.setTextFill(Color.web("#334155"));

        VBox progressBox = new VBox(6, progressTitle, progressBar, statusLabel);
        progressBox.setPadding(new Insets(8));
        progressBox.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-background-radius: 12;");

        Label logsTitle = new Label("Execution Log");
        logsTitle.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 14));
        logsTitle.setTextFill(Color.web("#0f172a"));
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(11);
        logArea.setStyle("-fx-font-family: 'Cascadia Code', 'Consolas', monospace; -fx-font-size: 12px;");

        VBox logBox = new VBox(6, logsTitle, logArea);
        logBox.setPadding(new Insets(8));
        logBox.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-background-radius: 12;");
        VBox.setVgrow(logBox, Priority.ALWAYS);

        startButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 10; -fx-padding: 8 14;");
        copyButton.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 10; -fx-padding: 8 14;");
        closeButton.setStyle("-fx-background-color: white; -fx-text-fill: #334155; -fx-border-color: #cbd5e1; -fx-font-weight: 700; -fx-background-radius: 10; -fx-border-radius: 10; -fx-padding: 8 14;");
        copyButton.setDisable(true);

        initializeExistingState();

        startButton.setOnAction(e -> startSetup());
        copyButton.setOnAction(e -> copyPrompt());
        closeButton.setOnAction(e -> dialogStage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, startButton, copyButton, spacer, closeButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, title, subtitle, infoGrid, progressBox, logBox, buttons);
        content.setFillWidth(true);
        content.setPadding(new Insets(2));
        content.setStyle("-fx-background-color: #f8fafc;");
        VBox.setVgrow(logBox, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: #f8fafc; -fx-background: #f8fafc; -fx-border-color: transparent;");

        Platform.runLater(() -> {
            if (scrollPane.getViewportBounds().getWidth() > 0) {
                content.setPrefWidth(scrollPane.getViewportBounds().getWidth() - 8);
            }
        });

        root.getChildren().add(scrollPane);

        dialogStage.setScene(new Scene(root, 720, 560));
        dialogStage.show();
    }

    private void addInfoRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label + ":");
        l.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 12));
        l.setTextFill(Color.web("#334155"));
        Label v = new Label(value);
        v.setWrapText(true);
        v.setMaxWidth(Double.MAX_VALUE);
        v.setMinHeight(Region.USE_PREF_SIZE);
        v.setTextFill(Color.web("#0f172a"));
        GridPane.setHgrow(v, Priority.ALWAYS);
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private void startSetup() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        startButton.setDisable(true);
        copyButton.setDisable(true);
        logArea.clear();

        Thread.ofVirtual().start(() -> {
            try {
                runStep(0.08, "Validating prerequisites", this::validatePrerequisites);
                runStep(0.18, "Preparing repository directory", this::prepareRepoDir);
                runStep(0.34, "Cloning NMS_MCP repository", this::cloneRepo);
                runStep(0.52, "Installing npm dependencies", this::npmInstall);
                runStep(0.68, "Building MCP project", this::npmBuild);
                runStep(0.84, "Updating Cline MCP settings", this::updateClineConfig);
                runStep(0.96, "Refreshing Codex MCP registration", this::updateCodexRegistration);
                updateUi(1.0, "Setup completed successfully.");
                appendLog("Done. NMS_MCP setup completed successfully.");
                Platform.runLater(() -> copyButton.setDisable(false));
            } catch (Exception ex) {
                appendLog("ERROR: " + ex.getMessage());
                updateUi(progressBar.getProgress(), "Setup failed: " + ex.getMessage());
            } finally {
                running.set(false);
                Platform.runLater(() -> startButton.setDisable(false));
            }
        });
    }

    private void runStep(double progress, String message, ThrowingRunnable runnable) throws Exception {
        updateUi(progress, message);
        appendLog("== " + message + " ==");
        runnable.run();
    }

    private void validatePrerequisites() throws Exception {
        detectedGitCommand = validateCommandExists("git");
        detectedNodeCommand = validateCommandExists("node.exe", "node");
        detectedNpmCommand = validateCommandExists("npm.cmd", "npm");
        detectedCodexCommand = validateCommandExists("codex.cmd", "codex");

        String nodeVersion = runCommand(List.of(detectedNodeCommand, "--version"), null, false);
        appendLog("Node version: " + nodeVersion.trim());
        int majorVersion = parseMajorNodeVersion(nodeVersion.trim());
        if (majorVersion < 18) {
            throw new IllegalStateException("Node.js 18+ is required for NMS_MCP. Detected: " + nodeVersion.trim());
        }
    }

    private int parseMajorNodeVersion(String version) {
        try {
            String normalized = version == null ? "" : version.trim();
            if (normalized.startsWith("v") || normalized.startsWith("V")) {
                normalized = normalized.substring(1);
            }
            int dot = normalized.indexOf('.');
            String major = dot >= 0 ? normalized.substring(0, dot) : normalized;
            return Integer.parseInt(major);
        } catch (Exception e) {
            logger.warning("Unable to parse Node.js version: " + version);
            return -1;
        }
    }

    private void prepareRepoDir() throws Exception {
        Path repoDir = getDocumentsRepoDir();
        if (Files.exists(repoDir)) {
            appendLog("Repository directory already exists. Cleaning it for a fresh setup: " + repoDir);
            forceDeleteRecursively(repoDir);
        }
        Files.createDirectories(repoDir.getParent());
    }

    private void initializeExistingState() {
        boolean repoExists = Files.exists(getDocumentsRepoDir());
        boolean clineConfigured = isClineConfigured();
        boolean codexConfigured = isCodexConfigured();

        if (repoExists) {
            appendLog("Existing NMS_MCP repository detected at: " + getDocumentsRepoDir());
        }
        if (clineConfigured) {
            appendLog("Existing Cline MCP entry found for: " + SERVER_NAME);
        }
        if (codexConfigured) {
            appendLog("Existing Codex MCP registration found for: " + SERVER_NAME);
        }

        if (repoExists || clineConfigured || codexConfigured) {
            copyButton.setDisable(false);
            statusLabel.setText("Existing MCP setup detected. You can copy the Codex prompt or run a clean setup again.");
        }
    }

    private boolean isClineConfigured() {
        try {
            Path configPath = getClineConfigPath();
            if (!Files.exists(configPath)) {
                return false;
            }
            ObjectMapper mapper = new ObjectMapper();
            LinkedHashMap<String, Object> root = mapper.readValue(Files.readAllBytes(configPath), new TypeReference<LinkedHashMap<String, Object>>() {});
            Object servers = root.get("mcpServers");
            return servers instanceof Map && ((Map<?, ?>) servers).containsKey(SERVER_NAME);
        } catch (Exception e) {
            logger.warning("Unable to inspect existing Cline MCP config: " + e.getMessage());
            return false;
        }
    }

    private boolean isCodexConfigured() {
        try {
            String output = runCommandSilently(List.of("codex.cmd", "mcp", "list"), null);
            return output != null && output.toLowerCase(Locale.ROOT).contains(SERVER_NAME.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            logger.warning("Unable to inspect existing Codex MCP registration: " + e.getMessage());
            return false;
        }
    }

    private void cloneRepo() throws Exception {
        runCommand(List.of(detectedGitCommand, "clone", REPO_URL, getDocumentsRepoDir().toString()), null, true);
    }

    private void npmInstall() throws Exception {
        runCommand(List.of(detectedNpmCommand, "install"), getDocumentsRepoDir(), true);
    }

    private void npmBuild() throws Exception {
        runCommand(List.of(detectedNpmCommand, "run", "build"), getDocumentsRepoDir(), true);
    }

    private void updateClineConfig() throws Exception {
        Path configPath = getClineConfigPath();
        if (configPath == null) {
            throw new IllegalStateException("Unable to resolve a Cline MCP settings path on this Windows system.");
        }
        if (!Files.exists(configPath.getParent())) {
            Files.createDirectories(configPath.getParent());
        }

        ObjectMapper mapper = new ObjectMapper();
        LinkedHashMap<String, Object> root;
        if (Files.exists(configPath)) {
            root = mapper.readValue(Files.readAllBytes(configPath), new TypeReference<LinkedHashMap<String, Object>>() {});
        } else {
            root = new LinkedHashMap<>();
        }

        LinkedHashMap<String, Object> servers = root.get("mcpServers") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) root.get("mcpServers"))
                : new LinkedHashMap<>();

        LinkedHashMap<String, Object> server = new LinkedHashMap<>();
        server.put("disabled", false);
        server.put("timeout", 60);
        server.put("type", "stdio");
        server.put("command", "node");
        server.put("args", List.of(getDocumentsRepoDir().resolve("dist").resolve("index.js").toString()));

        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        env.put("MCP_SSH_DEFAULT_TIMEOUT_MS", "120000");
        env.put("MCP_SSH_IDLE_TIMEOUT_MS", "3600000");
        env.put("MCP_SSH_APPROVAL_TTL_MS", "600000");
        env.put("MCP_SSH_POLICY_FILE", getDocumentsRepoDir().resolve("ssh-mcp-policy.json").toString());
        env.put("MCP_SSH_MAX_SESSIONS", "10");
        env.put("MCP_DB_DEFAULT_TIMEOUT_MS", "60000");
        env.put("MCP_DB_IDLE_TIMEOUT_MS", "3600000");
        env.put("MCP_DB_MAX_SESSIONS", "5");
        env.put("MCP_DB_MAX_ROWS", "200");
        env.put("MCP_AUDIT_LOG_FILE", getDocumentsRepoDir().resolve("mcp-audit.ndjson").toString());
        server.put("env", env);

        servers.put(SERVER_NAME, server);
        root.put("mcpServers", servers);
        Files.writeString(configPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        appendLog("Updated Cline MCP config: " + configPath);
    }

    private void updateCodexRegistration() throws Exception {
        runCommand(List.of(detectedCodexCommand, "mcp", "remove", SERVER_NAME), null, false);
        List<String> cmd = new ArrayList<>();
        cmd.add(detectedCodexCommand);
        cmd.add("mcp");
        cmd.add("add");
        cmd.add(SERVER_NAME);
        cmd.add("--env");
        cmd.add("MCP_SSH_DEFAULT_TIMEOUT_MS=120000");
        cmd.add("--env");
        cmd.add("MCP_SSH_IDLE_TIMEOUT_MS=3600000");
        cmd.add("--env");
        cmd.add("MCP_SSH_APPROVAL_TTL_MS=600000");
        cmd.add("--env");
        cmd.add("MCP_SSH_POLICY_FILE=" + getDocumentsRepoDir().resolve("ssh-mcp-policy.json"));
        cmd.add("--env");
        cmd.add("MCP_SSH_MAX_SESSIONS=10");
        cmd.add("--env");
        cmd.add("MCP_DB_DEFAULT_TIMEOUT_MS=60000");
        cmd.add("--env");
        cmd.add("MCP_DB_IDLE_TIMEOUT_MS=3600000");
        cmd.add("--env");
        cmd.add("MCP_DB_MAX_SESSIONS=5");
        cmd.add("--env");
        cmd.add("MCP_DB_MAX_ROWS=200");
        cmd.add("--env");
        cmd.add("MCP_AUDIT_LOG_FILE=" + getDocumentsRepoDir().resolve("mcp-audit.ndjson"));
        cmd.add("--");
        cmd.add(detectedNodeCommand);
        cmd.add(getDocumentsRepoDir().resolve("dist").resolve("index.js").toString());
        runCommand(cmd, null, true);
        appendLog(runCommand(List.of(detectedCodexCommand, "mcp", "list"), null, false));
    }

    private String validateCommandExists(String... candidates) throws Exception {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String result = runCommand(List.of("cmd.exe", "/c", "where", candidate), null, false);
            if (result != null && !result.trim().isEmpty()) {
                String resolved = firstNonBlankLine(result);
                appendLog("Detected " + candidate + ": " + resolved);
                return resolved;
            }
        }
        throw new IllegalStateException("Missing prerequisite: " + String.join(" / ", candidates));
    }

    private String firstNonBlankLine(String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return text.trim();
    }

    private String validateCommandExistsLegacy(String name) throws Exception {
        String result = runCommand(List.of("cmd.exe", "/c", "where", name), null, false);
        if (result == null || result.trim().isEmpty()) {
            throw new IllegalStateException("Missing prerequisite: " + name);
        }
        appendLog("Detected " + name + ": " + result.trim());
        return result.trim();
    }

    private String runCommand(List<String> command, Path workingDir, boolean failOnError) throws Exception {
        appendLog("$ " + String.join(" ", command));
        return executeCommand(command, workingDir, failOnError, true);
    }

    private String runCommandSilently(List<String> command, Path workingDir) throws Exception {
        return executeCommand(command, workingDir, false, false);
    }

    private String executeCommand(List<String> command, Path workingDir, boolean failOnError, boolean logOutput) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
                if (logOutput) {
                    appendLog(line);
                }
            }
        }
        int exit = process.waitFor();
        if (failOnError && exit != 0) {
            throw new IllegalStateException("Command failed (exit " + exit + "): " + String.join(" ", command));
        }
        return output.toString();
    }

    private void appendLog(String message) {
        Platform.runLater(() -> logArea.appendText(message + System.lineSeparator()));
    }

    private void updateUi(double progress, String message) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            statusLabel.setText(message);
        });
    }

    private void copyPrompt() {
        String prompt = buildCodexPrompt();
        ClipboardContent content = new ClipboardContent();
        content.putString(prompt);
        Clipboard.getSystemClipboard().setContent(content);
        appendLog("Copied Codex setup prompt to clipboard.");
        statusLabel.setText("Codex setup prompt copied to clipboard.");
    }

    private String buildCodexPrompt() {
        Path repoDir = getDocumentsRepoDir();
        return "Set up the NMS MCP server in Codex using these exact values:\n\n"
                + "Repo path: " + repoDir + "\n"
                + "Entry point: " + repoDir.resolve("dist").resolve("index.js") + "\n"
                + "Policy file: " + repoDir.resolve("ssh-mcp-policy.json") + "\n"
                + "Audit log: " + repoDir.resolve("mcp-audit.ndjson") + "\n\n"
                + "Recommended command:\n"
                + detectedCodexCommand + " mcp add nms-mcp --env MCP_SSH_DEFAULT_TIMEOUT_MS=120000 --env MCP_SSH_IDLE_TIMEOUT_MS=3600000 --env MCP_SSH_APPROVAL_TTL_MS=600000 --env MCP_SSH_POLICY_FILE="
                + repoDir.resolve("ssh-mcp-policy.json")
                + " --env MCP_SSH_MAX_SESSIONS=10 --env MCP_DB_DEFAULT_TIMEOUT_MS=60000 --env MCP_DB_IDLE_TIMEOUT_MS=3600000 --env MCP_DB_MAX_SESSIONS=5 --env MCP_DB_MAX_ROWS=200 --env MCP_AUDIT_LOG_FILE="
                + repoDir.resolve("mcp-audit.ndjson")
                + " -- " + detectedNodeCommand + " "
                + repoDir.resolve("dist").resolve("index.js")
                + "\n\nIf nms-mcp already exists, remove and add it again.";
    }

    private Path getDocumentsRepoDir() {
        return Paths.get(System.getProperty("user.home"), "Documents", "NMS_MCP");
    }

    private Path getClineConfigPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = Paths.get(System.getProperty("user.home"), "AppData", "Roaming").toString();
        }

        List<Path> candidates = List.of(
                Paths.get(appData, "Code", "User", "globalStorage", "saoudrizwan.claude-dev", "settings", "cline_mcp_settings.json"),
                Paths.get(appData, "Cursor", "User", "globalStorage", "saoudrizwan.claude-dev", "settings", "cline_mcp_settings.json"),
                Paths.get(appData, "VSCodium", "User", "globalStorage", "saoudrizwan.claude-dev", "settings", "cline_mcp_settings.json")
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private void forceDeleteRecursively(Path path) throws Exception {
        try {
            clearReadOnlyAttributes(path);
            deleteRecursively(path);
            return;
        } catch (Exception firstError) {
            appendLog("Standard delete failed, trying Windows force cleanup...");
            runCommand(List.of("cmd.exe", "/c", "attrib", "-r", "/s", "/d", path.toString() + "\\*"), null, false);
            runCommand(List.of("cmd.exe", "/c", "rd", "/s", "/q", path.toString()), null, false);
            if (Files.exists(path)) {
                throw firstError;
            }
        }
    }

    private void clearReadOnlyAttributes(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                DosFileAttributeView view = Files.getFileAttributeView(file, DosFileAttributeView.class);
                if (view != null) {
                    view.setReadOnly(false);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                DosFileAttributeView view = Files.getFileAttributeView(dir, DosFileAttributeView.class);
                if (view != null) {
                    view.setReadOnly(false);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}