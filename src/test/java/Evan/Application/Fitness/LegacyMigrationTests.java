package Evan.Application.Fitness;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the upgrade path of a database created by the legacy app: build the
 * V1 (legacy) schema, load legacy-shaped rows, then apply every later migration.
 * Fixture rows are synthetic.
 */
class LegacyMigrationTests {
    JdbcTemplate jdbc;
    FluentConfiguration flyway;

    @BeforeEach
    void legacyDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:legacy-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("1").load().migrate();

        jdbc.execute("INSERT INTO roles (id, name) VALUES (1, 'ROLE_USER')");
        jdbc.execute("INSERT INTO user_information (id, age, name, weight, userid) VALUES (1, 34, 'Alex Test', 190, NULL), (2, 28, 'Blake Test', 150, NULL), (3, 40, 'Alex Dup', 200, NULL)");
        jdbc.execute("INSERT INTO user_login_details (id, password, username, user_information_id) VALUES (1, 'x', 'alex', 1), (2, 'x', 'blake', 2), (3, 'x', 'alex', 3)");
        jdbc.execute("UPDATE user_information SET userid = id WHERE id IN (1, 3)"); // blake's reverse link missing
        jdbc.execute("INSERT INTO users_roles VALUES (1, 1), (2, 1), (3, 1)");
        jdbc.execute("INSERT INTO user_macro_information (id, daily_calories, daily_carbohydrates, daily_fat, daily_protein, userid) VALUES (1, 2400, 250, 70, 170, 1), (2, 2600, 280, 80, 190, 1)");
        jdbc.execute("INSERT INTO calorie_information (id, calories, carbohydrates, cholesterol, date, fats, fiber, item_name, meal_type, proteins, sodium, sugars, userid) VALUES (1, 450, 70, 40, '2026-09-20', 10, 3, 'Oats', 'BREAKFAST', 20, 300, 5, 1)");
        jdbc.execute("INSERT INTO workout_information (id, date, exercise_name, notes, reps, sets, weight, workout_type, userid) VALUES "
                + "(1, '2026-09-20', 'Back Squat', 'Top set felt fast', 5, 5, 275, 'STRENGTH', 1), "
                + "(2, '2026-09-20', 'bench press ', '', 6, 4, 205, 'STRENGTH', 1), "
                + "(3, '2026-09-22', 'Zercher Squat', 'Grip limited', 10, 3, 185, 'HYPERTROPHY', 1), "
                + "(4, '2026-09-24', 'zercher squat', NULL, 3, 5, 205, 'STRENGTH', 1), "
                + "(5, '2026-09-24', 'Zercher Squat', NULL, 8, 2, 135, 'ACCESSORY', 2), "
                + "(6, '2026-09-26', 'goblet squat', 'Warm-up block', 12, 3, 60, 'ACCESSORY', 2)");
        jdbc.execute("INSERT INTO reports (id, file_name, file_type, userid) VALUES (1, 'synthetic.txt', 'text/plain', 1)");
    }

    @Test
    void upgradesLegacyDataWithoutLosingMemberRecords() {
        flyway.load().migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'reports'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT username FROM user_login_details ORDER BY id", String.class))
                .containsExactly("alex", "blake", "alex_dup3");
        assertThat(jdbc.queryForObject("SELECT userid FROM user_information WHERE id = 2", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT daily_calories FROM user_macro_information WHERE userid = 1", Double.class)).isEqualTo(2600.0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calorie_information", Integer.class)).isEqualTo(1);
    }

    @Test
    void convertsFlatWorkoutLogIntoSessionsExercisesAndSets() {
        flyway.load().migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'workout_information'", Integer.class)).isZero();
        // one session per member per day
        assertThat(jdbc.queryForList("SELECT CONCAT(user_id, '@', session_date) FROM workout_session ORDER BY user_id, session_date", String.class))
                .containsExactly("1@2026-09-20", "1@2026-09-22", "1@2026-09-24", "2@2026-09-24", "2@2026-09-26");
        // "sets = N" becomes N rows: 5 + 4 + 3 + 5 for member 1, 2 + 3 for member 2
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_set st JOIN workout_session s ON s.id = st.session_id WHERE s.user_id = 1", Integer.class)).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_set st JOIN workout_session s ON s.id = st.session_id WHERE s.user_id = 2", Integer.class)).isEqualTo(5);
        // names that match the library reuse it (case/whitespace-insensitive); others become personal exercises
        assertThat(jdbc.queryForList("SELECT DISTINCT e.owner_id FROM exercise_set st JOIN exercise e ON e.id = st.exercise_id WHERE LOWER(e.name) IN ('back squat', 'bench press')", Long.class))
                .containsOnlyNulls();
        assertThat(jdbc.queryForList("SELECT owner_id FROM exercise WHERE LOWER(name) = 'zercher squat' ORDER BY owner_id", Long.class))
                .containsExactly(1L, 2L);
        // notes survive on the first set of each legacy row; load and reps are preserved
        assertThat(jdbc.queryForList("SELECT notes FROM exercise_set WHERE notes IS NOT NULL ORDER BY notes", String.class))
                .containsExactly("Grip limited", "Top set felt fast", "Warm-up block");
        assertThat(jdbc.queryForObject("SELECT SUM(reps * weight_lb) FROM exercise_set", Double.class))
                .isEqualTo(5 * 5 * 275.0 + 4 * 6 * 205.0 + 3 * 10 * 185.0 + 5 * 3 * 205.0 + 2 * 8 * 135.0 + 3 * 12 * 60.0);
    }

    @Test
    void expandedLibraryAbsorbsMatchingPersonalExercisesWithoutLosingHistory() {
        // V3 turned member 2's "goblet squat" into a personal exercise (not in the V3 library).
        flyway.target("3").load().migrate();
        Long personal = jdbc.queryForObject("SELECT id FROM exercise WHERE owner_id = 2 AND LOWER(name) = 'goblet squat'", Long.class);
        jdbc.update("INSERT INTO goal (user_id, title, metric, exercise_id, target_value, start_date, status) VALUES (2, 'Goblet 100', 'EXERCISE_1RM', ?, 100, '2026-09-01', 'ACTIVE')", personal);

        flyway.target("latest").load().migrate();

        Long library = jdbc.queryForObject("SELECT id FROM exercise WHERE owner_id IS NULL AND name = 'Goblet Squat'", Long.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise WHERE owner_id IS NULL", Integer.class)).isEqualTo(132);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise WHERE id = ?", Integer.class, personal)).isZero();
        assertThat(jdbc.queryForList("SELECT DISTINCT exercise_id FROM exercise_set st JOIN workout_session s ON s.id = st.session_id WHERE s.session_date = '2026-09-26'", Long.class))
                .containsExactly(library);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_set WHERE exercise_id = ?", Integer.class, library)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT exercise_id FROM goal WHERE title = 'Goblet 100'", Long.class)).isEqualTo(library);
        // Personal exercises with no library twin are untouched.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise WHERE owner_id IS NOT NULL AND LOWER(name) = 'zercher squat'", Integer.class)).isEqualTo(2);
    }

    @Test
    void movesProfileAgeAndWeightIntoBirthYearAndFirstWeighIn() {
        flyway.load().migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'user_information' AND column_name IN ('age', 'weight')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT birth_year FROM user_information WHERE id = 1", Integer.class))
                .isEqualTo(java.time.Year.now().getValue() - 34);
        assertThat(jdbc.queryForList("SELECT weight_lb FROM body_measurement ORDER BY user_id", Double.class))
                .containsExactly(190.0, 150.0, 200.0);
    }
}
