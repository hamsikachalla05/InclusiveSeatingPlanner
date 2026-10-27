package com.seatingplanner.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/** Export and persistence actions, each with a short explanation of what it produces. */
public class ExportPanel extends JPanel implements MainFrame.Screen {

    private final MainFrame app;
    private final JLabel projectPath = new JLabel();
    private final JLabel lastSaved = UiKit.muted(" ");

    public ExportPanel(MainFrame app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));
        add(UiKit.pageHeader("Export & Save", "Share the plan, print it, or save the project to continue later."), BorderLayout.NORTH);

        JPanel grid = UiKit.transparent(new GridLayout(2, 2, 16, 16));

        UiKit.AppButton csv = UiKit.primary("Export assignments CSV", Icons.Glyph.EXPORT);
        csv.addActionListener(e -> app.exportAssignmentsCsv());
        grid.add(tile(Icons.Glyph.FILE, Theme.PRIMARY, "Seat assignments (CSV)",
                "One row per attendee: ID, name, group, assigned row and seat, seat type and assignment status. "
                        + "Opens in Excel or Google Sheets. Names with commas or quotes are escaped correctly.", csv));

        UiKit.AppButton conflicts = UiKit.primary("Export conflict report", Icons.Glyph.ALERT);
        conflicts.addActionListener(e -> app.exportConflictsCsv());
        grid.add(tile(Icons.Glyph.ALERT, Theme.DANGER, "Conflict report (CSV)",
                "Every conflict with its ID, severity, category, affected attendees, description, suggested resolution "
                        + "and current resolution status.", conflicts));

        UiKit.AppButton preview = UiKit.secondary("Preview & print", Icons.Glyph.PRINT);
        preview.addActionListener(e -> app.showReportPreview());
        UiKit.AppButton html = UiKit.primary("Save report (HTML)", Icons.Glyph.PLAN);
        html.addActionListener(e -> app.exportHtmlReport());
        grid.add(tile(Icons.Glyph.PLAN, Theme.SUCCESS, "Printable seating plan",
                "A readable report with the colour-coded seat map, an alphabetical attendee lookup and unresolved conflicts. "
                        + "Print directly or save as HTML to open in any browser (and print to PDF).", UiKit.row(preview, html)));

        UiKit.AppButton save = UiKit.primary("Save", Icons.Glyph.SAVE);
        save.addActionListener(e -> app.saveProject());
        UiKit.AppButton saveAs = UiKit.secondary("Save as...", Icons.Glyph.SAVE);
        saveAs.addActionListener(e -> app.saveProjectAs());
        UiKit.AppButton open = UiKit.secondary("Open...", Icons.Glyph.FOLDER);
        open.addActionListener(e -> app.openProject());
        JPanel saveBox = UiKit.transparent(new BorderLayout(0, 8));
        projectPath.setFont(Theme.small());
        projectPath.setForeground(Theme.TEXT);
        saveBox.add(projectPath, BorderLayout.NORTH);
        saveBox.add(lastSaved, BorderLayout.CENTER);
        saveBox.add(UiKit.row(save, saveAs, open), BorderLayout.SOUTH);
        grid.add(tile(Icons.Glyph.SAVE, new Color(0x7C3AED), "Project file (JSON)",
                "Hall layout, attendees, assignments and conflict history are saved locally and automatically after every change. "
                        + "Reloading re-validates everything.", saveBox));

        JPanel wrap = UiKit.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 0;
        c.anchor = GridBagConstraints.NORTH;
        c.insets = new Insets(0, 0, 0, 0);
        wrap.add(grid, c);
        c.gridy = 1;
        c.weighty = 1;
        wrap.add(UiKit.transparent(null), c);
        add(wrap, BorderLayout.CENTER);
    }

    private static JComponent tile(Icons.Glyph glyph, Color accent, String title, String text, JComponent actions) {
        UiKit.Card card = UiKit.card(new BorderLayout(0, 12));
        JLabel t = new JLabel(title, Icons.of(glyph, 22, accent), JLabel.LEFT);
        t.setIconTextGap(10);
        t.setFont(Theme.bold(16));
        card.add(t, BorderLayout.NORTH);
        JLabel body = new JLabel("<html><div style='width:330px'>" + text + "</div></html>");
        body.setForeground(Theme.MUTED);
        body.setVerticalAlignment(JLabel.TOP);
        card.add(body, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    @Override
    public void refresh() {
        projectPath.setText("<html><b>Current file:</b> " + app.getProjectPath() + "</html>");
        lastSaved.setText(app.getSaveStatusText());
    }
}
