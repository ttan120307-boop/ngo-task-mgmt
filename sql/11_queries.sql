-- =====================================================================
-- 11_queries.sql -- the twelve SQL queries of the project, each preceded by the question it answers.
-- RUN AFTER ngo_full_schema_and_data.sql. Every query returns rows on the sample data.
-- "Open" task = status TODO or IN_PROGRESS. "Overdue" = open AND due_date < CURRENT_DATE.
-- All sample dates are relative to CURRENT_DATE, so the results below are valid on any day.
-- Requirement checklist:  multi-table join Q1, Q3, Q5 | outer join Q2, Q6 | GROUP BY + HAVING Q5, Q7, Q12 |
--   IN Q8 | EXISTS Q9 | NOT EXISTS ("for all") Q4 | set operation (UNION) Q10 | view Q11.
-- Topic 6 queries: Q1 (open tasks of a project), Q2 (open / overdue per volunteer), Q3 (blocked tasks),
--   Q4 (projects with every task completed), Q5 (volunteer who commented in most projects).
-- Relational algebra versions of Q1, Q3, Q4 and Q6: see 13_relational_algebra.md.
-- =====================================================================
USE ngo_task_mgmt;

-- Q1 [multi-table join, ORDER BY]
-- Question: Which tasks of project 1 are still open, and in which team, earliest due date first?
-- (This is the query served by idx_task_project_status (project_id, status, due_date); see 12_index_explain.sql.)
-- Expected: tasks 4 (IN_PROGRESS, overdue) and 5 (TODO).
SELECT t.task_id, t.title, t.status, t.priority, tm.team_name, t.due_date
FROM TASK t
JOIN TEAM tm ON tm.team_id = t.team_id
WHERE t.project_id = 1
  AND t.status IN ('TODO', 'IN_PROGRESS')
ORDER BY t.due_date;

-- Q2 [outer join, aggregation]
-- Question: For every volunteer, how many tasks does he or she have open, and how many of them are overdue?
-- (LEFT JOIN keeps volunteers who have no task at all, with 0 and 0; in the sample data every
-- volunteer has a task, a newly registered volunteer would appear with 0 and 0.)
SELECT v.volunteer_id, v.full_name,
       COUNT(CASE WHEN t.status IN ('TODO', 'IN_PROGRESS') THEN 1 END)                              AS open_tasks,
       COUNT(CASE WHEN t.status IN ('TODO', 'IN_PROGRESS') AND t.due_date < CURRENT_DATE THEN 1 END) AS overdue_tasks
FROM VOLUNTEER v
LEFT JOIN TASK_ASSIGNMENT a ON a.volunteer_id = v.volunteer_id
LEFT JOIN TASK t            ON t.task_id = a.task_id
GROUP BY v.volunteer_id, v.full_name
ORDER BY overdue_tasks DESC, open_tasks DESC, v.volunteer_id;

-- Q3 [join of TASK with itself through TASK_DEPENDENCY]
-- Question: Which tasks cannot start yet because a task they depend on is unfinished, and what are they waiting for?
SELECT t.task_id, t.title, t.project_id,
       COUNT(*)                                        AS unfinished_prerequisites,
       GROUP_CONCAT(p.task_id ORDER BY p.task_id)      AS waiting_for_tasks
FROM TASK t
JOIN TASK_DEPENDENCY d ON d.dependent_task_id = t.task_id
JOIN TASK p            ON p.task_id = d.prerequisite_task_id
WHERE t.status = 'TODO'
  AND p.status <> 'DONE'
GROUP BY t.task_id, t.title, t.project_id
ORDER BY t.project_id, t.task_id;

-- Q4 [NOT EXISTS = "for all"]
-- Question: Which projects have at least one task and have ALL their tasks completed (status DONE)?
-- Expected: projects 2 and 8 (project 7 has no task, so it is not listed).
SELECT p.project_id, p.project_name
FROM PROJECT p
WHERE EXISTS (SELECT 1 FROM TASK t WHERE t.project_id = p.project_id)
  AND NOT EXISTS (SELECT 1 FROM TASK t
                   WHERE t.project_id = p.project_id
                     AND t.status <> 'DONE');

-- Q5 [multi-table join, GROUP BY, HAVING, nested query]
-- Question: Which volunteer has commented on tasks of the largest number of different projects?
-- Expected: volunteer 9 (4 projects).
SELECT c.volunteer_id, v.full_name, COUNT(DISTINCT t.project_id) AS projects_commented
FROM TASK_COMMENT c
JOIN TASK t      ON t.task_id = c.task_id
JOIN VOLUNTEER v ON v.volunteer_id = c.volunteer_id
GROUP BY c.volunteer_id, v.full_name
HAVING COUNT(DISTINCT t.project_id) =
       (SELECT MAX(x.n)
          FROM (SELECT COUNT(DISTINCT t2.project_id) AS n
                  FROM TASK_COMMENT c2
                  JOIN TASK t2 ON t2.task_id = c2.task_id
                 GROUP BY c2.volunteer_id) x);

