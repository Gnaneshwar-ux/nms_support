package com.nms.support.nms_support.service.userdata;

import java.io.File;

public interface IManager {
    void initManager(File source);
    boolean saveData();
}
