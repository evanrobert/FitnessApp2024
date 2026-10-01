package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Web.ChartJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns logged data into the dashboard, insights, achievements and timeline.
 * Everything is derived on read from the member's own records; insights only
 * appear when there is enough data behind them to be true.
 */
@Service
public class AnalyticsService {
    private static final Set<MuscleGroup> MAJOR_GROUPS = EnumSet.of(MuscleGroup.CHEST, MuscleGroup.BACK,
            MuscleGroup.SHOULDERS, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES);

    private final TrainingService training;
    private final NutritionService nutrition;
    private final CheckInService checkIns;
    private final BodyService body;
    private final GoalService goals;
    private final RecordsService records;
    private final MetricService metrics;
    private final LimitationService limitations;
    private final ProfileService profiles;
    private final TodayService todayService;
    private final Fmt fmt;

    public AnalyticsService(TrainingService training, NutritionService nutrition, CheckInService checkIns,
                            BodyService body, GoalService goals, RecordsService records, MetricService metrics,
                            LimitationService limitations, ProfileService profiles, TodayService todayService, Fmt fmt) {
        this.training = training;
        this.nutrition = nutrition;
        this.checkIns = checkIns;
        this.body = body;
        this.goals = goals;
        this.records = records;
        this.metrics = metrics;
        this.limitations = limitations;
        this.profiles = profiles;
        this.todayService = todayService;
        this.fmt = fmt;
    }

    // =====================================================================
    // Data snapshot: loaded once per request, shared by every calculation
    // =====================================================================

    @Transactional(readOnly = true)
    public Snapshot snapshot(Long userId) {
        LocalDate today = todayService.today(userId);
        UserInformation profile = profiles.profile(userId);
        return new Snapshot(userId, today, profile,
                training.history(userId),
                nutrition.entriesFor(userId),
                checkIns.history(userId),
                body.chronological(userId),
                nutrition.targetsFor(userId).orElse(null),
                records.summary(userId),
                goals.progressFor(userId),
                limitations.current(userId),
                metrics.allEntries(userId));
    }

    public record Snapshot(Long userId, LocalDate today, UserInformation profile, List<WorkoutSession> sessions,
                           List<CalorieInformation> meals, List<DailyCheckIn> checkIns, List<BodyMeasurement> measurements,
                           UserMacroInformation targets, RecordsService.Summary records, List<GoalService.Progress> goals,
                           List<Limitation> limitations, List<CustomMetricEntry> metricEntries) {
        int weeklyTarget() {
            return profile.weeklyTargetOrDefault();
        }

        LocalDate weekStart() {
            return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        List<WorkoutSession> sessionsBetween(LocalDate from, LocalDate to) {
            return sessions.stream().filter(s -> !s.getSessionDate().isBefore(from) && !s.getSessionDate().isAfter(to)).toList();
        }

        Map<LocalDate, List<CalorieInformation>> mealsByDay() {
            return meals.stream().filter(m -> m.getDate() != null)
                    .collect(Collectors.groupingBy(CalorieInformation::getDate, TreeMap::new, Collectors.toList()));
        }

        Optional<DailyCheckIn> checkInOn(LocalDate day) {
            return checkIns.stream().filter(c -> c.getCheckInDate().equals(day)).findFirst();
        }

        List<BodyMeasurement> weighIns() {
            return measurements.stream().filter(m -> m.getWeightLb() != null).toList();
        }
    }

    // =====================================================================
    // Dashboard ("Today")
    // =====================================================================

    public record TodayTask(String label, String detail, boolean done, String href, String icon) {
    }

    public record Dashboard(Snapshot data, NutritionService.DayTotals todayTotals, DailyCheckIn todayCheckIn,
                            List<WorkoutSession> todaySessions, int weekSessions, double weekVolume, Streak streak,
                            BodyMeasurement latestWeight, Double weightChange7, List<TodayTask> tasks,
                            List<Insight> insights, List<RecordsService.PrEvent> recentPrs, List<TimelineDay> recent,
                            Map<String, Object> calendar, Map<String, Object> weightSpark, int tasksDone) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long userId) {
        Snapshot d = snapshot(userId);
        LocalDate today = d.today();
        List<CalorieInformation> todayMeals = d.meals().stream().filter(m -> today.equals(m.getDate())).toList();
        NutritionService.DayTotals totals = nutrition.totals(todayMeals);
        DailyCheckIn checkIn = d.checkInOn(today).orElse(null);
        List<WorkoutSession> todaySessions = d.sessionsBetween(today, today);
        List<WorkoutSession> week = d.sessionsBetween(d.weekStart(), today);
        List<BodyMeasurement> weighIns = d.weighIns();
        BodyMeasurement latest = weighIns.isEmpty() ? null : weighIns.get(weighIns.size() - 1);

        List<TodayTask> tasks = new ArrayList<>();
        tasks.add(new TodayTask("Say how you feel", checkIn != null && checkIn.readinessScore() != null
                ? "Ready-to-train score " + checkIn.readinessScore() : "3 quick questions, 30 seconds", checkIn != null, "/recover", "recover"));
        tasks.add(new TodayTask("Add what you eat", todayMeals.isEmpty() ? "Nothing added yet today"
                : todayMeals.size() + (todayMeals.size() == 1 ? " thing · " : " things · ") + fmt.num(totals.calories()) + " calories", !todayMeals.isEmpty(), "/fuel", "fuel"));
        boolean weekDone = week.size() >= d.weeklyTarget();
        tasks.add(new TodayTask(todaySessions.isEmpty() ? (weekDone ? "Weekly goal done" : "Work out") : "Worked out today",
                todaySessions.isEmpty() ? week.size() + " of " + d.weeklyTarget() + " workouts this week"
                        : todaySessions.get(0).displayTitle() + " · " + fmt.compact(todaySessions.stream().mapToDouble(WorkoutSession::volume).sum()) + " lb lifted",
                !todaySessions.isEmpty() || weekDone, todaySessions.isEmpty() ? "/train/new" : "/train/" + todaySessions.get(0).getId(), "train"));
        Double waterTarget = d.targets() == null ? null : d.targets().getDailyWaterOz();
        double water = checkIn == null || checkIn.getWaterOz() == null ? 0 : checkIn.getWaterOz();
        tasks.add(new TodayTask("Drink water", fmt.num(water) + (waterTarget != null ? " of " + fmt.num(waterTarget) : "") + " oz",
                waterTarget != null ? water >= waterTarget : water > 0, "/recover", "water"));
        boolean weightGoal = d.goals().stream().anyMatch(p -> p.goal().getStatus() == GoalStatus.ACTIVE
                && EnumSet.of(GoalMetric.BODY_WEIGHT, GoalMetric.BODY_FAT, GoalMetric.WAIST).contains(p.goal().getMetric()));
        if (weightGoal || latest == null) {
            boolean recent = latest != null && ChronoUnit.DAYS.between(latest.getMeasuredOn(), today) < 7;
            tasks.add(new TodayTask("Weigh yourself", latest == null ? "No weight added yet" : "Last " + fmt.relative(latest.getMeasuredOn(), today),
                    recent, "/body#weigh-in", "scale"));
        }

        Map<String, Object> spark = weighIns.size() < 2 ? null : ChartJson.spec("spark",
                "values", ChartJson.rollingAverage(weighIns.stream().skip(Math.max(0, weighIns.size() - 30)).map(BodyMeasurement::getWeightLb).toList(), 5),
                "color", "s1");

        return new Dashboard(d, totals, checkIn, todaySessions, week.size(), week.stream().mapToDouble(WorkoutSession::volume).sum(),
                streak(d), latest, weightChange(weighIns, 7), tasks, insights(d).stream().limit(4).toList(),
                d.records().events().stream().limit(4).toList(), timeline(d, 7, null).stream().limit(3).toList(),
                calendar(d, 26), spark, (int) tasks.stream().filter(TodayTask::done).count());
    }

