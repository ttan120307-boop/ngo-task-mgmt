package ngo;

import ngo.db.DatabaseInitializer;
import ngo.ui.LoginFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point of the application: run THIS class. */
public final class App {
    private App() { }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look and feel
        }

        // Automatically initialize database schema and seed data if missing
        DatabaseInitializer.ensureInitialized();

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}