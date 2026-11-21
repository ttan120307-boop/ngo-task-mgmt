package ngo.ui.report;

import ngo.dao.ReportDAO;
import ngo.db.DataAccessException;
import ngo.ui.browse.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Report screen: two summary reports (GROUP BY queries from ReportDAO) shown in one grid.
 * Report 1 = one row per project, report 2 = one row per volunteer.
 * Same look as Search / Browse / Master-detail (sidebar from UiStyle).
 */
public class ReportForm extends JFrame {

    private static final String[] PROJECT_HEADERS = {"ID", "Project", "Owner", "Total tasks",
        "TODO", "In progress", "Done", "Cancelled", "Overdue", "% done"};
    private static final int[] PROJECT_WIDTHS = {40, 230, 160, 90, 60, 90, 70, 80, 70, 80};

    private static final String[] VOLUNTEER_HEADERS = {"ID", "Volunteer", "Assigned tasks",
        "In progress", "Done", "Overdue"};
    private static final int[] VOLUNTEER_WIDTHS = {40, 260, 130, 110, 100, 100};

    private static final String[] BLOCKED_HEADERS = ReportDAO.BLOCKED_COLUMNS;
    private static final int[] BLOCKED_WIDTHS = {40, 260, 230, 150, 160};
    private static final String[] COMPLETED_HEADERS = ReportDAO.COMPLETED_COLUMNS;
    private static final int[] COMPLETED_WIDTHS = {40, 300, 200, 130};
    private static final String[] TOP_HEADERS = ReportDAO.TOP_COMMENTER_COLUMNS;
    private static final int[] TOP_WIDTHS = {40, 300, 160};
    private static final String[] MODE_NAMES = {"By project", "By volunteer", "Blocked tasks",
        "Completed projects", "Top commenter"};

    private static final Color LINE = new Color(0xD3D1C7);
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color MUTED = new Color(0x5F5E5A);

    private final ReportDAO reportDAO = new ReportDAO();

    private final RoundButton[] modeBtns = new RoundButton[5];
    private RoundButton refreshBtn;
    private final JLabel countLabel = new JLabel(" ");
    private final JTable table = new JTable();
    private int mode = 0;                 // which report is shown (index of MODE_NAMES)
    private int loadSeq = 0;              // ignore answers of older loads

    public ReportForm() {
        super("Report");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        UiStyle.apply(this, "Report");
        add(buildMainPanel(), BorderLayout.CENTER);

        table.setAutoCreateRowSorter(true);            // click a column header to sort
        UiStyle.styleTable(table);
        // numbers are Integer: use the same coloured renderer so the table looks the same
        table.setDefaultRenderer(Integer.class, table.getDefaultRenderer(Object.class));

        updateButtons();
        loadReport();
    }

    private JPanel buildMainPanel() {
        JLabel title = new JLabel("Report");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT);

        refreshBtn = new RoundButton("Refresh", 10);
        refreshBtn.filled();
        refreshBtn.addActionListener(e -> loadReport());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(Color.WHITE);
        buttons.add(refreshBtn);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(Color.WHITE);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(buttons, BorderLayout.EAST);

        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chipRow.setBackground(Color.WHITE);
        for (int i = 0; i < modeBtns.length; i++) {
            final int m = i;
            modeBtns[i] = new RoundButton(MODE_NAMES[i], 40);
            modeBtns[i].setFont(new Font("Segoe UI", Font.PLAIN, 12));
            modeBtns[i].setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            modeBtns[i].addActionListener(e -> selectReport(m));
            chipRow.add(modeBtns[i]);
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.getViewport().setBackground(Color.WHITE);

        countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        countLabel.setForeground(MUTED);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.BOTH;

        g.gridy = 0;
        g.weighty = 0;
        g.insets = new Insets(18, 16, 12, 16);
        main.add(titleRow, g);

        g.gridy = 1;
        g.insets = new Insets(0, 16, 12, 16);
        main.add(chipRow, g);

        g.gridy = 2;
        g.weighty = 1;
        g.insets = new Insets(4, 16, 4, 16);
        main.add(scroll, g);

        g.gridy = 3;
        g.weighty = 0;
        g.insets = new Insets(4, 16, 12, 16);
        main.add(countLabel, g);
        return main;
    }

    private void selectReport(int m) {
        mode = m;
        updateButtons();
        loadReport();
    }

    private void updateButtons() {
        for (int i = 0; i < modeBtns.length; i++) {
            if (i == mode) modeBtns[i].filled(); else modeBtns[i].outline();
        }
    }

    // ---------- database call (background thread) ----------
    private void loadReport() {
        final int m = mode;
        final int mySeq = ++loadSeq;
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() {
                switch (m) {
                    case 0:  return reportDAO.reportRows();
                    case 1:  return reportDAO.volunteerReportRows();
                    case 2:  return reportDAO.blockedTaskRows();
                    case 3:  return reportDAO.completedProjectRows();
                    default: return reportDAO.topCommenterRows();
                }
            }

            @Override
            protected void done() {
                if (mySeq != loadSeq) {
                    return;   // a newer request has been started
                }
                try {
                    List<Object[]> rows = get();
                    showRows(rows, m);
                    countLabel.setText(rows.size() + (rows.size() == 1 ? " row" : " rows"));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ee) {
                    showError(ee.getCause());
                }
            }
        }.execute();
    }

    private void showRows(List<Object[]> rows, int m) {
        final String[][] allHeaders = {PROJECT_HEADERS, VOLUNTEER_HEADERS, BLOCKED_HEADERS, COMPLETED_HEADERS, TOP_HEADERS};
        final int[][] allWidths = {PROJECT_WIDTHS, VOLUNTEER_WIDTHS, BLOCKED_WIDTHS, COMPLETED_WIDTHS, TOP_WIDTHS};
        final String[] headers = allHeaders[m];
        final int[] widths = allWidths[m];
        DefaultTableModel model = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }

            // tell the sorter the real type of each column, so numbers sort as numbers
            @Override
            public Class<?> getColumnClass(int col) {
                return getRowCount() == 0 || getValueAt(0, col) == null
                        ? Object.class : getValueAt(0, col).getClass();
            }
        };
        for (Object[] row : rows) {
            model.addRow(row);
        }
        table.setModel(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(60);
    }

    private void showError(Throwable cause) {
        String msg = (cause instanceof DataAccessException || cause instanceof IllegalArgumentException)
                ? cause.getMessage()
                : "Unexpected error. Please try again.";
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---------- rounded button ----------
    private static class RoundButton extends JButton {

        private final int radius;
        private Color fill = Color.WHITE;
        private Color line = LINE;

        RoundButton(String text, int radius) {
            super(text);
            this.radius = radius;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setForeground(TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        final void filled() {
            fill = new Color(0x111111);
            line = fill;
            setForeground(Color.WHITE);
            repaint();
        }

        final void outline() {
            fill = Color.WHITE;
            line = LINE;
            setForeground(TEXT);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(line);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            // keep the default look and feel
        }
        SwingUtilities.invokeLater(() -> new ReportForm().setVisible(true));
    }
}
