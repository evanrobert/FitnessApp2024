-- 1. HRA removal. The HRA upload feature is retired; its stored files are
--    health-assessment documents that must not linger in the database.
DROP TABLE IF EXISTS reports;

-- 2. Usernames must be unique. The legacy signup allowed duplicates, and any
--    duplicate account could not log in (lookup returned two rows). Keep the
--    oldest account's name and rename later duplicates so the constraint applies.
UPDATE user_login_details
SET username = CONCAT(username, '_dup', id)
WHERE id NOT IN (SELECT keep_id FROM (SELECT MIN(id) AS keep_id FROM user_login_details GROUP BY username) k);

CREATE UNIQUE INDEX uk_user_login_details_username ON user_login_details (username);

-- 3. The default role is reference data, seeded here instead of lazily at signup.
INSERT INTO roles (name)
SELECT 'ROLE_USER' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

ALTER TABLE users_roles ADD PRIMARY KEY (user_login_details_id, role_id);

-- 4. The profile link was stored twice (user_login_details.user_information_id
--    and user_information.userid). Keep user_information.userid as the single owner.
UPDATE user_information
SET userid = (SELECT l.id FROM user_login_details l WHERE l.user_information_id = user_information.id)
WHERE userid IS NULL;

ALTER TABLE user_login_details DROP FOREIGN KEY FK8qkwn23pjlr937c8ga3us7msn;
ALTER TABLE user_login_details DROP COLUMN user_information_id;

CREATE UNIQUE INDEX uk_user_information_user ON user_information (userid);

-- 5. One nutrition-target row per user (a repeated POST could create duplicates,
--    which broke every page that loads targets). Keep the most recent row.
DELETE FROM user_macro_information
WHERE id NOT IN (SELECT keep_id FROM (SELECT MAX(id) AS keep_id FROM user_macro_information GROUP BY userid) k);

CREATE UNIQUE INDEX uk_user_macro_information_user ON user_macro_information (userid);
