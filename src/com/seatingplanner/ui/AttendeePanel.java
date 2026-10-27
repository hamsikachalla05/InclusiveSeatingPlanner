package com.seatingplanner.ui;

import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.Seat;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Attendee register: searchable, filterable table with add / edit / remove and a details pane. */
public class AttendeePanel extends JPanel implements MainFrame.Screen {

    private static final String[] COLUMNS = {"ID", "Name", "Group", "Accommodation", "Companion", "Preference", "Seat", "Status"};

    private final MainFrame app;
    private final AttendeeModel model = new AttendeeModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<AttendeeModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = UiKit.searchField("Search by ID, name or group");
    private final JComboBox<String> filter = new JComboBox<>(new String[]{
            "All attendees", "Needs accommodation", "Requires companion", "Unassigned", "Unresolved"});
    private final JLabel countLabel = UiKit.muted(" ");
    private final JLabel details = new JLabel();
    private final UiKit.AppButton edit = UiKit.secondary("Edit", Icons.Glyph.EDIT);
    private final UiKit.AppButton remove = UiKit.danger("Remove", Icons.Glyph.TRASH);
    private final UiKit.AppButton locate = UiKit.secondary("Show in layout", Icons.Glyph.SEATS);
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = UiKit.transparent(centerCards);

    public AttendeePanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        UiKit.AppButton add = UiKit.primary("Register attendee", Icons.Glyph.USER_PLUS);
        add.addActionListener(e -> app.openNewAttendeeDialog());
        UiKit.AppButton imp = UiKit.secondary("Import CSV", Icons.Glyph.FILE);
        imp.setToolTipText("Columns: id, name, group, accessibility, companion_required, companion_id, preferred_area, preferred_row, notes");
        imp.addActionListener(e -> app.importAttendees());
        add(UiKit.pageHeader("Attendees", "Register attendees and their mandatory seating requirements and optional preferences.",
                imp, add), BorderLayout.NORTH);

