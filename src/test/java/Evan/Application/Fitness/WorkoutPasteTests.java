package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.MuscleGroup;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.WorkoutTextParser;
import Evan.Application.Fitness.Service.WorkoutTextParser.SetLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pasting a workout from a notes app. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPasteTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;

    static final String NOTES = """
            Workout today Incline dumbbell press
            * 50 lbs 10 reps
            * 50 lbs 10 reps
            * 50 lbs 10 reps

            Cable tower chest press
            * 80 lbs 12 reps
            * 80 lbs 12 reps
            * 80 lbs 12 reps

            Cable tower fly high to low
            * 50 lbs 12 reps
            * 50 lbs 12 reps
            * 50 lbs 12 reps

            Low to high cable fly\s
            * 30 lbs 12 reps
            * 30 lbs 12 reps
            * 30 lbs 12 reps

            Cable tower Tricep push down
            * 100 lbs 12 reps
            * 100 lbs 12 reps
            * 100 lbs 12 reps
            * 100 lbs 12 reps

            Overhead cable tower Tricep extension\s
            * 70 lbs 12 reps
            * 70 lbs 12 reps
            * 70 lbs 12 reps
            """;

    private static List<SetLine> sets(String line) {
        WorkoutTextParser.Result r = WorkoutTextParser.parse("Bench press\n" + line);
        return r.blocks().isEmpty() ? List.of() : r.blocks().get(0).sets();
    }

    @Test
    void readsTheNotesAppFormat() {
        WorkoutTextParser.Result r = WorkoutTextParser.parse(NOTES);
        assertThat(r.blocks()).extracting(WorkoutTextParser.Block::name).containsExactly(
                "Incline dumbbell press", "Cable tower chest press", "Cable tower fly high to low",
                "Low to high cable fly", "Cable tower Tricep push down", "Overhead cable tower Tricep extension");
        assertThat(r.setCount()).isEqualTo(19);
        assertThat(r.blocks().get(4).sets()).hasSize(4).allMatch(s -> s.reps() == 12 && s.weightLb() == 100.0);
        assertThat(r.skipped()).isEmpty();
    }

    @Test
    void understandsCommonWaysOfWritingSets() {
        assertThat(sets("135 x 8")).containsExactly(new SetLine(8, 135.0));
        assertThat(sets("135x8")).containsExactly(new SetLine(8, 135.0));
        assertThat(sets("3x10 @ 135")).hasSize(3).allMatch(s -> s.equals(new SetLine(10, 135.0)));
        assertThat(sets("3 x 10 135 lbs")).hasSize(3);
        assertThat(sets("3 sets of 12 at 50 lbs")).hasSize(3).allMatch(s -> s.equals(new SetLine(12, 50.0)));
        assertThat(sets("185 lbs: 8, 8, 6")).containsExactly(new SetLine(8, 185.0), new SetLine(8, 185.0), new SetLine(6, 185.0));
        assertThat(sets("135 x 8, 155 x 6")).containsExactly(new SetLine(8, 135.0), new SetLine(6, 155.0));
        assertThat(sets("12 @ 135")).containsExactly(new SetLine(12, 135.0));
        assertThat(sets("10 reps @ 50 lbs")).containsExactly(new SetLine(10, 50.0));
        assertThat(sets("50 lbs 10 reps x 3")).hasSize(3);
        assertThat(sets("12 reps")).containsExactly(new SetLine(12, null));
        assertThat(sets("bodyweight x 12")).containsExactly(new SetLine(12, null));
        assertThat(sets("3x15")).hasSize(3).allMatch(s -> s.equals(new SetLine(15, null)));
        assertThat(sets("60 kg x 5")).containsExactly(new SetLine(5, 132.5));
        assertThat(sets("Set 1: 225 lbs 5 reps")).containsExactly(new SetLine(5, 225.0));
        assertThat(sets("1. 95 lb x 10")).containsExactly(new SetLine(10, 95.0));
    }

    @Test
    void exerciseAndSetsOnOneLineTitleDateAndSkippedLines() {
        WorkoutTextParser.Result r = WorkoutTextParser.parse("""
                Push day
                10/3
                Bench press 3x8 @ 135
                Push-ups: 3x15
                Plank
                felt great
                """);
        assertThat(r.title()).isEqualTo("Push day");
        assertThat(r.date()).isEqualTo(new WorkoutTextParser.WrittenDate(10, 3, null));
        assertThat(r.blocks()).extracting(WorkoutTextParser.Block::name).containsExactly("Bench press", "Push-ups");
        assertThat(r.setCount()).isEqualTo(6);
        assertThat(r.skipped()).containsExactly("Plank", "felt great");
    }

    @Test
    void namesMatchTheLibraryDespiteSpellingAndBodyPartsAreGuessed() {
        assertThat(WorkoutTextParser.matchKey("Low to high cable fly")).isEqualTo(WorkoutTextParser.matchKey("Low-to-High Cable Fly"));
        assertThat(WorkoutTextParser.matchKey("tricep push down")).isEqualTo(WorkoutTextParser.matchKey("Triceps Pushdown"));
        assertThat(WorkoutTextParser.matchKey("Bench Press")).isNotEqualTo(WorkoutTextParser.matchKey("Incline Bench Press"));
        assertThat(WorkoutTextParser.guessMuscle("Cable tower Tricep push down")).isEqualTo(MuscleGroup.TRICEPS);
        assertThat(WorkoutTextParser.guessMuscle("Cable tower chest press")).isEqualTo(MuscleGroup.CHEST);
        assertThat(WorkoutTextParser.guessMuscle("Seated leg curl machine")).isEqualTo(MuscleGroup.HAMSTRINGS);
        assertThat(WorkoutTextParser.guessMuscle("Something new")).isNull();
    }

    @Test
    void pastingOpensTheFilledFormWithoutSaving() throws Exception {
        UserLoginDetails member = TestUsers.create(accounts);
        mvc.perform(get("/train/paste").with(as(member))).andExpect(status().isOk());
        String page = mvc.perform(post("/train/paste").with(as(member)).with(csrf()).param("text", NOTES))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Found 6 exercises and 19 sets.")
                // Matched to the library's names, so personal bests and levels line up.
                .contains("value=\"Incline Dumbbell Press\"").contains("value=\"Low-to-High Cable Fly\"")
                .contains("value=\"Cable tower Tricep push down\"").contains("value=\"100\"")
                .contains("<option value=\"TRICEPS\" selected=\"selected\">");
        String list = mvc.perform(get("/train").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("No workouts yet");
    }

    @Test
    void textWithoutSetsStaysOnThePasteBoxWithAHint() throws Exception {
        UserLoginDetails member = TestUsers.create(accounts);
        String page = mvc.perform(post("/train/paste").with(as(member)).with(csrf()).param("text", "went to the gym, felt good"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("couldn&#39;t find any sets").contains("went to the gym, felt good");
    }

    @Test
    void pasteNeedsSignInAndCsrf() throws Exception {
        mvc.perform(get("/train/paste")).andExpect(status().is3xxRedirection());
        UserLoginDetails member = TestUsers.create(accounts);
        mvc.perform(post("/train/paste").with(as(member)).param("text", NOTES)).andExpect(status().isForbidden());
    }
}
