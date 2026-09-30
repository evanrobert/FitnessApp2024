package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;

/**
 * Hourly check that sends each opted-in member their summary on Monday once it is
 * {@code app.weekly-email.hour} o'clock in their own time zone. The "last sent" claim
 * is a conditional update, so a restart, a missed hour or a second instance can't
 * send the same week twice.
 */
@Component
@ConditionalOnProperty(name = "app.weekly-email.enabled", havingValue = "true", matchIfMissing = true)
public class WeeklyEmailJob {
    private static final Logger log = LoggerFactory.getLogger(WeeklyEmailJob.class);

    private final UserLoginDetailsRepository users;
    private final WeeklySummaryService summaries;
    private final TodayService todayService;
    private final Clock clock;
    private final int sendHour;

    public WeeklyEmailJob(UserLoginDetailsRepository users, WeeklySummaryService summaries, TodayService todayService,
                          Clock clock, @Value("${app.weekly-email.hour:7}") int sendHour) {
        this.users = users;
        this.summaries = summaries;
        this.todayService = todayService;
        this.clock = clock;
        this.sendHour = sendHour;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void run() {
        int sent = 0;
        for (Long userId : users.weeklyEmailRecipients()) {
            try {
                if (sendIfDue(userId)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Weekly summary failed for member {}: {}", userId, e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Sent {} weekly summaries", sent);
        }
    }

    /** Sends when it is Monday after the send hour locally and this week's email hasn't gone out. */
    boolean sendIfDue(Long userId) {
        ZonedDateTime local = ZonedDateTime.now(clock.withZone(todayService.zoneFor(userId)));
        if (local.getDayOfWeek() != DayOfWeek.MONDAY || local.getHour() < sendHour) {
            return false;
        }
        return summaries.claim(userId, local.toLocalDate())
                && summaries.send(userId, WeeklySummaryService.lastWeekStart(local.toLocalDate()), false);
    }
}
