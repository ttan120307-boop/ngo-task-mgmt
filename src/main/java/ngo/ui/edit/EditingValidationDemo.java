package ngo.ui.edit;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Standalone entry point for testing the Editing & Validation tabs. */
public class EditingValidationDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("NGO Task Management – Editing & Validation");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new EditingValidationPanel());
            frame.setSize(1180, 820);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