    // =====================================================================
    // Insights page
    // =====================================================================

    public record Insights(Snapshot data, int days, LocalDate from, int sessions, double perWeek, double volume,
                           double cardioMinutes, Double avgCalories, Double proteinAdherence, Double calorieAdherence,
                           Double avgSleep, Double avgReadiness, Double weightChange, List<Insight> insights,
                           List<MuscleShare> muscleBalance, List<Comparison> comparisons, List<Achievement> achievements,
                           Map<String, Map<String, Object>> charts, int loggedFoodDays) {
    }

    public record MuscleShare(MuscleGroup group, long sets, double pct) {
    }

    public record Comparison(String title, String leftLabel, String rightLabel, String metric, String left, String right,
                             String note) {
    }

    @Transactional(readOnly = true)
    public Insights insights(Long userId, int days) {
        Snapshot d = snapshot(userId);
        LocalDate today = d.today();
        LocalDate from = today.minusDays(days - 1L);
        List<WorkoutSession> range = d.sessionsBetween(from, today);
        int weeks = Math.max(1, (int) Math.ceil(days / 7.0));

        // Weekly series (sessions & volume) over the range, Monday-aligned.
        LocalDate firstWeek = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<String> weekLabels = new ArrayList<>();
        List<Double> weekSessions = new ArrayList<>(), weekVolume = new ArrayList<>(), weekCardio = new ArrayList<>();
        for (LocalDate w = firstWeek; !w.isAfter(today); w = w.plusWeeks(1)) {
            List<WorkoutSession> inWeek = d.sessionsBetween(w, w.plusDays(6));
            weekLabels.add(w.toString());
            weekSessions.add((double) inWeek.size());
            weekVolume.add((double) Math.round(inWeek.stream().mapToDouble(WorkoutSession::volume).sum()));
            weekCardio.add((double) Math.round(inWeek.stream().mapToDouble(WorkoutSession::cardioMinutes).sum()));
        }

        // Daily nutrition series over the range (days with no food logged are gaps, not zeros).
        Map<LocalDate, List<CalorieInformation>> byDay = d.mealsByDay();
        List<String> dayLabels = new ArrayList<>();
        List<Double> dayCalories = new ArrayList<>(), dayProtein = new ArrayList<>(), sleep = new ArrayList<>(), readiness = new ArrayList<>();
        Map<LocalDate, DailyCheckIn> checkInByDay = d.checkIns().stream().collect(Collectors.toMap(DailyCheckIn::getCheckInDate, Function.identity(), (a, b) -> a));
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            dayLabels.add(day.toString());
            // Today is still in progress: a partial day would read as a sudden drop.
            List<CalorieInformation> meals = day.equals(today) ? null : byDay.get(day);
            dayCalories.add(meals == null ? null : (double) Math.round(meals.stream().mapToDouble(CalorieInformation::getCalories).sum()));
            dayProtein.add(meals == null ? null : (double) Math.round(meals.stream().mapToDouble(CalorieInformation::getProteins).sum()));
            DailyCheckIn c = checkInByDay.get(day);
            sleep.add(c == null ? null : c.getSleepHours());
            readiness.add(c == null || c.readinessScore() == null ? null : c.readinessScore().doubleValue());
        }
        List<Double> loggedCalories = dayCalories.stream().filter(Objects::nonNull).toList();
        List<Double> loggedProtein = dayProtein.stream().filter(Objects::nonNull).toList();
        UserMacroInformation t = d.targets();
        Double calorieAdherence = t == null || t.getDailyCalories() <= 0 || loggedCalories.isEmpty() ? null
                : loggedCalories.stream().filter(c -> Math.abs(c - t.getDailyCalories()) <= t.getDailyCalories() * 0.1).count() * 100.0 / loggedCalories.size();
        Double proteinAdherence = t == null || t.getDailyProtein() <= 0 || loggedProtein.isEmpty() ? null
                : Math.min(150, loggedProtein.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100 / t.getDailyProtein());

