-- Optional: for the mysql command-line client only (SOURCE is a client command, paths are relative to
-- the folder where you start mysql). In Workbench / DBeaver run ngo_full_schema_and_data.sql instead.
--   mysql -u root -p < ngo_full_schema_and_data.sql       (the simplest way)
SOURCE 01_foundation_schema_and_data.sql;
SOURCE 03_project_task_schema_and_data.sql;
SOURCE 05_interactions_schema_and_data.sql;
SOURCE 07_label_schema_and_data.sql;
SOURCE 09_account_schema_and_data.sql;
