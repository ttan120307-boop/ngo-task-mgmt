-- =====================================================================
-- Constraint tests for the Foundation module.
-- HOW TO RUN: run ngo_full_schema_and_data.sql (or 01_foundation_schema_and_data.sql) first. Part A must be run
-- with "continue on error" (mysql --force, or Workbench: Query > Continue on
-- SQL script errors) because every statement in part A is SUPPOSED to fail.
-- Failed INSERTs still consume AUTO_INCREMENT values; this is harmless.
-- These tests depend on the sample data (volunteer 2 leads team 1, etc.).
-- Record the real error message of each test in the report (Test | Statement |
-- Expected | Actual | Screenshot).
-- =====================================================================
USE ngo_task_mgmt;

-- ------------------------- A. statements that must FAIL -------------------------

-- A1  Duplicate e-mail (UNIQUE).
--     Expected: ERROR 1062 (uq_volunteer_email)
INSERT INTO VOLUNTEER (full_name, email) VALUES ('Test User', 'minhanh.nguyen@ngo-demo.org');

-- A2  NULL e-mail (NOT NULL).
--     Expected: ERROR 1048 (Column 'email' cannot be null)
INSERT INTO VOLUNTEER (full_name, email) VALUES ('Test User', NULL);

-- A3  System role outside DIRECTOR/MEMBER (CHECK).
--     Expected: ERROR 3819 (ck_volunteer_role)
INSERT INTO VOLUNTEER (full_name, email, system_role) VALUES ('Test User', 'test1@ngo-demo.org', 'ADMIN');

-- A4  Malformed e-mail, no @ (CHECK).
--     Expected: ERROR 3819 (ck_volunteer_email)
INSERT INTO VOLUNTEER (full_name, email) VALUES ('Test User', 'not-an-email');

-- A4b Malformed e-mail, double @ (CHECK, the stricter regex).
--     Expected: ERROR 3819 (ck_volunteer_email)
INSERT INTO VOLUNTEER (full_name, email) VALUES ('Test User', 'a@@b.org');

-- A4c Empty volunteer name (CHECK).
--     Expected: ERROR 3819 (ck_volunteer_name)
INSERT INTO VOLUNTEER (full_name, email) VALUES ('   ', 'blank.name@ngo-demo.org');

-- A5  Phone with letters (CHECK).
--     Expected: ERROR 3819 (ck_phone_format)
INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES ('09ABC00001', 1);

-- A6  Same phone twice for one volunteer (PRIMARY KEY).
--     Expected: ERROR 1062 (PRIMARY)
INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES ('0901000001', 1);

-- A6b The same phone given to ANOTHER volunteer (BR16, PRIMARY KEY).
--     Expected: ERROR 1062 (PRIMARY)
INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES ('0901000001', 2);

-- A7  Phone for a volunteer that does not exist (FK).
--     Expected: ERROR 1452 (fk_phone_volunteer)
INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES ('0909999999', 999);

-- A8  Duplicate team name (UNIQUE).
--     Expected: ERROR 1062 (uq_team_name)
INSERT INTO TEAM (team_name) VALUES ('Logistics');

-- A8b Empty team name (CHECK).
--     Expected: ERROR 3819 (ck_team_name)
INSERT INTO TEAM (team_name) VALUES ('   ');

-- A9  Duplicate role name (UNIQUE).
--     Expected: ERROR 1062 (uq_team_role_name)
INSERT INTO TEAM_ROLE (role_name) VALUES ('Treasurer');

-- A10 Membership of an unknown volunteer / team / role (FK).
--     Expected: ERROR 1452 for each of the three statements
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (999, 1, 3, '2025-03-01');
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (1, 999, 3, '2025-03-01');
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (1, 1, 999, '2025-03-01');

-- A11 NULL role in a membership (BR2, NOT NULL).
--     Expected: ERROR 1048 (Column 'team_role_id' cannot be null)
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (1, 1, NULL, '2025-03-01');

-- A12 Second CURRENT membership of volunteer 2 in team 1 (BR1).
--     Expected: ERROR 1062 (uq_membership_one_current)
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (2, 1, 3, '2025-03-01');

-- A13 Left date before joined date (CHECK).
--     Expected: ERROR 3819 (ck_membership_dates)
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date, left_date)
VALUES (1, 2, 3, '2025-03-01', '2025-02-01');

-- A14 Two rows for the same volunteer, team and join day.
--     Expected: ERROR 1062 (uq_membership_same_day)
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date, left_date)
VALUES (3, 1, 3, '2023-02-10', '2023-03-01');

-- A15 Delete a volunteer who still has memberships (ON DELETE RESTRICT).
--     Expected: ERROR 1451
DELETE FROM VOLUNTEER WHERE volunteer_id = 2;

-- A16 Delete a team that still has memberships (RESTRICT).
--     Expected: ERROR 1451
DELETE FROM TEAM WHERE team_id = 1;

-- A17 Delete a role that is still in use (RESTRICT).
--     Expected: ERROR 1451
DELETE FROM TEAM_ROLE WHERE team_role_id = 1;

