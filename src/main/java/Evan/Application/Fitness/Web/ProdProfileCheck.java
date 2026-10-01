package Evan.Application.Fitness.Web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * With the "prod" profile, refuse to start on settings that would quietly break or
 * weaken the hosted app: no database credentials, email links pointing at localhost,
 * or a plain-http public address.
 */
public class ProdProfileCheck implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (env.getProperty("spring.datasource.username", "").isBlank() || env.getProperty("spring.datasource.password", "").isBlank()) {
            problems.add("Set DB_URL, DB_USERNAME and DB_PASSWORD to your hosted MySQL database.");
        }
        String baseUrl = env.getProperty("app.base-url", "");
        if (!baseUrl.startsWith("https://") || baseUrl.contains("localhost")) {
            problems.add("Set APP_BASE_URL to the app's public https address (e.g. https://evan-fitness.onrender.com) so email links work.");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("\n\nThe \"prod\" profile isn't configured:\n  - " + String.join("\n  - ", problems) + "\n");
        }
    }
}
