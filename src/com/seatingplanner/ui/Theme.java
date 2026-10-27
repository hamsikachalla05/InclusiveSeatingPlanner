package com.seatingplanner.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/** Central palette and typography. Colours were chosen for WCAG AA contrast on their backgrounds. */
public final class Theme {

    public static final Color BG = new Color(0xF4F6FA);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(0xF8FAFC);
    public static final Color BORDER = new Color(0xE2E8F0);
    public static final Color BORDER_STRONG = new Color(0xCBD5E1);

    public static final Color SIDEBAR = new Color(0x141C2F);
    public static final Color SIDEBAR_HOVER = new Color(0x1F2A44);
    public static final Color SIDEBAR_ACTIVE = new Color(0x2B3A5C);
    public static final Color SIDEBAR_TEXT = new Color(0xB6C2D6);

    public static final Color PRIMARY = new Color(0x3B5BDB);
    public static final Color PRIMARY_HOVER = new Color(0x2F4AC0);
    public static final Color PRIMARY_SOFT = new Color(0xE8EDFF);

    public static final Color TEXT = new Color(0x1E293B);
    public static final Color MUTED = new Color(0x5B6578);
    public static final Color DISABLED = new Color(0x9AA4B5);

    public static final Color SUCCESS = new Color(0x15803D);
    public static final Color SUCCESS_SOFT = new Color(0xDCFCE7);
    public static final Color WARNING = new Color(0xB45309);
    public static final Color WARNING_SOFT = new Color(0xFEF3C7);
    public static final Color DANGER = new Color(0xB91C1C);
    public static final Color DANGER_SOFT = new Color(0xFEE2E2);
    public static final Color INFO_SOFT = new Color(0xE0F2FE);
    public static final Color INFO = new Color(0x0369A1);

    // Seat map palette
    public static final Color SEAT_FREE = new Color(0xF1F5F9);
    public static final Color SEAT_FREE_BORDER = new Color(0x94A3B8);
    public static final Color SEAT_ACCESSIBLE = new Color(0xD1FAE5);
    public static final Color SEAT_ACCESSIBLE_BORDER = new Color(0x047857);
    public static final Color SEAT_RESERVED = new Color(0xFEF3C7);
    public static final Color SEAT_RESERVED_BORDER = new Color(0xB45309);
    public static final Color SEAT_OCCUPIED = new Color(0x3B5BDB);
    public static final Color SEAT_SELECTED = new Color(0x0F172A);
    public static final Color SEAT_HIGHLIGHT = new Color(0xEA580C);
    public static final Color STAGE = new Color(0x1E293B);

    public static final String FONT_FAMILY = pickFont();

    private Theme() {
    }

    private static String pickFont() {
        List<String> available = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String f : List.of("Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Roboto", "Noto Sans", "DejaVu Sans")) {
            if (available.contains(f)) {
                return f;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    public static Font body() {
        return font(Font.PLAIN, 13);
    }

    public static Font small() {
        return font(Font.PLAIN, 12);
    }

    public static Font bold(float size) {
        return font(Font.BOLD, size);
    }

    /** Installs the system look and feel with the app's fonts and colours as defaults. */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        FontUIResource f = new FontUIResource(body());
        for (String key : List.of("Label.font", "Button.font", "TextField.font", "TextArea.font", "ComboBox.font",
                "CheckBox.font", "RadioButton.font", "Table.font", "TableHeader.font", "List.font", "Spinner.font",
                "ToolTip.font", "OptionPane.messageFont", "OptionPane.buttonFont", "Menu.font", "MenuItem.font",
                "MenuBar.font", "PopupMenu.font", "TabbedPane.font", "FormattedTextField.font", "Slider.font",
                "TitledBorder.font", "EditorPane.font", "CheckBoxMenuItem.font")) {
            UIManager.put(key, f);
        }
        UIManager.put("Panel.background", new ColorUIResource(BG));
        UIManager.put("OptionPane.background", new ColorUIResource(SURFACE));
        UIManager.put("Label.foreground", new ColorUIResource(TEXT));
        UIManager.put("ToolTip.background", new ColorUIResource(new Color(0x1E293B)));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
        UIManager.put("Table.selectionBackground", new ColorUIResource(PRIMARY_SOFT));
        UIManager.put("Table.selectionForeground", new ColorUIResource(TEXT));
        UIManager.put("ScrollBar.width", 12);
    }

    public static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }
}
