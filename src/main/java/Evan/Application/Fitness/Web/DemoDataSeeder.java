package Evan.Application.Fitness.Web;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.*;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.TodayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Local development only: seeds a synthetic member ("demo" / "demo-password")
 * with ~16 weeks of made-up training, nutrition, recovery and body data so every
 * dashboard and chart has something to show. Never active outside the dev profile.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int WEEKS = 16;

    private final AccountService accounts;
    private final UserLoginDetailsRepository users;
    private final UserInformationRepository profiles;
    private final UserMacroInformationRepository targets;
    private final ExerciseRepository exercises;
    private final WorkoutSessionRepository sessions;
    private final CalorieInformationRepository meals;
    private final DailyCheckInRepository checkIns;
    private final BodyMeasurementRepository measurements;
    private final GoalRepository goals;
    private final CustomMetricRepository metrics;
    private final CustomMetricEntryRepository metricEntries;
    private final LimitationRepository limitations;
    private final TodayService todayService;
    private final Random rnd = new Random(42);

    public DemoDataSeeder(AccountService accounts, UserLoginDetailsRepository users, UserInformationRepository profiles,
                          UserMacroInformationRepository targets, ExerciseRepository exercises, WorkoutSessionRepository sessions,
                          CalorieInformationRepository meals, DailyCheckInRepository checkIns, BodyMeasurementRepository measurements,
                          GoalRepository goals, CustomMetricRepository metrics, CustomMetricEntryRepository metricEntries,
                          LimitationRepository limitations, TodayService todayService) {
        this.accounts = accounts;
        this.users = users;
        this.profiles = profiles;
        this.targets = targets;
        this.exercises = exercises;
        this.sessions = sessions;
        this.meals = meals;
        this.checkIns = checkIns;
        this.measurements = measurements;
        this.goals = goals;
        this.metrics = metrics;
        this.metricEntries = metricEntries;
        this.limitations = limitations;
        this.todayService = todayService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByUsername("demo").isPresent()) {
            return;
        }
        SignupForm form = new SignupForm();
        form.setUsername("demo");
        form.setPassword("demo-password");
        form.setEmail("demo@example.com");
        form.setName("Demo Member");
        UserLoginDetails demo = accounts.register(form);
        // Synthetic address on a reserved domain, pre-confirmed so reset and weekly-summary previews work in dev.
        demo.setEmailVerifiedAt(java.time.LocalDateTime.now());
        users.save(demo);
        LocalDate today = todayService.today(demo.getId());
        LocalDate start = today.minusWeeks(WEEKS).with(DayOfWeek.MONDAY);

        profile(demo);
        nutritionTargets(demo);
        Map<String, Exercise> lib = new HashMap<>();
        exercises.findVisibleTo(demo.getId()).forEach(e -> lib.put(e.getName(), e));
        training(demo, lib, start, today);
        food(demo, start, today);
        recovery(demo, start, today);
        body(demo, start, today);
        goalsAndMetrics(demo, lib, today);
        log.info("Seeded demo member 'demo' / 'demo-password' with {} weeks of synthetic data", WEEKS);
    }

    private void profile(UserLoginDetails demo) {
        UserInformation p = profiles.findByUserId(demo.getId()).orElseThrow();
        p.setBirthYear(1991);
        p.setSex(Sex.MALE);
        p.setHeightIn(70.0);
        p.setActivityLevel(ActivityLevel.MODERATE);
        p.setPrimaryGoal(PrimaryGoal.GET_STRONGER);
        p.setExperienceLevel(ExperienceLevel.INTERMEDIATE);
        p.setWeeklyWorkoutTarget(4);
        p.setTimeZone("America/New_York");
        p.setBio("Powerbuilding block: squat, bench, deadlift up; stay under 190.");
        p.setGymName("Iron District");
        p.setMembershipPlan("Monthly");
        p.setMembershipStartedOn(LocalDate.now().minusMonths(14).withDayOfMonth(1));
        p.setMembershipRenewsOn(LocalDate.now().plusDays(12));
        p.setMembershipMonthlyCost(new BigDecimal("59.00"));
        profiles.save(p);
    }

    private void nutritionTargets(UserLoginDetails demo) {
        UserMacroInformation t = new UserMacroInformation();
        t.setUser(demo);
        t.setDailyCalories(2800);
        t.setDailyProtein(180);
        t.setDailyCarbohydrates(310);
        t.setDailyFat(85);
        t.setDailyFiber(35.0);
        t.setDailyWaterOz(100.0);
        targets.save(t);
    }

    private record Lift(String name, double startLoad, double weeklyGain, int reps, int sets) {
    }

    private void training(UserLoginDetails demo, Map<String, Exercise> lib, LocalDate start, LocalDate today) {
        Map<DayOfWeek, List<Lift>> plan = Map.of(
                DayOfWeek.MONDAY, List.of(new Lift("Back Squat", 255, 3.5, 5, 4), new Lift("Romanian Deadlift", 185, 2.5, 8, 3), new Lift("Leg Press", 360, 5, 10, 3), new Lift("Standing Calf Raise", 150, 2, 12, 3)),
                DayOfWeek.TUESDAY, List.of(new Lift("Bench Press", 195, 2.2, 5, 4), new Lift("Barbell Row", 165, 2, 8, 4), new Lift("Dumbbell Shoulder Press", 55, 0.6, 10, 3), new Lift("Triceps Pushdown", 60, 0.8, 12, 3)),
                DayOfWeek.THURSDAY, List.of(new Lift("Deadlift", 325, 4.5, 3, 3), new Lift("Front Squat", 185, 2.5, 6, 3), new Lift("Walking Lunge", 45, 0.8, 10, 3), new Lift("Hanging Leg Raise", 0, 0, 12, 3)),
                DayOfWeek.SATURDAY, List.of(new Lift("Overhead Press", 115, 1.4, 5, 4), new Lift("Pull-Up", 0, 0, 8, 4), new Lift("Incline Bench Press", 155, 1.8, 8, 3), new Lift("Dumbbell Curl", 30, 0.4, 12, 3)));
        Map<DayOfWeek, String> titles = Map.of(DayOfWeek.MONDAY, "Lower A", DayOfWeek.TUESDAY, "Upper A",
                DayOfWeek.THURSDAY, "Lower B", DayOfWeek.SATURDAY, "Upper B");

        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            List<Lift> lifts = plan.get(day.getDayOfWeek());
            if (lifts == null || rnd.nextDouble() < 0.14) {
                if (day.getDayOfWeek() == DayOfWeek.WEDNESDAY && rnd.nextDouble() < 0.6) {
                    cardioOnly(demo, day);
                }
                continue;
            }
            int week = (int) (java.time.temporal.ChronoUnit.DAYS.between(start, day) / 7);
            boolean deload = week % 6 == 5;
            WorkoutSession s = new WorkoutSession();
            s.setUser(demo);
            s.setSessionDate(day);
            s.setTitle(titles.get(day.getDayOfWeek()));
            s.setFocus(day.getDayOfWeek() == DayOfWeek.SATURDAY ? SessionFocus.HYPERTROPHY : SessionFocus.STRENGTH);
            s.setLocationType(LocationType.GYM);
            s.setDurationMin(55 + rnd.nextInt(30));
            s.setSessionRpe(deload ? 6 : 7 + rnd.nextInt(3));
            if (deload) {
                s.setNotes("Deload week — 85% loads, crisp reps.");
            }
            int block = 0;
            for (Lift lift : lifts) {
                Exercise exercise = lib.get(lift.name());
                double load = lift.startLoad() == 0 ? 0 : round5((lift.startLoad() + lift.weeklyGain() * week) * (deload ? 0.85 : 1) + (rnd.nextInt(3) - 1) * 5);
                int setNo = 1;
                if (block == 0 && load > 0) {
                    for (double warm : new double[]{0.5, 0.7}) {
                        s.addSet(set(exercise, block, setNo++, 5, round5(load * warm), null, true));
                    }
                }
                for (int i = 0; i < lift.sets(); i++) {
                    int reps = Math.max(1, lift.reps() + (i == lift.sets() - 1 ? -rnd.nextInt(2) : 0));
                    Double rpe = block == 0 ? 7.0 + i * 0.5 + (deload ? -1 : 0) : null;
                    s.addSet(set(exercise, block, setNo++, reps, load == 0 ? null : load, rpe, false));
                }
                block++;
            }
            if (rnd.nextDouble() < 0.35) {
                CardioEntry c = new CardioEntry();
                c.setActivity(CardioActivity.BIKE);
                c.setDurationMin(10 + rnd.nextInt(10));
                c.setDistanceMi(round1(c.getDurationMin() * 0.28));
                c.setAvgHr(125 + rnd.nextInt(20));
                s.addCardio(c);
            }
            sessions.save(s);
        }
    }

    private void cardioOnly(UserLoginDetails demo, LocalDate day) {
        WorkoutSession s = new WorkoutSession();
        s.setUser(demo);
        s.setSessionDate(day);
        s.setTitle("Zone 2 run");
        s.setFocus(SessionFocus.CARDIO);
        s.setLocationType(LocationType.OUTDOORS);
        s.setSessionRpe(5);
        CardioEntry run = new CardioEntry();
        run.setActivity(CardioActivity.RUN);
        run.setDurationMin(28 + rnd.nextInt(14));
        run.setDistanceMi(round1(run.getDurationMin() / (9.2 + rnd.nextDouble())));
        run.setAvgHr(138 + rnd.nextInt(10));
        run.setCalories(300 + rnd.nextInt(120));
        s.setDurationMin((int) Math.round(run.getDurationMin()));
        s.addCardio(run);
        sessions.save(s);
    }

    private static ExerciseSet set(Exercise e, int block, int number, int reps, Double load, Double rpe, boolean warmup) {
        ExerciseSet st = new ExerciseSet();
        st.setExercise(e);
        st.setSortOrder(block);
        st.setSetNumber(number);
        st.setReps(reps);
        st.setWeightLb(load);
        st.setRpe(rpe);
        st.setWarmup(warmup);
        return st;
    }

    private record Food(String name, String meal, double kcal, double p, double c, double f, double fiber, double sodium) {
    }

    private void food(UserLoginDetails demo, LocalDate start, LocalDate today) {
        List<Food> breakfast = List.of(new Food("Oats, whey & berries", "BREAKFAST", 560, 42, 72, 11, 9, 180),
                new Food("Eggs, toast & avocado", "BREAKFAST", 620, 32, 44, 34, 8, 640),
                new Food("Greek yogurt bowl", "BREAKFAST", 480, 38, 58, 10, 6, 150));
        List<Food> lunch = List.of(new Food("Chicken burrito bowl", "LUNCH", 820, 58, 92, 22, 12, 1180),
                new Food("Turkey sandwich & fruit", "LUNCH", 640, 44, 78, 14, 7, 1320),
                new Food("Salmon rice bowl", "LUNCH", 760, 46, 80, 26, 5, 890));
        List<Food> dinner = List.of(new Food("Steak, potatoes & greens", "DINNER", 880, 62, 70, 36, 8, 720),
                new Food("Pasta bolognese", "DINNER", 920, 48, 112, 28, 9, 980),
                new Food("Chicken stir-fry & rice", "DINNER", 780, 55, 96, 16, 6, 1400));
        List<Food> extras = List.of(new Food("Protein shake", "POST_WORKOUT", 260, 48, 10, 4, 1, 220),
                new Food("Banana & peanut butter", "PRE_WORKOUT", 330, 9, 38, 17, 5, 140),
                new Food("Trail mix", "SNACK", 290, 8, 24, 19, 3, 90));
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (rnd.nextDouble() < 0.1 && !day.equals(today)) {
                continue;
            }
            List<Food> eaten = new ArrayList<>(List.of(pick(breakfast), pick(lunch)));
            if (!day.equals(today)) {
                eaten.add(pick(dinner));
            }
            if (rnd.nextDouble() < 0.75) {
                eaten.add(pick(extras));
            }
            if (rnd.nextDouble() < 0.35) {
                eaten.add(pick(extras));
            }
            for (Food f : eaten) {
                double scale = 0.9 + rnd.nextDouble() * 0.25;
                CalorieInformation c = new CalorieInformation();
                c.setUser(demo);
                c.setDate(day);
                c.setItemName(f.name());
                c.setMealType(f.meal());
                c.setCalories((double) Math.round(f.kcal() * scale));
                c.setProteins(round1(f.p() * scale));
                c.setCarbohydrates(round1(f.c() * scale));
                c.setFats(round1(f.f() * scale));
                c.setFiber(round1(f.fiber() * scale));
                c.setSodium((double) Math.round(f.sodium() * scale));
                c.setSugars(round1(f.c() * 0.18 * scale));
                meals.save(c);
            }
        }
    }

    private void recovery(UserLoginDetails demo, LocalDate start, LocalDate today) {
        double hr = 60;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            hr = Math.max(51, hr - 0.06 + (rnd.nextDouble() - 0.5) * 0.4);
            if (rnd.nextDouble() < 0.18) {
                continue;
            }
            double sleep = round1(Math.max(5, Math.min(9, 7.1 + rnd.nextGaussian() * 0.8)));
            int energy = clamp((int) Math.round(1 + (sleep - 5) * 0.9 + rnd.nextGaussian() * 0.6));
            DailyCheckIn c = new DailyCheckIn();
            c.setUser(demo);
            c.setCheckInDate(day);
            c.setSleepHours(sleep);
            c.setSleepQuality(clamp((int) Math.round(energy + rnd.nextGaussian() * 0.6)));
            c.setEnergy(energy);
            c.setMood(clamp((int) Math.round(3.4 + (energy - 3) * 0.5 + rnd.nextGaussian() * 0.5)));
            c.setStress(clamp((int) Math.round(2.6 + rnd.nextGaussian() * 0.8)));
            c.setSoreness(clamp((int) Math.round((day.getDayOfWeek() == DayOfWeek.TUESDAY || day.getDayOfWeek() == DayOfWeek.FRIDAY ? 3.4 : 2.2) + rnd.nextGaussian() * 0.6)));
            c.setWaterOz((double) (64 + rnd.nextInt(6) * 8));
            c.setSteps(5500 + rnd.nextInt(7000));
            c.setRestingHr((int) Math.round(hr));
            if (sleep < 6) {
                c.setNotes("Short night — late work call.");
            }
            checkIns.save(c);
        }
    }

    private void body(UserLoginDetails demo, LocalDate start, LocalDate today) {
        measurements.findAllByUserIdOrderByMeasuredOnDescIdDesc(demo.getId()).forEach(measurements::delete);
        int i = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1), i++) {
            if (rnd.nextDouble() > 0.6 && !day.equals(today)) {
                continue;
            }
            BodyMeasurement m = new BodyMeasurement();
            m.setUser(demo);
            m.setMeasuredOn(day);
            m.setWeightLb(round1(181.5 + i * 0.045 + rnd.nextGaussian() * 0.7));
            if (day.getDayOfMonth() <= 2 || day.equals(today)) {
                m.setWaistIn(round1(33.8 + i * 0.004 + rnd.nextGaussian() * 0.1));
                m.setBodyFatPct(round1(16.8 - i * 0.004 + rnd.nextGaussian() * 0.2));
                m.setChestIn(round1(42.0 + i * 0.006));
                m.setArmIn(round1(15.2 + i * 0.003));
                m.setNotes("Monthly tape check, morning, fasted");
            }
            measurements.save(m);
        }
    }

    private void goalsAndMetrics(UserLoginDetails demo, Map<String, Exercise> lib, LocalDate today) {
        goals.save(goal(demo, "Squat 405 (e1RM)", GoalMetric.EXERCISE_1RM, lib.get("Back Squat"), null, 330.0, 405.0, today.minusWeeks(10), today.plusWeeks(6)));
        goals.save(goal(demo, "Stay under 190 lb", GoalMetric.BODY_WEIGHT, null, null, 183.0, 189.5, today.minusWeeks(12), today.plusWeeks(8)));
        goals.save(goal(demo, "Train 4× a week", GoalMetric.WORKOUTS_PER_WEEK, null, null, 0.0, 4.0, today.minusWeeks(12), null));
        Goal bench = goal(demo, "Bench 250 e1RM", GoalMetric.EXERCISE_1RM, lib.get("Bench Press"), null, 230.0, 250.0, today.minusWeeks(16), today.minusWeeks(4));
        bench.setStatus(GoalStatus.ACHIEVED);
        bench.setAchievedOn(today.minusWeeks(5));
        goals.save(bench);

        CustomMetric pushups = metric(demo, "Push-ups (max reps)", "reps", MetricCategory.ASSESSMENT, true);
        CustomMetric mile = metric(demo, "1-mile run", "min", MetricCategory.ASSESSMENT, false);
        for (int m = 4; m >= 0; m--) {
            entry(pushups, today.minusWeeks(m * 3L + 1), 33 + (4 - m) * 2 + rnd.nextInt(2));
            entry(mile, today.minusWeeks(m * 3L + 1), round1(7.9 - (4 - m) * 0.12));
        }
        goals.save(goal(demo, "50 push-ups", GoalMetric.CUSTOM_METRIC, null, pushups, 33.0, 50.0, today.minusWeeks(13), today.plusWeeks(10)));

        Limitation shoulder = new Limitation();
        shoulder.setUser(demo);
        shoulder.setBodyArea("Left shoulder");
        shoulder.setTitle("Pinch at the top of overhead pressing");
        shoulder.setSeverity(2);
        shoulder.setStatus(LimitationStatus.MANAGING);
        shoulder.setStartedOn(today.minusWeeks(3));
        shoulder.setNotes("Neutral-grip DB press; keep face pulls in every upper day.");
        limitations.save(shoulder);
    }

    private Goal goal(UserLoginDetails demo, String title, GoalMetric metric, Exercise exercise, CustomMetric custom,
                      Double start, double target, LocalDate from, LocalDate by) {
        Goal g = new Goal();
        g.setUser(demo);
        g.setTitle(title);
        g.setMetric(metric);
        g.setExercise(exercise);
        g.setCustomMetric(custom);
        g.setStartValue(start);
        g.setTargetValue(target);
        g.setStartDate(from);
        g.setTargetDate(by);
        g.setStatus(GoalStatus.ACTIVE);
        return g;
    }

    private CustomMetric metric(UserLoginDetails demo, String name, String unit, MetricCategory category, boolean higher) {
        CustomMetric m = new CustomMetric();
        m.setUser(demo);
        m.setName(name);
        m.setUnit(unit);
        m.setCategory(category);
        m.setHigherIsBetter(higher);
        return metrics.save(m);
    }

    private void entry(CustomMetric metric, LocalDate date, double value) {
        CustomMetricEntry e = new CustomMetricEntry();
        e.setMetric(metric);
        e.setRecordedOn(date);
        e.setValue(value);
        metricEntries.save(e);
    }

    private <T> T pick(List<T> options) {
        return options.get(rnd.nextInt(options.size()));
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round5(double v) {
        return Math.round(v / 5) * 5.0;
    }
}
