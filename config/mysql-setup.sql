-- One-time MySQL setup for Evan Fitness.
-- Run in MySQL Workbench while connected as root (or another admin account):
--   1. Replace CHANGE_ME below with a password of your choice.
--   2. Run the whole script (lightning-bolt button, or Ctrl+Shift+Enter).
--   3. Put the same username/password in config/application-local.yml.
--
-- It creates an empty database and a dedicated app account that can only touch
-- that database. The app creates its own tables on first start.

CREATE DATABASE IF NOT EXISTS fitness
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'fitness_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON fitness.* TO 'fitness_app'@'localhost';

FLUSH PRIVILEGES;

-- Check: this should list the fitness database.
SHOW DATABASES LIKE 'fitness';
