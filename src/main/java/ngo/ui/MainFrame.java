package ngo.ui;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;
import ngo.ui.browse.MasterDetailForm;
import ngo.ui.browse.SearchForm;
import ngo.ui.edit.EditingValidationPanel;
import ngo.ui.grid.BrowseForm;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import ngo.ui.report.ReportForm;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import ngo.ui.edit.EditTaskForm;

/**
 * Main application window: central launcher for all forms.
 * Run this class (ngo.ui.MainFrame) to use the entire application.
 *
 * To add a new form: add an addButton(...) line in the corresponding group inside the constructor.
 */
public class MainFrame extends JFrame {
        private static final Color BG = new Color(0xF7F7F5);
    private static final Color LINE = new Color(0xE5E5E5);
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color MUTED = new Color(0x5F5E5A);
    private static final Color HOVER = new Color(0xE6F1FB);
    private final JLabel lblStatus = new JLabel(" Checking MySQL connection...");
    private final Map<String, JFrame> openWindows = new HashMap<>();

    public MainFrame() {
        super("NGO Task Management");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

                JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(28, 40, 20, 40));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("NGO Volunteer Task Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT);
        g.gridy = 0;
        g.insets = new Insets(0, 0, 4, 0);
        root.add(title, g);

        JLabel sub = new JLabel("Choose a screen to open");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(MUTED);
        g.gridy = 1;
        g.insets = new Insets(0, 0, 22, 0);
        root.add(sub, g);

        g.gridy = 2;
        g.insets = new Insets(0, 0, 8, 0);
        root.add(sectionLabel("View data"), g);

        JPanel view = new JPanel(new GridLayout(2, 2, 12, 12));
        view.setOpaque(false);
        addCard(view, "Task list", "Browse all tasks by project", "browse", () -> new BrowseForm());
        addCard(view, "Projects & tasks", "Master-detail view", "masterdetail", () -> new MasterDetailForm());
        addCard(view, "Search tasks", "Filter by keyword, status, priority", "search", () -> new SearchForm());
        addCard(view, "Report", "Summary by project and volunteer", "report", () -> new ReportForm());
        g.gridy = 3;
        g.insets = new Insets(0, 0, 22, 0);
        root.add(view, g);

        g.gridy = 4;
        g.insets = new Insets(0, 0, 8, 0);
        root.add(sectionLabel("Edit data"), g);

        JPanel edit = new JPanel(new GridLayout(1, 1));
        edit.setOpaque(false);
                addCard(edit, "Edit task", "Create, update and delete tasks", "editing", () -> new EditTaskForm());
        g.gridy = 5;
        g.insets = new Insets(0, 0, 18, 0);
        root.add(edit, g);

        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(MUTED);
        g.gridy = 6;
        g.insets = new Insets(0, 0, 0, 0);
        root.add(lblStatus, g);

        setContentPane(root);
        setSize(720, 620);
        setLocationRelativeTo(null);
        checkConnection();
    }

        private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        l.setForeground(TEXT);
        return l;
    }

    /** One rounded "card" button: a name, a short description, and a click that opens a form. */
    private void addCard(JPanel panel, String name, String desc, String key, Supplier<JFrame> factory) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 2)) {
            private boolean hover;

            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) { open(key, factory); }
                });
            }

            @Override
            protected void paintComponent(Graphics gr) {
                Graphics2D g2 = (Graphics2D) gr.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? HOVER : BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lblName = new JLabel(name);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblName.setForeground(TEXT);
        JLabel lblDesc = new JLabel(desc);
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDesc.setForeground(MUTED);
        card.add(lblName);
        card.add(lblDesc);
        panel.add(card);
    }

    /** Opens a form. If clicked again while already open, brings it to the front instead of duplicating. */
    private void open(String key, Supplier<JFrame> factory) {
        JFrame existing = openWindows.get(key);
        if (existing != null && existing.isDisplayable()) {
            existing.toFront();
            return;
        }
        try {
            JFrame f = factory.get();
            // Some forms set EXIT_ON_CLOSE: override so closing a child form does not exit the whole app.
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setLocationRelativeTo(this);
            f.setVisible(true);
            openWindows.put(key, f);
        } catch (RuntimeException ex) {
            String msg = (ex instanceof DataAccessException || ex instanceof IllegalArgumentException)
                    ? ex.getMessage()
                    : "Unable to open form. Please try again.";
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Tests the MySQL connection on a background thread and displays the result in the status bar. */
    private void checkConnection() {
        new SwingWorker<String, Void>() {
            private boolean ok;

            @Override
            protected String doInBackground() {
                try (Connection c = DBConnection.getConnection()) {
                    ok = true;
                    return " Connected to MySQL";
                } catch (SQLException e) {
                    return " " + DataAccessException.from(e).getMessage();
                } catch (Throwable t) {   // e.g., missing config/db.properties file
                    Throwable root = (t instanceof ExceptionInInitializerError && t.getCause() != null)
                            ? t.getCause()
                            : t;
                    return " " + (root.getMessage() != null ? root.getMessage() : "Unable to connect to the database.");
                }
            }

            @Override
            protected void done() {
                try {
                    lblStatus.setText(get());
                    lblStatus.setForeground(ok ? new Color(0, 120, 0) : new Color(180, 0, 0));
                } catch (Exception ignored) {
                    // keep previous message
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // use default look and feel
        }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
