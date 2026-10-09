-- Starred foods: pinned to the top of the one-tap "log again" list. Stores only the
-- (lower-cased) food name; calories and macros come from the member's latest entry of it.
CREATE TABLE favorite_food (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    name_key   VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorite_food UNIQUE (user_id, name_key),
    CONSTRAINT fk_favorite_food_user FOREIGN KEY (user_id) REFERENCES user_login_details (id) ON DELETE CASCADE
);
