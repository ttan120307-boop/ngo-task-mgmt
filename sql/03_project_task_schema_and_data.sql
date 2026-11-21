-- =====================================================================
-- IT079IU - Topic 6: Volunteer/NGO Project and Task Management
-- Module 2: Project & Task (PROJECT, TASK, TASK_DEPENDENCY)
--
-- DBMS: MySQL 8.0.16 or later (CHECK enforced, InnoDB).
-- HOW TO RUN: run 01_foundation_schema_and_data.sql FIRST. It creates the
-- database ngo_task_mgmt and the tables VOLUNTEER and TEAM that this module
-- references. This file does NOT contain DROP DATABASE (only the master
-- script may). To re-run this module alone, just run this file again: it
-- first drops the tables of the later modules (05 and 07) that point to TASK,
-- so MySQL error 3730 cannot occur. Those modules (and their data) are then
-- gone and must be run again; 00_run_all.sql does everything in order.
--
-- MySQL-specific features used (not standard SQL):
--   * DEFAULT (CURRENT_DATE)            expression default (8.0.13+)
--   * WITH RECURSIVE inside a trigger   (cycle detection, 8.0+)
--   * triggers with SIGNAL SQLSTATE '45000'  (MySQL error 1644)
--   * DELIMITER (client command, used by mysql CLI and MySQL Workbench)
--
-- WHY THE "NO SELF-DEPENDENCY" RULE IS A TRIGGER AND NOT A CHECK:
--   MySQL forbids a CHECK on a column that is used by a foreign key with a
--   referential action (error 3823). Both columns of TASK_DEPENDENCY are
--   foreign keys with ON DELETE CASCADE, so
--   CHECK (dependent_task_id <> prerequisite_task_id) is rejected. The rule
--   is enforced in trigger trg_taskdep_bi instead.
--
-- BUSINESS RULE 6 (task data): title, priority, status, estimated_hours and due_date are ALL
--   mandatory (NOT NULL). The sample data gives every task, including the cancelled
--   task 30, an estimate and a due date; description stays optional.
-- DATES: every date in the sample data is written relative to CURRENT_DATE, so the
--   data is "alive" on any day the script is run (one dataset, no patch file).
-- RULE BR11 (task due date <= project due date): the due date of a task is on or before the due
--   date of its project. It compares two tables, so it is a trigger:
--   trg_task_bi / trg_task_bu (task side) and trg_project_bu (project side).
-- =====================================================================
USE ngo_task_mgmt;

-- Tables of later modules that reference TASK (they do not exist on a first run).
DROP TABLE IF EXISTS TASK_LABEL;
DROP TABLE IF EXISTS ATTACHMENT;
DROP TABLE IF EXISTS TASK_COMMENT;
DROP TABLE IF EXISTS TASK_ASSIGNMENT;

DROP TABLE IF EXISTS TASK_DEPENDENCY;
DROP TABLE IF EXISTS TASK;
DROP TABLE IF EXISTS PROJECT;

