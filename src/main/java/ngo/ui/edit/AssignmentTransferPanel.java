package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.TaskDAO;
import ngo.db.DataAccessException;
import ngo.model.Task;
import ngo.service.TaskService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** UI for the existing TaskService.transferAssignment(...) backend operation. */
public class AssignmentTransferPanel extends JPanel {
    private final TaskDAO taskDAO = new TaskDAO();
    private final LookupDAO lookupDAO = new LookupDAO();
    private final TaskService taskService = new TaskService();

    private final JComboBox<TaskChoice> taskCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> fromCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> toCombo = new JComboBox<>();
    private final JButton transferButton = new JButton("Transfer assignment");
    private final JButton refreshButton = new JButton("Refresh lists");

    public AssignmentTransferPanel() {
        buildUi();
        transferButton.addActionListener(e -> transfer());
        refreshButton.addActionListener(e -> loadLists());
        loadLists();
    }

    private void buildUi() {
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 7, 7, 7);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; add(new JLabel("Task *"), c);
        c.gridx = 1; c.weightx = 1; add(taskCombo, c);
        c.gridx = 0; c.gridy++; c.weightx = 0; add(new JLabel("Current volunteer *"), c);
        c.gridx = 1; c.weightx = 1; add(fromCombo, c);
        c.gridx = 0; c.gridy++; c.weightx = 0; add(new JLabel("New volunteer *"), c);
        c.gridx = 1; c.weightx = 1; add(toCombo, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(transferButton);
        buttons.add(refreshButton);
        c.gridx = 1; c.gridy++; add(buttons, c);

        JLabel note = new JLabel("Backend kiểm tra người cũ có đang được giao Task và người mới có thuộc Team hiện tại.");
        note.setForeground(new Color(90, 90, 90));
        c.gridx = 1; c.gridy++; add(note, c);
    }

    private void loadLists() {
        setBusy(true);
        UiSupport.runAsync(this, "tải danh sách Task và Volunteer", () -> {
            return new Lists(taskDAO.search(null, null, null, null), lookupDAO.volunteers());
        }, lists -> {
            taskCombo.removeAllItems();
            for (Task task : lists.tasks) taskCombo.addItem(new TaskChoice(task));
            fromCombo.removeAllItems();
            toCombo.removeAllItems();
            for (LookupDAO.ComboItem volunteer : lists.volunteers) {
                fromCombo.addItem(volunteer);
                toCombo.addItem(volunteer);
            }
        }, () -> setBusy(false));
    }

    private void transfer() {
        TaskChoice task = (TaskChoice) taskCombo.getSelectedItem();
        LookupDAO.ComboItem from = (LookupDAO.ComboItem) fromCombo.getSelectedItem();
        LookupDAO.ComboItem to = (LookupDAO.ComboItem) toCombo.getSelectedItem();
        if (task == null || from == null || to == null) {
            UiSupport.showError(this, "Vui lòng chọn Task, người đang được giao và người nhận.");
            return;
        }
        if (from.getId() == to.getId()) {
            UiSupport.showError(this, "Người giao và người nhận không được trùng nhau.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Chuyển Task " + task + " từ " + from + " sang " + to + "?",
                "Xác nhận chuyển giao", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;

        setBusy(true);
        UiSupport.runAsync(this, "chuyển giao Task", () -> {
            taskService.transferAssignment(task.getTaskId(), from.getId(), to.getId());
            return null;
        }, ignored -> UiSupport.showInfo(this, "Đã chuyển giao Task thành công."), () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        transferButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
    }

    private static final class Lists {
        final List<Task> tasks;
        final List<LookupDAO.ComboItem> volunteers;
        Lists(List<Task> tasks, List<LookupDAO.ComboItem> volunteers) {
            this.tasks = tasks;
            this.volunteers = volunteers;
        }
    }
}
