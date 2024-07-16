package com.nms.support.nms_support.controller;

import com.nms.support.nms_support.model.Entity;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import java.net.URL;
import java.util.ResourceBundle;

public class EntityCardController implements Initializable {

    @FXML
    private Label headerLabel;

    @FXML
    private Label bodyLabel;



    private String headerText;
    private String bodyText;

    public EntityCardController(String headerText, String bodyText) {
        this.headerText = headerText;
        this.bodyText = bodyText;
    }

    public EntityCardController(){

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set header and body text
        headerLabel.setText(headerText);
        bodyLabel.setText(bodyText);
    }

    public void setEntity(Entity entity) {
        this.headerText = entity.getName();
        this.bodyText = entity.getPORT_A()+" <=> "+entity.getPORT_B();
        headerLabel.setText(headerText);
        bodyLabel.setText(bodyText);
    }

}
