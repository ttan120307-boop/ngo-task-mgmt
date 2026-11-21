-- =====================================================================
-- Constraint tests for the Label module (07).
-- HOW TO RUN: run ngo_full_schema_and_data.sql (01, 03, 05, 07) first. Part A must be run with "continue on
-- error" (mysql --force, or Workbench: Query > Continue on SQL Script Errors)
-- because every statement in part A is SUPPOSED to fail.
-- Record the real error message of each test in the report (Test | Statement |
-- Expected | Actual | Screenshot).
-- =====================================================================
USE ngo_task_mgmt;

-- ------------------------- A. statements that must FAIL -------------------------

-- A1  Duplicate label name (UNIQUE).
--     Expected: ERROR 1062 (uq_label_name)
INSERT INTO LABEL (label_name) VALUES ('Urgent');

-- A2  Same name in another letter case (case-insensitive collation).
--     Expected: ERROR 1062 (uq_label_name)
INSERT INTO LABEL (label_name) VALUES ('URGENT');

-- A3  Empty label name (CHECK).
--     Expected: ERROR 3819 (ck_label_name)
INSERT INTO LABEL (label_name) VALUES ('   ');

-- A4  NULL label name (NOT NULL).
--     Expected: ERROR 1048 (Column 'label_name' cannot be null)
INSERT INTO LABEL (label_name) VALUES (NULL);

-- A5  The same label twice on one task (PRIMARY KEY).
--     Expected: ERROR 1062 (PRIMARY)
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (4, 1);

-- A6  Label of a task that does not exist (FK).
--     Expected: ERROR 1452 (fk_tasklabel_task)
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (999, 1);

-- A7  Task with a label that does not exist (FK).
--     Expected: ERROR 1452 (fk_tasklabel_label)
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (1, 999);

-- A8  NULL task or label in a link (primary key part is NOT NULL).
--     Expected: ERROR 1048 for each of the two statements
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (NULL, 1);
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (1, NULL);

-- ------------------------- B. statements that must SUCCEED (rolled back) -------------------------
START TRANSACTION;

-- B1 One label is used by tasks of different projects ('Urgent' = tasks 4, 16, 18).
SELECT COUNT(DISTINCT t.project_id) AS projects_with_urgent
  FROM TASK_LABEL tl JOIN TASK t ON t.task_id = tl.task_id
 WHERE tl.label_id = 1;                                                              -- Expected: 2

-- B2 One task has several labels (task 4 has 3).
SELECT COUNT(*) AS labels_of_task_4 FROM TASK_LABEL WHERE task_id = 4;               -- Expected: 3

-- B3 Deleting a label removes its links but keeps the tasks ('Pending Approval' = tasks 22 and 28).
DELETE FROM LABEL WHERE label_id = 15;
SELECT COUNT(*) AS links_left_for_label_15 FROM TASK_LABEL WHERE label_id = 15;      -- Expected: 0
SELECT COUNT(*) AS tasks_still_there FROM TASK WHERE task_id IN (22, 28);            -- Expected: 2

-- B4 Deleting a task removes its links but keeps the labels.
DELETE FROM TASK WHERE task_id = 9;
SELECT COUNT(*) AS links_left_for_task_9 FROM TASK_LABEL WHERE task_id = 9;          -- Expected: 0
SELECT COUNT(*) AS label_4_still_there FROM LABEL WHERE label_id = 4;           -- Expected: 1

-- B5 ON UPDATE CASCADE: renumber label 2; the links follow.
UPDATE LABEL SET label_id = 200 WHERE label_id = 2;
SELECT COUNT(*) AS links_of_label_200 FROM TASK_LABEL WHERE label_id = 200;          -- Expected: 4

-- B6 A task can have no label (task 30).
SELECT COUNT(*) AS labels_of_task_30 FROM TASK_LABEL WHERE task_id = 30;             -- Expected: 0

ROLLBACK;

-- Check the sample data is unchanged: 15 / 69 rows.
SELECT (SELECT COUNT(*) FROM LABEL) AS labels, (SELECT COUNT(*) FROM TASK_LABEL) AS task_labels;
