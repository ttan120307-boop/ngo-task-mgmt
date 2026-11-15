package ngo;

import ngo.dao.ProjectDAO;
import ngo.dao.TaskDAO;
import ngo.db.DataAccessException;
import ngo.model.Task;
import ngo.service.TaskService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Chạy thử CRUD + transaction + báo lỗi thân thiện. Cần đã nạp 01, 03, 05 .sql. */
public class Demo {
    public static void main(String[] args) {
        TaskDAO dao = new TaskDAO();
        TaskService svc = new TaskService();

        title("0. Browse project (Master)");
        for (Object[] r : new ProjectDAO().browseRows()) System.out.println(Arrays.toString(r));

        title("1. CREATE");
        Task t = newTask("Demo CRUD task");
        int id = dao.insert(t);
        System.out.println("Đã tạo: " + dao.findById(id));

        title("2. UPDATE");
        t.setStatus("IN_PROGRESS");
        t.setTitle("Demo CRUD task (đã sửa)");
        System.out.println("Số dòng sửa: " + dao.update(t) + " -> " + dao.findById(id));

        title("3. SEARCH (project 6, từ khoá 'Demo')");
        dao.search(6, "Demo", null, null).forEach(System.out::println);

        title("4. TRANSACTION - COMMIT (volunteer 4 là thành viên hiện tại của team 2)");
        int txId = svc.createTaskWithAssignees(newTask("Demo TX commit"), List.of(4));
        System.out.println("Browse project 6:");
        dao.browseRows(6).stream().filter(r -> (int) r[0] == txId)
                .forEach(r -> System.out.println(Arrays.toString(r)));

        title("5. TRANSACTION - ROLLBACK (volunteer 16 KHÔNG thuộc team 2)");
        try {
            svc.createTaskWithAssignees(newTask("Demo TX rollback"), List.of(4, 16));
        } catch (DataAccessException | IllegalArgumentException e) {
            System.out.println("Thông báo cho người dùng: " + e.getMessage());
        }
        System.out.println("Số task 'Demo TX rollback' còn lại (phải = 0): "
                + dao.search(6, "Demo TX rollback", null, null).size());

        title("6. Lỗi CHECK từ DB (bỏ qua validate Java)");
        try {
            Task bad = newTask("Bad priority");
            bad.setPriority("WRONG");
            dao.insert(bad);
        } catch (DataAccessException e) {
            System.out.println("Thông báo: " + e.getMessage());
        }

        title("7. Lỗi từ TRIGGER (task 20 cần task 19 DONE trước)");
        try {
            Task t20 = dao.findById(20);
            t20.setStatus("DONE");
            dao.update(t20);
        } catch (DataAccessException e) {
            System.out.println("Thông báo: " + e.getMessage());
        }

        title("8. Comment + attachment (weak entity)");
        int cid = svc.addComment(txId, 4, "Đã xong phần đầu.",
                List.of(new TaskService.AttachmentInfo("note.txt", "/uploads/demo/note.txt", "text/plain")));
        System.out.println("Comment mới có comment_id = " + cid);

        title("9. DELETE (dọn dữ liệu demo; CASCADE xoá luôn assignment/comment/attachment)");
        System.out.println("Xoá: " + dao.delete(id) + " + " + dao.delete(txId));
    }

    private static Task newTask(String title) {
        Task t = new Task();
        t.setProjectId(6);
        t.setTeamId(2);
        t.setTitle(title);
        t.setPriority("MEDIUM");
        t.setStatus("TODO");
        t.setEstimatedHours(new BigDecimal("5.5"));
        t.setDueDate(LocalDate.of(2025, 5, 1));
        return t;
    }

    private static void title(String s) { System.out.println("\n=== " + s + " ==="); }
}
