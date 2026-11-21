-- =====================================================================
-- Constraint tests for the ACCOUNT module (09). Run ngo_full_schema_and_data.sql first.
-- Part A must FAIL (run with "continue on error"); part B must SUCCEED (rolled back).
-- =====================================================================
USE ngo_task_mgmt;

-- A1 Second account for the same volunteer (PRIMARY KEY).           Expected: ERROR 1062 (PRIMARY)
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash)
VALUES (1, 'another.name', REPEAT('a', 32), REPEAT('b', 64));

-- A2 Duplicate user name, also in another letter case (UNIQUE).     Expected: ERROR 1062 (uq_account_username)
INSERT INTO VOLUNTEER (volunteer_id, full_name, email) VALUES (900, 'Temp Account', 'temp.account@ngo-demo.org');
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash)
VALUES (900, 'MINHANH.NGUYEN', REPEAT('a', 32), REPEAT('b', 64));

-- A3 Account of a volunteer that does not exist (FK).               Expected: ERROR 1452 (fk_account_volunteer)
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash)
VALUES (999, 'ghost.user', REPEAT('a', 32), REPEAT('b', 64));

-- A4 User name with a space or too short (CHECK).                   Expected: ERROR 3819 (ck_account_username) for both
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash) VALUES (900, 'bad name', REPEAT('a', 32), REPEAT('b', 64));
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash) VALUES (900, 'ab', REPEAT('a', 32), REPEAT('b', 64));

-- A5 A plain-text password instead of a 64-hex hash (CHECK).        Expected: ERROR 3819 (ck_account_hash)
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash) VALUES (900, 'plain.pwd', REPEAT('a', 32), 'Ngo@2026');

-- A6 is_active outside 0/1 (CHECK).                                 Expected: ERROR 3819 (ck_account_active)
UPDATE ACCOUNT SET is_active = 2 WHERE volunteer_id = 1;

-- A7 NULL password hash (NOT NULL).                                 Expected: ERROR 1048
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash) VALUES (900, 'no.hash', REPEAT('a', 32), NULL);

DELETE FROM VOLUNTEER WHERE volunteer_id = 900;   -- clean up the temporary volunteer of A2-A7

-- ------------------------- B. must SUCCEED (rolled back) -------------------------
START TRANSACTION;

-- B1 The sample password verifies: hash = SHA-256(salt || 'Ngo@2026').     Expected: 16 (all accounts)
SELECT COUNT(*) AS accounts_with_demo_password FROM ACCOUNT
 WHERE password_hash = SHA2(CONCAT(salt, 'Ngo@2026'), 256);

-- B2 Deleting a volunteer without projects, comments or memberships removes the account (ON DELETE CASCADE).
INSERT INTO VOLUNTEER (volunteer_id, full_name, email) VALUES (901, 'Temp Two', 'temp.two@ngo-demo.org');
INSERT INTO ACCOUNT (volunteer_id, username, salt, password_hash)
VALUES (901, 'temp.two', MD5('x'), SHA2(CONCAT(MD5('x'), 'pw'), 256));
DELETE FROM VOLUNTEER WHERE volunteer_id = 901;
SELECT COUNT(*) AS accounts_left_for_deleted_volunteer FROM ACCOUNT WHERE volunteer_id = 901;   -- Expected: 0

-- B3 ON UPDATE CASCADE: renumber a volunteer, the account follows.       Expected: 1
UPDATE VOLUNTEER SET volunteer_id = 902 WHERE volunteer_id = 16;
SELECT COUNT(*) AS account_of_902 FROM ACCOUNT WHERE volunteer_id = 902;

-- B4 Only the inactive account is refused: 15 active accounts.           Expected: 15
SELECT COUNT(*) AS active_accounts FROM ACCOUNT WHERE is_active = 1;

ROLLBACK;

-- Sample data unchanged: 16 accounts.
SELECT COUNT(*) AS accounts FROM ACCOUNT;
