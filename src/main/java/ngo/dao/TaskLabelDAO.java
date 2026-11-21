package ngo.dao;

import ngo.auth.Authz;

import ngo.dao.LookupDAO.ComboItem;
import ngo.db.DBConnection;
import ngo.db.DataAccessException;
import ngo.db.Tx;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * LABEL (lookup table, read-only) and TASK_LABEL (assigning labels to tasks, added and removed directly in the Task form).
 * Uses ComboItem to populate directly into JComboBox / JList.
 */
public class TaskLabelDAO {

    /** All labels, sorted by name (LABEL table, read-only). */
    public List<ComboItem> allLabels() {
        return labels("SELECT label_id, label_name FROM LABEL ORDER BY label_name", null);
    }

    /** Labels currently assigned to a task. */
    public List<ComboItem> labelsOf(int taskId) {
        return labels("SELECT l.label_id, l.label_name FROM TASK_LABEL tl " +
                      "JOIN LABEL l ON l.label_id = tl.label_id " +
                      "WHERE tl.task_id = ? ORDER BY l.label_name", taskId);
    }

    /** Labels NOT YET assigned to the task: used for the "add label" ComboBox. */
    public List<ComboItem> availableLabelsFor(int taskId) {
        return labels("SELECT l.label_id, l.label_name FROM LABEL l " +
                      "WHERE l.label_id NOT IN (SELECT label_id FROM TASK_LABEL WHERE task_id = ?) " +
                      "ORDER BY l.label_name", taskId);
    }

    /** Assigns a label to a task. Throws a user-friendly error on duplicate assignment. */
    public void add(int taskId, int labelId) {
        Authz.requireTaskEditorOfTask(taskId);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO TASK_LABEL (task_id, label_id) VALUES (?, ?)")) {
            ps.setInt(1, taskId);
            ps.setInt(2, labelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062)
                throw new IllegalArgumentException("This task already has the selected label.");
            throw DataAccessException.from(e);
        }
    }

    /** Removes a label from a task. Returns the number of deleted rows (0 if the task does not have that label). */
    public int remove(int taskId, int labelId) {
        Authz.requireTaskEditorOfTask(taskId);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM TASK_LABEL WHERE task_id = ? AND label_id = ?")) {
            ps.setInt(1, taskId);
            ps.setInt(2, labelId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /**
     * Replaces all labels of a task with the given list of labelIds (deletes all, then re-inserts)
     * in a SINGLE transaction: if an error occurs mid-way, the existing labels are preserved.
     * Convenient when the Task form has a multi-select checkbox list of labels and the user clicks Save.
     */
    public void replaceLabels(int taskId, List<Integer> labelIds) {
        Authz.requireTaskEditorOfTask(taskId);
        Tx.run(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM TASK_LABEL WHERE task_id = ?")) {
                del.setInt(1, taskId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO TASK_LABEL (task_id, label_id) VALUES (?, ?)")) {
                for (int labelId : new java.util.LinkedHashSet<>(labelIds)) {   // deduplicate IDs
                    ins.setInt(1, taskId);
                    ins.setInt(2, labelId);
                    ins.executeUpdate();
                }
            }
            return null;
        });
    }

    private List<ComboItem> labels(String sql, Integer param) {
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
