package ngo.dao;

import ngo.auth.Authz;

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
 * MEMBERSHIP: joining and leaving a team.
 * NOTE: leaving a team does NOT automatically remove existing task assignments (rule BR8 is only checked upon task assignment).
 */
public class MembershipDAO {
    public static final String[] TEAM_MEMBER_COLUMNS = {"ID", "Volunteer", "Role", "Joined"};
    public static final String[] VOLUNTEER_HISTORY_COLUMNS = {"ID", "Team", "Role", "Joined", "Left"};

    /** Join a team. If joinedDate is null, defaults to today. Returns the new membership_id. */
    public int join(int volunteerId, int teamId, int teamRoleId, LocalDate joinedDate) {
        Authz.requireTeamManager(teamId);
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

    /** Leave a team: sets left_date. If leftDate is null, defaults to today. */
    public void leave(int membershipId, LocalDate leftDate) {
        Authz.requireTeamManagerOfMembership(membershipId);
        LocalDate d = leftDate == null ? LocalDate.now() : leftDate;
        String sql = "UPDATE MEMBERSHIP SET left_date = ? WHERE membership_id = ? AND left_date IS NULL";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(d));
            ps.setInt(2, membershipId);
            if (ps.executeUpdate() == 0)
                throw new IllegalArgumentException("This member has already left the team or does not exist.");
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Current members of a team. Columns: ID, Volunteer, Role, Joined. */
    public List<Object[]> currentMembers(int teamId) {
        String sql = "SELECT m.membership_id, v.full_name, r.role_name, m.joined_date " +
                     "FROM MEMBERSHIP m JOIN VOLUNTEER v ON v.volunteer_id = m.volunteer_id " +
                     "JOIN TEAM_ROLE r ON r.team_role_id = m.team_role_id " +
                     "WHERE m.team_id = ? AND m.left_date IS NULL ORDER BY v.full_name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate().toString()});
                }
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Team membership history of a volunteer. Columns: ID, Team, Role, Joined, Left ("" if currently active). */
    public List<Object[]> historyOfVolunteer(int volunteerId) {
        String sql = "SELECT m.membership_id, t.team_name, r.role_name, m.joined_date, m.left_date " +
                     "FROM MEMBERSHIP m JOIN TEAM t ON t.team_id = m.team_id " +
                     "JOIN TEAM_ROLE r ON r.team_role_id = m.team_role_id " +
                     "WHERE m.volunteer_id = ? ORDER BY m.joined_date DESC, m.membership_id DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> list = new ArrayList<>();
                while (rs.next()) {
                    Date left = rs.getDate(5);
                    list.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate().toString(),
                            left == null ? "" : left.toLocalDate().toString()});
                }
                return list;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
