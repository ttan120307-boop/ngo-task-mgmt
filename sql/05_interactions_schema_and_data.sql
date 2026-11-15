-- =====================================================================
-- IT079IU - Topic 6 / Member 3: Interactions & Weak Entities
-- RUN ORDER: 01_foundation_schema_and_data.sql
--            03_project_task_schema_and_data.sql
--            05_interactions_schema_and_data.sql  (this file)
-- Requires MySQL 8.0.16+ and the Module 1 & Module 2 sample data.
-- No DROP DATABASE or DROP parent tables in this module.
-- =====================================================================
USE ngo_task_mgmt;

-- Re-running THIS module only: remove child first, then parent.
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

-- Weak entity: comment_id is a LOCAL sequence number within task_id.
-- The author is mandatory, and cannot be deleted while comments exist.
CREATE TABLE TASK_COMMENT (
    task_id      INT UNSIGNED NOT NULL,
    comment_id   INT UNSIGNED NOT NULL,
    volunteer_id INT UNSIGNED NOT NULL,
    content      TEXT NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_task_comment PRIMARY KEY (task_id, comment_id),
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
    comment_id    INT UNSIGNED NOT NULL,
    attachment_id INT UNSIGNED NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    file_type     VARCHAR(100) NULL,
    uploaded_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_attachment PRIMARY KEY (task_id, comment_id, attachment_id),
    CONSTRAINT fk_attachment_comment FOREIGN KEY (task_id, comment_id)
        REFERENCES TASK_COMMENT(task_id, comment_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_attachment_filename CHECK (CHAR_LENGTH(TRIM(file_name)) > 0),
    CONSTRAINT ck_attachment_filepath CHECK (CHAR_LENGTH(TRIM(file_path)) > 0)
) ENGINE=InnoDB;

-- BR8: For a task assigned to a team, only CURRENT members of that team
-- (MEMBERSHIP.left_date IS NULL) can be assigned to it.
-- Member 2's TASK.team_id is NOT NULL, so every task has a team.
DELIMITER //
CREATE TRIGGER trg_assignment_bi
BEFORE INSERT ON TASK_ASSIGNMENT
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM TASK WHERE task_id = NEW.task_id)
       AND NOT EXISTS (
           SELECT 1 FROM TASK t
           JOIN MEMBERSHIP m ON m.team_id = t.team_id
           WHERE t.task_id = NEW.task_id
             AND m.volunteer_id = NEW.volunteer_id
             AND m.left_date IS NULL
       ) THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'Assigned volunteer must be a current member of the task team';
    END IF;
END//

CREATE TRIGGER trg_assignment_bu
BEFORE UPDATE ON TASK_ASSIGNMENT
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM TASK WHERE task_id = NEW.task_id)
       AND NOT EXISTS (
           SELECT 1 FROM TASK t
           JOIN MEMBERSHIP m ON m.team_id = t.team_id
           WHERE t.task_id = NEW.task_id
             AND m.volunteer_id = NEW.volunteer_id
             AND m.left_date IS NULL
       ) THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'Assigned volunteer must be a current member of the task team';
    END IF;
END//
DELIMITER ;

-- Sample assignments: volunteers are active members of each task's team.
INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id, assigned_date) VALUES
 (8,1,'2024-03-02'), (1,2,'2024-04-01'), (4,3,'2024-04-16'),
 (7,4,'2024-07-01'), (12,5,'2024-10-01'), (8,6,'2024-06-02'),
 (1,7,'2024-06-21'), (6,8,'2024-06-22'), (5,9,'2024-06-22'),
 (2,10,'2024-07-26'), (4,11,'2024-08-01'), (3,12,'2024-09-02'),
 (14,13,'2024-10-16'), (12,14,'2024-11-01'), (11,15,'2025-01-01'),
 (4,16,'2024-10-16'), (13,17,'2024-10-26'), (4,18,'2024-11-06'),
 (11,19,'2025-01-11'), (14,20,'2025-02-01'), (15,21,'2025-01-16'),
 (5,22,'2025-02-01'), (9,23,'2025-02-15'), (14,24,'2025-03-01'),
 (2,25,'2025-03-01'), (8,26,'2025-02-02'), (4,27,'2025-02-03'),
 (6,28,'2025-02-04'), (4,29,'2025-04-01'), (2,30,'2025-02-02');

