package ngo.auth;

/** Holds the logged-in user for the whole application (one user per running application). */
public final class Session {
    private static volatile AuthUser current;

    private Session() { }

    public static AuthUser user() { return current; }

    public static boolean isLoggedIn() { return current != null; }

    static void set(AuthUser user) { current = user; }

    public static void logout() { current = null; }

    public static Role role() { return current == null ? null : current.getRole(); }
}
