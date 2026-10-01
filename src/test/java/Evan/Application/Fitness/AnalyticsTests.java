package Evan.Application.Fitness;

import Evan.Application.Fitness.Form.SessionForm;
import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.ExerciseRepository;
import Evan.Application.Fitness.Service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsTests {
    @Autowired AnalyticsService analytics;
    @Autowired AccountService accounts;
    @Autowired TrainingService training;
    @Autowired CheckInService checkIns;
    @Autowired BodyService body;
    @Autowired ExerciseRepository exercises;

    UserLoginDetails member;
    LocalDate today;
    Long squat;

    @BeforeEach
    void setUp() {
        member = TestUsers.create(accounts);
        today = LocalDate.now(ZoneId.of("America/New_York"));
        squat = exercises.findVisibleTo(member.getId()).stream().filter(e -> e.getName().equals("Back Squat")).findFirst().orElseThrow().getId();
    }

    @Test
    void weeklyRateIsTheLeastSquaresSlope() {
        List<BodyMeasurement> weights = List.of(weigh(today.minusDays(21), 190), weigh(today.minusDays(14), 189),
                weigh(today.minusDays(7), 188), weigh(today, 187));
        assertThat(AnalyticsService.weeklyRate(weights, today)).isEqualTo(-1.0);
        assertThat(AnalyticsService.weeklyRate(weights.subList(0, 2), today)).isNull(); // not enough data
        assertThat(AnalyticsService.weightChange(weights, 7)).isEqualTo(-1.0);
    }

    @Test
    void newMembersGetNoFabricatedInsights() {
        AnalyticsService.Dashboard dash = analytics.dashboard(member.getId());
        assertThat(dash.insights()).isEmpty();
        assertThat(dash.streak().current()).isEqualTo(1); // the sign-up weigh-in counts as today's log
        assertThat(dash.tasks()).extracting(AnalyticsService.TodayTask::done).contains(false);
    }

    @Test
    void streakCountsConsecutiveLoggedDaysAndSurvivesUntilTodayEnds() {
        for (int i = 1; i <= 5; i++) {
            DailyCheckIn c = new DailyCheckIn();
            c.setCheckInDate(today.minusDays(i));
            c.setEnergy(3);
            checkIns.save(member.getId(), c);
        }
        AnalyticsService.Streak streak = analytics.dashboard(member.getId()).streak();
        assertThat(streak.current()).isEqualTo(6); // five check-ins + today's sign-up weigh-in
        assertThat(streak.longest()).isEqualTo(6);
    }

    @Test
    void timelineGroupsAllPrsFromOneSessionIntoOneMilestone() {
        training.create(member.getId(), session(today.minusDays(7), 275, 285));
        training.create(member.getId(), session(today.minusDays(1), 295, 305)); // beats both heaviest and e1RM
        List<AnalyticsService.TimelineDay> days = analytics.timeline(member.getId(), 30, AnalyticsService.EventType.MILESTONE);
        assertThat(days).singleElement().satisfies(day -> assertThat(day.events()).singleElement()
                .satisfies(e -> assertThat(e.value()).isEqualTo("2 personal bests")));
    }

    @Test
    void achievementsReflectRealHistory() {
        training.create(member.getId(), session(today.minusDays(2), 225, 235));
        AnalyticsService.Insights view = analytics.insights(member.getId(), 30);
        assertThat(view.achievements()).filteredOn(AnalyticsService.Achievement::earned)
                .extracting(AnalyticsService.Achievement::code).contains("S1").doesNotContain("S10", "PR1");
        AnalyticsService.Achievement tenSessions = view.achievements().stream().filter(a -> a.code().equals("S10")).findFirst().orElseThrow();
        assertThat(tenSessions.progress()).isEqualTo(0.1);
        assertThat(view.muscleBalance()).singleElement().satisfies(m -> assertThat(m.group()).isEqualTo(MuscleGroup.QUADS));
    }

    @Test
    void insightsChartsExcludeTodaysUnfinishedIntake() {
        CalorieInformation meal = new CalorieInformation();
        meal.setItemName("Lunch");
        meal.setCalories(700.0);
        meal.setDate(today);
        nutrition.logMeal(member.getId(), meal);
        @SuppressWarnings("unchecked")
        List<Double> calories = (List<Double>) ((List<java.util.Map<String, Object>>) analytics.insights(member.getId(), 30)
                .charts().get("calories").get("series")).get(0).get("values");
        assertThat(calories.get(calories.size() - 1)).isNull();
    }

    @Autowired NutritionService nutrition;

    private SessionForm session(LocalDate date, double... loads) {
        SessionForm form = new SessionForm();
        form.setSessionDate(date);
        for (double load : loads) {
            SessionForm.SetRow row = new SessionForm.SetRow();
            row.setExerciseId(squat);
            row.setBlock(0);
            row.setReps(5);
            row.setWeightLb(load);
            form.getSets().add(row);
        }
        return form;
    }

    private static BodyMeasurement weigh(LocalDate date, double lb) {
        BodyMeasurement m = new BodyMeasurement();
        m.setMeasuredOn(date);
        m.setWeightLb(lb);
        return m;
    }
}