-- Demonstrate multiple assignees for one task.
INSERT INTO TASK_ASSIGNMENT (volunteer_id, task_id, assigned_date) VALUES
 (3,1,'2024-03-03'), (9,4,'2024-07-02'), (10,10,'2024-08-02');

-- Author can be any volunteer; comment_id restarts at 1 for each task.
INSERT INTO TASK_COMMENT (task_id, comment_id, volunteer_id, content, created_at) VALUES
 (1,1,8,'Survey completed; uploading school checklist.','2024-03-20 09:00:00'),
 (1,2,3,'I reviewed the survey findings.','2024-03-21 14:15:00'),
 (2,1,1,'Budget has been approved.','2024-04-10 10:30:00'),
 (3,1,4,'Supplier quotes are attached.','2024-06-12 09:45:00'),
 (4,1,7,'Installation is underway.','2024-09-15 08:00:00'),
 (4,2,9,'Please attach the updated installation photos.','2024-09-16 16:00:00'),
 (5,1,12,'Training materials are ready for review.','2024-11-01 10:00:00'),
 (10,1,2,'Social media materials scheduled.','2024-08-01 13:00:00'),
 (21,1,15,'Website audit report uploaded.','2025-01-25 11:15:00'),
 (22,1,5,'Brand concepts are in the attached files.','2025-03-02 15:30:00'),
 (23,1,9,'Waiting for the design approval.','2025-03-03 09:00:00'),
 (26,1,8,'Location planning started.','2025-02-15 08:00:00'),
 (30,1,2,'This activity is cancelled.','2025-02-20 17:00:00');

INSERT INTO ATTACHMENT (task_id, comment_id, attachment_id, file_name, file_path, file_type, uploaded_at) VALUES
 (1,1,1,'school-checklist.pdf','/uploads/tasks/1/comments/1/school-checklist.pdf','application/pdf','2024-03-20 09:05:00'),
 (1,1,2,'water-samples.csv','/uploads/tasks/1/comments/1/water-samples.csv','text/csv','2024-03-20 09:06:00'),
 (3,1,1,'supplier-quotes.xlsx','/uploads/tasks/3/comments/1/supplier-quotes.xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','2024-06-12 10:00:00'),
 (4,1,1,'installation-photo.jpg','/uploads/tasks/4/comments/1/installation-photo.jpg','image/jpeg','2024-09-15 08:10:00'),
 (4,2,1,'installation-update.jpg','/uploads/tasks/4/comments/2/installation-update.jpg','image/jpeg','2024-09-16 16:10:00'),
 (5,1,1,'training-outline.pdf','/uploads/tasks/5/comments/1/training-outline.pdf','application/pdf','2024-11-01 10:10:00'),
 (21,1,1,'audit-report.pdf','/uploads/tasks/21/comments/1/audit-report.pdf','application/pdf','2025-01-25 11:20:00'),
 (22,1,1,'brand-concept.png','/uploads/tasks/22/comments/1/brand-concept.png','image/png','2025-03-02 15:35:00'),
 (22,1,2,'brand-palette.pdf','/uploads/tasks/22/comments/1/brand-palette.pdf','application/pdf','2025-03-02 15:36:00'),
 (26,1,1,'camp-locations.pdf','/uploads/tasks/26/comments/1/camp-locations.pdf','application/pdf','2025-02-15 08:05:00'),
 (30,1,1,'cancellation-note.txt','/uploads/tasks/30/comments/1/cancellation-note.txt','text/plain','2025-02-20 17:05:00');

SELECT 'TASK_ASSIGNMENT' AS table_name, COUNT(*) AS row_count FROM TASK_ASSIGNMENT
UNION ALL SELECT 'TASK_COMMENT', COUNT(*) FROM TASK_COMMENT
UNION ALL SELECT 'ATTACHMENT', COUNT(*) FROM ATTACHMENT;

-- NOTE ON SCOPE: BR8 is checked at assignment INSERT/UPDATE. Later changes
-- to MEMBERSHIP.left_date or TASK.team_id are not revalidated by this module.
-- Discuss with the team if BR8 must be guaranteed after those changes too.
