# NGO Project and Task Management — SQL package v3 (IT079IU, Topic 6)

Requires MySQL 8.0.16 or later (CHECK constraints are enforced from 8.0.16). **Not yet run on a real MySQL server** (the
data and the expected test numbers were checked with a Python checker): run the tests once and record the real errors.

## How to run
1. **Run `ngo_full_schema_and_data.sql`** (any client: Workbench, DBeaver, `mysql -u root -p < ngo_full_schema_and_data.sql`).
   It drops and recreates the database, so it can be run twice in a row. It is the concatenation of 01, 03, 05, 07:
   `cat 01_*.sql 03_*.sql 05_*.sql 07_*.sql > ngo_full_schema_and_data.sql`.
   (`00_run_all.sql` is optional and only works with the `mysql` command-line client and relative paths.)
2. Tests: `02`, `04`, `06`, `08` — part A fails on purpose (run with "continue on error"), part B succeeds and is rolled back.
3. `10_dml_examples.sql` (INSERT/UPDATE/DELETE for every table, one COMMIT and one ROLLBACK transaction; self-cleaning),
   `11_queries.sql` (12 queries with the question in English), `12_index_explain.sql` (EXPLAIN before / after the index),
   `13_relational_algebra.md` (4 queries in relational algebra).

| File | Content |
|---|---|
| `01_foundation_schema_and_data.sql` | VOLUNTEER, VOLUNTEER_PHONE, TEAM, TEAM_ROLE, MEMBERSHIP (16/21/15/15/68 rows) |
| `03_project_task_schema_and_data.sql` | PROJECT, TASK, TASK_DEPENDENCY (15/55/45 rows) |
| `05_interactions_schema_and_data.sql` | TASK_ASSIGNMENT, TASK_COMMENT, ATTACHMENT (58/21/19 rows), procedure, audit view |
| `07_label_schema_and_data.sql` | LABEL, TASK_LABEL (15/69 rows) |

## Sample data
All dates of projects, tasks, assignments, comments and attachments are written relative to `CURRENT_DATE`
(for example `CURRENT_DATE - INTERVAL 20 DAY`): there is one dataset and it is "alive" on any day. Project 7 has no task
(NOT EXISTS / LEFT JOIN), projects 2 and 8 are completely DONE, tasks 4, 15, 22 and 36 are open and overdue,
15 tasks are blocked by an unfinished prerequisite. The dates of people and teams (registered / created / joined) are fixed past dates.

## ONE code set for the business rules (BR1 … BR24)
Rules 1–15 are the rules of the report (same numbers); BR16–BR24 are the additional rules of the SQL. The same codes are used in the SQL comments, the
tests, the normalization report (Section III) and the D2 Update Pack.

