package com.seatingplanner.ui;

import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.Seat;
import com.seatingplanner.model.SeatType;
import com.seatingplanner.service.ActionResult;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manual seating editor. Pick an attendee on the left and a seat on the map, then assign, move,
 * swap or unassign. Every action is validated by the planner; rejected actions explain why.
 */
public class LayoutEditorPanel extends JPanel implements MainFrame.Screen {

    private final MainFrame app;
    private final SeatMapPanel map;
    private final DefaultListModel<Attendee> listModel = new DefaultListModel<>();
    private final JList<Attendee> list = new JList<>(listModel);
    private final JTextField search = UiKit.searchField("Find attendee");
    private final JComboBox<String> filter = new JComboBox<>(new String[]{"All", "Unassigned", "Unresolved", "Needs accommodation"});
    private final JLabel attendeeInfo = new JLabel();
    private final JLabel seatInfo = new JLabel();
    private final UiKit.Banner feedback = new UiKit.Banner();
    private final UiKit.AppButton assignBtn = UiKit.primary("Assign", Icons.Glyph.ARROW_RIGHT);
    private final UiKit.AppButton swapBtn = UiKit.secondary("Swap seats", Icons.Glyph.SWAP);
    private final UiKit.AppButton unassignBtn = UiKit.secondary("Unassign", Icons.Glyph.UNDO);
    private final UiKit.AppButton occupantBtn = UiKit.secondary("Pick occupant", Icons.Glyph.PEOPLE);
    private final CardLayout mapCards = new CardLayout();
    private final JPanel mapHolder = UiKit.transparent(mapCards);
    private String selectedAttendee;
    private String selectedSeat;
    private boolean updatingList;

    public LayoutEditorPanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        this.map = new SeatMapPanel(app.planner(), SeatMapPanel.Mode.EDIT);
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        UiKit.AppButton auto = UiKit.primary("Auto-assign", Icons.Glyph.WAND);
        auto.addActionListener(e -> app.runAutoAssign());
        UiKit.AppButton reset = UiKit.danger("Reset assignments", Icons.Glyph.RESET);
        reset.addActionListener(e -> resetAll());
        add(UiKit.pageHeader("Seating Layout", "Pick an attendee and a seat, then assign, move or swap. Right-click a seat for actions.",
                reset, auto), BorderLayout.NORTH);

