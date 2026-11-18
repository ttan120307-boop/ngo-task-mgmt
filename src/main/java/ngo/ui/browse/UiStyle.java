package ngo.ui.browse;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

public class UiStyle {

    static final Color SIDEBAR_BG = new Color(0xF7F7F5);
    static final Color LINE = new Color(0xE5E5E5);
    static final Color TEXT = new Color(0x1A1A1A);
    static final Color MUTED = new Color(0x5F5E5A);
    static final Color SELECT_BG = new Color(0xE6F1FB);
    static final Color SELECT_FG = new Color(0x0C447C);
    static final Font FONT = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);

    public static void apply(JFrame f, String active) {
        f.getContentPane().setBackground(Color.WHITE);
        style(f.getContentPane());
        f.getContentPane().add(sidebar(active, f), BorderLayout.WEST);
        f.setSize(1200, 700);
        f.setLocationRelativeTo(null);
    }

    private static void style(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTable || k instanceof JTableHeader) {
                continue;
            }
            if (k instanceof JPanel) {
                k.setBackground(Color.WHITE);
            } else if (k instanceof JLabel) {
                k.setFont(FONT_BOLD);
                k.setForeground(TEXT);
            } else if (k instanceof JTextField || k instanceof JComboBox || k instanceof JButton) {
                k.setFont(FONT);
            }
            if (k instanceof Container) {
                style((Container) k);
            }
        }
    }

    public static void styleTable(JTable t) {
        t.setFont(FONT);
        t.setRowHeight(34);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(LINE);
        t.setSelectionBackground(SELECT_BG);
        t.setSelectionForeground(SELECT_FG);
        t.setFillsViewportHeight(true);
        t.setDefaultRenderer(Object.class, new CellRenderer());
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 36));
        h.setDefaultRenderer(new HeaderRenderer());
    }

    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, false, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setOpaque(true);
            if (selected) {
                setBackground(SELECT_BG);
                setForeground(SELECT_FG);
                return this;
            }
            setBackground(Color.WHITE);
            setForeground(TEXT);
            String s = (value == null) ? "" : value.toString();
            switch (s) {
                case "DONE":
                    setBackground(new Color(0xEAF3DE));
                    setForeground(new Color(0x27500A));
                    break;
                case "IN_PROGRESS":
                    setBackground(new Color(0xE6F1FB));
                    setForeground(new Color(0x0C447C));
                    break;
                case "TODO":
                    setBackground(new Color(0xF1EFE8));
                    setForeground(new Color(0x444441));
                    break;
                case "CANCELLED":
                    setBackground(new Color(0xFCEBEB));
                    setForeground(new Color(0x791F1F));
                    break;
                case "URGENT":
                    setForeground(new Color(0xA32D2D));
                    break;
                default:
                    break;
            }
            return this;
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, false, false, row, col);
            setOpaque(true);
            setBackground(SIDEBAR_BG);
            setForeground(MUTED);
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setHorizontalAlignment(LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                    BorderFactory.createEmptyBorder(0, 10, 0, 10)));
            return this;
        }
    }

  
    
private static JPanel sidebar(String active, final JFrame current) {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBackground(SIDEBAR_BG);

    p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, LINE),
            BorderFactory.createEmptyBorder(16, 10, 16, 10)));

    // Sidebar width
    p.setPreferredSize(new Dimension(140, 600));

    // Application name
    JLabel brand = new JLabel("NGO Tasks");
    brand.setFont(new Font("Segoe UI", Font.BOLD, 16));
    brand.setForeground(TEXT);
    brand.setBorder(BorderFactory.createEmptyBorder(0, 8, 18, 0));
    brand.setAlignmentX(Component.LEFT_ALIGNMENT);
    p.add(brand);

    // Menu items and icons
    String[] names = {
        "Browse",
        "Search tasks",
        "Projects",
        "Edit task",
        "Report"
    };


    for (int i = 0; i < names.length; i++) {
        final String name = names[i];

        JLabel item = new JLabel(name);

        boolean selected = name.equals(active);

        item.setFont(FONT);
        item.setOpaque(true);

        item.setBackground(selected ? Color.WHITE : SIDEBAR_BG);
        item.setForeground(selected ? TEXT : MUTED);

        item.setBorder(BorderFactory.createEmptyBorder(
                0, 10, 0, 4));

        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        item.setMaximumSize(new Dimension(120, 36));
        item.setPreferredSize(new Dimension(120, 36));

        item.setCursor(Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR));

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open(name, current);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!name.equals(active)) {
                    item.setBackground(new Color(0xEEEEEC));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!name.equals(active)) {
                    item.setBackground(SIDEBAR_BG);
                }
            }
        });

        p.add(item);
        p.add(Box.createVerticalStrut(4));
    }

    return p;
}


    private static void open(String name, JFrame current) {
        if (name.equals("Search tasks")) {
            if (!(current instanceof SearchForm)) {
                new SearchForm().setVisible(true);
                current.dispose();
            }
                    } else if (name.equals("Browse")) {
            if (!(current instanceof ngo.ui.grid.BrowseForm)) {
                new ngo.ui.grid.BrowseForm().setVisible(true);
                current.dispose();
            }
        } else if (name.equals("Projects")) {
            if (!(current instanceof MasterDetailForm)) {
                new MasterDetailForm().setVisible(true);
                current.dispose();
            }
        } else {
            JOptionPane.showMessageDialog(current, name + ": màn hình này đang được làm.");
        }
    }
}
