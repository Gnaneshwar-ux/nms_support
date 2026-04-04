package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ClineProjectDetailsDialog {

    public enum DialogAction {
        SAVE_AND_OPEN,
        RELOAD_PROJECT_TEMPLATE_AND_OPEN,
        CANCEL
    }

    private static final String PANEL_STYLE =
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #e2e8f0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 12; " +
            "-fx-background-radius: 12; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.10), 12, 0, 0, 4);";

    private static final String EMPTY_FIELD_STYLE =
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #ef4444; " +
            "-fx-border-width: 1.5; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 8 10; " +
            "-fx-min-height: 36; " +
            "-fx-text-fill: #334155;";

    private static final String PRIMARY_BUTTON_STYLE =
            "-fx-background-color: #2563eb; " +
            "-fx-border-color: #2563eb; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;";

    private static final String SECONDARY_BUTTON_STYLE =
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #cbd5e1; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: #475569; " +
            "-fx-padding: 10 20; " +
            "-fx-cursor: hand;";

    private static final String FIELD_STYLE =
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #d1d5db; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6; " +
            "-fx-padding: 8 10; " +
            "-fx-min-height: 36; " +
            "-fx-text-fill: #334155;";

    private static final String DEFAULTS_FILE_NAME = "cline_defaults.properties";

    private final Stage dialogStage = new Stage();
    private CompletableFuture<DialogAction> resultFuture;

    private final Map<String, FieldRow> rows = new LinkedHashMap<>();
    private Properties defaults;

    public ClineProjectDetailsDialog() {
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.setTitle("Cline Project Details");
        dialogStage.setResizable(true);
        dialogStage.setAlwaysOnTop(true);
        IconUtils.setStageIcon(dialogStage);
    }

    public CompletableFuture<DialogAction> showDialog(Window owner, ProjectEntity project) {
        this.resultFuture = new CompletableFuture<>();
        this.defaults = loadDefaults();
        if (owner != null) {
            dialogStage.initOwner(owner);
        }
        dialogStage.setOnCloseRequest(event -> completeIfPending(DialogAction.CANCEL));
        dialogStage.setOnHidden(event -> completeIfPending(DialogAction.CANCEL));

        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.setStyle(PANEL_STYLE);

        Label title = new Label("Provide missing project details for Cline workflow");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        title.setStyle("-fx-text-fill: #1e293b;");

        Label desc = new Label("Review and complete the project details used for Cline workflow generation. Empty values are highlighted in red.");
        desc.setWrapText(true);
        desc.setFont(Font.font("Inter", FontWeight.NORMAL, 12));
        desc.setStyle("-fx-text-fill: #64748b;");

        Label defaultsNote = new Label("Defaults file: Documents/nms_support_data/" + DEFAULTS_FILE_NAME);
        defaultsNote.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        VBox headerBox = new VBox(6, title, desc, defaultsNote);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        addRow(grid, 0, "serverName", "Server Name", project.getServerName(), "");
        addRow(grid, 1, "host", "Linux / NMS Host", project.getHost(), defaultValue("linux.host.default", ""));
        addRow(grid, 2, "hostPort", "Linux / NMS Port", project.getHostPort() > 0 ? String.valueOf(project.getHostPort()) : "", defaultValue("linux.port.default", "22"));
        addRow(grid, 3, "ldapUser", "LDAP User", project.getLdapUser(), defaultValue("ldap.user.default", ""));
        addPasswordRow(grid, 4, "ldapPassword", "LDAP Password", project.getLdapPassword());
        addRow(grid, 5, "targetUser", "General Target User", project.getTargetUser(), defaultValue("target.user.default", "gbuora"));
        addRow(grid, 6, "nmsTargetUser", "NMS Target User", project.getNmsTargetUser(), defaultValue("nms.target.user.default", project.getNmsTargetUser() != null ? project.getNmsTargetUser() : ""));
        addRow(grid, 7, "nmsAppURL", "NMS App URL", project.getNmsAppURL(), defaultValue("nms.app.url.default", ""));
        String derivedWeblogicHost = deriveHostFromUrl(project.getNmsAppURL());
        addRow(grid, 8, "weblogicHost", "Weblogic Host", nonEmpty(project.getWeblogicHost(), derivedWeblogicHost), defaultValue("weblogic.host.default", derivedWeblogicHost));
        addRow(grid, 9, "dbHost", "Database Host", project.getDbHost(), defaultValue("db.host.default", ""));
        addRow(grid, 10, "dbPort", "Database Port", project.getDbPort() > 0 ? String.valueOf(project.getDbPort()) : "", defaultValue("db.port.default", "1521"));
        addRow(grid, 11, "dbUser", "DB Schema User", project.getDbUser(), defaultValue("db.user.default", ""));
        addPasswordRow(grid, 12, "dbPassword", "DB Schema Password", project.getDbPassword());
        addRow(grid, 13, "dbSid", "DB SID", project.getDbSid(), defaultValue("db.sid.default", ""));
        addRow(grid, 14, "biPublisher", "BiPublisher", project.getBiPublisher(), defaultValue("bipublisher.host.default", "ugbu-ash-121.sniadprshared1.gbucdsint02iad.oraclevcn.com"));

        refreshFieldStyles();

        Button forceUpdateAndOpen = new Button("Reload Project Template & Open");
        Button save = new Button("Save & Open");
        Button cancel = new Button("Cancel");
        forceUpdateAndOpen.setStyle(SECONDARY_BUTTON_STYLE);
        save.setDefaultButton(true);
        save.setStyle(PRIMARY_BUTTON_STYLE);
        cancel.setStyle(SECONDARY_BUTTON_STYLE);

        cancel.setOnAction(e -> {
            resultFuture.complete(DialogAction.CANCEL);
            dialogStage.close();
        });
        forceUpdateAndOpen.setOnAction(e -> {
            persistToProject(project);
            resultFuture.complete(DialogAction.RELOAD_PROJECT_TEMPLATE_AND_OPEN);
            dialogStage.close();
        });
        save.setOnAction(e -> {
            persistToProject(project);
            resultFuture.complete(DialogAction.SAVE_AND_OPEN);
            dialogStage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(12, forceUpdateAndOpen, spacer, cancel, save);
        actions.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        grid.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(headerBox, scrollPane, actions);
        dialogStage.setScene(new Scene(root, 560, 610));

        Platform.runLater(() -> rows.values().stream().findFirst().ifPresent(r -> r.field.requestFocus()));
        dialogStage.showAndWait();
        completeIfPending(DialogAction.CANCEL);
        return resultFuture;
    }

    private void completeIfPending(DialogAction action) {
        if (resultFuture != null && !resultFuture.isDone()) {
            resultFuture.complete(action);
        }
    }

    private void addRow(GridPane grid, int rowIndex, String key, String labelText, String value, String defaultValue) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px; -fx-font-weight: 600;");
        TextField field = new TextField(preferredValue(value, defaultValue));
        field.setPrefWidth(310);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle(FIELD_STYLE);
        field.textProperty().addListener((obs, oldValue, newValue) -> applyFieldState(field));
        grid.add(label, 0, rowIndex);
        grid.add(field, 1, rowIndex);
        rows.put(key, new FieldRow(label, field));
    }

    private void addPasswordRow(GridPane grid, int rowIndex, String key, String labelText, String value) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px; -fx-font-weight: 600;");
        PasswordField field = new PasswordField();
        field.setText(value == null ? "" : value);
        field.setPrefWidth(310);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle(FIELD_STYLE);
        field.textProperty().addListener((obs, oldValue, newValue) -> applyFieldState(field));
        grid.add(label, 0, rowIndex);
        grid.add(field, 1, rowIndex);
        rows.put(key, new FieldRow(label, field));
    }

    private void refreshFieldStyles() {
        rows.values().forEach(row -> applyFieldState(row.field));
    }

    private void applyFieldState(TextInputControl field) {
        boolean empty = field.getText() == null || field.getText().trim().isEmpty();
        field.setStyle(empty ? EMPTY_FIELD_STYLE : FIELD_STYLE);
    }

    private void persistToProject(ProjectEntity project) {
        project.setServerName(text("serverName"));
        project.setHost(text("host"));
        project.setHostPort(parseInt(text("hostPort"), 22));
        project.setUseLdap(true);
        project.setLdapUser(text("ldapUser"));
        project.setLdapPassword(text("ldapPassword"));
        project.setTargetUser(text("targetUser"));
        project.setNmsTargetUser(text("nmsTargetUser"));
        project.setNmsAppURL(text("nmsAppURL"));
        project.setWeblogicHost(nonEmpty(text("weblogicHost"), deriveHostFromUrl(text("nmsAppURL"))));
        project.setDbHost(text("dbHost"));
        project.setDbPort(parseInt(text("dbPort"), parseInt(defaultValue("db.port.default", "1521"), 1521)));
        project.setDbUser(text("dbUser"));
        project.setDbPassword(text("dbPassword"));
        project.setDbSid(text("dbSid"));
        project.setBiPublisher(nonEmpty(text("biPublisher"), defaultValue("bipublisher.host.default", "ugbu-ash-121.sniadprshared1.gbucdsint02iad.oraclevcn.com")));
    }

    private String text(String key) {
        FieldRow row = rows.get(key);
        return row == null || row.field.getText() == null ? "" : row.field.getText().trim();
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String deriveHostFromUrl(String url) {
        try {
            if (url == null || url.isBlank()) return "";
            URI uri = URI.create(url.trim());
            return Optional.ofNullable(uri.getHost()).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private String nonEmpty(String first, String fallback) {
        return first != null && !first.isBlank() ? first : (fallback == null ? "" : fallback);
    }

    private String preferredValue(String value, String fallback) {
        return value != null && !value.isBlank() ? value : (fallback == null ? "" : fallback);
    }

    private String defaultValue(String key, String fallback) {
        return defaults == null ? fallback : defaults.getProperty(key, fallback);
    }

    private Properties loadDefaults() {
        Properties properties = new Properties();
        Path dir = Paths.get(System.getProperty("user.home"), "Documents", "nms_support_data");
        Path file = dir.resolve(DEFAULTS_FILE_NAME);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (!Files.exists(file)) {
                String content = String.join("\n",
                        "# Default values for Cline project details dialog",
                        "linux.host.default=",
                        "linux.port.default=22",
                        "ldap.user.default=",
                        "target.user.default=gbuora",
                        "nms.target.user.default=",
                        "nms.app.url.default=",
                        "weblogic.host.default=",
                        "db.host.default=",
                        "db.port.default=1521",
                        "db.user.default=",
                        "db.sid.default=",
                        "bipublisher.host.default=ugbu-ash-121.sniadprshared1.gbucdsint02iad.oraclevcn.com",
                        "");
                Files.writeString(file, content, StandardCharsets.UTF_8);
            }
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (Exception ignored) {
            properties.setProperty("linux.port.default", "22");
            properties.setProperty("db.port.default", "1521");
            properties.setProperty("target.user.default", "gbuora");
            properties.setProperty("bipublisher.host.default", "ugbu-ash-121.sniadprshared1.gbucdsint02iad.oraclevcn.com");
        }
        return properties;
    }

    private static class FieldRow {
        private final Label label;
        private final TextInputControl field;

        private FieldRow(Label label, TextInputControl field) {
            this.label = label;
            this.field = field;
        }
    }
}
