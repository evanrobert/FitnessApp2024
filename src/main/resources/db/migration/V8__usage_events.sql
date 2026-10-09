-- Product usage: one row per successful log (what kind, how, how many seconds the page was
-- open). No content is stored: not the food, weights or answers. Rows older than 180 days are
-- deleted nightly, and a member's rows go with their account.
CREATE TABLE usage_event (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    kind        VARCHAR(16) NOT NULL,
    method      VARCHAR(16) NOT NULL,
    seconds     INT,
    occurred_on DATE        NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_usage_event_user FOREIGN KEY (user_id) REFERENCES user_login_details (id) ON DELETE CASCADE
);
CREATE INDEX idx_usage_event_day ON usage_event (occurred_on);
