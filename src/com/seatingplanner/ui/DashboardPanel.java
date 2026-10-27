package com.seatingplanner.ui;

import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.ConflictSeverity;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Home screen: headline statistics, plan validity and quick actions. */
public class DashboardPanel extends JPanel implements MainFrame.Screen {

    private final MainFrame app;
    private final JLabel title = new JLabel();
    private final UiKit.Banner banner = new UiKit.Banner();
    private final StatCard capacity = new StatCard("Total capacity", Icons.Glyph.HALL, Theme.PRIMARY);
    private final StatCard registered = new StatCard("Registered attendees", Icons.Glyph.PEOPLE, new Color(0x7C3AED));
    private final StatCard assigned = new StatCard("Assigned attendees", Icons.Glyph.CHECK, Theme.SUCCESS);
    private final StatCard available = new StatCard("Available seats", Icons.Glyph.SEATS, Theme.INFO);
    private final StatCard conflicts = new StatCard("Unresolved conflicts", Icons.Glyph.ALERT, Theme.DANGER);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel progressText = UiKit.muted("");
    private final JLabel accessibleText = new JLabel();
    private final JLabel reservedText = new JLabel();
    private final JLabel conflictText = new JLabel();
    private final JPanel attention = UiKit.transparent(null);

    public DashboardPanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Theme.BG);

        JPanel page = UiKit.transparent(new GridBagLayout());
        page.setBorder(new EmptyBorder(24, 28, 24, 28));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 14, 0);

        title.setFont(Theme.bold(24));
        JPanel header = UiKit.transparent(new BorderLayout());
        header.add(title, BorderLayout.NORTH);
        header.add(UiKit.muted("Overview of the seating plan. Every number below is calculated from the live data."), BorderLayout.SOUTH);
        c.gridy = 0;
        page.add(header, c);

        c.gridy = 1;
        page.add(banner, c);

        JPanel stats = UiKit.transparent(new GridLayout(1, 5, 14, 0));
        stats.add(capacity);
        stats.add(registered);
        stats.add(assigned);
        stats.add(available);
        stats.add(conflicts);
        c.gridy = 2;
        page.add(stats, c);

        JPanel lower = UiKit.transparent(new GridLayout(1, 2, 14, 0));
        lower.add(buildQuickActions());
        lower.add(buildBreakdown());
        c.gridy = 3;
        page.add(lower, c);

        c.gridy = 4;
        page.add(buildAttention(), c);

        c.gridy = 5;
        c.weighty = 1;
        page.add(Box.createGlue(), c);

        JScrollPane sp = new JScrollPane(page);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);
    }

    private JComponent buildQuickActions() {
        UiKit.Card card = UiKit.card(new BorderLayout());
        card.add(UiKit.sectionTitle("Quick actions"), BorderLayout.NORTH);
        JPanel grid = UiKit.transparent(new GridLayout(3, 2, 10, 10));
        UiKit.AppButton hall = UiKit.secondary("Configure hall", Icons.Glyph.HALL);
        hall.addActionListener(e -> app.showScreen(MainFrame.HALL));
        UiKit.AppButton reg = UiKit.secondary("Register attendee", Icons.Glyph.USER_PLUS);
        reg.addActionListener(e -> app.openNewAttendeeDialog());
        UiKit.AppButton gen = UiKit.primary("Generate seating", Icons.Glyph.WAND);
        gen.addActionListener(e -> app.runAutoAssign());
        UiKit.AppButton view = UiKit.secondary("View layout", Icons.Glyph.SEATS);
        view.addActionListener(e -> app.showScreen(MainFrame.LAYOUT));
        UiKit.AppButton export = UiKit.secondary("Export results", Icons.Glyph.EXPORT);
        export.addActionListener(e -> app.showScreen(MainFrame.EXPORT));
        UiKit.AppButton demo = UiKit.ghost("Load demo project", Icons.Glyph.FOLDER);
        demo.addActionListener(e -> app.loadDemo());
        for (JComponent b : new JComponent[]{gen, hall, reg, view, export, demo}) {
            grid.add(b);
        }
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildBreakdown() {
        UiKit.Card card = UiKit.card(new BorderLayout());
        card.add(UiKit.sectionTitle("Assignment progress"), BorderLayout.NORTH);
        JPanel body = UiKit.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        progress.setStringPainted(false);
        progress.setForeground(Theme.SUCCESS);
        progress.setBackground(Theme.BORDER);
        progress.setBorder(BorderFactory.createEmptyBorder());
        progress.setPreferredSize(new Dimension(200, 10));
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        progress.setAlignmentX(LEFT_ALIGNMENT);
        progressText.setAlignmentX(LEFT_ALIGNMENT);
        body.add(progress);
        body.add(Box.createVerticalStrut(6));
        body.add(progressText);
        body.add(Box.createVerticalStrut(14));
        for (JLabel l : new JLabel[]{accessibleText, reservedText, conflictText}) {
            l.setFont(Theme.body());
            l.setForeground(Theme.TEXT);
            l.setAlignmentX(LEFT_ALIGNMENT);
            l.setIconTextGap(8);
            body.add(l);
            body.add(Box.createVerticalStrut(8));
        }
        accessibleText.setIcon(Icons.of(Icons.Glyph.ACCESSIBLE, 16, Theme.SEAT_ACCESSIBLE_BORDER));
        reservedText.setIcon(Icons.of(Icons.Glyph.SHIELD, 16, Theme.SEAT_RESERVED_BORDER));
        conflictText.setIcon(Icons.of(Icons.Glyph.ALERT, 16, Theme.DANGER));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildAttention() {
        UiKit.Card card = UiKit.card(new BorderLayout(0, 4));
        JPanel head = UiKit.transparent(new BorderLayout());
        head.add(UiKit.sectionTitle("Needs attention"), BorderLayout.WEST);
        UiKit.AppButton open = UiKit.ghost("Open conflicts", Icons.Glyph.ARROW_RIGHT);
        open.setHorizontalTextPosition(JLabel.LEFT);
        open.addActionListener(e -> app.showScreen(MainFrame.CONFLICTS));
        head.add(open, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        attention.setLayout(new BoxLayout(attention, BoxLayout.Y_AXIS));
        card.add(attention, BorderLayout.CENTER);
        return card;
    }

    /** Lists the most important open conflicts (errors first). */
    private void refreshAttention(SeatingPlanner planner) {
        attention.removeAll();
        List<Conflict> open = new ArrayList<>();
        for (Conflict c : planner.getConflictManager().getConflicts()) {
            if (c.isOpen()) {
                open.add(c);
            }
        }
        open.sort(Comparator.comparing(Conflict::getSeverity).thenComparing(Conflict::getId));
        if (open.isEmpty()) {
            JLabel ok = new JLabel("Nothing needs attention right now.", Icons.of(Icons.Glyph.CHECK, 16, Theme.SUCCESS), JLabel.LEFT);
            ok.setForeground(Theme.SUCCESS);
            ok.setIconTextGap(8);
            attention.add(ok);
        }
        for (int i = 0; i < Math.min(5, open.size()); i++) {
            Conflict c = open.get(i);
            Color col = c.getSeverity() == ConflictSeverity.ERROR ? Theme.DANGER : Theme.WARNING;
            JLabel l = new JLabel("<html><b>" + c.getId() + " " + c.getSeverity().getLabel() + "</b> &nbsp;"
                    + c.getDescription().replace("<", "&lt;") + "</html>", new UiKit.PillDot(col), JLabel.LEFT);
            l.setIconTextGap(8);
            l.setForeground(Theme.TEXT);
            l.setBorder(new EmptyBorder(3, 0, 3, 0));
            attention.add(l);
        }
        if (open.size() > 5) {
            attention.add(UiKit.muted("... and " + (open.size() - 5) + " more on the Conflicts screen."));
        }
        attention.revalidate();
        attention.repaint();
    }

    @Override
    public void refresh() {
        SeatingPlanner planner = app.planner();
        SeatingPlanner.Stats st = planner.getStats();
        title.setText(planner.hasHall() ? planner.getHall().getEventName() : "Welcome to Inclusive Event Seating Planner");
        capacity.set(String.valueOf(st.capacity()), planner.hasHall()
                ? planner.getHall().getRows() + " rows x " + planner.getHall().getSeatsPerRow() + " seats" : "No hall yet");
        registered.set(String.valueOf(st.registered()), st.registered() > st.assignableCapacity() && planner.hasHall()
                ? "Exceeds assignable seats!" : "Assignable seats: " + st.assignableCapacity());
        assigned.set(String.valueOf(st.assigned()), (st.registered() - st.assigned()) + " unassigned");
        available.set(String.valueOf(st.availableSeats()), st.reservedSeats() + " reserved seat(s) held");
        conflicts.set(String.valueOf(st.unresolved()), st.openErrors() + " error(s), " + st.openWarnings() + " warning(s)");

        int pct = st.registered() == 0 ? 0 : (int) Math.round(100.0 * st.assigned() / st.registered());
        progress.setValue(pct);
        progressText.setText(st.assigned() + " of " + st.registered() + " attendees seated (" + pct + "%)");
        accessibleText.setText("<html><b>" + st.accessibleSeats() + "</b> accessible seat(s), <b>" + st.accessibleFree()
                + "</b> free &nbsp;&middot;&nbsp; <b>" + st.needAccessible() + "</b> attendee(s) need one</html>");
        reservedText.setText("<html><b>" + st.reservedSeats() + "</b> reserved seat(s) held back from automatic assignment</html>");
        refreshAttention(planner);
        conflictText.setText("<html><b>" + st.unresolvedAttendees() + "</b> attendee(s) with unresolved mandatory requirements"
                + (st.awaitingReview() > 0 ? " &nbsp;&middot;&nbsp; <b>" + st.awaitingReview() + "</b> fix(es) awaiting review" : "")
                + "</html>");

        if (!planner.hasHall()) {
            banner.show("<b>Get started:</b> configure the event hall, then register attendees and generate seating. "
                    + "Or load the demo project from Quick actions.", Theme.INFO, Theme.INFO_SOFT, Icons.Glyph.INFO);
        } else if (st.registered() == 0) {
            banner.show("<b>The hall is ready.</b> Register attendees (or import a CSV) to start planning.",
                    Theme.INFO, Theme.INFO_SOFT, Icons.Glyph.INFO);
        } else if (planner.isPlanFullyValid()) {
            banner.show("<b>Seating plan is valid.</b> All attendees are seated and every mandatory accessibility and companion "
                    + "requirement is satisfied." + (st.openWarnings() > 0 ? " " + st.openWarnings() + " preference warning(s) remain." : ""),
                    Theme.SUCCESS, Theme.SUCCESS_SOFT, Icons.Glyph.CHECK);
        } else if (st.openErrors() > 0) {
            banner.show("<b>Plan is not yet valid:</b> " + st.openErrors() + " error(s) involve mandatory requirements or capacity. "
                    + "Review them on the Conflicts screen.", Theme.DANGER, Theme.DANGER_SOFT, Icons.Glyph.ALERT);
        } else {
            banner.show("<b>" + (st.registered() - st.assigned()) + " attendee(s) are not seated yet.</b> "
                    + "Use Generate seating to assign them.", Theme.WARNING, Theme.WARNING_SOFT, Icons.Glyph.INFO);
        }
    }

    /** Dashboard metric card: icon, big number, label and a detail line. */
    private static class StatCard extends UiKit.Card {
        private final JLabel value = new JLabel("0");
        private final JLabel detail = UiKit.muted(" ");

        StatCard(String label, Icons.Glyph glyph, Color accent) {
            super(new BorderLayout(0, 6));
            JLabel l = new JLabel(label, Icons.of(glyph, 18, accent), JLabel.LEFT);
            l.setIconTextGap(8);
            l.setFont(Theme.bold(12));
            l.setForeground(Theme.MUTED);
            value.setFont(Theme.bold(30));
            value.setForeground(Theme.TEXT);
            add(l, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(detail, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(170, 122));
        }

        void set(String v, String d) {
            value.setText(v);
            detail.setText(d);
        }
    }
}
