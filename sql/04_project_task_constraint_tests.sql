-- =====================================================================
-- Constraint tests for the Project & Task module.
-- HOW TO RUN: run ngo_full_schema_and_data.sql (or 01, 03, 05, 07) first. Part A must be
-- run with "continue on error" (mysql --force, or Workbench: Query > Continue on SQL
-- script errors) because every statement in part A is SUPPOSED to fail.
-- Failed INSERTs still consume AUTO_INCREMENT values; this is harmless.
-- All dates are relative to CURRENT_DATE, like the sample data, so the expected results
-- are the same on every day.
-- Since rule BR6 makes estimated_hours and due_date NOT NULL, every INSERT INTO TASK below
-- gives both columns, so that the statement fails for the reason named in its comment.
-- Rule codes (BR1 ... BR24) are explained in the table of README.md.
-- Record the real error message of each test in the report (Test | Statement |
-- Expected | Actual | Screenshot).
-- =====================================================================
USE ngo_task_mgmt;

-- ------------------------- A. statements that must FAIL -------------------------

-- ---- PROJECT ----

-- A1  Project owner that is not a volunteer (FK).
--     Expected: ERROR 1452 (fk_project_owner)
INSERT INTO PROJECT (owner_id, project_name, start_date, due_date)
VALUES (999, 'Ghost Project', CURRENT_DATE, CURRENT_DATE + INTERVAL 30 DAY);

-- A2  NULL owner (NOT NULL).
--     Expected: ERROR 1048 (Column 'owner_id' cannot be null)
INSERT INTO PROJECT (owner_id, project_name, start_date, due_date)
VALUES (NULL, 'No Owner', CURRENT_DATE, CURRENT_DATE + INTERVAL 30 DAY);

-- A3  Empty project name (CHECK).
--     Expected: ERROR 3819 (ck_project_name)
INSERT INTO PROJECT (owner_id, project_name, start_date, due_date)
VALUES (1, '   ', CURRENT_DATE, CURRENT_DATE + INTERVAL 30 DAY);

-- A4  Due date before start date (CHECK).
--     Expected: ERROR 3819 (ck_project_dates)
INSERT INTO PROJECT (owner_id, project_name, start_date, due_date)
VALUES (1, 'Backwards Dates', CURRENT_DATE + INTERVAL 60 DAY, CURRENT_DATE + INTERVAL 30 DAY);

-- A5  NULL due date (NOT NULL).
--     Expected: ERROR 1048 (Column 'due_date' cannot be null)
INSERT INTO PROJECT (owner_id, project_name, start_date, due_date)
VALUES (1, 'No Deadline', CURRENT_DATE, NULL);

-- A6  Delete a volunteer who still owns a project (ON DELETE RESTRICT).
--     A temporary volunteer is used so that the memberships of the sample
--     volunteers cannot be the reason for the error.
--     Expected: ERROR 1451 (fk_project_owner)
START TRANSACTION;
INSERT INTO VOLUNTEER (volunteer_id, full_name, email) VALUES (102, 'Temp Owner', 'temp.owner@ngo-demo.org');
INSERT INTO PROJECT (owner_id, project_name, start_date, due_date)
VALUES (102, 'Temp Project', CURRENT_DATE, CURRENT_DATE + INTERVAL 30 DAY);
DELETE FROM VOLUNTEER WHERE volunteer_id = 102;
ROLLBACK;

-- ---- TASK ----

-- A7  Task of a project that does not exist (FK).
--     Expected: ERROR 1452 (fk_task_project)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (999, 1, 'Orphan task', 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A8  Task of a team that does not exist (FK).
--     Expected: ERROR 1452 (fk_task_team)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 999, 'Task without team', 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A8b NULL team (NOT NULL): every task belongs to a team.
--     Expected: ERROR 1048 (Column 'team_id' cannot be null)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, NULL, 'No team', 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A9  Empty task title (CHECK).
--     Expected: ERROR 3819 (ck_task_title)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 1, '   ', 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A9b NULL task title (NOT NULL).
--     Expected: ERROR 1048 (Column 'title' cannot be null)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 1, NULL, 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A10 Priority outside LOW/MEDIUM/HIGH/URGENT (CHECK).
--     Expected: ERROR 3819 (ck_task_priority)
INSERT INTO TASK (project_id, team_id, title, priority, estimated_hours, due_date)
VALUES (1, 1, 'Bad priority', 'CRITICAL', 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A11 Status outside TODO/IN_PROGRESS/DONE/CANCELLED (CHECK).
--     Expected: ERROR 3819 (ck_task_status)
INSERT INTO TASK (project_id, team_id, title, status, estimated_hours, due_date)
VALUES (1, 1, 'Bad status', 'FINISHED', 8, CURRENT_DATE + INTERVAL 10 DAY);

-- A12 Estimate that is zero or negative (CHECK).
--     Expected: ERROR 3819 (ck_task_hours) for each of the two statements
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 1, 'Zero hours', 0, CURRENT_DATE + INTERVAL 10 DAY);
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 1, 'Negative hours', -5, CURRENT_DATE + INTERVAL 10 DAY);

