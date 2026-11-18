# NGO Tasks – New Edit Task UI

This is a **small integration patch**, not a replacement of the whole repository. It adds a new Edit Task screen that follows the sidebar/table style shown in the team's approved Projects screenshot. It deliberately does not copy the rejected old `ngo.ui.edit.TaskEditingPanel` UI from the project ZIP.

## Added source files

- `src/main/java/ngo/ui/browse/UiStyle.java` – shared sidebar, color palette, table styling and navigation route for Edit task.
- `src/main/java/ngo/ui/edit/EditTaskForm.java` – new Swing screen for Add / Edit / Delete Task.
- `src/main/java/ngo/dao/TaskAssignmentReadDAO.java` – loads existing assignee names for display.
- `src/main/java/ngo/service/TaskEditingService.java` – transactional task create/update/delete and validation of project deadline / team change.

## Integration

1. Extract this ZIP at the repository root (the folder containing `pom.xml`).
2. Keep the team's existing `TaskDAO`, `Task`, `LookupDAO`, `DBConnection`, `Tx`, `DataAccessException`, SQL scripts, SearchForm and Project forms.
3. Run `mvn clean compile`.
4. Run `ngo.ui.edit.EditTaskForm` from the IDE, or open it from the left navigation menu when using the updated `UiStyle`.

Detailed Vietnamese instructions are in `docs/README_EDIT_TASK_VI.md`.

## Verification

The 4 added Java files were compiled together with all Java source files in the uploaded `ngo-task-mgmt(6).zip` using `javac --release 17` successfully (32 source files in total). The UI has not been launched against the team's live MySQL database in this environment; live verification requires the team's local `config/db.properties` and SQL modules 01, 03 and 05.

The provided Figma link could not be opened by the viewer in this session. The current visual implementation is therefore based on the supplied Projects screenshot and the `UiStyle` raw source included in the pasted code, not a verified inspection of every Figma component.