-- A18 Second current Leader in team 1 (rule BR17, declared key uq_membership_one_leader).
--     Volunteer 5 is not in team 1, so only this key can reject the row.
--     Expected: ERROR 1062 (Duplicate entry '1-1' for key 'uq_membership_one_leader')
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (5, 1, 1, '2025-03-01');

-- A19 Joining before the volunteer registered (trigger).
--     Volunteer 16 registered on 2025-01-20.
--     Expected: ERROR 1644 ('Joined date is before the volunteer registered date')
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (16, 2, 3, '2024-12-01');

-- A20 Joining before the team was created (trigger).
--     Team 15 was created on 2024-12-01.
--     Expected: ERROR 1644 ('Joined date is before the team was created')
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (1, 15, 3, '2024-11-01');

-- A21 UPDATE a foreign key to a team that does not exist.
--     Expected: ERROR 1452 (fk_membership_team); row 1 keeps team 1
UPDATE MEMBERSHIP SET team_id = 999 WHERE membership_id = 1;

-- A22 UPDATE that would reopen an old membership while a current one exists
--     (row 2 is the ended membership of volunteer 3 in team 1; row 3 is current).
--     Expected: ERROR 1062 (uq_membership_one_current)
UPDATE MEMBERSHIP SET left_date = NULL WHERE membership_id = 2;

-- A23 UPDATE that promotes a second Leader in team 1 (rule BR17).
--     Expected: ERROR 1062 (uq_membership_one_leader)
UPDATE MEMBERSHIP SET team_role_id = 1 WHERE membership_id = 3;

-- A24 The role named 'Leader' must keep id 1 (the leader_flag column tests team_role_id = 1).
--     Expected: ERROR 1644 ('Role id 1 is reserved for the role Leader, and Leader must have id 1')
UPDATE TEAM_ROLE SET role_name = 'Chief' WHERE team_role_id = 1;

-- A25 Another role cannot take id 1, and Leader cannot move to another id.
--     Expected: ERROR 1644 for each of the two statements
INSERT INTO TEAM_ROLE (team_role_id, role_name) VALUES (1, 'Second Leader');
UPDATE TEAM_ROLE SET team_role_id = 99 WHERE team_role_id = 1;

-- ------------------------- B. statements that must SUCCEED (rolled back) -------------------------
START TRANSACTION;

-- B1 A volunteer may belong to several teams at the same time (BR1).
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (4, 5, 3, '2025-03-01');

-- B2 Re-joining after leaving creates a new row (volunteer 13 left Logistics on 2024-12-31).
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (13, 2, 3, '2025-03-01');

-- B3 Deleting a volunteer with phones but no membership removes the phones too (ON DELETE CASCADE).
INSERT INTO VOLUNTEER (volunteer_id, full_name, email) VALUES (100, 'Temp Person', 'temp@ngo-demo.org');
INSERT INTO VOLUNTEER_PHONE (phone_number, volunteer_id) VALUES ('0908888888', 100);
DELETE FROM VOLUNTEER WHERE volunteer_id = 100;
SELECT COUNT(*) AS phones_left_for_deleted_volunteer FROM VOLUNTEER_PHONE WHERE volunteer_id = 100;  -- Expected: 0

-- B4 Defaults: system_role = 'MEMBER' and registered_date = today when not given.
INSERT INTO VOLUNTEER (volunteer_id, full_name, email) VALUES (101, 'Default Test', 'default@ngo-demo.org');
SELECT system_role, registered_date = CURRENT_DATE AS registered_today FROM VOLUNTEER WHERE volunteer_id = 101;  -- Expected: MEMBER, 1

-- B5 Changing the Leader (BR17): end the old Leader, then add the new one (volunteer 4 is not in team 1).
UPDATE MEMBERSHIP SET left_date = '2025-03-01' WHERE membership_id = 1;
INSERT INTO MEMBERSHIP (volunteer_id, team_id, team_role_id, joined_date) VALUES (4, 1, 1, '2025-03-02');
SELECT volunteer_id FROM MEMBERSHIP
 WHERE team_id = 1 AND team_role_id = 1 AND left_date IS NULL;                -- Expected: only volunteer 4

-- B6 ON UPDATE CASCADE: renumber team 15; its memberships follow.
UPDATE TEAM SET team_id = 200 WHERE team_id = 15;
SELECT COUNT(*) AS memberships_of_team_200 FROM MEMBERSHIP WHERE team_id = 200;  -- Expected: 4

ROLLBACK;

-- Check the sample data is unchanged: 16 / 21 / 15 / 15 / 68 rows.
SELECT (SELECT COUNT(*) FROM VOLUNTEER) AS volunteers, (SELECT COUNT(*) FROM VOLUNTEER_PHONE) AS phones,
       (SELECT COUNT(*) FROM TEAM) AS teams, (SELECT COUNT(*) FROM TEAM_ROLE) AS roles,
       (SELECT COUNT(*) FROM MEMBERSHIP) AS memberships;
