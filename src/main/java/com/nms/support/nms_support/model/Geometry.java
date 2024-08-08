package com.nms.support.nms_support.model;

import java.util.ArrayList;
import java.util.List;

public class Geometry {
    List<Coordinate> coordinates;

    public Geometry(){
        this.coordinates = new ArrayList<>();
    }
    public boolean addCoordinate(String x, String y){
        Coordinate c= new Coordinate(x,y);
        return coordinates.add(c);
    }
    public Coordinate getCoordinate(int idx){
        return coordinates.get(idx);
    }

    public List<Coordinate> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Coordinate> coordinates) {
        this.coordinates = coordinates;
    }

    public String toString(){
        StringBuilder coordsBuilder = new StringBuilder();
        for(Coordinate c: coordinates){
            coordsBuilder.append(c.getX() + " - " + c.getY()+"\n");
        }
        return coordsBuilder.toString();
    }
}
