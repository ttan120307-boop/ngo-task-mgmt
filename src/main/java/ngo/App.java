package ngo;

import ngo.db.DatabaseInitializer;
import ngo.ui.LoginFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class App {
    private App() { }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        
        DatabaseInitializer.ensureInitialized();

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}