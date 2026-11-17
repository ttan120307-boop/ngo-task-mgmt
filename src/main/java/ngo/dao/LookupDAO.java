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
 * Dữ liệu cho các ComboBox: danh sách Project, Team, Volunteer.
 * Mỗi phần tử là ComboItem (id + nhãn). Cho thẳng vào JComboBox, ô sẽ hiện nhãn,
 * còn id lấy bằng ((LookupDAO.ComboItem) combo.getSelectedItem()).getId().
 */
public class LookupDAO {

    /** Giá trị cố định (khớp CHECK của bảng TASK). Dùng cho combobox Priority / Status. */
    public static final String[] PRIORITIES = {"LOW", "MEDIUM", "HIGH", "URGENT"};
    public static final String[] STATUSES = {"TODO", "IN_PROGRESS", "DONE", "CANCELLED"};

    public static class ComboItem {
        private final int id;
        private final String label;

        public ComboItem(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public int getId() { return id; }
        public String getLabel() { return label; }

        @Override
        public String toString() { return label; }   // JComboBox hiển thị nhãn này
    }

    /** Tất cả project, sắp theo tên. */
    public List<ComboItem> projects() {
        return query("SELECT project_id, project_name FROM PROJECT ORDER BY project_name", null);
    }

    /** Tất cả team, sắp theo tên. */
    public List<ComboItem> teams() {
        return query("SELECT team_id, team_name FROM TEAM ORDER BY team_name", null);
    }

    /** Vai trò trong team (Leader, Member...), cho combobox khi tham gia team. */
    public List<ComboItem> teamRoles() {
        return query("SELECT team_role_id, role_name FROM TEAM_ROLE ORDER BY role_name", null);
    }

    /** Tất cả volunteer, sắp theo tên. */
    public List<ComboItem> volunteers() {
        return query("SELECT volunteer_id, full_name FROM VOLUNTEER ORDER BY full_name", null);
    }

    /**
     * Chỉ những người ĐANG là thành viên của team (left_date IS NULL).
     * Dùng cho combobox "Người thực hiện": đúng quy tắc BR8, tránh chọn nhầm người ngoài team.
     */
    public List<ComboItem> currentMembersOfTeam(int teamId) {
        return query("SELECT DISTINCT v.volunteer_id, v.full_name "
                + "FROM VOLUNTEER v JOIN MEMBERSHIP m ON m.volunteer_id = v.volunteer_id "
                + "WHERE m.team_id = ? AND m.left_date IS NULL "
                + "ORDER BY v.full_name", teamId);
    }

    private List<ComboItem> query(String sql, Integer param) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<ComboItem> list = new ArrayList<>();
                while (rs.next()) list.add(new ComboItem(rs.getInt(1), rs.getString(2)));
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
