package com.seatingplanner.ui;

import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.service.ActionResult;
import com.seatingplanner.service.HallConfig;
import com.seatingplanner.service.InvalidConfigurationException;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;

/** Hall configuration form plus a live seat map where individual seat types can be changed. */
public class HallSetupPanel extends JPanel implements MainFrame.Screen {

    private final MainFrame app;
    private final JTextField eventName = UiKit.textField(20);
    private final JSpinner rows = new JSpinner(new SpinnerNumberModel(8, 1, EventHall.MAX_ROWS, 1));
    private final JSpinner seats = new JSpinner(new SpinnerNumberModel(12, 1, EventHall.MAX_SEATS_PER_ROW, 1));
    private final JTextField aisles = UiKit.textField(20);
    private final JTextArea accessible = area();
    private final JTextArea reserved = area();
    private final JLabel errors = new JLabel();
    private final JLabel summary = UiKit.muted(" ");
    private final SeatMapPanel map;

    public HallSetupPanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        add(UiKit.pageHeader("Hall Setup", "Define the layout. Seat IDs are row letter + seat number, e.g. C7."),
                BorderLayout.NORTH);

        // ----- form card
        UiKit.Card form = UiKit.card(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        int y = 0;
        addField(form, c, y++, "Event name *", eventName, null);
        JPanel dims = UiKit.transparent(new java.awt.GridLayout(1, 2, 10, 0));
        dims.add(labelled("Rows *", rows, "1-" + EventHall.MAX_ROWS));
        dims.add(labelled("Seats per row *", seats, "1-" + EventHall.MAX_SEATS_PER_ROW));
        c.gridy = y++;
        c.insets = new Insets(0, 0, 12, 0);
        form.add(dims, c);
        addField(form, c, y++, "Aisles after seat number", aisles, "e.g. \"4, 10\" puts aisles between seats 4|5 and 10|11");
        addField(form, c, y++, "Accessible seats", scroll(accessible), "IDs or ranges, e.g. \"A1-A2, A13, F1\"");
        addField(form, c, y++, "Reserved seats", scroll(reserved),
                "Optionally name the holder: \"A7-A8:Speakers, B7:A003\". An attendee ID seats that attendee there.");

        errors.setForeground(Theme.DANGER);
        errors.setFont(Theme.small());
        c.gridy = y++;
        form.add(errors, c);

        UiKit.AppButton apply = UiKit.primary("Generate layout", Icons.Glyph.HALL);
        apply.addActionListener(e -> applyConfig());
        UiKit.AppButton revert = UiKit.secondary("Revert", Icons.Glyph.UNDO);
        revert.setToolTipText("Discard form changes and show the current hall settings");
        revert.addActionListener(e -> loadFromHall());
        c.gridy = y++;
        c.insets = new Insets(6, 0, 0, 0);
        form.add(UiKit.row(apply, revert), c);
        c.gridy = y;
        c.weighty = 1;
        form.add(UiKit.transparent(null), c);
        form.setPreferredSize(new Dimension(370, form.getPreferredSize().height));

        // ----- map card
        map = new SeatMapPanel(app.planner(), SeatMapPanel.Mode.SETUP);
        map.setSeatListener(this::seatClicked);
        UiKit.Card mapCard = UiKit.card(new BorderLayout(0, 10));
        JPanel mapHead = UiKit.transparent(new BorderLayout());
        mapHead.add(UiKit.sectionTitle("Layout preview"), BorderLayout.WEST);
        mapHead.add(summary, BorderLayout.EAST);
        mapCard.add(mapHead, BorderLayout.NORTH);
        mapCard.add(UiKit.scroll(map), BorderLayout.CENTER);
        JPanel foot = UiKit.transparent(new BorderLayout());
        foot.add(new LegendPanel(false), BorderLayout.NORTH);
        foot.add(UiKit.muted("Tip: click any seat to change its type to Standard, Accessible or Reserved."), BorderLayout.SOUTH);
        mapCard.add(foot, BorderLayout.SOUTH);

        JPanel body = UiKit.transparent(new BorderLayout(16, 0));
        body.add(form, BorderLayout.WEST);
        body.add(mapCard, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        eventName.setText("My Event");
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea(3, 20);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(new EmptyBorder(5, 8, 5, 8));
        return a;
    }

    private static JScrollPane scroll(JTextArea a) {
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER_STRONG));
        return sp;
    }

    private static JPanel labelled(String label, java.awt.Component field, String hint) {
        JPanel p = UiKit.transparent(new BorderLayout(0, 4));
        p.add(UiKit.label(label), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        if (hint != null) {
            p.add(UiKit.muted(hint), BorderLayout.SOUTH);
        }
        return p;
    }

    private static void addField(JPanel form, GridBagConstraints c, int y, String label, java.awt.Component field, String hint) {
        c.gridy = y;
        c.insets = new Insets(0, 0, 12, 0);
        JPanel p = labelled(label, field, hint == null ? null : "<html><div style='width:300px'>" + hint + "</div></html>");
        form.add(p, c);
    }

    @Override
    public void refresh() {
        map.refresh();
        SeatingPlanner p = app.planner();
        if (p.hasHall()) {
            EventHall h = p.getHall();
            summary.setText(h.getCapacity() + " seats  ·  " + h.countSeats(SeatType.ACCESSIBLE) + " accessible  ·  "
                    + h.countSeats(SeatType.RESERVED) + " reserved  ·  " + (h.getAisleAfter().size() + 1) + " block(s)");
        } else {
            summary.setText("No hall yet - fill in the form and click Generate layout");
        }
    }

    /** Called when this screen is shown or a project is loaded: mirror the current hall into the form. */
    public void loadFromHall() {
        errors.setText("");
        SeatingPlanner p = app.planner();
        if (!p.hasHall()) {
            return;
        }
        EventHall h = p.getHall();
        eventName.setText(h.getEventName());
        rows.setValue(h.getRows());
        seats.setValue(h.getSeatsPerRow());
        aisles.setText(h.getAisleAfter().toString().replaceAll("[\\[\\]]", ""));
        accessible.setText(HallConfig.describeSeats(h, SeatType.ACCESSIBLE, false));
        reserved.setText(HallConfig.describeSeats(h, SeatType.RESERVED, true));
    }

    private void applyConfig() {
        HallConfig cfg;
        try {
            commitSpinners();
            cfg = HallConfig.parse(eventName.getText(), (Integer) rows.getValue(), (Integer) seats.getValue(),
                    aisles.getText(), accessible.getText(), reserved.getText());
        } catch (InvalidConfigurationException ex) {
            errors.setText("<html><b>Please fix:</b><br>&bull; " + String.join("<br>&bull; ", ex.getProblems()) + "</html>");
            return;
        }
        errors.setText("");
        SeatingPlanner p = app.planner();
        int lost = p.countAssignmentsAffectedBy(cfg);
        if (p.hasHall() && lost > 0) {
            int ok = JOptionPane.showConfirmDialog(this, lost + " existing seat assignment(s) are not valid in the new layout and will be "
                            + "released.\nAll other assignments are kept. Continue?", "Rebuild hall layout",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
        }
        int assignable = cfg.rows() * cfg.seatsPerRow() - (int) cfg.reservedSeats().values().stream()
                .filter(h -> !p.getProject().containsAttendee(h)).count();
        SeatingPlanner.HallChangeReport report = p.applyHallConfig(cfg);
        StringBuilder msg = new StringBuilder("Layout generated: " + cfg.rows() * cfg.seatsPerRow() + " seats.");
        if (!report.releasedAttendees().isEmpty()) {
            msg.append(" Released ").append(report.releasedAttendees().size()).append(" assignment(s).");
        }
        app.showStatus(msg.toString(), true);
        if (p.getProject().getAttendeeCount() > assignable) {
            JOptionPane.showMessageDialog(this, "Capacity warning: " + p.getProject().getAttendeeCount() + " attendees are registered but only "
                    + assignable + " seats can be assigned.\nThis is recorded as a conflict.", "Capacity", JOptionPane.WARNING_MESSAGE);
        }
        loadFromHall();
    }

    private void commitSpinners() {
        try {
            rows.commitEdit();
            seats.commitEdit();
        } catch (java.text.ParseException ignored) {
            // keep the last valid values
        }
    }

    private void seatClicked(Seat seat, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JLabel head = new JLabel("  Seat " + seat.getId() + " - currently " + seat.getType().getLabel());
        head.setFont(Theme.bold(12));
        head.setBorder(new EmptyBorder(4, 4, 6, 4));
        menu.add(head);
        menu.addSeparator();
        for (SeatType t : SeatType.values()) {
            JMenuItem item = new JMenuItem("Make " + t.getLabel().toLowerCase());
            item.setEnabled(t != seat.getType() || t == SeatType.RESERVED);
            if (t == SeatType.RESERVED && seat.getType() == SeatType.RESERVED) {
                item.setText("Change reservation holder...");
            }
            item.addActionListener(ev -> changeType(seat, t));
            menu.add(item);
        }
        menu.show(map, e.getX(), e.getY());
    }

    private void changeType(Seat seat, SeatType type) {
        String holder = "";
        if (type == SeatType.RESERVED) {
            Object in = JOptionPane.showInputDialog(this, "Reserve seat " + seat.getId() + " for (attendee ID or label, optional):",
                    "Reserve seat", JOptionPane.PLAIN_MESSAGE, null, null, seat.getReservedFor());
            if (in == null) {
                return;
            }
            holder = in.toString().trim();
            if (holder.length() > 40) {
                JOptionPane.showMessageDialog(this, "The reservation label must be 40 characters or fewer.", "Reserve seat",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        ActionResult r = app.planner().setSeatType(seat.getId(), type, holder);
        if (r.isSuccess()) {
            app.showStatus(r.getMessage(), true);
            loadFromHall();
        } else {
            JOptionPane.showMessageDialog(this, r.getMessage(), "Change rejected", JOptionPane.WARNING_MESSAGE);
        }
    }
}
