-- =====================================================================
-- IT079IU - Topic 6: Volunteer/NGO Project and Task Management
-- Module 5: Accounts for the log-in of the Java application (ACCOUNT)
--
-- RUN ORDER: 01 -> 03 -> 05 -> 07 -> 09 (this file). Requires MySQL 8.0.16+ and VOLUNTEER (module 01).
-- It does not contain DROP DATABASE.
--
-- One account per volunteer (1:1, weak-like table: its key is the volunteer's key).
--   volunteer_id -> username, salt, password_hash, is_active, created_at, last_login   (primary key)
--   username     -> volunteer_id                                                        (UNIQUE)
--   => both determinants are keys, the relation is in BCNF.
-- The password is NEVER stored: password_hash = SHA-256(salt || password), 64 hex characters,
-- computed by the application (and by SHA2() in the sample data below). The application level
-- of a volunteer (ADMIN / MANAGER / USER) is NOT stored: it is derived at log-in from data that
-- already exists (VOLUNTEER.system_role, PROJECT.owner_id, MEMBERSHIP + TEAM_ROLE), so it can never
-- disagree with them:
--   ADMIN   = VOLUNTEER.system_role = 'DIRECTOR'                    (Organization Director)
--   MANAGER = owns a project OR is the current Leader of a team    (Project Owner / Team Leader)
--   USER    = every other active volunteer                          (Team Member)
-- BR25: one account per volunteer, username unique (case-insensitive).
-- BR26: only an active account (is_active = 1) can log in.
-- BR27: permissions follow the application level; they are enforced by the Java application
--       (ngo.auth.Authz), NOT by MySQL: see the limits in README_SQL.md.
-- =====================================================================
USE ngo_task_mgmt;

DROP TABLE IF EXISTS ACCOUNT;

CREATE TABLE ACCOUNT (
    volunteer_id   INT UNSIGNED  NOT NULL,
    username       VARCHAR(60)   NOT NULL,
    salt           CHAR(32)      NOT NULL,
    password_hash  CHAR(64)      NOT NULL,
    is_active      TINYINT       NOT NULL DEFAULT 1,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login     DATETIME      NULL,
    CONSTRAINT pk_account          PRIMARY KEY (volunteer_id),
    CONSTRAINT uq_account_username UNIQUE (username),
    CONSTRAINT fk_account_volunteer FOREIGN KEY (volunteer_id)
        REFERENCES VOLUNTEER (volunteer_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_account_username CHECK (username REGEXP '^[A-Za-z0-9._-]{3,60}$'),
    CONSTRAINT ck_account_salt     CHECK (salt REGEXP '^[0-9a-f]{32}$'),
    CONSTRAINT ck_account_hash     CHECK (password_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_active   CHECK (is_active IN (0, 1))
) ENGINE = InnoDB;

-- =====================================================================
-- Sample data: 16 accounts, one per volunteer. DEMO ONLY: every sample account has the
-- password  Ngo@2026  (change it in a real installation). username = e-mail before the '@'.
--   admin    : minhanh.nguyen  (Director)
--   managers : e.g. phuong (owns projects), team leaders such as quocbao.tran
--   users    : the other volunteers
-- Volunteer 16 is created INACTIVE to demonstrate BR26.
-- =====================================================================
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash, is_active)
SELECT v.volunteer_id,
       SUBSTRING_INDEX(v.email, '@', 1),
       MD5(CONCAT('ngo-demo-salt-', v.volunteer_id)),
       SHA2(CONCAT(MD5(CONCAT('ngo-demo-salt-', v.volunteer_id)), 'Ngo@2026'), 256),
       1
FROM VOLUNTEER v
ORDER BY v.volunteer_id;

UPDATE ACCOUNT SET is_active = 0 WHERE volunteer_id = 16;

SELECT 'ACCOUNT' AS table_name, COUNT(*) AS row_count FROM ACCOUNT;