        List<DailyCheckIn> rangeCheckIns = d.checkIns().stream().filter(c -> !c.getCheckInDate().isBefore(from)).toList();
        Double avgSleep = average(rangeCheckIns.stream().map(DailyCheckIn::getSleepHours).toList());
        Double avgReadiness = average(rangeCheckIns.stream().map(c -> c.readinessScore() == null ? null : c.readinessScore().doubleValue()).toList());

        List<BodyMeasurement> weighIns = d.weighIns();
        List<BodyMeasurement> rangeWeights = weighIns.stream().filter(m -> !m.getMeasuredOn().isBefore(from)).toList();
        Double weightChange = rangeWeights.size() < 2 ? null
                : round1(rangeWeights.get(rangeWeights.size() - 1).getWeightLb() - rangeWeights.get(0).getWeightLb());

        Map<String, Map<String, Object>> charts = new LinkedHashMap<>();
        charts.put("frequency", ChartJson.spec("bar", "labels", weekLabels, "values", weekSessions, "name", "Sessions",
                "unit", "sessions", "decimals", 0, "target", ChartJson.target(d.weeklyTarget(), "Target"),
                "highlight", weekLabels.size() - 1, "title", "Sessions per week"));
        charts.put("volume", ChartJson.spec("bar", "labels", weekLabels, "values", weekVolume, "name", "Volume",
                "unit", "lb", "highlight", weekLabels.size() - 1, "title", "Weekly training volume", "empty", "Log weighted sets to see volume."));
        charts.put("cardio", ChartJson.spec("bar", "labels", weekLabels, "values", weekCardio, "name", "Cardio",
                "unit", "min", "color", "s3", "highlight", weekLabels.size() - 1, "title", "Weekly cardio minutes", "empty", "Add cardio blocks to sessions to see this."));
        charts.put("calories", ChartJson.spec("line", "labels", dayLabels,
                "series", List.of(ChartJson.series("Calories", dayCalories, "s1", null)), "unit", "kcal", "decimals", 0,
                "target", t != null && t.getDailyCalories() > 0 ? ChartJson.target(t.getDailyCalories(), "Target") : null,
                "title", "Daily calories", "empty", "Log meals on two or more days to see intake."));
        charts.put("protein", ChartJson.spec("line", "labels", dayLabels,
                "series", List.of(ChartJson.series("Protein", dayProtein, "s2", null)), "unit", "g", "decimals", 0,
                "target", t != null && t.getDailyProtein() > 0 ? ChartJson.target(t.getDailyProtein(), "Target") : null,
                "title", "Daily protein", "empty", "Log meals with protein to see this."));
        List<BodyMeasurement> weightWindow = weighIns.stream().filter(m -> !m.getMeasuredOn().isBefore(from.minusDays(14))).toList();
        List<Double> w = weightWindow.stream().map(BodyMeasurement::getWeightLb).toList();
        charts.put("weight", ChartJson.spec("line", "labels", weightWindow.stream().map(m -> m.getMeasuredOn().toString()).toList(),
                "series", List.of(ChartJson.series("Weigh-ins", w, "s1", "dots"), ChartJson.series("Trend", ChartJson.rollingAverage(w, 7), "s1", null)),
                "unit", "lb", "decimals", 1, "title", "Body weight", "empty", "Weigh in on two or more days to see the trend."));
        charts.put("sleep", ChartJson.spec("line", "labels", dayLabels, "series", List.of(ChartJson.series("Sleep", sleep, "s3", null)),
                "unit", "h", "decimals", 1, "target", ChartJson.target(8, "8 h"), "height", 180, "title", "Sleep", "empty", "Log sleep in daily check-ins."));
        charts.put("readiness", ChartJson.spec("line", "labels", dayLabels, "series", List.of(ChartJson.series("Ready-to-train score", readiness, "s1", null)),
                "decimals", 0, "height", 180, "title", "Ready-to-train score", "empty", "Answer the 3 daily questions to see this."));
        charts.put("calendar", calendar(d, Math.max(26, weeks)));

