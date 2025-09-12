package com.nms.support.nms_support;

import com.nms.support.nms_support.service.globalPack.LoggerUtil;

public class FakeMain {
    
    // Private constructor to prevent instantiation
    private FakeMain() {
        // Utility class - no instantiation needed
    }
    
    public static void main(String args[]){
//        System.setProperty("prism.allowhidpi", "true"); // Enable HiDPI support
//        System.setProperty("glass.win.uiScale", "150%"); // Let JavaFX handle scaling
//        System.setProperty("prism.lcdtext", "false"); // Better text rendering
        try {
            NMSSupportApplication.main(args);
        } catch (Exception e){
            LoggerUtil.error(e);
        }
    }
}
