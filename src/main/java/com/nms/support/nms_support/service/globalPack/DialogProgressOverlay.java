package com.nms.support.nms_support.service.globalPack;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Reusable progress overlay with customizable steps and live status updates.
 */
public class DialogProgressOverlay {

    public static class StepHandle {
        private final StepView view;
        private final int index;
        StepHandle(StepView view, int index) {
            this.view = view;
            this.index = index;
        }
        public void running() { Platform.runLater(view::setRunning); }
        public void success() { Platform.runLater(view::setDone); }
        public void fail(String message) { Platform.runLater(() -> view.setError(message)); }
        public void setProgress(int percent) { Platform.runLater(() -> view.setProgress(percent)); }
        public void setProgress(int percent, String message) { Platform.runLater(() -> view.setProgress(percent, message)); }
        public int index() { return index; }
    }

    public static class Config {
        public String title = "Processing";
        public List<String> stepTitles = new ArrayList<>();
        public boolean allowCancel = true;
        public String startLabel = "Start";
        public String closeLabel = "Close";
        public Consumer<List<StepHandle>> onStart; // invoked on background pool
        public Runnable onClosed;
    }

    private final StackPane overlay = new StackPane();
    private final VBox card = new VBox(12);
    private final VBox stepsBox = new VBox(12);
    private final Button startBtn = new Button();
    private final Button cancelBtn = new Button("Cancel");
    private final Button closeBtn = new Button();
    private final List<StepView> stepViews = new ArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("progress-overlay-worker");
        return t;
    });
    private Pane blurTarget;

    public DialogProgressOverlay() {
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
        overlay.setPickOnBounds(false);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16 20; -fx-min-width: 520; -fx-max-width: 560; -fx-text-fill: #111111;");
        card.setMaxHeight(Region.USE_PREF_SIZE);
        DropShadow ds = new DropShadow(14, Color.rgb(0,0,0,0.25));
        card.setEffect(ds);
        overlay.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    public void showOn(StackPane sceneRoot, Pane blurBehind, Config cfg) {
        this.blurTarget = blurBehind;
        blurTarget.setEffect(new GaussianBlur(12));
        blurTarget.setDisable(true);

        card.getChildren().clear();

        Label title = new Label(cfg.title);
        title.setStyle("-fx-font-size: 1.2em; -fx-text-fill: #1a237e; -fx-font-weight: 700;");
        card.getChildren().add(title);

        stepsBox.getChildren().clear();
        stepViews.clear();
        for (String stepTitle : cfg.stepTitles) {
            StepView v = new StepView(stepTitle);
            stepViews.add(v);
            stepsBox.getChildren().add(v.root);
        }
        VBox panel = new VBox();
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 12; -fx-border-color: #e5e7eb; -fx-border-radius: 10; -fx-border-width: 1;");
        panel.getChildren().add(stepsBox);
        card.getChildren().add(panel);

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        startBtn.setText(cfg.startLabel);
        startBtn.setDefaultButton(true);
        startBtn.setStyle("-fx-background-color: #1e88e5; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 16; -fx-font-weight: 600;");
        closeBtn.setText(cfg.closeLabel);
        closeBtn.setDisable(true);
        closeBtn.setStyle("-fx-background-color: #455a64; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 16; -fx-font-weight: 600;");
        cancelBtn.setManaged(cfg.allowCancel);
        cancelBtn.setVisible(cfg.allowCancel);
        cancelBtn.setStyle("-fx-background-color: #ef5350; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 12; -fx-font-weight: 600;");
        footer.getChildren().addAll(cancelBtn, startBtn, closeBtn);
        card.getChildren().add(footer);

        startBtn.setOnAction(e -> {
            startBtn.setDisable(true);
            List<StepHandle> handles = new ArrayList<>();
            for (int i = 0; i < stepViews.size(); i++) {
                handles.add(new StepHandle(stepViews.get(i), i));
            }
            if (cfg.onStart != null) {
                pool.submit(() -> cfg.onStart.accept(handles));
            }
        });
        closeBtn.setOnAction(e -> hide(sceneRoot, cfg));
        cancelBtn.setOnAction(e -> {
            // consumer should handle cancellation via its own cooperative flags
            closeBtn.setDisable(false);
            cancelBtn.setManaged(false);
            cancelBtn.setVisible(false);
        });

        if (!sceneRoot.getChildren().contains(overlay)) {
            overlay.setOpacity(0);
            sceneRoot.getChildren().add(overlay);
            FadeTransition f = new FadeTransition(Duration.millis(200), overlay);
            f.setFromValue(0);
            f.setToValue(1);
            f.play();
        }
    }

    public void markFinished() {
        Platform.runLater(() -> {
            closeBtn.setDisable(false);
            cancelBtn.setManaged(false);
            cancelBtn.setVisible(false);
        });
    }

    private void hide(StackPane sceneRoot, Config cfg) {
        FadeTransition out = new FadeTransition(Duration.millis(250), overlay);
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(ev -> {
            overlay.setVisible(false);
            sceneRoot.getChildren().remove(overlay);
            if (blurTarget != null) {
                blurTarget.setEffect(null);
                blurTarget.setDisable(false);
            }
            if (cfg.onClosed != null) cfg.onClosed.run();
        });
        out.play();
    }

    private static class StepView {
        final HBox root = new HBox(10);
        final Label label = new Label();
        final ProgressIndicator spinner = new ProgressIndicator();
        final Label percent = new Label("0%");
        final Label statusText = new Label("");
        final Label tick = new Label("\u2713");
        final Label cross = new Label("\u2717");
        final Label errorText = new Label("");
        StepView(String title) {
            root.setAlignment(Pos.CENTER_LEFT);
            label.setText(title);
            label.setStyle("-fx-font-weight: 600; -fx-text-fill: #111111;");
            spinner.setPrefSize(18,18);
            spinner.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            percent.setStyle("-fx-text-fill: #111111; -fx-font-size: 0.95em; -fx-font-weight: 600;");
            statusText.setStyle("-fx-text-fill: #64748b; -fx-font-size: 0.85em; -fx-font-style: italic;");
            statusText.setVisible(false);
            tick.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold; -fx-font-size: 1.2em;");
            tick.setVisible(false);
            cross.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold; -fx-font-size: 1.2em;");
            cross.setVisible(false);
            errorText.setStyle("-fx-text-fill: #c62828; -fx-font-size: 0.95em;");
            errorText.setVisible(false);
            VBox container = new VBox(4);
            container.getChildren().addAll(new HBox(10, label, spinner, percent, tick, cross), statusText, errorText);
            root.getChildren().add(container);
            setProgress(0);
            spinner.setVisible(false);
        }
        void setRunning() {
            spinner.setVisible(true);
            tick.setVisible(false);
            cross.setVisible(false);
            errorText.setVisible(false);
            statusText.setVisible(false);
            setProgress(1);
        }
        void setDone() {
            spinner.setVisible(false);
            tick.setVisible(true);
            statusText.setVisible(false);
            setProgress(100);
        }
        void setError(String message) {
            spinner.setVisible(false);
            cross.setVisible(true);
            errorText.setText(message);
            errorText.setVisible(true);
            statusText.setVisible(false);
        }
        void setProgress(int p) {
            int v = Math.max(0, Math.min(100, p));
            percent.setText(v + "%");
            // Update spinner to show determinate progress when > 0
            if (v > 0 && v < 100) {
                spinner.setProgress(v / 100.0);
            }
        }
        void setProgress(int p, String message) {
            setProgress(p);
            if (message != null && !message.isEmpty()) {
                statusText.setText(message);
                statusText.setVisible(true);
            }
        }
    }
}


