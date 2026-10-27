package com.seatingplanner.ui;

import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Interactive drawing of the hall: stage at the top, rows A.. from the front, aisles as gaps.
 * Seat colour shows the type (standard / accessible / reserved) and fills blue when occupied;
 * small corner letters (A / R) repeat the type so colour is never the only cue.
 */
public class SeatMapPanel extends JComponent implements Scrollable {

    public enum Mode { SETUP, EDIT, PLAN }

    public interface SeatListener {
        void seatClicked(Seat seat, MouseEvent e);
    }

    private static final int MARGIN = 16;
    private static final int LABEL_W = 30;
    private static final int STAGE_H = 34;

    private final SeatingPlanner planner;
    private final Mode mode;
    private double zoom = 1.0;
    private String selectedSeatId;
    private Set<String> highlighted = Set.of();
    private Predicate<Seat> filter;
    private Seat hover;
    private SeatListener listener;

    public SeatMapPanel(SeatingPlanner planner, Mode mode) {
        this.planner = planner;
        this.mode = mode;
        setOpaque(true);
        setBackground(Theme.SURFACE);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Seat s = seatAt(e.getPoint());
                if (s != hover) {
                    hover = s;
                    setCursor(java.awt.Cursor.getPredefinedCursor(s == null ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = null;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Seat s = seatAt(e.getPoint());
                if (s != null && listener != null) {
                    listener.seatClicked(s, e);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        getAccessibleContext().setAccessibleName("Seat map");
    }

    /** Plain JComponents have no accessible context; give the map one so screen readers can name it. */
    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.CANVAS;
                }
            };
        }
        return accessibleContext;
    }

