package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.TaskDAO;
import ngo.model.Task;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Project-level Task summary built from real TaskDAO and LookupDAO results. */
public class TaskReportPanel extends JPanel {
    private final TaskDAO taskDAO = new TaskDAO();
    private final LookupDAO lookupDAO = new LookupDAO();

    private final JButton refreshButton = new JButton("Generate report");
    private final JLabel projectCount = new JLabel("Projects: 0");
    private final JLabel taskCount = new JLabel("Tasks: 0");
    private final JLabel openCount = new JLabel("Open: 0");
    private final JLabel doneCount = new JLabel("Done: 0");
    private final JLabel overdueCount = new JLabel("Overdue: 0");
    private final JLabel cancelledCount = new JLabel("Cancelled: 0");

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Project ID", "Project", "Total", "Open", "Done", "Overdue", "Cancelled"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public TaskReportPanel() {
        buildUi();
        refreshButton.addActionListener(e -> generateReport());
        generateReport();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refreshButton);
        top.add(new JLabel("Counts are calculated from current records returned by TaskDAO.search()."));
        add(top, BorderLayout.NORTH);

        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(23);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel totals = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        totals.setBorder(BorderFactory.createTitledBorder("Organization summary"));
        totals.add(projectCount);
        totals.add(taskCount);
        totals.add(openCount);
        totals.add(doneCount);
        totals.add(overdueCount);
        totals.add(cancelledCount);
        add(totals, BorderLayout.SOUTH);
    }

    private void generateReport() {
        refreshButton.setEnabled(false);
        UiSupport.runAsync(this, "tạo báo cáo Task", () -> {
            return new ReportData(lookupDAO.projects(), taskDAO.search(null, null, null, null));
        }, data -> render(data), () -> refreshButton.setEnabled(true));
    }

    private void render(ReportData data) {
        Map<Integer, ProjectStats> statsByProject = new LinkedHashMap<>();
        for (LookupDAO.ComboItem p : data.projects) {
            statsByProject.put(p.getId(), new ProjectStats(p.getId(), p.getLabel()));
        }
        LocalDate today = LocalDate.now();
        for (Task task : data.tasks) {
            ProjectStats stats = statsByProject.get(task.getProjectId());
            if (stats == null) continue;
            stats.total++;
            String status = task.getStatus();
            if ("DONE".equals(status)) {
                stats.done++;
            } else if ("CANCELLED".equals(status)) {
                stats.cancelled++;
            } else {
                stats.open++;
                if (task.getDueDate() != null && task.getDueDate().isBefore(today)) stats.overdue++;
            }
        }

        model.setRowCount(0);
        int total = 0, open = 0, done = 0, overdue = 0, cancelled = 0;
        for (ProjectStats s : statsByProject.values()) {
            model.addRow(new Object[]{s.projectId, s.projectName, s.total, s.open, s.done, s.overdue, s.cancelled});
            total += s.total;
            open += s.open;
            done += s.done;
            overdue += s.overdue;
            cancelled += s.cancelled;
        }
        projectCount.setText("Projects: " + statsByProject.size());
        taskCount.setText("Tasks: " + total);
        openCount.setText("Open: " + open);
        doneCount.setText("Done: " + done);
        overdueCount.setText("Overdue: " + overdue);
        cancelledCount.setText("Cancelled: " + cancelled);
    }

    private static final class ReportData {
        final List<LookupDAO.ComboItem> projects;
        final List<Task> tasks;
        ReportData(List<LookupDAO.ComboItem> projects, List<Task> tasks) {
            this.projects = projects;
            this.tasks = tasks;
        }
    }

    private static final class ProjectStats {
        final int projectId;
        final String projectName;
        int total, open, done, overdue, cancelled;
        ProjectStats(int projectId, String projectName) {
            this.projectId = projectId;
            this.projectName = projectName;
        }
    }
}
