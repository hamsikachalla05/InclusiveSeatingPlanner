package com.seatingplanner.ui;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Colour key for the seat map. Each swatch also shows the letter marker used on the map. */
public class LegendPanel extends JPanel {

    public LegendPanel(boolean includeSelection) {
        super(new UiKit.WrapLayout(FlowLayout.LEFT, 14, 2));
        setOpaque(false);
        add(item("Available", Theme.SEAT_FREE, Theme.SEAT_FREE_BORDER, null, false));
        add(item("Occupied", Theme.SEAT_OCCUPIED, Theme.SEAT_OCCUPIED, null, false));
        add(item("Accessible", Theme.SEAT_ACCESSIBLE, Theme.SEAT_ACCESSIBLE_BORDER, "A", false));
        add(item("Reserved", Theme.SEAT_RESERVED, Theme.SEAT_RESERVED_BORDER, "R", false));
        add(item("Unresolved requirement", Theme.SEAT_OCCUPIED, Theme.SEAT_OCCUPIED, null, true));
        if (includeSelection) {
            add(outline("Selected", Theme.SEAT_SELECTED));
            add(outline("Related (companion / group)", Theme.SEAT_HIGHLIGHT));
        }
    }

    private static JLabel item(String text, Color fill, Color border, String letter, boolean redDot) {
        JLabel l = new JLabel(text, new Swatch(fill, border, letter, redDot, null), JLabel.LEFT);
        l.setFont(Theme.small());
        l.setForeground(Theme.MUTED);
        l.setIconTextGap(6);
        return l;
    }

    private static JLabel outline(String text, Color ring) {
        JLabel l = new JLabel(text, new Swatch(Theme.SEAT_FREE, Theme.SEAT_FREE_BORDER, null, false, ring), JLabel.LEFT);
        l.setFont(Theme.small());
        l.setForeground(Theme.MUTED);
        l.setIconTextGap(6);
        return l;
    }

    private record Swatch(Color fill, Color border, String letter, boolean redDot, Color ring) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(x + 2, y + 2, 20, 14, 6, 6);
            g.setColor(border);
            g.drawRoundRect(x + 2, y + 2, 19, 13, 6, 6);
            if (letter != null) {
                g.setFont(Theme.bold(8));
                g.drawString(letter, x + 15, y + 10);
            }
            if (redDot) {
                g.setColor(new Color(0xEF4444));
                g.fillOval(x + 4, y + 4, 6, 6);
            }
            if (ring != null) {
                g.setColor(ring);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(x, y, 23, 17, 8, 8);
            }
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }
}
