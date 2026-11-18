package ngo.dao;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;
import ngo.model.Project;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Browse (Master) + CRUD for PROJECT. */
public class ProjectDAO {
    public static final String[] BROWSE_COLUMNS = {"ID", "Project Name", "Owner", "Start Date", "Due Date", "Task Count"};

    // ---------------- BROWSE ----------------
    public List<Object[]> browseRows() {
        String sql = "SELECT p.project_id, p.project_name, v.full_name AS owner_name, " +
                     "       p.start_date, p.due_date, COUNT(t.task_id) AS task_count " +
                     "FROM PROJECT p " +
                     "JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id " +
                     "LEFT JOIN TASK t ON t.project_id = p.project_id " +
                     "GROUP BY p.project_id, p.project_name, v.full_name, p.start_date, p.due_date " +
                     "ORDER BY p.project_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getInt("project_id"),
                        rs.getString("project_name"),
                        rs.getString("owner_name"),
                        rs.getDate("start_date").toLocalDate().toString(),
                        rs.getDate("due_date").toLocalDate().toString(),
                        rs.getInt("task_count")
                });
            }
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- CREATE ----------------
    /** Returns the newly generated project_id. */
    public int insert(Project p) {
        String sql = "INSERT INTO PROJECT (owner_id, project_name, description, start_date, due_date) " +
                     "VALUES (?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                p.setProjectId(rs.getInt(1));
                return p.getProjectId();
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- READ ONE ----------------
    /** Returns null if not found. */
    public Project findById(int projectId) {
        String sql = "SELECT project_id, owner_id, project_name, description, start_date, due_date " +
                     "FROM PROJECT WHERE project_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Project p = new Project();
                p.setProjectId(rs.getInt("project_id"));
                p.setOwnerId(rs.getInt("owner_id"));
                p.setProjectName(rs.getString("project_name"));
                p.setDescription(rs.getString("description"));
                p.setStartDate(rs.getDate("start_date").toLocalDate());
                p.setDueDate(rs.getDate("due_date").toLocalDate());
                return p;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- UPDATE ----------------
    public int update(Project p) {
        String sql = "UPDATE PROJECT SET owner_id=?, project_name=?, description=?, start_date=?, due_date=? " +
                     "WHERE project_id=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            ps.setInt(6, p.getProjectId());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- DELETE ----------------
    /** CAUTION: deleting a project will CASCADE delete all of its tasks (along with assignments, comments, attachments, and dependencies). */
    public int delete(int projectId) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM PROJECT WHERE project_id = ?")) {
            ps.setInt(1, projectId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Number of tasks belonging to the project: used to display a warning in the delete confirmation dialog. */
    public int countTasks(int projectId) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM TASK WHERE project_id = ?")) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    private void bind(PreparedStatement ps, Project p) throws SQLException {
        ps.setInt(1, p.getOwnerId());
        ps.setString(2, p.getProjectName());
        ps.setString(3, p.getDescription());                       // null -> NULL
        ps.setDate(4, Date.valueOf(p.getStartDate()));
        ps.setDate(5, Date.valueOf(p.getDueDate()));
    }
}
