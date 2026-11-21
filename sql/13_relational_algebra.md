# Relational algebra versions of four queries (IT079IU, Topic 6)

Notation: σ selection, π projection, ⋈ natural/theta join, ρ rename, − difference, τ sort (not part of the pure algebra, written only to match ORDER BY).
Relations: PROJECT(project_id, owner_id, project_name, description, start_date, due_date), TASK(task_id, project_id, team_id, title, description, priority, status, estimated_hours, due_date), TEAM(team_id, team_name, …), TASK_DEPENDENCY(dependent_task_id, prerequisite_task_id).

## Q1 Open tasks of project 1 with their team, earliest due date first (SQL Q1)

```
OPEN   ← σ project_id = 1 ∧ (status = 'TODO' ∨ status = 'IN_PROGRESS') (TASK)
RESULT ← τ due_date ( π task_id, title, status, priority, team_name, due_date ( OPEN ⋈ OPEN.team_id = TEAM.team_id TEAM ) )
```

## Q3 Tasks that cannot start because a prerequisite is unfinished (SQL Q3, task and prerequisite columns only)

```
TODO_T ← σ status = 'TODO' (TASK)
UNFIN  ← ρ P (σ status ≠ 'DONE' (TASK))
RESULT ← π TODO_T.task_id, TODO_T.title ( ( TODO_T ⋈ TODO_T.task_id = dependent_task_id TASK_DEPENDENCY ) ⋈ prerequisite_task_id = P.task_id UNFIN )
```

## Q4 Projects whose tasks are ALL completed (SQL Q4, "for all" as a difference)

```
WITH_TASK  ← π project_id (TASK)                          -- projects that have at least one task
UNFINISHED ← π project_id ( σ status ≠ 'DONE' (TASK) )    -- projects that have a task that is not DONE
ALL_DONE   ← WITH_TASK − UNFINISHED
RESULT     ← π project_id, project_name ( PROJECT ⋈ ALL_DONE )
```

(Equivalent division form: TASK_STATUS(project_id, status) ÷ ... is not needed because the difference above is the direct translation of NOT EXISTS.)

## Q6 Projects that have no task (SQL Q6)

```
NO_TASK ← π project_id (PROJECT) − π project_id (TASK)
RESULT  ← π project_id, project_name, owner_id ( PROJECT ⋈ NO_TASK )
```

Result on the sample data: Q1 → tasks 4 and 5; Q3 → 15 tasks (5, 20, 23, 24, 25, 27, 28, 29, 37, 43, 44, 47, 49, 52, 55); Q4 → projects 2 and 8; Q6 → project 7.
