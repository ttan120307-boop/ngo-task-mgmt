package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.TaskDAO;
import ngo.db.DataAccessException;
import ngo.model.Task;
import ngo.service.TaskService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Editing & Validation screen for TASK.
 * Uses the exact TaskDAO, TaskService, Task model and LookupDAO APIs in the supplied backend.
 */
public class TaskEditingPanel extends JPanel {
    private final TaskDAO taskDAO = new TaskDAO();
    private final TaskService taskService = new TaskService();
    private final LookupDAO lookupDAO = new LookupDAO();

    private final JComboBox<LookupDAO.ComboItem> projectCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> teamCombo = new JComboBox<>();
    private final JComboBox<String> priorityCombo = new JComboBox<>(LookupDAO.PRIORITIES);
    private final JComboBox<String> statusCombo = new JComboBox<>(LookupDAO.STATUSES);
    private final JTextField titleField = new JTextField(26);
    private final JTextArea descriptionArea = new JTextArea(3, 26);
    private final JTextField hoursField = new JTextField(12);
    private final JTextField dueDateField = new JTextField(12);
    private final JList<LookupDAO.ComboItem> assigneeList = new JList<>();
    private final DefaultListModel<LookupDAO.ComboItem> assigneeModel = new DefaultListModel<>();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Task ID", "Project ID", "Team ID", "Title", "Priority", "Status", "Hours", "Due date"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable taskTable = new JTable(tableModel);
    private final JLabel modeLabel = new JLabel("Chế độ: Thêm Task mới");
    private final JLabel assigneeHint = new JLabel("Chọn Team để tải danh sách thành viên hiện tại.");
    private final JButton addButton = new JButton("Add Task");
    private final JButton updateButton = new JButton("Save Changes");
    private final JButton deleteButton = new JButton("Delete Selected");
    private final JButton clearButton = new JButton("New / Clear");
    private final JButton refreshButton = new JButton("Refresh");

    private List<Task> tasks = new ArrayList<>();
    private boolean editMode;
    private boolean loadingCombos;

