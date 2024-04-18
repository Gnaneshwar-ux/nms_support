package com.nms.support.nms_support;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {
    public Label welcome;
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
        welcome.setText("MyText");
    }
}