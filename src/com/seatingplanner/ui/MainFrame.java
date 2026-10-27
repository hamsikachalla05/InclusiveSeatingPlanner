package com.seatingplanner.ui;

import com.seatingplanner.model.Attendee;
import com.seatingplanner.model.Project;
import com.seatingplanner.service.DemoData;
import com.seatingplanner.service.ExportService;
import com.seatingplanner.service.FileStorageService;
import com.seatingplanner.service.SeatingAllocator;
import com.seatingplanner.service.SeatingPlanner;
import com.seatingplanner.service.StorageException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Application window: sidebar navigation, screens in a CardLayout, status bar, menus,
 * file handling and autosave. Business logic lives in {@link SeatingPlanner}.
 */
public class MainFrame extends JFrame {

    /** A screen that redraws itself from the planner's current state. */
    public interface Screen {
        void refresh();
    }

    public static final String DASHBOARD = "dashboard";
    public static final String HALL = "hall";
    public static final String ATTENDEES = "attendees";
    public static final String LAYOUT = "layout";
    public static final String CONFLICTS = "conflicts";
    public static final String PLAN = "plan";
    public static final String EXPORT = "export";

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREF_LAST = "lastProject";

    private final SeatingPlanner planner = new SeatingPlanner();
    private final FileStorageService storage = new FileStorageService();
    private final ExportService exporter = new ExportService();
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, NavButton> nav = new LinkedHashMap<>();
    private final JLabel statusMessage = new JLabel(" ");
    private final JLabel saveStatus = new JLabel(" ");
    private final Timer autosave;
    private final Timer statusClear;

    private Path projectPath;
    private boolean dirty;
    private boolean suppressAutosave;
    private String current = DASHBOARD;
    private String saveStatusText = "Not saved yet";
    private boolean autosaveErrorShown;

    private final HallSetupPanel hallPanel;
    private final AttendeePanel attendeePanel;
    private final LayoutEditorPanel layoutPanel;

