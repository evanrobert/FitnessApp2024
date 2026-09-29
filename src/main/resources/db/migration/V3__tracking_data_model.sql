-- Phase 3: data model for rich tracking. Every table below is owned by a member
-- (directly or through its parent) and carries dates so it can be trended.

-- ---------------------------------------------------------------------------
-- Accounts & profile
-- ---------------------------------------------------------------------------
ALTER TABLE user_login_details ADD COLUMN created_at DATETIME(6);

ALTER TABLE user_information ADD COLUMN birth_year INT;
ALTER TABLE user_information ADD COLUMN sex VARCHAR(16);
ALTER TABLE user_information ADD COLUMN height_in DOUBLE;
ALTER TABLE user_information ADD COLUMN activity_level VARCHAR(24);
ALTER TABLE user_information ADD COLUMN primary_goal VARCHAR(24);
ALTER TABLE user_information ADD COLUMN experience_level VARCHAR(24);
ALTER TABLE user_information ADD COLUMN weekly_workout_target INT;
ALTER TABLE user_information ADD COLUMN time_zone VARCHAR(64);
ALTER TABLE user_information ADD COLUMN bio VARCHAR(1000);
ALTER TABLE user_information ADD COLUMN gym_name VARCHAR(120);
ALTER TABLE user_information ADD COLUMN membership_plan VARCHAR(80);
ALTER TABLE user_information ADD COLUMN membership_started_on DATE;
ALTER TABLE user_information ADD COLUMN membership_renews_on DATE;
ALTER TABLE user_information ADD COLUMN membership_monthly_cost DECIMAL(10, 2);
ALTER TABLE user_information ADD COLUMN updated_at DATETIME(6);

-- Age goes stale; birth year does not.
UPDATE user_information SET birth_year = YEAR(CURRENT_DATE) - age WHERE age > 0;

-- ---------------------------------------------------------------------------
-- Nutrition targets & entries
-- ---------------------------------------------------------------------------
ALTER TABLE user_macro_information ADD COLUMN daily_fiber DOUBLE;
ALTER TABLE user_macro_information ADD COLUMN daily_water_oz DOUBLE;
ALTER TABLE user_macro_information ADD COLUMN updated_at DATETIME(6);

ALTER TABLE calorie_information ADD COLUMN created_at DATETIME(6);
CREATE INDEX ix_calorie_information_user_date ON calorie_information (userid, date);

-- ---------------------------------------------------------------------------
-- Body measurements (weigh-ins)
-- ---------------------------------------------------------------------------
CREATE TABLE body_measurement (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    measured_on  DATE   NOT NULL,
    weight_lb    DOUBLE,
    body_fat_pct DOUBLE,
    waist_in     DOUBLE,
    hips_in      DOUBLE,
    chest_in     DOUBLE,
    arm_in       DOUBLE,
    thigh_in     DOUBLE,
    neck_in      DOUBLE,
    notes        VARCHAR(500),
    created_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_body_measurement_user FOREIGN KEY (user_id) REFERENCES user_login_details (id)
);
CREATE INDEX ix_body_measurement_user_date ON body_measurement (user_id, measured_on);

-- The weight captured at sign-up becomes the first weigh-in.
INSERT INTO body_measurement (user_id, measured_on, weight_lb, notes, created_at)
SELECT userid, CURRENT_DATE, weight, 'Starting weight from sign-up', CURRENT_TIMESTAMP
FROM user_information
WHERE userid IS NOT NULL AND weight > 0;

ALTER TABLE user_information DROP COLUMN age;
ALTER TABLE user_information DROP COLUMN weight;

-- ---------------------------------------------------------------------------
-- Daily check-ins (recovery, sleep, hydration, wellbeing) - one per day
-- ---------------------------------------------------------------------------
CREATE TABLE daily_check_in (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    check_in_date DATE   NOT NULL,
    sleep_hours   DOUBLE,
    sleep_quality INT,
    energy        INT,
    mood          INT,
    stress        INT,
    soreness      INT,
    water_oz      DOUBLE,
    steps         INT,
    resting_hr    INT,
    notes         VARCHAR(1000),
    created_at    DATETIME(6),
    updated_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_daily_check_in_user FOREIGN KEY (user_id) REFERENCES user_login_details (id),
    CONSTRAINT uk_daily_check_in_user_date UNIQUE (user_id, check_in_date)
);

-- ---------------------------------------------------------------------------
-- Training: exercise library -> sessions -> sets / cardio
-- ---------------------------------------------------------------------------
CREATE TABLE exercise (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id     BIGINT,                 -- NULL = built-in library exercise
    name         VARCHAR(100) NOT NULL,
    muscle_group VARCHAR(24)  NOT NULL,
    equipment    VARCHAR(24),
    created_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_exercise_owner FOREIGN KEY (owner_id) REFERENCES user_login_details (id)
);
CREATE INDEX ix_exercise_owner_name ON exercise (owner_id, name);

