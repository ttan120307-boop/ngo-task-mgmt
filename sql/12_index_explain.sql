-- =====================================================================
-- 12_index_explain.sql -- the index idx_task_project_status (project_id, status, due_date)
-- and the query it is meant to help: Q1 of 11_queries.sql
--   "Every open task of one project, ordered by due date."
-- Run after ngo_full_schema_and_data.sql. Copy the EXPLAIN output into the report.
-- With only 55 sample rows the optimizer may still prefer a table scan; the test below FORCEs the
-- two alternatives so that the difference in the plan is visible. (Do not expect the row counts
-- to be large: what matters is the column "Extra".)
-- =====================================================================
USE ngo_task_mgmt;

-- BEFORE: without an index on the search columns the whole table is read and then sorted.
--   Expected: key = fk_task_project (index of the foreign key) or NULL, Extra contains "Using filesort".
DROP INDEX idx_task_project_status ON TASK;
EXPLAIN
SELECT task_id, title, status, due_date
FROM TASK
WHERE project_id = 1 AND status IN ('TODO', 'IN_PROGRESS')
ORDER BY due_date;

-- AFTER: the index (project_id, status, due_date) is created again.
--   The equality on project_id and the IN list on status select index ranges; inside each range the
--   rows are ordered by due_date. Expected: key = idx_task_project_status and, for a single status,
--   no "Using filesort". (With an IN list of two statuses MySQL merges two ranges, so for the
--   strict "no filesort" demonstration the query below uses one status.)
CREATE INDEX idx_task_project_status ON TASK (project_id, status, due_date);
EXPLAIN
SELECT task_id, title, status, due_date
FROM TASK
WHERE project_id = 1 AND status IN ('TODO', 'IN_PROGRESS')
ORDER BY due_date;

EXPLAIN
SELECT task_id, title, due_date
FROM TASK
WHERE project_id = 1 AND status = 'TODO'
ORDER BY due_date;

-- Note: the first EXPLAIN of each pair is Q1 itself. The index is the one created in
-- 03_project_task_schema_and_data.sql, so this script leaves the database as it found it.