        return new Insights(d, days, from, range.size(), range.size() / (days / 7.0),
                range.stream().mapToDouble(WorkoutSession::volume).sum(),
                range.stream().mapToDouble(WorkoutSession::cardioMinutes).sum(),
                average(loggedCalories), proteinAdherence, calorieAdherence, avgSleep, avgReadiness, weightChange,
                insights(d), muscleBalance(range), comparisons(d, from), achievements(d), charts, loggedCalories.size());
    }

    List<MuscleShare> muscleBalance(List<WorkoutSession> sessions) {
        Map<MuscleGroup, Long> counts = new EnumMap<>(MuscleGroup.class);
        sessions.forEach(s -> s.getSets().stream().filter(st -> !st.isWarmup())
                .forEach(st -> counts.merge(st.getExercise().getMuscleGroup(), 1L, Long::sum)));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream().sorted(Map.Entry.<MuscleGroup, Long>comparingByValue().reversed())
                .map(e -> new MuscleShare(e.getKey(), e.getValue(), total == 0 ? 0 : e.getValue() * 100.0 / total)).toList();
    }

    List<Comparison> comparisons(Snapshot d, LocalDate from) {
        List<Comparison> out = new ArrayList<>();
        // Sleep vs next-morning energy (same-day check-in holds last night's sleep).
        List<DailyCheckIn> withBoth = d.checkIns().stream().filter(c -> c.getSleepHours() != null && c.getEnergy() != null).toList();
        List<DailyCheckIn> longSleep = withBoth.stream().filter(c -> c.getSleepHours() >= 7).toList();
        List<DailyCheckIn> shortSleep = withBoth.stream().filter(c -> c.getSleepHours() < 7).toList();
        if (longSleep.size() >= 3 && shortSleep.size() >= 3) {
            out.add(new Comparison("Sleep and energy", "7h+ nights", "Under 7h", "Avg energy (1-5)",
                    fmt.dec(avg(longSleep, c -> c.getEnergy().doubleValue())), fmt.dec(avg(shortSleep, c -> c.getEnergy().doubleValue())),
                    longSleep.size() + " vs " + shortSleep.size() + " check-ins, all time"));
        }
        // Readiness on days you trained vs rested.
        Set<LocalDate> trainingDays = d.sessions().stream().map(WorkoutSession::getSessionDate).collect(Collectors.toSet());
        List<DailyCheckIn> withReadiness = d.checkIns().stream().filter(c -> c.readinessScore() != null).toList();
        List<DailyCheckIn> afterTraining = withReadiness.stream().filter(c -> trainingDays.contains(c.getCheckInDate().minusDays(1))).toList();
        List<DailyCheckIn> afterRest = withReadiness.stream().filter(c -> !trainingDays.contains(c.getCheckInDate().minusDays(1))).toList();
        if (afterTraining.size() >= 3 && afterRest.size() >= 3) {
            out.add(new Comparison("Recovery after training", "Day after training", "Day after rest", "Avg readiness",
                    fmt.num(avg(afterTraining, c -> c.readinessScore().doubleValue())), fmt.num(avg(afterRest, c -> c.readinessScore().doubleValue())),
                    "Readiness the morning after"));
        }
        // Nutrition on training vs rest days.
        Map<LocalDate, List<CalorieInformation>> byDay = d.mealsByDay();
        List<Double> trainCal = new ArrayList<>(), restCal = new ArrayList<>(), trainPro = new ArrayList<>(), restPro = new ArrayList<>();
        byDay.forEach((day, meals) -> {
            if (day.isBefore(from)) {
                return;
            }
            double cal = meals.stream().mapToDouble(CalorieInformation::getCalories).sum();
            double pro = meals.stream().mapToDouble(CalorieInformation::getProteins).sum();
            if (trainingDays.contains(day)) {
                trainCal.add(cal);
                trainPro.add(pro);
            } else {
                restCal.add(cal);
                restPro.add(pro);
            }
        });
        if (trainCal.size() >= 3 && restCal.size() >= 3) {
            out.add(new Comparison("Fuel on training days", "Training days", "Rest days", "Avg calories",
                    fmt.num(average(trainCal)), fmt.num(average(restCal)), "Protein " + fmt.num(average(trainPro)) + " g vs " + fmt.num(average(restPro)) + " g"));
        }
        return out;
    }

    // =====================================================================
    // Insight rules
    // =====================================================================

    public enum Tone { GOOD, WARN, INFO }

    public record Insight(Tone tone, String title, String body, String href) {
        public String css() {
            return switch (tone) {
                case GOOD -> "is-good";
                case WARN -> "is-warn";
                case INFO -> "is-info";
            };
        }
    }

    List<Insight> insights(Snapshot d) {
        List<Insight> out = new ArrayList<>();
        LocalDate today = d.today();

        // Readiness vs yesterday's load.
        d.checkInOn(today).map(DailyCheckIn::readinessScore).ifPresent(score -> {
            boolean hardYesterday = d.sessionsBetween(today.minusDays(1), today.minusDays(1)).stream()
                    .anyMatch(s -> s.getSessionRpe() != null && s.getSessionRpe() >= 8);
            if (score < 50) {
                out.add(new Insight(Tone.WARN, "Low ready-to-train score today (" + score + ")",
                        hardYesterday ? "Yesterday was a hard session. A lighter day or extra rest may pay off." : "Consider easing today's intensity, and check sleep and stress.", "/recover"));
            } else if (score >= 80) {
                out.add(new Insight(Tone.GOOD, "High ready-to-train score (" + score + ")", "A good day to push a top set or a harder session.", "/train/new"));
            }
        });

        // Weekly training frequency.
        int target = d.weeklyTarget();
        int done = d.sessionsBetween(d.weekStart(), today).size();
        long daysLeft = ChronoUnit.DAYS.between(today, d.weekStart().plusDays(6));
        if (done >= target) {
            out.add(new Insight(Tone.GOOD, "Weekly training target hit", done + " of " + target + " sessions done this week.", "/train"));
        } else if (!d.sessions().isEmpty()) {
            int left = target - done;
            out.add(new Insight(left > daysLeft + 1 ? Tone.WARN : Tone.INFO, left + " session" + (left == 1 ? "" : "s") + " to go this week",
                    done + " of " + target + " done with " + (daysLeft + 1) + " day" + (daysLeft == 0 ? "" : "s") + " left, including today.", "/train/new"));
        }

        // Protein and calorie adherence over the last 7 logged days.
        UserMacroInformation t = d.targets();
        List<Map.Entry<LocalDate, List<CalorieInformation>>> lastLogged = d.mealsByDay().entrySet().stream()
                .filter(e -> !e.getKey().isAfter(today) && e.getKey().isAfter(today.minusDays(14)))
                .sorted(Map.Entry.<LocalDate, List<CalorieInformation>>comparingByKey().reversed()).limit(7).toList();
        if (t != null && t.getDailyProtein() > 0 && lastLogged.size() >= 4) {
            double avgProtein = lastLogged.stream().mapToDouble(e -> e.getValue().stream().mapToDouble(CalorieInformation::getProteins).sum()).average().orElse(0);
            double pct = avgProtein * 100 / t.getDailyProtein();
            if (pct < 85) {
                out.add(new Insight(Tone.WARN, "Protein is running at " + Math.round(pct) + "% of target",
                        "You've averaged " + fmt.num(avgProtein) + " g against " + fmt.num(t.getDailyProtein()) + " g over your last " + lastLogged.size() + " logged days.", "/fuel"));
            } else if (pct >= 95) {
                out.add(new Insight(Tone.GOOD, "Protein on target", fmt.num(avgProtein) + " g a day on average over your last " + lastLogged.size() + " logged days.", "/fuel"));
            }
        }
        if (t != null && t.getDailyCalories() > 0 && lastLogged.size() >= 4) {
            long onTarget = lastLogged.stream().filter(e -> Math.abs(e.getValue().stream().mapToDouble(CalorieInformation::getCalories).sum()
                    - t.getDailyCalories()) <= t.getDailyCalories() * 0.1).count();
            out.add(new Insight(onTarget * 2 >= lastLogged.size() ? Tone.GOOD : Tone.INFO,
                    onTarget + " of " + lastLogged.size() + " logged days within 10% of your calorie target",
                    "Consistency matters more than any single day.", "/insights"));
        }

        // Weight trend vs goal direction.
        List<BodyMeasurement> weighIns = d.weighIns();
        Double rate = weeklyRate(weighIns, today);
        PrimaryGoal goal = d.profile().getPrimaryGoal();
        if (rate != null && goal != null) {
            String rateText = fmt.signed(rate) + " lb/week over the last 4 weeks";
            if (goal == PrimaryGoal.LOSE_FAT && rate > 0.1) {
                out.add(new Insight(Tone.WARN, "Weight is trending up", rateText + ". Your goal is fat loss: a 200-300 kcal reduction usually restarts progress.", "/body"));
            } else if (goal == PrimaryGoal.LOSE_FAT && rate < -1.5) {
                out.add(new Insight(Tone.WARN, "Losing faster than recommended", rateText + ". Faster than ~1% a week risks muscle; consider eating a little more.", "/body"));
            } else if (goal == PrimaryGoal.BUILD_MUSCLE && rate < 0) {
                out.add(new Insight(Tone.WARN, "Weight is trending down", rateText + ". Building muscle usually needs a small surplus.", "/fuel/targets"));
            } else if (goal == PrimaryGoal.LOSE_FAT || goal == PrimaryGoal.BUILD_MUSCLE) {
                out.add(new Insight(Tone.GOOD, "Weight trend matches your goal", rateText + ".", "/body"));
            }
        }

        // Stale weigh-ins while a body goal is active.
        boolean bodyGoal = d.goals().stream().anyMatch(p -> p.goal().getStatus() == GoalStatus.ACTIVE && p.goal().getMetric() == GoalMetric.BODY_WEIGHT);
        if (bodyGoal && (weighIns.isEmpty() || ChronoUnit.DAYS.between(weighIns.get(weighIns.size() - 1).getMeasuredOn(), today) > 10)) {
            out.add(new Insight(Tone.INFO, "Time for a weigh-in", "Your weight goal needs recent data to track progress.", "/body#weigh-in"));
        }

        // Goals behind pace.
        d.goals().stream().filter(p -> p.goal().getStatus() == GoalStatus.ACTIVE && p.pace() == GoalService.Pace.BEHIND).limit(2)
                .forEach(p -> out.add(new Insight(Tone.WARN, "\"" + p.goal().getTitle() + "\" is behind pace",
                        Math.round(p.pct()) + "% done" + (p.daysLeft() != null ? " with " + p.daysLeft() + " days left." : "."), "/goals")));
        d.goals().stream().filter(p -> p.goal().getStatus() == GoalStatus.ACTIVE && p.reached()).limit(2)
                .forEach(p -> out.add(new Insight(Tone.GOOD, "Target reached: " + p.goal().getTitle(), "Mark it achieved to log the milestone.", "/goals")));

        // Recent PRs.
        List<RecordsService.PrEvent> recentPrs = d.records().events().stream().filter(e -> e.date().isAfter(today.minusDays(30))).toList();
        if (!recentPrs.isEmpty()) {
            RecordsService.PrEvent best = recentPrs.stream().max(Comparator.comparingDouble(RecordsService.PrEvent::gain)).orElseThrow();
            out.add(new Insight(Tone.GOOD, recentPrs.size() + " PR" + (recentPrs.size() == 1 ? "" : "s") + " in the last 30 days",
                    best.exercise().getName() + " " + best.kind().getLabel().toLowerCase() + " up " + fmt.dec(best.gain()) + " lb.", "/records"));
        }

        // Muscle balance over 4 weeks.
        List<WorkoutSession> month = d.sessionsBetween(today.minusDays(27), today);
        Map<MuscleGroup, Long> sets = new EnumMap<>(MuscleGroup.class);
        month.forEach(s -> s.getSets().stream().filter(st -> !st.isWarmup()).forEach(st -> sets.merge(st.getExercise().getMuscleGroup(), 1L, Long::sum)));
        long totalSets = sets.values().stream().mapToLong(Long::longValue).sum();
        if (totalSets >= 30) {
            List<String> missing = MAJOR_GROUPS.stream().filter(g -> sets.getOrDefault(g, 0L) == 0).map(g -> g.getLabel().toLowerCase()).toList();
            if (!missing.isEmpty()) {
                out.add(new Insight(Tone.INFO, "No " + String.join(", ", missing) + " work in 4 weeks",
                        totalSets + " working sets logged, none for " + String.join(", ", missing) + ". Intentional?", "/insights#balance"));
            }
        }

        // Hydration.
        Double waterTarget = t == null ? null : t.getDailyWaterOz();
        List<Double> water = d.checkIns().stream().filter(c -> c.getCheckInDate().isAfter(today.minusDays(7)) && c.getWaterOz() != null)
                .map(DailyCheckIn::getWaterOz).toList();
        if (waterTarget != null && waterTarget > 0 && water.size() >= 4) {
            double avgWater = average(water);
            if (avgWater < waterTarget * 0.8) {
                out.add(new Insight(Tone.INFO, "Hydration below target", "Averaging " + fmt.num(avgWater) + " oz against " + fmt.num(waterTarget) + " oz this week.", "/recover"));
            }
        }

        // Membership value.
        BigDecimal cost = d.profile().getMembershipMonthlyCost();
        if (cost != null && cost.signum() > 0) {
            long visits = d.sessionsBetween(today.minusDays(29), today).stream().filter(s -> s.getLocationType() == LocationType.GYM).count();
            out.add(new Insight(visits >= 8 ? Tone.GOOD : Tone.INFO, visits == 0 ? "No gym visits logged in 30 days"
                    : "$" + cost.divide(BigDecimal.valueOf(visits), 2, RoundingMode.HALF_UP) + " per gym visit",
                    visits + " gym session" + (visits == 1 ? "" : "s") + " in the last 30 days at $" + cost.setScale(2, RoundingMode.HALF_UP) + "/month.", "/profile"));
        }

        // Active limitations.
        if (!d.limitations().isEmpty()) {
            out.add(new Insight(Tone.INFO, "Training around " + d.limitations().size() + " limitation" + (d.limitations().size() == 1 ? "" : "s"),
                    d.limitations().stream().map(Limitation::getBodyArea).collect(Collectors.joining(", ")) + " — shown when you log workouts.", "/profile#limitations"));
        }

        out.sort(Comparator.comparingInt(i -> i.tone() == Tone.WARN ? 0 : i.tone() == Tone.GOOD ? 1 : 2));
        return out;
    }

    // =====================================================================
    // Streaks & achievements
    // =====================================================================

    public record Streak(int current, int longest, boolean activeToday) {
    }

    /** Consecutive days with anything logged; today still counts as "alive" until it ends. */
    Streak streak(Snapshot d) {
        TreeSet<LocalDate> days = activeDays(d);
        int longest = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate day : days) {
            run = prev != null && prev.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = day;
        }
        boolean today = days.contains(d.today());
        LocalDate cursor = today ? d.today() : d.today().minusDays(1);
        int current = 0;
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new Streak(current, longest, today);
    }

    private static TreeSet<LocalDate> activeDays(Snapshot d) {
        TreeSet<LocalDate> days = new TreeSet<>();
        d.sessions().forEach(s -> days.add(s.getSessionDate()));
        d.meals().forEach(m -> { if (m.getDate() != null) days.add(m.getDate()); });
        d.checkIns().forEach(c -> days.add(c.getCheckInDate()));
        d.measurements().forEach(m -> days.add(m.getMeasuredOn()));
        d.metricEntries().forEach(e -> days.add(e.getRecordedOn()));
        return days;
    }

    public record Achievement(String code, String title, String description, boolean earned, LocalDate earnedOn,
                              double progress, String progressText) {
    }

    List<Achievement> achievements(Snapshot d) {
        List<Achievement> out = new ArrayList<>();
        List<WorkoutSession> chrono = new ArrayList<>(d.sessions());
        chrono.sort(Comparator.comparing(WorkoutSession::getSessionDate).thenComparing(WorkoutSession::getId));
        for (int n : new int[]{1, 10, 25, 50, 100, 250}) {
            out.add(countAchievement("S" + n, n == 1 ? "First session" : n + " sessions", n == 1 ? "Log your first workout" : "Log " + n + " training sessions",
                    chrono.stream().map(WorkoutSession::getSessionDate).toList(), n));
        }
        // Lifetime volume milestones.
        double running = 0;
        LocalDate hit100k = null, hit1m = null;
        for (WorkoutSession s : chrono) {
            running += s.volume();
            if (hit100k == null && running >= 100_000) hit100k = s.getSessionDate();
            if (hit1m == null && running >= 1_000_000) hit1m = s.getSessionDate();
        }
        out.add(new Achievement("V100K", "100K club", "Lift 100,000 lb in total", hit100k != null, hit100k, Math.min(1, running / 100_000), fmt.compact(running) + " / 100K lb"));
        out.add(new Achievement("V1M", "Million-pound club", "Lift 1,000,000 lb in total", hit1m != null, hit1m, Math.min(1, running / 1_000_000), fmt.compact(running) + " / 1M lb"));

        List<LocalDate> prDates = d.records().events().stream().map(RecordsService.PrEvent::date).sorted().toList();
        out.add(countAchievement("PR1", "First PR", "Beat a previous best", prDates, 1));
        out.add(countAchievement("PR10", "10 PRs", "Set ten personal records", prDates, 10));

        List<LocalDate> checkInDates = d.checkIns().stream().map(DailyCheckIn::getCheckInDate).sorted().toList();
        out.add(streakAchievement("C7", "7-day check-in streak", "Check in seven days in a row", checkInDates, 7));
        out.add(streakAchievement("C30", "30-day check-in streak", "Check in thirty days in a row", checkInDates, 30));

        List<LocalDate> active = new ArrayList<>(activeDays(d));
        out.add(streakAchievement("L7", "One-week streak", "Log something seven days running", active, 7));
        out.add(streakAchievement("L30", "30-day streak", "Log something thirty days running", active, 30));

        List<LocalDate> mealDays = d.meals().stream().map(CalorieInformation::getDate).filter(Objects::nonNull).sorted().toList();
        out.add(countAchievement("M100", "100 meals logged", "Log one hundred food entries", mealDays, 100));

        List<LocalDate> weighDates = d.weighIns().stream().map(BodyMeasurement::getMeasuredOn).sorted().toList();
        out.add(countAchievement("W10", "Ten weigh-ins", "Weigh in ten times", weighDates, 10));

        List<LocalDate> achieved = d.goals().stream().filter(p -> p.goal().getStatus() == GoalStatus.ACHIEVED && p.goal().getAchievedOn() != null)
                .map(p -> p.goal().getAchievedOn()).sorted().toList();
        out.add(countAchievement("G1", "Goal crusher", "Achieve a goal", achieved, 1));

        // Consecutive weeks meeting the weekly session target (completed weeks + current week if already met).
        int target = d.weeklyTarget(), weeksInRow = 0, bestRun = 0;
        LocalDate bestRunEnd = null;
        LocalDate firstWeek = chrono.isEmpty() ? d.weekStart() : chrono.get(0).getSessionDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate w = firstWeek; !w.isAfter(d.weekStart()); w = w.plusWeeks(1)) {
            boolean met = d.sessionsBetween(w, w.plusDays(6)).size() >= target;
            if (met) {
                weeksInRow++;
                if (weeksInRow > bestRun) {
                    bestRun = weeksInRow;
                    bestRunEnd = w.plusDays(6);
                }
            } else if (!w.equals(d.weekStart())) {
                weeksInRow = 0;
            }
        }
        out.add(new Achievement("WK4", "Four-week consistency", "Hit your weekly session target four weeks in a row", bestRun >= 4,
                bestRun >= 4 ? bestRunEnd : null, Math.min(1, bestRun / 4.0), bestRun + " / 4 weeks"));
        return out;
    }

    private Achievement countAchievement(String code, String title, String description, List<LocalDate> sortedDates, int n) {
        boolean earned = sortedDates.size() >= n;
        return new Achievement(code, title, description, earned, earned ? sortedDates.get(n - 1) : null,
                Math.min(1, sortedDates.size() / (double) n), Math.min(sortedDates.size(), n) + " / " + n);
    }

    private static Achievement streakAchievement(String code, String title, String description, List<LocalDate> sortedDates, int n) {
        int run = 0, best = 0;
        LocalDate prev = null, earnedOn = null;
        for (LocalDate day : new TreeSet<>(sortedDates)) {
            run = prev != null && prev.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            if (earnedOn == null && run >= n) {
                earnedOn = day;
            }
            prev = day;
        }
        return new Achievement(code, title, description, earnedOn != null, earnedOn, Math.min(1, best / (double) n), Math.min(best, n) + " / " + n + " days");
    }

    // =====================================================================
    // Timeline
    // =====================================================================

    public enum EventType {
        TRAINING("Training", "train"), NUTRITION("Nutrition", "fuel"), RECOVERY("Recovery", "recover"),
        BODY("Body", "scale"), METRIC("Metrics", "metrics"), MILESTONE("Milestones", "trophy");

        private final String label;
        private final String icon;

        EventType(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }

        public String getLabel() {
            return label;
        }

        public String getIcon() {
            return icon;
        }
    }

    public record TimelineEvent(LocalDate date, EventType type, String title, String meta, String value, String href, String iconClass) {
    }

    public record TimelineDay(LocalDate date, List<TimelineEvent> events) {
    }

    @Transactional(readOnly = true)
    public List<TimelineDay> timeline(Long userId, int days, EventType filter) {
        return timeline(snapshot(userId), days, filter);
    }

    List<TimelineDay> timeline(Snapshot d, int days, EventType filter) {
        LocalDate from = d.today().minusDays(days - 1L);
        List<TimelineEvent> events = new ArrayList<>();
        for (WorkoutSession s : d.sessions()) {
            if (s.getSessionDate().isBefore(from)) continue;
            String parts = s.workingSetCount() + " sets" + (s.getCardio().isEmpty() ? "" : " · " + fmt.minutes(s.cardioMinutes()) + " cardio")
                    + (s.getDurationMin() != null ? " · " + fmt.minutes(s.getDurationMin()) : "");
            events.add(new TimelineEvent(s.getSessionDate(), EventType.TRAINING, s.displayTitle(),
                    (s.exerciseSummary().isBlank() ? "" : s.exerciseSummary() + " — ") + parts,
                    s.volume() > 0 ? fmt.compact(s.volume()) + " lb" : null, "/train/" + s.getId(), "is-train"));
        }
        // One milestone per session, however many records it set.
        d.records().events().stream().filter(p -> !p.date().isBefore(from))
                .collect(Collectors.groupingBy(RecordsService.PrEvent::sessionId, LinkedHashMap::new, Collectors.toList()))
                .forEach((sessionId, prs) -> {
                    List<String> lifts = prs.stream().map(p -> p.exercise().getName()).distinct().toList();
                    RecordsService.PrEvent biggest = prs.stream().max(Comparator.comparingDouble(RecordsService.PrEvent::gain)).orElseThrow();
                    events.add(new TimelineEvent(biggest.date(), EventType.MILESTONE,
                            lifts.size() == 1 ? "PR · " + lifts.get(0) : lifts.size() + " lifts hit PRs",
                            String.join(", ", lifts) + " — biggest: " + biggest.exercise().getName() + " +" + fmt.dec(biggest.gain()) + " lb",
                            prs.size() + (prs.size() == 1 ? " record" : " records"), "/train/" + sessionId, "is-pr"));
                });
        d.mealsByDay().forEach((day, meals) -> {
            if (day.isBefore(from)) return;
            double kcal = meals.stream().mapToDouble(CalorieInformation::getCalories).sum();
            double protein = meals.stream().mapToDouble(CalorieInformation::getProteins).sum();
            events.add(new TimelineEvent(day, EventType.NUTRITION, meals.size() + (meals.size() == 1 ? " food entry" : " food entries"),
                    fmt.num(protein) + " g protein · " + meals.stream().map(CalorieInformation::getItemName).filter(Objects::nonNull).limit(3).collect(Collectors.joining(", ")),
                    fmt.num(kcal) + " kcal", "/fuel?date=" + day, ""));
        });
        for (DailyCheckIn c : d.checkIns()) {
            if (c.getCheckInDate().isBefore(from)) continue;
            List<String> bits = new ArrayList<>();
            if (c.getSleepHours() != null) bits.add(fmt.dec(c.getSleepHours()) + " h sleep");
            if (c.getEnergy() != null) bits.add("energy " + c.getEnergy() + "/5");
            if (c.getWaterOz() != null) bits.add(fmt.num(c.getWaterOz()) + " oz water");
            if (c.getSteps() != null) bits.add(fmt.num(c.getSteps()) + " steps");
            events.add(new TimelineEvent(c.getCheckInDate(), EventType.RECOVERY, "Check-in", String.join(" · ", bits),
                    c.readinessScore() == null ? null : "Ready-to-train score " + c.readinessScore(), "/recover?date=" + c.getCheckInDate(), ""));
        }
        for (BodyMeasurement m : d.measurements()) {
            if (m.getMeasuredOn().isBefore(from)) continue;
            List<String> bits = new ArrayList<>();
            if (m.getBodyFatPct() != null) bits.add(fmt.dec(m.getBodyFatPct()) + "% body fat");
            if (m.getWaistIn() != null) bits.add(fmt.dec(m.getWaistIn()) + " in waist");
            if (m.getNotes() != null) bits.add(m.getNotes());
            events.add(new TimelineEvent(m.getMeasuredOn(), EventType.BODY, m.getWeightLb() != null ? "Weigh-in" : "Measurements",
                    String.join(" · ", bits), m.getWeightLb() == null ? null : fmt.dec(m.getWeightLb()) + " lb", "/body", ""));
        }
        for (CustomMetricEntry e : d.metricEntries()) {
            if (e.getRecordedOn().isBefore(from)) continue;
            events.add(new TimelineEvent(e.getRecordedOn(), EventType.METRIC, e.getMetric().getName(), e.getNotes() == null ? "" : e.getNotes(),
                    fmt.dec(e.getValue()) + (e.getMetric().getUnit() == null ? "" : " " + e.getMetric().getUnit()), "/metrics/" + e.getMetric().getId(), ""));
        }
        for (GoalService.Progress p : d.goals()) {
            if (p.goal().getAchievedOn() != null && !p.goal().getAchievedOn().isBefore(from)) {
                events.add(new TimelineEvent(p.goal().getAchievedOn(), EventType.MILESTONE, "Goal achieved", p.goal().getTitle(), null, "/goals", "is-goal"));
            }
        }
        return events.stream()
                .filter(e -> filter == null || e.type() == filter)
                .collect(Collectors.groupingBy(TimelineEvent::date, () -> new TreeMap<LocalDate, List<TimelineEvent>>(Comparator.reverseOrder()), Collectors.toList()))
                .entrySet().stream().map(e -> new TimelineDay(e.getKey(), e.getValue())).toList();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Training calendar: sessions per day for the last N weeks (Monday-aligned). */
    Map<String, Object> calendar(Snapshot d, int weeks) {
        LocalDate start = d.weekStart().minusWeeks(weeks - 1L);
        Map<LocalDate, Long> perDay = d.sessions().stream().filter(s -> !s.getSessionDate().isBefore(start))
                .collect(Collectors.groupingBy(WorkoutSession::getSessionDate, Collectors.counting()));
        List<Long> values = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(d.today()); day = day.plusDays(1)) {
            values.add(perDay.getOrDefault(day, 0L));
        }
        return ChartJson.spec("calendar", "start", start.toString(), "values", values, "unit", "session(s)", "max", 2,
                "title", "Training calendar", "empty", "Sessions you log appear here.");
    }

    public static Double weightChange(List<BodyMeasurement> weighIns, int days) {
        if (weighIns.size() < 2) return null;
        BodyMeasurement latest = weighIns.get(weighIns.size() - 1);
        BodyMeasurement base = null;
        for (BodyMeasurement m : weighIns) {
            if (!m.getMeasuredOn().isAfter(latest.getMeasuredOn().minusDays(days))) base = m;
        }
        return base == null ? null : round1(latest.getWeightLb() - base.getWeightLb());
    }

    /** Least-squares slope of weigh-ins over the last 28 days, lb per week. */
    public static Double weeklyRate(List<BodyMeasurement> weighIns, LocalDate today) {
        List<BodyMeasurement> recent = weighIns.stream().filter(m -> !m.getMeasuredOn().isBefore(today.minusDays(28))).toList();
        if (recent.size() < 3) return null;
        LocalDate origin = recent.get(0).getMeasuredOn();
        double n = recent.size(), sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (BodyMeasurement m : recent) {
            double x = ChronoUnit.DAYS.between(origin, m.getMeasuredOn());
            sx += x; sy += m.getWeightLb(); sxx += x * x; sxy += x * m.getWeightLb();
        }
        double denom = n * sxx - sx * sx;
        return denom == 0 ? null : Math.round((n * sxy - sx * sy) / denom * 7 * 100) / 100.0;
    }

    private static Double average(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }

    private static <T> double avg(List<T> items, Function<T, Double> f) {
        return items.stream().mapToDouble(f::apply).average().orElse(0);
    }

    private static Double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
