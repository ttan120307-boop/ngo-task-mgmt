package ngo.dao;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MEMBERSHIP: tham gia và rời team.
 * LƯU Ý: rời team KHÔNG tự gỡ các phân công task cũ (quy tắc BR8 chỉ kiểm tra lúc giao việc).
 */
public class MembershipDAO {
    public static final String[] TEAM_MEMBER_COLUMNS = {"ID", "Tình nguyện viên", "Vai trò", "Tham gia"};
    public static final String[] VOLUNTEER_HISTORY_COLUMNS = {"ID", "Team", "Vai trò", "Tham gia", "Rời"};

    /** Tham gia team. joinedDate null thì dùng hôm nay. Trả về membership_id mới. */
    public int join(int volunteerId, int teamId, int teamRoleId, LocalDate joinedDate) {
        LocalDate d = joinedDate == null ? LocalDate.now() : joinedDate;
        String sql = "INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (?,?,?,?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, volunteerId);
            ps.setInt(2, teamId);
            ps.setInt(3, teamRoleId);
            ps.setDate(4, Date.valueOf(d));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Rời team: ghi left_date. leftDate null thì dùng hôm nay. */
    public void leave(int membershipId, LocalDate leftDate) {
        LocalDate d = leftDate == null ? LocalDate.now() : leftDate;
        String sql = "UPDATE MEMBERSHIP SET left_date = ? WHERE membership_id = ? AND left_date IS NULL";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(d));
            ps.setInt(2, membershipId);
            if (ps.executeUpdate() == 0)
                throw new IllegalArgumentException("Thành viên này đã rời team trước đó hoặc không tồn tại.");
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Thành viên hiện tại của một team. Cột: ID, Tình nguyện viên, Vai trò, Tham gia. */
    public List<Object[]> currentMembers(int teamId) {
        String sql = "SELECT m.membership_id, v.full_name, r.role_name, m.joined_date "
                + "FROM MEMBERSHIP m JOIN VOLUNTEER v ON v.volunteer_id = m.volunteer_id "
                + "JOIN TEAM_ROLE r ON r.team_role_id = m.team_role_id "
                + "WHERE m.team_id = ? AND m.left_date IS NULL ORDER BY v.full_name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getDate(4).toLocalDate().toString()});
                }
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Lịch sử tham gia các team của một volunteer. Cột: ID, Team, Vai trò, Tham gia, Rời ("" nếu đang là thành viên). */
    public List<Object[]> historyOfVolunteer(int volunteerId) {
        String sql = "SELECT m.membership_id, t.team_name, r.role_name, m.joined_date, m.left_date "
                + "FROM MEMBERSHIP m JOIN TEAM t ON t.team_id = m.team_id "
                + "JOIN TEAM_ROLE r ON r.team_role_id = m.team_role_id "
                + "WHERE m.volunteer_id = ? ORDER BY m.joined_date DESC, m.membership_id DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) {
                    Date left = rs.getDate(5);
                    list.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getDate(4).toLocalDate().toString(), left == null ? "" : left.toLocalDate().toString()});
                }
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