-- A12b NULL estimate (rule BR6: estimated_hours is mandatory).
--      Expected: ERROR 1048 (Column 'estimated_hours' cannot be null)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 1, 'No estimate', NULL, CURRENT_DATE + INTERVAL 10 DAY);

-- A12c NULL due date (rule BR6: due_date is mandatory, so BR11 can always be checked).
--      Expected: ERROR 1048 (Column 'due_date' cannot be null)
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 1, 'No due date', 8, NULL);

-- A12d Estimate and due date left out (no default value).
--      Expected: ERROR 1364 (Field 'estimated_hours' doesn't have a default value)
INSERT INTO TASK (project_id, team_id, title) VALUES (1, 1, 'Nothing planned');

-- A13 UPDATE a foreign key to a team that does not exist.
--     Expected: ERROR 1452 (fk_task_team); task 1 keeps team 4
UPDATE TASK SET team_id = 999 WHERE task_id = 1;

-- ---- TASK_DEPENDENCY: foreign keys and primary key ----

-- A14 Same dependency twice (PRIMARY KEY).
--     Expected: ERROR 1062 (PRIMARY)
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (2, 1);

-- A15 Dependency of a task that does not exist, in either column (FK).
--     Expected: ERROR 1452 for each of the two statements
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (999, 1);
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (1, 999);

-- ---- TASK_DEPENDENCY: trigger rules ----

-- A16 A task depending on itself (rule BR19).
--     Expected: ERROR 1644 ('A task cannot depend on itself')
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (5, 5);

-- A17 Dependency between tasks of different projects (rule BR9).
--     Task 21 is in project 5, task 1 in project 1.
--     Expected: ERROR 1644 ('Dependent and prerequisite task must belong to the same project')
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (21, 1);

-- A18 Direct cycle (rule BR20): task 2 already needs task 1, so task 1 cannot need task 2.
--     Expected: ERROR 1644 ('This dependency would create a circular dependency')
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (1, 2);

-- A19 Indirect cycle (rule BR20): chain 1 <- 2 <- 3 <- 4 <- 5, so task 1 cannot need task 5.
--     Expected: ERROR 1644 ('This dependency would create a circular dependency')
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (1, 5);

-- A20 A started task gets an unfinished prerequisite (rule BR10).
--     Task 26 is IN_PROGRESS; task 30 is CANCELLED (not DONE).
--     Expected: ERROR 1644 ('A task that has already started cannot get an unfinished prerequisite')
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (26, 30);

-- A21 UPDATE of a dependency row (rule BR23).
--     Expected: ERROR 1644 ('A dependency cannot be updated; delete it and insert a new one')
UPDATE TASK_DEPENDENCY SET prerequisite_task_id = 3 WHERE dependent_task_id = 5 AND prerequisite_task_id = 4;

-- A22 Start a task whose prerequisite is not completed (rule BR10).
--     Task 5 needs task 4, which is still IN_PROGRESS.
--     Expected: ERROR 1644 ('A task cannot start until all its prerequisite tasks are completed')
UPDATE TASK SET status = 'IN_PROGRESS' WHERE task_id = 5;

-- A23 Finish a task whose prerequisite is not completed (rule BR10).
--     Task 23 needs tasks 21 (DONE) and 22 (IN_PROGRESS).
--     Expected: ERROR 1644 ('A task cannot start until all its prerequisite tasks are completed')
UPDATE TASK SET status = 'DONE' WHERE task_id = 23;

-- A24 Re-open a task whose dependent task has already started (rule BR21).
--     Task 3 is DONE and task 4 (IN_PROGRESS) needs it.
--     Expected: ERROR 1644 ('A task cannot be re-opened while a dependent task has already started')
UPDATE TASK SET status = 'TODO' WHERE task_id = 3;

-- A25 Move a task that has dependencies to another project (rule BR22).
--     Expected: ERROR 1644 ('A task that has dependencies cannot move to another project')
UPDATE TASK SET project_id = 2 WHERE task_id = 2;

-- ---- Rule BR11: task due date <= project due date ----

-- A26 INSERT a task that is due after its project (project 1 is due in 60 days).
--     Expected: ERROR 1644 ('Task due date is after the project due date')
INSERT INTO TASK (project_id, team_id, title, estimated_hours, due_date)
VALUES (1, 4, 'Late task', 8, CURRENT_DATE + INTERVAL 90 DAY);

-- A27 UPDATE the due date of a task beyond its project.
--     Expected: ERROR 1644 ('Task due date is after the project due date')
UPDATE TASK SET due_date = CURRENT_DATE + INTERVAL 61 DAY WHERE task_id = 1;

-- A28 Shorten a project below the due date of one of its tasks (task 4 is due 20 days ago,
--     the new project due date is 100 days ago).
--     Expected: ERROR 1644 ('A task of this project is due after the new project due date')
UPDATE PROJECT SET due_date = CURRENT_DATE - INTERVAL 100 DAY WHERE project_id = 1;

-- ------------------------- B. statements that must SUCCEED (rolled back) -------------------------
START TRANSACTION;

-- B1 Deleting a project deletes its tasks and, through them, their dependencies (ON DELETE CASCADE).
DELETE FROM PROJECT WHERE project_id = 2;
SELECT (SELECT COUNT(*) FROM TASK WHERE project_id = 2) AS tasks_of_project_2,
       (SELECT COUNT(*) FROM TASK_DEPENDENCY
         WHERE dependent_task_id BETWEEN 6 AND 11
            OR prerequisite_task_id BETWEEN 6 AND 11) AS dependencies_of_project_2;   -- Expected: 0, 0

-- B2 A new task can be added and made to wait for an existing one.
INSERT INTO TASK (task_id, project_id, team_id, title, estimated_hours, due_date)
VALUES (100, 6, 4, 'Evaluate the health camp', 10, CURRENT_DATE + INTERVAL 90 DAY);
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (100, 29);
SELECT prerequisite_task_id FROM TASK_DEPENDENCY WHERE dependent_task_id = 100;      -- Expected: 29

-- B3 A task can start once its prerequisite is DONE (finish task 4, then start task 5).
UPDATE TASK SET status = 'DONE' WHERE task_id = 4;
UPDATE TASK SET status = 'IN_PROGRESS' WHERE task_id = 5;
SELECT task_id, status FROM TASK WHERE task_id IN (4, 5) ORDER BY task_id;           -- Expected: 4 DONE, 5 IN_PROGRESS

-- B4 Defaults: priority = 'MEDIUM' and status = 'TODO' when not given.
INSERT INTO TASK (task_id, project_id, team_id, title, estimated_hours, due_date)
VALUES (101, 1, 4, 'Default test', 4, CURRENT_DATE + INTERVAL 10 DAY);
SELECT priority, status FROM TASK WHERE task_id = 101;                               -- Expected: MEDIUM, TODO

-- B5 A task may have several prerequisites, also a redundant (indirect) one:
--    task 25 already waits for 23 and 24; 21 is an older ancestor, not a cycle.
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (25, 21);
SELECT COUNT(*) AS prerequisites_of_task_25 FROM TASK_DEPENDENCY WHERE dependent_task_id = 25;  -- Expected: 3

-- B6 ON UPDATE CASCADE: renumber task 26; the dependency rows follow.
UPDATE TASK SET task_id = 200 WHERE task_id = 26;
SELECT COUNT(*) AS dependents_of_task_200 FROM TASK_DEPENDENCY WHERE prerequisite_task_id = 200;  -- Expected: 2

-- B7 Deleting a task removes its dependency rows on both sides (task 23 has 4).
DELETE FROM TASK WHERE task_id = 23;
SELECT COUNT(*) AS dependencies_left_for_task_23 FROM TASK_DEPENDENCY
 WHERE dependent_task_id = 23 OR prerequisite_task_id = 23;                          -- Expected: 0

-- B8 A project may be created with the default start date (today).
INSERT INTO PROJECT (project_id, owner_id, project_name, due_date)
VALUES (100, 1, 'Default Dates', DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY));
SELECT start_date = CURRENT_DATE AS starts_today FROM PROJECT WHERE project_id = 100;  -- Expected: 1

