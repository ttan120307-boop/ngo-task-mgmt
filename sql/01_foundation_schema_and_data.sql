-- =====================================================================
-- IT079IU - Topic 6: Volunteer/NGO Project and Task Management
-- Module 1: Foundation (VOLUNTEER, VOLUNTEER_PHONE, TEAM, TEAM_ROLE, MEMBERSHIP)
--
-- DBMS: MySQL 8.0.16 or later (tested requirements: CHECK enforced, InnoDB).
-- MySQL-specific features used in this file (not standard SQL):
--   * DEFAULT (CURRENT_DATE)  expression default          (needs 8.0.13+)
--   * GENERATED ALWAYS ... STORED column (is_current)
--   * REGEXP in CHECK constraints
--   * triggers with SIGNAL SQLSTATE '45000'  (MySQL error 1644)
--   * DELIMITER (client command, used by mysql CLI and MySQL Workbench)
--
-- NOTE FOR INTEGRATION: only the MASTER script may contain DROP DATABASE.
-- When the modules are merged, keep this file's table definitions and
-- triggers, and use the same type INT UNSIGNED for every foreign key that
-- points to volunteer_id or team_id.
-- =====================================================================
DROP DATABASE IF EXISTS ngo_task_mgmt;
CREATE DATABASE ngo_task_mgmt CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ngo_task_mgmt;

-- ---------------------------------------------------------------------
-- 1. VOLUNTEER  (the "users" table)
-- ---------------------------------------------------------------------
CREATE TABLE VOLUNTEER (
    volunteer_id    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    full_name       VARCHAR(100)  NOT NULL,
    email           VARCHAR(150)  NOT NULL,
    registered_date DATE          NOT NULL DEFAULT (CURRENT_DATE),
    system_role     VARCHAR(10)   NOT NULL DEFAULT 'MEMBER',
    CONSTRAINT pk_volunteer        PRIMARY KEY (volunteer_id),
    CONSTRAINT uq_volunteer_email  UNIQUE (email),
    CONSTRAINT ck_volunteer_role   CHECK (system_role IN ('DIRECTOR', 'MEMBER')),
    CONSTRAINT ck_volunteer_name   CHECK (CHAR_LENGTH(TRIM(full_name)) > 0),
    -- stricter than LIKE '%_@_%._%': rejects 'a@@b.c', 'a@b..c', 'a b@c.org'
    CONSTRAINT ck_volunteer_email  CHECK (email REGEXP
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)*[.][A-Za-z]{2,}$')
) ENGINE = InnoDB;

