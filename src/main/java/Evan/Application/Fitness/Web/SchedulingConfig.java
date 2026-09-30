package Evan.Application.Fitness.Web;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs: weekly summary emails and expired-link cleanup. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
