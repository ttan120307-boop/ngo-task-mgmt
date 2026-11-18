package ngo.dao;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only queries for the assignees displayed on the Edit Task screen. */
public class TaskAssignmentReadDAO {
    public List<String> findAssigneeNames(int taskId) {
        String sql = "SELECT v.full_name "
                + "FROM TASK_ASSIGNMENT a "
                + "JOIN VOLUNTEER v ON v.volunteer_id = a.volunteer_id "
                + "WHERE a.task_id = ? "
                + "ORDER BY v.full_name";
        List<String> names = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("full_name"));
                return names;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