INSERT INTO exercise (owner_id, name, muscle_group, equipment) VALUES
    (NULL, 'Back Squat', 'QUADS', 'BARBELL'),
    (NULL, 'Front Squat', 'QUADS', 'BARBELL'),
    (NULL, 'Deadlift', 'BACK', 'BARBELL'),
    (NULL, 'Romanian Deadlift', 'HAMSTRINGS', 'BARBELL'),
    (NULL, 'Bench Press', 'CHEST', 'BARBELL'),
    (NULL, 'Incline Bench Press', 'CHEST', 'BARBELL'),
    (NULL, 'Dumbbell Bench Press', 'CHEST', 'DUMBBELL'),
    (NULL, 'Push-Up', 'CHEST', 'BODYWEIGHT'),
    (NULL, 'Overhead Press', 'SHOULDERS', 'BARBELL'),
    (NULL, 'Dumbbell Shoulder Press', 'SHOULDERS', 'DUMBBELL'),
    (NULL, 'Lateral Raise', 'SHOULDERS', 'DUMBBELL'),
    (NULL, 'Face Pull', 'SHOULDERS', 'CABLE'),
    (NULL, 'Pull-Up', 'BACK', 'BODYWEIGHT'),
    (NULL, 'Chin-Up', 'BACK', 'BODYWEIGHT'),
    (NULL, 'Barbell Row', 'BACK', 'BARBELL'),
    (NULL, 'Dumbbell Row', 'BACK', 'DUMBBELL'),
    (NULL, 'Lat Pulldown', 'BACK', 'CABLE'),
    (NULL, 'Seated Cable Row', 'BACK', 'CABLE'),
    (NULL, 'Barbell Curl', 'BICEPS', 'BARBELL'),
    (NULL, 'Dumbbell Curl', 'BICEPS', 'DUMBBELL'),
    (NULL, 'Hammer Curl', 'BICEPS', 'DUMBBELL'),
    (NULL, 'Triceps Pushdown', 'TRICEPS', 'CABLE'),
    (NULL, 'Skull Crusher', 'TRICEPS', 'BARBELL'),
    (NULL, 'Dip', 'TRICEPS', 'BODYWEIGHT'),
    (NULL, 'Leg Press', 'QUADS', 'MACHINE'),
    (NULL, 'Leg Extension', 'QUADS', 'MACHINE'),
    (NULL, 'Bulgarian Split Squat', 'QUADS', 'DUMBBELL'),
    (NULL, 'Walking Lunge', 'QUADS', 'DUMBBELL'),
    (NULL, 'Leg Curl', 'HAMSTRINGS', 'MACHINE'),
    (NULL, 'Hip Thrust', 'GLUTES', 'BARBELL'),
    (NULL, 'Kettlebell Swing', 'GLUTES', 'KETTLEBELL'),
    (NULL, 'Standing Calf Raise', 'CALVES', 'MACHINE'),
    (NULL, 'Plank', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Hanging Leg Raise', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Cable Crunch', 'CORE', 'CABLE'),
    (NULL, 'Farmer''s Carry', 'FULL_BODY', 'DUMBBELL'),
    (NULL, 'Power Clean', 'FULL_BODY', 'BARBELL');

CREATE TABLE workout_session (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    session_date  DATE   NOT NULL,
    title         VARCHAR(120),
    focus         VARCHAR(24),
    location_type VARCHAR(16),
    duration_min  INT,
    session_rpe   INT,
    notes         VARCHAR(2000),
    created_at    DATETIME(6),
    updated_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_workout_session_user FOREIGN KEY (user_id) REFERENCES user_login_details (id)
);
CREATE INDEX ix_workout_session_user_date ON workout_session (user_id, session_date);

CREATE TABLE exercise_set (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    session_id  BIGINT  NOT NULL,
    exercise_id BIGINT  NOT NULL,
    sort_order  INT     NOT NULL,
    set_number  INT     NOT NULL,
    reps        INT,
    weight_lb   DOUBLE,
    rpe         DOUBLE,
    warmup      BOOLEAN NOT NULL DEFAULT FALSE,
    notes       VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT fk_exercise_set_session FOREIGN KEY (session_id) REFERENCES workout_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_set_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id)
);
CREATE INDEX ix_exercise_set_exercise ON exercise_set (exercise_id);

CREATE TABLE cardio_entry (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    session_id   BIGINT      NOT NULL,
    activity     VARCHAR(24) NOT NULL,
    duration_min DOUBLE      NOT NULL,
    distance_mi  DOUBLE,
    avg_hr       INT,
    calories     INT,
    notes        VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT fk_cardio_entry_session FOREIGN KEY (session_id) REFERENCES workout_session (id) ON DELETE CASCADE
);

