package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.ProjectDAO;
import ngo.model.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Project create/read/update/delete screen backed by ProjectDAO. */
public class ProjectEditingPanel extends JPanel {
    private final ProjectDAO projectDAO = new ProjectDAO();
    private final LookupDAO lookupDAO = new LookupDAO();
    private final JComboBox<LookupDAO.ComboItem> ownerCombo = new JComboBox<>();
    private final JTextField nameField = new JTextField(28);
    private final JTextArea descriptionArea = new JTextArea(3, 28);
    private final JTextField startDateField = new JTextField(12);
    private final JTextField dueDateField = new JTextField(12);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Project ID", "Project", "Owner", "Start", "Due", "Tasks"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JButton addButton = new JButton("Add Project");
    private final JButton updateButton = new JButton("Save Changes");
    private final JButton deleteButton = new JButton("Delete Project");
    private final JButton clearButton = new JButton("New / Clear");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel modeLabel = new JLabel("Create mode");
    private List<Object[]> rows = new ArrayList<>();
    private boolean editMode;
    private boolean loadingOwners;

    public ProjectEditingPanel() {
        buildUi(); bindEvents(); loadOwners(); loadProjects(); setEditMode(false);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8)); setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel form = new JPanel(new GridBagLayout()); form.setBorder(BorderFactory.createTitledBorder("Project information"));
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(4, 6, 4, 6); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        int r = 0;
        addField(form, c, r++, "Project name *", nameField);
        addField(form, c, r++, "Owner *", ownerCombo);
        descriptionArea.setLineWrap(true); descriptionArea.setWrapStyleWord(true);
        addField(form, c, r++, "Description", new JScrollPane(descriptionArea));
        addField(form, c, r++, "Start date * (yyyy-MM-dd)", startDateField);
        addField(form, c, r++, "Due date * (yyyy-MM-dd)", dueDateField);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(addButton); actions.add(updateButton); actions.add(deleteButton); actions.add(clearButton); actions.add(refreshButton); actions.add(modeLabel);
        JPanel top = new JPanel(new BorderLayout(6, 6)); top.add(form, BorderLayout.CENTER); top.add(actions, BorderLayout.SOUTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true);
        JScrollPane grid = new JScrollPane(table); grid.setBorder(BorderFactory.createTitledBorder("Projects – select a row to edit"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, grid); split.setResizeWeight(0.55); add(split, BorderLayout.CENTER);
    }

    private void addField(JPanel p, GridBagConstraints c, int row, String label, Component field) {
        c.gridy=row; c.gridx=0; c.weightx=0; p.add(new JLabel(label),c); c.gridx=1; c.weightx=1; p.add(field,c);
    }

    private void bindEvents() {
        addButton.addActionListener(e -> addProject()); updateButton.addActionListener(e -> updateProject());
        deleteButton.addActionListener(e -> deleteProject()); clearButton.addActionListener(e -> clearForm());
        refreshButton.addActionListener(e -> loadProjects());
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) loadSelectedProject(); });
    }

    private void loadOwners() {
        setBusy(true);
        UiSupport.runAsync(this, "load Project owners", lookupDAO::volunteers, owners -> {
            loadingOwners = true; ownerCombo.removeAllItems(); owners.forEach(ownerCombo::addItem); loadingOwners = false;
        }, () -> setBusy(false));
    }

    private void loadProjects() {
        setBusy(true);
        UiSupport.runAsync(this, "load Projects", projectDAO::browseRows, result -> {
            rows = result; model.setRowCount(0); result.forEach(model::addRow);
        }, () -> setBusy(false));
    }

    private void loadSelectedProject() {
        int viewRow = table.getSelectedRow(); if (viewRow < 0) return;
        int row = table.convertRowIndexToModel(viewRow); if (row < 0 || row >= rows.size()) return;
        int id = ((Number) rows.get(row)[0]).intValue(); setBusy(true);
        UiSupport.runAsync(this, "load Project", () -> projectDAO.findById(id), project -> {
            if (project == null) { UiSupport.showError(this, "Project no longer exists. Refresh the list."); loadProjects(); return; }
            nameField.setText(project.getProjectName()); descriptionArea.setText(project.getDescription() == null ? "" : project.getDescription());
            selectOwner(project.getOwnerId()); startDateField.setText(project.getStartDate().toString()); dueDateField.setText(project.getDueDate().toString()); setEditMode(true);
        }, () -> setBusy(false));
    }

    private Project readProject() {
        LookupDAO.ComboItem owner = (LookupDAO.ComboItem) ownerCombo.getSelectedItem();
        if (owner == null) throw new IllegalArgumentException("Please select a Project owner.");
        Project p = new Project(); p.setOwnerId(owner.getId()); p.setProjectName(nameField.getText().trim());
        p.setDescription(descriptionArea.getText().trim().isEmpty() ? null : descriptionArea.getText().trim());
        try { p.setStartDate(LocalDate.parse(startDateField.getText().trim())); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Start date must use yyyy-MM-dd."); }
        try { p.setDueDate(LocalDate.parse(dueDateField.getText().trim())); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Due date must use yyyy-MM-dd."); }
        String error = p.validate(); if (error != null) throw new IllegalArgumentException(error); return p;
    }

    private int selectedProjectId() {
        int view = table.getSelectedRow(); if (view < 0) return -1;
        int row = table.convertRowIndexToModel(view); return row >= 0 && row < rows.size() ? ((Number) rows.get(row)[0]).intValue() : -1;
    }

    private void addProject() {
        final Project p; try { p = readProject(); } catch (IllegalArgumentException e) { UiSupport.showError(this, e.getMessage()); return; }
        setBusy(true); UiSupport.runAsync(this, "create Project", () -> projectDAO.insert(p), id -> {
            UiSupport.showInfo(this, "Project created. ID: " + id); clearForm(); loadProjects();
        }, () -> setBusy(false));
    }

    private void updateProject() {
        int id = selectedProjectId(); if (id <= 0) { UiSupport.showError(this, "Select a Project first."); return; }
        final Project p; try { p = readProject(); p.setProjectId(id); } catch (IllegalArgumentException e) { UiSupport.showError(this, e.getMessage()); return; }
        setBusy(true); UiSupport.runAsync(this, "update Project", () -> projectDAO.update(p), count -> {
            UiSupport.showInfo(this, count > 0 ? "Project updated." : "No Project row was changed."); loadProjects(); setEditMode(false);
        }, () -> setBusy(false));
    }

    private void deleteProject() {
        int id = selectedProjectId(); if (id <= 0) { UiSupport.showError(this, "Select a Project first."); return; }
        setBusy(true); UiSupport.runAsync(this, "check Project tasks", () -> projectDAO.countTasks(id), count -> {
            String warning = "Delete Project #" + id + "?";
            if (count > 0) warning += "\nThis Project contains " + count + " Task(s). Deleting it CASCADE-deletes those Tasks and related assignments, comments, attachments and dependencies.";
            int answer = JOptionPane.showConfirmDialog(this, warning, "Confirm delete Project", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) { setBusy(false); return; }
            UiSupport.runAsync(this, "delete Project", () -> projectDAO.delete(id), deleted -> {
                UiSupport.showInfo(this, deleted > 0 ? "Project deleted." : "Project no longer exists."); clearForm(); loadProjects();
            }, () -> setBusy(false));
        }, () -> setBusy(false));
    }

    private void selectOwner(int id) { for (int i=0;i<ownerCombo.getItemCount();i++) if (ownerCombo.getItemAt(i).getId()==id) { ownerCombo.setSelectedIndex(i); return; } }
    private void clearForm() { table.clearSelection(); nameField.setText(""); descriptionArea.setText(""); startDateField.setText(""); dueDateField.setText(""); if(ownerCombo.getItemCount()>0) ownerCombo.setSelectedIndex(0); setEditMode(false); }
    private void setEditMode(boolean editing) { editMode=editing; addButton.setEnabled(!editing); updateButton.setEnabled(editing); deleteButton.setEnabled(editing); modeLabel.setText(editing?"Edit mode":"Create mode"); }
    private void setBusy(boolean busy) { addButton.setEnabled(!busy&&!editMode); updateButton.setEnabled(!busy&&editMode); deleteButton.setEnabled(!busy&&editMode); clearButton.setEnabled(!busy); refreshButton.setEnabled(!busy); }
}