        // ---- left: attendee list
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AttendeeCell());
        list.setFixedCellHeight(46);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingList) {
                Attendee a = list.getSelectedValue();
                selectedAttendee = a == null ? null : a.getId();
                if (a != null && app.planner().seatOf(a) != null) {
                    Seat s = app.planner().seatOf(a);
                    map.scrollRectToVisible(map.seatBounds(s));
                }
                updateSelection();
            }
        });
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                reloadList();
            }

            public void removeUpdate(DocumentEvent e) {
                reloadList();
            }

            public void changedUpdate(DocumentEvent e) {
                reloadList();
            }
        };
        search.getDocument().addDocumentListener(dl);
        filter.addActionListener(e -> reloadList());
        UiKit.Card left = UiKit.card(new BorderLayout(0, 8));
        left.setPreferredSize(new Dimension(250, 400));
        JPanel leftTop = UiKit.transparent(new GridLayout(3, 1, 0, 6));
        leftTop.add(UiKit.sectionTitle("Attendees"));
        leftTop.add(search);
        leftTop.add(filter);
        left.add(leftTop, BorderLayout.NORTH);
        JScrollPane listScroll = UiKit.scroll(list);
        left.add(listScroll, BorderLayout.CENTER);

        // ---- centre: map
        map.setSeatListener(this::seatClicked);
        JSlider zoom = new JSlider(50, 160, 100);
        zoom.setOpaque(false);
        zoom.setPreferredSize(new Dimension(140, 24));
        zoom.addChangeListener(e -> map.setZoom(zoom.getValue() / 100.0));
        zoom.getAccessibleContext().setAccessibleName("Zoom");
        UiKit.Card mapCard = UiKit.card(new BorderLayout(0, 8));
        JPanel mapTop = UiKit.transparent(new BorderLayout());
        mapTop.add(UiKit.sectionTitle("Hall"), BorderLayout.WEST);
        mapTop.add(UiKit.row(UiKit.muted("Zoom"), zoom), BorderLayout.EAST);
        mapCard.add(mapTop, BorderLayout.NORTH);
        UiKit.AppButton goHall = UiKit.primary("Configure hall", Icons.Glyph.HALL);
        goHall.addActionListener(e -> app.showScreen(MainFrame.HALL));
        mapHolder.add(UiKit.scroll(map), "map");
        mapHolder.add(UiKit.emptyState(Icons.Glyph.HALL, "No hall yet", "Create the hall layout before assigning seats.", goHall), "empty");
        mapCard.add(mapHolder, BorderLayout.CENTER);
        mapCard.add(new LegendPanel(true), BorderLayout.SOUTH);

        // ---- bottom strip: selection details & actions (keeps the map as wide as possible)
        UiKit.Card selection = UiKit.card(new BorderLayout(0, 10));
        selection.setBorder(new EmptyBorder(12, 14, 12, 14));
        attendeeInfo.setVerticalAlignment(JLabel.TOP);
        seatInfo.setVerticalAlignment(JLabel.TOP);
        JPanel actions = UiKit.transparent(new GridLayout(2, 2, 8, 8));
        assignBtn.addActionListener(e -> assign());
        swapBtn.addActionListener(e -> swap());
        unassignBtn.addActionListener(e -> unassign());
        occupantBtn.addActionListener(e -> selectOccupant());
        actions.add(assignBtn);
        actions.add(swapBtn);
        actions.add(unassignBtn);
        actions.add(occupantBtn);
        JPanel infoBoxes = UiKit.transparent(new GridLayout(1, 2, 12, 0));
        infoBoxes.add(box("Selected attendee", attendeeInfo));
        infoBoxes.add(box("Selected seat", seatInfo));
        JPanel actionBox = UiKit.transparent(new BorderLayout());
        actionBox.add(actions, BorderLayout.NORTH);
        actionBox.setPreferredSize(new Dimension(330, 120));
        JPanel strip = UiKit.transparent(new BorderLayout(12, 0));
        strip.add(infoBoxes, BorderLayout.CENTER);
        strip.add(actionBox, BorderLayout.EAST);
        strip.setPreferredSize(new Dimension(600, 128));
        selection.add(strip, BorderLayout.CENTER);
        feedback.setVisible(false);
        selection.add(feedback, BorderLayout.SOUTH);

        JPanel centerCol = UiKit.transparent(new BorderLayout(0, 12));
        centerCol.add(mapCard, BorderLayout.CENTER);
        centerCol.add(selection, BorderLayout.SOUTH);

        JPanel body = UiKit.transparent(new BorderLayout(14, 0));
        body.add(left, BorderLayout.WEST);
        body.add(centerCol, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private static JPanel box(String title, JLabel content) {
        JPanel p = UiKit.transparent(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(10, 12, 10, 12)));
        JLabel t = UiKit.label(title.toUpperCase());
        t.setForeground(Theme.MUTED);
        t.setFont(Theme.bold(11));
        p.add(t, BorderLayout.NORTH);
        content.setFont(Theme.body());
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refresh() {
        mapCards.show(mapHolder, app.planner().hasHall() ? "map" : "empty");
        if (selectedSeat != null && (app.planner().getHall() == null || app.planner().getHall().getSeat(selectedSeat) == null)) {
            selectedSeat = null;
        }
        if (selectedAttendee != null && !app.planner().getProject().containsAttendee(selectedAttendee)) {
            selectedAttendee = null;
        }
        reloadList();
        map.refresh();
        updateSelection();
    }

    /** Selects an attendee (e.g. from another screen) and scrolls their seat into view. */
    public void focusAttendee(String id) {
        search.setText("");
        filter.setSelectedIndex(0);
        selectedAttendee = id;
        reloadList();
        Attendee a = app.planner().getProject().getAttendee(id);
        if (a != null && app.planner().seatOf(a) != null) {
            selectedSeat = app.planner().seatOf(a).getId();
            SwingUtilities.invokeLater(() -> map.scrollRectToVisible(map.seatBounds(app.planner().seatOf(a))));
        }
        updateSelection();
    }

    private void reloadList() {
        SeatingPlanner p = app.planner();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        int mode = filter.getSelectedIndex();
        updatingList = true;
        listModel.clear();
        int selIndex = -1;
        for (Attendee a : p.getProject().getAttendees()) {
            boolean match = q.isEmpty() || a.getId().toLowerCase(Locale.ROOT).contains(q)
                    || a.getFullName().toLowerCase(Locale.ROOT).contains(q) || a.getGroupId().toLowerCase(Locale.ROOT).contains(q);
            boolean modeOk = switch (mode) {
                case 1 -> p.seatOf(a) == null;
                case 2 -> p.statusOf(a) == AssignmentStatus.UNRESOLVED;
                case 3 -> a.hasAccessibilityNeed() || a.isRequiresCompanion();
                default -> true;
            };
            if (match && modeOk) {
                if (a.getId().equals(selectedAttendee)) {
                    selIndex = listModel.size();
                }
                listModel.addElement(a);
            }
        }
        if (selIndex >= 0) {
            list.setSelectedIndex(selIndex);
            list.ensureIndexIsVisible(selIndex);
        } else {
            list.clearSelection();
        }
        updatingList = false;
    }

    private void seatClicked(Seat seat, MouseEvent e) {
        selectedSeat = seat.getId();
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
            updateSelection();
            showSeatMenu(seat, e);
            return;
        }
        if (e.getClickCount() == 2) {
            if (seat.isOccupied()) {
                selectOccupant();
            } else if (selectedAttendee != null) {
                assign();
            }
            return;
        }
        updateSelection();
    }

    private void showSeatMenu(Seat seat, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        Attendee sel = selectedAttendeeObj();
        Attendee occ = app.planner().occupantOf(seat);
        if (sel != null && occ == null) {
            JMenuItem it = new JMenuItem((app.planner().seatOf(sel) == null ? "Assign " : "Move ") + sel.getId() + " here");
            it.addActionListener(ev -> assign());
            menu.add(it);
        }
        if (sel != null && occ != null && !occ.getId().equals(sel.getId()) && app.planner().seatOf(sel) != null) {
            JMenuItem it = new JMenuItem("Swap " + sel.getId() + " with " + occ.getId());
            it.addActionListener(ev -> swap());
            menu.add(it);
        }
        if (occ != null) {
            JMenuItem pick = new JMenuItem("Select " + occ.getId() + " (" + occ.getShortName() + ")");
            pick.addActionListener(ev -> selectOccupant());
            menu.add(pick);
            JMenuItem un = new JMenuItem("Unassign " + occ.getId());
            un.addActionListener(ev -> {
                selectOccupant();
                unassign();
            });
            menu.add(un);
        }
        if (menu.getComponentCount() == 0) {
            JMenuItem none = new JMenuItem("Select an attendee first");
            none.setEnabled(false);
            menu.add(none);
        }
        menu.show(map, e.getX(), e.getY());
    }

    private Attendee selectedAttendeeObj() {
        return selectedAttendee == null ? null : app.planner().getProject().getAttendee(selectedAttendee);
    }

    private Seat selectedSeatObj() {
        return selectedSeat == null || !app.planner().hasHall() ? null : app.planner().getHall().getSeat(selectedSeat);
    }

    private void updateSelection() {
        SeatingPlanner p = app.planner();
        Attendee a = selectedAttendeeObj();
        Seat s = selectedSeatObj();
        map.setSelectedSeat(selectedSeat);

        Set<String> related = new HashSet<>();
        if (a != null) {
            Seat own = p.seatOf(a);
            if (own != null) {
                related.add(own.getId());
            }
            for (String id : p.getValidator().companionClosure(p.getProject(), a.getId())) {
                Attendee x = p.getProject().getAttendee(id);
                if (x != null && p.seatOf(x) != null) {
                    related.add(p.seatOf(x).getId());
                }
            }
            if (a.hasGroup()) {
                for (Attendee m : p.getProject().getGroupMembers(a.getGroupId())) {
                    if (p.seatOf(m) != null) {
                        related.add(p.seatOf(m).getId());
                    }
                }
            }
        }
        map.setHighlighted(related);

        if (a == null) {
            attendeeInfo.setText("<html><span style='color:#5b6578'>Pick someone from the list (or double-click an occupied seat).</span></html>");
        } else {
            Seat own = p.seatOf(a);
            StringBuilder sb = new StringBuilder("<html><b>").append(esc(a.getFullName())).append("</b> (").append(a.getId()).append(")<br>")
                    .append("Seat: <b>").append(own == null ? "none" : own.getId()).append("</b> &middot; ")
                    .append(p.statusOf(a).getLabel());
            if (a.hasAccessibilityNeed()) {
                sb.append("<br>Needs: ").append(a.getAccessibilityNeed().getLabel());
            }
            if (a.isRequiresCompanion()) {
                sb.append("<br>Companion: ").append(esc(a.getCompanionId()));
            }
            if (a.hasGroup()) {
                sb.append("<br>Group: ").append(esc(a.getGroupId()));
            }
            if (a.hasPreference()) {
                sb.append("<br>Prefers: ").append(esc(a.describePreference()));
            }
            List<Conflict> open = p.getConflictManager().openConflictsFor(a.getId());
            if (!open.isEmpty()) {
                sb.append("<br><span style='color:#b91c1c'>").append(open.size()).append(" open conflict(s)</span>");
            }
            attendeeInfo.setText(sb.append("</html>").toString());
        }
        if (s == null) {
            seatInfo.setText("<html><span style='color:#5b6578'>Click a seat on the map.</span></html>");
        } else {
            Attendee occ = p.occupantOf(s);
            StringBuilder sb = new StringBuilder("<html><b>").append(s.getId()).append("</b> &middot; row ").append(s.getRowLabel())
                    .append(", seat ").append(s.getNumber()).append("<br>Type: ").append(s.getType().getLabel());
            if (s.getType() == SeatType.RESERVED && !s.getReservedFor().isEmpty()) {
                sb.append(" for ").append(esc(s.getReservedFor()));
            }
            sb.append("<br>").append(p.getHall().areaOf(s.getRow()).getLabel()).append(" section")
                    .append(p.getHall().isAisleSeat(s) ? " &middot; aisle seat" : "");
            sb.append("<br>Occupant: ").append(occ == null ? "<i>empty</i>" : "<b>" + esc(occ.getFullName()) + "</b> (" + occ.getId() + ")");
            seatInfo.setText(sb.append("</html>").toString());
        }

        Attendee occ = s == null ? null : p.occupantOf(s);
        boolean aSeated = a != null && p.seatOf(a) != null;
        assignBtn.setEnabled(a != null && s != null && occ == null);
        assignBtn.setText(aSeated ? "Move here" : "Assign");
        assignBtn.setToolTipText(a != null && s != null ? (aSeated ? "Move " : "Assign ") + a.getId() + " to " + s.getId() : null);
        occupantBtn.setToolTipText("Select the attendee sitting in the selected seat");
        swapBtn.setEnabled(aSeated && occ != null && !occ.getId().equals(a.getId()));
        unassignBtn.setEnabled(aSeated);
        occupantBtn.setEnabled(occ != null && (a == null || !occ.getId().equals(a.getId())));
    }

    // ------------------------------------------------------------------ actions

    private void assign() {
        Attendee a = selectedAttendeeObj();
        Seat s = selectedSeatObj();
        if (a == null || s == null) {
            return;
        }
        SeatingPlanner p = app.planner();
        if (p.wouldWasteAccessibleSeat(a.getId(), s.getId())) {
            int ok = JOptionPane.showConfirmDialog(this, "Seat " + s.getId() + " is an accessible seat and " + a.getId()
                            + " has no accessibility requirement.\nUsing it may leave someone who needs it without a suitable seat.\n\n"
                            + "Assign it anyway?", "Accessible seat", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
        }
        ActionResult r = p.assignSeat(a.getId(), s.getId());
        if (!r.isSuccess() && r.hasProposal()) {
            Attendee partner = p.getProject().getAttendee(r.getProposedAttendeeId());
            int ok = JOptionPane.showConfirmDialog(this, r.getMessage() + "\n\nMove " + partner.getId() + " (" + partner.getFullName()
                            + ") to seat " + r.getProposedSeatId() + " as well, so they stay side by side?",
                    "Keep companions together", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.YES_OPTION) {
                r = p.assignPair(a.getId(), s.getId(), partner.getId(), r.getProposedSeatId());
            } else {
                feedback(ActionResult.rejected("Change cancelled - the layout was not modified."));
                return;
            }
        }
        feedback(r);
    }

    private void swap() {
        Attendee a = selectedAttendeeObj();
        Seat s = selectedSeatObj();
        Attendee occ = s == null ? null : app.planner().occupantOf(s);
        if (a == null || occ == null) {
            return;
        }
        feedback(app.planner().swap(a.getId(), occ.getId()));
    }

    private void unassign() {
        Attendee a = selectedAttendeeObj();
        SeatingPlanner p = app.planner();
        if (a == null || p.seatOf(a) == null) {
            return;
        }
        List<Attendee> deps = p.seatedDependentsOf(a.getId());
        SeatingPlanner.DependentPolicy policy = SeatingPlanner.DependentPolicy.UNASSIGN_DEPENDENTS;
        if (!deps.isEmpty()) {
            StringBuilder names = new StringBuilder();
            deps.forEach(d -> names.append("\n  - ").append(d.getId()).append(" (").append(d.getFullName()).append(")"));
            Object[] options = {"Unassign both", "Unassign only " + a.getId(), "Cancel"};
            int choice = JOptionPane.showOptionDialog(this, a.getId() + " is the required companion of:" + names
                            + "\n\nIf only " + a.getId() + " is unassigned, that requirement becomes UNRESOLVED and is reported as an error.",
                    "Companion requirement affected", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (choice == 1) {
                policy = SeatingPlanner.DependentPolicy.LEAVE_UNRESOLVED;
            } else if (choice != 0) {
                return;
            }
        } else {
            int ok = JOptionPane.showConfirmDialog(this, "Unassign " + a.getId() + " (" + a.getFullName() + ") from seat "
                    + p.seatOf(a).getId() + "?", "Unassign attendee", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
        }
        feedback(p.unassign(a.getId(), policy));
    }

    private void selectOccupant() {
        Seat s = selectedSeatObj();
        Attendee occ = s == null ? null : app.planner().occupantOf(s);
        if (occ != null) {
            if (filter.getSelectedIndex() != 0 || !search.getText().isEmpty()) {
                search.setText("");
                filter.setSelectedIndex(0);
            }
            selectedAttendee = occ.getId();
            reloadList();
            updateSelection();
        }
    }

    private void resetAll() {
        if (!app.planner().hasHall() || app.planner().getProject().getAssignment().size() == 0) {
            feedback(ActionResult.rejected("There are no assignments to reset."));
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Remove ALL " + app.planner().getProject().getAssignment().size()
                        + " seat assignments? Attendees, the hall layout and conflict history are kept.\nThis cannot be undone.",
                "Reset assignments", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            app.planner().resetAssignments();
            feedback(ActionResult.ok("All assignments were cleared."));
        }
    }

    private void feedback(ActionResult r) {
        if (r.isSuccess()) {
            feedback.show(esc(r.getMessage()), Theme.SUCCESS, Theme.SUCCESS_SOFT, Icons.Glyph.CHECK);
            app.showStatus(r.getMessage(), true);
        } else {
            feedback.show(esc(r.getMessage()).replace("\n", "<br>"), Theme.DANGER, Theme.DANGER_SOFT, Icons.Glyph.ALERT);
            JOptionPane.showMessageDialog(this, r.getMessage(), "Change rejected", JOptionPane.WARNING_MESSAGE);
        }
        updateSelection();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** List cell: status dot, name, ID and current seat. */
    private class AttendeeCell extends JPanel implements ListCellRenderer<Attendee> {
        private final JLabel name = new JLabel();
        private final JLabel sub = UiKit.muted("");
        private final JLabel seat = new JLabel();

        AttendeeCell() {
            super(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(5, 10, 5, 10));
            JPanel text = UiKit.transparent(new GridLayout(2, 1));
            name.setFont(Theme.bold(13));
            text.add(name);
            text.add(sub);
            add(text, BorderLayout.CENTER);
            seat.setFont(Theme.bold(12));
            add(seat, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Attendee> l, Attendee a, int index, boolean sel, boolean focus) {
            SeatingPlanner p = app.planner();
            AssignmentStatus st = p.statusOf(a);
            name.setText(a.getFullName());
            name.setIcon(new UiKit.PillDot(UiKit.pillColors(st)[0]));
            name.setIconTextGap(7);
            String tags = (a.hasAccessibilityNeed() ? " · " + a.getAccessibilityNeed().getLabel() : "")
                    + (a.isRequiresCompanion() ? " · companion" : "");
            sub.setText(a.getId() + (a.hasGroup() ? " · " + a.getGroupId() : "") + tags);
            Seat s = p.seatOf(a);
            seat.setText(s == null ? "-" : s.getId());
            seat.setForeground(s == null ? Theme.WARNING : Theme.PRIMARY);
            setBackground(sel ? Theme.PRIMARY_SOFT : index % 2 == 0 ? Theme.SURFACE : Theme.SURFACE_ALT);
            setOpaque(true);
            return this;
        }
    }
}