-- Convert the flat legacy workout log (one row = exercise x sets x reps x weight):
--   rows on the same day become one session, names map to library exercises
--   when they match (else a personal exercise), and "sets = N" becomes N set rows.
INSERT INTO exercise (owner_id, name, muscle_group, created_at)
SELECT w.userid, MIN(TRIM(w.exercise_name)), 'OTHER', CURRENT_TIMESTAMP
FROM workout_information w
WHERE w.userid IS NOT NULL AND w.exercise_name IS NOT NULL AND TRIM(w.exercise_name) <> ''
  AND NOT EXISTS (SELECT 1 FROM exercise lib
                  WHERE lib.owner_id IS NULL AND LOWER(lib.name) = LOWER(TRIM(w.exercise_name)))
GROUP BY w.userid, LOWER(TRIM(w.exercise_name));

INSERT INTO workout_session (user_id, session_date, focus, notes, created_at)
SELECT w.userid, COALESCE(w.date, CURRENT_DATE), MIN(w.workout_type), 'Imported from the original workout log', CURRENT_TIMESTAMP
FROM workout_information w
WHERE w.userid IS NOT NULL AND w.exercise_name IS NOT NULL AND TRIM(w.exercise_name) <> ''
GROUP BY w.userid, COALESCE(w.date, CURRENT_DATE);

INSERT INTO exercise_set (session_id, exercise_id, sort_order, set_number, reps, weight_lb, warmup, notes)
SELECT s.id, e.id, w.id, n.n, w.reps, w.weight, FALSE, CASE WHEN n.n = 1 THEN NULLIF(TRIM(w.notes), '') END
FROM workout_information w
JOIN workout_session s ON s.user_id = w.userid AND s.session_date = COALESCE(w.date, CURRENT_DATE)
JOIN exercise e ON LOWER(e.name) = LOWER(TRIM(w.exercise_name)) AND (e.owner_id IS NULL OR e.owner_id = w.userid)
JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
      UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
      UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
      UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20) n
  ON n.n <= GREATEST(w.sets, 1)
WHERE w.userid IS NOT NULL;

-- Every legacy row now lives in workout_session / exercise_set.
DROP TABLE workout_information;

-- ---------------------------------------------------------------------------
-- Custom metrics (member-defined tracking: assessments, habits, anything numeric)
-- ---------------------------------------------------------------------------
CREATE TABLE custom_metric (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    name             VARCHAR(80) NOT NULL,
    unit             VARCHAR(24),
    category         VARCHAR(24),
    higher_is_better BOOLEAN     NOT NULL DEFAULT TRUE,
    archived         BOOLEAN     NOT NULL DEFAULT FALSE,
    description      VARCHAR(255),
    created_at       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_custom_metric_user FOREIGN KEY (user_id) REFERENCES user_login_details (id),
    CONSTRAINT uk_custom_metric_user_name UNIQUE (user_id, name)
);

CREATE TABLE custom_metric_entry (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    metric_id   BIGINT NOT NULL,
    recorded_on DATE   NOT NULL,
    metric_value DOUBLE NOT NULL,
    notes       VARCHAR(500),
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_custom_metric_entry_metric FOREIGN KEY (metric_id) REFERENCES custom_metric (id) ON DELETE CASCADE
);
CREATE INDEX ix_custom_metric_entry_metric_date ON custom_metric_entry (metric_id, recorded_on);

-- ---------------------------------------------------------------------------
-- Goals
-- ---------------------------------------------------------------------------
CREATE TABLE goal (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(120) NOT NULL,
    metric           VARCHAR(24)  NOT NULL,
    exercise_id      BIGINT,
    custom_metric_id BIGINT,
    start_value      DOUBLE,
    target_value     DOUBLE       NOT NULL,
    start_date       DATE         NOT NULL,
    target_date      DATE,
    status           VARCHAR(16)  NOT NULL,
    achieved_on      DATE,
    notes            VARCHAR(1000),
    created_at       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES user_login_details (id),
    CONSTRAINT fk_goal_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    CONSTRAINT fk_goal_custom_metric FOREIGN KEY (custom_metric_id) REFERENCES custom_metric (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Injuries & limitations
-- ---------------------------------------------------------------------------
CREATE TABLE limitation (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    body_area   VARCHAR(60)  NOT NULL,
    title       VARCHAR(120) NOT NULL,
    severity    INT,
    status      VARCHAR(16)  NOT NULL,
    started_on  DATE,
    resolved_on DATE,
    notes       VARCHAR(1000),
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_limitation_user FOREIGN KEY (user_id) REFERENCES user_login_details (id)
);
