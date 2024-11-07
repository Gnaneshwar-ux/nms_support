package com.nms.support.nms_support.service.globalPack;

import javafx.scene.image.Image;
import javafx.stage.Stage;

public class IconUtils {
    private static final Image APP_ICON = new Image(IconUtils.class.getResourceAsStream("/com/nms/support/nms_support/images/icons/nmssupport.png"));

    public static void setStageIcon(Stage stage) {
        stage.getIcons().add(APP_ICON);
    }
}
