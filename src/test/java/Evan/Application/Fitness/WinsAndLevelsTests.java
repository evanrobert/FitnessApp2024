package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.ExerciseRepository;
import Evan.Application.Fitness.Repositorys.GoalRepository;
import Evan.Application.Fitness.Repositorys.UserInformationRepository;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.EntryIcons;
import Evan.Application.Fitness.Service.GoalService;
import Evan.Application.Fitness.Service.StrengthStandards;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Lift goals reached by a workout, strength levels and entry icons. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WinsAndLevelsTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired GoalService goalService;
    @Autowired GoalRepository goals;
    @Autowired ExerciseRepository exercises;
    @Autowired UserInformationRepository profiles;

    UserLoginDetails member;
    Exercise bench;

    @BeforeEach
    void setUp() {
        member = TestUsers.create(accounts); // synthetic member, 180 lb starting weight
        bench = exercises.findVisibleByName("Bench Press", member.getId()).get(0);
    }

    private Goal benchGoal(double target) {
        Goal form = new Goal();
        form.setTitle("Bench " + (int) target);
        form.setMetric(GoalMetric.EXERCISE_WEIGHT);
        form.setTargetValue(target);
        return goalService.save(member.getId(), null, form, bench.getId(), null);
    }

    private MvcResult logBench(String date, String weight, String reps) throws Exception {
        return mvc.perform(post("/train").with(as(member)).with(csrf())
                        .param("sessionDate", date)
                        .param("sets[0].exerciseName", "Bench Press").param("sets[0].block", "0")
                        .param("sets[0].weightLb", weight).param("sets[0].reps", reps))
                .andExpect(status().is3xxRedirection()).andReturn();
    }

    @Test
    void liftingTheGoalWeightMarksTheGoalReachedAndSaysSo() throws Exception {
        Goal goal = benchGoal(185);
        logBench("2026-03-02", "175", "5");
        assertThat(goals.findById(goal.getId()).orElseThrow().getStatus()).isEqualTo(GoalStatus.ACTIVE);

        MvcResult result = logBench("2026-03-05", "185", "1");
        assertThat((String) result.getFlashMap().get("flashSuccess")).contains("Goal reached: Bench 185!");
        Goal reached = goals.findById(goal.getId()).orElseThrow();
        assertThat(reached.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
        assertThat(reached.getAchievedOn()).isEqualTo(LocalDate.of(2026, 3, 5));

        String detail = mvc.perform(get(result.getResponse().getRedirectedUrl()).with(as(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("How this workout moved them").contains("Reached in this workout!");
    }

    @Test
    void heavierEstimatedMaxAloneDoesNotReachAWeightGoal() throws Exception {
        Goal goal = benchGoal(185);
        logBench("2026-03-02", "175", "8"); // estimated max ~220, but nothing at 185 yet
        assertThat(goals.findById(goal.getId()).orElseThrow().getStatus()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void strengthLevelsUseBodyWeightAndSex() {
        StrengthStandards.Level level = StrengthStandards.level("Bench Press", Sex.MALE, 180.0, 185).orElseThrow();
        assertThat(level.name()).isEqualTo("Intermediate");
        assertThat(level.next()).isEqualTo("Advanced");
        assertThat(level.nextWeightLb()).isEqualTo(270.0);
        assertThat(level.lbToNext()).isEqualTo(85.0);

        assertThat(StrengthStandards.level("Bench Press", Sex.FEMALE, 140.0, 140).orElseThrow().name()).isEqualTo("Advanced");
        assertThat(StrengthStandards.level("Bench Press", Sex.MALE, 200.0, 60).orElseThrow().label()).isEqualTo("Getting started");
        assertThat(StrengthStandards.level("Deadlift", Sex.MALE, 150.0, 460).orElseThrow().next()).isNull();
        assertThat(StrengthStandards.level("Bench Press", Sex.UNSPECIFIED, 180.0, 185)).isEmpty();
        assertThat(StrengthStandards.level("Cable Fly", Sex.MALE, 180.0, 100)).isEmpty();
    }

    @Test
    void reachingANewLevelIsCelebratedAndShownOnPersonalBests() throws Exception {
        UserInformation profile = profiles.findByUserId(member.getId()).orElseThrow();
        profile.setSex(Sex.MALE);
        profiles.save(profile);
        logBench("2026-03-02", "135", "1"); // 0.75 x 180 = Novice
        MvcResult result = logBench("2026-03-05", "185", "1"); // 1.0 x 180 = Intermediate
        assertThat((String) result.getFlashMap().get("flashSuccess")).contains("New level: Intermediate at Bench Press!");

        String records = mvc.perform(get("/records").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(records).contains("level-badge").contains(">Intermediate<");
        String exercise = mvc.perform(get("/exercises/{id}", bench.getId()).with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(exercise).contains("Strength level").contains("lb more on your estimated max to reach Advanced.");
    }

    @Test
    void levelsAskForSexWhenItIsMissing() throws Exception {
        logBench("2026-03-02", "135", "5");
        String records = mvc.perform(get("/records").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(records).contains("Add your sex in Profile to see your strength level.");
    }

    @Test
    void foodsAndWorkoutsGetFittingIcons() {
        assertThat(EntryIcons.food("Chocolate protein shake")).isEqualTo("shake");
        assertThat(EntryIcons.food("Grilled chicken breast")).isEqualTo("drumstick");
        assertThat(EntryIcons.food("Chicken noodle soup")).isEqualTo("bowl");
        assertThat(EntryIcons.food("Iced latte")).isEqualTo("coffee");
        assertThat(EntryIcons.food("Banana")).isEqualTo("apple");
        assertThat(EntryIcons.food("Scrambled eggs")).isEqualTo("egg");
        assertThat(EntryIcons.food("Pineapple")).isEqualTo("apple");
        assertThat(EntryIcons.food("Mystery casserole")).isEqualTo("bowl");
        assertThat(EntryIcons.food(null)).isEqualTo("bowl");
    }
}
