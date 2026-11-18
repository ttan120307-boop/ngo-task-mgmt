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

/** Test run for CRUD + transactions + user-friendly error messages. Requires 01, 03, and 05 .sql scripts to be loaded first. */
public class Demo {
    public static void main(String[] args) {
        TaskDAO dao = new TaskDAO();
        TaskService svc = new TaskService();

        title("0. Browse project (Master)");
        for (Object[] r : new ProjectDAO().browseRows()) System.out.println(Arrays.toString(r));

        title("1. CREATE");
        Task t = newTask("Demo CRUD task");
        int id = dao.insert(t);
        System.out.println("Created: " + dao.findById(id));

        title("2. UPDATE");
        t.setStatus("IN_PROGRESS");
        t.setTitle("Demo CRUD task (updated)");
        System.out.println("Rows updated: " + dao.update(t) + " -> " + dao.findById(id));

        title("3. SEARCH (project 6, keyword 'Demo')");
        dao.search(6, "Demo", null, null).forEach(System.out::println);

        title("4. TRANSACTION - COMMIT (volunteer 4 is a current member of team 2)");
        int txId = svc.createTaskWithAssignees(newTask("Demo TX commit"), List.of(4));
        System.out.println("Browse project 6:");
        dao.browseRows(6).stream().filter(r -> (int) r[0] == txId).forEach(r -> System.out.println(Arrays.toString(r)));

        title("5. TRANSACTION - ROLLBACK (volunteer 16 is NOT a member of team 2)");
        try {
            svc.createTaskWithAssignees(newTask("Demo TX rollback"), List.of(4, 16));
        } catch (DataAccessException | IllegalArgumentException e) {
            System.out.println("User message: " + e.getMessage());
        }
        System.out.println("Remaining 'Demo TX rollback' tasks (must be 0): "
                + dao.search(6, "Demo TX rollback", null, null).size());

        title("6. CHECK constraint error from DB (bypassing Java validation)");
        try {
            Task bad = newTask("Bad priority");
            bad.setPriority("WRONG");
            dao.insert(bad);
        } catch (DataAccessException e) {
            System.out.println("Message: " + e.getMessage());
        }

        title("7. TRIGGER error (task 20 requires task 19 to be DONE first)");
        try {
            Task t20 = dao.findById(20);
            t20.setStatus("DONE");
            dao.update(t20);
        } catch (DataAccessException e) {
            System.out.println("Message: " + e.getMessage());
        }

        // Cleanup demo records
        dao.delete(id);
        dao.delete(txId);
    }

    private static Task newTask(String title) {
        Task t = new Task();
        t.setProjectId(6);
        t.setTeamId(2);
        t.setTitle(title);
        t.setDescription("Created by Demo");
        t.setPriority("HIGH");
        t.setStatus("TODO");
        t.setEstimatedHours(new BigDecimal("4.50"));
        t.setDueDate(LocalDate.now().plusDays(14));
        return t;
    }

    private static void title(String s) {
        System.out.println("\n=== " + s + " ===");
    }
}
