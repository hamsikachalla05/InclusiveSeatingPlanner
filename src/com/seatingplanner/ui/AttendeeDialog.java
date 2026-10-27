package com.seatingplanner.ui;

import com.seatingplanner.model.AccessibilityNeed;
import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.EventHall;
import com.seatingplanner.model.Ids;
import com.seatingplanner.model.Project;
import com.seatingplanner.model.SeatingArea;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Modal form for registering or editing an attendee. Validates every field and shows all problems
 * inline; returns a new {@link Attendee} only when the input is valid.
 */
public class AttendeeDialog extends JDialog {

    private static final int MAX_NAME = 80;
    private static final int MAX_NOTES = 300;

    private final Project project;
    private final Attendee original;
    private final JTextField id = UiKit.textField(10);
    private final JTextField name = UiKit.textField(24);
    private final JComboBox<String> group = new JComboBox<>();
    private final JComboBox<AccessibilityNeed> need = new JComboBox<>(AccessibilityNeed.values());
    private final JLabel needHelp = UiKit.muted(" ");
    private final JCheckBox requiresCompanion = new JCheckBox("Requires a companion seated directly beside them");
    private final JComboBox<String> companion = new JComboBox<>();
    private final JComboBox<SeatingArea> area = new JComboBox<>(SeatingArea.values());
    private final JComboBox<String> row = new JComboBox<>();
    private final JTextArea notes = new JTextArea(3, 24);
    private final JLabel errors = new JLabel();
    private Attendee result;

