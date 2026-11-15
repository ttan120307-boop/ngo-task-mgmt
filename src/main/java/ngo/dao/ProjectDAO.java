package ngo.dao;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Phần "Master" của Master-Detail: danh sách Project (chọn 1 dòng -> hiện Task ở bảng dưới). */
public class ProjectDAO {
    public static final String[] BROWSE_COLUMNS = {"ID", "Tên project", "Chủ sở hữu", "Bắt đầu", "Hạn", "Số task"};

    public List<Object[]> browseRows() {
        String sql = "SELECT p.project_id, p.project_name, v.full_name AS owner_name, "
                + "       p.start_date, p.due_date, COUNT(t.task_id) AS task_count "
                + "FROM PROJECT p "
                + "JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id "
                + "LEFT JOIN TASK t ON t.project_id = p.project_id "
                + "GROUP BY p.project_id, p.project_name, v.full_name, p.start_date, p.due_date "
                + "ORDER BY p.project_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getInt("project_id"), rs.getString("project_name"), rs.getString("owner_name"),
                        rs.getDate("start_date").toLocalDate().toString(),
                        rs.getDate("due_date").toLocalDate().toString(),
                        rs.getInt("task_count")});
            }
            return rows;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
