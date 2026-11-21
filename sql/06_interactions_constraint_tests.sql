-- =====================================================================
-- Constraint tests for the interaction module. Run ngo_full_schema_and_data.sql first.
-- All dates are relative to CURRENT_DATE, like the sample data.
-- Part A MUST FAIL: run each statement individually and capture its error.
-- Do NOT run the whole file without 'Continue on SQL Script Errors'.
-- Part B MUST SUCCEED: transaction rolls back all changes.
-- =====================================================================
USE ngo_task_mgmt;

-- A. EXPECTED FAILURES (run INDIVIDUALLY)
-- A1 Duplicate assignment PK. Expected ERROR 1062.
INSERT INTO TASK_ASSIGNMENT(volunteer_id, task_id) VALUES (8,1);

-- A2 Assignment for missing volunteer. Expected ERROR 1452 (FK).
--    (the trigger skips unknown volunteers, so the foreign key answers first-hand)
INSERT INTO TASK_ASSIGNMENT(volunteer_id, task_id) VALUES (999,1);

-- A3 Assignment for missing task. Expected ERROR 1452 (FK).
INSERT INTO TASK_ASSIGNMENT(volunteer_id, task_id) VALUES (8,999);

-- A4 Rule BR8: volunteer 2 is not a member of task 1's team 4.
-- Expected ERROR 1644.
INSERT INTO TASK_ASSIGNMENT(volunteer_id, task_id) VALUES (2,1);

-- A5 Rule BR8 also applies on UPDATE; volunteer 2 is not in team 4.
-- Expected ERROR 1644.
UPDATE TASK_ASSIGNMENT SET volunteer_id = 2 WHERE volunteer_id = 8 AND task_id = 1;

-- A6 Duplicate comment composite PK. Expected ERROR 1062.
INSERT INTO TASK_COMMENT(task_id,comment_no,volunteer_id,content)
VALUES (1,1,8,'Duplicate comment ID');

-- A7 Comment without task. Expected ERROR 1452.
INSERT INTO TASK_COMMENT(task_id,comment_no,volunteer_id,content)
VALUES (999,1,8,'No task exists');

-- A8 Comment with missing author. Expected ERROR 1452.
INSERT INTO TASK_COMMENT(task_id,comment_no,volunteer_id,content)
VALUES (1,99,999,'No author exists');

-- A9 Empty comment content. Expected ERROR 3819.
INSERT INTO TASK_COMMENT(task_id,comment_no,volunteer_id,content)
VALUES (1,99,8,'   ');

-- A10 Duplicate attachment composite PK. Expected ERROR 1062.
INSERT INTO ATTACHMENT(task_id,comment_no,attachment_no,file_name,file_path)
VALUES (1,1,1,'duplicate.pdf','/tmp/duplicate.pdf');

-- A11 Attachment without existing (task_id,comment_no) parent.
-- Task 1 comment 99 doesn't exist. Expected ERROR 1452.
INSERT INTO ATTACHMENT(task_id,comment_no,attachment_no,file_name,file_path)
VALUES (1,99,1,'orphan.pdf','/tmp/orphan.pdf');

-- A12 Composite FK protects against mismatched task/comment identifiers.
-- (Task 2, comment 2 does not exist although task 1 comment 2 does.)
-- Expected ERROR 1452.
INSERT INTO ATTACHMENT(task_id,comment_no,attachment_no,file_name,file_path)
VALUES (2,2,1,'mismatch.pdf','/tmp/mismatch.pdf');

-- A13 Blank file name. Expected ERROR 3819.
INSERT INTO ATTACHMENT(task_id,comment_no,attachment_no,file_name,file_path)
VALUES (1,1,90,'   ','/tmp/blank.pdf');

-- A14 Rule BR8 (date part): volunteer 4 joined team 11 on 2024-04-01, so he cannot
-- be assigned to task 17 (team 11) on a date before that. Expected ERROR 1644.
INSERT INTO TASK_ASSIGNMENT(volunteer_id, task_id, assigned_date) VALUES (4,17,'2024-01-01');

-- A14b Rule BR8 (interval): volunteer 5 left team 2 on 2024-05-31, so he cannot be given
-- a task of team 2 (task 3) AFTER that date, e.g. today. Expected ERROR 1644.
INSERT INTO TASK_ASSIGNMENT(volunteer_id, task_id, assigned_date) VALUES (5,3,CURRENT_DATE);

-- A15 Rule BR8 (date part) on UPDATE: volunteer 8 joined team 4 on 2023-07-01.
-- Expected ERROR 1644.
UPDATE TASK_ASSIGNMENT SET assigned_date = '2020-01-01' WHERE volunteer_id = 8 AND task_id = 1;

