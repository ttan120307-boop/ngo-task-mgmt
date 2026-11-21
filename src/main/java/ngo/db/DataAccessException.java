package ngo.db;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User-friendly exception: getMessage() returns a clear English message for UI display
 * (JOptionPane...). Raw SQL details are logged only and never exposed to the UI.
 */
public class DataAccessException extends RuntimeException {
    private static final Logger LOG = Logger.getLogger(DataAccessException.class.getName());

    // Messages from triggers (SIGNAL SQLSTATE '45000' -> MySQL error 1644)
    private static final Map<String, String> TRIGGER_MSG = new LinkedHashMap<>();
    // CHECK constraint name -> user-friendly message
    private static final Map<String, String> CHECK_MSG = new LinkedHashMap<>();

    static {
        TRIGGER_MSG.put("Assigned volunteer must be a member of the task team on the assigned date",
                "The volunteer must be a member of the task's team on the assigned date.");
        TRIGGER_MSG.put("The team of a task that already has assignments cannot be changed",
                "The team of a task that already has assignees cannot be changed.");
        TRIGGER_MSG.put("Task due date is after the project due date",
                "The task due date cannot be after the project due date.");
        TRIGGER_MSG.put("A task of this project is due after the new project due date",
                "A task of this project is due after the new project due date.");
        TRIGGER_MSG.put("Role id 1 is reserved for the role Leader, and Leader must have id 1",
                "Role id 1 is reserved for the role Leader.");
        TRIGGER_MSG.put("A task cannot start until all its prerequisite tasks are completed",
                "Cannot change status to IN_PROGRESS/DONE while prerequisite tasks are still unfinished.");
        TRIGGER_MSG.put("A task cannot be re-opened while a dependent task has already started",
                "Cannot reopen this task because a dependent task has already started.");
        TRIGGER_MSG.put("A task that has dependencies cannot move to another project",
                "Cannot move this task to another project because it has existing dependencies.");
        TRIGGER_MSG.put("A task cannot depend on itself", "A task cannot depend on itself.");
        TRIGGER_MSG.put("This dependency would create a circular dependency",
                "This dependency would create a circular loop (e.g., A requires B, B requires A...).");
        TRIGGER_MSG.put("Dependent and prerequisite task must belong to the same project",
                "Dependent and prerequisite tasks must belong to the same project.");
        TRIGGER_MSG.put("A task that has already started cannot get an unfinished prerequisite",
                "Cannot add an unfinished prerequisite to a task that has already started.");
        TRIGGER_MSG.put("A dependency cannot be updated; delete it and insert a new one",
                "Cannot update a dependency; please delete it and insert a new one.");
        TRIGGER_MSG.put("Joined date is before the volunteer registered date",
                "Joined date cannot be earlier than the volunteer's registration date.");
        TRIGGER_MSG.put("Joined date is before the team was created",
                "Joined date cannot be earlier than the team's creation date.");

        CHECK_MSG.put("ck_membership_dates", "Left date must be on or after the joined date.");
        CHECK_MSG.put("ck_project_name", "Project name cannot be empty.");
        CHECK_MSG.put("ck_task_priority", "Priority must be LOW, MEDIUM, HIGH, or URGENT.");
        CHECK_MSG.put("ck_task_status", "Status must be TODO, IN_PROGRESS, DONE, or CANCELLED.");
        CHECK_MSG.put("ck_task_title", "Task title cannot be empty.");
        CHECK_MSG.put("ck_task_hours", "Estimated hours must be greater than 0.");
        CHECK_MSG.put("ck_project_dates", "Due date must be on or after the start date.");
        CHECK_MSG.put("ck_comment_not_empty", "Comment content cannot be empty.");
        CHECK_MSG.put("ck_attachment_filename", "Attachment filename cannot be empty.");
        CHECK_MSG.put("ck_attachment_filepath", "Attachment file path cannot be empty.");
    }

    public DataAccessException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }

    public static DataAccessException from(SQLException e) {
        LOG.log(Level.SEVERE, "SQL error code=" + e.getErrorCode() + " state=" + e.getSQLState(), e);
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String msg;
        switch (e.getErrorCode()) {
            case 1644: // trigger SIGNAL
                msg = TRIGGER_MSG.getOrDefault(raw, raw);
                break;
            case 3819: // CHECK violated
                msg = "Invalid data (check constraint violated).";
                for (Map.Entry<String, String> en : CHECK_MSG.entrySet()) {
                    if (raw.contains(en.getKey())) { msg = en.getValue(); break; }
                }
                break;
            case 1062:
                if (raw.contains("uq_membership_one_current"))
                    msg = "This volunteer is already an active member of the team.";
                else if (raw.contains("uq_membership_one_leader"))
                    msg = "This team already has a current Leader.";
                else if (raw.contains("uq_membership_same_day"))
                    msg = "A membership record for this team on the same date already exists.";
                else
                    msg = "Duplicate data conflicts with an existing record.";
                break;
            case 1451:
                msg = "Cannot delete or update because this record is referenced by other data.";
                break;
            case 1452:
                msg = "Referenced record does not exist (invalid Project, Team, Volunteer, or Task).";
                break;
            case 1048:
                msg = "Missing required information (a mandatory field cannot be empty).";
                break;
            case 1406:
                msg = "Input data exceeds the maximum allowed field length.";
                break;
            case 1292:
            case 1366:
                msg = "Invalid value format (date or number).";
                break;
            case 1045:
                msg = "Invalid MySQL username or password (check config/db.properties).";
                break;
            case 1049:
                msg = "Database ngo_task_mgmt does not exist. Please run the .sql scripts first.";
                break;
            default:
                if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
                    msg = "Cannot connect to MySQL. Ensure MySQL is running and verify db.url in config/db.properties.";
                } else {
                    msg = "A database error occurred. Please try again or contact the administrator.";
                }
        }
        return new DataAccessException(msg, e);
    }
}
