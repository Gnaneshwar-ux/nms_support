package com.nms.support.nms_support.service.globalPack;

public class Mappings {
    public static String getAppFromCode(String code){
        return switch (code) {
            case "CREW" -> "WebWorkspace.exe";
            case "SERVICE_ALERT" -> "ServiceAlert.exe";
            case "WCE" -> "WebCallEntry.exe";
            case "OMS_CONFIG_TOOL" -> "ConfigurationAssistant.exe";
            case "WCB" -> "WebCallbacks.exe";
            case "MODEL" -> "ModelManagement.exe";
            case "STORM" -> "StormManagement.exe";
            default -> "Unknown";
        };
    }
    public static String getCodeFromApp(String app) {
        return switch (app) {
            case "WebWorkspace.exe" -> "CREW";
            case "ServiceAlert.exe" -> "SERVICE_ALERT";
            case "WebCallEntry.exe" -> "WCE";
            case "ConfigurationAssistant.exe" -> "OMS_CONFIG_TOOL";
            case "WebCallbacks.exe" -> "WCB";
            case "ModelManagement.exe" -> "MODEL";
            case "StormManagement.exe" -> "STORM";
            default -> "Unknown";
        };
    }
}
