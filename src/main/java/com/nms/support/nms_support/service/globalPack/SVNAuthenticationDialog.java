package com.nms.support.nms_support.service.globalPack;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Modern JavaFX-based SVN Authentication Dialog
 * Replaces the old Swing implementation with a clean, professional design
 */
public class SVNAuthenticationDialog {
    
    private final Stage dialog;
    private String username;
    private String password;
    private boolean cancelled = false;
    
    public SVNAuthenticationDialog(Window parent, String realm, String url) {
        dialog = new Stage();
        dialog.initOwner(parent);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("SVN Authentication");
        dialog.setResizable(false);
        dialog.setMinWidth(400);
        dialog.setMinHeight(300);
        
        // Set the standard dialog icon
        IconUtils.setStageIcon(dialog);
        
        createUI(realm, url);
    }
    
    private void createUI(String realm, String url) {
        VBox mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(30));
        mainContainer.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 8;");
        mainContainer.setAlignment(Pos.CENTER);
        
        // Header section
        VBox headerSection = new VBox(8);
        headerSection.setAlignment(Pos.CENTER);
        
        // Icon
        Label iconLabel = new Label("🔐");
        iconLabel.setStyle("-fx-font-size: 32px; -fx-padding: 0 0 10 0;");
        
        // Title
        Label titleLabel = new Label("SVN Authentication Required");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        
        // Subtitle
        Label subtitleLabel = new Label("Please enter your credentials to access the SVN repository");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-wrap-text: true;");
        subtitleLabel.setMaxWidth(350);
        subtitleLabel.setAlignment(Pos.CENTER);
        
        headerSection.getChildren().addAll(iconLabel, titleLabel, subtitleLabel);
        
        // Repository info section
        VBox infoSection = new VBox(4);
        infoSection.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 12;");
        
        Label realmLabel = new Label("Realm: " + (realm != null ? realm : "Default"));
        realmLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-weight: 500;");
        
        Label urlLabel = new Label("URL: " + (url != null ? url : "Unknown"));
        urlLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-wrap-text: true;");
        urlLabel.setMaxWidth(350);
        
        infoSection.getChildren().addAll(realmLabel, urlLabel);
        
        // Form section
        VBox formSection = new VBox(15);
        formSection.setAlignment(Pos.CENTER);
        
        // Username field
        VBox usernameContainer = new VBox(5);
        Label usernameLabel = new Label("Username");
        usernameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: #374151;");
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-radius: 4; -fx-padding: 8 12; -fx-font-size: 13px;");
        usernameField.setPrefWidth(300);
        
        usernameContainer.getChildren().addAll(usernameLabel, usernameField);
        
        // Password field
        VBox passwordContainer = new VBox(5);
        Label passwordLabel = new Label("Password");
        passwordLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: #374151;");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-radius: 4; -fx-padding: 8 12; -fx-font-size: 13px;");
        passwordField.setPrefWidth(300);
        
        passwordContainer.getChildren().addAll(passwordLabel, passwordField);
        
        formSection.getChildren().addAll(usernameContainer, passwordContainer);
        
        // Button section
        HBox buttonSection = new HBox(12);
        buttonSection.setAlignment(Pos.CENTER);
        buttonSection.setPadding(new Insets(10, 0, 0, 0));
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle(
            "-fx-background-color: #6b7280; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500; " +
            "-fx-cursor: hand;"
        );
        cancelButton.setPrefWidth(100);
        
        Button okButton = new Button("OK");
        okButton.setStyle(
            "-fx-background-color: #3b82f6; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500; " +
            "-fx-cursor: hand;"
        );
        okButton.setPrefWidth(100);
        
        // Button hover effects
        cancelButton.setOnMouseEntered(e -> cancelButton.setStyle(
            "-fx-background-color: #4b5563; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500; " +
            "-fx-cursor: hand;"
        ));
        cancelButton.setOnMouseExited(e -> cancelButton.setStyle(
            "-fx-background-color: #6b7280; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500; " +
            "-fx-cursor: hand;"
        ));
        
        okButton.setOnMouseEntered(e -> okButton.setStyle(
            "-fx-background-color: #2563eb; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500; " +
            "-fx-cursor: hand;"
        ));
        okButton.setOnMouseExited(e -> okButton.setStyle(
            "-fx-background-color: #3b82f6; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: 6; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500; " +
            "-fx-cursor: hand;"
        ));
        
        buttonSection.getChildren().addAll(cancelButton, okButton);
        
        // Event handlers
        cancelButton.setOnAction(e -> {
            cancelled = true;
            dialog.close();
        });
        
        okButton.setOnAction(e -> {
            username = usernameField.getText();
            password = passwordField.getText();
            dialog.close();
        });
        
        // Set default button
        okButton.setDefaultButton(true);
        
        // Focus on username field
        Platform.runLater(() -> usernameField.requestFocus());
        
        // Assemble main container
        mainContainer.getChildren().addAll(headerSection, infoSection, formSection, buttonSection);
        
        Scene scene = new Scene(mainContainer);
        dialog.setScene(scene);
    }
    
    public boolean showAndWait() {
        dialog.showAndWait();
        return !cancelled && username != null && !username.trim().isEmpty();
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
}
