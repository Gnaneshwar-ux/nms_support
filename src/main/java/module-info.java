module com.nms.support.nms_support {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;
    requires jsch;
    requires java.logging;
    requires com.hierynomus.sshj;

    opens com.nms.support.nms_support.model to javafx.base,com.fasterxml.jackson.databind;
    opens com.nms.support.nms_support to javafx.fxml;
    opens com.nms.support.nms_support.controller to javafx.fxml;
    exports com.nms.support.nms_support;
    exports com.nms.support.nms_support.controller;
    exports com.nms.support.nms_support.model to com.fasterxml.jackson.databind;
}