    public AttendeeDialog(Window owner, Project project, Attendee existing) {
        super(owner, existing == null ? "Register attendee" : "Edit attendee " + existing.getId(), ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.original = existing;
        buildUi();
        populate();
        pack();
        setMinimumSize(new Dimension(560, getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and returns the validated attendee, or null if cancelled. */
    public Attendee showDialog() {
        setVisible(true);
        return result;
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.SURFACE);
        root.setBorder(new EmptyBorder(20, 22, 18, 22));

        JPanel form = UiKit.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 10, 0);
        int y = 0;

        JPanel ids = UiKit.transparent(new GridLayout(1, 2, 12, 0));
        ids.add(field("Attendee ID *", id, original == null ? "Letters, digits, - or _ (max 20)" : "IDs cannot be changed"));
        group.setEditable(true);
        ids.add(field("Group", group, "Type a new group or pick an existing one"));
        c.gridy = y++;
        form.add(ids, c);
        c.gridy = y++;
        form.add(field("Full name *", name, null), c);

        c.gridy = y++;
        form.add(section("Mandatory requirements", "Treated as hard constraints - never relaxed by the allocator."), c);
        need.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof AccessibilityNeed n) {
                    setText(n.getLabel());
                }
                return this;
            }
        });
        need.addActionListener(e -> needHelp.setText(((AccessibilityNeed) need.getSelectedItem()).getDescription()));
        c.gridy = y++;
        form.add(field("Seating accommodation", need, null), c);
        c.gridy = y++;
        c.insets = new Insets(-6, 0, 10, 0);
        form.add(needHelp, c);
        c.insets = new Insets(0, 0, 10, 0);
        requiresCompanion.setOpaque(false);
        requiresCompanion.addActionListener(e -> companion.setEnabled(requiresCompanion.isSelected()));
        c.gridy = y++;
        form.add(requiresCompanion, c);
        companion.setEditable(true);
        c.gridy = y++;
        form.add(field("Companion attendee", companion, "Choose the registered companion, or type an ID to register later (reported as a conflict until then)"), c);

        c.gridy = y++;
        form.add(section("Preferences (optional)", "Soft constraints - honoured when possible; unmet preferences are reported."), c);
        JPanel prefs = UiKit.transparent(new GridLayout(1, 2, 12, 0));
        prefs.add(field("Preferred area", area, "Front, middle or back third of the hall"));
        prefs.add(field("Preferred row", row, "Overrides the area if set"));
        c.gridy = y++;
        form.add(prefs, c);

        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBorder(new EmptyBorder(5, 8, 5, 8));
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_STRONG));
        c.gridy = y++;
        form.add(field("Seating notes", notesScroll,
                "Seating logistics only. Do not record medical details or diagnoses."), c);

        errors.setForeground(Theme.DANGER);
        errors.setFont(Theme.small());
        c.gridy = y;
        form.add(errors, c);
        root.add(form, BorderLayout.CENTER);

        UiKit.AppButton save = UiKit.primary(original == null ? "Register" : "Save changes", Icons.Glyph.CHECK);
        save.addActionListener(e -> onSave());
        UiKit.AppButton cancel = UiKit.secondary("Cancel", null);
        cancel.addActionListener(e -> dispose());
        JPanel buttons = UiKit.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(save);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JRootPane.WHEN_IN_FOCUSED_WINDOW);
    }

    private static JPanel field(String label, JComponent input, String hint) {
        JPanel p = UiKit.transparent(new BorderLayout(0, 4));
        p.add(UiKit.label(label), BorderLayout.NORTH);
        p.add(input, BorderLayout.CENTER);
        if (hint != null) {
            p.add(UiKit.muted("<html>" + hint + "</html>"), BorderLayout.SOUTH);
        }
        return p;
    }

    private static JPanel section(String title, String subtitle) {
        JPanel p = UiKit.transparent(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 0, 0, 0)));
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(14));
        p.add(t, BorderLayout.NORTH);
        p.add(UiKit.muted(subtitle), BorderLayout.SOUTH);
        return p;
    }

    private void populate() {
        TreeSet<String> groups = new TreeSet<>(Ids.NATURAL);
        groups.addAll(project.getGroups().keySet());
        List<String> groupItems = new ArrayList<>();
        groupItems.add("");
        groupItems.addAll(groups);
        group.setModel(new DefaultComboBoxModel<>(groupItems.toArray(new String[0])));

        List<String> companions = new ArrayList<>();
        companions.add("");
        for (Attendee a : project.getAttendees()) {
            if (original == null || !a.getId().equals(original.getId())) {
                companions.add(a.getId() + " - " + a.getFullName());
            }
        }
        companion.setModel(new DefaultComboBoxModel<>(companions.toArray(new String[0])));

        List<String> rows = new ArrayList<>();
        rows.add("");
        if (project.hasHall()) {
            for (int r = 1; r <= project.getHall().getRows(); r++) {
                rows.add(EventHall.rowLabel(r));
            }
        }
        row.setModel(new DefaultComboBoxModel<>(rows.toArray(new String[0])));

        if (original == null) {
            id.setText(project.nextAttendeeId());
            need.setSelectedItem(AccessibilityNeed.NONE);
            area.setSelectedItem(SeatingArea.ANY);
        } else {
            id.setText(original.getId());
            id.setEditable(false);
            id.setBackground(Theme.SURFACE_ALT);
            name.setText(original.getFullName());
            group.setSelectedItem(original.getGroupId());
            need.setSelectedItem(original.getAccessibilityNeed());
            requiresCompanion.setSelected(original.isRequiresCompanion());
            Attendee comp = project.getAttendee(original.getCompanionId());
            companion.setSelectedItem(comp != null ? comp.getId() + " - " + comp.getFullName() : original.getCompanionId());
            area.setSelectedItem(original.getPreferredArea());
            if (!original.getPreferredRow().isEmpty()) {
                if (((DefaultComboBoxModel<String>) row.getModel()).getIndexOf(original.getPreferredRow()) < 0) {
                    row.addItem(original.getPreferredRow());
                }
                row.setSelectedItem(original.getPreferredRow());
            }
            notes.setText(original.getNotes());
        }
        needHelp.setText(((AccessibilityNeed) need.getSelectedItem()).getDescription());
        companion.setEnabled(requiresCompanion.isSelected());
    }

    private void onSave() {
        List<String> problems = new ArrayList<>();
        String idText = Ids.normalize(id.getText());
        if (original == null) {
            if (idText.isEmpty()) {
                problems.add("Attendee ID is required.");
            } else if (!idText.matches("[A-Z0-9_-]{1,20}")) {
                problems.add("Attendee ID may only contain letters, digits, '-' or '_' (max 20 characters).");
            } else if (project.containsAttendee(idText)) {
                problems.add("Attendee ID " + idText + " is already registered.");
            }
        }
        String nameText = name.getText().trim();
        if (nameText.isEmpty()) {
            problems.add("Full name is required.");
        } else if (nameText.length() > MAX_NAME) {
            problems.add("Full name must be " + MAX_NAME + " characters or fewer.");
        }
        String groupText = group.getSelectedItem() == null ? "" : group.getSelectedItem().toString().trim();
        if (groupText.length() > 40) {
            problems.add("Group name must be 40 characters or fewer.");
        }
        String companionId = "";
        if (requiresCompanion.isSelected()) {
            Object sel = companion.getSelectedItem();
            String text = sel == null ? "" : sel.toString().trim();
            companionId = Ids.normalize(text.contains(" - ") ? text.substring(0, text.indexOf(" - ")) : text);
            if (companionId.isEmpty()) {
                problems.add("Select the companion, or untick 'Requires a companion'.");
            } else if (companionId.equals(idText)) {
                problems.add("An attendee cannot be their own companion.");
            } else if (!companionId.matches("[A-Z0-9_-]{1,20}")) {
                problems.add("Companion ID is not a valid attendee ID.");
            }
        }
        if (notes.getText().length() > MAX_NOTES) {
            problems.add("Notes must be " + MAX_NOTES + " characters or fewer.");
        }
        if (!problems.isEmpty()) {
            errors.setText("<html>&bull; " + String.join("<br>&bull; ", problems) + "</html>");
            pack();
            return;
        }
        Attendee a = new Attendee(original == null ? idText : original.getId(), nameText);
        a.setGroupId(groupText);
        a.setAccessibilityNeed((AccessibilityNeed) need.getSelectedItem());
        a.setRequiresCompanion(requiresCompanion.isSelected());
        a.setCompanionId(requiresCompanion.isSelected() ? companionId : "");
        a.setPreferredArea((SeatingArea) area.getSelectedItem());
        a.setPreferredRow(row.getSelectedItem() == null ? "" : row.getSelectedItem().toString());
        a.setNotes(notes.getText());
        result = a;
        dispose();
    }
}
