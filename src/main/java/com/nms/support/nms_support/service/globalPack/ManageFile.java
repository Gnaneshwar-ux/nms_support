package com.nms.support.nms_support.service.globalPack;

import javafx.scene.control.Alert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ManageFile {
    public static boolean open(String filePath) {
        if (!Files.exists(Paths.get(filePath))) {
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Invalid File Path", "The provided file path does not exist.");
            return false;
        }

        String notepadPlusPlusPath = "C:\\Program Files\\Notepad++\\notepad++.exe";
        String notepadPlusPlusPath_x86 = "C:\\Program Files (x86)\\Notepad++\\notepad++.exe";
        String[] command;

        if (new File(notepadPlusPlusPath).exists()) {
            command = new String[]{notepadPlusPlusPath, filePath};
        } else if (new File(notepadPlusPlusPath_x86).exists()) {
            command = new String[]{notepadPlusPlusPath_x86, filePath};
        } else {
            command = new String[]{"cmd", "/c", "notepad", filePath};
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            int exitCode = process.waitFor(); // Wait for the process to complete
            if (exitCode == 0) {
                return true;
            } else {
                DialogUtil.showAlert(Alert.AlertType.ERROR, "Failed to open file", "The process returned a non-zero exit code: " + exitCode);
                return false;
            }
        } catch (Exception ex) {
            DialogUtil.showAlert(Alert.AlertType.ERROR, "Exception while opening log", ex.getMessage());
            return false;
        }
    }

    public static void replaceTextInFiles(List<String> filePaths, String oldText, String newText) {
        for (String filePath : filePaths) {
            try {
                Path path = Paths.get(filePath);
                String content = new String(Files.readAllBytes(path));
                content = content.replace(oldText, newText);
                Files.write(path, content.getBytes());
                LoggerUtil.getLogger().info("Replace text in files completed successfully.");
            } catch (IOException e) {
                LoggerUtil.getLogger().severe("Failed to replace text in file: " + filePath);
                e.printStackTrace();
            }
        }
    }
}
