package ngo.dao;

import ngo.auth.Authz;

import ngo.dao.LookupDAO.ComboItem;
import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * TASK_DEPENDENCY: "task A requires task B to be completed first".
 * Added and removed directly within the Task form. Errors (self-dependency, circular dependency, different projects...)
 * are raised by triggers and translated into user-friendly messages in DataAccessException.
 */
public class TaskDependencyDAO {
    public static final String[] COLUMNS = {"ID", "Title", "Status"};

    /** Prerequisite tasks of a task (must be completed first). Columns: ID, Title, Status. */
    public List<Object[]> prerequisitesOf(int taskId) {
        return rows("SELECT t.task_id, t.title, t.status FROM TASK_DEPENDENCY d " +
                    "JOIN TASK t ON t.task_id = d.prerequisite_task_id " +
                    "WHERE d.dependent_task_id = ? ORDER BY t.task_id", taskId);
    }

    /** Tasks that depend on this task. Columns: ID, Title, Status. */
    public List<Object[]> dependentsOf(int taskId) {
        return rows("SELECT t.task_id, t.title, t.status FROM TASK_DEPENDENCY d " +
                    "JOIN TASK t ON t.task_id = d.dependent_task_id " +
                    "WHERE d.prerequisite_task_id = ? ORDER BY t.task_id", taskId);
    }

    /** Tasks in the same project (excluding itself): used for the "add prerequisite task" ComboBox. */
    public List<ComboItem> candidatePrerequisites(int taskId) {
        String sql = "SELECT t.task_id, t.title FROM TASK t " +
                     "WHERE t.project_id = (SELECT project_id FROM TASK WHERE task_id = ?) " +
                     "AND t.task_id <> ? ORDER BY t.task_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.setInt(2, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ComboItem> list = new ArrayList<>();
                while (rs.next()) list.add(new ComboItem(rs.getInt(1), "#" + rs.getInt(1) + " " + rs.getString(2)));
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** dependentTaskId requires prerequisiteTaskId to be completed first. */
    public void add(int dependentTaskId, int prerequisiteTaskId) {
        Authz.requireTaskEditorOfTask(dependentTaskId);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (?, ?)")) {
            ps.setInt(1, dependentTaskId);
            ps.setInt(2, prerequisiteTaskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Returns the number of deleted rows (0 if the dependency does not exist). */
    public int remove(int dependentTaskId, int prerequisiteTaskId) {
        Authz.requireTaskEditorOfTask(dependentTaskId);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM TASK_DEPENDENCY WHERE dependent_task_id = ? AND prerequisite_task_id = ?")) {
            ps.setInt(1, dependentTaskId);
            ps.setInt(2, prerequisiteTaskId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    private List<Object[]> rows(String sql, int taskId) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) list.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
