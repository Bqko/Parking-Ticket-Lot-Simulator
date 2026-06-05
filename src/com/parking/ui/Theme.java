package com.parking.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class Theme {

    // ── Observable flag ───────────────────────────────────────────────────
    public static final BooleanProperty isDark = new SimpleBooleanProperty(true);

    public static void toggle() { isDark.set(!isDark.get()); }

    // ── Token accessors — always return the current theme's value ─────────
    public static String BG_BASE()    { return isDark.get() ? "#0A0E1A"  : "#F8FAFC";  }
    public static String BG_SURFACE() { return isDark.get() ? "#111827"  : "#FFFFFF";  }
    public static String BG_RAISED()  { return isDark.get() ? "#1C2333"  : "#F3F4F6";  }
    public static String BG_SIDEBAR() { return isDark.get() ? "#0D1220"  : "#F1F5F9";  }
    public static String ACCENT()     { return "#3B82F6"; }
    public static String SUCCESS()    { return "#22C55E"; }
    public static String WARNING()    { return "#F59E0B"; }
    public static String DANGER()     { return "#EF4444"; }
    public static String INFO()       { return "#06B6D4"; }
    public static String TEXT_H()     { return isDark.get() ? "#F9FAFB"  : "#0F172A";  }
    public static String TEXT_B()     { return isDark.get() ? "#D1D5DB"  : "#374151";  }
    public static String TEXT_M()     { return isDark.get() ? "#6B7280"  : "#64748B";  }
    public static String BORDER()     { return isDark.get() ? "#1F2937"  : "#E2E8F0";  }
    public static String BORDER_LIT() { return isDark.get() ? "#374151"  : "#CBD5E1";  }
}