        UiKit.styleTable(table);
        table.setRowSorter(sorter);
        sorter.setComparator(0, com.seatingplanner.model.Ids.NATURAL);
        sorter.setComparator(6, com.seatingplanner.model.Ids.NATURAL);
        table.getColumnModel().getColumn(7).setCellRenderer(new UiKit.PillRenderer());
        int[] widths = {70, 190, 80, 150, 90, 110, 60, 110};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetails();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) {
                    editSelected();
                }
            }
        });

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        filter.addActionListener(e -> applyFilter());

        JPanel toolbar = UiKit.transparent(new BorderLayout(10, 0));
        toolbar.setBorder(new EmptyBorder(0, 0, 10, 0));
        JPanel leftTools = UiKit.row(search, filter);
        search.setPreferredSize(new Dimension(280, 32));
        toolbar.add(leftTools, BorderLayout.WEST);
        toolbar.add(countLabel, BorderLayout.EAST);

        UiKit.Card tableCard = UiKit.card(new BorderLayout());
        tableCard.add(toolbar, BorderLayout.NORTH);
        tableCard.add(UiKit.scroll(table), BorderLayout.CENTER);

        UiKit.AppButton empty = UiKit.primary("Register the first attendee", Icons.Glyph.USER_PLUS);
        empty.addActionListener(e -> app.openNewAttendeeDialog());
        UiKit.Card emptyCard = UiKit.card(new BorderLayout());
        emptyCard.add(UiKit.emptyState(Icons.Glyph.PEOPLE, "No attendees yet",
                "Register attendees one by one, import them from a CSV file, or load the demo project from the dashboard.", empty));
        center.add(tableCard, "table");
        center.add(emptyCard, "empty");

        // details pane
        UiKit.Card side = UiKit.card(new BorderLayout(0, 12));
        side.setPreferredSize(new Dimension(300, 200));
        side.add(UiKit.sectionTitle("Details"), BorderLayout.NORTH);
        details.setVerticalAlignment(JLabel.TOP);
        details.setFont(Theme.body());
        side.add(details, BorderLayout.CENTER);
        edit.addActionListener(e -> editSelected());
        remove.addActionListener(e -> removeSelected());
        locate.addActionListener(e -> {
            Attendee a = selected();
            if (a != null) {
                app.focusAttendeeInLayout(a.getId());
            }
        });
        JPanel actions = UiKit.transparent(new java.awt.GridLayout(3, 1, 0, 8));
        actions.add(edit);
        actions.add(locate);
        actions.add(remove);
        side.add(actions, BorderLayout.SOUTH);

        JPanel body = UiKit.transparent(new BorderLayout(16, 0));
        body.add(center, BorderLayout.CENTER);
        body.add(side, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        updateDetails();
    }

    @Override
    public void refresh() {
        String keep = selected() == null ? null : selected().getId();
        model.reload(app.planner());
        centerCards.show(center, model.getRowCount() == 0 ? "empty" : "table");
        applyFilter();
        if (keep != null) {
            selectAttendee(keep);
        }
        updateDetails();
    }

    public void selectAttendee(String id) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.rows.get(i).getId().equals(id)) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) {
                    table.setRowSelectionInterval(view, view);
                    table.scrollRectToVisible(table.getCellRect(view, 0, true));
                }
                return;
            }
        }
    }

    private Attendee selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : model.rows.get(table.convertRowIndexToModel(view));
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        int mode = filter.getSelectedIndex();
        SeatingPlanner planner = app.planner();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends AttendeeModel, ? extends Integer> entry) {
                Attendee a = model.rows.get(entry.getIdentifier());
                if (!q.isEmpty() && !(a.getId().toLowerCase(Locale.ROOT).contains(q)
                        || a.getFullName().toLowerCase(Locale.ROOT).contains(q)
                        || a.getGroupId().toLowerCase(Locale.ROOT).contains(q))) {
                    return false;
                }
                return switch (mode) {
                    case 1 -> a.hasAccessibilityNeed();
                    case 2 -> a.isRequiresCompanion();
                    case 3 -> planner.seatOf(a) == null;
                    case 4 -> planner.statusOf(a) == AssignmentStatus.UNRESOLVED;
                    default -> true;
                };
            }
        });
        countLabel.setText("Showing " + table.getRowCount() + " of " + model.getRowCount());
    }

    private void updateDetails() {
        Attendee a = selected();
        edit.setEnabled(a != null);
        remove.setEnabled(a != null);
        locate.setEnabled(a != null && app.planner().hasHall());
        if (a == null) {
            details.setText("<html><span style='color:#5b6578'>Select an attendee to see their seat, requirements and "
                    + "any open conflicts.</span></html>");
            return;
        }
        SeatingPlanner p = app.planner();
        Seat s = p.seatOf(a);
        StringBuilder sb = new StringBuilder("<html><div style='width:240px'>");
        sb.append("<b style='font-size:14px'>").append(esc(a.getFullName())).append("</b><br>")
                .append("<span style='color:#5b6578'>").append(a.getId()).append(a.hasGroup() ? " &middot; Group " + esc(a.getGroupId()) : "")
                .append("</span><br><br>");
        if (s != null) {
            sb.append("<b>Seat:</b> ").append(s.getId()).append(" (row ").append(s.getRowLabel()).append(", seat ")
                    .append(s.getNumber()).append(")<br><b>Seat type:</b> ").append(s.getType().getLabel())
                    .append("<br><b>Section:</b> ").append(p.getHall().areaOf(s.getRow()).getLabel()).append("<br>");
        } else {
            sb.append("<b>Seat:</b> not assigned<br>");
        }
        sb.append("<b>Status:</b> ").append(p.statusOf(a).getLabel()).append("<br><br>");
        sb.append("<b>Accommodation:</b> ").append(a.getAccessibilityNeed().getLabel()).append("<br>");
        if (a.isRequiresCompanion()) {
            Attendee c = p.getProject().getAttendee(a.getCompanionId());
            sb.append("<b>Companion:</b> ").append(esc(a.getCompanionId())).append(c == null ? " (not registered)" : " - " + esc(c.getFullName()))
                    .append("<br>");
        }
        List<Attendee> deps = p.dependentsOf(a.getId());
        if (!deps.isEmpty()) {
            sb.append("<b>Companion for:</b> ");
            List<String> ids = new ArrayList<>();
            deps.forEach(d -> ids.add(d.getId()));
            sb.append(String.join(", ", ids)).append("<br>");
        }
        if (a.hasPreference()) {
            sb.append("<b>Preference:</b> ").append(esc(a.describePreference())).append("<br>");
        }
        if (!a.getNotes().isEmpty()) {
            sb.append("<b>Notes:</b> ").append(esc(a.getNotes())).append("<br>");
        }
        List<Conflict> open = p.getConflictManager().openConflictsFor(a.getId());
        if (!open.isEmpty()) {
            sb.append("<br><b style='color:#b91c1c'>Open conflicts</b><br>");
            for (Conflict c : open) {
                String color = c.getSeverity().name().equals("ERROR") ? "#b91c1c" : "#b45309";
                sb.append("<span style='color:").append(color).append("'>&bull; ").append(c.getId()).append(": ")
                        .append(esc(c.getDescription())).append("</span><br>");
            }
        }
        details.setText(sb.append("</div></html>").toString());
    }

    private void editSelected() {
        Attendee a = selected();
        if (a == null) {
            return;
        }
        Attendee edited = new AttendeeDialog(app, app.planner().getProject(), a).showDialog();
        if (edited == null) {
            return;
        }
        String problem = app.planner().seatProblemAfterEdit(edited);
        if (problem != null) {
            int ok = JOptionPane.showConfirmDialog(this, "With these changes the current seat no longer meets " + a.getId()
                            + "'s mandatory requirement:\n" + problem + "\n\nSave and release the seat? " + a.getId()
                            + " will be reported as unassigned until a suitable seat is found.",
                    "Seat no longer suitable", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
        }
        boolean released = app.planner().updateAttendee(edited);
        app.showStatus("Saved " + edited.getId() + (released ? " - seat released because it no longer fits." : "."), !released);
    }

    private void removeSelected() {
        Attendee a = selected();
        if (a == null) {
            return;
        }
        List<Attendee> deps = app.planner().dependentsOf(a.getId());
        String extra = deps.isEmpty() ? "" : "\n\nWarning: " + deps.size() + " attendee(s) list " + a.getId()
                + " as their required companion. Their companion requirement will become unresolved.";
        int ok = JOptionPane.showConfirmDialog(this, "Remove " + a.getId() + " (" + a.getFullName() + ")? This also frees their seat."
                + extra, "Remove attendee", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            app.planner().removeAttendee(a.getId());
            app.showStatus("Removed " + a.getId() + ".", true);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Table model over a snapshot of the attendee list. */
    private static class AttendeeModel extends AbstractTableModel {
        private final List<Attendee> rows = new ArrayList<>();
        private SeatingPlanner planner;

        void reload(SeatingPlanner p) {
            planner = p;
            rows.clear();
            rows.addAll(p.getProject().getAttendees());
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 7 ? AssignmentStatus.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Attendee a = rows.get(r);
            return switch (c) {
                case 0 -> a.getId();
                case 1 -> a.getFullName();
                case 2 -> a.getGroupId();
                case 3 -> a.hasAccessibilityNeed() ? a.getAccessibilityNeed().getLabel() : "-";
                case 4 -> a.isRequiresCompanion() ? a.getCompanionId() : "-";
                case 5 -> a.hasPreference() ? a.describePreference() : "-";
                case 6 -> {
                    Seat s = planner.seatOf(a);
                    yield s == null ? "-" : s.getId();
                }
                default -> planner.statusOf(a);
            };
        }
    }
}
