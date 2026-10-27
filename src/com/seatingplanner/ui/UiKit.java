package com.seatingplanner.ui;

import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.ConflictSeverity;
import com.seatingplanner.model.ResolutionStatus;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Small library of consistently styled Swing building blocks. */
public final class UiKit {

    private UiKit() {
    }

    // ------------------------------------------------------------------ buttons

    /** Flat, rounded button with hover feedback and a readable disabled state. */
    public static class AppButton extends JButton {

        public enum Kind { PRIMARY, SECONDARY, DANGER, GHOST, SUCCESS }

        private final Kind kind;
        private boolean hover;

        public AppButton(String text, Icons.Glyph glyph, Kind kind) {
            super(text);
            this.kind = kind;
            setUI(new BasicButtonUI() {
                @Override
                protected void paintText(Graphics g, AbstractButton b, Rectangle r, String t) {
                    g.setColor(b.isEnabled() ? b.getForeground() : Theme.DISABLED);
                    FontMetrics fm = g.getFontMetrics();
                    BasicGraphicsUtils.drawStringUnderlineCharAt(b, (Graphics2D) g, t, -1, r.x, r.y + fm.getAscent());
                }
            });
            setContentAreaFilled(false);
            setFocusPainted(true);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.bold(13));
            setBorder(new EmptyBorder(8, 14, 8, 14));
            setIconTextGap(7);
            Color fg = switch (kind) {
                case PRIMARY, DANGER, SUCCESS -> Color.WHITE;
                case SECONDARY, GHOST -> Theme.TEXT;
            };
            setForeground(fg);
            if (glyph != null) {
                setIcon(Icons.of(glyph, 16, fg));
            }
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isEnabled();
            Color bg = switch (kind) {
                case PRIMARY -> hover ? Theme.PRIMARY_HOVER : Theme.PRIMARY;
                case DANGER -> hover ? new Color(0x991B1B) : Theme.DANGER;
                case SUCCESS -> hover ? new Color(0x166534) : Theme.SUCCESS;
                case SECONDARY -> hover ? new Color(0xF1F5F9) : Color.WHITE;
                case GHOST -> hover ? new Color(0xEEF2F7) : new Color(0, 0, 0, 0);
            };
            if (!on) {
                bg = kind == Kind.GHOST ? new Color(0, 0, 0, 0) : new Color(0xEDF0F5);
            }
            g.setColor(bg);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (kind == Kind.SECONDARY) {
                g.setColor(on ? Theme.BORDER_STRONG : Theme.BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            if (isFocusOwner()) {
                g.setColor(new Color(0x93A8F4));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 9, 9);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    public static AppButton primary(String text, Icons.Glyph glyph) {
        return new AppButton(text, glyph, AppButton.Kind.PRIMARY);
    }

    public static AppButton secondary(String text, Icons.Glyph glyph) {
        return new AppButton(text, glyph, AppButton.Kind.SECONDARY);
    }

    public static AppButton danger(String text, Icons.Glyph glyph) {
        return new AppButton(text, glyph, AppButton.Kind.DANGER);
    }

    public static AppButton ghost(String text, Icons.Glyph glyph) {
        return new AppButton(text, glyph, AppButton.Kind.GHOST);
    }

    public static AppButton success(String text, Icons.Glyph glyph) {
        return new AppButton(text, glyph, AppButton.Kind.SUCCESS);
    }

    // ------------------------------------------------------------------ containers

    /** White rounded card with a hairline border. */
    public static class Card extends JPanel {
        private final Color fill;

        public Card(LayoutManager layout) {
            this(layout, Theme.SURFACE);
        }

        public Card(LayoutManager layout, Color fill) {
            super(layout);
            this.fill = fill;
            setOpaque(false);
            setBorder(new EmptyBorder(16, 18, 16, 18));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    public static Card card(LayoutManager layout) {
        return new Card(layout);
    }

    /** Page header: title, subtitle and optional actions on the right. */
    public static JPanel pageHeader(String title, String subtitle, JComponent... actions) {
        JPanel p = new JPanel(new BorderLayout(16, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 0, 16, 0));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(22));
        t.setForeground(Theme.TEXT);
        JLabel s = new JLabel(subtitle);
        s.setFont(Theme.body());
        s.setForeground(Theme.MUTED);
        text.add(t);
        text.add(Box.createVerticalStrut(3));
        text.add(s);
        p.add(text, BorderLayout.CENTER);
        if (actions.length > 0) {
            p.add(row(actions), BorderLayout.EAST);
        }
        return p;
    }

    public static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(15));
        l.setForeground(Theme.TEXT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.small());
        l.setForeground(Theme.MUTED);
        return l;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.TEXT);
        return l;
    }

