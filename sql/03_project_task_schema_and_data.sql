-- =====================================================================
-- IT079IU - Topic 6: Volunteer/NGO Project and Task Management
-- Module 2: Project & Task (PROJECT, TASK, TASK_DEPENDENCY, LABEL, TASK_LABEL)
--
-- DBMS: MySQL 8.0.16 or later (CHECK enforced, InnoDB).
-- HOW TO RUN: run 01_foundation_schema_and_data.sql FIRST. It creates the
-- database ngo_task_mgmt and the tables VOLUNTEER and TEAM that this module
-- references. This file does NOT contain DROP DATABASE (only the master
-- script may). To re-run this module alone, the DROP TABLE lines
-- below remove only this module's five tables.
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
-- =====================================================================
USE ngo_task_mgmt;

DROP TABLE IF EXISTS TASK_LABEL;        -- child tables first
DROP TABLE IF EXISTS TASK_DEPENDENCY;
DROP TABLE IF EXISTS TASK;
DROP TABLE IF EXISTS PROJECT;
DROP TABLE IF EXISTS LABEL;

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
    estimated_hours  DECIMAL(6,2)  NULL,
    due_date         DATE          NULL,
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
    CONSTRAINT ck_task_hours    CHECK (estimated_hours IS NULL OR estimated_hours > 0)
) ENGINE = InnoDB;

