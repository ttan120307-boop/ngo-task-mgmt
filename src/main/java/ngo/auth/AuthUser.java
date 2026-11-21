package ngo.auth;

/** The volunteer that is logged in (immutable). */
public final class AuthUser {
    private final int volunteerId;
    private final String fullName;
    private final String username;
    private final Role role;

    public AuthUser(int volunteerId, String fullName, String username, Role role) {
        this.volunteerId = volunteerId;
        this.fullName = fullName;
        this.username = username;
        this.role = role;
    }

    public int getVolunteerId() { return volunteerId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
}
