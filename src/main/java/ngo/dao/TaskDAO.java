package ngo.dao;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;
import ngo.model.Task;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD cho TASK.
 * - Các hàm nhận Connection: dùng bên trong transaction (do Service quản lý commit/rollback).
 * - Các hàm không nhận Connection: tự mở/đóng kết nối, dùng cho thao tác đơn lẻ.
 * Luôn dùng PreparedStatement (chống SQL injection).
 */
public class TaskDAO {
    private static final String COLS =
            "task_id, project_id, team_id, title, description, priority, status, estimated_hours, due_date";

    /** Tiêu đề cột cho lưới Browse (khớp với browseRows). */
    public static final String[] BROWSE_COLUMNS =
            {"ID", "Tiêu đề", "Team", "Ưu tiên", "Trạng thái", "Hạn", "Người thực hiện"};

    // ---------------- CREATE ----------------
    public int insert(Connection c, Task t) throws SQLException {
        String sql = "INSERT INTO TASK (project_id, team_id, title, description, priority, status, "
                + "estimated_hours, due_date) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, t);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                t.setTaskId(rs.getInt(1));
                return t.getTaskId();
            }
        }
    }

    public int insert(Task t) {
        try (Connection c = DBConnection.getConnection()) {
            return insert(c, t);
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- READ ----------------
    public Task findById(int taskId) {
        String sql = "SELECT " + COLS + " FROM TASK WHERE task_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Search form: lọc động nhưng vẫn tham số hoá hoàn toàn. Tham số nào null/rỗng thì bỏ qua. */
    public List<Task> search(Integer projectId, String keyword, String status, String priority) {
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM TASK WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            params.add(projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title LIKE ? OR description LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            params.add(k);
            params.add(k);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND priority = ?");
            params.add(priority);
        }
        sql.append(" ORDER BY task_id");

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /**
     * Browse / Master-Detail: các task của 1 project, kèm tên team và danh sách người thực hiện.
     * Trả về List<Object[]> để đổ thẳng vào DefaultTableModel (JTable).
     */
    public List<Object[]> browseRows(int projectId) {
        String sql = "SELECT t.task_id, t.title, tm.team_name, t.priority, t.status, t.due_date, "
                + "       GROUP_CONCAT(v.full_name ORDER BY v.full_name SEPARATOR ', ') AS assignees "
                + "FROM TASK t "
                + "JOIN TEAM tm ON tm.team_id = t.team_id "
                + "LEFT JOIN TASK_ASSIGNMENT a ON a.task_id = t.task_id "
                + "LEFT JOIN VOLUNTEER v ON v.volunteer_id = a.volunteer_id "
                + "WHERE t.project_id = ? "
                + "GROUP BY t.task_id, t.title, tm.team_name, t.priority, t.status, t.due_date "
                + "ORDER BY t.task_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    Date due = rs.getDate("due_date");
                    rows.add(new Object[]{
                            rs.getInt("task_id"), rs.getString("title"), rs.getString("team_name"),
                            rs.getString("priority"), rs.getString("status"),
                            due == null ? "" : due.toLocalDate().toString(),
                            rs.getString("assignees") == null ? "" : rs.getString("assignees")});
                }
                return rows;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- UPDATE ----------------
    public int update(Connection c, Task t) throws SQLException {
        String sql = "UPDATE TASK SET project_id=?, team_id=?, title=?, description=?, priority=?, "
                + "status=?, estimated_hours=?, due_date=? WHERE task_id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, t);
            ps.setInt(9, t.getTaskId());
            return ps.executeUpdate();
        }
    }

    public int update(Task t) {
        try (Connection c = DBConnection.getConnection()) {
            return update(c, t);
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- DELETE ----------------
    /** Xoá TASK sẽ CASCADE sang TASK_ASSIGNMENT, TASK_COMMENT, ATTACHMENT, TASK_DEPENDENCY. */
    public int delete(Connection c, int taskId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM TASK WHERE task_id = ?")) {
            ps.setInt(1, taskId);
            return ps.executeUpdate();
        }
    }

    public int delete(int taskId) {
        try (Connection c = DBConnection.getConnection()) {
            return delete(c, taskId);
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- helpers ----------------
    private void bind(PreparedStatement ps, Task t) throws SQLException {
        ps.setInt(1, t.getProjectId());
        ps.setInt(2, t.getTeamId());
        ps.setString(3, t.getTitle());
        ps.setString(4, t.getDescription());                 // null -> NULL
        ps.setString(5, t.getPriority());
        ps.setString(6, t.getStatus());
        ps.setBigDecimal(7, t.getEstimatedHours());          // null -> NULL
        ps.setDate(8, t.getDueDate() == null ? null : Date.valueOf(t.getDueDate()));
    }

    private Task map(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.setTaskId(rs.getInt("task_id"));
        t.setProjectId(rs.getInt("project_id"));
        t.setTeamId(rs.getInt("team_id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setPriority(rs.getString("priority"));
        t.setStatus(rs.getString("status"));
        t.setEstimatedHours(rs.getBigDecimal("estimated_hours"));
        Date d = rs.getDate("due_date");
        t.setDueDate(d == null ? null : d.toLocalDate());
        return t;
    }
}
