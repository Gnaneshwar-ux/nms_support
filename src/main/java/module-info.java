module com.nms.support.nms_support {

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
    requires java.sql;
    requires com.hierynomus.sshj;
    requires svnkit;
    requires javafx.swing;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires org.kordamp.ikonli.fontawesome;
    requires com.oracle.database.jdbc;

    opens com.nms.support.nms_support.model to javafx.base,com.fasterxml.jackson.databind;
    opens com.nms.support.nms_support to javafx.fxml;
    opens com.nms.support.nms_support.controller to javafx.fxml;
    exports com.nms.support.nms_support;
    exports com.nms.support.nms_support.controller;
    exports com.nms.support.nms_support.model;
    exports com.nms.support.nms_support.service.userdata;
    exports com.nms.support.nms_support.service.globalPack;

}