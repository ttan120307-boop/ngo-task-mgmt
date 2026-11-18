package ngo.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Maps to the TASK table. */
public class Task {
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE", "CANCELLED");

    private int taskId;
    private int projectId;
    private int teamId;
    private String title;
    private String description;          // nullable
    private String priority = "MEDIUM";
    private String status = "TODO";
    private BigDecimal estimatedHours;   // nullable
    private LocalDate dueDate;           // nullable

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

    /** Java-side validation (catches errors early before touching the DB). Returns null if valid. */
    public String validate() {
        if (title == null || title.isBlank()) return "Task title cannot be empty.";
        if (title.length() > 150) return "Task title must be at most 150 characters.";
        if (projectId <= 0) return "Please select a Project.";
        if (teamId <= 0) return "Please select an assigned Team.";
        if (!PRIORITIES.contains(priority)) return "Priority must be LOW, MEDIUM, HIGH, or URGENT.";
        if (!STATUSES.contains(status)) return "Status must be TODO, IN_PROGRESS, DONE, or CANCELLED.";
        if (estimatedHours != null && estimatedHours.signum() <= 0) return "Estimated hours must be greater than 0.";
        return null;
    }

    @Override
    public String toString() {
        return "Task#" + taskId + " [" + title + " | " + priority + " | " + status + "]";
    }
}
