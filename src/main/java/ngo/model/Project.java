package ngo.model;

import java.time.LocalDate;

/** Maps to the PROJECT table. */
public class Project {
    private int projectId;
    private int ownerId;                 // volunteer_id of the project owner
    private String projectName;
    private String description;          // nullable
    private LocalDate startDate;
    private LocalDate dueDate;

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }
    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    /** Java-side validation. Returns null if valid. */
    public String validate() {
        if (projectName == null || projectName.isBlank()) return "Project name cannot be empty.";
        if (projectName.length() > 150) return "Project name must be at most 150 characters.";
        if (description != null && description.length() > 500) return "Description must be at most 500 characters.";
        if (ownerId <= 0) return "Please select a project owner.";
        if (startDate == null) return "Start date is required.";
        if (dueDate == null) return "Due date is required.";
        if (dueDate.isBefore(startDate)) return "Due date must be on or after the start date.";
        return null;
    }

    @Override
    public String toString() { return "Project#" + projectId + " [" + projectName + "]"; }
}
