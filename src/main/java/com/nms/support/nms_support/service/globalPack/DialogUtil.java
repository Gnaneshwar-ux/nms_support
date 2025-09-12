package com.nms.support.nms_support.service.globalPack;

import com.nms.support.nms_support.model.ProjectEntity;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DialogUtil {

    // Private constructor to prevent instantiation
    private DialogUtil() {
        // Utility class - no instantiation needed
    }

    // Global inline style for all dialog content
    private static final String GLOBAL_STYLE = "-fx-font-family: 'Arial'; -fx-font-size: 14px;";

    // Professional typography and styling constants
    private static final String PROFESSIONAL_FONT_FAMILY = "'Segoe UI', 'Inter', 'Roboto', 'Arial', sans-serif";
    private static final String MODERN_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 14px;
        -fx-background-color: #FFFFFF;
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String TITLE_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 18px;
        -fx-font-weight: bold;
        -fx-text-fill: #1F2937;
        -fx-text-alignment: center;
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String SUBTITLE_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-font-weight: normal;
        -fx-text-fill: #6B7280;
        -fx-text-alignment: center;
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String LABEL_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-font-weight: 600;
        -fx-text-fill: #374151;
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String FIELD_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-background-color: #FFFFFF;
        -fx-border-color: #D1D5DB;
        -fx-border-radius: 6;
        -fx-padding: 8 12;
        -fx-text-fill: #374151;
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String MODERN_BUTTON_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-font-weight: 600;
        -fx-background-color: #3B82F6;
        -fx-text-fill: white;
        -fx-padding: 8 16;
        -fx-background-radius: 6;
        -fx-border-radius: 6;
        -fx-cursor: hand;
        -fx-effect: dropshadow(gaussian, rgba(59, 130, 246, 0.3), 3, 0, 0, 1);
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String MODERN_BUTTON_HOVER_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-font-weight: 600;
        -fx-background-color: #2563EB;
        -fx-text-fill: white;
        -fx-padding: 8 16;
        -fx-background-radius: 6;
        -fx-border-radius: 6;
        -fx-cursor: hand;
        -fx-effect: dropshadow(gaussian, rgba(37, 99, 235, 0.4), 4, 0, 0, 2);
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String MODERN_CANCEL_BUTTON_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-font-weight: 600;
        -fx-background-color: #6B7280;
        -fx-text-fill: white;
        -fx-padding: 8 16;
        -fx-background-radius: 6;
        -fx-border-radius: 6;
        -fx-cursor: hand;
        -fx-effect: dropshadow(gaussian, rgba(107, 114, 128, 0.3), 3, 0, 0, 1);
        """.formatted(PROFESSIONAL_FONT_FAMILY);
    
    private static final String MODERN_CANCEL_BUTTON_HOVER_STYLE = """
        -fx-font-family: %s;
        -fx-font-size: 13px;
        -fx-font-weight: 600;
        -fx-background-color: #4B5563;
        -fx-text-fill: white;
        -fx-padding: 8 16;
        -fx-background-radius: 6;
        -fx-border-radius: 6;
        -fx-cursor: hand;
        -fx-effect: dropshadow(gaussian, rgba(75, 85, 99, 0.4), 4, 0, 0, 2);
        """.formatted(PROFESSIONAL_FONT_FAMILY);

    // Apply the global inline style to any Node
    private static void applyGlobalStyle(Node node) {
        if (node != null) {
            node.setStyle(GLOBAL_STYLE);
            
            // Apply additional styling for dialog panes
            if (node instanceof DialogPane) {
                DialogPane dialogPane = (DialogPane) node;
                dialogPane.setStyle(GLOBAL_STYLE + 
                    "-fx-background-color: #ffffff; " +
                    "-fx-border-color: #e2e8f0; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 8; " +
                    "-fx-background-radius: 8; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 8, 0, 0, 2);");
            }
        }
    }

    private static void setAlertIcon(Alert alert) {
        IconUtils.setStageIcon((Stage) alert.getDialogPane().getScene().getWindow());
        applyGlobalStyle(alert.getDialogPane());
    }

    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            setAlertIcon(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            // Set dialog properties for proper button containment with content-based sizing
            alert.setResizable(true);
            int width = calculateDialogWidth(message);
            int height = calculateDialogHeight(message);
            alert.getDialogPane().setPrefSize(width, height);
            alert.getDialogPane().setMinSize(Math.min(width, 400), Math.min(height, 140));
            
            alert.showAndWait();
        });
    }

    public static void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.WARNING);
            setAlertIcon(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            // Set dialog properties for proper button containment with content-based sizing
            alert.setResizable(true);
            int width = calculateDialogWidth(message);
            int height = calculateDialogHeight(message);
            alert.getDialogPane().setPrefSize(width, height);
            alert.getDialogPane().setMinSize(Math.min(width, 400), Math.min(height, 140));
            
            alert.showAndWait();
        });
    }

    public static Optional<ButtonType> showConfirmationDialog(String title, String header, String content) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        setAlertIcon(alert);
        applyGlobalStyle(alert.getDialogPane());
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        // Set dialog properties for proper button containment with content-based sizing
        alert.setResizable(true);
        // Combine header and content for size calculation
        String fullMessage = (header != null ? header + "\n" : "") + (content != null ? content : "");
        int width = calculateDialogWidth(fullMessage);
        int height = calculateDialogHeight(fullMessage);
        alert.getDialogPane().setPrefSize(width, height);
        alert.getDialogPane().setMinSize(Math.min(width, 400), Math.min(height, 140));
        
        return alert.showAndWait();
    }

    public static CompletableFuture<Optional<String>> showTextInputDialog(String title, String header, String content, String defaultValue) {
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog(defaultValue);
            IconUtils.setStageIcon((Stage) dialog.getDialogPane().getScene().getWindow());
            applyGlobalStyle(dialog.getDialogPane());
            dialog.setTitle(title);
            dialog.setHeaderText(header);
            dialog.setContentText(content);
            
            // Set dialog properties for proper button containment with content-based sizing
            dialog.setResizable(true);
            // Combine header and content for size calculation
            String fullMessage = (header != null ? header + "\n" : "") + (content != null ? content : "");
            int width = calculateDialogWidth(fullMessage);
            int height = calculateDialogHeight(fullMessage);
            dialog.getDialogPane().setPrefSize(width, height);
            dialog.getDialogPane().setMinSize(Math.min(width, 400), Math.min(height, 140));
            
            future.complete(dialog.showAndWait());
        });
        return future;
    }

    public static void showAlert(javafx.scene.control.Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            setAlertIcon(alert);
            applyGlobalStyle(alert.getDialogPane());
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            
            // Set dialog properties for proper button containment with content-based sizing
            alert.setResizable(true);
            int width = calculateDialogWidth(content);
            int height = calculateDialogHeight(content);
            alert.getDialogPane().setPrefSize(width, height);
            alert.getDialogPane().setMinSize(Math.min(width, 400), Math.min(height, 140));
            
            alert.showAndWait();
        });
    }

    public static List<Map<String, String>> selectProcess(ProjectEntity project, List<Map<String, String>> process) {
        // Use the ProcessSelectionDialog directly to avoid circular dependency
        return ProcessSelectionDialog.selectProcess(project, process, ProcessSelectionDialog.DialogPurpose.GENERAL, null);
    }

    public static CompletableFuture<Optional<String[]>> showTwoInputDialog(
            String title, String message1, String message2,
            String desc1, String desc2, String v1, String v2) {
        CompletableFuture<Optional<String[]>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle(title);
            IconUtils.setStageIcon((javafx.stage.Stage) dialog.getDialogPane().getScene().getWindow());
            applyGlobalStyle(dialog.getDialogPane());
            ButtonType okType = new ButtonType("OK", ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(10); applyGlobalStyle(grid);
            Label lbl1 = new Label(message1); applyGlobalStyle(lbl1);
            TextField fld1 = new TextField(v1); applyGlobalStyle(fld1); Label d1 = new Label(desc1); applyGlobalStyle(d1);
            Label lbl2 = new Label(message2); applyGlobalStyle(lbl2);
            TextField fld2 = new TextField(v2); applyGlobalStyle(fld2); Label d2 = new Label(desc2); applyGlobalStyle(d2);
            grid.add(lbl1, 0, 0); grid.add(fld1, 1, 0); grid.add(d1, 1, 1);
            grid.add(lbl2, 0, 2); grid.add(fld2, 1, 2); grid.add(d2, 1, 3);

            dialog.getDialogPane().setContent(grid);
            
            // Set dialog properties for proper button containment with content-based sizing
            dialog.setResizable(true);
            // For two input dialogs, use a reasonable fixed size since content is more complex
            dialog.getDialogPane().setPrefSize(450, 200);
            dialog.getDialogPane().setMinSize(400, 160);
            
            Platform.runLater(() -> fld1.requestFocus());
            dialog.setResultConverter(btn -> btn == okType ? new String[]{fld1.getText(), fld2.getText()} : null);
            future.complete(dialog.showAndWait());
        });
        return future;
    }

    public static CompletableFuture<Boolean> showProjectSetupDialog(
            String defaultEnv, ProjectEntity project) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Stage dialogStage = new Stage();
            IconUtils.setStageIcon(dialogStage);
            dialogStage.setTitle("Initial Project Setup");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
            ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(30);
            ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50);
            ColumnConstraints c3 = new ColumnConstraints(); c3.setPercentWidth(20);
            grid.getColumnConstraints().addAll(c1, c2, c3); applyGlobalStyle(grid);

            TextField projFld = new TextField(); projFld.setPromptText("Path without /jconfig"); applyGlobalStyle(projFld);

            directoryChooserListener(dialogStage, projFld);

            TextField prodFld = new TextField(); applyGlobalStyle(prodFld);

            directoryChooserListener(dialogStage, prodFld);

            TextField urlFld = new TextField(); applyGlobalStyle(urlFld);
            TextField envFld = new TextField(defaultEnv); applyGlobalStyle(envFld);
            TextField hostFld = new TextField(); applyGlobalStyle(hostFld);
            TextField userFld = new TextField(); applyGlobalStyle(userFld);
            PasswordField passFld = new PasswordField(); applyGlobalStyle(passFld);
            TextField svnFld = new TextField("https://adc4110315.us.oracle.com/svn/nms-projects/trunk/projects");
            svnFld.setDisable(true); applyGlobalStyle(svnFld);
            Button browseBtn = new Button("Find"); applyGlobalStyle(browseBtn);
            browseBtn.setDisable(true);
            browseBtn.setOnAction(e -> {
                browseBtn.setDisable(true); // disable immediately in FX thread
                Window fxWindow = browseBtn.getScene().getWindow();
                new Thread(() -> {
                    LoggerUtil.getLogger().info("Invoked SVN Browser for project dir");

                    try {
                        String picked = new SVNAutomationTool().browseAndSelectFolder( fxWindow, svnFld.getText());

                        if (picked != null) {
                            Platform.runLater(() -> svnFld.setText(picked));
                        }
                    } catch (SVNException ex) {
                        LoggerUtil.error(ex);
                        Platform.runLater(() -> showError("SVN Browse Failed", ex.getMessage()));
                    } finally {
                        Platform.runLater(() -> browseBtn.setDisable(false));
                    }
                }).start();
            });


            int row = 0;
            grid.add(new Label("Project Folder*:"), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(projFld, 1, row++, 2, 1);
            grid.add(new Label("Product Dir*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(prodFld, 1, row++, 2, 1);
            grid.add(new Label("NMS App URL*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(urlFld, 1, row++, 2, 1);
            grid.add(new Label("ENV Var: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(envFld, 1, row++, 2, 1);
            grid.add(new Label("NMS HOST*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(hostFld, 1, row++, 2, 1);
            grid.add(new Label("HOST USER*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(userFld, 1, row++, 2, 1);
            grid.add(new Label("HOST PASS*: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(passFld, 1, row++, 2, 1);
            grid.add(new Label("SVN Repo URL: "), 0, row); applyGlobalStyle(grid.getChildren().get(grid.getChildren().size()-1));
            grid.add(svnFld, 1, row); grid.add(browseBtn, 2, row++);

            CheckBox svnChk = new CheckBox("Enable SVN Checkout"); applyGlobalStyle(svnChk);
            svnChk.selectedProperty().addListener((obs, oldV, newV) -> {
                svnFld.setDisable(!newV); browseBtn.setDisable(!newV);
            });
            grid.add(svnChk, 1, row++, 2, 1);

            Button setupBtn = new Button("Setup"); applyGlobalStyle(setupBtn);
            Button skipBtn = new Button("Skip"); applyGlobalStyle(skipBtn);
            HBox btnBox = new HBox(10, setupBtn, skipBtn);
            btnBox.setAlignment(Pos.CENTER_RIGHT); btnBox.setPadding(new Insets(10,0,0,0)); applyGlobalStyle(btnBox);

            setupBtn.setOnAction(evt -> {
                String pDir = projFld.getText().trim(); String pdDir = prodFld.getText().trim();
                String nmUrl = urlFld.getText().trim(); String env = envFld.getText().trim();
                String hst = hostFld.getText().trim(); String usr = userFld.getText().trim(); String pw = passFld.getText().trim();
                if (pDir.isEmpty()||pdDir.isEmpty()||nmUrl.isEmpty()||hst.isEmpty()||usr.isEmpty()||pw.isEmpty()) {
                    showError("Missing Fields", "Please fill in all mandatory (*) fields."); return; }
                File pd = new File(pDir); if (!pd.exists()||!pd.isDirectory()) { showError("Invalid Directory", "Project Dir does not exist or is not a directory."); return; }
                File prod = new File(pdDir);
                if (!prod.isAbsolute()) {
                    showError("Invalid Directory Path", "Provided path is not an absolute path: " + pdDir);
                    return;
                }
                if (!prod.exists()) { if (!prod.mkdirs()) { showError("Failed to create product dir","System not able to create specified product dir. please create dir manually."); return; }}
                if (svnChk.isSelected() && !svnFld.getText().trim().isEmpty()) project.setSvnRepo(svnFld.getText().trim());
                else {
                    project.setSvnRepo("NULL");
                    if (!searchFile(pd, "build.xml")||!searchFile(pd, "build.properties")) {
                        showError("Build Files Missing","Project Dir must contain 'build.xml' and 'build.properties'. Make sure given project jconfig path."); return; }
                }
                project.setProjectFolderPath(pDir); project.setExePath(pdDir); project.setNmsEnvVar(env); project.setNmsAppURL(nmUrl);
                project.setHost(hst); project.setHostUser(usr); project.setHostPass(pw);
                future.complete(true); dialogStage.close();
            });
            skipBtn.setOnAction(evt -> { project.setSvnRepo("NULL"); future.complete(false); dialogStage.close(); });

            VBox root = new VBox(10, grid, btnBox);
            root.setPadding(new Insets(15)); applyGlobalStyle(root);
            Scene scene = new Scene(root);
            applyGlobalStyle(scene.getRoot());
            dialogStage.setScene(scene);
            Platform.runLater(() -> scene.getRoot().requestFocus());
            dialogStage.showAndWait();
        });
        return future;
    }

    private static boolean searchFile(File root, String fileName) {
        if (root == null || !root.exists()) return false;
        File[] files = root.listFiles(); if (files == null) return false;
        for (File file : files) {
            if (file.getName().equalsIgnoreCase(fileName)) return true;
        }
        return false;
    }

    public static void directoryChooserListener(Stage stage, TextField path){
        path.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                DirectoryChooser directoryChooser = new DirectoryChooser();

                File existingPath = new File(path.getText() != null ? path.getText():"");
                if (existingPath.exists() && existingPath.isDirectory()) {
                    directoryChooser.setInitialDirectory(existingPath);
                }

                File selectedDirectory = directoryChooser.showDialog(stage);
                if (selectedDirectory != null) {
                    path.setText(selectedDirectory.getAbsolutePath());
                }

                event.consume(); // prevent space input
            }
        });
    }
    
    /**
     * Calculate the optimal height for a dialog based on the content length
     * @param message The message content to calculate height for
     * @return The calculated height in pixels
     */
    private static int calculateDialogHeight(String message) {
        if (message == null || message.trim().isEmpty()) {
            return 120; // Reduced for simple messages
        }
        
        int baseHeight = 120; // Reduced base height for better proportions
        int lineCount = 0;
        
        // Split by newlines and calculate line count
        String[] lines = message.split("\n");
        for (String line : lines) {
            // Calculate how many display lines this content line will need
            // Using more conservative estimate for text wrapping
            int displayLines = (int) Math.ceil(line.length() / 50.0);
            lineCount += Math.max(1, displayLines); // At least 1 line per content line
        }
        
        // Add extra height for lines beyond the base (2 lines)
        int extraHeight = 0;
        if (lineCount > 2) {
            extraHeight = (lineCount - 2) * 20; // 20px per additional line
        }
        
        // Ensure minimum height to accommodate buttons properly
        int calculatedHeight = baseHeight + extraHeight;
        return Math.max(calculatedHeight, 120); // Reduced minimum to 120px
    }
    
    /**
     * Calculate the optimal width for a dialog based on the content length
     * @param message The message content to calculate width for
     * @return The calculated width in pixels
     */
    private static int calculateDialogWidth(String message) {
        if (message == null || message.trim().isEmpty()) {
            return 350; // Further reduced default width for simple messages
        }
        
        int maxLineLength = 0;
        String[] lines = message.split("\n");
        
        for (String line : lines) {
            maxLineLength = Math.max(maxLineLength, line.length());
        }
        
        // Calculate width based on character count with more conservative estimate
        // Using ~7 pixels per character for tighter fit
        int calculatedWidth = maxLineLength * 7 + 100; // Reduced padding
        
        // Ensure reasonable bounds with tighter range for better proportions
        return Math.max(350, Math.min(calculatedWidth, 500)); // Between 350 and 500 pixels
    }
}