| Code | Rule | Mechanism | Test |
|---|---|---|---|
| BR1 | One current membership per volunteer and team | generated column `is_current` + `uq_membership_one_current`; `uq_membership_same_day` | 02-A12, A22 |
| BR2 | A membership has a role | `team_role_id NOT NULL` + FK | 02-A10, A11 |
| BR3–BR5 | Project owner, mandatory project data, task belongs to one project | NOT NULL, FK, CHECK | 04-A1–A7 |
| BR6 | A task has title, priority, status, **estimated_hours and due_date (all NOT NULL)**; only description is optional | NOT NULL, CHECK `ck_task_hours` | 04-A9b, A12–A12d |
| BR7 | Many assignees per task, many tasks per volunteer, with date | `TASK_ASSIGNMENT` composite PK | 06-A1–A3 |
| BR8 | An assignee was a member of the task's team **on the assigned date** (`joined_date <= d AND (left_date IS NULL OR left_date >= d)`): one definition in the triggers and in the view | procedure `sp_check_assignment` (triggers `trg_assignment_bi/bu`); view `v_assignment_outside_membership` (same condition, must be empty) | 06-A4, A5, A14, A14b, A15, B5 |
| BR9 | A dependency joins two tasks of the same project | `trg_taskdep_bi` | 04-A17 |
| BR10 | A task cannot start (IN_PROGRESS / DONE) before all its prerequisites are DONE | `trg_taskdep_bi`, `trg_task_bu` | 04-A20, A22, A23 |
| BR11 | Task due date ≤ project due date | `trg_task_bi`, `trg_task_bu`, `trg_project_bu` | 04-A26–A28 |
| BR12–BR14 | Weak entities: comments belong to a task, attachments to a comment, cascade delete | composite PKs, composite FK `ON DELETE CASCADE` | 06-A6–A13, B1–B4 |
| BR15 | Labels on many tasks of many projects | `TASK_LABEL`, `uq_label_name` | 08-A1–A8 |
| BR16 | A phone number belongs to one volunteer | `PRIMARY KEY (phone_number)` | 02-A6b |
| BR17 | One current **Leader** per team | **declared key**: generated column `leader_flag` + `uq_membership_one_leader UNIQUE (team_id, leader_flag)`; no trigger. Role id 1 = Leader (two triggers on TEAM_ROLE guard it; `fk_membership_role` is RESTRICT/RESTRICT) | 02-A18, A23, A24, A25, B5 |
| BR18 | Joined date not before the volunteer registered / the team was created | triggers `trg_membership_bi/bu` | 02-A19, A20 |
| BR19 | No self dependency | `trg_taskdep_bi` (a CHECK is impossible: error 3823) | 04-A16 |
| BR20 | No circular dependency | `trg_taskdep_bi` (WITH RECURSIVE) | 04-A18, A19 |
| BR21 | A DONE task cannot be re-opened while a dependent task has started | `trg_task_bu` | 04-A24 |
| BR22 | A task with dependencies cannot move to another project | `trg_task_bu` | 04-A25 |
| BR23 | A dependency row is never edited | `trg_taskdep_bu` | 04-A21 |
| BR24 | The team of a task with assignments cannot change | `trg_task_team_bu` | 06-A16, B7 |

## Index
`idx_task_project_status ON TASK (project_id, status, due_date)` serves the query "open tasks of one project ordered by due date"
(Q1 of `11_queries.sql`): `project_id` and `status` select the rows, `due_date` gives the order without a sort. See `12_index_explain.sql`.

## Design decisions
* `TASK.team_id` NOT NULL with `ON DELETE RESTRICT` (tested by 04-A8b); deleting a PROJECT deletes its tasks and everything below.
* A task that waits for a CANCELLED task cannot start; delete the dependency row to release it.
* A volunteer who leaves a team after the assigned date keeps the assignment as history (inside the membership interval).
* `is_current` and `leader_flag` are generated columns: physical devices for BR1 / BR17, not part of the logical design.
* Because MySQL forbids CASCADE on the base column of a stored generated column, `MEMBERSHIP.team_role_id` is `ON UPDATE RESTRICT`.

## Known limits (state them in the report)
* Not run on real MySQL yet: confirm the error numbers of every test (especially 1364 in 04-A12d, the generated-column key in 02-A18,
  the procedure called from a trigger and `FOLLOWS` in module 05).
* BR20 can be bypassed by two sessions inserting at the same moment (no locking).
* Cascaded foreign-key actions never fire triggers.
* Dates of VOLUNTEER / TEAM are not re-checked when changed later (BR18).
* The Java application is not part of this package.

## Module 09 - ACCOUNT (log-in)
`09_account_schema_and_data.sql` creates ACCOUNT (14th table) and 16 demo accounts (password `Ngo@2026`, account of volunteer 16 disabled).
`14_account_constraint_tests.sql` tests rules BR25/BR26 (A1-A7 expected errors, B1-B4 positive checks, rolled back).
BR27 (role permissions) is enforced by the Java application (`ngo.auth.Authz`), not by MySQL.
