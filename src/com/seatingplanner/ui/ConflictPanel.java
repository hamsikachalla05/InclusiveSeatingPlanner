package com.seatingplanner.ui;

import com.seatingplanner.model.Conflict;
import com.seatingplanner.model.ConflictSeverity;
import com.seatingplanner.model.ResolutionStatus;
import com.seatingplanner.service.SeatingPlanner;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Conflict register. Errors concern mandatory requirements; warnings concern relaxed preferences.
 * A conflict can only be marked resolved after re-validation confirms the issue is gone.
 */
public class ConflictPanel extends JPanel implements MainFrame.Screen {

    private static final String[] COLUMNS = {"ID", "Severity", "Category", "Affected", "Description", "Status"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss");

    private final MainFrame app;
    private final ConflictModel model = new ConflictModel();
    private final JTable table = new JTable(model);
    private final JComboBox<String> filter = new JComboBox<>(new String[]{
            "Needs attention (open + awaiting review)", "Open only", "Errors (open)", "Warnings (open)", "Awaiting review",
            "Resolved & acknowledged", "All conflicts"});
    private final JLabel chipErrors = UiKit.chip("", Theme.DANGER, Theme.DANGER_SOFT);
    private final JLabel chipWarnings = UiKit.chip("", Theme.WARNING, Theme.WARNING_SOFT);
    private final JLabel chipReview = UiKit.chip("", Theme.INFO, Theme.INFO_SOFT);
    private final JLabel chipResolved = UiKit.chip("", Theme.SUCCESS, Theme.SUCCESS_SOFT);
    private final JLabel detailTitle = new JLabel(" ");
    private final JTextArea detailText = new JTextArea();
    private final JLabel detailMeta = UiKit.muted(" ");
    private final UiKit.AppButton resolveBtn = UiKit.success("Mark resolved", Icons.Glyph.CHECK);
    private final UiKit.AppButton ackBtn = UiKit.secondary("Acknowledge warning", Icons.Glyph.INFO);
    private final UiKit.AppButton gotoBtn = UiKit.secondary("Show in layout", Icons.Glyph.SEATS);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = UiKit.transparent(cards);

    public ConflictPanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        UiKit.AppButton revalidate = UiKit.secondary("Re-validate now", Icons.Glyph.RESET);
        revalidate.addActionListener(e -> {
            app.planner().revalidate();
            app.showStatus("Re-validated the layout.", true);
        });
        UiKit.AppButton confirmAll = UiKit.secondary("Confirm all addressed", Icons.Glyph.CHECK);
        confirmAll.setToolTipText("Marks every conflict that re-validation no longer detects as resolved");
        confirmAll.addActionListener(e -> {
            int n = app.planner().confirmAllAddressed();
            app.showStatus(n == 0 ? "No addressed conflicts awaiting review." : "Confirmed " + n + " addressed conflict(s) as resolved.", true);
        });
        UiKit.AppButton purge = UiKit.ghost("Clear resolved", Icons.Glyph.TRASH);
        purge.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this, "Remove all resolved conflicts from the history?", "Clear resolved",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                app.showStatus("Removed " + app.planner().purgeResolvedConflicts() + " resolved conflict(s).", true);
            }
        });
        add(UiKit.pageHeader("Conflicts", "Detected automatically after every change.",
                purge, confirmAll, revalidate), BorderLayout.NORTH);

        UiKit.styleTable(table);
        TableRowSorter<ConflictModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(1).setCellRenderer(new UiKit.PillRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new UiKit.PillRenderer());
        int[] widths = {55, 100, 230, 130, 520, 190};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetail();
            }
        });
        filter.addActionListener(e -> refresh());

        JPanel chips = UiKit.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.add(chipErrors);
        chips.add(chipWarnings);
        chips.add(chipReview);
        chips.add(chipResolved);
        JPanel toolbar = UiKit.transparent(new BorderLayout());
        toolbar.setBorder(new EmptyBorder(0, 0, 10, 0));
        toolbar.add(chips, BorderLayout.WEST);
        toolbar.add(filter, BorderLayout.EAST);

        UiKit.Card tableCard = UiKit.card(new BorderLayout());
        tableCard.add(toolbar, BorderLayout.NORTH);
        tableCard.add(UiKit.scroll(table), BorderLayout.CENTER);
        UiKit.Card emptyCard = UiKit.card(new BorderLayout());
        emptyCard.add(UiKit.emptyState(Icons.Glyph.SHIELD, "No conflicts to show",
                "Nothing matches this filter. Conflicts appear here automatically whenever a requirement or preference "
                        + "cannot be met.", null));
        center.add(tableCard, "table");
        center.add(emptyCard, "empty");

        // detail card
        UiKit.Card detail = UiKit.card(new BorderLayout(0, 8));
        detail.setPreferredSize(new Dimension(200, 210));
        detailTitle.setFont(Theme.bold(15));
        detailText.setEditable(false);
        detailText.setLineWrap(true);
        detailText.setWrapStyleWord(true);
        detailText.setFont(Theme.body());
        detailText.setOpaque(false);
        detailText.setBorder(BorderFactory.createEmptyBorder());
        JPanel top = UiKit.transparent(new BorderLayout());
        top.add(detailTitle, BorderLayout.NORTH);
        top.add(detailMeta, BorderLayout.SOUTH);
        detail.add(top, BorderLayout.NORTH);
        detail.add(detailText, BorderLayout.CENTER);
        resolveBtn.addActionListener(e -> markResolved());
        ackBtn.addActionListener(e -> acknowledge());
        gotoBtn.addActionListener(e -> {
            Conflict c = selected();
            if (c != null && !c.getPrimaryAttendee().isEmpty()) {
                app.focusAttendeeInLayout(c.getPrimaryAttendee());
            }
        });
        JPanel actions = UiKit.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(resolveBtn);
        actions.add(ackBtn);
        actions.add(gotoBtn);
        detail.add(actions, BorderLayout.SOUTH);

        JPanel body = UiKit.transparent(new BorderLayout(0, 14));
        body.add(center, BorderLayout.CENTER);
        body.add(detail, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
        updateDetail();
    }

    @Override
    public void refresh() {
        SeatingPlanner p = app.planner();
        String keep = selected() == null ? null : selected().getId();
        List<Conflict> rows = new ArrayList<>();
        int mode = filter.getSelectedIndex();
        for (Conflict c : p.getConflictManager().getConflicts()) {
            ResolutionStatus s = c.getStatus();
            boolean include = switch (mode) {
                case 0 -> s == ResolutionStatus.OPEN || s == ResolutionStatus.ADDRESSED;
                case 1 -> s == ResolutionStatus.OPEN;
                case 2 -> s == ResolutionStatus.OPEN && c.getSeverity() == ConflictSeverity.ERROR;
                case 3 -> s == ResolutionStatus.OPEN && c.getSeverity() == ConflictSeverity.WARNING;
                case 4 -> s == ResolutionStatus.ADDRESSED;
                case 5 -> s == ResolutionStatus.RESOLVED || s == ResolutionStatus.ACKNOWLEDGED;
                default -> true;
            };
            if (include) {
                rows.add(c);
            }
        }
        // errors first, then warnings; newest last within each
        rows.sort((a, b) -> a.getSeverity() != b.getSeverity() ? a.getSeverity().compareTo(b.getSeverity())
                : a.getStatus() != b.getStatus() ? a.getStatus().compareTo(b.getStatus()) : a.getId().compareTo(b.getId()));
        model.set(rows);
        cards.show(center, rows.isEmpty() ? "empty" : "table");
        SeatingPlanner.Stats st = p.getStats();
        UiKit.setChip(chipErrors, st.openErrors() + " open error(s)", Theme.DANGER, Theme.DANGER_SOFT);
        UiKit.setChip(chipWarnings, st.openWarnings() + " open warning(s)", Theme.WARNING, Theme.WARNING_SOFT);
        UiKit.setChip(chipReview, st.awaitingReview() + " awaiting review", Theme.INFO, Theme.INFO_SOFT);
        long resolved = p.getConflictManager().getConflicts().stream().filter(c -> c.getStatus() == ResolutionStatus.RESOLVED).count();
        UiKit.setChip(chipResolved, resolved + " resolved", Theme.SUCCESS, Theme.SUCCESS_SOFT);
        if (keep != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getId().equals(keep)) {
                    int v = table.convertRowIndexToView(i);
                    if (v >= 0) {
                        table.setRowSelectionInterval(v, v);
                    }
                }
            }
        }
        updateDetail();
    }

    private Conflict selected() {
        int v = table.getSelectedRow();
        return v < 0 ? null : model.rows.get(table.convertRowIndexToModel(v));
    }

    private void updateDetail() {
        Conflict c = selected();
        resolveBtn.setEnabled(c != null && c.getStatus() != ResolutionStatus.RESOLVED);
        ackBtn.setEnabled(c != null && c.getSeverity() == ConflictSeverity.WARNING && c.getStatus() == ResolutionStatus.OPEN);
        gotoBtn.setEnabled(c != null && !c.getPrimaryAttendee().isEmpty() && app.planner().hasHall()
                && app.planner().getProject().containsAttendee(c.getPrimaryAttendee()));
        if (c == null) {
            detailTitle.setText("Select a conflict");
            detailMeta.setText("Details, affected attendees and the suggested resolution appear here.");
            detailText.setText("");
            return;
        }
        detailTitle.setText(c.getId() + "  ·  " + c.getSeverity().getLabel().toUpperCase() + "  ·  " + c.getCategory().getLabel());
        detailTitle.setForeground(c.getSeverity() == ConflictSeverity.ERROR ? Theme.DANGER : Theme.WARNING);
        detailMeta.setText("Status: " + c.getStatus().getLabel() + "   ·   Affected: "
                + (c.getAffectedAttendees().isEmpty() ? "hall-wide" : String.join(", ", c.getAffectedAttendees()))
                + "   ·   Detected " + c.getDetectedAt().format(TIME) + "   ·   Updated " + c.getUpdatedAt().format(TIME)
                + (c.isLive() ? "" : "   ·   Event record (blocked action)"));
        detailText.setText(c.formatMessage() + (c.getSuggestion().isEmpty() ? "" : "\n\nSuggested resolution: " + c.getSuggestion()));
        detailText.setCaretPosition(0);
    }

    private void markResolved() {
        Conflict c = selected();
        if (c == null) {
            return;
        }
        String error = app.planner().markConflictResolved(c.getId());
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Cannot mark " + c.getId() + " resolved", JOptionPane.WARNING_MESSAGE);
        } else {
            app.showStatus(c.getId() + " validated and marked resolved.", true);
        }
    }

    private void acknowledge() {
        Conflict c = selected();
        if (c == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Accept this warning as-is? The preference stays unmet but will no longer be "
                + "flagged as needing attention.", "Acknowledge " + c.getId(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String error = app.planner().acknowledgeConflict(c.getId());
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Cannot acknowledge", JOptionPane.WARNING_MESSAGE);
        } else {
            app.showStatus(c.getId() + " acknowledged.", true);
        }
    }

    private static class ConflictModel extends AbstractTableModel {
        private final List<Conflict> rows = new ArrayList<>();

        void set(List<Conflict> list) {
            rows.clear();
            rows.addAll(list);
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
            return c == 1 ? ConflictSeverity.class : c == 5 ? ResolutionStatus.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Conflict x = rows.get(r);
            return switch (c) {
                case 0 -> x.getId();
                case 1 -> x.getSeverity();
                case 2 -> x.getCategory().getLabel();
                case 3 -> x.getAffectedAttendees().isEmpty() ? "hall-wide" : summarize(x.getAffectedAttendees());
                case 4 -> x.getDescription();
                default -> x.getStatus();
            };
        }

        private static String summarize(List<String> ids) {
            return ids.size() <= 3 ? String.join(", ", ids) : String.join(", ", ids.subList(0, 3)) + " +" + (ids.size() - 3);
        }
    }
}
