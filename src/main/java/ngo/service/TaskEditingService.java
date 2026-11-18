package ngo.service;

import ngo.dao.TaskDAO;
import ngo.db.Tx;
import ngo.model.Task;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Task CRUD service used by the new Edit Task screen. */
public class TaskEditingService {
    private static final BigDecimal MAX_HOURS = new BigDecimal("9999.99");
    private final TaskDAO taskDAO = new TaskDAO();

    /** Create Task and its initial assignees in one transaction. */
    public int create(Task task, List<Integer> volunteerIds) {
        validateTask(task);
        Set<Integer> uniqueAssignees = sanitizeVolunteerIds(volunteerIds);
        return Tx.run(connection -> {
            validateProjectDeadline(connection, task);
            int taskId = taskDAO.insert(connection, task);
            if (!uniqueAssignees.isEmpty()) {
                String sql = "INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id) VALUES (?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (Integer volunteerId : uniqueAssignees) {
                        ps.setInt(1, volunteerId);
                        ps.setInt(2, taskId);
                        ps.executeUpdate();
                    }
                }
            }
            return taskId;
        });
    }

    /** Update task fields; existing assignments remain unchanged. */
    public int update(Task task) {
        validateTask(task);
        if (task.getTaskId() <= 0) throw new IllegalArgumentException("Task ID không hợp lệ.");
        return Tx.run(connection -> {
            int oldTeamId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT team_id FROM TASK WHERE task_id = ? FOR UPDATE")) {
                ps.setInt(1, task.getTaskId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Task không còn tồn tại. Hãy tải lại danh sách.");
                    oldTeamId = rs.getInt("team_id");
                }
            }

            validateProjectDeadline(connection, task);
            if (oldTeamId != task.getTeamId()) {
                int invalidAssignees = countAssigneesNotInTeam(connection, task.getTaskId(), task.getTeamId());
                if (invalidAssignees > 0) {
                    throw new IllegalArgumentException(
                            "Không thể đổi Team vì có " + invalidAssignees
                            + " người đang được giao Task nhưng không phải thành viên hiện tại của Team mới. "
                            + "Hãy xử lý Assignment trước khi đổi Team.");
                }
            }

            // SQL triggers enforce dependency/project/status rules at database level.
            return taskDAO.update(connection, task);
        });
    }

    /** Delete Task; the database's FK CASCADE rules handle dependent records. */
    public int delete(int taskId) {
        if (taskId <= 0) throw new IllegalArgumentException("Task ID không hợp lệ.");
        return Tx.run(connection -> taskDAO.delete(connection, taskId));
    }

    private void validateTask(Task task) {
        if (task == null) throw new IllegalArgumentException("Không có dữ liệu Task.");
        String error = task.validate();
        if (error != null) throw new IllegalArgumentException(error);

        BigDecimal hours = task.getEstimatedHours();
        if (hours != null) {
            if (hours.compareTo(MAX_HOURS) > 0) {
                throw new IllegalArgumentException("Estimated hours không được vượt quá 9999.99.");
            }
            if (hours.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Estimated hours chỉ được có tối đa 2 chữ số thập phân.");
            }
        }
    }

    private Set<Integer> sanitizeVolunteerIds(List<Integer> ids) {
        Set<Integer> result = new LinkedHashSet<>();
        if (ids == null) return result;
        for (Integer id : ids) {
            if (id == null || id <= 0) throw new IllegalArgumentException("Volunteer được chọn không hợp lệ.");
            result.add(id);
        }
        return result;
    }

    private void validateProjectDeadline(java.sql.Connection connection, Task task) throws SQLException {
        String sql = "SELECT due_date FROM PROJECT WHERE project_id = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, task.getProjectId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Project được chọn không tồn tại.");
                Date projectDueSql = rs.getDate("due_date");
                LocalDate projectDue = projectDueSql == null ? null : projectDueSql.toLocalDate();
                if (task.getDueDate() != null && projectDue != null && task.getDueDate().isAfter(projectDue)) {
                    throw new IllegalArgumentException(
                            "Due date của Task không được sau deadline của Project (" + projectDue + ").");
                }
            }
        }
    }

    private int countAssigneesNotInTeam(java.sql.Connection connection, int taskId, int teamId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM TASK_ASSIGNMENT a "
                + "WHERE a.task_id = ? AND NOT EXISTS ("
                + "SELECT 1 FROM MEMBERSHIP m "
                + "WHERE m.volunteer_id = a.volunteer_id "
                + "AND m.team_id = ? AND m.left_date IS NULL)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.setInt(2, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
