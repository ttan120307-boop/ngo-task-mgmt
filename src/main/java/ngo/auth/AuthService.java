package ngo.auth;

import ngo.db.DBConnection;
import ngo.db.DataAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Log-in against the table ACCOUNT. password_hash = SHA-256(salt || password) as 64 lower-case hex
 * characters (the same value as MySQL SHA2(CONCAT(salt, password), 256)). Only PreparedStatements are used.
 * A wrong user name and a wrong password give the SAME message, so user names cannot be guessed.
 */
public final class AuthService {
    public static final String BAD_LOGIN = "Invalid username or password.";
    private static final int MIN_PASSWORD = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthService() { }

    /** Verifies the credentials and starts the session. Throws IllegalArgumentException with a user message. */
    public static AuthUser login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty())
            throw new IllegalArgumentException("Please enter your username and password.");
        String sql = "SELECT a.volunteer_id, a.salt, a.password_hash, a.is_active, v.full_name, v.system_role "
                   + "FROM ACCOUNT a JOIN VOLUNTEER v ON v.volunteer_id = a.volunteer_id WHERE a.username = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    hash("x", password);            // same work as a real check (no timing hint)
                    throw new IllegalArgumentException(BAD_LOGIN);
                }
                boolean ok = MessageDigest.isEqual(
                        hash(rs.getString("salt"), password).getBytes(StandardCharsets.US_ASCII),
                        rs.getString("password_hash").getBytes(StandardCharsets.US_ASCII));
                if (!ok) throw new IllegalArgumentException(BAD_LOGIN);
                if (rs.getInt("is_active") != 1)
                    throw new IllegalArgumentException("This account is disabled. Please contact the Director.");
                int id = rs.getInt("volunteer_id");
                Role role = deriveRole(c, id, rs.getString("system_role"));
                AuthUser user = new AuthUser(id, rs.getString("full_name"), username.trim(), role);
                touchLastLogin(c, id);
                Session.set(user);
                return user;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** ADMIN = Director; MANAGER = owns a project or currently leads a team; otherwise USER. */
    private static Role deriveRole(Connection c, int volunteerId, String systemRole) throws SQLException {
        if ("DIRECTOR".equals(systemRole)) return Role.ADMIN;
        String sql = "SELECT (SELECT COUNT(*) FROM PROJECT WHERE owner_id = ?) + "
                   + "(SELECT COUNT(*) FROM MEMBERSHIP WHERE volunteer_id = ? AND team_role_id = 1 AND left_date IS NULL)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            ps.setInt(2, volunteerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0 ? Role.MANAGER : Role.USER;
            }
        }
    }

    private static void touchLastLogin(Connection c, int volunteerId) {
        try (PreparedStatement ps = c.prepareStatement("UPDATE ACCOUNT SET last_login = NOW() WHERE volunteer_id = ?")) {
            ps.setInt(1, volunteerId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // the time of the last log-in is informative only
        }
    }

    /** Changes the password of the logged-in user after checking the old one. */
    public static void changePassword(String oldPassword, String newPassword) {
        AuthUser me = Authz.user();
        if (newPassword == null || newPassword.length() < MIN_PASSWORD)
            throw new IllegalArgumentException("The new password must have at least " + MIN_PASSWORD + " characters.");
        if (newPassword.equals(oldPassword))
            throw new IllegalArgumentException("The new password must be different from the old one.");
        try (Connection c = DBConnection.getConnection()) {
            String salt;
            String stored;
            try (PreparedStatement ps = c.prepareStatement("SELECT salt, password_hash FROM ACCOUNT WHERE volunteer_id = ?")) {
                ps.setInt(1, me.getVolunteerId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Account not found.");
                    salt = rs.getString(1);
                    stored = rs.getString(2);
                }
            }
            if (!MessageDigest.isEqual(hash(salt, oldPassword == null ? "" : oldPassword).getBytes(StandardCharsets.US_ASCII),
                    stored.getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("The old password is wrong.");
            String newSalt = newSalt();
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE ACCOUNT SET salt = ?, password_hash = ? WHERE volunteer_id = ?")) {
                ps.setString(1, newSalt);
                ps.setString(2, hash(newSalt, newPassword));
                ps.setInt(3, me.getVolunteerId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** SHA-256(salt || password) as 64 lower-case hex characters. */
    public static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 32 random lower-case hex characters. */
    public static String newSalt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void logout() { Session.logout(); }
}
