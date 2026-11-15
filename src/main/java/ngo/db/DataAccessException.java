package ngo.db;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ngoại lệ "thân thiện": getMessage() là câu tiếng Việt dễ hiểu để UI hiển thị
 * (JOptionPane...). Chi tiết SQL gốc chỉ ghi vào log, không đưa lên giao diện.
 */
public class DataAccessException extends RuntimeException {
    private static final Logger LOG = Logger.getLogger(DataAccessException.class.getName());

    // Thông báo từ trigger (SIGNAL SQLSTATE '45000' -> MySQL error 1644)
    private static final Map<String, String> TRIGGER_MSG = new LinkedHashMap<>();
    // Tên CHECK constraint -> thông báo
    private static final Map<String, String> CHECK_MSG = new LinkedHashMap<>();
    static {
        TRIGGER_MSG.put("Assigned volunteer must be a current member of the task team",
                "Tình nguyện viên phải là thành viên hiện tại của team phụ trách task.");
        TRIGGER_MSG.put("A task cannot start until all its prerequisite tasks are completed",
                "Task chưa thể chuyển sang IN_PROGRESS/DONE vì còn task tiên quyết chưa hoàn thành.");
        TRIGGER_MSG.put("A task cannot be re-opened while a dependent task has already started",
                "Không thể mở lại task vì có task phụ thuộc đã bắt đầu.");
        TRIGGER_MSG.put("A task that has dependencies cannot move to another project",
                "Task đang có phụ thuộc nên không thể chuyển sang project khác.");
        TRIGGER_MSG.put("A task cannot depend on itself", "Task không thể phụ thuộc vào chính nó.");
        TRIGGER_MSG.put("This dependency would create a circular dependency",
                "Phụ thuộc này tạo ra vòng lặp (A cần B, B cần A...).");
        TRIGGER_MSG.put("Dependent and prerequisite task must belong to the same project",
                "Hai task phụ thuộc nhau phải thuộc cùng một project.");

        CHECK_MSG.put("ck_task_priority", "Priority phải là LOW, MEDIUM, HIGH hoặc URGENT.");
        CHECK_MSG.put("ck_task_status", "Status phải là TODO, IN_PROGRESS, DONE hoặc CANCELLED.");
        CHECK_MSG.put("ck_task_title", "Tiêu đề task không được để trống.");
        CHECK_MSG.put("ck_task_hours", "Số giờ ước tính phải lớn hơn 0.");
        CHECK_MSG.put("ck_project_dates", "Ngày hết hạn phải lớn hơn hoặc bằng ngày bắt đầu.");
        CHECK_MSG.put("ck_comment_not_empty", "Nội dung bình luận không được để trống.");
        CHECK_MSG.put("ck_attachment_filename", "Tên file đính kèm không được để trống.");
        CHECK_MSG.put("ck_attachment_filepath", "Đường dẫn file đính kèm không được để trống.");
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
                msg = "Dữ liệu không hợp lệ (vi phạm ràng buộc kiểm tra).";
                for (Map.Entry<String, String> en : CHECK_MSG.entrySet()) {
                    if (raw.contains(en.getKey())) { msg = en.getValue(); break; }
                }
                break;
            case 1062: msg = "Dữ liệu bị trùng với bản ghi đã tồn tại."; break;
            case 1451: msg = "Không thể xóa/sửa vì dữ liệu đang được bản ghi khác sử dụng."; break;
            case 1452: msg = "Dữ liệu tham chiếu không tồn tại (Project/Team/Volunteer/Task không hợp lệ)."; break;
            case 1048: msg = "Thiếu thông tin bắt buộc (có trường không được để trống)."; break;
            case 1406: msg = "Dữ liệu nhập quá dài so với giới hạn của trường."; break;
            case 1292: case 1366: msg = "Giá trị nhập sai định dạng (ngày/số)."; break;
            case 1045: msg = "Sai tên đăng nhập hoặc mật khẩu MySQL (kiểm tra config/db.properties)."; break;
            case 1049: msg = "Chưa có database ngo_task_mgmt. Hãy chạy các file .sql trước."; break;
            default:
                if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
                    msg = "Không kết nối được tới MySQL. Kiểm tra MySQL đã chạy và db.url trong config/db.properties.";
                } else {
                    msg = "Có lỗi cơ sở dữ liệu. Vui lòng thử lại hoặc liên hệ quản trị.";
                }
        }
        return new DataAccessException(msg, e);
    }
}
