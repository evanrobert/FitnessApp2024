package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.*;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** Goals measure against live data; progress and pace are computed on read. */
@Service
public class GoalService {
    private final GoalRepository goals;
    private final BodyMeasurementRepository measurements;
    private final WorkoutSessionRepository sessions;
    private final CustomMetricRepository metrics;
    private final CustomMetricEntryRepository metricEntries;
    private final ExerciseRepository exercises;
    private final RecordsService records;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public GoalService(GoalRepository goals, BodyMeasurementRepository measurements, WorkoutSessionRepository sessions,
                       CustomMetricRepository metrics, CustomMetricEntryRepository metricEntries,
                       ExerciseRepository exercises, RecordsService records, UserLoginDetailsRepository users,
                       TodayService todayService) {
        this.goals = goals;
        this.measurements = measurements;
        this.sessions = sessions;
        this.metrics = metrics;
        this.metricEntries = metricEntries;
        this.exercises = exercises;
        this.records = records;
        this.users = users;
        this.todayService = todayService;
    }

    @Transactional(readOnly = true)
    public List<Progress> progressFor(Long userId) {
        return goals.findAllByUserIdOrderByStatusAscTargetDateAscIdDesc(userId).stream()
                .map(g -> progress(userId, g))
                .sorted(Comparator.comparing((Progress p) -> p.goal().getStatus()).thenComparing(p -> -p.pct()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Progress> active(Long userId) {
        return progressFor(userId).stream().filter(p -> p.goal().getStatus() == GoalStatus.ACTIVE).toList();
    }

    public Goal get(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Goal"));
    }

    public Goal blank(Long userId) {
        Goal g = new Goal();
        g.setStartDate(todayService.today(userId));
        g.setMetric(GoalMetric.BODY_WEIGHT);
        return g;
    }

    /** Returns an error message for an inconsistent goal, or null when it is valid. */
    public String problemWith(Long userId, Goal form, Long exerciseId, Long customMetricId) {
        if (form.getMetric() == GoalMetric.EXERCISE_1RM
                && (exerciseId == null || exercises.findVisible(exerciseId, userId).isEmpty())) {
            return "Pick the exercise this goal tracks";
        }
        if (form.getMetric() == GoalMetric.CUSTOM_METRIC
                && (customMetricId == null || metrics.findByIdAndUserId(customMetricId, userId).isEmpty())) {
            return "Pick the custom metric this goal tracks";
        }
        if (form.getTargetDate() != null && form.getStartDate() != null && form.getTargetDate().isBefore(form.getStartDate())) {
            return "The target date must be after the start date";
        }
        return null;
    }

    @Transactional
    public Goal save(Long userId, Long id, Goal form, Long exerciseId, Long customMetricId) {
        Goal goal = id == null ? new Goal() : get(userId, id);
        if (id == null) {
            goal.setUser(users.getReferenceById(userId));
            goal.setStatus(GoalStatus.ACTIVE);
        }
        goal.setTitle(form.getTitle().trim());
        goal.setMetric(form.getMetric());
        goal.setExercise(form.getMetric() == GoalMetric.EXERCISE_1RM ? exercises.findVisible(exerciseId, userId).orElse(null) : null);
        goal.setCustomMetric(form.getMetric() == GoalMetric.CUSTOM_METRIC ? metrics.findByIdAndUserId(customMetricId, userId).orElse(null) : null);
        goal.setTargetValue(form.getTargetValue());
        goal.setStartDate(form.getStartDate() == null ? todayService.today(userId) : form.getStartDate());
        goal.setTargetDate(form.getTargetDate());
        goal.setNotes(ProfileService.blankToNull(form.getNotes()));
        // Capture the starting point from real data when the member leaves it blank.
        goal.setStartValue(form.getStartValue() != null ? form.getStartValue() : currentValue(userId, goal));
        return goals.save(goal);
    }

    @Transactional
    public void setStatus(Long userId, Long id, GoalStatus status) {
        Goal goal = get(userId, id);
        goal.setStatus(status);
        goal.setAchievedOn(status == GoalStatus.ACHIEVED ? todayService.today(userId) : null);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        goals.delete(get(userId, id));
    }

    public Progress progress(Long userId, Goal goal) {
        Double current = currentValue(userId, goal);
        Double start = goal.getStartValue();
        double target = goal.getTargetValue();
        double pct;
        if (current == null) {
            pct = 0;
        } else if (start == null || start == target) {
            pct = reached(start, current, target) ? 100 : 0;
        } else {
            pct = clamp((current - start) / (target - start) * 100);
        }
        boolean reached = current != null && reached(start, current, target);
        LocalDate today = todayService.today(userId);
        Long daysLeft = goal.getTargetDate() == null ? null : ChronoUnit.DAYS.between(today, goal.getTargetDate());

        Pace pace;
        if (goal.getStatus() == GoalStatus.ACHIEVED || reached) {
            pace = Pace.REACHED;
        } else if (current == null) {
            pace = Pace.NO_DATA;
        } else if (goal.getTargetDate() == null) {
            pace = Pace.OPEN;
        } else {
            long total = Math.max(1, ChronoUnit.DAYS.between(goal.getStartDate(), goal.getTargetDate()));
            long elapsed = Math.max(0, ChronoUnit.DAYS.between(goal.getStartDate(), today));
            double expected = Math.min(100, elapsed * 100.0 / total);
            pace = daysLeft < 0 ? Pace.OVERDUE : pct + 10 >= expected ? Pace.ON_TRACK : Pace.BEHIND;
        }
        return new Progress(goal, current, start, pct, reached, pace, daysLeft, unit(goal));
    }

    private static boolean reached(Double start, double current, double target) {
        boolean decreasing = start != null && target < start;
        return decreasing ? current <= target : current >= target;
    }

    private static String unit(Goal goal) {
        if (goal.getMetric() == GoalMetric.CUSTOM_METRIC && goal.getCustomMetric() != null) {
            return goal.getCustomMetric().getUnit() == null ? "" : goal.getCustomMetric().getUnit();
        }
        return goal.getMetric().getUnit();
    }

    Double currentValue(Long userId, Goal goal) {
        return switch (goal.getMetric()) {
            case BODY_WEIGHT -> measurements.findFirstByUserIdAndWeightLbNotNullOrderByMeasuredOnDescIdDesc(userId)
                    .map(BodyMeasurement::getWeightLb).orElse(null);
            case BODY_FAT -> measurements.findFirstByUserIdAndBodyFatPctNotNullOrderByMeasuredOnDescIdDesc(userId)
                    .map(BodyMeasurement::getBodyFatPct).orElse(null);
            case WAIST -> measurements.findFirstByUserIdAndWaistInNotNullOrderByMeasuredOnDescIdDesc(userId)
                    .map(BodyMeasurement::getWaistIn).orElse(null);
            case EXERCISE_1RM -> goal.getExercise() == null ? null : records.series(userId, goal.getExercise().getId()).stream()
                    .mapToDouble(RecordsService.SessionPoint::topE1rm).max().stream().boxed().findFirst().orElse(null);
            case WORKOUTS_PER_WEEK -> {
                LocalDate today = todayService.today(userId);
                yield (double) sessions.findAllByUserIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(userId, today.minusDays(6), today).size();
            }
            case CUSTOM_METRIC -> goal.getCustomMetric() == null ? null
                    : metricEntries.findFirstByMetricIdOrderByRecordedOnDescIdDesc(goal.getCustomMetric().getId())
                    .map(CustomMetricEntry::getValue).orElse(null);
        };
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    public enum Pace {
        ON_TRACK("On track", "badge-good"), BEHIND("Behind pace", "badge-warn"), OVERDUE("Past target date", "badge-bad"),
        REACHED("Target reached", "badge-blue"), OPEN("No deadline", ""), NO_DATA("Waiting for data", "");

        private final String label;
        private final String css;

        Pace(String label, String css) {
            this.label = label;
            this.css = css;
        }

        public String getLabel() {
            return label;
        }

        public String getCss() {
            return css;
        }
    }

    public record Progress(Goal goal, Double current, Double start, double pct, boolean reached, Pace pace,
                           Long daysLeft, String unit) {
    }
}
