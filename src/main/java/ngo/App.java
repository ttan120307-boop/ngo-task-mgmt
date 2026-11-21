package ngo;

import ngo.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point of the application: run THIS class. It shows the log-in screen first. */
public final class App {
    private App() { }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look and feel
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
