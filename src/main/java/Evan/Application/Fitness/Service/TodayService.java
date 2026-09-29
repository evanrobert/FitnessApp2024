package Evan.Application.Fitness.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Resolves "today" in the member's time zone rather than the database server's,
 * so a meal logged at 11pm lands on the right day.
 */
@Service
public class TodayService {
    private final Clock clock;
    private final ZoneId defaultZone;

    public TodayService(Clock clock, @Value("${app.default-time-zone}") String defaultZone) {
        this.clock = clock;
        this.defaultZone = ZoneId.of(defaultZone);
    }

    public LocalDate today(Long userId) {
        return LocalDate.now(clock.withZone(zoneFor(userId)));
    }

    public ZoneId zoneFor(Long userId) {
        return defaultZone;
    }
}
