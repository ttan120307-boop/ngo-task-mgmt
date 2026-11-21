package ngo.service;

import ngo.auth.AccessDeniedException;
import ngo.auth.Authz;
import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** What every logged-in volunteer (role USER included) may do with the tasks assigned to him/her. */
public class MyTaskService {
    public static final String[] COLUMNS = {"ID", "Title", "Project", "Priority", "Status", "Due Date"};
    public static final String[] USER_STATUSES = {"TODO", "IN_PROGRESS", "DONE"};

    public List<Object[]> myTasks() {
        int me = Authz.user().getVolunteerId();
        String sql = "SELECT t.task_id, t.title, p.project_name, t.priority, t.status, t.due_date "
                   + "FROM TASK t JOIN TASK_ASSIGNMENT a ON a.task_id = t.task_id "
                   + "JOIN PROJECT p ON p.project_id = t.project_id "
                   + "WHERE a.volunteer_id = ? ORDER BY (t.status IN ('DONE','CANCELLED')), t.due_date, t.task_id";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, me);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getDate(6)});
                }
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
        return rows;
    }

    /** Changes the status of a task assigned to the logged-in volunteer (TODO / IN_PROGRESS / DONE only). */
    public void updateStatus(int taskId, String newStatus) {
        if (!Set.of(USER_STATUSES).contains(newStatus))
            throw new IllegalArgumentException("You can only set TODO, IN_PROGRESS or DONE.");
        int me = Authz.user().getVolunteerId();
        try (Connection c = DBConnection.getConnection()) {
            String current;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t.status FROM TASK t JOIN TASK_ASSIGNMENT a ON a.task_id = t.task_id "
                  + "WHERE t.task_id = ? AND a.volunteer_id = ?")) {
                ps.setInt(1, taskId);
                ps.setInt(2, me);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new AccessDeniedException("This task is not assigned to you.");
                    current = rs.getString(1);
                }
            }
            if ("CANCELLED".equals(current))
                throw new IllegalArgumentException("A cancelled task cannot be changed.");
            try (PreparedStatement ps = c.prepareStatement("UPDATE TASK SET status = ? WHERE task_id = ?")) {
                ps.setString(1, newStatus);
                ps.setInt(2, taskId);
                ps.executeUpdate();            // triggers of the database still check the business rules (e.g. BR21)
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Adds a comment written by the logged-in volunteer. */
    public int addComment(int taskId, String content) {
        return new TaskService().addComment(taskId, Authz.user().getVolunteerId(), content, new ArrayList<>());
    }
}