-- Q6 [outer join with IS NULL: "no match"]
-- Question: Which projects have no task yet, and who owns them?
-- Expected: project 7.
SELECT p.project_id, p.project_name, v.full_name AS owner
FROM PROJECT p
JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id
LEFT JOIN TASK t ON t.project_id = p.project_id
WHERE t.task_id IS NULL;

-- Q7 [GROUP BY, HAVING]
-- Question: Which teams currently own at least three open tasks, and how many?
-- Expected: teams 1, 2, 12 and 14.
SELECT tm.team_id, tm.team_name, COUNT(*) AS open_tasks
FROM TEAM tm
JOIN TASK t ON t.team_id = tm.team_id
WHERE t.status IN ('TODO', 'IN_PROGRESS')
GROUP BY tm.team_id, tm.team_name
HAVING COUNT(*) >= 3
ORDER BY open_tasks DESC, tm.team_id;

-- Q8 [nested query with IN]
-- Question: Who has been assigned at least one URGENT task?
SELECT v.volunteer_id, v.full_name
FROM VOLUNTEER v
WHERE v.volunteer_id IN (SELECT a.volunteer_id
                           FROM TASK_ASSIGNMENT a
                           JOIN TASK t ON t.task_id = a.task_id
                          WHERE t.priority = 'URGENT')
ORDER BY v.volunteer_id;

-- Q9 [nested query with EXISTS]
-- Question: Which open tasks have at least one file attached to one of their comments?
SELECT t.task_id, t.title, t.status
FROM TASK t
WHERE t.status IN ('TODO', 'IN_PROGRESS')
  AND EXISTS (SELECT 1 FROM ATTACHMENT att WHERE att.task_id = t.task_id)
ORDER BY t.task_id;

-- Q10 [set operation: UNION]
-- Question: Who is involved in project 1 in any way: as its owner, as an assignee of one of its tasks,
-- or as the author of a comment on one of its tasks?
SELECT v.volunteer_id, v.full_name, 'owner' AS involvement
FROM PROJECT p JOIN VOLUNTEER v ON v.volunteer_id = p.owner_id
WHERE p.project_id = 1
UNION
SELECT v.volunteer_id, v.full_name, 'assignee'
FROM TASK t JOIN TASK_ASSIGNMENT a ON a.task_id = t.task_id
            JOIN VOLUNTEER v ON v.volunteer_id = a.volunteer_id
WHERE t.project_id = 1
UNION
SELECT v.volunteer_id, v.full_name, 'commenter'
FROM TASK t JOIN TASK_COMMENT c ON c.task_id = t.task_id
            JOIN VOLUNTEER v ON v.volunteer_id = c.volunteer_id
WHERE t.project_id = 1
ORDER BY volunteer_id, involvement;

-- Q11 [view]
-- Question: How far is each project (number of tasks, completed tasks, percentage, overdue open tasks)?
-- A view hides the aggregation; the query after it lists the projects that are late.
CREATE OR REPLACE VIEW v_project_progress AS
SELECT p.project_id, p.project_name, p.due_date,
       COUNT(t.task_id)                                                    AS total_tasks,
       COUNT(CASE WHEN t.status = 'DONE' THEN 1 END)                       AS done_tasks,
       ROUND(100 * COUNT(CASE WHEN t.status = 'DONE' THEN 1 END)
                 / NULLIF(COUNT(t.task_id), 0), 0)                         AS percent_done,
       COUNT(CASE WHEN t.status IN ('TODO', 'IN_PROGRESS')
                   AND t.due_date < CURRENT_DATE THEN 1 END)               AS overdue_open_tasks
FROM PROJECT p
LEFT JOIN TASK t ON t.project_id = p.project_id
GROUP BY p.project_id, p.project_name, p.due_date;

SELECT * FROM v_project_progress WHERE overdue_open_tasks > 0 ORDER BY overdue_open_tasks DESC, project_id;

-- Q12 [GROUP BY, HAVING over a many-to-many link]
-- Question: Which labels are used by tasks of at least five different projects, and in how many tasks?
SELECT l.label_id, l.label_name, COUNT(*) AS tasks_with_label, COUNT(DISTINCT t.project_id) AS projects
FROM LABEL l
JOIN TASK_LABEL tl ON tl.label_id = l.label_id
JOIN TASK t        ON t.task_id = tl.task_id
GROUP BY l.label_id, l.label_name
HAVING COUNT(DISTINCT t.project_id) >= 5
ORDER BY projects DESC, tasks_with_label DESC, l.label_id;
