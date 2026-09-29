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
}
