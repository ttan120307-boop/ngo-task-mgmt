package ngo.service;

import ngo.auth.Authz;

import ngo.dao.TaskDAO;
import ngo.db.Tx;
import ngo.model.Task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Business operations updating MULTIPLE TABLES at once -> each method runs in a single transaction (COMMIT/ROLLBACK).
 * Errors raised by trigger BR8 (volunteer does not belong to the team) will ROLLBACK the entire operation.
 */
public class TaskService {
    private final TaskDAO taskDAO = new TaskDAO();

    public static class AttachmentInfo {
        public final String fileName, filePath, fileType;
        public AttachmentInfo(String fileName, String filePath, String fileType) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileType = fileType;
        }
    }

    /** (1) TASK + multiple TASK_ASSIGNMENT records in 1 transaction. Returns the new task_id. */
    public int createTaskWithAssignees(Task task, List<Integer> volunteerIds) {
        String err = task.validate();
        if (err != null) throw new IllegalArgumentException(err);

        return Tx.run(c -> {
            int taskId = taskDAO.insert(c, task);
            String sql = "INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id) VALUES (?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int vid : volunteerIds) {
                    ps.setInt(1, vid);
                    ps.setInt(2, taskId);
                    ps.executeUpdate();        // trigger trg_assignment_bi enforces rule BR8 here
                }
            }
            return taskId;
        });
    }

    /**
     * (2) TASK_COMMENT (weak entity) + ATTACHMENT (nested weak entity).
     * comment_id is a LOCAL sequence number per task -> computed as MAX+1 after locking the parent TASK row
     * so concurrent comments do not cause duplicate key conflicts.
     */
    public int addComment(int taskId, int authorId, String content, List<AttachmentInfo> files) {
        Authz.requireSelfOrAdmin(authorId);
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Comment content cannot be empty.");

        return Tx.run(c -> {
            try (PreparedStatement lock = c.prepareStatement("SELECT task_id FROM TASK WHERE task_id = ? FOR UPDATE")) {
                lock.setInt(1, taskId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Task does not exist: " + taskId);
                }
            }

            int commentId;
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(comment_id), 0) + 1 FROM TASK_COMMENT WHERE task_id = ?")) {
                ps.setInt(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    commentId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = c.prepareStatement("INSERT INTO TASK_COMMENT (task_id, comment_id, volunteer_id, content) VALUES (?,?,?,?)")) {
                ps.setInt(1, taskId);
                ps.setInt(2, commentId);
                ps.setInt(3, authorId);
                ps.setString(4, content);
                ps.executeUpdate();
            }

            if (files != null && !files.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ATTACHMENT (task_id, comment_id, attachment_id, file_name, file_path, file_type) " +
                        "VALUES (?,?,?,?,?,?)")) {
                    int attId = 1;                       // new comment -> attachment_id starts from 1
                    for (AttachmentInfo f : files) {
                        ps.setInt(1, taskId);
                        ps.setInt(2, commentId);
                        ps.setInt(3, attId++);
                        ps.setString(4, f.fileName);
                        ps.setString(5, f.filePath);
                        ps.setString(6, f.fileType);
                        ps.executeUpdate();
                    }
                }
            }
            return commentId;
        });
    }

    /**
     * (3) Reassign task: DELETE old assignment + INSERT new assignment.
     * If the new assignee is not a current member of the team -> trigger raises an error -> ROLLBACK,
     * restoring the previous assignment (a great example to demonstrate ROLLBACK).
     */
    public void transferAssignment(int taskId, int fromVolunteerId, int toVolunteerId) {
        Authz.requireTaskEditorOfTask(taskId);
        Tx.run(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM TASK_ASSIGNMENT WHERE task_id = ? AND volunteer_id = ?")) {
                del.setInt(1, taskId);
                del.setInt(2, fromVolunteerId);
                if (del.executeUpdate() == 0)
                    throw new IllegalArgumentException("Volunteer " + fromVolunteerId + " is not assigned to task " + taskId);
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id) VALUES (?, ?)")) {
                ins.setInt(1, toVolunteerId);
                ins.setInt(2, taskId);
                ins.executeUpdate();
            }
            return null;
        });
    }
}
