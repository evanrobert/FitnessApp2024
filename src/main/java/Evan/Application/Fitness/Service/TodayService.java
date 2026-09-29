package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Repositorys.UserInformationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Resolves "today" in the member's own time zone (profile setting, else the
 * app default), so a meal logged at 11pm lands on the right day.
 */
@Service
public class TodayService {
    private final Clock clock;
    private final ZoneId defaultZone;
    private final UserInformationRepository profiles;

    public TodayService(Clock clock, @Value("${app.default-time-zone}") String defaultZone,
                        UserInformationRepository profiles) {
        this.clock = clock;
        this.defaultZone = ZoneId.of(defaultZone);
        this.profiles = profiles;
    }

    public LocalDate today(Long userId) {
        return LocalDate.now(clock.withZone(zoneFor(userId)));
    }

    public ZoneId zoneFor(Long userId) {
        String zone = profiles.findByUserId(userId).map(UserInformation::getTimeZone).orElse(null);
        if (zone == null || zone.isBlank()) {
            return defaultZone;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException invalid) {
            return defaultZone;
        }
    }
}
