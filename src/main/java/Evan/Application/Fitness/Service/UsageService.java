package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.UsageEvent;
import Evan.Application.Fitness.Repositorys.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Counts how members log (one tap, typed, pasted, live) and how long it takes, so changes meant
 * to make logging quicker can be checked. Stores no content; reports are totals only.
 */
@Service
public class UsageService {
    private static final Logger log = LoggerFactory.getLogger(UsageService.class);
    public static final int KEEP_DAYS = 180;

    private final UsageEventRepository events;
    private final TodayService todayService;
    private final Clock clock;

    public UsageService(UsageEventRepository events, TodayService todayService, Clock clock) {
        this.events = events;
        this.todayService = todayService;
        this.clock = clock;
    }

    /** Best effort: a failure here never affects the member's save. */
    public void record(Long userId, String kind, String method, Integer seconds) {
        try {
            UsageEvent e = new UsageEvent();
            e.setUserId(userId);
            e.setKind(kind);
            e.setMethod(method);
            e.setSeconds(seconds);
            e.setOccurredOn(todayService.today(userId));
            events.save(e);
        } catch (RuntimeException ex) {
            log.warn("Usage event not recorded: {}", ex.getMessage());
        }
    }

    /** One row of the report: e.g. food logged by one tap. */
    public record MethodRow(String kind, String method, int count, Integer medianSeconds) {
    }

    public record Report(int days, int activeMembers, double loggingDaysPerMemberPerWeek, int totalLogs,
                         List<MethodRow> methods, List<String> dayLabels, List<Integer> membersPerDay) {
    }

    public Report report(int days) {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);
        List<UsageEvent> all = events.findAllByOccurredOnBetween(from, to.plusDays(1)); // members ahead of UTC
        Set<Long> members = all.stream().map(UsageEvent::getUserId).collect(Collectors.toSet());
        long memberDays = all.stream().map(e -> e.getUserId() + "|" + e.getOccurredOn()).distinct().count();
        double perWeek = members.isEmpty() ? 0 : memberDays / (double) members.size() / (days / 7.0);

        Map<String, List<UsageEvent>> byMethod = all.stream()
                .collect(Collectors.groupingBy(e -> e.getKind() + "|" + e.getMethod(), TreeMap::new, Collectors.toList()));
        List<MethodRow> rows = new ArrayList<>();
        byMethod.forEach((key, list) -> {
            String[] parts = key.split("\\|", 2);
            rows.add(new MethodRow(parts[0], parts[1], list.size(), median(list)));
        });
        rows.sort(Comparator.comparing(MethodRow::kind).thenComparing(r -> -r.count()));

        List<String> labels = new ArrayList<>();
        List<Integer> perDay = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate day = d;
            labels.add(day.toString());
            perDay.add((int) all.stream().filter(e -> e.getOccurredOn().equals(day)).map(UsageEvent::getUserId).distinct().count());
        }
        return new Report(days, members.size(), Math.round(perWeek * 10) / 10.0, all.size(), rows, labels, perDay);
    }

    private static Integer median(List<UsageEvent> list) {
        List<Integer> secs = list.stream().map(UsageEvent::getSeconds).filter(Objects::nonNull).sorted().toList();
        return secs.isEmpty() ? null : secs.get(secs.size() / 2);
    }

    /** Nightly: drop events past the retention window. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeOld() {
        int n = events.deleteOlderThan(LocalDate.now(clock).minusDays(KEEP_DAYS));
        if (n > 0) {
            log.info("Removed {} usage events older than {} days", n, KEEP_DAYS);
        }
    }
}
