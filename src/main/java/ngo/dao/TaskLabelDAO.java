package ngo.dao;

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
 * LABEL (bảng tra cứu, chỉ đọc) và TASK_LABEL (gắn nhãn cho task, thêm và xóa ngay trong form Task).
 * Dùng ComboItem để đổ thẳng vào JComboBox / JList.
 */
public class TaskLabelDAO {

    /** Tất cả nhãn, sắp theo tên (bảng LABEL, chỉ đọc). */
    public List<ComboItem> allLabels() {
        return labels("SELECT label_id, label_name FROM LABEL ORDER BY label_name", null);
    }

    /** Các nhãn đang gắn trên một task. */
    public List<ComboItem> labelsOf(int taskId) {
        return labels("SELECT l.label_id, l.label_name FROM TASK_LABEL tl "
                + "JOIN LABEL l ON l.label_id = tl.label_id "
                + "WHERE tl.task_id = ? ORDER BY l.label_name", taskId);
    }

    /** Các nhãn CHƯA gắn trên task: dùng cho combobox "thêm nhãn". */
    public List<ComboItem> availableLabelsFor(int taskId) {
        return labels("SELECT l.label_id, l.label_name FROM LABEL l "
                + "WHERE l.label_id NOT IN (SELECT label_id FROM TASK_LABEL WHERE task_id = ?) "
                + "ORDER BY l.label_name", taskId);
    }

    /** Gắn nhãn cho task. Gắn trùng thì báo lỗi dễ hiểu. */
    public void add(int taskId, int labelId) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO TASK_LABEL (task_id, label_id) VALUES (?, ?)")) {
            ps.setInt(1, taskId);
            ps.setInt(2, labelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new IllegalArgumentException("Task đã có nhãn này rồi.");
            throw DataAccessException.from(e);
        }
    }

    /** Gỡ nhãn khỏi task. Trả về số dòng đã xóa (0 nếu task không có nhãn đó). */
    public int remove(int taskId, int labelId) {
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
     * Đặt lại toàn bộ nhãn của task bằng đúng danh sách labelIds (xóa hết rồi thêm lại)
     * trong MỘT transaction: lỗi giữa chừng thì nhãn cũ được giữ nguyên.
     * Tiện khi form Task có danh sách tích chọn nhiều nhãn rồi bấm Lưu.
     */
    public void replaceLabels(int taskId, List<Integer> labelIds) {
        Tx.run(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM TASK_LABEL WHERE task_id = ?")) {
                del.setInt(1, taskId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO TASK_LABEL (task_id, label_id) VALUES (?, ?)")) {
                for (int labelId : new java.util.LinkedHashSet<>(labelIds)) {   // bỏ id trùng
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
