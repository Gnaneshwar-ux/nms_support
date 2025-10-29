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
    
    public BuildFilesAction showDialog(Stage parentStage) {
        // Create the dialog stage
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(parentStage);
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.setResizable(false);
        dialogStage.setTitle("Missing Build Files");
        
        // Create the main content
        VBox mainContent = createMainContent(dialogStage);
        
        // Create scene
        Scene scene = new Scene(mainContent);
        dialogStage.setScene(scene);
        
        // Set icon
        IconUtils.setStageIcon(dialogStage);
        
        // Handle close button (X) click
        dialogStage.setOnCloseRequest(event -> {
            selectedAction = BuildFilesAction.CANCEL_SETUP;
            dialogStage.close();
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
        mainContainer.setMaxSize(500, 400);
        mainContainer.setMinSize(500, 400);
        
        // Header
        VBox header = createHeader();
        
        // Content
        VBox content = createContent();
        
        // Footer
        HBox footer = createFooter(dialogStage);
        
        mainContainer.getChildren().addAll(header, content, footer);
        
        return mainContainer;
    }
    
    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(24, 24, 16, 24));
        header.setStyle("-fx-background-color: #fef3c7; -fx-background-radius: 16 16 0 0;");
        
        // Icon
        Label iconLabel = new Label("⚠️");
        iconLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        iconLabel.setStyle("-fx-text-fill: #d97706;");
        
        // Title
        Label titleLabel = new Label("Build Files Missing");
        titleLabel.setFont(Font.font("Inter", FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #92400e;");
        titleLabel.setAlignment(Pos.CENTER);
        
        header.getChildren().addAll(iconLabel, titleLabel);
        return header;
    }
    
    private VBox createContent() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20, 24, 20, 24));
        
        // Main message
        Label messageLabel = new Label(
            "The required build files (build.xml and build.properties) are missing from your project.\n\n" +
            "These files are essential for project compilation and deployment."
        );
        messageLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 14));
        messageLabel.setStyle("-fx-text-fill: #374151;");
        messageLabel.setWrapText(true);
        messageLabel.setTextAlignment(TextAlignment.CENTER);
        messageLabel.setMaxWidth(450);
        
        // Options
        VBox optionsBox = new VBox(12);
        optionsBox.setAlignment(Pos.CENTER_LEFT);
        optionsBox.setPadding(new Insets(16, 0, 0, 0));
        
        // Option 1: Download and Continue
        HBox option1 = createOption(
            "📥 Download from Server & Continue",
            "Download build files from server and continue with setup",
            BuildFilesAction.DOWNLOAD_AND_CONTINUE
        );
        
        // Option 2: Skip and Continue
        HBox option2 = createOption(
            "⏭️ Skip & Continue",
            "Skip build files download and continue with setup",
            BuildFilesAction.SKIP_AND_CONTINUE
        );
        
        // Option 3: Cancel
        HBox option3 = createOption(
            "❌ Cancel Setup",
            "Cancel the current setup process",
            BuildFilesAction.CANCEL_SETUP
        );
        
        optionsBox.getChildren().addAll(option1, option2, option3);
        
        // Note
        Label noteLabel = new Label(
            "💡 Tip: You can download build files later using the 'Project Build Files Only' setup mode."
        );
        noteLabel.setFont(Font.font("Inter", FontWeight.NORMAL, 12));
        noteLabel.setStyle("-fx-text-fill: #6b7280; -fx-background-color: #f3f4f6; -fx-background-radius: 8;");
        noteLabel.setWrapText(true);
        noteLabel.setTextAlignment(TextAlignment.CENTER);
        noteLabel.setPadding(new Insets(8, 12, 8, 12));
        noteLabel.setMaxWidth(450);
        
        content.getChildren().addAll(messageLabel, optionsBox, noteLabel);
        
        return content;
    }
    
    private HBox createOption(String title, String description, BuildFilesAction action) {
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
        radioButton.setSelected(action == BuildFilesAction.DOWNLOAD_AND_CONTINUE); // Default selection
        if (action == BuildFilesAction.DOWNLOAD_AND_CONTINUE) {
            selectedAction = action;
        }
        
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
        
        // Add click handler
        optionBox.setOnMouseClicked(e -> {
            radioButton.setSelected(true);
            selectedAction = action;
            updateOptionStyles(optionBox.getParent());
        });
        
        radioButton.setOnAction(e -> {
            selectedAction = action;
            updateOptionStyles(optionBox.getParent());
        });
        
        optionBox.getChildren().addAll(radioButton, contentBox);
        
        return optionBox;
    }
    
    private void updateOptionStyles(Parent parent) {
        if (parent instanceof VBox) {
            VBox vbox = (VBox) parent;
            for (javafx.scene.Node node : vbox.getChildren()) {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    // Reset all styles
                    hbox.setStyle(
                        "-fx-background-color: #f9fafb; " +
                        "-fx-border-color: #e5e7eb; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-cursor: hand;"
                    );
                }
            }
        }
    }
    
    private HBox createFooter(Stage dialogStage) {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 24, 24, 24));
        footer.setStyle("-fx-background-color: #f9fafb; -fx-background-radius: 0 0 16 16;");
        
        // Continue button
        Button continueButton = new Button("Continue");
        continueButton.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        continueButton.setStyle(
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
        continueButton.setOnAction(e -> dialogStage.close());
        
        footer.getChildren().add(continueButton);
        return footer;
    }
}
