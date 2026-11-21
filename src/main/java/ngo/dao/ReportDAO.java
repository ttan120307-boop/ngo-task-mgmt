package ngo.dao;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data for the Report screen: aggregate queries (GROUP BY).
 * Each method returns List<Object[]> (each Object[] is a row in the report table),
 * accompanied by a public static final array of column names for table headers.
 * Numeric columns are returned as Integer so sorting by column sorts numerically rather than alphabetically.
 *
 * "Overdue" = task with due_date before today and neither DONE nor CANCELLED.
 */
public class ReportDAO {
    // ---------------- Report 1: summary by Project ----------------
    public static final String[] REPORT_COLUMNS =
            {"ID", "Project", "Owner", "Total Tasks", "TODO", "In Progress", "Done", "Cancelled", "Overdue", "% Completed"};

    /**
     * Each row represents a project: total tasks, task count by status, and overdue task count.
     * The last column "% Completed" = DONE / (total tasks minus cancelled tasks), rounded; 0 if project has no active tasks.
     */
    public List<Object[]> reportRows() {
        String sql = "SELECT p.project_id, p.project_name, v.full_name AS owner_name, " +
                     "COUNT(t.task_id) AS total_tasks, " +
                     "SUM(CASE WHEN t.status = 'TODO' THEN 1 ELSE 0 END) AS todo_tasks, " +
                     "SUM(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_tasks, " +
                     "SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS done_tasks, " +
                     "SUM(CASE WHEN t.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_tasks, " +
                     "SUM(CASE WHEN t.due_date < CURDATE() AND t.status NOT IN ('DONE','CANCELLED') " +
                     "         THEN 1 ELSE 0 END) AS overdue_tasks " +
                     "FROM PROJECT p " +
                     "JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id " +
                     "LEFT JOIN TASK t ON t.project_id = p.project_id " +
                     "GROUP BY p.project_id, p.project_name, v.full_name " +
                     "ORDER BY p.project_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) {
                int total = rs.getInt("total_tasks");
                int done = rs.getInt("done_tasks");
                int cancelled = rs.getInt("cancelled_tasks");
                int active = total - cancelled;
                int percent = active <= 0 ? 0 : (int) Math.round(100.0 * done / active);
                rows.add(new Object[]{rs.getInt("project_id"), rs.getString("project_name"), rs.getString("owner_name"),
                        total, rs.getInt("todo_tasks"), rs.getInt("in_progress_tasks"), done, cancelled,
                        rs.getInt("overdue_tasks"), percent});
            }
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- Report 2: task count by Volunteer ----------------
    public static final String[] VOLUNTEER_REPORT_COLUMNS =
            {"ID", "Volunteer", "Assigned Tasks", "In Progress", "Done", "Overdue"};

    /**
     * Each row represents a volunteer assigned to at least one task, ordered by task count descending.
     */
    public List<Object[]> volunteerReportRows() {
        String sql = "SELECT v.volunteer_id, v.full_name, COUNT(*) AS total_tasks, " +
                     "SUM(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_tasks, " +
                     "SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS done_tasks, " +
                     "SUM(CASE WHEN t.due_date < CURDATE() AND t.status NOT IN ('DONE','CANCELLED') " +
                     "         THEN 1 ELSE 0 END) AS overdue_tasks " +
                     "FROM VOLUNTEER v " +
                     "JOIN TASK_ASSIGNMENT a ON a.volunteer_id = v.volunteer_id " +
                     "JOIN TASK t ON t.task_id = a.task_id " +
                     "GROUP BY v.volunteer_id, v.full_name " +
                     "ORDER BY total_tasks DESC, v.full_name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new Object[]{rs.getInt("volunteer_id"), rs.getString("full_name"),
                        rs.getInt("total_tasks"), rs.getInt("in_progress_tasks"), rs.getInt("done_tasks"),
                        rs.getInt("overdue_tasks")});
            }
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- Report 3: blocked tasks ----------------
    public static final String[] BLOCKED_COLUMNS =
            {"ID", "Task", "Project", "Unfinished prerequisites", "Waiting for task IDs"};

    /** TODO tasks that cannot start because at least one prerequisite is not DONE (Topic 6 query 3). */
    public List<Object[]> blockedTaskRows() {
        String sql = "SELECT t.task_id, t.title, p.project_name, COUNT(*) AS unfinished, " +
                     "GROUP_CONCAT(pre.task_id ORDER BY pre.task_id SEPARATOR ', ') AS waiting_for " +
                     "FROM TASK t " +
                     "JOIN PROJECT p ON p.project_id = t.project_id " +
                     "JOIN TASK_DEPENDENCY d ON d.dependent_task_id = t.task_id " +
                     "JOIN TASK pre ON pre.task_id = d.prerequisite_task_id " +
                     "WHERE t.status = 'TODO' AND pre.status <> 'DONE' " +
                     "GROUP BY t.task_id, t.title, p.project_name " +
                     "ORDER BY t.project_id, t.task_id";
        return run(sql, rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)});
    }

    // ---------------- Report 4: completed projects ----------------
    public static final String[] COMPLETED_COLUMNS = {"ID", "Project", "Owner", "Tasks (all DONE)"};

    /** Projects that have at least one task and where every task is DONE (NOT EXISTS, Topic 6 query 4). */
    public List<Object[]> completedProjectRows() {
        String sql = "SELECT p.project_id, p.project_name, v.full_name, " +
                     "(SELECT COUNT(*) FROM TASK t2 WHERE t2.project_id = p.project_id) AS n " +
                     "FROM PROJECT p JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id " +
                     "WHERE EXISTS (SELECT 1 FROM TASK t WHERE t.project_id = p.project_id) " +
                     "AND NOT EXISTS (SELECT 1 FROM TASK t WHERE t.project_id = p.project_id AND t.status <> 'DONE') " +
                     "ORDER BY p.project_id";
        return run(sql, rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)});
    }

    // ---------------- Report 5: volunteer who commented in most projects ----------------
    public static final String[] TOP_COMMENTER_COLUMNS = {"ID", "Volunteer", "Projects commented"};

    /** The volunteer(s) who commented on tasks of the largest number of different projects (Topic 6 query 5). */
    public List<Object[]> topCommenterRows() {
        String sql = "SELECT c.volunteer_id, v.full_name, COUNT(DISTINCT t.project_id) AS n " +
                     "FROM TASK_COMMENT c JOIN TASK t ON t.task_id = c.task_id " +
                     "JOIN VOLUNTEER v ON v.volunteer_id = c.volunteer_id " +
                     "GROUP BY c.volunteer_id, v.full_name " +
                     "HAVING COUNT(DISTINCT t.project_id) = (SELECT MAX(x.n) FROM (" +
                     "  SELECT COUNT(DISTINCT t2.project_id) AS n FROM TASK_COMMENT c2 " +
                     "  JOIN TASK t2 ON t2.task_id = c2.task_id GROUP BY c2.volunteer_id) x) " +
                     "ORDER BY c.volunteer_id";
        return run(sql, rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getInt(3)});
    }

    @FunctionalInterface
    private interface RowMapper { Object[] map(ResultSet rs) throws SQLException; }

    private List<Object[]> run(String sql, RowMapper mapper) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) rows.add(mapper.map(rs));
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
