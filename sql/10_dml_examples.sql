-- =====================================================================
-- 10_dml_examples.sql  --  INSERT / UPDATE / DELETE for every table, and two transactions
-- (one COMMIT, one ROLLBACK).
-- RUN AFTER ngo_full_schema_and_data.sql. The script is SELF-CLEANING: it works on demo rows
-- with ids >= 1000 and removes them in part D, so the sample data is the same before and
-- after (15 projects, 55 tasks, ...). It can be run several times in a row.
-- Every statement must succeed (no error is expected). All dates are relative to CURRENT_DATE.
-- Run it in autocommit mode (the default); the transactions are explicit.
-- =====================================================================
USE ngo_task_mgmt;

-- ======================= A. INSERT (one demo row per table) =======================
INSERT INTO VOLUNTEER (volunteer_id, full_name, email)
VALUES (1000, 'Demo Volunteer', 'demo.volunteer@ngo-demo.org');
INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES ('0907000001', 1000);

INSERT INTO TEAM (team_id, team_name, description)
VALUES (1000, 'Demo Team', 'Team created for the DML demonstration');

-- The role with id 1 is the Leader (BR17): the demo volunteer leads the demo team.
INSERT INTO MEMBERSHIP (membership_id, volunteer_id, team_id, team_role_id, joined_date)
VALUES (1000, 1000, 1000, 1, CURRENT_DATE);

INSERT INTO PROJECT (project_id, owner_id, project_name, description, start_date, due_date)
VALUES (1000, 1000, 'Demo Project', 'Project for the DML demonstration',
        CURRENT_DATE, CURRENT_DATE + INTERVAL 30 DAY);

INSERT INTO TASK (task_id, project_id, team_id, title, priority, status, estimated_hours, due_date)
VALUES (1000, 1000, 1000, 'Demo task A', 'HIGH', 'TODO', 8.00, CURRENT_DATE + INTERVAL 10 DAY),
       (1001, 1000, 1000, 'Demo task B', 'LOW',  'TODO', 4.00, CURRENT_DATE + INTERVAL 20 DAY);

INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (1001, 1000);

INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id, assigned_date)
VALUES (1000, 1000, CURRENT_DATE);

INSERT INTO TASK_COMMENT (task_id, comment_no, volunteer_id, content)
VALUES (1000, 1, 1000, 'First comment on demo task A');

INSERT INTO ATTACHMENT (task_id, comment_no, attachment_no, file_name, file_path, file_type)
VALUES (1000, 1, 1, 'demo.pdf', '/uploads/tasks/1000/comments/1/demo.pdf', 'application/pdf');

INSERT INTO LABEL (label_id, label_name) VALUES (1000, 'Demo Label');
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (1000, 1000), (1001, 1000);

-- ======================= B. UPDATE (every table) =======================
UPDATE VOLUNTEER       SET full_name = 'Demo Volunteer (renamed)' WHERE volunteer_id = 1000;
UPDATE VOLUNTEER_PHONE SET phone_number = '0907000002'            WHERE phone_number = '0907000001';
UPDATE TEAM            SET description = 'Description changed'    WHERE team_id = 1000;
UPDATE MEMBERSHIP      SET team_role_id = 3                       WHERE membership_id = 1000;  -- Leader -> Member
UPDATE PROJECT         SET due_date = CURRENT_DATE + INTERVAL 45 DAY WHERE project_id = 1000;
UPDATE TASK            SET status = 'DONE'                        WHERE task_id = 1000;        -- no prerequisite
UPDATE TASK            SET status = 'IN_PROGRESS'                 WHERE task_id = 1001;        -- prerequisite 1000 is DONE (BR10)
UPDATE TASK_ASSIGNMENT SET assigned_date = CURRENT_DATE           WHERE volunteer_id = 1000 AND task_id = 1000;
UPDATE TASK_COMMENT    SET content = 'First comment (edited)'     WHERE task_id = 1000 AND comment_no = 1;
UPDATE ATTACHMENT      SET file_name = 'demo-v2.pdf'              WHERE task_id = 1000 AND comment_no = 1 AND attachment_no = 1;
UPDATE LABEL           SET label_name = 'Demo Label (renamed)'    WHERE label_id = 1000;
-- TASK_DEPENDENCY rows are never updated (BR23): delete and insert instead.
DELETE FROM TASK_DEPENDENCY WHERE dependent_task_id = 1001 AND prerequisite_task_id = 1000;
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES (1001, 1000);
-- TASK_LABEL: move a link to another label by delete + insert (both columns are the key).
DELETE FROM TASK_LABEL WHERE task_id = 1001 AND label_id = 1000;
INSERT INTO TASK_LABEL (task_id, label_id) VALUES (1001, 1);     -- label 1 'Urgent'

