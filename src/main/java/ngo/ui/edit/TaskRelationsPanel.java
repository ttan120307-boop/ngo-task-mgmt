package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.TaskDependencyDAO;
import ngo.dao.TaskLabelDAO;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Immediate add/remove of Labels and prerequisite Dependencies for the selected Task. */
final class TaskRelationsPanel extends JPanel {
    private final TaskLabelDAO labelDAO = new TaskLabelDAO();
    private final TaskDependencyDAO dependencyDAO = new TaskDependencyDAO();
    private int taskId = -1;

    private final DefaultListModel<LookupDAO.ComboItem> labelsModel = new DefaultListModel<>();
    private final JList<LookupDAO.ComboItem> labelsList = new JList<>(labelsModel);
    private final JComboBox<LookupDAO.ComboItem> availableLabels = new JComboBox<>();
    private final JButton addLabel = new JButton("Add label");
    private final JButton removeLabel = new JButton("Remove selected");

    private final DefaultTableModel prerequisiteModel = new DefaultTableModel(
            new Object[]{"Task ID", "Title", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable prerequisiteTable = new JTable(prerequisiteModel);
    private final JComboBox<LookupDAO.ComboItem> candidates = new JComboBox<>();
    private final JButton addDependency = new JButton("Add dependency");
    private final JButton removeDependency = new JButton("Remove selected");
    private final JLabel selectedTaskLabel = new JLabel("Select a Task in the table below.");

    TaskRelationsPanel() {
        buildUi();
        addLabel.addActionListener(e -> addLabel());
        removeLabel.addActionListener(e -> removeLabel());
        addDependency.addActionListener(e -> addDependency());
        removeDependency.addActionListener(e -> removeDependency());
        setControlsEnabled(false);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        add(selectedTaskLabel, BorderLayout.NORTH);

        JPanel labels = new JPanel(new BorderLayout(6, 6));
        labels.setBorder(BorderFactory.createTitledBorder("Task Labels"));
        labelsList.setVisibleRowCount(5);
        labels.add(new JScrollPane(labelsList), BorderLayout.CENTER);
        JPanel labelActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        availableLabels.setPreferredSize(new Dimension(240, 28));
        labelActions.add(availableLabels);
        labelActions.add(addLabel);
        labelActions.add(removeLabel);
        labels.add(labelActions, BorderLayout.SOUTH);

        JPanel dependencies = new JPanel(new BorderLayout(6, 6));
        dependencies.setBorder(BorderFactory.createTitledBorder("Task Prerequisites / Dependencies"));
        prerequisiteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        prerequisiteTable.setFillsViewportHeight(true);
        dependencies.add(new JScrollPane(prerequisiteTable), BorderLayout.CENTER);
        JPanel dependencyActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        candidates.setPreferredSize(new Dimension(320, 28));
        dependencyActions.add(candidates);
        dependencyActions.add(addDependency);
        dependencyActions.add(removeDependency);
        dependencies.add(dependencyActions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, labels, dependencies);
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);
    }

    void setSelectedTask(int id, String title) {
        taskId = id;
        selectedTaskLabel.setText(id > 0 ? "Editing relations for Task #" + id + " – " + title
                : "Select a Task in the table below.");
        clearData();
        setControlsEnabled(id > 0);
        if (id > 0) refresh();
    }

    private void refresh() {
        if (taskId <= 0) return;
        int selectedId = taskId;
        setControlsEnabled(false);
        UiSupport.runAsync(this, "load Task Labels and Dependencies", () -> new RelationData(
                labelDAO.labelsOf(selectedId), labelDAO.availableLabelsFor(selectedId),
                dependencyDAO.prerequisitesOf(selectedId), dependencyDAO.candidatePrerequisites(selectedId)), data -> {
            if (taskId != selectedId) return;
            labelsModel.clear();
            data.labels.forEach(labelsModel::addElement);
            availableLabels.removeAllItems();
            data.availableLabels.forEach(availableLabels::addItem);
            prerequisiteModel.setRowCount(0);
            data.prerequisites.forEach(prerequisiteModel::addRow);
            candidates.removeAllItems();
            data.candidates.forEach(candidates::addItem);
        }, () -> setControlsEnabled(taskId > 0));
    }

    private void addLabel() {
        LookupDAO.ComboItem item = (LookupDAO.ComboItem) availableLabels.getSelectedItem();
        if (taskId <= 0 || item == null) { UiSupport.showError(this, "Select a Task and a Label to add."); return; }
        int selectedId = taskId, labelId = item.getId();
        setControlsEnabled(false);
        UiSupport.runAsync(this, "add Label", () -> { labelDAO.add(selectedId, labelId); return null; },
                ignored -> { UiSupport.showInfo(this, "Label added to Task."); refresh(); },
                () -> setControlsEnabled(taskId > 0));
    }

    private void removeLabel() {
        LookupDAO.ComboItem item = labelsList.getSelectedValue();
        if (taskId <= 0 || item == null) { UiSupport.showError(this, "Select a Label to remove."); return; }
        int answer = JOptionPane.showConfirmDialog(this, "Remove Label '" + item.getLabel() + "' from Task #" + taskId + "?",
                "Confirm remove Label", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        int selectedId = taskId, labelId = item.getId();
        setControlsEnabled(false);
        UiSupport.runAsync(this, "remove Label", () -> labelDAO.remove(selectedId, labelId), rows -> {
            UiSupport.showInfo(this, rows > 0 ? "Label removed." : "Label was not attached to this Task."); refresh();
        }, () -> setControlsEnabled(taskId > 0));
    }

    private void addDependency() {
        LookupDAO.ComboItem item = (LookupDAO.ComboItem) candidates.getSelectedItem();
        if (taskId <= 0 || item == null) { UiSupport.showError(this, "Select a prerequisite Task to add."); return; }
        int selectedId = taskId, prerequisiteId = item.getId();
        setControlsEnabled(false);
        UiSupport.runAsync(this, "add Task Dependency", () -> {
            dependencyDAO.add(selectedId, prerequisiteId); return null;
        }, ignored -> { UiSupport.showInfo(this, "Prerequisite added."); refresh(); },
                () -> setControlsEnabled(taskId > 0));
    }

    private void removeDependency() {
        int viewRow = prerequisiteTable.getSelectedRow();
        if (taskId <= 0 || viewRow < 0) { UiSupport.showError(this, "Select a Dependency to remove."); return; }
        int row = prerequisiteTable.convertRowIndexToModel(viewRow);
        int prerequisiteId = ((Number) prerequisiteModel.getValueAt(row, 0)).intValue();
        int answer = JOptionPane.showConfirmDialog(this,
                "Remove prerequisite Task #" + prerequisiteId + " from Task #" + taskId + "?",
                "Confirm remove Dependency", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        int selectedId = taskId;
        setControlsEnabled(false);
        UiSupport.runAsync(this, "remove Task Dependency", () -> dependencyDAO.remove(selectedId, prerequisiteId), rows -> {
            UiSupport.showInfo(this, rows > 0 ? "Dependency removed." : "Dependency no longer exists."); refresh();
        }, () -> setControlsEnabled(taskId > 0));
    }

    private void clearData() {
        labelsModel.clear(); availableLabels.removeAllItems(); prerequisiteModel.setRowCount(0); candidates.removeAllItems();
    }

    private void setControlsEnabled(boolean enabled) {
        labelsList.setEnabled(enabled); availableLabels.setEnabled(enabled); addLabel.setEnabled(enabled); removeLabel.setEnabled(enabled);
        prerequisiteTable.setEnabled(enabled); candidates.setEnabled(enabled); addDependency.setEnabled(enabled); removeDependency.setEnabled(enabled);
    }

    private static final class RelationData {
        final List<LookupDAO.ComboItem> labels, availableLabels, candidates;
        final List<Object[]> prerequisites;
        RelationData(List<LookupDAO.ComboItem> labels, List<LookupDAO.ComboItem> availableLabels,
                     List<Object[]> prerequisites, List<LookupDAO.ComboItem> candidates) {
            this.labels = labels; this.availableLabels = availableLabels;
            this.prerequisites = prerequisites; this.candidates = candidates;
        }
    }
}
