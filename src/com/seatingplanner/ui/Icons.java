package com.seatingplanner.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Resolution-independent line icons drawn with Java2D on a 24x24 grid (no image files needed). */
public final class Icons implements Icon {

    public enum Glyph {
        DASHBOARD, HALL, PEOPLE, SEATS, ALERT, PLAN, EXPORT, PLUS, EDIT, TRASH, SEARCH, WAND, SAVE, FOLDER,
        CHECK, RESET, SWAP, PRINT, INFO, USER_PLUS, UNDO, ARROW_RIGHT, FILE, ACCESSIBLE, SHIELD
    }

    private final Glyph glyph;
    private final int size;
    private final Color color;

    public Icons(Glyph glyph, int size, Color color) {
        this.glyph = glyph;
        this.size = size;
        this.color = color;
    }

    public static Icons of(Glyph glyph, int size, Color color) {
        return new Icons(glyph, size, color);
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        Color col = c != null && !c.isEnabled() ? Theme.DISABLED : color;
        g.setColor(col);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        draw(g);
        g.dispose();
    }

    private void draw(Graphics2D g) {
        switch (glyph) {
            case DASHBOARD -> {
                g.draw(rr(3, 3, 8, 9));
                g.draw(rr(13, 3, 8, 5));
                g.draw(rr(13, 10, 8, 11));
                g.draw(rr(3, 14, 8, 7));
            }
            case HALL -> {
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        g.draw(rr(3.5 + c * 6.2, 5 + r * 5.8, 4.6, 3.6));
                    }
                }
                g.draw(new Line2D.Double(4, 2.5, 20, 2.5));
            }
            case PEOPLE -> {
                g.draw(new Ellipse2D.Double(5.5, 4, 6, 6));
                g.draw(new Arc2D.Double(2.5, 13, 12, 12, 0, 180, Arc2D.OPEN));
                g.draw(new Ellipse2D.Double(14, 5, 5, 5));
                g.draw(new Arc2D.Double(14, 13.5, 8, 9, 20, 160, Arc2D.OPEN));
            }
            case SEATS -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 3);
                p.lineTo(6, 13);
                p.lineTo(18, 13);
                p.moveTo(6, 13);
                p.lineTo(6, 21);
                p.moveTo(18, 13);
                p.lineTo(18, 21);
                p.moveTo(6, 17);
                p.lineTo(18, 17);
                g.draw(p);
                g.draw(rr(5, 3, 3, 10));
            }
            case ALERT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3);
                p.lineTo(22, 20);
                p.lineTo(2, 20);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(12, 9, 12, 14));
                g.fill(new Ellipse2D.Double(10.8, 16, 2.4, 2.4));
            }
            case PLAN, FILE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 2.5);
                p.lineTo(14, 2.5);
                p.lineTo(19, 7.5);
                p.lineTo(19, 21.5);
                p.lineTo(6, 21.5);
                p.closePath();
                g.draw(p);
                if (glyph == Glyph.PLAN) {
                    g.draw(new Line2D.Double(9, 11, 16, 11));
                    g.draw(new Line2D.Double(9, 14.5, 16, 14.5));
                    g.draw(new Line2D.Double(9, 18, 13, 18));
                }
            }
            case EXPORT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 14);
                p.lineTo(4, 20);
                p.lineTo(20, 20);
                p.lineTo(20, 14);
                g.draw(p);
                g.draw(new Line2D.Double(12, 3.5, 12, 15));
                Path2D a = new Path2D.Double();
                a.moveTo(7.5, 8);
                a.lineTo(12, 3.5);
                a.lineTo(16.5, 8);
                g.draw(a);
            }
            case PLUS -> {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case EDIT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 20);
                p.lineTo(5, 15.5);
                p.lineTo(16, 4.5);
                p.lineTo(19.5, 8);
                p.lineTo(8.5, 19);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(13.5, 7, 17, 10.5));
            }
            case TRASH -> {
                g.draw(new Line2D.Double(4, 6.5, 20, 6.5));
                g.draw(new Line2D.Double(9.5, 3.5, 14.5, 3.5));
                Path2D p = new Path2D.Double();
                p.moveTo(6, 6.5);
                p.lineTo(7, 20.5);
                p.lineTo(17, 20.5);
                p.lineTo(18, 6.5);
                g.draw(p);
                g.draw(new Line2D.Double(10, 10.5, 10, 16.5));
                g.draw(new Line2D.Double(14, 10.5, 14, 16.5));
            }
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
            }
            case WAND -> {
                g.draw(new Line2D.Double(4, 20, 15, 9));
                star(g, 17, 5.5, 3);
                star(g, 8, 5, 2);
                star(g, 19.5, 14, 2);
            }
            case SAVE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 3.5);
                p.lineTo(17, 3.5);
                p.lineTo(20.5, 7);
                p.lineTo(20.5, 20.5);
                p.lineTo(4, 20.5);
                p.closePath();
                g.draw(p);
                g.draw(rr(8, 3.5, 7, 5));
                g.draw(rr(7.5, 13, 9, 7.5));
            }
            case FOLDER -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 6);
                p.lineTo(9, 6);
                p.lineTo(11, 8.5);
                p.lineTo(21, 8.5);
                p.lineTo(21, 19.5);
                p.lineTo(3, 19.5);
                p.closePath();
                g.draw(p);
            }
            case CHECK -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 12.5);
                p.lineTo(9.5, 17.5);
                p.lineTo(19.5, 6.5);
                g.draw(p);
            }
            case RESET, UNDO -> {
                g.draw(new Arc2D.Double(4, 4, 16, 16, 120, 270, Arc2D.OPEN));
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 4.5);
                p.lineTo(6.5, 8.5);
                p.lineTo(10.5, 6);
                g.draw(p);
            }
            case SWAP -> {
                g.draw(new Line2D.Double(4, 8, 19, 8));
                g.draw(new Line2D.Double(15.5, 4.5, 19, 8));
                g.draw(new Line2D.Double(15.5, 11.5, 19, 8));
                g.draw(new Line2D.Double(5, 16, 20, 16));
                g.draw(new Line2D.Double(8.5, 12.5, 5, 16));
                g.draw(new Line2D.Double(8.5, 19.5, 5, 16));
            }
            case PRINT -> {
                g.draw(rr(3, 9, 18, 8));
                g.draw(new Line2D.Double(7, 9, 7, 3.5));
                g.draw(new Line2D.Double(7, 3.5, 17, 3.5));
                g.draw(new Line2D.Double(17, 3.5, 17, 9));
                g.draw(rr(7, 14, 10, 7));
            }
            case INFO -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Line2D.Double(12, 11, 12, 16.5));
                g.fill(new Ellipse2D.Double(10.9, 6.8, 2.2, 2.2));
            }
            case USER_PLUS -> {
                g.draw(new Ellipse2D.Double(5, 4, 7, 7));
                g.draw(new Arc2D.Double(2, 14, 13, 13, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(19, 7, 19, 15));
                g.draw(new Line2D.Double(15, 11, 23, 11));
            }
            case ARROW_RIGHT -> {
                g.draw(new Line2D.Double(4, 12, 19, 12));
                g.draw(new Line2D.Double(13.5, 6.5, 19, 12));
                g.draw(new Line2D.Double(13.5, 17.5, 19, 12));
            }
            case ACCESSIBLE -> {
                g.fill(new Ellipse2D.Double(10, 1.5, 4, 4));
                g.draw(new Line2D.Double(12, 7, 12, 13));
                g.draw(new Line2D.Double(7.5, 9, 16.5, 9));
                g.draw(new Line2D.Double(12, 13, 17, 13));
                g.draw(new Line2D.Double(17, 13, 19, 19));
                g.draw(new Arc2D.Double(3.5, 10, 11, 11, 100, 250, Arc2D.OPEN));
            }
            case SHIELD -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 2.5);
                p.lineTo(20, 5.5);
                p.curveTo(20, 13, 17, 18.5, 12, 21.5);
                p.curveTo(7, 18.5, 4, 13, 4, 5.5);
                p.closePath();
                g.draw(p);
                Path2D t = new Path2D.Double();
                t.moveTo(8.5, 12);
                t.lineTo(11, 14.5);
                t.lineTo(15.5, 9.5);
                g.draw(t);
            }
        }
    }

    private static RoundRectangle2D rr(double x, double y, double w, double h) {
        return new RoundRectangle2D.Double(x, y, w, h, 2.5, 2.5);
    }

    private static void star(Graphics2D g, double cx, double cy, double r) {
        g.draw(new Line2D.Double(cx - r, cy, cx + r, cy));
        g.draw(new Line2D.Double(cx, cy - r, cx, cy + r));
    }
}