-- ======================= C. TRANSACTIONS =======================
-- C1 COMMIT: two tasks of the demo project are planned together; either both are saved or none.
START TRANSACTION;
INSERT INTO TASK (task_id, project_id, team_id, title, estimated_hours, due_date)
VALUES (1002, 1000, 1000, 'Demo task C', 6.00, CURRENT_DATE + INTERVAL 25 DAY);
INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id, assigned_date) VALUES (1000, 1002, CURRENT_DATE);
INSERT INTO TASK_COMMENT (task_id, comment_no, volunteer_id, content)
VALUES (1002, 1, 1000, 'Task C planned and assigned in one transaction');
COMMIT;
SELECT COUNT(*) AS tasks_of_demo_project_after_commit FROM TASK WHERE project_id = 1000;      -- Expected: 3

-- C2 ROLLBACK: a wrong bulk delete is undone.
START TRANSACTION;
DELETE FROM TASK WHERE project_id = 1000;
SELECT COUNT(*) AS tasks_inside_transaction FROM TASK WHERE project_id = 1000;                 -- Expected: 0
ROLLBACK;
SELECT COUNT(*) AS tasks_after_rollback FROM TASK WHERE project_id = 1000;                     -- Expected: 3

-- ======================= D. DELETE (every table, children first) =======================
DELETE FROM ATTACHMENT      WHERE task_id = 1000 AND comment_no = 1 AND attachment_no = 1;
DELETE FROM TASK_COMMENT    WHERE task_id = 1000 AND comment_no = 1;
DELETE FROM TASK_LABEL      WHERE task_id IN (1000, 1001);
DELETE FROM LABEL           WHERE label_id = 1000;
DELETE FROM TASK_ASSIGNMENT WHERE volunteer_id = 1000 AND task_id = 1000;
DELETE FROM TASK_DEPENDENCY WHERE dependent_task_id = 1001 AND prerequisite_task_id = 1000;
DELETE FROM TASK            WHERE task_id IN (1000, 1001);
DELETE FROM PROJECT         WHERE project_id = 1000;       -- also removes task 1002 and its assignment / comment (CASCADE)
DELETE FROM MEMBERSHIP      WHERE membership_id = 1000;
DELETE FROM TEAM            WHERE team_id = 1000;
DELETE FROM VOLUNTEER       WHERE volunteer_id = 1000;     -- also removes the phone (CASCADE)

-- Check: the sample data is back to 15 projects / 55 tasks / 45 dependencies / 58 assignments /
-- 21 comments / 19 attachments / 15 labels / 69 task labels.
SELECT (SELECT COUNT(*) FROM PROJECT) AS projects, (SELECT COUNT(*) FROM TASK) AS tasks,
       (SELECT COUNT(*) FROM TASK_DEPENDENCY) AS dependencies, (SELECT COUNT(*) FROM TASK_ASSIGNMENT) AS assignments,
       (SELECT COUNT(*) FROM TASK_COMMENT) AS comments, (SELECT COUNT(*) FROM ATTACHMENT) AS attachments,
       (SELECT COUNT(*) FROM LABEL) AS labels, (SELECT COUNT(*) FROM TASK_LABEL) AS task_labels;
