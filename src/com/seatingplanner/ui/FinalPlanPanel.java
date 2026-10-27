package com.seatingplanner.ui;

import com.seatingplanner.model.AssignmentStatus;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Ids;
import com.seatingplanner.model.Seat;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The final seating plan, generated from the live assignment data: seat map with names,
 * filters, attendee lookup table and an assignment summary.
 */
public class FinalPlanPanel extends JPanel implements MainFrame.Screen {

    private static final String[] COLUMNS = {"Name", "ID", "Group", "Row", "Seat", "Seat ID", "Seat type", "Status"};
    private static final String ALL = "All";

    private final MainFrame app;
    private final SeatMapPanel map;
    private final JLabel eventTitle = new JLabel();
    private final JLabel validity = new JLabel();
    private final JLabel chipAssigned = UiKit.chip("", Theme.SUCCESS, Theme.SUCCESS_SOFT);
    private final JLabel chipUnassigned = UiKit.chip("", Theme.WARNING, Theme.WARNING_SOFT);
    private final JLabel chipConflicts = UiKit.chip("", Theme.DANGER, Theme.DANGER_SOFT);
    private final JComboBox<String> rowFilter = new JComboBox<>();
    private final JComboBox<String> groupFilter = new JComboBox<>();
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{ALL, "Assigned", "Unassigned", "Unresolved"});
    private final JTextField attendeeFilter = UiKit.searchField("Attendee name or ID");
    private final LookupModel model = new LookupModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<LookupModel> sorter = new TableRowSorter<>(model);
    private final JLabel tableCount = UiKit.muted(" ");
    private boolean reloadingFilters;

    public FinalPlanPanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        // header
        UiKit.AppButton print = UiKit.secondary("Print / preview report", Icons.Glyph.PRINT);
        print.addActionListener(e -> app.showReportPreview());
        UiKit.AppButton export = UiKit.primary("Export", Icons.Glyph.EXPORT);
        export.addActionListener(e -> app.showScreen(MainFrame.EXPORT));
        eventTitle.setFont(Theme.bold(22));
        JPanel titleBlock = UiKit.transparent(new BorderLayout(0, 4));
        titleBlock.add(eventTitle, BorderLayout.NORTH);
        titleBlock.add(validity, BorderLayout.CENTER);
        JPanel chips = UiKit.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setBorder(new EmptyBorder(6, -8, 0, 0));
        chips.add(chipAssigned);
        chips.add(chipUnassigned);
        chips.add(chipConflicts);
        titleBlock.add(chips, BorderLayout.SOUTH);
        JPanel header = UiKit.transparent(new BorderLayout());
        header.setBorder(new EmptyBorder(0, 0, 14, 0));
        header.add(titleBlock, BorderLayout.CENTER);
        header.add(UiKit.row(print, export), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // filters
        attendeeFilter.setPreferredSize(new Dimension(220, 32));
        JPanel filters = UiKit.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(UiKit.label("Row"));
        filters.add(rowFilter);
        filters.add(UiKit.label("  Group"));
        filters.add(groupFilter);
        filters.add(UiKit.label("  Status"));
        filters.add(statusFilter);
        filters.add(attendeeFilter);
        UiKit.AppButton clear = UiKit.ghost("Clear filters", Icons.Glyph.RESET);
        clear.addActionListener(e -> {
            reloadingFilters = true;
            rowFilter.setSelectedIndex(0);
            groupFilter.setSelectedIndex(0);
            statusFilter.setSelectedIndex(0);
            attendeeFilter.setText("");
            reloadingFilters = false;
            applyFilters();
        });
        filters.add(clear);
        rowFilter.addActionListener(e -> applyFilters());
        groupFilter.addActionListener(e -> applyFilters());
        statusFilter.addActionListener(e -> applyFilters());
        attendeeFilter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        });

        // map
        map = new SeatMapPanel(app.planner(), SeatMapPanel.Mode.PLAN);
        map.setSeatListener((seat, e) -> {
            Attendee occ = app.planner().occupantOf(seat);
            if (occ != null) {
                selectInTable(occ.getId());
            }
        });
        UiKit.Card mapCard = UiKit.card(new BorderLayout(0, 8));
        mapCard.add(filters, BorderLayout.NORTH);
        mapCard.add(UiKit.scroll(map), BorderLayout.CENTER);
        mapCard.add(new LegendPanel(false), BorderLayout.SOUTH);

        // lookup table
        UiKit.styleTable(table);
        table.setRowSorter(sorter);
        sorter.setComparator(1, Ids.NATURAL);
        sorter.setComparator(5, Ids.NATURAL);
        table.getColumnModel().getColumn(7).setCellRenderer(new UiKit.PillRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new UiKit.ZebraRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            int v = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && v >= 0) {
                Attendee a = model.rows.get(table.convertRowIndexToModel(v));
                Seat s = app.planner().seatOf(a);
                map.setSelectedSeat(s == null ? null : s.getId());
                if (s != null) {
                    map.scrollRectToVisible(map.seatBounds(s));
                }
            }
        });
        UiKit.Card tableCard = UiKit.card(new BorderLayout(0, 8));
        JPanel tableHead = UiKit.transparent(new BorderLayout());
        tableHead.add(UiKit.sectionTitle("Attendee lookup"), BorderLayout.WEST);
        tableHead.add(tableCount, BorderLayout.EAST);
        tableCard.add(tableHead, BorderLayout.NORTH);
        tableCard.add(UiKit.scroll(table), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapCard, tableCard);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        split.setDividerSize(10);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        SeatingPlanner p = app.planner();
        SeatingPlanner.Stats st = p.getStats();
        eventTitle.setText(p.hasHall() ? p.getHall().getEventName() : "Final Seating Plan");
        if (!p.hasHall()) {
            validity.setText("<html><span style='color:#5b6578'>Configure a hall and assign seats to see the final plan.</span></html>");
        } else if (p.isPlanFullyValid()) {
            validity.setText("<html><span style='color:#15803d'><b>✔ Valid plan</b> - everyone is seated and all mandatory "
                    + "requirements are satisfied.</span></html>");
        } else {
            validity.setText("<html><span style='color:#b91c1c'><b>Not fully valid</b> - " + st.openErrors()
                    + " open error(s). Unresolved mandatory requirements are flagged with a red dot.</span></html>");
        }
        UiKit.setChip(chipAssigned, st.assigned() + " assigned", Theme.SUCCESS, Theme.SUCCESS_SOFT);
        UiKit.setChip(chipUnassigned, (st.registered() - st.assigned()) + " unassigned", Theme.WARNING, Theme.WARNING_SOFT);
        UiKit.setChip(chipConflicts, st.unresolved() + " unresolved conflict(s)", Theme.DANGER, Theme.DANGER_SOFT);

        reloadingFilters = true;
        Object row = rowFilter.getSelectedItem();
        Object group = groupFilter.getSelectedItem();
        List<String> rows = new ArrayList<>(List.of(ALL));
        if (p.hasHall()) {
            for (int r = 1; r <= p.getHall().getRows(); r++) {
                rows.add(EventHall.rowLabel(r));
            }
        }
        rowFilter.setModel(new DefaultComboBoxModel<>(rows.toArray(new String[0])));
        rowFilter.setSelectedItem(rows.contains(row) ? row : ALL);
        List<String> groups = new ArrayList<>(List.of(ALL));
        groups.addAll(p.getProject().getGroups().keySet());
        groupFilter.setModel(new DefaultComboBoxModel<>(groups.toArray(new String[0])));
        groupFilter.setSelectedItem(groups.contains(group) ? group : ALL);
        reloadingFilters = false;

        model.reload(p);
        map.refresh();
        applyFilters();
    }

    private void applyFilters() {
        if (reloadingFilters) {
            return;
        }
        SeatingPlanner p = app.planner();
        String row = (String) rowFilter.getSelectedItem();
        String group = (String) groupFilter.getSelectedItem();
        int status = statusFilter.getSelectedIndex();
        String q = attendeeFilter.getText().trim().toLowerCase(Locale.ROOT);
        boolean any = !ALL.equals(row) || !ALL.equals(group) || status > 0 || !q.isEmpty();

        Predicate<Attendee> attendeeMatch = a -> (ALL.equals(group) || a.getGroupId().equalsIgnoreCase(group))
                && (q.isEmpty() || a.getFullName().toLowerCase(Locale.ROOT).contains(q) || a.getId().toLowerCase(Locale.ROOT).contains(q))
                && switch (status) {
                    case 1 -> p.statusOf(a) == AssignmentStatus.ASSIGNED;
                    case 2 -> p.statusOf(a) == AssignmentStatus.UNASSIGNED;
                    case 3 -> p.statusOf(a) == AssignmentStatus.UNRESOLVED;
                    default -> true;
                };
        boolean attendeeFilterActive = !ALL.equals(group) || status > 0 || !q.isEmpty();
        map.setFilter(!any ? null : seat -> {
            if (row != null && !ALL.equals(row) && !seat.getRowLabel().equals(row)) {
                return false;
            }
            if (!attendeeFilterActive) {
                return true;
            }
            Attendee occ = p.occupantOf(seat);
            return occ != null && attendeeMatch.test(occ);
        });

        sorter.setRowFilter(new javax.swing.RowFilter<>() {
            @Override
            public boolean include(Entry<? extends LookupModel, ? extends Integer> entry) {
                Attendee a = model.rows.get(entry.getIdentifier());
                if (row != null && !ALL.equals(row)) {
                    Seat s = p.seatOf(a);
                    if (s == null || !s.getRowLabel().equals(row)) {
                        return false;
                    }
                }
                return attendeeMatch.test(a);
            }
        });
        tableCount.setText(table.getRowCount() + " of " + model.getRowCount() + " attendees");
    }

    private void selectInTable(String id) {
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).getId().equals(id)) {
                int v = table.convertRowIndexToView(i);
                if (v >= 0) {
                    table.setRowSelectionInterval(v, v);
                    table.scrollRectToVisible(table.getCellRect(v, 0, true));
                }
                return;
            }
        }
    }

    private static class LookupModel extends AbstractTableModel {
        private final List<Attendee> rows = new ArrayList<>();
        private SeatingPlanner planner;

        void reload(SeatingPlanner p) {
            planner = p;
            rows.clear();
            rows.addAll(p.getProject().getAttendees());
            rows.sort((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()));
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
            return c == 7 ? AssignmentStatus.class : c == 4 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Attendee a = rows.get(r);
            Seat s = planner.seatOf(a);
            return switch (c) {
                case 0 -> a.getFullName();
                case 1 -> a.getId();
                case 2 -> a.getGroupId();
                case 3 -> s == null ? "-" : s.getRowLabel();
                case 4 -> s == null ? null : s.getNumber();
                case 5 -> s == null ? "-" : s.getId();
                case 6 -> s == null ? "-" : s.getType().getLabel();
                default -> planner.statusOf(a);
            };
        }
    }
}
