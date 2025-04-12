package com.nms.support.nms_support;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FirebaseUpdater {

    // Firebase Database URL (replace with your Firebase database URL)
    private static final String FIREBASE_URL = "https://firestore.googleapis.com/v1/projects/nms-devtools/databases/(default)/documents/NMS_DevTools/VERSION_DATA/";

    public static void main(String[] args) {
        // Load the version from properties
        String defaultVersion = loadVersionFromProperties();

        // Create a simple UI to collect version and description
        JFrame frame = new JFrame("Firebase Data Push");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400); // Standard size for better appearance

        GridBagLayout gbl = new GridBagLayout();
        frame.setLayout(gbl);

        // Create GridBagConstraints to set layout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        // Setup UI components
        JLabel versionLabel = new JLabel("Version:");
        JTextField versionField = new JTextField(defaultVersion, 10);
        versionField.setPreferredSize(new Dimension(400, 30));

        JLabel descriptionLabel = new JLabel("Description:");
        JTextArea descriptionField = new JTextArea(5, 20);
        descriptionField.setWrapStyleWord(true);
        descriptionField.setLineWrap(true);
        descriptionField.setPreferredSize(new Dimension(300, 100));
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionField);
        JButton submitButton = new JButton("Push to Firebase");

        // Define GridBagLayout constraints for the first column (fixed width)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;  // First column takes 100px width
        gbc.weightx = 0;    // Do not resize the first column
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(versionLabel, gbc);

        // Define GridBagLayout constraints for the second column (resizable)
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 1;    // Allow the second column to resize
        gbc.weighty = 0;    // No vertical resizing for this row
        frame.add(versionField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;    // First column with fixed width
        gbc.weighty = 0;    // No vertical resizing for this row
        frame.add(descriptionLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;    // Second column resizable
        gbc.weighty = 1;    // Allow vertical resizing only for this row (description field)
        frame.add(descriptionScrollPane, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;    // No vertical resizing for this row
        frame.add(new JLabel(), gbc); // Spacer

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;    // No vertical resizing for this row (button)
        frame.add(submitButton, gbc);

        // Styling the button
        submitButton.setBackground(new Color(66, 133, 244)); // Google blue color
        submitButton.setForeground(Color.WHITE);
        submitButton.setBorder(BorderFactory.createLineBorder(new Color(36, 113, 163), 2));
        submitButton.setFocusPainted(false);
        submitButton.setPreferredSize(new Dimension(120, 35)); // Reduced button size
        submitButton.setHorizontalAlignment(SwingConstants.CENTER); // Center align the button

        // Resize behavior
        versionField.setMinimumSize(new Dimension(150, 30));
        descriptionField.setMinimumSize(new Dimension(300, 100));

        // Submit button action
        submitButton.addActionListener(e -> {
            String version = versionField.getText();
            String description = descriptionField.getText();

            if (version.isEmpty() || description.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please fill in all fields.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Validate version format
            if (!isValidVersion(version)) {
                JOptionPane.showMessageDialog(frame, "Version must be in the format X.X.X (e.g., 1.0.0).", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                // Push data to Firebase
                pushToFirebase(version, description);
                JOptionPane.showMessageDialog(frame, "Data pushed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to push data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.setVisible(true);
    }

    private static boolean isValidVersion(String version) {
        // Regex to check version format like X.X.X where X is a number
        String versionPattern = "^\\d+\\.\\d+\\.\\d+$";
        Pattern pattern = Pattern.compile(versionPattern);
        Matcher matcher = pattern.matcher(version);
        return matcher.matches();
    }

    private static void pushToFirebase(String version, String description) throws Exception {
        // Construct JSON payload
        String jsonPayload = String.format(
                "{ \"fields\": { \"version\": { \"stringValue\": \"%s\" }, \"description\": { \"stringValue\": \"%s\" } } }",
                version, description
        );
        // Open connection to Firebase
        URL url = new URL(FIREBASE_URL);

        System.out.println(jsonPayload);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        conn.setRequestMethod("POST"); // Use POST to create new entries
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setDoOutput(true);

        // Write JSON payload
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonPayload.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Check response
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
            throw new RuntimeException("Failed to push data. HTTP error code: " + responseCode);
        }
    }

    private static String loadVersionFromProperties() {
        Properties properties = new Properties();
        String version = "1.0.0"; // Default version if properties are not found
        try (InputStream input = FirebaseUpdater.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                version = properties.getProperty("app.version", version);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return version;
    }
}
