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

/**
 * Browse form: hiển thị danh sách Task lên lưới (JTable), lọc theo Project.
 * Dữ liệu lấy từ TaskDAO.browseRows / searchRows, tải trong luồng nền để giao diện không bị treo.
 */
public class BrowseForm extends JFrame {
    private static final ComboItem ALL = new ComboItem(-1, "Tất cả project");

    private final TaskDAO taskDAO = new TaskDAO();
    private final LookupDAO lookupDAO = new LookupDAO();

    private final JComboBox<ComboItem> cboProject = new JComboBox<>();
    private final JLabel lblCount = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(TaskDAO.BROWSE_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int col) { return false; }   // lưới chỉ để xem
    };
    private final JTable table = new JTable(model);
    private boolean adjusting = false;   // true khi đang nạp combobox, tránh tải lưới nhiều lần

    public BrowseForm() {
        super("Browse Tasks");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 520);
        setLocationRelativeTo(null);

        JButton btnRefresh = new JButton("Làm mới");
        btnRefresh.addActionListener(e -> loadTasks());
        cboProject.addActionListener(e -> {
            if (!adjusting) loadTasks();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Project:"));
        top.add(cboProject);
        top.add(btnRefresh);

        table.setAutoCreateRowSorter(true);          // bấm tiêu đề cột để sắp xếp
        table.setFillsViewportHeight(true);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblCount, BorderLayout.SOUTH);

        loadProjects();
    }

    private void loadProjects() {
        runAsync(lookupDAO::projects, (List<ComboItem> projects) -> {
            adjusting = true;
            cboProject.removeAllItems();
            cboProject.addItem(ALL);
            for (ComboItem p : projects) cboProject.addItem(p);
            cboProject.setSelectedIndex(0);
            adjusting = false;
            loadTasks();
        });
    }

    private void loadTasks() {
        ComboItem sel = (ComboItem) cboProject.getSelectedItem();
        if (sel == null) return;
        runAsync(() -> sel.getId() == ALL.getId()
                        ? taskDAO.searchRows(null, null, null, null)   // mọi project
                        : taskDAO.browseRows(sel.getId()),
                (List<Object[]> rows) -> {
                    model.setRowCount(0);
                    for (Object[] r : rows) model.addRow(r);
                    lblCount.setText("  " + rows.size() + " task");
                });
    }

    /** Chạy việc nặng (gọi DB) ở luồng nền, rồi cập nhật giao diện ở luồng giao diện. */
    private <T> void runAsync(Callable<T> work, Consumer<T> onDone) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception { return work.call(); }

            @Override
            protected void done() {
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
                : "Có lỗi không mong muốn. Vui lòng thử lại.";
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BrowseForm().setVisible(true));
    }
}
