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

/**
 * Cửa sổ chính của ứng dụng: nơi mở tất cả các form.
 * Chạy lớp này (ngo.ui.MainFrame) để dùng toàn bộ chương trình.
 *
 * Thêm form mới: thêm một dòng addButton(...) trong hàm group tương ứng ở constructor.
 */
public class MainFrame extends JFrame {
    private final JLabel lblStatus = new JLabel(" Đang kiểm tra kết nối MySQL...");
    private final Map<String, JFrame> openWindows = new HashMap<>();

    public MainFrame() {
        super("NGO Task Management");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel title = new JLabel("Quản lý công việc tình nguyện (NGO)", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setBorder(BorderFactory.createEmptyBorder(14, 10, 6, 10));

        JPanel view = group("Xem dữ liệu");
        addButton(view, "Danh sách Task (Browse)", "browse", () -> new BrowseForm());
        addButton(view, "Project và Task (Master-Detail)", "masterdetail", () -> new MasterDetailForm());
        addButton(view, "Tìm kiếm Task (Search)", "search", () -> new SearchForm());

        JPanel edit = group("Chỉnh sửa");
        addButton(edit, "Editing: Task, Project, Membership, Label, Dependency, Comment, Assignment", "editing", () -> {
            JFrame f = new JFrame("Editing & Validation");
            f.setContentPane(new EditingValidationPanel());
            f.setSize(1180, 820);
            return f;
        });

        JPanel center = new JPanel(new GridLayout(0, 1, 10, 10));
        center.setBorder(BorderFactory.createEmptyBorder(6, 16, 10, 16));
        center.add(view);
        center.add(edit);

        lblStatus.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        add(title, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(lblStatus, BorderLayout.SOUTH);

        pack();
        setSize(Math.max(getWidth(), 520), getHeight());
        setLocationRelativeTo(null);
        checkConnection();
    }

    private JPanel group(String name) {
        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.setBorder(BorderFactory.createTitledBorder(name));
        return p;
    }

    private void addButton(JPanel panel, String text, String key, Supplier<JFrame> factory) {
        JButton b = new JButton(text);
        b.addActionListener(e -> open(key, factory));
        panel.add(b);
    }

    /** Mở form. Bấm lại khi form đang mở thì đưa nó lên trước, không mở trùng. */
    private void open(String key, Supplier<JFrame> factory) {
        JFrame existing = openWindows.get(key);
        if (existing != null && existing.isDisplayable()) {
            existing.toFront();
            return;
        }
        try {
            JFrame f = factory.get();
            // Một số form đặt EXIT_ON_CLOSE: đổi để đóng form con không tắt cả chương trình.
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setLocationRelativeTo(this);
            f.setVisible(true);
            openWindows.put(key, f);
        } catch (RuntimeException ex) {
            String msg = (ex instanceof DataAccessException || ex instanceof IllegalArgumentException)
                    ? ex.getMessage() : "Không mở được form. Vui lòng thử lại.";
            JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Thử kết nối MySQL ở luồng nền và hiện kết quả ở thanh trạng thái. */
    private void checkConnection() {
        new SwingWorker<String, Void>() {
            private boolean ok;

            @Override
            protected String doInBackground() {
                try (Connection c = DBConnection.getConnection()) {
                    ok = true;
                    return " Đã kết nối MySQL";
                } catch (SQLException e) {
                    return " " + DataAccessException.from(e).getMessage();
                } catch (Throwable t) {   // ví dụ thiếu file config/db.properties
                    Throwable root = (t instanceof ExceptionInInitializerError && t.getCause() != null)
                            ? t.getCause() : t;
                    return " " + (root.getMessage() != null ? root.getMessage()
                            : "Không kết nối được cơ sở dữ liệu.");
                }
            }

            @Override
            protected void done() {
                try {
                    lblStatus.setText(get());
                    lblStatus.setForeground(ok ? new Color(0, 120, 0) : new Color(180, 0, 0));
                } catch (Exception ignored) {
                    // giữ nguyên thông báo cũ
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
            // dùng giao diện mặc định
        }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
