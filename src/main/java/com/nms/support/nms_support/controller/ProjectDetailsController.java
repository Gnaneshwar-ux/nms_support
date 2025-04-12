package com.nms.support.nms_support.controller;

import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

public class ProjectDetailsController {
    public StackPane overlayPane;
    public ProgressIndicator loadingIndicator;
    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
}
