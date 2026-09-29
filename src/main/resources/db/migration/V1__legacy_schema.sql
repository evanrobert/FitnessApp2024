-- Baseline: the schema the legacy application generated through Hibernate
-- ddl-auto (Spring Boot 2.7 / Hibernate 5). Existing databases are baselined
-- at this version and skip it; new databases create it and then evolve
-- through the same migrations, so every install follows one upgrade path.
-- Constraint names match Hibernate's deterministic generated names.

CREATE TABLE roles (
    id   BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE user_login_details (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    password            VARCHAR(255),
    username            VARCHAR(255),
    user_information_id BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE user_information (
    id     BIGINT NOT NULL AUTO_INCREMENT,
    age    INT NOT NULL,
    name   VARCHAR(255),
    weight INT NOT NULL,
    userid BIGINT,
    PRIMARY KEY (id)
);

ALTER TABLE user_login_details ADD CONSTRAINT FK8qkwn23pjlr937c8ga3us7msn
    FOREIGN KEY (user_information_id) REFERENCES user_information (id);
ALTER TABLE user_information ADD CONSTRAINT FKealwfbq6jq3jw5uw39datn16c
    FOREIGN KEY (userid) REFERENCES user_login_details (id);

CREATE TABLE users_roles (
    user_login_details_id BIGINT NOT NULL,
    role_id               BIGINT NOT NULL,
    CONSTRAINT FKfb36nl002v2rpxhoygmej5o0u FOREIGN KEY (user_login_details_id) REFERENCES user_login_details (id),
    CONSTRAINT FKj6m8fwv7oqv74fcehir1a9ffy FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE calorie_information (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    calories      DOUBLE NOT NULL,
    carbohydrates DOUBLE NOT NULL,
    cholesterol   DOUBLE NOT NULL,
    date          DATE,
    fats          DOUBLE NOT NULL,
    fiber         DOUBLE NOT NULL,
    item_name     VARCHAR(255),
    meal_type     VARCHAR(255),
    proteins      DOUBLE NOT NULL,
    sodium        DOUBLE NOT NULL,
    sugars        DOUBLE NOT NULL,
    userid        BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT FK2uhjfd7kfr0alqurclwyd7x97 FOREIGN KEY (userid) REFERENCES user_login_details (id)
);

CREATE TABLE user_macro_information (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    daily_calories      DOUBLE NOT NULL,
    daily_carbohydrates DOUBLE NOT NULL,
    daily_fat           DOUBLE NOT NULL,
    daily_protein       DOUBLE NOT NULL,
    userid              BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT FKbv7ii8iq5ty3t8n1dokoqlg0r FOREIGN KEY (userid) REFERENCES user_login_details (id)
);

CREATE TABLE workout_information (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    date          DATE,
    exercise_name VARCHAR(255),
    notes         VARCHAR(255),
    reps          INT NOT NULL,
    sets          INT NOT NULL,
    weight        DOUBLE NOT NULL,
    workout_type  VARCHAR(255),
    userid        BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT FKt7rjuv32xuo23j3d6gwgpu8o0 FOREIGN KEY (userid) REFERENCES user_login_details (id)
);

-- Legacy HRA upload storage (feature removed; dropped in V2).
CREATE TABLE reports (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    data        LONGBLOB,
    file_name   VARCHAR(255),
    file_type   VARCHAR(255),
    uploaded_at DATETIME(6),
    userid      BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT FK41kot2qaof3jb1cksvt6kk41r FOREIGN KEY (userid) REFERENCES user_login_details (id)
);
