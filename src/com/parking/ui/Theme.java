package com.parking.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class Theme {

    // ── Theme identifiers ─────────────────────────────────────────────────

    public enum ThemeId {
        DARK        ("Dark"),
        LIGHT       ("Light"),
        MIDNIGHT    ("Midnight Blue"),
        FOREST      ("Forest Green"),
        ROSE        ("Rose Gold");

        public final String displayName;
        ThemeId(String displayName) { this.displayName = displayName; }
    }

    // ── Observable active theme ───────────────────────────────────────────

    public static final ObjectProperty<ThemeId> current =
            new SimpleObjectProperty<>(ThemeId.DARK);

    /** Kept for backward compatibility — true when any dark variant is active. */
    public static final javafx.beans.property.BooleanProperty isDark =
            new javafx.beans.property.SimpleBooleanProperty(true);

    public static void setTheme(ThemeId id) {
        current.set(id);
        isDark.set(id != ThemeId.LIGHT && id != ThemeId.ROSE);
    }

    /** Legacy two-way toggle (Dark ↔ Light). */
    public static void toggle() {
        setTheme(current.get() == ThemeId.LIGHT ? ThemeId.DARK : ThemeId.LIGHT);
    }

    // ── Token accessors ───────────────────────────────────────────────────

    public static String BG_BASE() {
        return switch (current.get()) {
            case DARK     -> "#0A0E1A";
            case LIGHT    -> "#F8FAFC";
            case MIDNIGHT -> "#080C18";
            case FOREST   -> "#0A120A";
            case ROSE     -> "#FDF6F0";
        };
    }

    public static String BG_SURFACE() {
        return switch (current.get()) {
            case DARK     -> "#111827";
            case LIGHT    -> "#FFFFFF";
            case MIDNIGHT -> "#0F1629";
            case FOREST   -> "#0F1A0F";
            case ROSE     -> "#FFFFFF";
        };
    }

    public static String BG_RAISED() {
        return switch (current.get()) {
            case DARK     -> "#1C2333";
            case LIGHT    -> "#F3F4F6";
            case MIDNIGHT -> "#1A2340";
            case FOREST   -> "#162416";
            case ROSE     -> "#FDF2EC";
        };
    }

    public static String BG_SIDEBAR() {
        return switch (current.get()) {
            case DARK     -> "#0D1220";
            case LIGHT    -> "#F1F5F9";
            case MIDNIGHT -> "#0A1020";
            case FOREST   -> "#091509";
            case ROSE     -> "#FEF9F5";
        };
    }

    public static String ACCENT() {
        return switch (current.get()) {
            case DARK, LIGHT -> "#3B82F6";   // Blue
            case MIDNIGHT    -> "#7C3AED";   // Violet
            case FOREST      -> "#16A34A";   // Green
            case ROSE        -> "#E11D48";   // Rose
        };
    }

    public static String SUCCESS() { return "#22C55E"; }
    public static String WARNING() { return "#F59E0B"; }
    public static String DANGER()  { return "#EF4444"; }
    public static String INFO()    { return "#06B6D4"; }

    public static String TEXT_H() {
        return switch (current.get()) {
            case DARK, MIDNIGHT, FOREST -> "#F9FAFB";
            case LIGHT                  -> "#0F172A";
            case ROSE                   -> "#1C0A0A";
        };
    }

    public static String TEXT_B() {
        return switch (current.get()) {
            case DARK, MIDNIGHT, FOREST -> "#D1D5DB";
            case LIGHT                  -> "#374151";
            case ROSE                   -> "#44292A";
        };
    }

    public static String TEXT_M() {
        return switch (current.get()) {
            case DARK, MIDNIGHT, FOREST -> "#6B7280";
            case LIGHT                  -> "#64748B";
            case ROSE                   -> "#9F6B70";
        };
    }

    public static String BORDER() {
        return switch (current.get()) {
            case DARK     -> "#1F2937";
            case LIGHT    -> "#E2E8F0";
            case MIDNIGHT -> "#1E2D4A";
            case FOREST   -> "#1A2E1A";
            case ROSE     -> "#F3D5D8";
        };
    }

    public static String BORDER_LIT() {
        return switch (current.get()) {
            case DARK     -> "#374151";
            case LIGHT    -> "#CBD5E1";
            case MIDNIGHT -> "#2D3F60";
            case FOREST   -> "#2A4A2A";
            case ROSE     -> "#E8B4BA";
        };
    }
}