package Evan.Application.Fitness;

import Evan.Application.Fitness.Web.ProdProfileCheck;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The hosted ("prod") profile refuses to start with settings that would break email links or the database. */
class ProdProfileCheckTests {
    private static MockEnvironment prod() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.username", "app");
        env.setProperty("spring.datasource.password", "synthetic-test-secret");
        env.setProperty("app.base-url", "https://evan-fitness.example.com");
        return env;
    }

    @Test
    void completeSettingsStart() {
        assertThatCode(() -> new ProdProfileCheck().postProcessEnvironment(prod(), null)).doesNotThrowAnyException();
    }

    @Test
    void localhostOrPlainHttpLinksAreRejected() {
        MockEnvironment env = prod();
        env.setProperty("app.base-url", "http://localhost:8080");
        assertThatThrownBy(() -> new ProdProfileCheck().postProcessEnvironment(env, null)).hasMessageContaining("APP_BASE_URL");
    }

    @Test
    void missingDatabaseCredentialsAreRejected() {
        MockEnvironment env = prod();
        env.setProperty("spring.datasource.password", "");
        assertThatThrownBy(() -> new ProdProfileCheck().postProcessEnvironment(env, null)).hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void otherProfilesAreNotChecked() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThatCode(() -> new ProdProfileCheck().postProcessEnvironment(env, null)).doesNotThrowAnyException();
    }
}
