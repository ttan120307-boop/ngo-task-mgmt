package ngo.ui.edit;

import ngo.model.Task;

/** Small UI object for selecting a Task in JComboBox. */
final class TaskChoice {
    private final int taskId;
    private final String title;

    TaskChoice(Task task) {
        this.taskId = task.getTaskId();
        this.title = task.getTitle();
    }

    int getTaskId() { return taskId; }

    @Override
    public String toString() {
        return "#" + taskId + " – " + title;
    }
}