-- Multivalued attribute: one volunteer, several phone numbers.
-- A phone number belongs to exactly ONE volunteer (business rule 17), so
-- phone_number alone is the primary key (FD: phone_number -> volunteer_id).
CREATE TABLE VOLUNTEER_PHONE (
    phone_number  VARCHAR(11)  NOT NULL,
    volunteer_id  INT UNSIGNED NOT NULL,
    CONSTRAINT pk_volunteer_phone PRIMARY KEY (phone_number),
    CONSTRAINT fk_phone_volunteer FOREIGN KEY (volunteer_id)
        REFERENCES VOLUNTEER (volunteer_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_phone_format    CHECK (phone_number REGEXP '^[0-9]{9,11}$')
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. TEAM  (specialist departments of the whole organization)
-- ---------------------------------------------------------------------
CREATE TABLE TEAM (
    team_id       INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    team_name     VARCHAR(80)   NOT NULL,
    description   VARCHAR(255)  NULL,
    created_date  DATE          NOT NULL DEFAULT (CURRENT_DATE),
    CONSTRAINT pk_team       PRIMARY KEY (team_id),
    CONSTRAINT uq_team_name  UNIQUE (team_name),
    CONSTRAINT ck_team_name  CHECK (CHAR_LENGTH(TRIM(team_name)) > 0)
) ENGINE = InnoDB;

-- Lookup table for the role a volunteer plays inside a team (BR2).
-- The role named 'Leader' has a special meaning in trigger trg_membership_bi/bu.
CREATE TABLE TEAM_ROLE (
    team_role_id  INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    role_name     VARCHAR(50)   NOT NULL,
    CONSTRAINT pk_team_role       PRIMARY KEY (team_role_id),
    CONSTRAINT uq_team_role_name  UNIQUE (role_name)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. MEMBERSHIP  (the junction table "team_members")
--    BR1: many teams per volunteer, but only ONE current membership per
--    (volunteer, team). is_current is 1 while left_date IS NULL, otherwise
--    NULL. UNIQUE ignores NULLs, so ended memberships never collide.
-- ---------------------------------------------------------------------
CREATE TABLE MEMBERSHIP (
    membership_id  INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    volunteer_id   INT UNSIGNED  NOT NULL,
    team_id        INT UNSIGNED  NOT NULL,
    team_role_id   INT UNSIGNED  NOT NULL,
    joined_date    DATE          NOT NULL DEFAULT (CURRENT_DATE),
    left_date      DATE          NULL,
    is_current     TINYINT GENERATED ALWAYS AS (CASE WHEN left_date IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT pk_membership PRIMARY KEY (membership_id),
    CONSTRAINT uq_membership_one_current UNIQUE (volunteer_id, team_id, is_current),
    CONSTRAINT uq_membership_same_day    UNIQUE (volunteer_id, team_id, joined_date),
    CONSTRAINT ck_membership_dates CHECK (left_date IS NULL OR left_date >= joined_date),
    CONSTRAINT fk_membership_volunteer FOREIGN KEY (volunteer_id)
        REFERENCES VOLUNTEER (volunteer_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_membership_team FOREIGN KEY (team_id)
        REFERENCES TEAM (team_id)           ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_membership_role FOREIGN KEY (team_role_id)
        REFERENCES TEAM_ROLE (team_role_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB;

-- Index for: "current members of a team" and "teams with at least 3 current
-- members" (filter on team_id and left_date IS NULL, then count volunteers).
CREATE INDEX idx_membership_team_current ON MEMBERSHIP (team_id, left_date, volunteer_id);

-- ---------------------------------------------------------------------
-- Cross-table rules that CHECK cannot express (triggers):
--   R-A  a team has at most ONE current Leader
--   R-B  joined_date >= volunteer.registered_date
--   R-C  joined_date >= team.created_date
-- Scalar sub-selects are used so that an unknown volunteer/team gives NULL
-- (the foreign key then reports error 1452 as usual).
-- Limitation: the dates of VOLUNTEER/TEAM are not re-checked if they are
-- changed later (documented in the Discussion section of the report).
-- ---------------------------------------------------------------------
DELIMITER //

CREATE TRIGGER trg_membership_bi
BEFORE INSERT ON MEMBERSHIP
FOR EACH ROW
BEGIN
    DECLARE v_registered   DATE;
    DECLARE v_team_created DATE;
    DECLARE v_is_leader    INT DEFAULT 0;

    SET v_registered   = (SELECT registered_date FROM VOLUNTEER WHERE volunteer_id = NEW.volunteer_id);
    SET v_team_created = (SELECT created_date    FROM TEAM      WHERE team_id      = NEW.team_id);

    IF NEW.joined_date < v_registered THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Joined date is before the volunteer registered date';
    END IF;
    IF NEW.joined_date < v_team_created THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Joined date is before the team was created';
    END IF;

    SET v_is_leader = (SELECT COUNT(*) FROM TEAM_ROLE
                        WHERE team_role_id = NEW.team_role_id AND role_name = 'Leader');
    IF v_is_leader = 1 AND NEW.left_date IS NULL AND EXISTS (
           SELECT 1 FROM MEMBERSHIP
            WHERE team_id = NEW.team_id AND team_role_id = NEW.team_role_id AND left_date IS NULL) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'This team already has a current Leader';
    END IF;
END//

CREATE TRIGGER trg_membership_bu
BEFORE UPDATE ON MEMBERSHIP
FOR EACH ROW
BEGIN
    DECLARE v_registered   DATE;
    DECLARE v_team_created DATE;
    DECLARE v_is_leader    INT DEFAULT 0;

    SET v_registered   = (SELECT registered_date FROM VOLUNTEER WHERE volunteer_id = NEW.volunteer_id);
    SET v_team_created = (SELECT created_date    FROM TEAM      WHERE team_id      = NEW.team_id);

    IF NEW.joined_date < v_registered THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Joined date is before the volunteer registered date';
    END IF;
    IF NEW.joined_date < v_team_created THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Joined date is before the team was created';
    END IF;

    SET v_is_leader = (SELECT COUNT(*) FROM TEAM_ROLE
                        WHERE team_role_id = NEW.team_role_id AND role_name = 'Leader');
    IF v_is_leader = 1 AND NEW.left_date IS NULL AND EXISTS (
           SELECT 1 FROM MEMBERSHIP
            WHERE team_id = NEW.team_id AND team_role_id = NEW.team_role_id AND left_date IS NULL
              AND membership_id <> NEW.membership_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'This team already has a current Leader';
    END IF;
END//

DELIMITER ;

-- =====================================================================
-- Sample data. Fictional people. 16 / 21 / 15 / 15 / 68 rows.
-- =====================================================================

INSERT INTO VOLUNTEER (volunteer_id, full_name, email, registered_date, system_role) VALUES
 ( 1, 'Nguyễn Minh Anh',   'minhanh.nguyen@ngo-demo.org',  '2023-01-10', 'DIRECTOR'),
 ( 2, 'Trần Quốc Bảo',     'quocbao.tran@ngo-demo.org',    '2023-01-15', 'MEMBER'),
 ( 3, 'Lê Thu Hà',         'thuha.le@ngo-demo.org',        '2023-02-01', 'MEMBER'),
 ( 4, 'Phạm Gia Huy',      'giahuy.pham@ngo-demo.org',     '2023-02-20', 'MEMBER'),
 ( 5, 'Võ Thanh Lam',      'thanhlam.vo@ngo-demo.org',     '2023-03-05', 'MEMBER'),
 ( 6, 'Đặng Khánh Linh',   'khanhlinh.dang@ngo-demo.org',  '2023-03-18', 'MEMBER'),
 ( 7, 'Bùi Anh Khoa',      'anhkhoa.bui@ngo-demo.org',     '2023-05-02', 'MEMBER'),
 ( 8, 'Hoàng Mai Phương',  'maiphuong.hoang@ngo-demo.org', '2023-06-12', 'MEMBER'),
 ( 9, 'Ngô Đức Thịnh',     'ducthinh.ngo@ngo-demo.org',    '2023-09-01', 'MEMBER'),
 (10, 'Đỗ Bảo Ngọc',       'baongoc.do@ngo-demo.org',      '2023-09-15', 'MEMBER'),
 (11, 'Dương Tuấn Kiệt',   'tuankiet.duong@ngo-demo.org',  '2024-01-08', 'MEMBER'),
 (12, 'Lý Hải Yến',        'haiyen.ly@ngo-demo.org',       '2024-02-19', 'MEMBER'),
 (13, 'Phan Nhật Nam',     'nhatnam.phan@ngo-demo.org',    '2024-03-10', 'MEMBER'),
 (14, 'Vũ Thùy Trang',     'thuytrang.vu@ngo-demo.org',    '2024-06-01', 'MEMBER'),
 (15, 'Trịnh Hoàng Long',  'hoanglong.trinh@ngo-demo.org', '2024-09-09', 'MEMBER'),
 (16, 'Huỳnh Ngọc Hân',    'ngochan.huynh@ngo-demo.org',   '2025-01-20', 'MEMBER');

INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES
 ('0901000001', 1), ('02838000001', 1),
 ('0901000002', 2), ('0911000002', 2),
 ('0901000003', 3),
 ('0901000004', 4),
 ('0901000005', 5), ('0911000005', 5),
 ('0901000006', 6),
 ('0901000007', 7),
 ('0901000008', 8),
 ('0901000009', 9), ('0911000009', 9),
 ('0901000010', 10),
 ('0901000011', 11),
 ('0901000012', 12),
 ('0901000013', 13), ('0911000013', 13),
 ('0901000014', 14),
 ('0901000015', 15),
 ('0901000016', 16);

INSERT INTO TEAM (team_id, team_name, description, created_date) VALUES
 ( 1, 'Media and Communications', 'Posts, press releases and social media of every campaign',   '2023-01-20'),
 ( 2, 'Logistics',                'Transport, equipment and venue set-up',                      '2023-01-20'),
 ( 3, 'Fundraising',              'Donations, sponsors and charity sales',                      '2023-01-20'),
 ( 4, 'Programme Planning',       'Campaign planning and schedules',                            '2023-02-01'),
 ( 5, 'Finance',                  'Budgets, receipts and expense reports',                      '2023-02-01'),
 ( 6, 'Human Resources',          'Recruiting and organizing volunteers',                       '2023-02-15'),
 ( 7, 'External Relations',       'Contact with schools, local authorities and media',          '2023-03-01'),
 ( 8, 'Information Technology',   'Tools, accounts and data of the organization',               '2023-09-10'),
 ( 9, 'Graphic Design',           'Posters, banners and slides',                                '2023-09-10'),
 (10, 'Volunteer Care',           'Welfare and motivation of volunteers during campaigns',      '2024-01-15'),
 (11, 'Partnerships',             'Long-term partners and sponsors',                            '2024-02-01'),
 (12, 'Training',                 'Skill workshops for new volunteers',                         '2024-02-10'),
 (13, 'Safety and Security',      'Safety plans and first aid at events',                       '2023-12-01'),
 (14, 'Documentation',            'Minutes, reports and archive',                               '2024-03-15'),
 (15, 'Field Operations',         'On-site work in the communities we serve',                   '2024-12-01');

INSERT INTO TEAM_ROLE (team_role_id, role_name) VALUES
 ( 1, 'Leader'),         ( 2, 'Deputy Leader'), ( 3, 'Member'),
 ( 4, 'Coordinator'),    ( 5, 'Reviewer'),      ( 6, 'Treasurer'),
 ( 7, 'Secretary'),      ( 8, 'Trainer'),       ( 9, 'Mentor'),
 (10, 'Designer'),       (11, 'Content Writer'), (12, 'Photographer'),
 (13, 'Logistics Officer'), (14, 'Fundraiser'), (15, 'Intern');

-- History is kept: a row with left_date is a past membership; a row without it is current.
-- Rows 1-23 are the original data; rows 24-68 give every team 4 current members
-- and exactly one current Leader, plus a few past memberships.
INSERT INTO MEMBERSHIP (membership_id, volunteer_id, team_id, team_role_id, joined_date, left_date) VALUES
 ( 1,  2,  1,  1, '2023-02-01', NULL),          -- Bảo leads Media
 ( 2,  3,  1, 11, '2023-02-10', '2023-12-31'),  -- Hà wrote for Media, then left ...
 ( 3,  3,  1, 11, '2024-03-01', NULL),          -- ... and re-joined: a NEW row
 ( 4,  4,  2,  1, '2023-03-01', NULL),          -- Huy leads Logistics
 ( 5,  5,  2, 13, '2023-03-20', '2024-05-31'),  -- Lam left Logistics ...
 ( 6,  5,  3, 14, '2024-06-01', NULL),          -- ... and moved to Fundraising
 ( 7,  6,  3,  1, '2023-04-01', NULL),          -- Linh leads Fundraising
 ( 8,  7,  5,  6, '2023-06-01', NULL),          -- Khoa is Treasurer of Finance
 ( 9,  8,  4,  1, '2023-07-01', NULL),          -- Phương leads Programme Planning
 (10,  9,  8,  3, '2023-10-01', NULL),          -- Thịnh in IT ...
 (11, 10,  9, 10, '2023-10-05', NULL),          -- Ngọc designs
 (12, 10,  1, 12, '2023-10-20', NULL),          -- ... and photographs for Media (two teams at once)
 (13, 11, 10,  1, '2024-02-01', NULL),          -- Kiệt leads Volunteer Care
 (14, 12, 12,  8, '2024-03-01', NULL),          -- Yến trains volunteers
 (15, 13,  2,  3, '2024-04-01', '2024-12-31'),  -- Nam left Logistics ...
 (16, 13, 15,  3, '2025-01-05', NULL),          -- ... and joined Field Operations
 (17, 14,  6,  7, '2024-07-01', NULL),          -- Trang is Secretary of HR
 (18, 15, 14, 15, '2024-10-01', NULL),          -- Long is an intern in Documentation
 (19, 16,  1, 15, '2025-02-01', NULL),          -- Hân is an intern in Media
 (20,  9, 13,  5, '2024-01-15', NULL),          -- Thịnh also reviews safety plans
 (21, 12, 11,  9, '2024-05-10', NULL),          -- Yến mentors in Partnerships
 (22,  2,  7,  2, '2023-08-01', NULL),          -- Bảo is deputy in External Relations
 (23,  6,  4,  5, '2024-02-01', '2024-08-31'),  -- Linh reviewed Programme plans for a while
 -- Logistics (leader: row 4)
 (24,  7,  2, 13, '2024-06-01', NULL),
 (25, 14,  2,  3, '2024-08-01', NULL),
 (26,  9,  2,  4, '2024-02-01', NULL),
 -- Fundraising (leader: row 7)
 (27,  1,  3,  5, '2023-06-01', NULL),
 (28, 12,  3, 14, '2024-04-01', NULL),
 -- Programme Planning (leader: row 9)
 (29,  1,  4,  2, '2023-03-01', NULL),
 (30,  3,  4,  3, '2023-09-01', NULL),
 (31, 15,  4, 15, '2024-10-15', NULL),
 -- Finance (leader: row 32)
 (32,  1,  5,  1, '2023-03-01', NULL),
 (33,  6,  5,  3, '2023-06-01', NULL),
 (34, 11,  5,  3, '2024-03-01', NULL),
 -- Human Resources (leader: row 35)
 (35,  3,  6,  1, '2023-03-01', NULL),
 (36, 16,  6,  3, '2025-02-10', NULL),
 (37,  8,  6,  4, '2024-01-05', NULL),
 -- External Relations (leader: row 38)
 (38,  1,  7,  1, '2023-03-15', NULL),
 (39,  9,  7,  3, '2023-10-01', NULL),
 (40, 14,  7,  3, '2024-09-01', NULL),
 -- Information Technology (leader: row 41)
 (41, 15,  8,  1, '2024-10-10', NULL),
 (42, 13,  8,  3, '2024-05-01', NULL),
 (43, 11,  8,  3, '2024-03-01', NULL),
 -- Graphic Design (leader: row 44)
 (44,  5,  9,  1, '2023-10-01', NULL),
 (45, 16,  9, 10, '2025-02-15', NULL),
 (46, 14,  9, 10, '2024-07-10', NULL),
 -- Volunteer Care (leader: row 13)
 (47,  6, 10,  3, '2024-03-01', NULL),
 (48,  8, 10,  9, '2024-03-15', NULL),
 (49, 16, 10,  3, '2025-02-20', NULL),
 -- Partnerships (leader: row 50)
 (50, 13, 11,  1, '2025-01-10', NULL),
 (51,  2, 11,  3, '2024-03-01', NULL),
 (52,  4, 11,  4, '2024-04-01', NULL),
 -- Training (leader: row 53)
 (53,  3, 12,  1, '2024-03-01', NULL),
 (54, 10, 12,  8, '2024-04-01', NULL),
 (55, 15, 12,  3, '2024-11-01', NULL),
 -- Safety and Security (leader: row 56)
 (56,  4, 13,  1, '2024-01-10', NULL),
 (57, 13, 13,  3, '2024-06-01', NULL),
 (58,  7, 13,  3, '2024-02-01', NULL),
 -- Documentation (leader: row 59)
 (59, 14, 14,  1, '2024-07-15', NULL),
 (60,  3, 14,  7, '2024-04-01', NULL),
 (61,  5, 14,  3, '2024-05-01', NULL),
 -- Field Operations (leader: row 62)
 (62, 11, 15,  1, '2025-01-10', NULL),
 (63, 16, 15,  3, '2025-02-05', NULL),
 (64, 15, 15,  3, '2025-01-20', NULL),
 -- Past memberships (left_date set)
 (65, 11,  3, 14, '2024-02-01', '2024-09-30'),
 (66, 14,  1, 12, '2024-07-01', '2025-01-31'),
 (67, 12,  6,  3, '2024-03-01', '2024-12-31'),
 (68,  7,  3,  3, '2023-07-01', '2024-01-31');

SELECT 'VOLUNTEER' AS table_name, COUNT(*) AS row_count FROM VOLUNTEER
UNION ALL SELECT 'VOLUNTEER_PHONE', COUNT(*) FROM VOLUNTEER_PHONE
UNION ALL SELECT 'TEAM',            COUNT(*) FROM TEAM
UNION ALL SELECT 'TEAM_ROLE',       COUNT(*) FROM TEAM_ROLE
UNION ALL SELECT 'MEMBERSHIP',      COUNT(*) FROM MEMBERSHIP;
