-- "Keep me signed in on this device": one row per device (Spring Security's persistent
-- remember-me tokens). Each use rotates the token; a stolen, replayed cookie wipes the
-- member's tokens. Password changes, resets, logout and account deletion remove them.
CREATE TABLE persistent_logins (
    series    VARCHAR(64) NOT NULL PRIMARY KEY,
    username  VARCHAR(255) NOT NULL,
    token     VARCHAR(64) NOT NULL,
    last_used TIMESTAMP   NOT NULL
);
CREATE INDEX idx_persistent_logins_username ON persistent_logins (username);
