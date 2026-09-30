package Evan.Application.Fitness.Web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * With the "local" profile (your own MySQL), stop at startup with a clear
 * instruction when config/application-local.yml hasn't been created or filled
 * in, instead of a generic connection error further down.
 */
public class LocalProfileCheck implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("local")) {
            return;
        }
        String username = env.getProperty("spring.datasource.username", "");
        String password = env.getProperty("spring.datasource.password", "");
        if (username.isBlank() || password.isBlank() || "CHANGE_ME".equals(password)) {
            throw new IllegalStateException("""

                    MySQL credentials are not set for the "local" profile.
                      1. Copy config/application-local.example.yml to config/application-local.yml
                      2. Enter your MySQL username and password in the new file
                      3. Start again from the project folder:  gradlew bootRun -Pprofile=local
                    (Create the database and user first with config/mysql-setup.sql in MySQL Workbench.)
                    """);
        }
    }
}
