package com.seatingplanner.ui;

import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;

/** Shows the generated seating plan report and lets the organizer print it or save it as HTML. */
public class ReportPreviewDialog extends JDialog {

    public ReportPreviewDialog(Window owner, String html, Runnable saveAction) {
        super(owner, "Seating plan report", ModalityType.APPLICATION_MODAL);
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(pane);
        sp.getVerticalScrollBar().setUnitIncrement(16);

        UiKit.AppButton print = UiKit.primary("Print...", Icons.Glyph.PRINT);
        print.addActionListener(e -> {
            try {
                pane.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Printing failed: " + ex.getMessage(), "Print", JOptionPane.ERROR_MESSAGE);
            }
        });
        UiKit.AppButton save = UiKit.secondary("Save as HTML...", Icons.Glyph.SAVE);
        save.addActionListener(e -> saveAction.run());
        UiKit.AppButton close = UiKit.secondary("Close", null);
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(new EmptyBorder(10, 12, 10, 12));
        buttons.add(close);
        buttons.add(save);
        buttons.add(print);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sp, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JRootPane.WHEN_IN_FOCUSED_WINDOW);
        setSize(new Dimension(1000, 760));
        setLocationRelativeTo(owner);
    }
}
