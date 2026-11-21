package ngo.auth;

/**
 * Thrown when the logged-in user is not allowed to do an operation. It extends
 * IllegalArgumentException so that every existing screen shows its message in an error dialog.
 */
public class AccessDeniedException extends IllegalArgumentException {
    public AccessDeniedException(String message) { super(message); }
}
