-- =====================================================================
-- IT079IU - Topic 6: Volunteer/NGO Project and Task Management
-- Module 4: Labels (LABEL, TASK_LABEL)
--
-- RUN ORDER: 01 -> 03 -> 05 -> 07 (this file). Requires MySQL 8.0.16+ and the
-- sample data of module 2 (TASK). It does not contain DROP DATABASE.
-- If module 03 is run again, it drops TASK_LABEL: run this file again afterwards.
-- =====================================================================
USE ngo_task_mgmt;

DROP TABLE IF EXISTS TASK_LABEL;
DROP TABLE IF EXISTS LABEL;

-- A label is a free tag ("Urgent", "Pending Approval") reused by many tasks of
-- different projects. label_name is unique (case-insensitive because of the
-- utf8mb4_unicode_ci collation of the database): FD label_name -> label_id.
CREATE TABLE LABEL (
    label_id    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    label_name  VARCHAR(50)   NOT NULL,
    CONSTRAINT pk_label       PRIMARY KEY (label_id),
    CONSTRAINT uq_label_name  UNIQUE (label_name),
    CONSTRAINT ck_label_name  CHECK (CHAR_LENGTH(TRIM(label_name)) > 0)
) ENGINE = InnoDB;

-- Many-to-many link between TASK and LABEL. Deleting a task or a label removes
-- the links only, never the other side.
CREATE TABLE TASK_LABEL (
    task_id   INT UNSIGNED NOT NULL,
    label_id  INT UNSIGNED NOT NULL,
    CONSTRAINT pk_task_label PRIMARY KEY (task_id, label_id),
    CONSTRAINT fk_tasklabel_task  FOREIGN KEY (task_id)
        REFERENCES TASK (task_id)   ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_tasklabel_label FOREIGN KEY (label_id)
        REFERENCES LABEL (label_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB;
-- The primary key serves "labels of a task"; the foreign key on label_id
-- creates the index for "tasks with a label".

-- =====================================================================
-- Sample data. Fictional. 15 labels / 69 links (task 30 has no label).
-- =====================================================================
INSERT INTO LABEL (label_id, label_name) VALUES
 ( 1, 'Urgent'),
 ( 2, 'Fieldwork'),
 ( 3, 'Fundraising'),
 ( 4, 'Design'),
 ( 5, 'Logistics'),
 ( 6, 'Training'),
 ( 7, 'Finance'),
 ( 8, 'Communication'),
 ( 9, 'Documentation'),
 (10, 'Safety'),
 (11, 'Procurement'),
 (12, 'Reporting'),
 (13, 'Community Outreach'),
 (14, 'IT Support'),
 (15, 'Pending Approval');

-- (task_id, label_id)
INSERT INTO TASK_LABEL (task_id, label_id) VALUES
 (1,2),(1,13),(2,7),(2,9),(3,11),(3,5),
 (4,1),(4,2),(4,5),(5,6),(6,13),(7,8),
 (8,3),(9,4),(10,8),(11,5),(12,9),(13,9),
 (13,12),(14,6),(15,13),(16,1),(16,10),(17,13),
 (18,1),(18,5),(19,2),(19,13),(20,12),(20,9),
 (21,14),(22,4),(22,15),(23,14),(24,14),(24,9),
 (25,8),(26,2),(27,10),(28,3),(28,15),(29,5),
 (31,13),(31,9),(32,4),(33,8),(34,10),(35,13),(36,5),(37,12),
 (38,13),(39,10),(40,6),(41,7),(42,7),(42,12),(43,8),(44,9),
 (45,14),(46,6),(47,13),(48,13),(49,5),(50,10),(51,10),(52,4),
 (53,5),(54,10),(55,8);

SELECT 'LABEL' AS table_name, COUNT(*) AS row_count FROM LABEL
UNION ALL SELECT 'TASK_LABEL', COUNT(*) FROM TASK_LABEL;
