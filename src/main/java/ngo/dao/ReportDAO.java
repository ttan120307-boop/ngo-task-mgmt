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
 * Dữ liệu cho màn hình Report: các truy vấn tổng hợp (GROUP BY).
 * Mỗi hàm trả về List<Object[]> (mỗi Object[] là một dòng của bảng báo cáo),
 * đi kèm một mảng tên cột public static final để đặt tiêu đề bảng.
 * Các cột số được trả về kiểu Integer nên bấm sắp xếp cột sẽ theo số, không theo chữ.
 *
 * "Trễ hạn" = task có hạn (due_date) trước hôm nay và chưa DONE, chưa CANCELLED.
 */
public class ReportDAO {

    // ---------------- Báo cáo 1: tổng hợp theo Project ----------------
    public static final String[] REPORT_COLUMNS = {
            "ID", "Project", "Chủ sở hữu", "Tổng task", "TODO", "Đang làm",
            "Hoàn thành", "Đã hủy", "Trễ hạn", "% hoàn thành"};

    /**
     * Mỗi dòng là một project: tổng số task và số task theo từng trạng thái, số task trễ hạn.
     * Cột cuối "% hoàn thành" = DONE / (tổng task trừ task đã hủy), làm tròn; project chưa có task thì 0.
     */
    public List<Object[]> reportRows() {
        String sql = "SELECT p.project_id, p.project_name, v.full_name AS owner_name, "
                + "COUNT(t.task_id) AS total_tasks, "
                + "SUM(CASE WHEN t.status = 'TODO' THEN 1 ELSE 0 END) AS todo_tasks, "
                + "SUM(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_tasks, "
                + "SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS done_tasks, "
                + "SUM(CASE WHEN t.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_tasks, "
                + "SUM(CASE WHEN t.due_date < CURDATE() AND t.status NOT IN ('DONE','CANCELLED') "
                + "         THEN 1 ELSE 0 END) AS overdue_tasks "
                + "FROM PROJECT p "
                + "JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id "
                + "LEFT JOIN TASK t ON t.project_id = p.project_id "
                + "GROUP BY p.project_id, p.project_name, v.full_name "
                + "ORDER BY p.project_id";
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
                rows.add(new Object[]{
                        rs.getInt("project_id"), rs.getString("project_name"), rs.getString("owner_name"),
                        total, rs.getInt("todo_tasks"), rs.getInt("in_progress_tasks"),
                        done, cancelled, rs.getInt("overdue_tasks"), percent});
            }
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    // ---------------- Báo cáo 2: số task theo từng thành viên ----------------
    public static final String[] VOLUNTEER_REPORT_COLUMNS = {
            "ID", "Tình nguyện viên", "Số task được giao", "Đang làm", "Hoàn thành", "Trễ hạn"};

    /**
     * Mỗi dòng là một tình nguyện viên đang được giao ít nhất một task, sắp theo số task giảm dần.
     */
    public List<Object[]> volunteerReportRows() {
        String sql = "SELECT v.volunteer_id, v.full_name, COUNT(*) AS total_tasks, "
                + "SUM(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_tasks, "
                + "SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS done_tasks, "
                + "SUM(CASE WHEN t.due_date < CURDATE() AND t.status NOT IN ('DONE','CANCELLED') "
                + "         THEN 1 ELSE 0 END) AS overdue_tasks "
                + "FROM VOLUNTEER v "
                + "JOIN TASK_ASSIGNMENT a ON a.volunteer_id = v.volunteer_id "
                + "JOIN TASK t ON t.task_id = a.task_id "
                + "GROUP BY v.volunteer_id, v.full_name "
                + "ORDER BY total_tasks DESC, v.full_name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getInt("volunteer_id"), rs.getString("full_name"), rs.getInt("total_tasks"),
                        rs.getInt("in_progress_tasks"), rs.getInt("done_tasks"), rs.getInt("overdue_tasks")});
            }
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