-- Index for: "tasks of a project by status" and "open tasks of a project".
CREATE INDEX idx_task_project_status ON TASK (project_id, status);

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
-- 4. LABEL  (a tag such as 'Fundraising' that can be put on many tasks)
--    label_name is UNIQUE: two labels with the same name would make the
--    tags meaningless. This adds a second candidate key {label_name} and
--    the dependency label_name -> label_id to the normalization document
--    (section II.7); LABEL stays in BCNF because both determinants are
--    candidate keys.
-- ---------------------------------------------------------------------
CREATE TABLE LABEL (
    label_id    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    label_name  VARCHAR(50)   NOT NULL,
    CONSTRAINT pk_label       PRIMARY KEY (label_id),
    CONSTRAINT uq_label_name  UNIQUE (label_name),
    CONSTRAINT ck_label_name  CHECK (CHAR_LENGTH(TRIM(label_name)) > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 5. TASK_LABEL  (resolves the many-to-many relationship TASK - LABEL)
--    Deleting a task or a label removes only the link row (CASCADE); the
--    other side is untouched. A task may have no label, and a label may
--    be unused.
-- ---------------------------------------------------------------------
CREATE TABLE TASK_LABEL (
    task_id   INT UNSIGNED NOT NULL,
    label_id  INT UNSIGNED NOT NULL,
    CONSTRAINT pk_task_label PRIMARY KEY (task_id, label_id),
    CONSTRAINT fk_tasklabel_task FOREIGN KEY (task_id)
        REFERENCES TASK (task_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_tasklabel_label FOREIGN KEY (label_id)
        REFERENCES LABEL (label_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB;
-- The primary key serves "labels of a task"; the foreign key on label_id
-- creates the index for "tasks that carry a label".

-- ---------------------------------------------------------------------
-- Cross-row rules that CHECK cannot express (triggers):
--   R-D  a task cannot depend on itself
--   R-E  dependent and prerequisite task belong to the SAME project
--   R-F  no circular dependency (A needs B, B needs C, C needs A)
--   R-G  a task that has started (IN_PROGRESS/DONE) can only get
--        prerequisites that are already DONE
--   R-H  a task cannot become IN_PROGRESS or DONE until ALL its
--        prerequisites are DONE   ("cannot start until prerequisites are
--        completed"); a CANCELLED prerequisite does not count as completed
--   R-I  a DONE task cannot be re-opened while a dependent task has started
--   R-J  a task that takes part in a dependency cannot move to another
--        project (it would break R-E)
--   R-K  a dependency row is never edited; delete it and insert a new one
-- Scalar sub-selects are used so that an unknown task gives NULL (the
-- foreign key then reports error 1452 as usual).
-- Cascaded foreign-key actions do not fire triggers in MySQL, so deleting
-- or renumbering a project/task is never blocked by these triggers.
-- Limitation: concurrent sessions can bypass R-F (no locking), the same
-- kind of limitation as the one-Leader rule in module 1.
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

    -- R-D
    IF NEW.dependent_task_id = NEW.prerequisite_task_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task cannot depend on itself';
    END IF;

    SET v_dep_project = (SELECT project_id FROM TASK WHERE task_id = NEW.dependent_task_id);
    SET v_pre_project = (SELECT project_id FROM TASK WHERE task_id = NEW.prerequisite_task_id);
    SET v_dep_status  = (SELECT status     FROM TASK WHERE task_id = NEW.dependent_task_id);
    SET v_pre_status  = (SELECT status     FROM TASK WHERE task_id = NEW.prerequisite_task_id);

    -- R-E
    IF v_dep_project <> v_pre_project THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Dependent and prerequisite task must belong to the same project';
    END IF;

    -- R-F: the new edge "dependent needs prerequisite" closes a cycle exactly
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

    -- R-G
    IF v_dep_status IN ('IN_PROGRESS', 'DONE') AND v_pre_status <> 'DONE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task that has already started cannot get an unfinished prerequisite';
    END IF;
END//

CREATE TRIGGER trg_taskdep_bu
BEFORE UPDATE ON TASK_DEPENDENCY
FOR EACH ROW
BEGIN
    -- R-K
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'A dependency cannot be updated; delete it and insert a new one';
END//

CREATE TRIGGER trg_task_bu
BEFORE UPDATE ON TASK
FOR EACH ROW
BEGIN
    -- R-J
    IF NEW.project_id <> OLD.project_id AND EXISTS (
           SELECT 1 FROM TASK_DEPENDENCY
            WHERE dependent_task_id = OLD.task_id OR prerequisite_task_id = OLD.task_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task that has dependencies cannot move to another project';
    END IF;

    -- R-H
    IF NEW.status IN ('IN_PROGRESS', 'DONE') AND EXISTS (
           SELECT 1 FROM TASK_DEPENDENCY d
             JOIN TASK p ON p.task_id = d.prerequisite_task_id
            WHERE d.dependent_task_id = OLD.task_id AND p.status <> 'DONE') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A task cannot start until all its prerequisite tasks are completed';
    END IF;

    -- R-I
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
-- Sample data. Fictional. 6 / 30 / 27 / 10 / 29 rows
-- (PROJECT / TASK / TASK_DEPENDENCY / LABEL / TASK_LABEL).
-- Owners and teams are taken from module 1 (volunteers 1-16, teams 1-15).
-- Rows are inserted so that every trigger rule is already satisfied.
-- =====================================================================

INSERT INTO PROJECT (project_id, owner_id, project_name, description, start_date, due_date) VALUES
 (1,  8, 'Clean Water for Rural Schools',  'Install and maintain water filters in partner schools',        '2024-03-01', '2024-12-15'),
 (2,  6, 'Back-to-School Charity Fair',    'Annual fair that raises funds for school supplies',            '2024-06-01', '2024-09-01'),
 (3,  3, 'Volunteer Onboarding Programme', 'Handbook, orientation and feedback for new volunteers',        '2024-09-01', '2025-01-31'),
 (4, 11, 'Flood Relief Response',          'Emergency supplies and recovery visits after the autumn floods','2024-10-15', '2025-02-28'),
 (5, 15, 'NGO Website Redesign',           'New visual identity and website for the organization',         '2025-01-15', '2025-06-30'),
 (6,  1, 'Community Health Camp',          'Free health check-ups in three communes',                      '2025-02-01', '2025-05-31');

INSERT INTO TASK (task_id, project_id, team_id, title, description, priority, status, estimated_hours, due_date) VALUES
 -- Project 1: Clean Water (a straight chain 1 -> 2 -> 3 -> 4 -> 5)
 ( 1, 1,  4, 'Survey partner schools',             'Visit schools and record water sources',          'HIGH',   'DONE',        24.00, '2024-03-31'),
 ( 2, 1,  5, 'Prepare project budget',             'Costs for equipment, transport and training',     'HIGH',   'DONE',        16.00, '2024-04-15'),
 ( 3, 1,  2, 'Procure filtration equipment',       'Quotes, purchase and storage',                    'HIGH',   'DONE',        40.00, '2024-06-30'),
 ( 4, 1,  2, 'Install water filters at schools',   NULL,                                              'URGENT', 'IN_PROGRESS', 80.00, '2024-10-31'),
 ( 5, 1, 12, 'Train teachers on filter maintenance', NULL,                                            'MEDIUM', 'TODO',        20.00, '2024-11-30'),
 -- Project 2: Charity Fair (6 -> 7, 8, 9; 9 -> 10; 7 and 8 -> 11)
 ( 6, 2,  4, 'Plan fair programme and schedule',   NULL,                                              'HIGH',   'DONE',        12.00, '2024-06-20'),
 ( 7, 2,  7, 'Secure venue permit from the district', NULL,                                           'HIGH',   'DONE',        10.00, '2024-07-10'),
 ( 8, 2,  3, 'Recruit sponsors for booths',        NULL,                                              'MEDIUM', 'DONE',        30.00, '2024-07-31'),
 ( 9, 2,  9, 'Design posters and banners',         NULL,                                              'MEDIUM', 'DONE',        18.00, '2024-07-25'),
 (10, 2,  1, 'Promote the fair on social media',   NULL,                                              'MEDIUM', 'DONE',        25.00, '2024-08-25'),
 (11, 2,  2, 'Set up booths on event day',         NULL,                                              'HIGH',   'DONE',        36.00, '2024-08-31'),
 -- Project 3: Onboarding
 (12, 3,  6, 'Draft volunteer handbook',           NULL,                                              'MEDIUM', 'DONE',        20.00, '2024-10-15'),
 (13, 3, 14, 'Review and archive the handbook',    NULL,                                              'LOW',    'DONE',         8.00, '2024-10-31'),
 (14, 3, 12, 'Run orientation workshop',           NULL,                                              'HIGH',   'DONE',        16.00, '2024-11-20'),
 (15, 3, 10, 'Collect new-volunteer feedback',     NULL,                                              'LOW',    'IN_PROGRESS', 10.00, '2025-01-15'),
 -- Project 4: Flood Relief (chain 16 -> 17 -> 18 -> 19 -> 20)
 (16, 4, 13, 'Assess flood risk in affected communes', NULL,                                          'URGENT', 'DONE',        20.00, '2024-10-25'),
 (17, 4, 11, 'Coordinate relief supplies with partners', NULL,                                        'HIGH',   'DONE',        24.00, '2024-11-05'),
 (18, 4,  2, 'Deliver relief packages',            NULL,                                              'URGENT', 'DONE',        60.00, '2024-11-20'),
 (19, 4, 15, 'Run post-flood recovery visits',     NULL,                                              'MEDIUM', 'IN_PROGRESS', 50.00, '2025-01-31'),
 (20, 4, 14, 'Write the final relief report',      NULL,                                              'LOW',    'TODO',        15.00, '2025-02-20'),
 -- Project 5: Website (21 -> 22 -> 23 -> 24, 25)
 (21, 5,  8, 'Audit the current website',          NULL,                                              'MEDIUM', 'DONE',        12.00, '2025-01-31'),
 (22, 5,  9, 'Create the new visual identity',     NULL,                                              'HIGH',   'IN_PROGRESS', 30.00, '2025-03-15'),
 (23, 5,  8, 'Build the new website pages',        NULL,                                              'HIGH',   'TODO',        60.00, '2025-05-15'),
 (24, 5, 14, 'Migrate content and reports',        NULL,                                              'MEDIUM', 'TODO',        25.00, '2025-05-31'),
 (25, 5,  1, 'Announce the new website',           NULL,                                              'LOW',    'TODO',         8.00, '2025-06-20'),
 -- Project 6: Health Camp (26 -> 27, 28 -> 29); task 30 has no estimate and no due date
 (26, 6,  4, 'Plan health camp locations',         NULL,                                              'HIGH',   'IN_PROGRESS', 16.00, '2025-03-10'),
 (27, 6, 13, 'Prepare first-aid and safety plan',  NULL,                                              'HIGH',   'TODO',        20.00, '2025-04-10'),
 (28, 6,  3, 'Raise funds for medical supplies',   NULL,                                              'MEDIUM', 'TODO',        40.00, '2025-04-20'),
 (29, 6,  2, 'Transport equipment to camp sites',  NULL,                                              'HIGH',   'TODO',        30.00, '2025-05-20'),
 (30, 6,  1, 'Print flyers',                       'Replaced by digital posters',                     'LOW',    'CANCELLED',   NULL,  NULL);

-- (dependent_task_id, prerequisite_task_id): the first task waits for the second.
INSERT INTO TASK_DEPENDENCY (dependent_task_id, prerequisite_task_id) VALUES
 ( 2,  1), ( 3,  2), ( 4,  3), ( 5,  4),                                   -- project 1
 ( 7,  6), ( 8,  6), ( 9,  6), (10,  9), (11,  7), (11,  8),               -- project 2
 (13, 12), (14, 12), (15, 14),                                             -- project 3
 (17, 16), (18, 17), (19, 18), (20, 19),                                   -- project 4
 (22, 21), (23, 21), (23, 22), (24, 23), (25, 23), (25, 24),               -- project 5
 (27, 26), (28, 26), (29, 27), (29, 28);                                   -- project 6

INSERT INTO LABEL (label_id, label_name) VALUES
 ( 1, 'Fieldwork'),
 ( 2, 'Fundraising'),
 ( 3, 'Training'),
 ( 4, 'Logistics'),
 ( 5, 'Communication'),
 ( 6, 'Documentation'),
 ( 7, 'Safety'),
 ( 8, 'Design'),
 ( 9, 'Finance'),
 (10, 'Planning');

INSERT INTO TASK_LABEL (task_id, label_id) VALUES
 ( 1,  1), ( 2,  9), ( 3,  4), ( 4,  1), ( 4,  4), ( 5,  3),          -- project 1
 ( 6, 10), ( 7,  4), ( 8,  2), ( 8,  9), ( 9,  8), (10,  5), (11,  4), -- project 2
 (12,  6), (14,  3), (15,  5),                                         -- project 3
 (16,  7), (16,  1), (17,  4), (18,  4), (19,  1), (20,  6),           -- project 4
 (22,  8), (24,  6), (25,  5),                                         -- project 5 (task 23 has no label)
 (26, 10), (27,  7), (28,  2), (29,  4);                               -- project 6 (task 30 has no label)

SELECT 'PROJECT' AS table_name, COUNT(*) AS row_count FROM PROJECT
UNION ALL SELECT 'TASK',            COUNT(*) FROM TASK
UNION ALL SELECT 'TASK_DEPENDENCY', COUNT(*) FROM TASK_DEPENDENCY
UNION ALL SELECT 'LABEL',           COUNT(*) FROM LABEL
UNION ALL SELECT 'TASK_LABEL',      COUNT(*) FROM TASK_LABEL;
