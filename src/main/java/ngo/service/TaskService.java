package ngo.service;

import ngo.dao.TaskDAO;
import ngo.db.Tx;
import ngo.model.Task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Các nghiệp vụ cập nhật NHIỀU BẢNG cùng lúc -> mỗi hàm là một transaction (COMMIT/ROLLBACK).
 * Lỗi từ trigger BR8 (volunteer không thuộc team) sẽ làm ROLLBACK toàn bộ.
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

    /** (1) TASK + nhiều TASK_ASSIGNMENT trong 1 transaction. Trả về task_id mới. */
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
                    ps.executeUpdate();        // trigger trg_assignment_bi kiểm tra BR8 ở đây
                }
            }
            return taskId;
        });
    }

    /**
     * (2) TASK_COMMENT (weak entity) + ATTACHMENT (weak entity lồng nhau).
     * comment_id là số thứ tự CỤC BỘ theo task -> tự tính MAX+1 sau khi khoá dòng TASK cha
     * để hai người bình luận cùng lúc không bị trùng khoá.
     */
    public int addComment(int taskId, int authorId, String content, List<AttachmentInfo> files) {
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Nội dung bình luận không được để trống.");

        return Tx.run(c -> {
            try (PreparedStatement lock = c.prepareStatement("SELECT task_id FROM TASK WHERE task_id = ? FOR UPDATE")) {
                lock.setInt(1, taskId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Task không tồn tại: " + taskId);
                }
            }
            int commentId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(MAX(comment_id), 0) + 1 FROM TASK_COMMENT WHERE task_id = ?")) {
                ps.setInt(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    commentId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO TASK_COMMENT (task_id, comment_id, volunteer_id, content) VALUES (?,?,?,?)")) {
                ps.setInt(1, taskId);
                ps.setInt(2, commentId);
                ps.setInt(3, authorId);
                ps.setString(4, content);
                ps.executeUpdate();
            }
            if (files != null && !files.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ATTACHMENT (task_id, comment_id, attachment_id, file_name, file_path, file_type) "
                                + "VALUES (?,?,?,?,?,?)")) {
                    int attId = 1;                       // comment mới -> attachment_id bắt đầu từ 1
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
     * (3) Chuyển giao việc: DELETE assignment cũ + INSERT assignment mới.
     * Nếu người nhận không phải thành viên hiện tại của team -> trigger báo lỗi -> ROLLBACK,
     * assignment cũ được khôi phục (ví dụ rất tốt để demo ROLLBACK).
     */
    public void transferAssignment(int taskId, int fromVolunteerId, int toVolunteerId) {
        Tx.run(c -> {
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM TASK_ASSIGNMENT WHERE task_id = ? AND volunteer_id = ?")) {
                del.setInt(1, taskId);
                del.setInt(2, fromVolunteerId);
                if (del.executeUpdate() == 0)
                    throw new IllegalArgumentException("Volunteer " + fromVolunteerId
                            + " chưa được giao task " + taskId);
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id) VALUES (?, ?)")) {
                ins.setInt(1, toVolunteerId);
                ins.setInt(2, taskId);
                ins.executeUpdate();
            }
            return null;
        });
    }
}
