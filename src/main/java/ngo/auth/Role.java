package ngo.auth;

/**
 * Application level of a logged-in volunteer. It is DERIVED at log-in, never stored:
 * ADMIN   = Organization Director (VOLUNTEER.system_role = 'DIRECTOR'),
 * MANAGER = Project Owner or current Team Leader,
 * USER    = Team Member (volunteer).
 */
public enum Role {
    ADMIN("Admin"), MANAGER("Manager"), USER("User");

    private final String label;

    Role(String label) { this.label = label; }

    public String label() { return label; }

    /** Admin or manager. */
    public boolean isStaff() { return this != USER; }
}
