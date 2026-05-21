package com.parking.ui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Central animation library for ParkSpace UI.
 *
 * All methods are static — call them from any screen.
 */
public class Animations {

    // ── Page transitions ──────────────────────────────────────────────────

    /**
     * Slides a new page in from the right while fading in.
     * Used by ParkingApp.showPage().
     */
    public static void pageEnter(Node page) {
        page.setOpacity(0);
        page.setTranslateX(30);

        FadeTransition fade = new FadeTransition(Duration.millis(220), page);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), page);
        slide.setFromX(30);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.play();
    }

    /**
     * Fades out the current page before switching.
     * @param onFinished called when fade is done (swap the page here)
     */
    public static void pageExit(Node page, Runnable onFinished) {
        FadeTransition fade = new FadeTransition(Duration.millis(100), page);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> onFinished.run());
        fade.play();
    }

    // ── Card stagger entrance ─────────────────────────────────────────────

    /**
     * Animates a list of cards one after another with a stagger delay.
     * Each card fades in and slides up slightly.
     *
     * @param delayMs  base delay in ms before the first card starts
     * @param gapMs    gap between each card animation
     * @param cards    the nodes to animate
     */
    public static void staggerCards(int delayMs, int gapMs, Node... cards) {
        for (int i = 0; i < cards.length; i++) {
            Node card = cards[i];
            card.setOpacity(0);
            card.setTranslateY(22);

            FadeTransition fade = new FadeTransition(Duration.millis(300), card);
            fade.setFromValue(0);
            fade.setToValue(1);

            TranslateTransition slide = new TranslateTransition(Duration.millis(300), card);
            slide.setFromY(22);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition pt = new ParallelTransition(fade, slide);
            pt.setDelay(Duration.millis(delayMs + (long) i * gapMs));
            pt.play();
        }
    }

    // ── Single card pop-in ────────────────────────────────────────────────

    /**
     * Scales a card from 0.92 → 1.0 with a fade, giving a "pop" effect.
     * Good for result cards that appear after a user action.
     */
    public static void cardPop(Node card) {
        card.setOpacity(0);
        card.setScaleX(0.92);
        card.setScaleY(0.92);

        FadeTransition fade = new FadeTransition(Duration.millis(200), card);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }

    // ── Count-up animation for stat numbers ───────────────────────────────

    /**
     * Animates a label from 0 up to {@code target} over {@code durationMs}.
     * Works for integer values (spot counts, transaction counts).
     */
    public static void countUp(Label label, int target, int durationMs) {
        if (target == 0) { label.setText("0"); return; }

        long[] frame = {0};
        long steps = 30;
        long stepMs = durationMs / steps;

        Timeline tl = new Timeline();
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis((long) step * stepMs),
                    e -> {
                        int current = (int) Math.round(target * (step / (double) steps));
                        label.setText(String.valueOf(current));
                    }
            ));
        }
        tl.play();
    }

    /**
     * Count-up for decimal values (revenue, fees).
     */
    public static void countUpDecimal(Label label, double target, int durationMs, String suffix) {
        if (target == 0) { label.setText("0" + suffix); return; }

        int steps = 30;
        Timeline tl = new Timeline();
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis((long) step * (durationMs / steps)),
                    e -> {
                        double current = target * (step / (double) steps);
                        label.setText(String.format("%.0f", current) + suffix);
                    }
            ));
        }
        tl.play();
    }

    // ── Button pulse ──────────────────────────────────────────────────────

    /**
     * Briefly scales a button up then back to normal on click.
     * Call inside a button's setOnAction handler.
     */
    public static void buttonPulse(Node button) {
        ScaleTransition up = new ScaleTransition(Duration.millis(80), button);
        up.setToX(0.95); up.setToY(0.95);

        ScaleTransition down = new ScaleTransition(Duration.millis(80), button);
        down.setToX(1.0); down.setToY(1.0);

        SequentialTransition seq = new SequentialTransition(up, down);
        seq.play();
    }

    // ── Status banner slide-down ──────────────────────────────────────────

    /**
     * Slides a status/alert label down into view from above.
     */
    public static void statusSlideIn(Node label) {
        label.setVisible(true);
        label.setOpacity(0);
        label.setTranslateY(-10);

        FadeTransition fade = new FadeTransition(Duration.millis(180), label);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(180), label);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide).play();
    }

    // ── Shake (validation error) ──────────────────────────────────────────

    /**
     * Shakes a node left-right to signal a validation error.
     */
    public static void shake(Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(8);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    // ── Sidebar nav item highlight pulse ─────────────────────────────────

    /**
     * Briefly scales a nav item when clicked.
     */
    public static void navClick(Node item) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), item);
        st.setFromX(1.0); st.setFromY(1.0);
        st.setToX(0.96);  st.setToY(0.96);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    // ── Table row highlight ───────────────────────────────────────────────

    /**
     * Fades in a table after data is loaded.
     */
    public static void tableLoad(Node table) {
        table.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(250), table);
        ft.setToValue(1);
        ft.setDelay(Duration.millis(100));
        ft.play();
    }

    // ── Loading spinner (for async operations) ────────────────────────────

    /**
     * Continuously rotates a node (e.g. a ⟳ label) to show loading.
     * Returns the Timeline so you can stop it: timeline.stop().
     */
    public static Timeline spinner(Node node) {
        RotateTransition rt = new RotateTransition(Duration.millis(600), node);
        rt.setFromAngle(0);
        rt.setToAngle(360);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        rt.play();

        // wrap in timeline for consistent API
        Timeline tl = new Timeline(new KeyFrame(Duration.INDEFINITE));
        tl.setOnFinished(e -> rt.stop());
        return tl;
    }
}