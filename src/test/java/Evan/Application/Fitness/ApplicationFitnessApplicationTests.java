package Evan.Application.Fitness;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Boots the full context: runs every Flyway migration on H2 and validates the JPA mapping against it. */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationFitnessApplicationTests {

	@Test
	void contextLoads() {
	}
}
