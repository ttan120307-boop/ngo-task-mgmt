# NGO Project and Task Management (IT079IU, Topic 6, group 13)

Java Swing + JDBC + MySQL 8.0.16+. Database `ngo_task_mgmt`: 13 tables, constraints and triggers in SQL (not in Java).

## 1. Set up the database
1. Open MySQL Workbench (or any client) and run **`sql/ngo_full_schema_and_data.sql`** once. It drops and recreates the database, so it can be run again at any time.
2. (Optional) tests: `sql/02`, `04`, `06`, `08`, `14` (accounts) (part A fails on purpose: run with "continue on error"), demo DML `sql/10_dml_examples.sql`, queries `sql/11_queries.sql`, index `sql/12_index_explain.sql`. Details: `sql/README_SQL.md`.

## 2. Configure the connection
Copy `config/db.properties.example` to `config/db.properties` and type your MySQL user and password.
`config/db.properties` is in `.gitignore`: **never commit a password**.

## 3. Build and run
```
mvn clean compile
Run class ngo.App from the IDE (Run As > Java Application). It opens the LOG-IN screen first.
```
Another config path: `java -Ddb.config=D:/cfg/db.properties ...`.

## 4. Screens and the five Topic 6 queries
| Query of the assignment | Where in the application |
|---|---|
| Every open task of a project, ordered by due date | Search Tasks: choose the project and the status TODO or IN_PROGRESS (rows are ordered by due date) |
| Open and overdue tasks per volunteer | Report > "By volunteer" |
| Tasks that cannot start (unfinished prerequisite) | Report > "Blocked tasks" |
| Projects where every task is completed | Report > "Completed projects" |
| Volunteer who commented in most projects | Report > "Top commenter" |

Other screens: project / task / membership editing, assignments, comments and attachments, dependencies, labels.
Transactions (COMMIT / ROLLBACK) are in `ngo.db.Tx` and used by `TaskService` and `TaskEditingService`.

## 5. Rules the application relies on (all enforced by the database)
`estimated_hours` and `due_date` of a task are mandatory (BR6); the team of a task with assignees cannot change (BR24);
one current Leader per team (BR17); an assignee must have been a team member on the assigned date (BR8). The code BR1-BR24 is explained in `sql/README_SQL.md`.

## 6. Not verified yet
The application and the SQL have been compiled / checked offline but **not run against a live MySQL server by the author of this patch**: run the tests once and keep the screenshots for the report.

## 6. Log-in and roles (new)

The application now starts at a log-in screen (`ngo.App`). Accounts are in table `ACCOUNT` (SQL module `sql/09`); the role is derived at log-in, not stored.
All 15 active demo accounts use the password **`Ngo@2026`** (change it from the sidebar: *Change password*). Account 16 is disabled on purpose.

| Role | Rule | Demo username |
|---|---|---|
| Admin | Organization Director | `minhanh.nguyen` |
| Manager | owns a project or currently leads a team | `quocbao.tran`, `thuha.le`, ... (run `SELECT username FROM ACCOUNT`) |
| User | any other active volunteer | the remaining usernames |

| Action | User | Manager | Admin |
|---|---|---|---|
| Browse / Search / Projects / Reports (read) | yes | yes | yes |
| Update status of own assigned task, comment (My tasks) | yes | yes | yes |
| Create/edit/delete tasks, assignments, labels, dependencies | no | own projects or led teams | all |
| Create projects; edit/delete a project | no | own projects only | all |
| Join / remove team members | no | teams he leads | all |

Every screen shares the same sidebar, colours and fonts (`ngo.ui.browse.UiStyle`). The "Edit task" and "Manage" menu items are hidden for Users, **and** the DAO/service layer rejects the call (`ngo.auth.Authz`), so a hidden button is not the only protection.
Design details, FD/BCNF of ACCOUNT and limitations: `docs/AUTH_DESIGN.md`.
