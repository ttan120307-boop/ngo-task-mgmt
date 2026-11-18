package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.TaskAssignmentReadDAO;
import ngo.dao.TaskDAO;
import ngo.db.DataAccessException;
import ngo.model.Task;
import ngo.service.TaskEditingService;
import ngo.ui.browse.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * New Edit Task screen. It deliberately does not reuse the rejected old TaskEditingPanel UI.
 * Data access is delegated to the group's existing DAO/service/model classes.
 */
public class EditTaskForm extends JFrame {
    private static final Color TEXT = new Color(0x1A1A1A);
    private static final Color MUTED = new Color(0x5F5E5A);
    private static final Color LINE = new Color(0xE5E5E5);
    private static final Color SOFT_BG = new Color(0xF7F7F5);
    private static final Color PRIMARY = new Color(0x1A1A1A);
    private static final Color DANGER = new Color(0xA32D2D);

    private final TaskDAO taskDAO = new TaskDAO();
    private final LookupDAO lookupDAO = new LookupDAO();
    private final TaskAssignmentReadDAO assignmentReadDAO = new TaskAssignmentReadDAO();
    private final TaskEditingService editingService = new TaskEditingService();

    private final JLabel taskIdValue = new JLabel("New task");
    private final JComboBox<LookupDAO.ComboItem> projectCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> teamCombo = new JComboBox<>();
    private final JTextField titleField = new JTextField();
    private final JTextArea descriptionArea = new JTextArea(3, 30);
    private final JComboBox<String> priorityCombo = new JComboBox<>(LookupDAO.PRIORITIES);
    private final JComboBox<String> statusCombo = new JComboBox<>(LookupDAO.STATUSES);
    private final JTextField hoursField = new JTextField();
    private final JTextField dueDateField = new JTextField();

    private final DefaultTableModel taskTableModel = new DefaultTableModel(
            new Object[]{"ID", "Project", "Team", "Task title", "Priority", "Status", "Due date"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable taskTable = new JTable(taskTableModel);
    private final JLabel taskCountLabel = new JLabel("0 tasks");
    private final JLabel modeLabel = new JLabel("Create mode");
    private final JLabel messageLabel = new JLabel("Select a task below or create a new task.");

    private final javax.swing.DefaultListModel<LookupDAO.ComboItem> memberListModel =
            new javax.swing.DefaultListModel<>();
    private final JList<LookupDAO.ComboItem> memberList = new JList<>(memberListModel);
    private final JTextArea currentAssigneesArea = new JTextArea(3, 24);
    private final JPanel assignmentCards = new JPanel(new CardLayout());

    private final JButton newButton = new JButton("New task");
    private final JButton saveButton = new JButton("Add task");
    private final JButton deleteButton = new JButton("Delete task");
    private final JButton refreshButton = new JButton("Refresh list");

    private final Map<Integer, String> projectNames = new HashMap<>();
    private final Map<Integer, String> teamNames = new HashMap<>();
    private List<Task> tasks = new ArrayList<>();
    private boolean loadingLookups;
    private Integer editingTaskId;

    public EditTaskForm() {
        super("NGO Tasks – Edit task");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        buildInterface();
        bindEvents();
        UiStyle.apply(this, "Edit task");
        UiStyle.styleTable(taskTable);
        loadLookups();
    }

    private void buildInterface() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        JPanel page = new JPanel(new BorderLayout(12, 14));
        page.setBackground(Color.WHITE);
        page.setBorder(new EmptyBorder(22, 20, 14, 20));
        page.add(buildPageHeader(), BorderLayout.NORTH);

        JPanel formCard = buildFormCard();
        JPanel tableCard = buildTaskTableCard();
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formCard, tableCard);
        split.setBorder(null);
        split.setDividerSize(7);
        split.setResizeWeight(0.66);
        split.setContinuousLayout(true);
        split.setBackground(Color.WHITE);
        page.add(split, BorderLayout.CENTER);

        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageLabel.setForeground(MUTED);
        messageLabel.setBorder(new EmptyBorder(3, 2, 0, 2));
        page.add(messageLabel, BorderLayout.SOUTH);
        getContentPane().add(page, BorderLayout.CENTER);
    }