-- B9 A task may be due exactly on the project due date (the date of project 6 is read from the table).
INSERT INTO TASK (task_id, project_id, team_id, title, estimated_hours, due_date)
VALUES (102, 6, 4, 'Last-day task', 5, (SELECT due_date FROM PROJECT WHERE project_id = 6));
SELECT t.due_date = p.due_date AS same_as_project FROM TASK t JOIN PROJECT p ON p.project_id = t.project_id
 WHERE t.task_id = 102;                                                              -- Expected: 1

-- B10 A project without tasks can be shortened (project 7 has no task).
UPDATE PROJECT SET due_date = CURRENT_DATE + INTERVAL 90 DAY WHERE project_id = 7;
SELECT due_date = CURRENT_DATE + INTERVAL 90 DAY AS shortened FROM PROJECT WHERE project_id = 7;  -- Expected: 1

-- B11 Editing a started task never trips rule BR10 (status is unchanged).
UPDATE TASK SET description = 'Edited while in progress' WHERE task_id = 4;
SELECT description FROM TASK WHERE task_id = 4;                                      -- Expected: Edited while in progress

-- B12 One volunteer owns several projects (volunteer 8 owns projects 1, 7 and 13).
SELECT owner_id, COUNT(*) AS projects_owned FROM PROJECT WHERE owner_id = 8 GROUP BY owner_id;  -- Expected: 8, 3

ROLLBACK;

-- Check the sample data is unchanged: 15 / 55 / 45 rows.
SELECT (SELECT COUNT(*) FROM PROJECT) AS projects, (SELECT COUNT(*) FROM TASK) AS tasks,
       (SELECT COUNT(*) FROM TASK_DEPENDENCY) AS dependencies;