-- ---------------------------------------------------------------------
-- 1. PROJECT  (high-level work of the NGO; owned by one volunteer)
--    A volunteer who still owns a project cannot be deleted (RESTRICT),
--    the same policy as MEMBERSHIP in module 1.
-- ---------------------------------------------------------------------
CREATE TABLE PROJECT (
    project_id    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    owner_id      INT UNSIGNED  NOT NULL,
    project_name  VARCHAR(150)  NOT NULL,
    description   VARCHAR(500)  NULL,
    start_date    DATE          NOT NULL DEFAULT (CURRENT_DATE),
    due_date      DATE          NOT NULL,
    CONSTRAINT pk_project       PRIMARY KEY (project_id),
    CONSTRAINT fk_project_owner  FOREIGN KEY (owner_id)
        REFERENCES VOLUNTEER (volunteer_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_project_name  CHECK (CHAR_LENGTH(TRIM(project_name)) > 0),
    CONSTRAINT ck_project_dates CHECK (due_date >= start_date)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. TASK  (individual work items of a project, done by one team)
--    Deleting a project deletes its tasks (ON DELETE CASCADE), and through
--    TASK_DEPENDENCY the dependency rows of those tasks as well.
--    A team that still has tasks cannot be deleted (RESTRICT).
--    priority: LOW < MEDIUM < HIGH < URGENT
--    status  : TODO -> IN_PROGRESS -> DONE, or CANCELLED
-- ---------------------------------------------------------------------
CREATE TABLE TASK (
    task_id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    project_id       INT UNSIGNED  NOT NULL,
    team_id          INT UNSIGNED  NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    description      TEXT          NULL,
    priority         VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    status           VARCHAR(12)   NOT NULL DEFAULT 'TODO',
    estimated_hours  DECIMAL(6,2)  NOT NULL,
    due_date         DATE          NOT NULL,
    CONSTRAINT pk_task PRIMARY KEY (task_id),
    CONSTRAINT fk_task_project FOREIGN KEY (project_id)
        REFERENCES PROJECT (project_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_task_team FOREIGN KEY (team_id)
        REFERENCES TEAM (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_task_title    CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_task_status   CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_task_hours    CHECK (estimated_hours > 0)
) ENGINE = InnoDB;

-- Index for the query "open tasks of a project, earliest due date first":
--   WHERE project_id = ? AND status IN ('TODO','IN_PROGRESS') ORDER BY due_date
-- (project_id, status) filter, the third column due_date lets MySQL read the rows
-- already sorted. See 12_index_explain.sql for EXPLAIN before / after.
CREATE INDEX idx_task_project_status ON TASK (project_id, status, due_date);

-- ---------------------------------------------------------------------
-- 3. TASK_DEPENDENCY  (recursive M:N relationship of TASK with itself)
--    One row = "dependent_task_id cannot start until prerequisite_task_id
--    is DONE". Both columns reference TASK. Deleting either task removes
--    the row (CASCADE), so no orphan dependency can exist.
-- ---------------------------------------------------------------------
CREATE TABLE TASK_DEPENDENCY (
    dependent_task_id    INT UNSIGNED NOT NULL,
    prerequisite_task_id INT UNSIGNED NOT NULL,
    CONSTRAINT pk_task_dependency PRIMARY KEY (dependent_task_id, prerequisite_task_id),
    CONSTRAINT fk_taskdep_dependent FOREIGN KEY (dependent_task_id)
        REFERENCES TASK (task_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_taskdep_prerequisite FOREIGN KEY (prerequisite_task_id)
        REFERENCES TASK (task_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB;
-- The primary key serves lookups by dependent task; the foreign key on
-- prerequisite_task_id creates the index for the reverse lookup.

-- ---------------------------------------------------------------------
-- Cross-row rules that CHECK cannot express (triggers):
--   BR19  a task cannot depend on itself
--   BR9  dependent and prerequisite task belong to the SAME project
--   BR20  no circular dependency (A needs B, B needs C, C needs A)
--   BR10  a task that has started (IN_PROGRESS/DONE) can only get
--        prerequisites that are already DONE
--   BR10  a task cannot become IN_PROGRESS or DONE until ALL its
--        prerequisites are DONE   ("cannot start until prerequisites are
--        completed"); a CANCELLED prerequisite does not count as completed
--   BR21  a DONE task cannot be re-opened while a dependent task has started
--   BR22  a task that takes part in a dependency cannot move to another
--        project (it would break BR9)
--   BR23  a dependency row is never edited; delete it and insert a new one
--   BR11  task.due_date <= project.due_date (rule 11); checked when a task is
--        inserted or its date/project changes, and when a project due date
--        is moved earlier than one of its tasks
-- A task that waits for a CANCELLED task can never start (BR10 counts only DONE
-- as completed). This is a deliberate decision: delete that dependency row
-- (or re-open the prerequisite) to release the task.
-- Scalar sub-selects are used so that an unknown task gives NULL (the
-- foreign key then reports error 1452 as usual).
-- Cascaded foreign-key actions do not fire triggers in MySQL, so deleting
-- or renumbering a project/task is never blocked by these triggers.
-- Limitation: concurrent sessions can bypass BR20 (no locking).
-- ---------------------------------------------------------------------
DELIMITER //

CREATE TRIGGER trg_taskdep_bi
BEFORE INSERT ON TASK_DEPENDENCY
FOR EACH ROW
BEGIN
    DECLARE v_dep_project INT UNSIGNED;
    DECLARE v_pre_project INT UNSIGNED;
    DECLARE v_dep_status  VARCHAR(12);
    DECLARE v_pre_status  VARCHAR(12);
    DECLARE v_cycle       INT DEFAULT 0;

    -- BR19
    IF NEW.dependent_task_id = NEW.prerequisite_task_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task cannot depend on itself';
    END IF;

    SET v_dep_project = (SELECT project_id FROM TASK WHERE task_id = NEW.dependent_task_id);
    SET v_pre_project = (SELECT project_id FROM TASK WHERE task_id = NEW.prerequisite_task_id);
    SET v_dep_status  = (SELECT status     FROM TASK WHERE task_id = NEW.dependent_task_id);
    SET v_pre_status  = (SELECT status     FROM TASK WHERE task_id = NEW.prerequisite_task_id);

    -- BR9
    IF v_dep_project <> v_pre_project THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Dependent and prerequisite task must belong to the same project';
    END IF;

    -- BR20: the new edge "dependent needs prerequisite" closes a cycle exactly
    -- when the dependent task is already a (direct or indirect) prerequisite
    -- of the prerequisite task.
    WITH RECURSIVE ancestors (task_id) AS (
        SELECT prerequisite_task_id FROM TASK_DEPENDENCY
         WHERE dependent_task_id = NEW.prerequisite_task_id
        UNION
        SELECT d.prerequisite_task_id FROM TASK_DEPENDENCY d
          JOIN ancestors a ON d.dependent_task_id = a.task_id
    )
    SELECT COUNT(*) INTO v_cycle FROM ancestors WHERE task_id = NEW.dependent_task_id;
    IF v_cycle > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'This dependency would create a circular dependency';
    END IF;

    -- BR10
    IF v_dep_status IN ('IN_PROGRESS', 'DONE') AND v_pre_status <> 'DONE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task that has already started cannot get an unfinished prerequisite';
    END IF;
END//

CREATE TRIGGER trg_taskdep_bu
BEFORE UPDATE ON TASK_DEPENDENCY
FOR EACH ROW
BEGIN
    -- BR23
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'A dependency cannot be updated; delete it and insert a new one';
END//

CREATE TRIGGER trg_task_bi
BEFORE INSERT ON TASK
FOR EACH ROW
BEGIN
    -- BR11 (an unknown project gives NULL here, so the foreign key reports 1452)
    IF NEW.due_date > (SELECT due_date FROM PROJECT WHERE project_id = NEW.project_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Task due date is after the project due date';
    END IF;
END//

CREATE TRIGGER trg_project_bu
BEFORE UPDATE ON PROJECT
FOR EACH ROW
BEGIN
    -- BR11 from the project side: the due date cannot move before a task's due date
    IF NEW.due_date < OLD.due_date AND EXISTS (
           SELECT 1 FROM TASK
            WHERE project_id = OLD.project_id AND due_date > NEW.due_date) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task of this project is due after the new project due date';
    END IF;
END//

CREATE TRIGGER trg_task_bu
BEFORE UPDATE ON TASK
FOR EACH ROW
BEGIN
    -- BR22
    IF NEW.project_id <> OLD.project_id AND EXISTS (
           SELECT 1 FROM TASK_DEPENDENCY
            WHERE dependent_task_id = OLD.task_id OR prerequisite_task_id = OLD.task_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task that has dependencies cannot move to another project';
    END IF;

    -- BR11
    IF (NEW.due_date <> OLD.due_date OR NEW.project_id <> OLD.project_id)
       AND NEW.due_date > (SELECT due_date FROM PROJECT WHERE project_id = NEW.project_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Task due date is after the project due date';
    END IF;

    -- BR10 (only when the status changes, so editing a title never fails)
    IF NEW.status <> OLD.status AND NEW.status IN ('IN_PROGRESS', 'DONE') AND EXISTS (
           SELECT 1 FROM TASK_DEPENDENCY d
             JOIN TASK p ON p.task_id = d.prerequisite_task_id
            WHERE d.dependent_task_id = OLD.task_id AND p.status <> 'DONE') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task cannot start until all its prerequisite tasks are completed';
    END IF;

    -- BR21
    IF OLD.status = 'DONE' AND NEW.status <> 'DONE' AND EXISTS (
           SELECT 1 FROM TASK_DEPENDENCY d
             JOIN TASK t ON t.task_id = d.dependent_task_id
            WHERE d.prerequisite_task_id = OLD.task_id
              AND t.status IN ('IN_PROGRESS', 'DONE')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task cannot be re-opened while a dependent task has already started';
    END IF;
END//

DELIMITER ;

-- =====================================================================
-- Sample data. Fictional. 15 / 55 / 45 rows. All dates are relative to CURRENT_DATE.
-- Owners and teams are taken from module 1 (volunteers 1-16, teams 1-15).
-- Rows are inserted so that every trigger rule is already satisfied.
-- Project 7 has NO task on purpose (it shows NOT EXISTS / LEFT JOIN on an empty set);
-- project 8 has only DONE tasks (a completed project); projects 1, 3, 4, 9 have open
-- tasks that are already past their due date (overdue queries).
-- =====================================================================

INSERT INTO PROJECT (project_id, owner_id, project_name, description, start_date, due_date) VALUES
 (1,  8, 'Clean Water for Rural Schools', 'Install and maintain water filters in partner schools', CURRENT_DATE - INTERVAL 330 DAY, CURRENT_DATE + INTERVAL 60 DAY),
 (2,  6, 'Back-to-School Charity Fair', 'Annual fair that raises funds for school supplies', CURRENT_DATE - INTERVAL 300 DAY, CURRENT_DATE - INTERVAL 150 DAY),
 (3,  3, 'Volunteer Onboarding Programme', 'Handbook, orientation and feedback for new volunteers', CURRENT_DATE - INTERVAL 250 DAY, CURRENT_DATE + INTERVAL 20 DAY),
 (4, 11, 'Flood Relief Response', 'Emergency supplies and recovery visits after the autumn floods', CURRENT_DATE - INTERVAL 200 DAY, CURRENT_DATE + INTERVAL 30 DAY),
 (5, 15, 'NGO Website Redesign', 'New visual identity and website for the organization', CURRENT_DATE - INTERVAL 120 DAY, CURRENT_DATE + INTERVAL 90 DAY),
 (6,  1, 'Community Health Camp', 'Free health check-ups in three communes', CURRENT_DATE - INTERVAL 90 DAY, CURRENT_DATE + INTERVAL 100 DAY),
 (7,  8, 'Library Corner Programme', 'Reading corners in partner schools; planning has not started', CURRENT_DATE - INTERVAL 10 DAY, CURRENT_DATE + INTERVAL 180 DAY),
 (8,  3, 'Teacher Appreciation Week', 'Thank-you events and small gifts for partner-school teachers', CURRENT_DATE - INTERVAL 140 DAY, CURRENT_DATE - INTERVAL 30 DAY),
 (9, 11, 'Winter Clothing Drive', 'Collect and distribute warm clothes before winter', CURRENT_DATE - INTERVAL 75 DAY, CURRENT_DATE + INTERVAL 15 DAY),
 (10, 15, 'Youth Volunteer Camp', 'Three-day camp that recruits student volunteers', CURRENT_DATE - INTERVAL 60 DAY, CURRENT_DATE + INTERVAL 120 DAY),
 (11,  1, 'Annual Donor Report', 'Report on the use of donations, sent to every donor', CURRENT_DATE - INTERVAL 50 DAY, CURRENT_DATE + INTERVAL 70 DAY),
 (12,  6, 'Mobile Library Launch', 'A van that brings books to remote villages', CURRENT_DATE - INTERVAL 45 DAY, CURRENT_DATE + INTERVAL 85 DAY),
 (13,  8, 'Clean Water Phase 2', 'Second wave of filter installation in twelve more schools', CURRENT_DATE - INTERVAL 30 DAY, CURRENT_DATE + INTERVAL 150 DAY),
 (14,  3, 'Volunteer Safety Training', 'Safety course for all field volunteers', CURRENT_DATE - INTERVAL 25 DAY, CURRENT_DATE + INTERVAL 35 DAY),
 (15, 11, 'Year-End Fundraising Gala', 'Gala dinner that closes the fundraising year', CURRENT_DATE - INTERVAL 20 DAY, CURRENT_DATE + INTERVAL 60 DAY);

INSERT INTO TASK (task_id, project_id, team_id, title, description, priority, status, estimated_hours, due_date) VALUES
 (1, 1, 4, 'Survey partner schools', 'Visit schools and record water sources', 'HIGH', 'DONE', 24.00, CURRENT_DATE - INTERVAL 300 DAY),
 (2, 1, 5, 'Prepare project budget', 'Costs for equipment, transport and training', 'HIGH', 'DONE', 16.00, CURRENT_DATE - INTERVAL 270 DAY),
 (3, 1, 2, 'Procure filtration equipment', 'Quotes, purchase and storage', 'HIGH', 'DONE', 40.00, CURRENT_DATE - INTERVAL 200 DAY),
 (4, 1, 2, 'Install water filters at schools', NULL, 'URGENT', 'IN_PROGRESS', 80.00, CURRENT_DATE - INTERVAL 20 DAY),
 (5, 1, 12, 'Train teachers on filter maintenance', NULL, 'MEDIUM', 'TODO', 20.00, CURRENT_DATE + INTERVAL 40 DAY),
 (6, 2, 4, 'Plan fair programme and schedule', NULL, 'HIGH', 'DONE', 12.00, CURRENT_DATE - INTERVAL 280 DAY),
 (7, 2, 7, 'Secure venue permit from the district', NULL, 'HIGH', 'DONE', 10.00, CURRENT_DATE - INTERVAL 260 DAY),
 (8, 2, 3, 'Recruit sponsors for booths', NULL, 'MEDIUM', 'DONE', 30.00, CURRENT_DATE - INTERVAL 230 DAY),
 (9, 2, 9, 'Design posters and banners', NULL, 'MEDIUM', 'DONE', 18.00, CURRENT_DATE - INTERVAL 235 DAY),
 (10, 2, 1, 'Promote the fair on social media', NULL, 'MEDIUM', 'DONE', 25.00, CURRENT_DATE - INTERVAL 170 DAY),
 (11, 2, 2, 'Set up booths on event day', NULL, 'HIGH', 'DONE', 36.00, CURRENT_DATE - INTERVAL 160 DAY),
 (12, 3, 6, 'Draft volunteer handbook', NULL, 'MEDIUM', 'DONE', 20.00, CURRENT_DATE - INTERVAL 220 DAY),
 (13, 3, 14, 'Review and archive the handbook', NULL, 'LOW', 'DONE', 8.00, CURRENT_DATE - INTERVAL 200 DAY),
 (14, 3, 12, 'Run orientation workshop', NULL, 'HIGH', 'DONE', 16.00, CURRENT_DATE - INTERVAL 150 DAY),
 (15, 3, 10, 'Collect new-volunteer feedback', NULL, 'LOW', 'IN_PROGRESS', 10.00, CURRENT_DATE - INTERVAL 5 DAY),
 (16, 4, 13, 'Assess flood risk in affected communes', NULL, 'URGENT', 'DONE', 20.00, CURRENT_DATE - INTERVAL 180 DAY),
 (17, 4, 11, 'Coordinate relief supplies with partners', NULL, 'HIGH', 'DONE', 24.00, CURRENT_DATE - INTERVAL 165 DAY),
 (18, 4, 2, 'Deliver relief packages', NULL, 'URGENT', 'DONE', 60.00, CURRENT_DATE - INTERVAL 145 DAY),
 (19, 4, 15, 'Run post-flood recovery visits', NULL, 'MEDIUM', 'IN_PROGRESS', 50.00, CURRENT_DATE + INTERVAL 5 DAY),
 (20, 4, 14, 'Write the final relief report', NULL, 'LOW', 'TODO', 15.00, CURRENT_DATE + INTERVAL 25 DAY),
 (21, 5, 8, 'Audit the current website', NULL, 'MEDIUM', 'DONE', 12.00, CURRENT_DATE - INTERVAL 90 DAY),
 (22, 5, 9, 'Create the new visual identity', NULL, 'HIGH', 'IN_PROGRESS', 30.00, CURRENT_DATE - INTERVAL 7 DAY),
 (23, 5, 8, 'Build the new website pages', NULL, 'HIGH', 'TODO', 60.00, CURRENT_DATE + INTERVAL 30 DAY),
 (24, 5, 14, 'Migrate content and reports', NULL, 'MEDIUM', 'TODO', 25.00, CURRENT_DATE + INTERVAL 60 DAY),
 (25, 5, 1, 'Announce the new website', NULL, 'LOW', 'TODO', 8.00, CURRENT_DATE + INTERVAL 80 DAY),
 (26, 6, 4, 'Plan health camp locations', NULL, 'HIGH', 'IN_PROGRESS', 16.00, CURRENT_DATE + INTERVAL 10 DAY),
 (27, 6, 13, 'Prepare first-aid and safety plan', NULL, 'HIGH', 'TODO', 20.00, CURRENT_DATE + INTERVAL 35 DAY),
 (28, 6, 3, 'Raise funds for medical supplies', NULL, 'MEDIUM', 'TODO', 40.00, CURRENT_DATE + INTERVAL 50 DAY),
 (29, 6, 2, 'Transport equipment to camp sites', NULL, 'HIGH', 'TODO', 30.00, CURRENT_DATE + INTERVAL 70 DAY),
 (30, 6, 1, 'Print flyers', 'Replaced by digital posters', 'LOW', 'CANCELLED', 6.00, CURRENT_DATE + INTERVAL 20 DAY),
 (31, 8, 4, 'Collect teacher wish lists', NULL, 'MEDIUM', 'DONE', 10.00, CURRENT_DATE - INTERVAL 120 DAY),
 (32, 8, 9, 'Design thank-you cards', NULL, 'LOW', 'DONE', 12.00, CURRENT_DATE - INTERVAL 90 DAY),
 (33, 8, 1, 'Publish the event photo story', NULL, 'LOW', 'DONE', 8.00, CURRENT_DATE - INTERVAL 50 DAY),
 (34, 9, 13, 'Check storage and safety of donations', NULL, 'MEDIUM', 'DONE', 10.00, CURRENT_DATE - INTERVAL 60 DAY),
 (35, 9, 11, 'Agree drop-off points with partners', NULL, 'HIGH', 'DONE', 14.00, CURRENT_DATE - INTERVAL 40 DAY),
 (36, 9, 2, 'Transport clothes to distribution sites', NULL, 'HIGH', 'IN_PROGRESS', 36.00, CURRENT_DATE - INTERVAL 8 DAY),
 (37, 9, 14, 'Write the distribution report', NULL, 'LOW', 'TODO', 10.00, CURRENT_DATE + INTERVAL 10 DAY),
 (38, 10, 15, 'Choose the camp site', NULL, 'HIGH', 'DONE', 12.00, CURRENT_DATE - INTERVAL 40 DAY),
 (39, 10, 13, 'Draft the camp safety rules', NULL, 'HIGH', 'IN_PROGRESS', 16.00, CURRENT_DATE + INTERVAL 20 DAY),
 (40, 10, 12, 'Prepare the training sessions', NULL, 'MEDIUM', 'TODO', 30.00, CURRENT_DATE + INTERVAL 60 DAY),
 (41, 11, 3, 'Collect donation records', NULL, 'HIGH', 'DONE', 14.00, CURRENT_DATE - INTERVAL 30 DAY),
 (42, 11, 5, 'Audit the donation accounts', NULL, 'URGENT', 'IN_PROGRESS', 28.00, CURRENT_DATE + INTERVAL 10 DAY),
 (43, 11, 1, 'Lay out and print the report', NULL, 'MEDIUM', 'TODO', 20.00, CURRENT_DATE + INTERVAL 40 DAY),
 (44, 11, 14, 'Archive the final report', NULL, 'LOW', 'TODO', 6.00, CURRENT_DATE + INTERVAL 65 DAY),
 (45, 12, 8, 'Set up the book catalogue database', NULL, 'MEDIUM', 'DONE', 24.00, CURRENT_DATE - INTERVAL 25 DAY),
 (46, 12, 12, 'Train the van volunteers', NULL, 'HIGH', 'IN_PROGRESS', 18.00, CURRENT_DATE + INTERVAL 15 DAY),
 (47, 12, 6, 'Recruit and schedule van drivers', NULL, 'MEDIUM', 'TODO', 20.00, CURRENT_DATE + INTERVAL 50 DAY),
 (48, 13, 4, 'Select the twelve schools', NULL, 'HIGH', 'IN_PROGRESS', 20.00, CURRENT_DATE + INTERVAL 30 DAY),
 (49, 13, 15, 'Install filters at the new schools', NULL, 'HIGH', 'TODO', 90.00, CURRENT_DATE + INTERVAL 100 DAY),
 (50, 14, 10, 'Survey volunteer safety needs', NULL, 'MEDIUM', 'DONE', 10.00, CURRENT_DATE - INTERVAL 10 DAY),
 (51, 14, 7, 'Agree course content with the Red Cross', NULL, 'HIGH', 'IN_PROGRESS', 14.00, CURRENT_DATE + INTERVAL 10 DAY),
 (52, 14, 9, 'Design the course handout', NULL, 'LOW', 'TODO', 12.00, CURRENT_DATE + INTERVAL 30 DAY),
 (53, 15, 2, 'Book the venue and the catering', NULL, 'HIGH', 'IN_PROGRESS', 22.00, CURRENT_DATE + INTERVAL 15 DAY),
 (54, 15, 13, 'Check the venue safety certificate', NULL, 'MEDIUM', 'DONE', 6.00, CURRENT_DATE - INTERVAL 5 DAY),
 (55, 15, 1, 'Send gala invitations', NULL, 'MEDIUM', 'TODO', 12.00, CURRENT_DATE + INTERVAL 50 DAY);

-- (dependent_task_id, prerequisite_task_id): the first task waits for the second.
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES
 (2, 1),
 (3, 2),
 (4, 3),
 (5, 4),
 (7, 6),
 (8, 6),
 (9, 6),
 (10, 9),
 (11, 7),
 (11, 8),
 (13, 12),
 (14, 12),
 (15, 14),
 (17, 16),
 (18, 17),
 (19, 18),
 (20, 19),
 (22, 21),
 (23, 21),
 (23, 22),
 (24, 23),
 (25, 23),
 (25, 24),
 (27, 26),
 (28, 26),
 (29, 27),
 (29, 28),
 (32, 31),
 (33, 32),
 (35, 34),
 (36, 34),
 (37, 35),
 (37, 36),
 (39, 38),
 (40, 38),
 (42, 41),
 (43, 42),
 (44, 43),
 (46, 45),
 (47, 46),
 (49, 48),
 (51, 50),
 (52, 51),
 (53, 54),
 (55, 53);

SELECT 'PROJECT' AS table_name, COUNT(*) AS row_count FROM PROJECT
UNION ALL SELECT 'TASK',            COUNT(*) FROM TASK
UNION ALL SELECT 'TASK_DEPENDENCY', COUNT(*) FROM TASK_DEPENDENCY;