    public void setSeatListener(SeatListener listener) {
        this.listener = listener;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.5, Math.min(2.0, zoom));
        revalidate();
        repaint();
    }

    public double getZoom() {
        return zoom;
    }

    public void setSelectedSeat(String seatId) {
        this.selectedSeatId = seatId;
        repaint();
    }

    public String getSelectedSeatId() {
        return selectedSeatId;
    }

    public void setHighlighted(Set<String> seatIds) {
        this.highlighted = seatIds == null ? Set.of() : seatIds;
        repaint();
    }

    /** Seats not matching the filter are drawn faded. Null shows everything. */
    public void setFilter(Predicate<Seat> filter) {
        this.filter = filter;
        repaint();
    }

    public void refresh() {
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ geometry

    private int seatW() {
        return (int) Math.round((mode == Mode.PLAN ? 76 : mode == Mode.EDIT ? 52 : 44) * zoom);
    }

    private int seatH() {
        return (int) Math.round((mode == Mode.PLAN ? 40 : 36) * zoom);
    }

    private int gap() {
        return (int) Math.max(3, Math.round(5 * zoom));
    }

    private int aisleW() {
        return (int) Math.round(22 * zoom);
    }

    private int gridTop() {
        return MARGIN + STAGE_H + 26;
    }

    private int seatX(EventHall hall, int number) {
        int x = MARGIN + LABEL_W;
        x += (number - 1) * (seatW() + gap());
        x += hall.blockOf(number) * aisleW();
        return x;
    }

    private int gridWidth(EventHall hall) {
        return seatX(hall, hall.getSeatsPerRow()) + seatW() - (MARGIN + LABEL_W);
    }

    /** Seat bounds in drawing coordinates (before the horizontal centring offset). */
    private Rectangle seatRect(Seat s) {
        EventHall hall = planner.getHall();
        int y = gridTop() + (s.getRow() - 1) * (seatH() + gap());
        return new Rectangle(seatX(hall, s.getNumber()), y, seatW(), seatH());
    }

    /** Seat bounds in component coordinates, e.g. for scrollRectToVisible. */
    public Rectangle seatBounds(Seat s) {
        Rectangle r = seatRect(s);
        r.translate(centringOffset(), 0);
        return r;
    }

    /** Horizontal offset used to centre the map when the viewport is wider than the hall. */
    private int centringOffset() {
        return planner.getHall() == null ? 0 : Math.max(0, (getWidth() - getPreferredSize().width) / 2);
    }

    /** The seat under a point in component coordinates, or null. */
    public Seat seatAt(Point point) {
        EventHall hall = planner.getHall();
        if (hall == null) {
            return null;
        }
        Point p = new Point(point.x - centringOffset(), point.y);
        int row = (p.y - gridTop()) / (seatH() + gap()) + 1;
        if (p.y < gridTop() || row < 1 || row > hall.getRows()) {
            return null;
        }
        for (Seat s : hall.getRowSeats(row)) {
            if (seatRect(s).contains(p)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        EventHall hall = planner.getHall();
        if (hall == null) {
            return new Dimension(400, 240);
        }
        int w = MARGIN * 2 + LABEL_W * 2 + gridWidth(hall);
        int h = gridTop() + hall.getRows() * (seatH() + gap()) + MARGIN;
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        EventHall hall = planner.getHall();
        if (hall == null) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.body());
            g.drawString("No hall configured yet. Use Hall Setup to create the layout.", MARGIN, MARGIN + 20);
            g.dispose();
            return;
        }
        int left = MARGIN + LABEL_W;
        int width = gridWidth(hall);
        g.translate(centringOffset(), 0);

        g.setColor(Theme.STAGE);
        g.fillRoundRect(left, MARGIN, width, STAGE_H, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(Theme.bold(12));
        String stage = "STAGE  •  FRONT OF HALL";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(stage, left + (width - fm.stringWidth(stage)) / 2, MARGIN + (STAGE_H + fm.getAscent()) / 2 - 2);

        // seat numbers
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.setColor(Theme.MUTED);
        fm = g.getFontMetrics();
        for (int n = 1; n <= hall.getSeatsPerRow(); n++) {
            String t = String.valueOf(n);
            g.drawString(t, seatX(hall, n) + (seatW() - fm.stringWidth(t)) / 2, gridTop() - 8);
        }

        // aisles
        g.setColor(new Color(0xF1F5F9));
        for (int a : hall.getAisleAfter()) {
            int x = seatX(hall, a) + seatW() + gap() / 2;
            g.fillRect(x, gridTop() - 4, aisleW() - gap() / 2, hall.getRows() * (seatH() + gap()));
        }

        for (int r = 1; r <= hall.getRows(); r++) {
            int y = gridTop() + (r - 1) * (seatH() + gap());
            g.setFont(Theme.bold(12));
            g.setColor(Theme.MUTED);
            fm = g.getFontMetrics();
            String label = EventHall.rowLabel(r);
            int ty = y + (seatH() + fm.getAscent()) / 2 - 2;
            g.drawString(label, MARGIN + (LABEL_W - fm.stringWidth(label)) / 2 - 4, ty);
            g.drawString(label, left + width + 8, ty);
            for (Seat s : hall.getRowSeats(r)) {
                drawSeat(g, s);
            }
        }
        g.dispose();
    }

    private void drawSeat(Graphics2D g, Seat s) {
        Rectangle r = seatRect(s);
        Attendee occ = planner.occupantOf(s);
        boolean dim = filter != null && !filter.test(s);
        Composite old = g.getComposite();
        if (dim) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        }
        Color fill;
        Color border;
        switch (s.getType()) {
            case ACCESSIBLE -> {
                fill = Theme.SEAT_ACCESSIBLE;
                border = Theme.SEAT_ACCESSIBLE_BORDER;
            }
            case RESERVED -> {
                fill = Theme.SEAT_RESERVED;
                border = Theme.SEAT_RESERVED_BORDER;
            }
            default -> {
                fill = Theme.SEAT_FREE;
                border = Theme.SEAT_FREE_BORDER;
            }
        }
        boolean occupied = occ != null;
        if (occupied) {
            fill = Theme.SEAT_OCCUPIED;
        }
        if (s == hover && !dim) {
            fill = occupied ? Theme.PRIMARY_HOVER : fill.darker();
            if (!occupied) {
                fill = blend(fill, Color.WHITE, 0.55f);
            }
        }
        int arc = (int) (10 * zoom);
        g.setColor(fill);
        g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        g.setColor(border);
        g.setStroke(new BasicStroke(s.getType() == SeatType.STANDARD ? 1f : 2f));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);

        // type marker letter (non-colour cue)
        if (s.getType() != SeatType.STANDARD) {
            g.setFont(Theme.bold(Math.max(8, (float) (9 * zoom))));
            g.setColor(occupied ? Color.WHITE : border);
            g.drawString(s.getType() == SeatType.ACCESSIBLE ? "A" : "R", r.x + r.width - (int) (10 * zoom), r.y + (int) (11 * zoom));
        }
        // red dot = attendee with an unresolved mandatory requirement
        if (occupied && planner.statusOf(occ) == AssignmentStatus.UNRESOLVED) {
            g.setColor(new Color(0xEF4444));
            int d = (int) Math.max(6, 8 * zoom);
            g.fillOval(r.x + 3, r.y + 3, d, d);
            g.setColor(Color.WHITE);
            g.drawOval(r.x + 3, r.y + 3, d, d);
        }

        drawSeatText(g, s, r, occ);

        if (highlighted.contains(s.getId())) {
            g.setColor(Theme.SEAT_HIGHLIGHT);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(r.x - 3, r.y - 3, r.width + 5, r.height + 5, arc + 4, arc + 4);
        }
        if (s.getId().equals(selectedSeatId)) {
            g.setColor(Theme.SEAT_SELECTED);
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(r.x - 3, r.y - 3, r.width + 5, r.height + 5, arc + 4, arc + 4);
        }
        g.setStroke(new BasicStroke(1f));
        g.setComposite(old);
    }

    private void drawSeatText(Graphics2D g, Seat s, Rectangle r, Attendee occ) {
        Color fg = occ != null ? Color.WHITE : Theme.TEXT;
        String main;
        String sub = null;
        if (mode == Mode.PLAN) {
            main = occ != null ? occ.getShortName() : String.valueOf(s.getNumber());
            sub = occ != null ? occ.getId() : (s.getType() == SeatType.RESERVED && !s.getReservedFor().isEmpty() ? s.getReservedFor() : null);
        } else if (mode == Mode.EDIT) {
            main = occ != null ? occ.getId() : s.getId();
            sub = occ != null ? s.getId() : null;
        } else {
            main = s.getId();
        }
        float size = (float) Math.max(8, (mode == Mode.PLAN ? 10.5 : 11) * zoom);
        g.setFont(Theme.font(occ != null ? Font.BOLD : Font.PLAIN, size));
        if (occ == null && mode != Mode.SETUP) {
            fg = Theme.MUTED;
        }
        g.setColor(fg);
        FontMetrics fm = g.getFontMetrics();
        main = fit(main, fm, r.width - 6);
        if (sub == null) {
            g.drawString(main, r.x + (r.width - fm.stringWidth(main)) / 2, r.y + (r.height + fm.getAscent()) / 2 - 2);
        } else {
            int y1 = r.y + r.height / 2 - 1;
            g.drawString(main, r.x + (r.width - fm.stringWidth(main)) / 2, y1);
            g.setFont(Theme.font(Font.PLAIN, Math.max(7.5f, size - 2)));
            FontMetrics fm2 = g.getFontMetrics();
            g.setColor(occ != null ? new Color(0xDCE3FF) : Theme.MUTED);
            String subText = fit(sub, fm2, r.width - 6);
            g.drawString(subText, r.x + (r.width - fm2.stringWidth(subText)) / 2, y1 + fm2.getAscent() + 1);
        }
    }

    private static String fit(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "…") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public String getToolTipText(MouseEvent e) {
        Seat s = seatAt(e.getPoint());
        if (s == null) {
            return null;
        }
        EventHall hall = planner.getHall();
        StringBuilder sb = new StringBuilder("<html><b>Seat ").append(s.getId()).append("</b> &nbsp;Row ")
                .append(s.getRowLabel()).append(", seat ").append(s.getNumber())
                .append("<br>Type: ").append(s.getType().getLabel());
        if (s.getType() == SeatType.RESERVED && !s.getReservedFor().isEmpty()) {
            sb.append(" (for ").append(esc(s.getReservedFor())).append(")");
        }
        sb.append("<br>Section: ").append(hall.areaOf(s.getRow()).getLabel())
                .append(hall.isAisleSeat(s) ? " &middot; aisle seat" : "")
                .append("<br>Status: ").append(s.getOccupancyStatus().getLabel());
        Attendee occ = planner.occupantOf(s);
        if (occ != null) {
            sb.append("<hr><b>").append(esc(occ.getFullName())).append("</b> (").append(occ.getId()).append(")");
            if (occ.hasGroup()) {
                sb.append("<br>Group: ").append(esc(occ.getGroupId()));
            }
            if (occ.hasAccessibilityNeed()) {
                sb.append("<br>Needs: ").append(occ.getAccessibilityNeed().getLabel());
            }
            if (occ.isRequiresCompanion()) {
                sb.append("<br>Companion: ").append(esc(occ.getCompanionId()));
            }
            sb.append("<br>Status: ").append(planner.statusOf(occ).getLabel());
        }
        return sb.append("</html>").toString();
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return new Point(e.getX() + 14, e.getY() + 18);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height - 40 : visible.width - 40;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() != null && getParent().getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
}