    /** Horizontal row of components with 8px gaps, transparent background. */
    public static JPanel row(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (JComponent c : items) {
            p.add(c);
        }
        return p;
    }

    public static JPanel transparent(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** Coloured message banner (info/success/warning/error). */
    public static class Banner extends Card {
        private final JLabel text = new JLabel();

        public Banner() {
            super(new BorderLayout(10, 0), Theme.INFO_SOFT);
            setBorder(new EmptyBorder(10, 14, 10, 14));
            text.setFont(Theme.body());
            add(text, BorderLayout.CENTER);
        }

        public void show(String html, Color fg, Color bg, Icons.Glyph glyph) {
            setFill(bg);
            text.setForeground(fg);
            text.setText("<html>" + html + "</html>");
            text.setIcon(glyph == null ? null : Icons.of(glyph, 18, fg));
            text.setIconTextGap(10);
            setVisible(true);
            repaint();
        }

        private Color current = Theme.INFO_SOFT;

        private void setFill(Color c) {
            current = c;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(current);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.dispose();
        }
    }

    /** Friendly placeholder shown when a screen has nothing to display yet. */
    public static JPanel emptyState(Icons.Glyph glyph, String title, String message, JComponent action) {
        JPanel p = transparent(new java.awt.GridBagLayout());
        JPanel inner = transparent(null);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(Icons.of(glyph, 44, Theme.BORDER_STRONG));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(16));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel m = new JLabel("<html><div style='text-align:center;width:320px'>" + message + "</div></html>");
        m.setForeground(Theme.MUTED);
        m.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(icon);
        inner.add(Box.createVerticalStrut(10));
        inner.add(t);
        inner.add(Box.createVerticalStrut(6));
        inner.add(m);
        if (action != null) {
            inner.add(Box.createVerticalStrut(14));
            action.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(action);
        }
        p.add(inner);
        return p;
    }