    public TaskEditingPanel() {
        buildUi();
        bindEvents();
        loadLookups();
        loadTasks();
        setEditMode(false);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Task information"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        addField(form, c, r++, "Project *", projectCombo);
        addField(form, c, r++, "Responsible Team *", teamCombo);
        addField(form, c, r++, "Title *", titleField);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        addField(form, c, r++, "Description", new JScrollPane(descriptionArea));
        addField(form, c, r++, "Priority *", priorityCombo);
        addField(form, c, r++, "Status *", statusCombo);
        addField(form, c, r++, "Estimated hours", hoursField);
        addField(form, c, r++, "Due date (yyyy-MM-dd)", dueDateField);

        assigneeList.setModel(assigneeModel);
        assigneeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane peopleScroll = new JScrollPane(assigneeList);
        peopleScroll.setPreferredSize(new Dimension(280, 86));
        addField(form, c, r++, "Assignees (khi tạo mới)", peopleScroll);
        addField(form, c, r++, "", assigneeHint);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(addButton);
        actions.add(updateButton);
        actions.add(deleteButton);
        actions.add(clearButton);
        actions.add(refreshButton);
        actions.add(modeLabel);

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(form, BorderLayout.CENTER);
        top.add(actions, BorderLayout.SOUTH);

        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setAutoCreateRowSorter(true);
        taskTable.setFillsViewportHeight(true);
        JScrollPane grid = new JScrollPane(taskTable);
        grid.setBorder(BorderFactory.createTitledBorder("Tasks – chọn một dòng để sửa hoặc xóa"));
        grid.setPreferredSize(new Dimension(950, 240));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, grid);
        split.setResizeWeight(0.63);
        add(split, BorderLayout.CENTER);
    }

    private void addField(JPanel panel, GridBagConstraints c, int row,
                          String label, Component field) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private void bindEvents() {
        addButton.addActionListener(e -> addTask());
        updateButton.addActionListener(e -> updateTask());
        deleteButton.addActionListener(e -> deleteTask());
        clearButton.addActionListener(e -> clearForm());
        refreshButton.addActionListener(e -> loadTasks());
        teamCombo.addActionListener(e -> {
            if (!loadingCombos) loadCurrentTeamMembers();
        });
        taskTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedTask();
        });
    }

    private void loadLookups() {
        setBusy(true);
        UiSupport.runAsync(this, "tải danh sách Project và Team", () -> {
            return new LookupData(lookupDAO.projects(), lookupDAO.teams());
        }, data -> {
            loadingCombos = true;
            projectCombo.removeAllItems();
            for (LookupDAO.ComboItem item : data.projects) projectCombo.addItem(item);
            teamCombo.removeAllItems();
            for (LookupDAO.ComboItem item : data.teams) teamCombo.addItem(item);
            loadingCombos = false;
            loadCurrentTeamMembers();
        }, () -> setBusy(false));
    }

    private void loadCurrentTeamMembers() {
        LookupDAO.ComboItem team = (LookupDAO.ComboItem) teamCombo.getSelectedItem();
        assigneeModel.clear();
        if (team == null) {
            assigneeHint.setText("Không có Team để tải thành viên.");
            return;
        }
        assigneeHint.setText("Đang tải thành viên hiện tại...");
        UiSupport.runAsync(this, "tải thành viên của Team", () -> lookupDAO.currentMembersOfTeam(team.getId()), members -> {
            for (LookupDAO.ComboItem member : members) assigneeModel.addElement(member);
            assigneeHint.setText(members.size() + " thành viên hiện tại. Chỉ áp dụng danh sách này khi tạo Task mới.");
        }, null);
    }

    private void loadTasks() {
        setBusy(true);
        UiSupport.runAsync(this, "tải danh sách Task",
                () -> taskDAO.search(null, null, null, null), result -> {
                    tasks = result;
                    tableModel.setRowCount(0);
                    for (Task task : tasks) {
                        tableModel.addRow(new Object[]{task.getTaskId(), task.getProjectId(), task.getTeamId(),
                                task.getTitle(), task.getPriority(), task.getStatus(), task.getEstimatedHours(), task.getDueDate()});
                    }
                }, () -> setBusy(false));
    }

    private void loadSelectedTask() {
        int viewRow = taskTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = taskTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= tasks.size()) return;
        int taskId = tasks.get(modelRow).getTaskId();
        setBusy(true);
        UiSupport.runAsync(this, "lấy thông tin Task", () -> taskDAO.findById(taskId), task -> {
            if (task == null) {
                UiSupport.showError(this, "Task không còn tồn tại. Hãy làm mới danh sách.");
                loadTasks();
                return;
            }
            fillForm(task);
            setEditMode(true);
        }, () -> setBusy(false));
    }

    private void fillForm(Task task) {
        selectLookup(projectCombo, task.getProjectId());
        selectLookup(teamCombo, task.getTeamId());
        titleField.setText(value(task.getTitle()));
        descriptionArea.setText(value(task.getDescription()));
        priorityCombo.setSelectedItem(task.getPriority());
        statusCombo.setSelectedItem(task.getStatus());
        hoursField.setText(task.getEstimatedHours() == null ? "" : task.getEstimatedHours().stripTrailingZeros().toPlainString());
        dueDateField.setText(task.getDueDate() == null ? "" : task.getDueDate().toString());
    }

    private void addTask() {
        final Task task;
        try {
            task = readAndValidateForm();
        } catch (IllegalArgumentException e) {
            UiSupport.showError(this, e.getMessage());
            return;
        }
        List<Integer> volunteerIds = new ArrayList<>();
        for (LookupDAO.ComboItem member : assigneeList.getSelectedValuesList()) volunteerIds.add(member.getId());

        setBusy(true);
        UiSupport.runAsync(this, "thêm Task", () -> {
            if (volunteerIds.isEmpty()) return taskDAO.insert(task);
            return taskService.createTaskWithAssignees(task, volunteerIds);
        }, taskId -> {
            UiSupport.showInfo(this, "Đã thêm Task thành công. Task ID: " + taskId);
            clearForm();
            loadTasks();
        }, () -> setBusy(false));
    }

    private void updateTask() {
        int taskId = selectedTaskId();
        if (taskId <= 0) {
            UiSupport.showError(this, "Hãy chọn một Task trong bảng trước khi cập nhật.");
            return;
        }
        final Task task;
        try {
            task = readAndValidateForm();
            task.setTaskId(taskId);
        } catch (IllegalArgumentException e) {
            UiSupport.showError(this, e.getMessage());
            return;
        }
        setBusy(true);
        UiSupport.runAsync(this, "cập nhật Task", () -> taskDAO.update(task), rows -> {
            if (rows == 0) UiSupport.showError(this, "Task không tồn tại hoặc không có thay đổi.");
            else UiSupport.showInfo(this, "Đã cập nhật Task thành công.");
            loadTasks();
            setEditMode(false);
        }, () -> setBusy(false));
    }

    private void deleteTask() {
        int taskId = selectedTaskId();
        if (taskId <= 0) {
            UiSupport.showError(this, "Hãy chọn một Task trong bảng trước khi xóa.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa Task #" + taskId + "?\n"
                        + "Các Assignment, Comment, Attachment và Dependency liên quan có thể bị xóa CASCADE.",
                "Xác nhận xóa Task", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;

        setBusy(true);
        UiSupport.runAsync(this, "xóa Task", () -> taskDAO.delete(taskId), rows -> {
            if (rows == 0) UiSupport.showError(this, "Task không còn tồn tại.");
            else UiSupport.showInfo(this, "Đã xóa Task thành công.");
            clearForm();
            loadTasks();
        }, () -> setBusy(false));
    }

    private Task readAndValidateForm() {
        LookupDAO.ComboItem project = (LookupDAO.ComboItem) projectCombo.getSelectedItem();
        LookupDAO.ComboItem team = (LookupDAO.ComboItem) teamCombo.getSelectedItem();
        if (project == null) throw new IllegalArgumentException("Vui lòng chọn Project.");
        if (team == null) throw new IllegalArgumentException("Vui lòng chọn Team phụ trách.");

        BigDecimal hours = null;
        if (!hoursField.getText().trim().isEmpty()) {
            try {
                hours = new BigDecimal(hoursField.getText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Estimated hours phải là số hợp lệ, ví dụ 2.5.");
            }
        }

        LocalDate dueDate = null;
        if (!dueDateField.getText().trim().isEmpty()) {
            try {
                dueDate = LocalDate.parse(dueDateField.getText().trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Due date không hợp lệ. Dùng định dạng yyyy-MM-dd, ví dụ 2026-11-22.");
            }
        }

        Task task = new Task();
        task.setProjectId(project.getId());
        task.setTeamId(team.getId());
        task.setTitle(titleField.getText().trim());
        task.setDescription(descriptionArea.getText().trim().isEmpty() ? null : descriptionArea.getText().trim());
        task.setPriority((String) priorityCombo.getSelectedItem());
        task.setStatus((String) statusCombo.getSelectedItem());
        task.setEstimatedHours(hours);
        task.setDueDate(dueDate);

        String error = task.validate();
        if (error != null) throw new IllegalArgumentException(error);
        return task;
    }

    private int selectedTaskId() {
        int viewRow = taskTable.getSelectedRow();
        if (viewRow < 0) return -1;
        int modelRow = taskTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= tasks.size()) return -1;
        return tasks.get(modelRow).getTaskId();
    }

    private void selectLookup(JComboBox<LookupDAO.ComboItem> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId() == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void clearForm() {
        taskTable.clearSelection();
        projectCombo.setSelectedIndex(projectCombo.getItemCount() > 0 ? 0 : -1);
        teamCombo.setSelectedIndex(teamCombo.getItemCount() > 0 ? 0 : -1);
        titleField.setText("");
        descriptionArea.setText("");
        priorityCombo.setSelectedItem("MEDIUM");
        statusCombo.setSelectedItem("TODO");
        hoursField.setText("");
        dueDateField.setText("");
        assigneeList.clearSelection();
        setEditMode(false);
    }

    private void setEditMode(boolean editing) {
        editMode = editing;
        addButton.setEnabled(!editing);
        updateButton.setEnabled(editing);
        deleteButton.setEnabled(editing);
        assigneeList.setEnabled(!editing);
        modeLabel.setText(editing ? "Chế độ: Sửa Task đang chọn" : "Chế độ: Thêm Task mới");
    }

    private void setBusy(boolean busy) {
        addButton.setEnabled(!busy && !editMode);
        updateButton.setEnabled(!busy && editMode);
        deleteButton.setEnabled(!busy && editMode);
        clearButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
    }

    private String value(String value) { return value == null ? "" : value; }

    private static final class LookupData {
        final List<LookupDAO.ComboItem> projects;
        final List<LookupDAO.ComboItem> teams;
        LookupData(List<LookupDAO.ComboItem> projects, List<LookupDAO.ComboItem> teams) {
            this.projects = projects;
            this.teams = teams;
        }
    }
}
