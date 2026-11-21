package ngo.auth;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Permission rules of the application (BR27). Every write operation of the DAO / service classes calls one of
 * the require...() methods first, so a screen that forgets to hide a button still cannot change the data.
 *
 *   ADMIN   : everything.
 *   MANAGER : creates projects he owns; edits/deletes his own projects; creates, edits, deletes, assigns, labels and links
 *             the tasks of his projects or of a team he leads; joins/removes members of a team he leads.
 *   USER    : reads everything; comments; changes the status of the tasks assigned to him (ngo.service.MyTaskService).
 *
 * Limit (state it in the report): these checks live in the Java application. MySQL itself still sees one shared
 * database user (config/db.properties); real per-user database privileges are a future improvement.
 */
public final class Authz {
    private Authz() { }

    public static AuthUser user() {
        AuthUser u = Session.user();
        if (u == null) throw new AccessDeniedException("Please log in first.");
        return u;
    }

    public static boolean isAdmin() { return Session.role() == Role.ADMIN; }

    public static boolean isStaff() { return Session.role() != null && Session.role().isStaff(); }

    // ---------------- checks used by the screens (no exception) ----------------
    public static boolean canEditProject(int projectId) {
        AuthUser u = Session.user();
        return u != null && (u.getRole() == Role.ADMIN || (u.getRole() == Role.MANAGER && ownsProject(u, projectId)));
    }

    // ---------------- checks used by the DAO / service classes (throw) ----------------
    public static void requireAdmin() {
        if (user().getRole() != Role.ADMIN) throw new AccessDeniedException("Only an Admin (Director) can do this.");
    }

    public static void requireStaff() {
        if (!user().getRole().isStaff()) throw new AccessDeniedException("Only a Manager or Admin can do this.");
    }

    /** Create a project: a Manager can only create a project that he owns. */
    public static void requireCanCreateProject(int ownerId) {
        AuthUser u = user();
        if (u.getRole() == Role.ADMIN) return;
        if (u.getRole() == Role.MANAGER && ownerId == u.getVolunteerId()) return;
        throw new AccessDeniedException("You can only create a project that you own.");
    }

    /** Edit or delete a project; newOwnerId is the owner after the change (use the old owner for a delete). */
    public static void requireProjectManager(int projectId, int newOwnerId) {
        AuthUser u = user();
        if (u.getRole() == Role.ADMIN) return;
        if (u.getRole() == Role.MANAGER && ownsProject(u, projectId)) {
            if (newOwnerId != u.getVolunteerId())
                throw new AccessDeniedException("Only an Admin can give a project to another owner.");
            return;
        }
        throw new AccessDeniedException("Only the owner of this project (or an Admin) can change it.");
    }

    /** Create or change a task of the given project and team. */
    public static void requireTaskEditor(int projectId, int teamId) {
        AuthUser u = user();
        if (u.getRole() == Role.ADMIN) return;
        if (u.getRole() == Role.MANAGER && (ownsProject(u, projectId) || leadsTeam(u, teamId))) return;
        throw new AccessDeniedException("Only the project owner, the leader of the responsible team or an Admin can change this task.");
    }

    /** Change an existing task (its project and team are read from the database). */
    public static void requireTaskEditorOfTask(int taskId) {
        AuthUser u = user();
        if (u.getRole() == Role.ADMIN) return;
        int[] pt = projectAndTeamOf(taskId);
        if (pt == null) return;                    // unknown task: the database reports it
        requireTaskEditor(pt[0], pt[1]);
    }

    /** Join or leave a team. */
    public static void requireTeamManager(int teamId) {
        AuthUser u = user();
        if (u.getRole() == Role.ADMIN) return;
        if (u.getRole() == Role.MANAGER && leadsTeam(u, teamId)) return;
        throw new AccessDeniedException("Only the leader of this team (or an Admin) can change its members.");
    }

    public static void requireTeamManagerOfMembership(int membershipId) {
        AuthUser u = user();
        if (u.getRole() == Role.ADMIN) return;
        Integer teamId = queryInt("SELECT team_id FROM MEMBERSHIP WHERE membership_id = ?", membershipId);
        if (teamId == null) return;
        requireTeamManager(teamId);
    }

    /** A volunteer may only act as himself (the Admin may act for anybody). */
    public static void requireSelfOrAdmin(int volunteerId) {
        AuthUser u = user();
        if (u.getRole() != Role.ADMIN && u.getVolunteerId() != volunteerId)
            throw new AccessDeniedException("You can only do this as yourself.");
    }

    // ---------------- helpers ----------------
    private static boolean ownsProject(AuthUser u, int projectId) {
        Integer owner = queryInt("SELECT owner_id FROM PROJECT WHERE project_id = ?", projectId);
        return owner != null && owner == u.getVolunteerId();
    }

    private static boolean leadsTeam(AuthUser u, int teamId) {
        Integer n = queryInt("SELECT COUNT(*) FROM MEMBERSHIP WHERE volunteer_id = ? AND team_id = ? "
                + "AND team_role_id = 1 AND left_date IS NULL", u.getVolunteerId(), teamId);
        return n != null && n > 0;
    }

    private static int[] projectAndTeamOf(int taskId) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT project_id, team_id FROM TASK WHERE task_id = ?")) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new int[]{rs.getInt(1), rs.getInt(2)} : null;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    private static Integer queryInt(String sql, int... params) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setInt(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
