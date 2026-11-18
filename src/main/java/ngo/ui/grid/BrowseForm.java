package ngo.ui.grid;

import ngo.dao.LookupDAO;
import ngo.dao.LookupDAO.ComboItem;
import ngo.dao.TaskDAO;
import ngo.db.DataAccessException;

import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import ngo.ui.browse.UiStyle;
import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
/**
 * Browse form: displays the list of Tasks in a grid (JTable), filtered by Project.
 * Data is fetched from TaskDAO.browseRows / searchRows and loaded on a background thread to keep the UI responsive.
 */
public class BrowseForm extends JFrame {
   private static final ComboItem ALL = new ComboItem(-1, "All projects");
    private static final String[] HEADERS = {"ID", "Title", "Team", "Priority", "Status", "Due date", "Assignee"};
    private static final int[] WIDTHS = {40, 200, 130, 80, 110, 100, 260};
    private static final Color LINE = new Color(0xD3D1C7);
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color MUTED = new Color(0x5F5E5A);

    private final TaskDAO taskDAO = new TaskDAO();
    private final LookupDAO lookupDAO = new LookupDAO();

    private ComboItem selectedProject = ALL;
    private final JPopupMenu projectMenu = new JPopupMenu();
    private RoundButton projectBtn;
    private RoundButton refreshBtn;
    private final JLabel lblCount = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(TaskDAO.BROWSE_COLUMNS, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }   // read-only grid
    };
    private final JTable table = new JTable(model);
    private int totalTasks = -1;   // tổng số task (biết sau lần tải "All projects")   // true khi đang nạp combobox, tránh tải lưới nhiều lần

   public BrowseForm() {
        super("Browse tasks");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UiStyle.apply(this, "Browse");
        add(buildMainPanel(), BorderLayout.CENTER);

        table.setAutoCreateRowSorter(true);          // bấm tiêu đề cột để sắp xếp
        UiStyle.styleTable(table);
        if (table.getColumnCount() == HEADERS.length) {
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            for (int i = 0; i < HEADERS.length; i++) {
                table.getColumnModel().getColumn(i).setHeaderValue(HEADERS[i]);
                table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
            }
            table.getColumnModel().getColumn(0).setMaxWidth(60);
        }

        loadProjects();
    }

    private JPanel buildMainPanel() {
        JLabel title = new JLabel("Browse tasks");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT);

        refreshBtn = new RoundButton("Refresh", 10);
        refreshBtn.filled();
        refreshBtn.addActionListener(e -> loadTasks());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(Color.WHITE);
        buttons.add(refreshBtn);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(Color.WHITE);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(buttons, BorderLayout.EAST);

        // bộ lọc Project: nút + menu thả xuống
        projectBtn = new RoundButton("Project: All projects", 10);
        projectBtn.setIcon(new ArrowIcon());
        projectBtn.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        projectBtn.setIconTextGap(8);
        projectBtn.setPreferredSize(new Dimension(300, 38));
        projectBtn.addActionListener(e -> projectMenu.show(projectBtn, 0, projectBtn.getHeight()));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filterRow.setBackground(Color.WHITE);
        filterRow.add(projectBtn);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.getViewport().setBackground(Color.WHITE);

        lblCount.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblCount.setForeground(MUTED);

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
        g.insets = new Insets(4, 16, 12, 16);
        main.add(filterRow, g);

        g.gridy = 2;
        g.weighty = 1;
        g.insets = new Insets(4, 16, 4, 16);
        main.add(scroll, g);

        g.gridy = 3;
        g.weighty = 0;
        g.insets = new Insets(4, 16, 12, 16);
        main.add(lblCount, g);
        return main;
    }

    private void loadProjects() {
        runAsync(lookupDAO::projects, (List<ComboItem> projects) -> {
            projectMenu.removeAll();
            addProjectItem(ALL);
            for (ComboItem p : projects) addProjectItem(p);
            selectProject(ALL);
        });
    }

    private void addProjectItem(ComboItem item) {
        JMenuItem mi = new JMenuItem(item.toString());
        mi.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        mi.addActionListener(e -> selectProject(item));
        projectMenu.add(mi);
    }

    private void selectProject(ComboItem item) {
        selectedProject = item;
        projectBtn.setText("Project: " + item);
        loadTasks();
    }

    private void loadTasks() {
        final ComboItem sel = selectedProject;
        runAsync(() -> sel == ALL
                        ? taskDAO.searchRows(null, null, null, null)   // mọi project
                        : taskDAO.browseRows(sel.getId()),
                (List<Object[]> rows) -> {
                    model.setRowCount(0);
                    for (Object[] r : rows) model.addRow(r);
                    if (sel == ALL) totalTasks = rows.size();
                    int total = (totalTasks < 0) ? rows.size() : totalTasks;
                    lblCount.setText("Showing " + rows.size() + " of " + total + " tasks");
                });
    }

    /** Runs heavy work (DB calls) on a background thread, then updates the UI on the EDT. */
    private <T> void runAsync(Callable<T> work, Consumer<T> onDone) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }

            @Override protected void done() {
                try {
                    onDone.accept(get());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ee) {
                    showError(ee.getCause());
                }
            }
        }.execute();
    }

    private void showError(Throwable cause) {
        String msg = (cause instanceof DataAccessException || cause instanceof IllegalArgumentException)
                ? cause.getMessage()
                : "Unexpected error. Please try again.";
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---------- mũi tên nhỏ vẽ bằng code ----------
    private static class ArrowIcon implements javax.swing.Icon {
        @Override
        public int getIconWidth() { return 10; }

        @Override
        public int getIconHeight() { return 6; }

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TEXT);
            g2.fillPolygon(new int[]{x, x + 10, x + 5}, new int[]{y, y, y + 6}, 3);
            g2.dispose();
        }
    }

    // ---------- nút bo tròn ----------
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
        SwingUtilities.invokeLater(() -> new BrowseForm().setVisible(true));
    }
}
