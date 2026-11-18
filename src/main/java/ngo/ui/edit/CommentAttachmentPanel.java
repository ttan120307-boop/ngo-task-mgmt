package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.TaskDAO;
import ngo.db.DataAccessException;
import ngo.model.Task;
import ngo.service.TaskService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Adds a comment and optional attachments through the existing TaskService transaction. */
public class CommentAttachmentPanel extends JPanel {
    private final TaskDAO taskDAO = new TaskDAO();
    private final LookupDAO lookupDAO = new LookupDAO();
    private final TaskService taskService = new TaskService();

    private final JComboBox<TaskChoice> taskCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> authorCombo = new JComboBox<>();
    private final JTextArea contentArea = new JTextArea(5, 36);
    private final DefaultListModel<Path> fileModel = new DefaultListModel<>();
    private final JList<Path> fileList = new JList<>(fileModel);
    private final JButton chooseFilesButton = new JButton("Choose attachments");
    private final JButton removeFileButton = new JButton("Remove selected file");
    private final JButton submitButton = new JButton("Add comment");
    private final JButton refreshButton = new JButton("Refresh lists");

    public CommentAttachmentPanel() {
        buildUi();
        chooseFilesButton.addActionListener(e -> chooseFiles());
        removeFileButton.addActionListener(e -> removeSelectedFile());
        submitButton.addActionListener(e -> addComment());
        refreshButton.addActionListener(e -> loadLists());
        loadLists();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createTitledBorder("Comment information"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; fields.add(new JLabel("Task *"), c);
        c.gridx = 1; c.weightx = 1; fields.add(taskCombo, c);
        c.gridx = 0; c.gridy++; c.weightx = 0; fields.add(new JLabel("Author *"), c);
        c.gridx = 1; c.weightx = 1; fields.add(authorCombo, c);
        c.gridx = 0; c.gridy++; c.weightx = 0; fields.add(new JLabel("Comment *"), c);
        c.gridx = 1; c.weightx = 1;
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        fields.add(new JScrollPane(contentArea), c);

        JPanel files = new JPanel(new BorderLayout(5, 5));
        files.setBorder(BorderFactory.createTitledBorder("Attachments (optional)"));
        fileList.setVisibleRowCount(4);
        files.add(new JScrollPane(fileList), BorderLayout.CENTER);
        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileButtons.add(chooseFilesButton);
        fileButtons.add(removeFileButton);
        files.add(fileButtons, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refreshButton);
        actions.add(submitButton);

        add(fields, BorderLayout.NORTH);
        add(files, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    private void loadLists() {
        setBusy(true);
        UiSupport.runAsync(this, "tải danh sách Task và Volunteer", () -> {
            return new Lists(taskDAO.search(null, null, null, null), lookupDAO.volunteers());
        }, lists -> {
            taskCombo.removeAllItems();
            for (Task task : lists.tasks) taskCombo.addItem(new TaskChoice(task));
            authorCombo.removeAllItems();
            for (LookupDAO.ComboItem volunteer : lists.volunteers) authorCombo.addItem(volunteer);
        }, () -> setBusy(false));
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (java.io.File file : chooser.getSelectedFiles()) {
                Path path = file.toPath();
                if (!fileModel.contains(path)) fileModel.addElement(path);
            }
        }
    }

    private void removeSelectedFile() {
        int index = fileList.getSelectedIndex();
        if (index >= 0) fileModel.remove(index);
    }

    private void addComment() {
        TaskChoice task = (TaskChoice) taskCombo.getSelectedItem();
        LookupDAO.ComboItem author = (LookupDAO.ComboItem) authorCombo.getSelectedItem();
        String content = contentArea.getText().trim();
        if (task == null) { UiSupport.showError(this, "Vui lòng chọn Task."); return; }
        if (author == null) { UiSupport.showError(this, "Vui lòng chọn người viết Comment."); return; }
        if (content.isEmpty()) { UiSupport.showError(this, "Nội dung Comment không được để trống."); return; }

        List<Path> selectedFiles = new ArrayList<>();
        for (int i = 0; i < fileModel.size(); i++) selectedFiles.add(fileModel.get(i));
        int taskId = task.getTaskId();
        int authorId = author.getId();
        setBusy(true);
        UiSupport.runAsync(this, "thêm Comment và Attachment", () -> saveCommentWithFiles(taskId, authorId, content, selectedFiles), commentId -> {
            UiSupport.showInfo(this, "Đã thêm Comment thành công. Comment ID trong Task: " + commentId);
            contentArea.setText("");
            fileModel.clear();
        }, () -> setBusy(false));
    }

    private int saveCommentWithFiles(int taskId, int authorId, String content, List<Path> selectedFiles) {
        List<TaskService.AttachmentInfo> attachmentInfos = new ArrayList<>();
        List<Path> copiedFiles = new ArrayList<>();
        try {
            Path folder = Paths.get("uploads", "tasks", String.valueOf(taskId)).toAbsolutePath().normalize();
            Files.createDirectories(folder);
            for (Path source : selectedFiles) {
                String originalName = source.getFileName().toString();
                String safeName = System.currentTimeMillis() + "_" + originalName.replaceAll("[^A-Za-z0-9._-]", "_");
                Path destination = folder.resolve(safeName).normalize();
                if (!destination.startsWith(folder)) throw new IllegalArgumentException("Tên file đính kèm không hợp lệ.");
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                copiedFiles.add(destination);
                String fileType = Files.probeContentType(destination);
                attachmentInfos.add(new TaskService.AttachmentInfo(
                        originalName,
                        Paths.get("uploads", "tasks", String.valueOf(taskId), safeName).toString(),
                        fileType == null ? "application/octet-stream" : fileType));
            }
            return taskService.addComment(taskId, authorId, content, attachmentInfos);
        } catch (IOException e) {
            deleteCopiedFiles(copiedFiles);
            throw new IllegalArgumentException("Không thể đọc hoặc lưu file đính kèm. Hãy kiểm tra quyền truy cập thư mục uploads.");
        } catch (DataAccessException | IllegalArgumentException e) {
            deleteCopiedFiles(copiedFiles);
            throw e;
        }
    }

    private void deleteCopiedFiles(List<Path> paths) {
        for (Path path : paths) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }
    }

    private void setBusy(boolean busy) {
        chooseFilesButton.setEnabled(!busy);
        removeFileButton.setEnabled(!busy);
        submitButton.setEnabled(!busy);
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
