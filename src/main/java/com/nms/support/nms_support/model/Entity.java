package com.nms.support.nms_support.model;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Entity {
    Map<String,String> attributes;
    String name;
    String ID;
    String PORT_A;
    String PORT_B;
    Entity next;
    Entity prev;
    Diagram diagram;
    public Entity(String ID, String name){
        this.name = name;
        this.ID = ID;
        this.next = this.prev = null;
        this.attributes = new HashMap<>();
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getPORT_A() {
        return PORT_A;
    }

    public void setPORT_A(String PORT_A) {
        this.PORT_A = PORT_A;
    }

    public String getPORT_B() {
        return PORT_B;
    }

    public void setPORT_B(String PORT_B) {
        this.PORT_B = PORT_B;
    }

    public Entity getNext() {
        return next;
    }

    public void setNext(Entity next) {
        this.next = next;
    }

    public Entity getPrev() {
        return prev;
    }

    public void setPrev(Entity prev) {
        this.prev = prev;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    public void setDiagram(Diagram diagram) {
        this.diagram = diagram;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getAttribute(String key){
        return attributes.get(key);
    }

    public void addAttribute(String key, String value){
        attributes.put(key, value);
    }

}
