package com.seatingplanner;

import com.seatingplanner.ui.MainFrame;
import com.seatingplanner.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Entry point for the Inclusive Event Seating Planner. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            // Report unexpected errors instead of failing silently.
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "An unexpected error occurred: " + e + "\nYour data is autosaved after every change.",
                        "Unexpected error", JOptionPane.ERROR_MESSAGE));
            });
            new MainFrame().start();
        });
    }
}