    private JPanel buildPageHeader() {
        JPanel header = new JPanel();
        header.setBackground(Color.WHITE);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Edit task");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel("Create a new task or update the information of an existing task.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        subtitle.setBorder(new EmptyBorder(5, 1, 0, 0));

        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JPanel buildFormCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(10, 10));

        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(Color.WHITE);
        JPanel heading = new JPanel();
        heading.setBackground(Color.WHITE);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel sectionTitle = new JLabel("Task information");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sectionTitle.setForeground(TEXT);
        JLabel sectionHint = new JLabel("Fields marked * are required. Date format: yyyy-MM-dd.");
        sectionHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sectionHint.setForeground(MUTED);
        heading.add(sectionTitle);
        heading.add(Box.createVerticalStrut(3));
        heading.add(sectionHint);
        cardHeader.add(heading, BorderLayout.WEST);
        cardHeader.add(modeLabel, BorderLayout.EAST);
        modeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modeLabel.setForeground(MUTED);
        card.add(cardHeader, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(Color.WHITE);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 7, 5, 7);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        addField(fields, c, 0, 0, 1, "Task ID", taskIdValue);
        addField(fields, c, 0, 1, 1, "Project *", projectCombo);
        addField(fields, c, 0, 2, 1, "Responsible team *", teamCombo);
        addField(fields, c, 1, 0, 2, "Task title *", titleField);
        addField(fields, c, 1, 2, 1, "Priority *", priorityCombo);
        addField(fields, c, 2, 0, 1, "Status *", statusCombo);
        addField(fields, c, 2, 1, 1, "Estimated hours", hoursField);
        addField(fields, c, 2, 2, 1, "Due date", dueDateField);

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        descriptionArea.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(300, 58));
        addField(fields, c, 3, 0, 2, "Description", descriptionScroll);

        JPanel assignmentPanel = buildAssignmentPanel();
        c.gridx = 2;
        c.gridy = 3;
        c.gridwidth = 1;
        c.weightx = 1;
        c.weighty = 1;
        fields.add(assignmentPanel, c);

        card.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(Color.WHITE);
        stylePrimaryButton(saveButton);
        styleSecondaryButton(newButton);
        styleSecondaryButton(refreshButton);
        styleDangerButton(deleteButton);
        actions.add(saveButton);
        actions.add(newButton);
        actions.add(deleteButton);
        actions.add(refreshButton);
        card.add(actions, BorderLayout.SOUTH);

        styleInput(titleField);
        styleInput(hoursField);
        styleInput(dueDateField);
        styleInput(projectCombo);
        styleInput(teamCombo);
        styleInput(priorityCombo);
        styleInput(statusCombo);
        taskIdValue.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        taskIdValue.setForeground(MUTED);
        return card;
    }

    private JPanel buildAssignmentPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(Color.WHITE);
        JLabel title = new JLabel("Assignees");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(TEXT);
        panel.add(title, BorderLayout.NORTH);

        memberList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        memberList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        memberList.setVisibleRowCount(3);
        JScrollPane memberScroll = new JScrollPane(memberList);
        memberScroll.setPreferredSize(new Dimension(250, 62));

        currentAssigneesArea.setEditable(false);
        currentAssigneesArea.setLineWrap(true);
        currentAssigneesArea.setWrapStyleWord(true);
        currentAssigneesArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        currentAssigneesArea.setBackground(SOFT_BG);
        currentAssigneesArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        assignmentCards.add(memberScroll, "CREATE");
        assignmentCards.add(new JScrollPane(currentAssigneesArea), "EDIT");
        panel.add(assignmentCards, BorderLayout.CENTER);

        JLabel hint = new JLabel("For existing tasks, assignments are managed separately.");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        hint.setForeground(MUTED);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTaskTableCard() {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        JLabel title = new JLabel("Tasks");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(TEXT);
        taskCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        taskCountLabel.setForeground(MUTED);
        header.add(title, BorderLayout.WEST);
        header.add(taskCountLabel, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setAutoCreateRowSorter(true);
        taskTable.setFillsViewportHeight(true);
        JScrollPane tableScroll = new JScrollPane(taskTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(LINE));
        card.add(tableScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel createCard() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(12, 14, 12, 14)));
        return panel;
    }

    private void addField(JPanel panel, GridBagConstraints c, int row, int column,
                          int columnSpan, String label, Component field) {
        JPanel block = new JPanel(new BorderLayout(0, 5));
        block.setBackground(Color.WHITE);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(new Font("Segoe UI", Font.BOLD, 12));
        labelComponent.setForeground(TEXT);
        block.add(labelComponent, BorderLayout.NORTH);
        if (field instanceof JLabel) {
            block.add(field, BorderLayout.CENTER);
        } else {
            block.add(field, BorderLayout.CENTER);
        }
        c.gridx = column;
        c.gridy = row;
        c.gridwidth = columnSpan;
        c.weightx = columnSpan == 2 ? 2 : 1;
        c.weighty = 0;
        panel.add(block, c);
        c.gridwidth = 1;
    }

    private void bindEvents() {
        newButton.addActionListener(event -> clearForm());
        saveButton.addActionListener(event -> saveTask());
        deleteButton.addActionListener(event -> deleteTask());
        refreshButton.addActionListener(event -> loadTasks());

        teamCombo.addActionListener(event -> {
            if (!loadingLookups && editingTaskId == null) loadCurrentTeamMembers();
        });

        taskTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelectedTask();
        });
    }

    private void loadLookups() {
        setActionBusy(true);
        runAsync("load Project and Team lists", () -> new LookupData(lookupDAO.projects(), lookupDAO.teams()), data -> {
            loadingLookups = true;
            projectCombo.removeAllItems();
            projectNames.clear();
            for (LookupDAO.ComboItem item : data.projects) {
                projectCombo.addItem(item);
                projectNames.put(item.getId(), item.getLabel());
            }
            teamCombo.removeAllItems();
            teamNames.clear();
            for (LookupDAO.ComboItem item : data.teams) {
                teamCombo.addItem(item);
                teamNames.put(item.getId(), item.getLabel());
            }
            if (projectCombo.getItemCount() > 0) projectCombo.setSelectedIndex(0);
            if (teamCombo.getItemCount() > 0) teamCombo.setSelectedIndex(0);
            loadingLookups = false;
            loadTasks();
            if (editingTaskId == null) loadCurrentTeamMembers();
        }, this::setActionBusyFalse);
    }

    private void loadTasks() {
        setActionBusy(true);
        runAsync("load Tasks", () -> taskDAO.search(null, null, null, null), result -> {
            tasks = result;
            taskTableModel.setRowCount(0);
            for (Task task : tasks) {
                taskTableModel.addRow(new Object[]{
                        task.getTaskId(),
                        projectNames.getOrDefault(task.getProjectId(), "Project #" + task.getProjectId()),
                        teamNames.getOrDefault(task.getTeamId(), "Team #" + task.getTeamId()),
                        task.getTitle(), task.getPriority(), task.getStatus(),
                        task.getDueDate() == null ? "—" : task.getDueDate().toString()
                });
            }
            taskCountLabel.setText(tasks.size() + (tasks.size() == 1 ? " task" : " tasks"));
            UiStyle.styleTable(taskTable);
        }, this::setActionBusyFalse);
    }

    private void loadCurrentTeamMembers() {
        LookupDAO.ComboItem selectedTeam = (LookupDAO.ComboItem) teamCombo.getSelectedItem();
        memberListModel.clear();
        if (selectedTeam == null) return;
        runAsync("load current team members",
                () -> lookupDAO.currentMembersOfTeam(selectedTeam.getId()),
                members -> {
                    memberListModel.clear();
                    for (LookupDAO.ComboItem member : members) memberListModel.addElement(member);
                }, null);
    }

    private void loadSelectedTask() {
        int viewRow = taskTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = taskTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= tasks.size()) return;
        int taskId = tasks.get(modelRow).getTaskId();

        setActionBusy(true);
        runAsync("load selected Task", () -> {
            Task task = taskDAO.findById(taskId);
            if (task == null) throw new IllegalArgumentException("Task no longer exists. Refresh the list.");
            List<String> assigneeNames = assignmentReadDAO.findAssigneeNames(taskId);
            return new TaskDetails(task, assigneeNames);
        }, details -> {
            editingTaskId = details.task.getTaskId();
            taskIdValue.setText("#" + editingTaskId);
            selectLookup(projectCombo, details.task.getProjectId());
            selectLookup(teamCombo, details.task.getTeamId());
            titleField.setText(value(details.task.getTitle()));
            descriptionArea.setText(value(details.task.getDescription()));
            priorityCombo.setSelectedItem(details.task.getPriority());
            statusCombo.setSelectedItem(details.task.getStatus());
            hoursField.setText(details.task.getEstimatedHours() == null ? ""
                    : details.task.getEstimatedHours().stripTrailingZeros().toPlainString());
            dueDateField.setText(details.task.getDueDate() == null ? "" : details.task.getDueDate().toString());
            currentAssigneesArea.setText(details.assigneeNames.isEmpty()
                    ? "No assignees assigned."
                    : String.join(", ", details.assigneeNames));
            ((CardLayout) assignmentCards.getLayout()).show(assignmentCards, "EDIT");
            setEditMode(true);
            setMessage("Editing Task #" + editingTaskId + ". Existing assignments are not changed by this form.", false);
        }, this::setActionBusyFalse);
    }

    private void saveTask() {
        final Task task;
        try {
            task = readAndValidateForm();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        if (editingTaskId == null) {
            List<Integer> selectedVolunteerIds = new ArrayList<>();
            for (LookupDAO.ComboItem member : memberList.getSelectedValuesList()) {
                selectedVolunteerIds.add(member.getId());
            }
            setActionBusy(true);
            runAsync("create Task", () -> editingService.create(task, selectedVolunteerIds), newId -> {
                showInfo("Task created successfully. Task ID: " + newId);
                clearForm();
                loadTasks();
            }, this::setActionBusyFalse);
        } else {
            task.setTaskId(editingTaskId);
            setActionBusy(true);
            runAsync("update Task", () -> editingService.update(task), affectedRows -> {
                if (affectedRows > 0) showInfo("Task updated successfully.");
                else showInfo("No values changed. The Task is still up to date.");
                loadTasks();
                clearForm();
            }, this::setActionBusyFalse);
        }
    }

    private Task readAndValidateForm() {
        LookupDAO.ComboItem project = (LookupDAO.ComboItem) projectCombo.getSelectedItem();
        LookupDAO.ComboItem team = (LookupDAO.ComboItem) teamCombo.getSelectedItem();
        if (project == null) throw new IllegalArgumentException("Please select a Project.");
        if (team == null) throw new IllegalArgumentException("Please select a responsible Team.");

        BigDecimal estimatedHours = null;
        String hoursText = hoursField.getText().trim();
        if (!hoursText.isEmpty()) {
            try {
                estimatedHours = new BigDecimal(hoursText);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Estimated hours must be a valid number, for example 2.5.");
            }
        }

        LocalDate dueDate = null;
        String dateText = dueDateField.getText().trim();
        if (!dateText.isEmpty()) {
            try {
                dueDate = LocalDate.parse(dateText);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Due date must use yyyy-MM-dd, for example 2026-11-20.");
            }
        }

        Task task = new Task();
        task.setProjectId(project.getId());
        task.setTeamId(team.getId());
        task.setTitle(titleField.getText().trim());
        String description = descriptionArea.getText().trim();
        task.setDescription(description.isEmpty() ? null : description);
        task.setPriority((String) priorityCombo.getSelectedItem());
        task.setStatus((String) statusCombo.getSelectedItem());
        task.setEstimatedHours(estimatedHours);
        task.setDueDate(dueDate);

        String error = task.validate();
        if (error != null) throw new IllegalArgumentException(error);
        return task;
    }

    private void deleteTask() {
        if (editingTaskId == null) {
            showError("Select a Task from the table before deleting.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete Task #" + editingTaskId + "?\n"
                        + "Related assignments, comments, attachments and dependencies may be removed by the database CASCADE rules.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;

        int taskId = editingTaskId;
        setActionBusy(true);
        runAsync("delete Task", () -> editingService.delete(taskId), affectedRows -> {
            showInfo(affectedRows > 0 ? "Task deleted successfully." : "Task no longer exists.");
            clearForm();
            loadTasks();
        }, this::setActionBusyFalse);
    }

    private void clearForm() {
        editingTaskId = null;
        taskTable.clearSelection();
        taskIdValue.setText("New task (ID generated automatically)");
        loadingLookups = true;
        if (projectCombo.getItemCount() > 0) projectCombo.setSelectedIndex(0);
        if (teamCombo.getItemCount() > 0) teamCombo.setSelectedIndex(0);
        loadingLookups = false;
        titleField.setText("");
        descriptionArea.setText("");
        priorityCombo.setSelectedItem("MEDIUM");
        statusCombo.setSelectedItem("TODO");
        hoursField.setText("");
        dueDateField.setText("");
        memberList.clearSelection();
        ((CardLayout) assignmentCards.getLayout()).show(assignmentCards, "CREATE");
        setEditMode(false);
        setMessage("Create a task or select a row below to edit it.", false);
        loadCurrentTeamMembers();
    }

    private void setEditMode(boolean editing) {
        saveButton.setText(editing ? "Save changes" : "Add task");
        deleteButton.setEnabled(editing);
        modeLabel.setText(editing ? "Edit mode" : "Create mode");
        modeLabel.setForeground(editing ? new Color(0x0C447C) : MUTED);
    }

    private void selectLookup(JComboBox<LookupDAO.ComboItem> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId() == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void styleInput(Component component) {
        component.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (component instanceof JTextField) {
            ((JTextField) component).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LINE), new EmptyBorder(7, 9, 7, 9)));
            ((JTextField) component).setPreferredSize(new Dimension(180, 34));
        } else if (component instanceof JComboBox) {
            ((JComboBox<?>) component).setBorder(BorderFactory.createLineBorder(LINE));
            ((JComboBox<?>) component).setPreferredSize(new Dimension(180, 34));
            ((JComboBox<?>) component).setBackground(Color.WHITE);
        }
    }

    private void stylePrimaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        button.setFocusPainted(false);
    }

    private void styleSecondaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setForeground(TEXT);
        button.setBackground(Color.WHITE);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(8, 13, 8, 13)));
        button.setFocusPainted(false);
    }

    private void styleDangerButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setForeground(DANGER);
        button.setBackground(Color.WHITE);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE7CACA)), BorderFactory.createEmptyBorder(8, 13, 8, 13)));
        button.setFocusPainted(false);
    }

    private void setActionBusy(boolean busy) {
        newButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy && editingTaskId != null);
        refreshButton.setEnabled(!busy);
        projectCombo.setEnabled(!busy);
        teamCombo.setEnabled(!busy);
        titleField.setEnabled(!busy);
        descriptionArea.setEnabled(!busy);
        priorityCombo.setEnabled(!busy);
        statusCombo.setEnabled(!busy);
        hoursField.setEnabled(!busy);
        dueDateField.setEnabled(!busy);
        memberList.setEnabled(!busy && editingTaskId == null);
        taskTable.setEnabled(!busy);
    }

    private void setActionBusyFalse() {
        setActionBusy(false);
        setEditMode(editingTaskId != null);
    }

    private void setMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.setForeground(error ? DANGER : MUTED);
    }

    private void showError(String text) {
        setMessage(text, true);
        JOptionPane.showMessageDialog(this, text, "Unable to complete action", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String text) {
        setMessage(text, false);
        JOptionPane.showMessageDialog(this, text, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private String value(String text) { return text == null ? "" : text; }

    private <T> void runAsync(String operation, Callable<T> work, Consumer<T> onSuccess, Runnable onFinished) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }

            @Override protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null) onSuccess.accept(result);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("The operation was interrupted. Please try again.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    String message;
                    if (cause instanceof DataAccessException || cause instanceof IllegalArgumentException) {
                        message = cause.getMessage();
                    } else {
                        message = "Could not " + operation + ". Please check the database connection and try again.";
                    }
                    showError(message == null ? "An unexpected error occurred." : message);
                } finally {
                    if (onFinished != null) onFinished.run();
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> new EditTaskForm().setVisible(true));
    }

    private static final class LookupData {
        final List<LookupDAO.ComboItem> projects;
        final List<LookupDAO.ComboItem> teams;
        LookupData(List<LookupDAO.ComboItem> projects, List<LookupDAO.ComboItem> teams) {
            this.projects = projects;
            this.teams = teams;
        }
    }

    private static final class TaskDetails {
        final Task task;
        final List<String> assigneeNames;
        TaskDetails(Task task, List<String> assigneeNames) {
            this.task = task;
            this.assigneeNames = assigneeNames;
        }
    }
}
