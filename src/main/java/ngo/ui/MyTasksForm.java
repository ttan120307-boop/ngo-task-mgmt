package ngo.ui;

import ngo.auth.Session;
import ngo.service.MyTaskService;
import ngo.ui.browse.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.List;

/** "My tasks": the tasks assigned to the logged-in volunteer; he/she can change the status and comment. */
public class MyTasksForm extends JFrame {
    private final MyTaskService service = new MyTaskService();
    private final DefaultTableModel model = new DefaultTableModel(MyTaskService.COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<String> cboStatus = new JComboBox<>(MyTaskService.USER_STATUSES);
    private final JTextField txtComment = new JTextField(30);
    private final JLabel lblCount = new JLabel(" ");

    public MyTasksForm() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("My tasks");
        title.setFont(UiStyle.FONT_TITLE);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.add(title, BorderLayout.WEST);
        top.add(lblCount, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setBackground(Color.WHITE);
        actions.add(new JLabel("Set status:"));
        actions.add(cboStatus);
        JButton btnStatus = new JButton("Update status");
        btnStatus.addActionListener(e -> updateStatus());
        actions.add(btnStatus);
        actions.add(Box.createHorizontalStrut(24));
        actions.add(new JLabel("Comment:"));
        actions.add(txtComment);
        JButton btnComment = new JButton("Add comment");
        btnComment.addActionListener(e -> addComment());
        actions.add(btnComment);
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> reload());
        UiStyle.styleButton(btnRefresh, true);
        actions.add(btnRefresh);
        root.add(actions, BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);
        UiStyle.apply(this, "My tasks");
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        reload();
    }

    private void reload() {
        UiStyle.async(this, service::myTasks, this::fill);
    }

    private void fill(List<Object[]> rows) {
        model.setRowCount(0);
        for (Object[] r : rows) model.addRow(r);
        lblCount.setText(rows.size() + " task(s) assigned to " + Session.user().getFullName());
        lblCount.setForeground(UiStyle.MUTED);
    }

    private Integer selectedTaskId() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a task first.", "NGO Tasks", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return (Integer) model.getValueAt(table.convertRowIndexToModel(row), 0);
    }

    private void updateStatus() {
        Integer id = selectedTaskId();
        if (id == null) return;
        String status = (String) cboStatus.getSelectedItem();
        UiStyle.async(this, () -> { service.updateStatus(id, status); return Boolean.TRUE; }, ok -> reload());
    }

    private void addComment() {
        Integer id = selectedTaskId();
        if (id == null) return;
        String text = txtComment.getText();
        UiStyle.async(this, () -> service.addComment(id, text), n -> {
            txtComment.setText("");
            JOptionPane.showMessageDialog(this, "Comment added.", "NGO Tasks", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
