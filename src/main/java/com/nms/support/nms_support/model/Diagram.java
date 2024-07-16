package com.nms.support.nms_support.model;

import eu.hansolo.tilesfx.addons.Switch;

public class Diagram {
    String type;
    String height;
    String angle;
    String scale;
    Geometry geometry;

    public Diagram(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getAngle() {
        return angle;
    }

    public void setAngle(String angle) {
        this.angle = angle;
    }

    public String getScale() {
        return scale;
    }

    public void setScale(String scale) {
        this.scale = scale;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public boolean setData(String key, String value){
        if(key == null) return false;
        switch (key.toUpperCase()){
            case "HEIGHT": this.height = value;
            break;
            case "ANGLE" : this.angle = value;
            break;
            case "SCALE": this.scale = value;
            break;
            default : System.out.println("Diagram attribute key is invalid");
            return false;
        }
        return true;
    }
}