    public MainFrame() {
        super("Inclusive Event Seating Planner");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(List.of(appIcon(16), appIcon(32), appIcon(64)));
        setMinimumSize(new Dimension(1100, 700));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1440, screen.width - 60), Math.min(900, screen.height - 60));
        setLocationRelativeTo(null);

        autosave = new Timer(700, e -> saveQuietly());
        autosave.setRepeats(false);
        statusClear = new Timer(7000, e -> statusMessage.setText(" "));
        statusClear.setRepeats(false);

        content.setBackground(Theme.BG);
        hallPanel = new HallSetupPanel(this);
        attendeePanel = new AttendeePanel(this);
        layoutPanel = new LayoutEditorPanel(this);
        addScreen(DASHBOARD, "Dashboard", Icons.Glyph.DASHBOARD, new DashboardPanel(this));
        addScreen(HALL, "Hall Setup", Icons.Glyph.HALL, hallPanel);
        addScreen(ATTENDEES, "Attendees", Icons.Glyph.PEOPLE, attendeePanel);
        addScreen(LAYOUT, "Seating Layout", Icons.Glyph.SEATS, layoutPanel);
        addScreen(CONFLICTS, "Conflicts", Icons.Glyph.ALERT, new ConflictPanel(this));
        addScreen(PLAN, "Final Plan", Icons.Glyph.PLAN, new FinalPlanPanel(this));
        addScreen(EXPORT, "Export & Save", Icons.Glyph.EXPORT, new ExportPanel(this));

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.add(content, BorderLayout.CENTER);
        main.add(buildStatusBar(), BorderLayout.SOUTH);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        setJMenuBar(buildMenu());

        planner.addChangeListener(() -> {
            refreshAll();
            if (!suppressAutosave) {
                dirty = true;
                saveStatus.setText("Saving...");
                autosave.restart();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        showScreen(DASHBOARD);
    }

    /** Opens the last project (or starts empty) and shows the window. */
    public void start() {
        String last = prefs.get(PREF_LAST, null);
        Path path = last != null ? Path.of(last) : defaultDirectory().resolve("seating-project.json");
        if (Files.exists(path)) {
            if (!loadFrom(path, true)) {
                Path backup = backupDamaged(path);
                projectPath = defaultDirectory().resolve("seating-project-" + LocalDateTime.now().format(FILE_STAMP) + ".json");
                JOptionPane.showMessageDialog(this, "Starting with an empty project."
                                + (backup != null ? "\nThe unreadable file was kept as:\n" + backup : ""),
                        "Project could not be opened", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            projectPath = path;
        }
        refreshAll();
        updateTitle();
        setVisible(true);
    }

    // ------------------------------------------------------------------ accessors for screens

    public SeatingPlanner planner() {
        return planner;
    }

    public String getProjectPath() {
        return projectPath == null ? "(none)" : projectPath.toString();
    }

    public String getSaveStatusText() {
        return saveStatusText;
    }

    // ------------------------------------------------------------------ layout

    private void addScreen(String key, String label, Icons.Glyph glyph, JComponent panel) {
        screens.put(key, (Screen) panel);
        content.add(panel, key);
        NavButton b = new NavButton(label, glyph, CONFLICTS.equals(key) ? new Color(0xDC2626) : new Color(0x334366));
        b.addActionListener(e -> showScreen(key));
        nav.put(key, b);
    }

    private JComponent buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(Theme.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(18, 12, 14, 12));
        side.setPreferredSize(new Dimension(224, 100));

        JLabel brand = new JLabel("<html><b style='font-size:13px'>Inclusive Event</b><br>"
                + "<span style='color:#b6c2d6'>Seating Planner</span></html>", new javax.swing.ImageIcon(appIcon(34)), JLabel.LEFT);
        brand.setForeground(Color.WHITE);
        brand.setIconTextGap(10);
        brand.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        brand.setBorder(new EmptyBorder(0, 6, 22, 0));
        side.add(brand);

        JLabel section = new JLabel("PLANNING");
        section.setFont(Theme.bold(10));
        section.setForeground(new Color(0x6B7A93));
        section.setBorder(new EmptyBorder(0, 10, 8, 0));
        section.setAlignmentX(LEFT_ALIGNMENT);
        side.add(section);
        for (NavButton b : nav.values()) {
            b.setAlignmentX(LEFT_ALIGNMENT);
            side.add(b);
            side.add(Box.createVerticalStrut(3));
        }
        side.add(Box.createVerticalGlue());
        saveStatus.setForeground(Theme.SIDEBAR_TEXT);
        saveStatus.setFont(Theme.small());
        saveStatus.setAlignmentX(LEFT_ALIGNMENT);
        saveStatus.setBorder(new EmptyBorder(0, 10, 0, 0));
        saveStatus.setIcon(Icons.of(Icons.Glyph.SAVE, 14, Theme.SIDEBAR_TEXT));
        saveStatus.setIconTextGap(8);
        side.add(saveStatus);
        return side;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(6, 16, 6, 16)));
        statusMessage.setFont(Theme.small());
        bar.add(statusMessage, BorderLayout.CENTER);
        JLabel hint = UiKit.muted("Ctrl+S save  ·  Ctrl+O open  ·  Ctrl+G generate seating");
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(item("New project", KeyEvent.VK_N, e -> newProject()));
        file.add(item("Open project...", KeyEvent.VK_O, e -> openProject()));
        file.add(item("Save", KeyEvent.VK_S, e -> saveProject()));
        JMenuItem saveAs = item("Save as...", 0, e -> saveProjectAs());
        saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        file.add(saveAs);
        file.addSeparator();
        file.add(item("Import attendees from CSV...", 0, e -> importAttendees()));
        file.add(item("Load demo project", 0, e -> loadDemo()));
        file.addSeparator();
        file.add(item("Export assignments CSV...", 0, e -> exportAssignmentsCsv()));
        file.add(item("Export conflict report...", 0, e -> exportConflictsCsv()));
        file.add(item("Print / preview seating report...", KeyEvent.VK_P, e -> showReportPreview()));
        file.addSeparator();
        file.add(item("Exit", 0, e -> exit()));
        bar.add(file);

        JMenu seating = new JMenu("Seating");
        seating.add(item("Generate seating (auto-assign)", KeyEvent.VK_G, e -> runAutoAssign()));
        seating.add(item("Register attendee...", KeyEvent.VK_R, e -> openNewAttendeeDialog()));
        seating.add(item("Re-validate conflicts", 0, e -> {
            planner.revalidate();
            showStatus("Re-validated the layout.", true);
        }));
        bar.add(seating);

        JMenu help = new JMenu("Help");
        help.add(item("How seating works", 0, e -> showHelp()));
        help.add(item("About", 0, e -> JOptionPane.showMessageDialog(this, "Inclusive Event Seating Planner\nVersion 1.0\n\n"
                + "Plans accessible, companion-aware seating with transparent conflict reporting.\nRuns fully offline. Data file:\n"
                + getProjectPath(), "About", JOptionPane.INFORMATION_MESSAGE)));
        bar.add(help);
        return bar;
    }

    private JMenuItem item(String text, int ctrlKey, java.awt.event.ActionListener action) {
        JMenuItem it = new JMenuItem(text);
        if (ctrlKey != 0) {
            it.setAccelerator(KeyStroke.getKeyStroke(ctrlKey, InputEvent.CTRL_DOWN_MASK));
        }
        it.addActionListener(action);
        return it;
    }

    public void showScreen(String key) {
        current = key;
        cards.show(content, key);
        nav.forEach((k, b) -> b.setActive(k.equals(key)));
        if (HALL.equals(key)) {
            hallPanel.loadFromHall();
        }
        screens.get(key).refresh();
    }

    private void refreshAll() {
        for (Screen s : screens.values()) {
            s.refresh();
        }
        updateBadges();
        updateTitle();
    }

    private void updateBadges() {
        SeatingPlanner.Stats st = planner.getStats();
        nav.get(CONFLICTS).setBadge(st.openErrors() > 0 ? String.valueOf(st.openErrors()) : null);
        nav.get(ATTENDEES).setBadge(st.registered() > 0 ? String.valueOf(st.registered()) : null);
    }

    private void updateTitle() {
        String event = planner.hasHall() ? planner.getHall().getEventName() + " - " : "";
        String file = projectPath == null ? "" : " (" + projectPath.getFileName() + ")";
        setTitle(event + "Inclusive Event Seating Planner" + file);
    }

    public void showStatus(String message, boolean ok) {
        statusMessage.setForeground(ok ? Theme.SUCCESS : Theme.DANGER);
        statusMessage.setIcon(Icons.of(ok ? Icons.Glyph.CHECK : Icons.Glyph.ALERT, 14, ok ? Theme.SUCCESS : Theme.DANGER));
        statusMessage.setText(message);
        statusClear.restart();
    }

    // ------------------------------------------------------------------ shared actions

    public void openNewAttendeeDialog() {
        Attendee a = new AttendeeDialog(this, planner.getProject(), null).showDialog();
        if (a != null) {
            planner.addAttendee(a);
            showStatus("Registered " + a.getId() + " (" + a.getFullName() + ").", true);
            attendeePanel.selectAttendee(a.getId());
        }
    }

    public void focusAttendeeInLayout(String attendeeId) {
        showScreen(LAYOUT);
        layoutPanel.focusAttendee(attendeeId);
    }

    public void runAutoAssign() {
        if (!planner.hasHall()) {
            JOptionPane.showMessageDialog(this, "Configure the event hall first.", "No hall", JOptionPane.INFORMATION_MESSAGE);
            showScreen(HALL);
            return;
        }
        if (planner.getProject().getAttendeeCount() == 0) {
            JOptionPane.showMessageDialog(this, "Register attendees before generating seating.", "No attendees",
                    JOptionPane.INFORMATION_MESSAGE);
            showScreen(ATTENDEES);
            return;
        }
        boolean preserve = false;
        int existing = planner.getProject().getAssignment().size();
        if (existing > 0) {
            Object[] options = {"Keep valid assignments", "Start fresh", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this, existing + " attendee(s) are already seated.\n\n"
                            + "Keep valid assignments: only seats unassigned attendees (recommended after manual edits).\n"
                            + "Start fresh: clears all seats and recalculates the whole plan.",
                    "Generate seating", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 2 || choice < 0) {
                return;
            }
            preserve = choice == 0;
            if (!preserve) {
                int ok = JOptionPane.showConfirmDialog(this, "This discards all " + existing + " current assignments, including manual changes. Continue?",
                        "Start fresh", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) {
                    return;
                }
            }
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SeatingAllocator.AllocationResult r;
        try {
            r = planner.autoAssign(preserve);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
        SeatingPlanner.Stats st = planner.getStats();
        StringBuilder msg = new StringBuilder();
        msg.append("Seated ").append(r.assignedTotal()).append(" of ").append(planner.getProject().getAttendeeCount()).append(" attendees");
        if (preserve) {
            msg.append(" (kept ").append(r.keptAssignments()).append(", added ").append(r.newlyAssigned()).append(")");
        }
        msg.append(".\n");
        if (r.releasedInvalid() > 0) {
            msg.append(r.releasedInvalid()).append(" invalid assignment(s) were released and re-planned.\n");
        }
        if (r.relocatedFromAccessible() > 0) {
            msg.append(r.relocatedFromAccessible()).append(" attendee(s) without accessibility needs were moved out of accessible seats.\n");
        }
        if (r.unassignedTotal() > 0) {
            msg.append("\n").append(r.unassignedTotal()).append(" attendee(s) could not be seated without breaking a mandatory requirement ")
                    .append("and were left unassigned.\n");
        }
        msg.append("\nOpen errors: ").append(st.openErrors()).append("   Open warnings: ").append(st.openWarnings());
        msg.append(planner.isPlanFullyValid() ? "\n\nAll mandatory requirements are satisfied."
                : "\n\nThe plan is NOT fully valid yet - review the Conflicts screen.");
        showStatus("Auto-assign complete: " + r.assignedTotal() + " seated, " + r.unassignedTotal() + " unassigned.", st.openErrors() == 0);
        if (st.openErrors() > 0) {
            Object[] options = {"Review conflicts", "View layout"};
            int choice = JOptionPane.showOptionDialog(this, msg.toString(), "Seating generated", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            showScreen(choice == 0 ? CONFLICTS : LAYOUT);
        } else {
            JOptionPane.showMessageDialog(this, msg.toString(), "Seating generated", JOptionPane.INFORMATION_MESSAGE);
            showScreen(LAYOUT);
        }
    }

    // ------------------------------------------------------------------ project files

    public void newProject() {
        int ok = JOptionPane.showConfirmDialog(this, "Start a new, empty project?\nThe current project stays saved at:\n" + getProjectPath(),
                "New project", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION || !flushSave()) {
            return;
        }
        replace(new Project(), defaultDirectory().resolve("seating-project-" + LocalDateTime.now().format(FILE_STAMP) + ".json"));
        showScreen(HALL);
        showStatus("New project created.", true);
    }

    public void loadDemo() {
        if (planner.getProject().getAttendeeCount() > 0 || planner.hasHall()) {
            int ok = JOptionPane.showConfirmDialog(this, "Load the demo project in a new file?\nYour current project stays saved at:\n"
                    + getProjectPath(), "Load demo project", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION || !flushSave()) {
                return;
            }
        }
        replace(DemoData.create(), defaultDirectory().resolve("demo-project-" + LocalDateTime.now().format(FILE_STAMP) + ".json"));
        showScreen(DASHBOARD);
        showStatus("Demo project loaded: 78 attendees, 9x14 hall. Click 'Generate seating' to plan it.", true);
    }

    private void replace(Project project, Path path) {
        suppressAutosave = true;
        try {
            planner.replaceProject(project, List.of(), 1);
        } finally {
            suppressAutosave = false;
        }
        setProjectPath(path);
        saveQuietly();
        hallPanel.loadFromHall();
    }

    public void openProject() {
        JFileChooser fc = chooser("Open project", "Seating project (*.json)", "json");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || !flushSave()) {
            return;
        }
        if (loadFrom(fc.getSelectedFile().toPath(), false)) {
            showScreen(DASHBOARD);
        }
    }

    /** Loads a project file; returns false (after telling the user) if it cannot be read. */
    private boolean loadFrom(Path path, boolean startup) {
        FileStorageService.LoadResult result;
        try {
            result = storage.load(path);
        } catch (StorageException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not open project", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        List<String> released;
        suppressAutosave = true;
        try {
            released = planner.replaceProject(result.project(), result.conflicts(), result.nextConflictNumber());
        } finally {
            suppressAutosave = false;
        }
        setProjectPath(path);
        dirty = false;
        saveStatusText = "Loaded " + LocalDateTime.now().format(CLOCK);
        saveStatus.setText(saveStatusText);
        hallPanel.loadFromHall();
        List<String> notes = new java.util.ArrayList<>(result.warnings());
        if (!released.isEmpty()) {
            notes.add(released.size() + " saved assignment(s) no longer satisfied a mandatory requirement and were released: "
                    + String.join(", ", released));
        }
        if (!notes.isEmpty()) {
            showLongMessage("The project was opened, but some saved data needed attention:", notes);
            dirty = true;
            autosave.restart();
        }
        if (!startup) {
            showStatus("Opened " + path.getFileName() + ".", true);
        }
        return true;
    }

    public void saveProject() {
        if (projectPath == null) {
            saveProjectAs();
            return;
        }
        try {
            storage.save(planner, projectPath);
            markSaved();
            showStatus("Saved to " + projectPath, true);
        } catch (StorageException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveProjectAs() {
        JFileChooser fc = chooser("Save project as", "Seating project (*.json)", "json");
        fc.setSelectedFile(new File(projectPath == null ? "seating-project.json" : projectPath.getFileName().toString()));
        Path target = chooseSaveTarget(fc, "json");
        if (target == null) {
            return;
        }
        try {
            storage.save(planner, target);
            setProjectPath(target);
            markSaved();
            showStatus("Saved to " + target, true);
        } catch (StorageException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void importAttendees() {
        JFileChooser fc = chooser("Import attendees", "CSV files (*.csv)", "csv");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            FileStorageService.ImportResult r = storage.importAttendeesCsv(fc.getSelectedFile().toPath(), planner.getProject());
            if (r.attendees().isEmpty()) {
                showLongMessage("No attendees could be imported.", r.warnings());
                return;
            }
            String text = "Import " + r.attendees().size() + " attendee(s)?" + (r.warnings().isEmpty() ? "" : "\n" + r.warnings().size()
                    + " row(s) will be skipped (details follow).");
            if (JOptionPane.showConfirmDialog(this, text, "Import attendees", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return;
            }
            planner.addAttendees(r.attendees());
            if (!r.warnings().isEmpty()) {
                showLongMessage("Imported " + r.attendees().size() + " attendee(s). Skipped rows:", r.warnings());
            }
            showStatus("Imported " + r.attendees().size() + " attendee(s).", true);
            showScreen(ATTENDEES);
        } catch (StorageException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Import failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ exports

    public void exportAssignmentsCsv() {
        Path target = exportTarget("Export seat assignments", "seat-assignments.csv", "CSV files (*.csv)", "csv");
        if (target != null) {
            runExport(() -> exporter.exportAssignmentsCsv(planner, target), target);
        }
    }

    public void exportConflictsCsv() {
        Path target = exportTarget("Export conflict report", "conflict-report.csv", "CSV files (*.csv)", "csv");
        if (target != null) {
            runExport(() -> exporter.exportConflictsCsv(planner, target), target);
        }
    }

    public void exportHtmlReport() {
        Path target = exportTarget("Save seating plan report", "seating-plan.html", "HTML report (*.html)", "html");
        if (target != null) {
            runExport(() -> exporter.exportHtmlReport(planner, target), target);
        }
    }

    public void showReportPreview() {
        new ReportPreviewDialog(this, exporter.buildHtmlReport(planner), this::exportHtmlReport).setVisible(true);
    }

    private interface ExportJob {
        void run() throws StorageException;
    }

    private void runExport(ExportJob job, Path target) {
        try {
            job.run();
            showStatus("Exported " + target, true);
            JOptionPane.showMessageDialog(this, "Saved:\n" + target, "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (StorageException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path exportTarget(String title, String defaultName, String description, String ext) {
        JFileChooser fc = chooser(title, description, ext);
        String prefix = planner.hasHall() ? planner.getHall().getEventName().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "") + "-" : "";
        fc.setSelectedFile(new File(prefix.toLowerCase() + defaultName));
        return chooseSaveTarget(fc, ext);
    }

    /** Shows a save dialog, enforces the extension and confirms overwriting. */
    private Path chooseSaveTarget(JFileChooser fc, String ext) {
        while (true) {
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith("." + ext)) {
                f = new File(f.getParentFile(), f.getName() + "." + ext);
            }
            if (f.exists()) {
                int ok = JOptionPane.showConfirmDialog(this, f.getName() + " already exists. Replace it?", "Confirm overwrite",
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ok == JOptionPane.NO_OPTION) {
                    continue;
                }
                if (ok != JOptionPane.YES_OPTION) {
                    return null;
                }
            }
            return f.toPath();
        }
    }

    private JFileChooser chooser(String title, String description, String ext) {
        JFileChooser fc = new JFileChooser(projectPath != null && projectPath.getParent() != null
                ? projectPath.getParent().toFile() : defaultDirectory().toFile());
        fc.setDialogTitle(title);
        fc.setFileFilter(new FileNameExtensionFilter(description, ext));
        return fc;
    }

    // ------------------------------------------------------------------ saving

    private void setProjectPath(Path path) {
        projectPath = path.toAbsolutePath();
        prefs.put(PREF_LAST, projectPath.toString());
        updateTitle();
    }

    private void markSaved() {
        dirty = false;
        autosaveErrorShown = false;
        saveStatusText = "All changes saved · " + LocalDateTime.now().format(CLOCK);
        saveStatus.setText(saveStatusText);
        screens.get(EXPORT).refresh();
    }

    /** Autosave: never interrupts the user except for the first failure. */
    private void saveQuietly() {
        if (projectPath == null) {
            return;
        }
        try {
            storage.save(planner, projectPath);
            markSaved();
        } catch (StorageException ex) {
            saveStatusText = "Autosave failed";
            saveStatus.setText(saveStatusText);
            showStatus(ex.getMessage(), false);
            if (!autosaveErrorShown) {
                autosaveErrorShown = true;
                JOptionPane.showMessageDialog(this, ex.getMessage() + "\n\nUse File > Save as... to choose another location.",
                        "Autosave failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Writes pending changes immediately; returns false if the user chose to stay after a failure. */
    private boolean flushSave() {
        autosave.stop();
        if (!dirty) {
            return true;
        }
        saveQuietly();
        if (!dirty) {
            return true;
        }
        return JOptionPane.showConfirmDialog(this, "Your latest changes could not be saved. Continue anyway and lose them?",
                "Unsaved changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void exit() {
        if (flushSave()) {
            dispose();
            System.exit(0);
        }
    }

    private static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), "InclusiveSeatingPlanner");
    }

    private static Path backupDamaged(Path path) {
        try {
            Path backup = path.resolveSibling(path.getFileName() + ".damaged-" + LocalDateTime.now().format(FILE_STAMP));
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ dialogs

    private void showLongMessage(String header, List<String> lines) {
        JTextArea area = new JTextArea(String.join("\n", lines));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(Math.min(12, lines.size() + 1));
        area.setColumns(60);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel(header), BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, p, "Please review", JOptionPane.WARNING_MESSAGE);
    }

    private void showHelp() {
        String html = "<html><div style='width:520px'>"
                + "<h3>How automatic seating works</h3><ol>"
                + "<li>Seats reserved for a specific attendee ID go to that attendee; other reserved seats stay empty.</li>"
                + "<li><b>Mandatory requirements first:</b> attendees needing accessible seats, then front-section and aisle seats, "
                + "are placed before anyone else. Companion pairs are only ever placed on two adjacent seats with no aisle between.</li>"
                + "<li><b>Groups</b> are seated in one contiguous run in a row when possible; otherwise as a compact cluster.</li>"
                + "<li>Everyone else is seated front-to-back, avoiding accessible seats while standard seats remain.</li></ol>"
                + "<p>If a mandatory requirement cannot be met, the attendee is <b>left unassigned</b> and an <b>Error</b> explains why. "
                + "Relaxed preferences are reported as <b>Warnings</b>. The planner never reports a plan as valid while errors remain.</p>"
                + "<h3>Manual changes</h3><p>Every assign / move / swap is validated first. Changes that would break an accessibility or "
                + "companion requirement are rejected with an explanation (and, where possible, a suggested fix).</p>"
                + "<h3>Conflict statuses</h3><p><b>Open</b> - still present. <b>Addressed</b> - re-validation no longer finds it; "
                + "confirm to mark <b>Resolved</b>. <b>Acknowledged</b> - a warning you accepted.</p></div></html>";
        JOptionPane.showMessageDialog(this, html, "How seating works", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ sidebar button & icon

    /** Sidebar navigation entry with active/hover states and an optional count badge. */
    private static class NavButton extends JButton {
        private final Icons.Glyph glyph;
        private final Color badgeColor;
        private boolean active;
        private boolean hover;
        private String badge;

        NavButton(String text, Icons.Glyph glyph, Color badgeColor) {
            super(text);
            this.glyph = glyph;
            this.badgeColor = badgeColor;
            setUI(new BasicButtonUI());
            setIcon(Icons.of(glyph, 18, Theme.SIDEBAR_TEXT));
            setHorizontalAlignment(LEFT);
            setIconTextGap(12);
            setFont(Theme.font(java.awt.Font.PLAIN, 13));
            setForeground(Theme.SIDEBAR_TEXT);
            setBorder(new EmptyBorder(9, 12, 9, 12));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            Color fg = active ? Color.WHITE : Theme.SIDEBAR_TEXT;
            setForeground(fg);
            setFont(Theme.font(active ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 13));
            setIcon(Icons.of(glyph, 18, fg));
            getAccessibleContext().setAccessibleDescription(active ? "current screen" : null);
            repaint();
        }

        void setBadge(String badge) {
            this.badge = badge;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active || hover) {
                g.setColor(active ? Theme.SIDEBAR_ACTIVE : Theme.SIDEBAR_HOVER);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            if (active) {
                g.setColor(new Color(0x7C9BFF));
                g.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
            }
            if (badge != null) {
                g.setFont(Theme.bold(11));
                int w = Math.max(22, g.getFontMetrics().stringWidth(badge) + 12);
                int h = 18;
                int x = getWidth() - w - 8;
                int y = (getHeight() - h) / 2;
                g.setColor(badgeColor);
                g.fillRoundRect(x, y, w, h, h, h);
                g.setColor(Color.WHITE);
                g.drawString(badge, x + (w - g.getFontMetrics().stringWidth(badge)) / 2, y + 13);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** The app icon: a small seat grid on an indigo tile, drawn in code. */
    static Image appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.PRIMARY);
        g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
        double cell = size / 5.0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                boolean accent = r == 2 && c == 0;
                g.setColor(accent ? new Color(0x6EE7B7) : Color.WHITE);
                g.fillRoundRect((int) (cell * (0.9 + c * 1.15)), (int) (cell * (1.05 + r * 1.15)), (int) Math.ceil(cell * 0.85),
                        (int) Math.ceil(cell * 0.7), Math.max(2, size / 12), Math.max(2, size / 12));
            }
        }
        g.dispose();
        return img;
    }
}