-- A16 Rule BR24: task 1 already has assignments, so its team cannot change.
-- Expected ERROR 1644.
UPDATE TASK SET team_id = 5 WHERE task_id = 1;

-- A17 A new team that does not exist is still reported by the foreign key.
-- Expected ERROR 1452.
UPDATE TASK SET team_id = 999 WHERE task_id = 1;

-- A18 Delete an author who still has comments (ON DELETE RESTRICT).
-- A temporary volunteer is used. Expected ERROR 1451 (fk_comment_author).
START TRANSACTION;
INSERT INTO VOLUNTEER (volunteer_id, full_name, email) VALUES (103, 'Temp Author', 'temp.author@ngo-demo.org');
INSERT INTO TASK_COMMENT(task_id,comment_no,volunteer_id,content) VALUES (1,50,103,'Temporary comment');
DELETE FROM VOLUNTEER WHERE volunteer_id = 103;
ROLLBACK;

-- B. EXPECTED SUCCESSES (all rolled back)
START TRANSACTION;

-- B1 Same local comment_no can be reused under DIFFERENT tasks.
INSERT INTO TASK_COMMENT(task_id,comment_no,volunteer_id,content)
VALUES (2,2,1,'Second comment on task 2');
SELECT task_id, comment_no FROM TASK_COMMENT
WHERE comment_no=2 AND task_id IN (1,2) ORDER BY task_id;
-- Expected: (1,2), (2,2).

-- B2 Same local attachment_no can be reused under DIFFERENT comments.
INSERT INTO ATTACHMENT(task_id,comment_no,attachment_no,file_name,file_path)
VALUES (1,2,1,'review.txt','/uploads/review.txt');
SELECT task_id,comment_no,attachment_no FROM ATTACHMENT
WHERE task_id=1 AND attachment_no=1 ORDER BY comment_no;
-- Expected: (1,1,1), (1,2,1).

-- B3 Delete a comment -> attachments disappear; the parent task remains.
DELETE FROM TASK_COMMENT WHERE task_id=1 AND comment_no=1;
SELECT COUNT(*) AS attachments_after_comment_delete FROM ATTACHMENT
WHERE task_id=1 AND comment_no=1; -- Expected 0
SELECT COUNT(*) AS task_still_exists FROM TASK WHERE task_id=1; -- Expected 1

-- B4 Delete task 30 -> comment and attachment and assignment disappear.
-- Task 30 has no dependency and is safe to delete for this test.
DELETE FROM TASK WHERE task_id=30;
SELECT COUNT(*) AS assignments_left FROM TASK_ASSIGNMENT WHERE task_id=30; -- 0
SELECT COUNT(*) AS comments_left FROM TASK_COMMENT WHERE task_id=30; -- 0
SELECT COUNT(*) AS attachments_left FROM ATTACHMENT WHERE task_id=30; -- 0

-- B5 The audit view finds no assignment outside the team membership (BR8, same condition as the trigger).
SELECT COUNT(*) AS assignments_outside_membership FROM v_assignment_outside_membership; -- Expected 0

-- B6 ON UPDATE CASCADE: renumber volunteer 8; his assignments and comments follow.
--    Volunteer 8 has 6 assignments (tasks 1, 6, 26, 47, 48, 50) and 3 comments in the sample data
--    ((1,1), (26,1), (45,1)); B3 above deleted comment (1,1) inside this transaction,
--    so 2 comments remain.
UPDATE VOLUNTEER SET volunteer_id = 500 WHERE volunteer_id = 8;
SELECT COUNT(*) AS assignments_of_500 FROM TASK_ASSIGNMENT WHERE volunteer_id = 500; -- Expected 6
SELECT COUNT(*) AS comments_of_500 FROM TASK_COMMENT WHERE volunteer_id = 500;       -- Expected 2

-- B7 Changing the team of a task WITHOUT assignments is allowed. Every sample task has
-- an assignment, so a fresh task is created to show the allowed case.
INSERT INTO TASK (task_id, project_id, team_id, title, estimated_hours, due_date)
VALUES (104, 6, 4, 'Team change demo', 4, CURRENT_DATE + INTERVAL 30 DAY);
UPDATE TASK SET team_id = 3 WHERE task_id = 104;
SELECT team_id FROM TASK WHERE task_id = 104;                                        -- Expected 3

ROLLBACK;

-- Original sample counts must remain 58 assignments, 21 comments, 19 attachments.
SELECT (SELECT COUNT(*) FROM TASK_ASSIGNMENT) AS assignments,
       (SELECT COUNT(*) FROM TASK_COMMENT) AS comments,
       (SELECT COUNT(*) FROM ATTACHMENT) AS attachments;
