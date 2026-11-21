-- =====================================================================
-- IT079IU - Topic 6 / Member 3: Interactions & Weak Entities
-- RUN ORDER: 01_foundation_schema_and_data.sql
--            03_project_task_schema_and_data.sql
--            05_interactions_schema_and_data.sql  (this file)
--            07_label_schema_and_data.sql
-- Requires MySQL 8.0.16+ and the Module 1 & Module 2 sample data.
-- No DROP DATABASE or DROP parent tables in this module.
-- If module 03 is run again, it drops these tables: run this file again afterwards.
-- =====================================================================
USE ngo_task_mgmt;

-- Re-running THIS module only: remove child first, then parent.
DROP VIEW IF EXISTS v_assignment_outside_membership;
DROP PROCEDURE IF EXISTS sp_check_assignment;
DROP TABLE IF EXISTS ATTACHMENT;
DROP TABLE IF EXISTS TASK_COMMENT;
DROP TABLE IF EXISTS TASK_ASSIGNMENT;

-- Many-to-many: each volunteer can receive many tasks and vice versa.
CREATE TABLE TASK_ASSIGNMENT (
    volunteer_id  INT UNSIGNED NOT NULL,
    task_id       INT UNSIGNED NOT NULL,
    assigned_date DATE NOT NULL DEFAULT (CURRENT_DATE),
    CONSTRAINT pk_task_assignment PRIMARY KEY (volunteer_id, task_id),
    CONSTRAINT fk_assignment_volunteer FOREIGN KEY (volunteer_id)
        REFERENCES VOLUNTEER(volunteer_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_assignment_task FOREIGN KEY (task_id)
        REFERENCES TASK(task_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_assignment_task ON TASK_ASSIGNMENT(task_id);

-- Weak entity: comment_no is a LOCAL sequence number within task_id.
-- The author is mandatory, and cannot be deleted while comments exist.
CREATE TABLE TASK_COMMENT (
    task_id      INT UNSIGNED NOT NULL,
    comment_no   INT UNSIGNED NOT NULL,
    volunteer_id INT UNSIGNED NOT NULL,
    content      TEXT NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_task_comment PRIMARY KEY (task_id, comment_no),
    CONSTRAINT fk_comment_task FOREIGN KEY (task_id)
        REFERENCES TASK(task_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (volunteer_id)
        REFERENCES VOLUNTEER(volunteer_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_comment_not_empty CHECK (CHAR_LENGTH(TRIM(content)) > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_comment_volunteer ON TASK_COMMENT(volunteer_id);

-- Nested weak entity: each attachment belongs to one specific comment.
CREATE TABLE ATTACHMENT (
    task_id       INT UNSIGNED NOT NULL,
    comment_no    INT UNSIGNED NOT NULL,
    attachment_no INT UNSIGNED NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    file_type     VARCHAR(100) NULL,
    uploaded_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_attachment PRIMARY KEY (task_id, comment_no, attachment_no),
    CONSTRAINT fk_attachment_comment FOREIGN KEY (task_id, comment_no)
        REFERENCES TASK_COMMENT(task_id, comment_no)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_attachment_filename CHECK (CHAR_LENGTH(TRIM(file_name)) > 0),
    CONSTRAINT ck_attachment_filepath CHECK (CHAR_LENGTH(TRIM(file_path)) > 0)
) ENGINE=InnoDB;

-- BR8 (business rule 8), ONE definition used by the triggers AND the audit view:
--   a task of a team can only be given to a volunteer who was a member of that team
--   ON THE ASSIGNED DATE, i.e. joined_date <= assigned_date AND
--   (left_date IS NULL OR left_date >= assigned_date).
-- The check lives in one procedure that both triggers call. The view
-- v_assignment_outside_membership below tests exactly the same condition.
-- An unknown volunteer or task is left to the foreign keys (error 1452), so
-- the trigger only judges rows whose references exist.
DELIMITER //
CREATE PROCEDURE sp_check_assignment(
    IN p_volunteer_id INT UNSIGNED,
    IN p_task_id      INT UNSIGNED,
    IN p_assigned     DATE)
BEGIN
    IF EXISTS (SELECT 1 FROM VOLUNTEER WHERE volunteer_id = p_volunteer_id)
       AND EXISTS (SELECT 1 FROM TASK WHERE task_id = p_task_id)
       AND NOT EXISTS (
           SELECT 1 FROM TASK t
           JOIN MEMBERSHIP m ON m.team_id = t.team_id
           WHERE t.task_id = p_task_id
             AND m.volunteer_id = p_volunteer_id
             AND m.joined_date <= p_assigned
             AND (m.left_date IS NULL OR m.left_date >= p_assigned)
       ) THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'Assigned volunteer must be a member of the task team on the assigned date';
    END IF;
END//

CREATE TRIGGER trg_assignment_bi
BEFORE INSERT ON TASK_ASSIGNMENT
FOR EACH ROW
BEGIN
    CALL sp_check_assignment(NEW.volunteer_id, NEW.task_id, NEW.assigned_date);
END//

CREATE TRIGGER trg_assignment_bu
BEFORE UPDATE ON TASK_ASSIGNMENT
FOR EACH ROW
BEGIN
    IF NEW.volunteer_id <> OLD.volunteer_id OR NEW.task_id <> OLD.task_id
       OR NEW.assigned_date <> OLD.assigned_date THEN
        CALL sp_check_assignment(NEW.volunteer_id, NEW.task_id, NEW.assigned_date);
    END IF;
END//

-- BR24: the team of a task cannot be changed while the task has assignments
-- (the assigned volunteers belong to the old team). A team that does not exist
-- is left to the foreign key (error 1452). It runs after module 03's trigger.
CREATE TRIGGER trg_task_team_bu
BEFORE UPDATE ON TASK
FOR EACH ROW
FOLLOWS trg_task_bu
BEGIN
    IF NEW.team_id <> OLD.team_id
       AND EXISTS (SELECT 1 FROM TEAM WHERE team_id = NEW.team_id)
       AND EXISTS (SELECT 1 FROM TASK_ASSIGNMENT WHERE task_id = OLD.task_id) THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'The team of a task that already has assignments cannot be changed';
    END IF;
END//
DELIMITER ;

-- Audit view: lists assignments whose volunteer was NOT a member of the task's
-- team on the assigned date (same condition as sp_check_assignment, negated).
-- It must always be empty. A volunteer who leaves the team AFTER the assigned date
-- keeps the assignment as history, which is allowed and does not appear here.
CREATE VIEW v_assignment_outside_membership AS
SELECT a.volunteer_id, a.task_id, a.assigned_date
FROM TASK_ASSIGNMENT a
JOIN TASK t ON t.task_id = a.task_id
WHERE NOT EXISTS (
    SELECT 1 FROM MEMBERSHIP m
    WHERE m.volunteer_id = a.volunteer_id
      AND m.team_id = t.team_id
      AND m.joined_date <= a.assigned_date
      AND (m.left_date IS NULL OR m.left_date >= a.assigned_date));

-- Sample assignments: every volunteer is a member of the task's team on the assigned date.
-- Dates are relative to CURRENT_DATE. Tasks 1-30: the original assignees; tasks 31-55:
-- a current member of the team chosen in turn.
INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id, assigned_date) VALUES
 (8, 1, CURRENT_DATE - INTERVAL 325 DAY),
 (1, 2, CURRENT_DATE - INTERVAL 295 DAY),
 (4, 3, CURRENT_DATE - INTERVAL 225 DAY),
 (7, 4, CURRENT_DATE - INTERVAL 45 DAY),
 (12, 5, CURRENT_DATE - INTERVAL 1 DAY),
 (8, 6, CURRENT_DATE - INTERVAL 298 DAY),
 (1, 7, CURRENT_DATE - INTERVAL 285 DAY),
 (6, 8, CURRENT_DATE - INTERVAL 255 DAY),
 (5, 9, CURRENT_DATE - INTERVAL 260 DAY),
 (2, 10, CURRENT_DATE - INTERVAL 195 DAY),
 (4, 11, CURRENT_DATE - INTERVAL 185 DAY),
 (3, 12, CURRENT_DATE - INTERVAL 245 DAY),
 (14, 13, CURRENT_DATE - INTERVAL 225 DAY),
 (12, 14, CURRENT_DATE - INTERVAL 175 DAY),
 (11, 15, CURRENT_DATE - INTERVAL 30 DAY),
 (4, 16, CURRENT_DATE - INTERVAL 198 DAY),
 (2, 17, CURRENT_DATE - INTERVAL 190 DAY),
 (4, 18, CURRENT_DATE - INTERVAL 170 DAY),
 (11, 19, CURRENT_DATE - INTERVAL 20 DAY),
 (14, 20, CURRENT_DATE - INTERVAL 1 DAY),
 (15, 21, CURRENT_DATE - INTERVAL 115 DAY),
 (5, 22, CURRENT_DATE - INTERVAL 32 DAY),
 (9, 23, CURRENT_DATE - INTERVAL 1 DAY),
 (14, 24, CURRENT_DATE - INTERVAL 1 DAY),
 (2, 25, CURRENT_DATE - INTERVAL 1 DAY),
 (8, 26, CURRENT_DATE - INTERVAL 15 DAY),
 (4, 27, CURRENT_DATE - INTERVAL 1 DAY),
 (6, 28, CURRENT_DATE - INTERVAL 1 DAY),
 (4, 29, CURRENT_DATE - INTERVAL 1 DAY),
 (2, 30, CURRENT_DATE - INTERVAL 5 DAY),
 (15, 31, CURRENT_DATE - INTERVAL 138 DAY),
 (10, 32, CURRENT_DATE - INTERVAL 115 DAY),
 (3, 33, CURRENT_DATE - INTERVAL 75 DAY),
 (13, 34, CURRENT_DATE - INTERVAL 73 DAY),
 (4, 35, CURRENT_DATE - INTERVAL 65 DAY),
 (4, 36, CURRENT_DATE - INTERVAL 33 DAY),
 (14, 37, CURRENT_DATE - INTERVAL 15 DAY),
 (16, 38, CURRENT_DATE - INTERVAL 58 DAY),
 (7, 39, CURRENT_DATE - INTERVAL 5 DAY),
 (12, 40, CURRENT_DATE - INTERVAL 1 DAY),
 (6, 41, CURRENT_DATE - INTERVAL 48 DAY),
 (6, 42, CURRENT_DATE - INTERVAL 15 DAY),
 (16, 43, CURRENT_DATE - INTERVAL 1 DAY),
 (15, 44, CURRENT_DATE - INTERVAL 1 DAY),
 (15, 45, CURRENT_DATE - INTERVAL 43 DAY),
 (10, 46, CURRENT_DATE - INTERVAL 10 DAY),
 (8, 47, CURRENT_DATE - INTERVAL 1 DAY),
 (8, 48, CURRENT_DATE - INTERVAL 1 DAY),
 (11, 49, CURRENT_DATE - INTERVAL 1 DAY),
 (8, 50, CURRENT_DATE - INTERVAL 23 DAY),
 (14, 51, CURRENT_DATE - INTERVAL 15 DAY),
 (10, 52, CURRENT_DATE - INTERVAL 1 DAY),
 (7, 53, CURRENT_DATE - INTERVAL 10 DAY),
 (13, 54, CURRENT_DATE - INTERVAL 18 DAY),
 (16, 55, CURRENT_DATE - INTERVAL 1 DAY);

-- Demonstrate multiple assignees for one task.
INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id, assigned_date) VALUES
 (3, 1, CURRENT_DATE - INTERVAL 324 DAY),
 (9, 4, CURRENT_DATE - INTERVAL 44 DAY),
 (10, 10, CURRENT_DATE - INTERVAL 194 DAY);

-- Author can be any volunteer; comment_no restarts at 1 for each task.
INSERT INTO TASK_COMMENT (task_id, comment_no, volunteer_id, content, created_at) VALUES
 (1, 1, 8, 'Survey completed; uploading school checklist.', CURRENT_DATE - INTERVAL 310 DAY + INTERVAL 9 HOUR),
 (1, 2, 3, 'I reviewed the survey findings.', CURRENT_DATE - INTERVAL 309 DAY + INTERVAL 14 HOUR + INTERVAL 15 MINUTE),
 (2, 1, 1, 'Budget has been approved.', CURRENT_DATE - INTERVAL 285 DAY + INTERVAL 10 HOUR + INTERVAL 30 MINUTE),
 (3, 1, 4, 'Supplier quotes are attached.', CURRENT_DATE - INTERVAL 230 DAY + INTERVAL 9 HOUR + INTERVAL 45 MINUTE),
 (4, 1, 7, 'Installation is underway.', CURRENT_DATE - INTERVAL 70 DAY + INTERVAL 8 HOUR),
 (4, 2, 9, 'Please attach the updated installation photos.', CURRENT_DATE - INTERVAL 69 DAY + INTERVAL 16 HOUR),
 (5, 1, 12, 'Training materials are ready for review.', CURRENT_DATE - INTERVAL 15 DAY + INTERVAL 10 HOUR),
 (10, 1, 2, 'Social media materials scheduled.', CURRENT_DATE - INTERVAL 210 DAY + INTERVAL 13 HOUR),
 (21, 1, 15, 'Website audit report uploaded.', CURRENT_DATE - INTERVAL 95 DAY + INTERVAL 11 HOUR + INTERVAL 15 MINUTE),
 (22, 1, 5, 'Brand concepts are in the attached files.', CURRENT_DATE - INTERVAL 40 DAY + INTERVAL 15 HOUR + INTERVAL 30 MINUTE),
 (23, 1, 9, 'Waiting for the design approval.', CURRENT_DATE - INTERVAL 39 DAY + INTERVAL 9 HOUR),
 (26, 1, 8, 'Location planning started.', CURRENT_DATE - INTERVAL 30 DAY + INTERVAL 8 HOUR),
 (30, 1, 2, 'This activity is cancelled.', CURRENT_DATE - INTERVAL 35 DAY + INTERVAL 17 HOUR),
 (33, 1, 4, 'Photo story is published on the website.', CURRENT_DATE - INTERVAL 55 DAY + INTERVAL 10 HOUR),
 (36, 1, 9, 'Two trucks are late; we need a new transport slot.', CURRENT_DATE - INTERVAL 12 DAY + INTERVAL 14 HOUR),
 (39, 1, 15, 'Safety rules draft is ready for review.', CURRENT_DATE - INTERVAL 30 DAY + INTERVAL 9 HOUR + INTERVAL 30 MINUTE),
 (42, 1, 9, 'Accounts for the last quarter are still missing.', CURRENT_DATE - INTERVAL 20 DAY + INTERVAL 11 HOUR),
 (45, 1, 8, 'Catalogue database is online and tested.', CURRENT_DATE - INTERVAL 28 DAY + INTERVAL 16 HOUR),
 (46, 1, 12, 'First training session is planned for next week.', CURRENT_DATE - INTERVAL 18 DAY + INTERVAL 10 HOUR + INTERVAL 30 MINUTE),
 (51, 1, 3, 'Red Cross sent the draft course outline.', CURRENT_DATE - INTERVAL 9 DAY + INTERVAL 13 HOUR + INTERVAL 45 MINUTE),
 (53, 1, 2, 'Venue deposit has been paid.', CURRENT_DATE - INTERVAL 6 DAY + INTERVAL 15 HOUR);

INSERT INTO ATTACHMENT (task_id, comment_no, attachment_no, file_name, file_path, file_type, uploaded_at) VALUES
 (1, 1, 1, 'school-checklist.pdf', '/uploads/tasks/1/comments/1/school-checklist.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 310 DAY + INTERVAL 9 HOUR + INTERVAL 5 MINUTE),
 (1, 1, 2, 'water-samples.csv', '/uploads/tasks/1/comments/1/water-samples.csv', 'text/csv', CURRENT_DATE - INTERVAL 310 DAY + INTERVAL 9 HOUR + INTERVAL 6 MINUTE),
 (3, 1, 1, 'supplier-quotes.xlsx', '/uploads/tasks/3/comments/1/supplier-quotes.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', CURRENT_DATE - INTERVAL 230 DAY + INTERVAL 10 HOUR),
 (4, 1, 1, 'installation-photo.jpg', '/uploads/tasks/4/comments/1/installation-photo.jpg', 'image/jpeg', CURRENT_DATE - INTERVAL 70 DAY + INTERVAL 8 HOUR + INTERVAL 10 MINUTE),
 (4, 2, 1, 'installation-update.jpg', '/uploads/tasks/4/comments/2/installation-update.jpg', 'image/jpeg', CURRENT_DATE - INTERVAL 69 DAY + INTERVAL 16 HOUR + INTERVAL 10 MINUTE),
 (5, 1, 1, 'training-outline.pdf', '/uploads/tasks/5/comments/1/training-outline.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 15 DAY + INTERVAL 10 HOUR + INTERVAL 10 MINUTE),
 (21, 1, 1, 'audit-report.pdf', '/uploads/tasks/21/comments/1/audit-report.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 95 DAY + INTERVAL 11 HOUR + INTERVAL 20 MINUTE),
 (22, 1, 1, 'brand-concept.png', '/uploads/tasks/22/comments/1/brand-concept.png', 'image/png', CURRENT_DATE - INTERVAL 40 DAY + INTERVAL 15 HOUR + INTERVAL 35 MINUTE),
 (22, 1, 2, 'brand-palette.pdf', '/uploads/tasks/22/comments/1/brand-palette.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 40 DAY + INTERVAL 15 HOUR + INTERVAL 36 MINUTE),
 (26, 1, 1, 'camp-locations.pdf', '/uploads/tasks/26/comments/1/camp-locations.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 30 DAY + INTERVAL 8 HOUR + INTERVAL 5 MINUTE),
 (30, 1, 1, 'cancellation-note.txt', '/uploads/tasks/30/comments/1/cancellation-note.txt', 'text/plain', CURRENT_DATE - INTERVAL 35 DAY + INTERVAL 17 HOUR + INTERVAL 5 MINUTE),
 (33, 1, 1, 'closing-report.pdf', '/uploads/tasks/33/comments/1/closing-report.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 55 DAY + INTERVAL 10 HOUR + INTERVAL 5 MINUTE),
 (36, 1, 1, 'delay-report.pdf', '/uploads/tasks/36/comments/1/delay-report.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 12 DAY + INTERVAL 14 HOUR + INTERVAL 5 MINUTE),
 (39, 1, 1, 'camp-safety-rules.pdf', '/uploads/tasks/39/comments/1/camp-safety-rules.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 30 DAY + INTERVAL 9 HOUR + INTERVAL 40 MINUTE),
 (42, 1, 1, 'missing-accounts.xlsx', '/uploads/tasks/42/comments/1/missing-accounts.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', CURRENT_DATE - INTERVAL 20 DAY + INTERVAL 11 HOUR + INTERVAL 8 MINUTE),
 (46, 1, 1, 'training-agenda.pdf', '/uploads/tasks/46/comments/1/training-agenda.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 18 DAY + INTERVAL 10 HOUR + INTERVAL 35 MINUTE),
 (51, 1, 1, 'course-outline.pdf', '/uploads/tasks/51/comments/1/course-outline.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 9 DAY + INTERVAL 13 HOUR + INTERVAL 50 MINUTE),
 (53, 1, 1, 'venue-deposit.pdf', '/uploads/tasks/53/comments/1/venue-deposit.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 6 DAY + INTERVAL 15 HOUR + INTERVAL 5 MINUTE),
 (53, 1, 2, 'catering-menu.pdf', '/uploads/tasks/53/comments/1/catering-menu.pdf', 'application/pdf', CURRENT_DATE - INTERVAL 6 DAY + INTERVAL 15 HOUR + INTERVAL 9 MINUTE);

SELECT 'TASK_ASSIGNMENT' AS table_name, COUNT(*) AS row_count FROM TASK_ASSIGNMENT
UNION ALL SELECT 'TASK_COMMENT', COUNT(*) FROM TASK_COMMENT
UNION ALL SELECT 'ATTACHMENT', COUNT(*) FROM ATTACHMENT;

-- NOTE ON SCOPE: BR8 is checked when an assignment is inserted or changed, and a
-- task whose assignments exist cannot change team (BR24). Setting MEMBERSHIP.left_date
-- later is NOT blocked: the assignment stays as history (it is still inside the
-- membership interval). Open tasks of leavers are found with the report query
-- "open tasks assigned to former members of the team" (11_queries.sql).
