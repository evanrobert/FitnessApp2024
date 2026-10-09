package Evan.Application.Fitness;

import Evan.Application.Fitness.Form.SessionForm;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.WorkoutSession;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Service.TrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** "Next up" suggestions and the live (tick-as-you-go) workout form. Synthetic data only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NextWorkoutTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired TrainingService training;
    @Autowired TodayService todayService;

    UserLoginDetails member;
    LocalDate today;

    @BeforeEach
    void setUp() {
        member = TestUsers.create(accounts);
        today = todayService.today(member.getId());
    }

    private WorkoutSession log(String title, int daysAgo, String exercise) {
        SessionForm form = training.blankForm(member.getId());
        form.setSessionDate(today.minusDays(daysAgo));
        form.setTitle(title);
        SessionForm.SetRow row = new SessionForm.SetRow();
        row.setExerciseName(exercise);
        row.setBlock(0);
        row.setReps(8);
        row.setWeightLb(135.0);
        form.getSets().add(row);
        return training.create(member.getId(), form);
    }

    @Test
    void nothingToSuggestBeforeTheFirstWorkout() {
        assertThat(training.nextUp(member.getId())).isEmpty();
    }

    @Test
    void withoutARoutineTheLatestWorkoutIsSuggested() {
        log("Push", 5, "Bench Press");
        WorkoutSession latest = log("Legs", 2, "Back Squat");
        TrainingService.NextUp next = training.nextUp(member.getId()).orElseThrow();
        assertThat(next.session().getId()).isEqualTo(latest.getId());
        assertThat(next.rotation()).isFalse();
    }

    @Test
    void aRotationSuggestsTheWorkoutDoneLongestAgo() {
        log("Upper A", 9, "Bench Press");
        log("Lower A", 8, "Back Squat");
        log("Upper B", 7, "Overhead Press");
        WorkoutSession upperA = log("Upper A", 5, "Bench Press");
        log("Lower A", 3, "Back Squat");
        log("Upper B", 1, "Overhead Press");

        TrainingService.NextUp next = training.nextUp(member.getId()).orElseThrow();
        assertThat(next.rotation()).isTrue();
        assertThat(next.session().getId()).isEqualTo(upperA.getId());
        assertThat(next.others()).extracting(WorkoutSession::displayTitle).containsExactly("Lower A", "Upper B");
    }

    @Test
    void todayShowsNextUpUntilAWorkoutIsLoggedToday() throws Exception {
        WorkoutSession push = log("Push day", 2, "Bench Press");
        String home = mvc.perform(get("/home").with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(home).contains("Start workout").contains("/train/new?repeat=" + push.getId() + "&amp;start=true");

        log("Push day", 0, "Bench Press");
        home = mvc.perform(get("/home").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(home).doesNotContain("Start workout");
    }

    @Test
    void startingAWorkoutOpensTheLiveFormWithLastWeights() throws Exception {
        WorkoutSession push = log("Push day", 2, "Bench Press");
        String form = mvc.perform(get("/train/new").param("repeat", push.getId().toString()).param("start", "true").with(as(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("Let's go.").contains("data-action=\"done-set\"").contains("value=\"135\"")
                .contains("Felt easy last time? +5 lb").contains("data-rest");
    }

    @Test
    void pingKeepsTheSessionAliveForSignedInMembersOnly() throws Exception {
        mvc.perform(get("/ping").with(as(member))).andExpect(status().isNoContent());
        mvc.perform(get("/ping")).andExpect(status().is3xxRedirection());
    }
}
