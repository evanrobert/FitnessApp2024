-- Account security and email: sign-in by email, password reset, lockout after
-- repeated failed sign-ins, and an opt-in weekly summary email.

ALTER TABLE user_login_details ADD COLUMN email VARCHAR(254);
ALTER TABLE user_login_details ADD COLUMN email_verified_at DATETIME(6);
ALTER TABLE user_login_details ADD COLUMN password_changed_at DATETIME(6);
ALTER TABLE user_login_details ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE user_login_details ADD COLUMN locked_until DATETIME(6);
ALTER TABLE user_login_details ADD COLUMN weekly_email BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_login_details ADD COLUMN weekly_email_last_sent DATE;
ALTER TABLE user_login_details ADD COLUMN unsubscribe_token VARCHAR(64);

-- Addresses are stored lower-cased; NULLs (accounts without an email yet) don't collide.
CREATE UNIQUE INDEX uk_user_login_details_email ON user_login_details (email);
CREATE UNIQUE INDEX uk_user_login_details_unsubscribe ON user_login_details (unsubscribe_token);

-- Single-use links sent by email (password reset, email verification).
-- Only a SHA-256 hash of each token is stored, so a database leak can't be used to reset passwords.
CREATE TABLE account_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    purpose    VARCHAR(24) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    email      VARCHAR(254),
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_token_user FOREIGN KEY (user_id) REFERENCES user_login_details (id) ON DELETE CASCADE
);
CREATE INDEX ix_account_token_user_purpose ON account_token (user_id, purpose);
