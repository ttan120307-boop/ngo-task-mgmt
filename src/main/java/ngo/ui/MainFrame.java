package ngo.ui;

import ngo.auth.AuthUser;
import ngo.auth.Session;
import ngo.db.DBConnection;
import ngo.ui.browse.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Home screen shown after log-in: welcome, role, a few figures and shortcuts that depend on the role.
 * Start the application with ngo.App (the log-in screen), not with this class.
 */
public class MainFrame extends JFrame {
    private final JLabel[] values = new JLabel[4];

    public MainFrame() {
        if (!Session.isLoggedIn()) throw new IllegalStateException("Please log in first.");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        AuthUser me = Session.user();

        JPanel root = new JPanel(new BorderLayout(0, 20));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JPanel head = new JPanel(new GridLayout(0, 1, 0, 4));
        head.setBackground(Color.WHITE);
        JLabel hello = new JLabel("Welcome, " + me.getFullName());
        hello.setFont(UiStyle.FONT_TITLE);
        JLabel sub = new JLabel(describe(me));
        sub.setFont(UiStyle.FONT);
        sub.setForeground(UiStyle.MUTED);
        head.add(hello);
        head.add(sub);
        root.add(head, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 4, 14, 0));
        cards.setBackground(Color.WHITE);
        String[] names = {"Projects", "Open tasks", "My open tasks", "Overdue open tasks"};
        for (int i = 0; i < 4; i++) {
            values[i] = new JLabel("-");
            cards.add(card(names[i], values[i]));
        }
        JPanel center = new JPanel(new BorderLayout(0, 24));
        center.setBackground(Color.WHITE);
        center.add(cards, BorderLayout.NORTH);

        JPanel shortcuts = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        shortcuts.setBackground(Color.WHITE);
        for (String m : UiStyle.menuFor(me.getRole())) {
            if (m.equals("Home")) continue;
            JButton b = new JButton(m);
            UiStyle.styleButton(b, !m.equals("My tasks"));
            b.addActionListener(e -> UiStyle.go(m, this));
            shortcuts.add(b);
        }
        JLabel scTitle = new JLabel("Shortcuts");
        scTitle.setFont(UiStyle.FONT_BOLD);
        JPanel sc = new JPanel(new BorderLayout(0, 10));
        sc.setBackground(Color.WHITE);
        sc.add(scTitle, BorderLayout.NORTH);
        sc.add(shortcuts, BorderLayout.CENTER);
        center.add(sc, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        add(root, BorderLayout.CENTER);
        UiStyle.apply(this, "Home");
        loadFigures(me.getVolunteerId());
    }

    private static String describe(AuthUser me) {
        switch (me.getRole()) {
            case ADMIN: return "Admin - full access to all projects, tasks, teams and reports.";
            case MANAGER: return "Manager - you manage your own projects and the teams you lead; other data is read-only.";
            default: return "User - you can browse everything, update your own tasks and write comments.";
        }
    }

    private static JPanel card(String name, JLabel value) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.setBackground(UiStyle.SIDEBAR_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.LINE), BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        JLabel l = new JLabel(name);
        l.setFont(UiStyle.FONT);
        l.setForeground(UiStyle.MUTED);
        value.setFont(new Font("Segoe UI", Font.BOLD, 28));
        value.setForeground(UiStyle.TEXT);
        p.add(l);
        p.add(value);
        return p;
    }

    private void loadFigures(int me) {
        String[] sql = {
            "SELECT COUNT(*) FROM PROJECT",
            "SELECT COUNT(*) FROM TASK WHERE status NOT IN ('DONE','CANCELLED')",
            "SELECT COUNT(*) FROM TASK t JOIN TASK_ASSIGNMENT a ON a.task_id = t.task_id "
                + "WHERE a.volunteer_id = ? AND t.status NOT IN ('DONE','CANCELLED')",
            "SELECT COUNT(*) FROM TASK WHERE status NOT IN ('DONE','CANCELLED') AND due_date < CURDATE()"
        };
        UiStyle.async(this, () -> {
            String[] out = new String[4];
            try (Connection c = DBConnection.getConnection()) {
                for (int i = 0; i < 4; i++) {
                    try (PreparedStatement ps = c.prepareStatement(sql[i])) {
                        if (i == 2) ps.setInt(1, me);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            out[i] = String.valueOf(rs.getInt(1));
                        }
                    }
                }
            } catch (SQLException e) {
                throw ngo.db.DataAccessException.from(e);
            }
            return out;
        }, out -> { for (int i = 0; i < 4; i++) values[i].setText(out[i]); });
    }
}
