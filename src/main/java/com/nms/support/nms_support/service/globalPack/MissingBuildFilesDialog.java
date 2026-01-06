package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog for handling missing build files during setup
 */
public class MissingBuildFilesDialog {
    
    public enum BuildFilesAction {
        DOWNLOAD_AND_CONTINUE,
        SKIP_AND_CONTINUE,
        CANCEL_SETUP
    }
    
    private BuildFilesAction selectedAction = BuildFilesAction.CANCEL_SETUP;
    private com.nms.support.nms_support.model.ProjectEntity project;
    
    public BuildFilesAction showDialog(Stage parentStage) {
        return showDialog(parentStage, null);
    }
    
    public BuildFilesAction showDialog(Stage parentStage, com.nms.support.nms_support.model.ProjectEntity project) {
        this.project = project;
        // Create the dialog stage
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        if (parentStage != null) {
            dialogStage.initOwner(parentStage);
        }
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.setResizable(true);
        dialogStage.setMinWidth(520);
        dialogStage.setMinHeight(420);
        dialogStage.setTitle("Missing Build Files");
        
        // Create the main content
        VBox mainContent = createMainContent(dialogStage);
        
        // Create scene
        Scene scene = new Scene(mainContent);
        dialogStage.setScene(scene);
        dialogStage.sizeToScene();
        
        // Set icon
        IconUtils.setStageIcon(dialogStage);
        
        // Handle close button (X) click - default to Skip & Continue
        dialogStage.setOnCloseRequest(event -> {
            selectedAction = BuildFilesAction.SKIP_AND_CONTINUE;
            dialogStage.close();
        });

        // Keyboard shortcuts: Enter = Confirm, Esc = Cancel
        scene.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER:
                    dialogStage.close();
                    break;
                case ESCAPE:
                    // ESC defaults to skip & continue
                    selectedAction = BuildFilesAction.SKIP_AND_CONTINUE;
                    dialogStage.close();
                    break;
                default:
                    break;
            }
        });
        
        // Center on parent
        dialogStage.centerOnScreen();
        
        // Show and wait
        dialogStage.showAndWait();
        
        return selectedAction;
    }
    
    private VBox createMainContent(Stage dialogStage) {
        // Main container
        VBox mainContainer = new VBox(0);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 16;");
        mainContainer.setEffect(new DropShadow(20, Color.rgb(0, 0, 0, 0.15)));
        // Allow dynamic sizing; min size handled by Stage
        
        // Header
        VBox header = createHeader();
        
        // Content
        VBox content = createContent();

        // Wrap content in a ScrollPane so footer stays visible and window is resizable
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        // Footer (fixed at bottom)
        HBox footer = createFooter(dialogStage);

        // Layout: header (top), scrollable content (center), footer (bottom)
        mainContainer.getChildren().addAll(header, scrollPane, footer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        return mainContainer;
    }
    
    private VBox createHeader() {
        // Compact header (no banner)
        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 8, 16));

        Label titleLabel = new Label("Missing Build Files");
        titleLabel.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        titleLabel.setStyle("-fx-text-fill: #111827;");

        header.getChildren().add(titleLabel);
        return header;
    }
    
    private VBox createContent() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(12, 16, 12, 16));

        // Server details (host and user)
        if (project != null) {
            VBox detailsCard = new VBox(6);
            detailsCard.setAlignment(Pos.CENTER_LEFT);
            detailsCard.setPadding(new Insets(12));
            detailsCard.setStyle("-fx-background-color: #f3f4f6; -fx-background-radius: 8; -fx-border-color: #e5e7eb; -fx-border-radius: 8;");
            
            Label detailsTitle = new Label("Server Details");
            detailsTitle.setFont(Font.font("Inter", FontWeight.BOLD, 13));
            detailsTitle.setStyle("-fx-text-fill: #111827;");
            
            String hostVal = project.getHost() != null ? project.getHost() : "(not set)";
            if (project.getHostPort() > 0) {
                hostVal = hostVal + ":" + project.getHostPort();
            }
            String userVal = project.isUseLdap() ? 
                (project.getLdapUser() != null ? project.getLdapUser() : "(not set)") :
                (project.getHostUser() != null ? project.getHostUser() : "(not set)");
            
            Label hostLabel = new Label("Host: " + hostVal);
            hostLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 12;");
            Label userLabel = new Label("User: " + userVal);
            userLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 12;");
            
            detailsCard.getChildren().addAll(detailsTitle, hostLabel, userLabel);
            content.getChildren().add(detailsCard);
        }
        
        // Main message
        Label messageLabel = new Label(
            "Required build files (build.xml, build.properties) are missing from the project jconfig folder."
        );
        messageLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 13));
        messageLabel.setStyle("-fx-text-fill: #374151;");
        messageLabel.setWrapText(true);
        messageLabel.setTextAlignment(TextAlignment.LEFT);
        messageLabel.setMaxWidth(480);
        
        // Options
        VBox optionsBox = new VBox(8);
        optionsBox.setAlignment(Pos.CENTER_LEFT);
        optionsBox.setPadding(new Insets(16, 0, 0, 0));
        
        ToggleGroup group = new ToggleGroup();
        
        // Option 1: Download and Continue
        HBox option1 = createOption(
            "📥 Download from Server & Continue",
            "Download build files from server and continue with setup",
            BuildFilesAction.DOWNLOAD_AND_CONTINUE,
            group
        );
        
        // Option 2: Skip and Continue
        HBox option2 = createOption(
            "⏭️ Skip & Continue",
            "Skip build files download and continue with setup",
            BuildFilesAction.SKIP_AND_CONTINUE,
            group
        );
        
        // Option 3: Cancel
        HBox option3 = createOption(
            "❌ Cancel Setup",
            "Cancel the current setup process",
            BuildFilesAction.CANCEL_SETUP,
            group
        );
        
        optionsBox.getChildren().addAll(option1, option2, option3);
        
        // Default selection and initial highlight
        RadioButton defaultRb = (RadioButton) option1.getChildren().get(0);
        group.selectToggle(defaultRb);
        selectedAction = BuildFilesAction.DOWNLOAD_AND_CONTINUE;
        updateOptionStyles(optionsBox);
        
        content.getChildren().addAll(messageLabel, optionsBox);
        
        return content;
    }
    
    private HBox createOption(String title, String description, BuildFilesAction action, ToggleGroup group) {
        HBox optionBox = new HBox(12);
        optionBox.setAlignment(Pos.CENTER_LEFT);
        optionBox.setPadding(new Insets(8, 12, 8, 12));
        optionBox.setStyle(
            "-fx-background-color: #f9fafb; " +
            "-fx-border-color: #e5e7eb; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-cursor: hand;"
        );
        
        // Radio button
        RadioButton radioButton = new RadioButton();
        radioButton.setToggleGroup(group);
        
        // Update action and styles when selected
        radioButton.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                selectedAction = action;
                updateOptionStyles(optionBox.getParent());
            }
        });
        
        // Content
        VBox contentBox = new VBox(2);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: #1f2937;");
        
        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 12));
        descLabel.setStyle("-fx-text-fill: #6b7280;");
        descLabel.setWrapText(true);
        
        contentBox.getChildren().addAll(titleLabel, descLabel);
        
        // Click anywhere on the row to select
        optionBox.setOnMouseClicked(e -> radioButton.setSelected(true));
        
        optionBox.getChildren().addAll(radioButton, contentBox);
        
        return optionBox;
    }
    
    private void updateOptionStyles(Parent parent) {
        if (parent instanceof VBox) {
            VBox vbox = (VBox) parent;
            for (javafx.scene.Node node : vbox.getChildren()) {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    RadioButton rb = null;
                    if (!hbox.getChildren().isEmpty() && hbox.getChildren().get(0) instanceof RadioButton) {
                        rb = (RadioButton) hbox.getChildren().get(0);
                    }
                    boolean selected = rb != null && rb.isSelected();
                    String base = "-fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;";
                    if (selected) {
                        hbox.setStyle("-fx-background-color: #e0f2fe; " + base);
                    } else {
                        hbox.setStyle("-fx-background-color: #f9fafb; " + base);
                    }
                }
            }
        }
    }
    
    private HBox createFooter(Stage dialogStage) {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 24, 24, 24));
        footer.setStyle("-fx-background-color: #f9fafb; -fx-background-radius: 0 0 16 16;");

        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        cancelButton.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #d1d5db; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: #374151; " +
            "-fx-padding: 8 20; " +
            "-fx-cursor: hand;"
        );
        cancelButton.setOnAction(e -> {
            // Cancel button defaults to Skip & Continue unless user explicitly chose "Cancel Setup"
            if (selectedAction == BuildFilesAction.CANCEL_SETUP) {
                // Respect explicit "Cancel Setup" choice if selected
                dialogStage.close();
                return;
            }
            selectedAction = BuildFilesAction.SKIP_AND_CONTINUE;
            dialogStage.close();
        });

        Button confirmButton = new Button("Continue");
        confirmButton.setDefaultButton(true);
        confirmButton.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        confirmButton.setStyle(
            "-fx-background-color: #1565c0; " +
            "-fx-border-color: #1565c0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 8; " +
            "-fx-background-radius: 8; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 8 20; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.3), 4, 0, 0, 2);"
        );
        confirmButton.setOnAction(e -> {
            // selectedAction already reflects the selected option
            dialogStage.close();
        });

        footer.getChildren().addAll(cancelButton, confirmButton);
        return footer;
    }
}