    // ------------------------------------------------------------------ inputs

    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER_STRONG),
                new EmptyBorder(5, 8, 5, 8));
    }

    public static JTextField textField(int columns) {
        JTextField f = new JTextField(columns);
        f.setBorder(fieldBorder());
        return f;
    }

    /** Search box with a magnifier icon and placeholder text. */
    public static JTextField searchField(String placeholder) {
        JTextField f = new JTextField(18) {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Icons.of(Icons.Glyph.SEARCH, 15, Theme.MUTED).paintIcon(this, g0, 8, (getHeight() - 15) / 2);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(Theme.DISABLED);
                    g.setFont(getFont());
                    Insets in = getInsets();
                    g.drawString(placeholder, in.left, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2);
                    g.dispose();
                }
            }
        };
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER_STRONG),
                new EmptyBorder(6, 30, 6, 8)));
        f.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                f.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                f.repaint();
            }
        });
        f.getAccessibleContext().setAccessibleName(placeholder);
        return f;
    }

    // ------------------------------------------------------------------ tables

    /** Applies the app's table styling: taller rows, light grid, styled header, zebra striping. */
    public static void styleTable(JTable table) {
        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Theme.BORDER);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setBackground(Theme.SURFACE);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setFont(Theme.bold(12));
                l.setForeground(Theme.MUTED);
                l.setBackground(Theme.SURFACE_ALT);
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                        new EmptyBorder(0, 10, 0, 10)));
                return l;
            }
        });
        table.setDefaultRenderer(Object.class, new ZebraRenderer());
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /** Default cell renderer with padding and zebra striping. */
    public static class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, row, col);
            l.setBorder(new EmptyBorder(0, 10, 0, 10));
            if (!sel) {
                l.setBackground(row % 2 == 0 ? Theme.SURFACE : Theme.SURFACE_ALT);
                l.setForeground(Theme.TEXT);
            }
            return l;
        }
    }

    /** Renders AssignmentStatus / ConflictSeverity / ResolutionStatus values as coloured pills. */
    public static class PillRenderer extends ZebraRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            Color[] c = pillColors(v);
            l.setIcon(new PillDot(c[0]));
            l.setForeground(c[0]);
            l.setFont(Theme.bold(12));
            return l;
        }
    }

    public static Color[] pillColors(Object v) {
        if (v instanceof AssignmentStatus s) {
            return switch (s) {
                case ASSIGNED -> new Color[]{Theme.SUCCESS, Theme.SUCCESS_SOFT};
                case UNASSIGNED -> new Color[]{Theme.WARNING, Theme.WARNING_SOFT};
                case UNRESOLVED -> new Color[]{Theme.DANGER, Theme.DANGER_SOFT};
            };
        }
        if (v instanceof ConflictSeverity s) {
            return s == ConflictSeverity.ERROR ? new Color[]{Theme.DANGER, Theme.DANGER_SOFT}
                    : new Color[]{Theme.WARNING, Theme.WARNING_SOFT};
        }
        if (v instanceof ResolutionStatus s) {
            return switch (s) {
                case OPEN -> new Color[]{Theme.DANGER, Theme.DANGER_SOFT};
                case ADDRESSED -> new Color[]{Theme.INFO, Theme.INFO_SOFT};
                case RESOLVED -> new Color[]{Theme.SUCCESS, Theme.SUCCESS_SOFT};
                case ACKNOWLEDGED -> new Color[]{Theme.MUTED, Theme.SURFACE_ALT};
            };
        }
        return new Color[]{Theme.TEXT, Theme.SURFACE_ALT};
    }

    /** Small filled circle used as a status indicator. */
    public static class PillDot implements Icon {
        private final Color color;

        public PillDot(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(x, y + 1, 8, 8);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 9;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }

    /** FlowLayout that wraps onto extra rows and reports the wrapped height, so nothing is clipped. */
    public static class WrapLayout extends FlowLayout {
        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(java.awt.Container target) {
            Dimension d = layoutSize(target, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension layoutSize(java.awt.Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                java.awt.Container container = target;
                while (container.getSize().width == 0 && container.getParent() != null) {
                    container = container.getParent();
                }
                int targetWidth = container.getSize().width == 0 ? Integer.MAX_VALUE : container.getSize().width;
                Insets insets = target.getInsets();
                int edge = insets.left + insets.right + getHgap() * 2;
                int maxWidth = targetWidth - edge;
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;
                for (Component m : target.getComponents()) {
                    if (!m.isVisible()) {
                        continue;
                    }
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth != 0) {
                        rowWidth += getHgap();
                    }
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width += edge;
                dim.height += insets.top + insets.bottom + getVgap() * 2;
                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) {
                dim.height += getVgap();
            }
            dim.height += rowHeight;
        }
    }

    /** A compact coloured chip such as "3 errors". */
    public static JLabel chip(String text, Color fg, Color bg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g.dispose();
                super.paintComponent(g0);
            }
        };
        l.setOpaque(false);
        l.setBackground(bg);
        l.setForeground(fg);
        l.setFont(Theme.bold(12));
        l.setBorder(new EmptyBorder(4, 10, 4, 10));
        return l;
    }

    public static void setChip(JLabel chip, String text, Color fg, Color bg) {
        chip.setText(text);
        chip.setForeground(fg);
        chip.setBackground(bg);
        chip.repaint();
    }
}
