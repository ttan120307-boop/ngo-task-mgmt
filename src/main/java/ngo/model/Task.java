package ngo.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Ánh xạ bảng TASK. */
public class Task {
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE", "CANCELLED");

    private int taskId;
    private int projectId;
    private int teamId;
    private String title;
    private String description;          // có thể null
    private String priority = "MEDIUM";
    private String status = "TODO";
    private BigDecimal estimatedHours;   // có thể null
    private LocalDate dueDate;           // có thể null

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }
    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }
    public int getTeamId() { return teamId; }
    public void setTeamId(int teamId) { this.teamId = teamId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    /** Validate phía Java (bắt lỗi sớm, trước khi chạm DB). Trả về null nếu hợp lệ. */
    public String validate() {
        if (title == null || title.isBlank()) return "Tiêu đề task không được để trống.";
        if (title.length() > 150) return "Tiêu đề tối đa 150 ký tự.";
        if (projectId <= 0) return "Phải chọn Project.";
        if (teamId <= 0) return "Phải chọn Team phụ trách.";
        if (!PRIORITIES.contains(priority)) return "Priority phải là LOW, MEDIUM, HIGH hoặc URGENT.";
        if (!STATUSES.contains(status)) return "Status phải là TODO, IN_PROGRESS, DONE hoặc CANCELLED.";
        if (estimatedHours != null && estimatedHours.signum() <= 0) return "Số giờ ước tính phải lớn hơn 0.";
        return null;
    }

    @Override
    public String toString() {
        return "Task#" + taskId + " [" + title + " | " + priority + " | " + status + "]";
    }
}
