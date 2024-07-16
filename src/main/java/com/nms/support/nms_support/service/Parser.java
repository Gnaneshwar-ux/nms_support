package com.nms.support.nms_support.service;

import com.nms.support.nms_support.model.Entity;

import java.util.ArrayList;
import java.util.List;

public interface Parser {
    public List<Entity> parseFile(String file);
}
