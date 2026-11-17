package ngo.model;

import java.time.LocalDate;

/** Ánh xạ bảng PROJECT. */
public class Project {
    private int projectId;
    private int ownerId;                 // volunteer_id của người phụ trách
    private String projectName;
    private String description;          // có thể null
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

    /** Validate phía Java. Trả về null nếu hợp lệ. */
    public String validate() {
        if (projectName == null || projectName.isBlank()) return "Tên project không được để trống.";
        if (projectName.length() > 150) return "Tên project tối đa 150 ký tự.";
        if (description != null && description.length() > 500) return "Mô tả tối đa 500 ký tự.";
        if (ownerId <= 0) return "Phải chọn người phụ trách project.";
        if (startDate == null) return "Phải nhập ngày bắt đầu.";
        if (dueDate == null) return "Phải nhập ngày hết hạn.";
        if (dueDate.isBefore(startDate)) return "Ngày hết hạn phải sau hoặc bằng ngày bắt đầu.";
        return null;
    }

    @Override
    public String toString() { return "Project#" + projectId + " [" + projectName + "]"; }
}
