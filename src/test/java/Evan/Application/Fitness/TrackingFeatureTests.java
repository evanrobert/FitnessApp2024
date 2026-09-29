package Evan.Application.Fitness;

import Evan.Application.Fitness.Form.SessionForm;
import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.*;
import Evan.Application.Fitness.Service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end behaviour of the tracking areas, including member isolation for each. Synthetic data only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrackingFeatureTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired TrainingService training;
    @Autowired RecordsService records;
    @Autowired GoalService goals;
    @Autowired BodyService body;
    @Autowired CheckInService checkIns;
    @Autowired MetricService metrics;
    @Autowired WorkoutSessionRepository sessions;
    @Autowired DailyCheckInRepository checkInRepository;
    @Autowired UserLoginDetailsRepository users;
    @Autowired UserInformationRepository profiles;
    @Autowired ExerciseRepository exercises;

    UserLoginDetails alice;
    UserLoginDetails bob;
    Long squat;

    @BeforeEach
    void setUp() {
        alice = TestUsers.create(accounts);
        bob = TestUsers.create(accounts);
        squat = exercises.findVisibleTo(alice.getId()).stream().filter(e -> e.getName().equals("Back Squat")).findFirst().orElseThrow().getId();
    }

    private SessionForm session(LocalDate date, Object[]... sets) {
        SessionForm form = new SessionForm();
        form.setSessionDate(date);
        form.setLocationType(LocationType.GYM);
        for (Object[] s : sets) {
            SessionForm.SetRow row = new SessionForm.SetRow();
            row.setExerciseId((Long) s[0]);
            row.setWeightLb((Double) s[1]);
            row.setReps((Integer) s[2]);
            row.setWarmup(s.length > 3 && (Boolean) s[3]);
            form.getSets().add(row);
        }
        return form;
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void sessionBuilderNumbersSetsPerExerciseAndSkipsBlankRows() throws Exception {
        mvc.perform(post("/train").with(as(alice)).with(csrf())
                        .param("sessionDate", "2026-09-01").param("locationType", "GYM")
                        .param("sets[0].exerciseId", squat.toString()).param("sets[0].block", "0").param("sets[0].weightLb", "135").param("sets[0].reps", "5").param("sets[0].warmup", "true")
                        .param("sets[1].exerciseId", squat.toString()).param("sets[1].block", "0").param("sets[1].weightLb", "275").param("sets[1].reps", "5")
                        .param("sets[2].exerciseId", squat.toString()).param("sets[2].block", "0")
                        .param("cardio[0].activity", "ROW").param("cardio[0].durationMin", "10").param("cardio[0].distanceMi", "1.5"))
                .andExpect(status().is3xxRedirection());

        WorkoutSession saved = sessions.findAllByUserIdOrderBySessionDateDescIdDesc(alice.getId()).get(0);
        assertThat(saved.getSets()).extracting(ExerciseSet::getSetNumber).containsExactly(1, 2);
        assertThat(saved.volume()).isEqualTo(275 * 5.0); // warm-up excluded
        assertThat(saved.getCardio()).singleElement().satisfies(c -> assertThat(c.paceMinPerMile()).isCloseTo(6.67, org.assertj.core.data.Offset.offset(0.01)));
    }

    @Test
    void membersCannotReadEditOrDeleteOthersSessions() throws Exception {
        WorkoutSession bobs = training.create(bob.getId(), session(LocalDate.of(2026, 9, 2), new Object[]{squat, 225.0, 5}));
        mvc.perform(get("/train/{id}", bobs.getId()).with(as(alice))).andExpect(status().isNotFound());
        mvc.perform(post("/train/{id}", bobs.getId()).with(as(alice)).with(csrf()).param("sessionDate", "2026-09-03"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/train/{id}/delete", bobs.getId()).with(as(alice)).with(csrf())).andExpect(status().isNotFound());
        assertThat(sessions.findById(bobs.getId())).isPresent();
    }

    @Test
    void personalExercisesArePrivateToTheirOwner() {
        Exercise form = new Exercise();
        form.setName("Bob's Secret Press");
        form.setMuscleGroup(MuscleGroup.CHEST);
        Exercise bobsExercise = training.createExercise(bob.getId(), form);

        assertThat(training.visibleExercises(alice.getId())).extracting(Exercise::getId).doesNotContain(bobsExercise.getId());
        SessionForm sneaky = session(LocalDate.of(2026, 9, 4), new Object[]{bobsExercise.getId(), 100.0, 5});
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> training.create(alice.getId(), sneaky))
                .isInstanceOf(Evan.Application.Fitness.Web.NotFoundException.class);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void typedExerciseNamesReuseMatchesOrCreateOnePersonalExercise() throws Exception {
        mvc.perform(post("/train").with(as(alice)).with(csrf()).param("sessionDate", "2026-09-07")
                        // library name typed in different case -> library exercise
                        .param("sets[0].exerciseName", "  goblet   SQUAT ").param("sets[0].block", "0").param("sets[0].weightLb", "70").param("sets[0].reps", "10")
                        // brand-new name, two sets -> one personal exercise with the chosen muscle group
                        .param("sets[1].exerciseName", "Landmine Row").param("sets[1].muscleGroup", "BACK").param("sets[1].block", "1").param("sets[1].weightLb", "90").param("sets[1].reps", "8")
                        .param("sets[2].exerciseName", "Landmine Row").param("sets[2].muscleGroup", "BACK").param("sets[2].block", "1").param("sets[2].weightLb", "90").param("sets[2].reps", "8"))
                .andExpect(status().is3xxRedirection());

        WorkoutSession saved = sessions.findAllByUserIdOrderBySessionDateDescIdDesc(alice.getId()).get(0);
        assertThat(saved.getSets()).extracting(st -> st.getExercise().getName()).containsExactly("Goblet Squat", "Landmine Row", "Landmine Row");
        assertThat(saved.getSets().get(0).getExercise().isLibrary()).isTrue();
        Exercise created = saved.getSets().get(1).getExercise();
        assertThat(created.isLibrary()).isFalse();
        assertThat(created.getMuscleGroup()).isEqualTo(MuscleGroup.BACK);
        assertThat(saved.getSets().get(2).getSetNumber()).isEqualTo(2);

        // Next time, typing it again (any case) reuses the same personal exercise.
        mvc.perform(post("/train").with(as(alice)).with(csrf()).param("sessionDate", "2026-09-08")
                        .param("sets[0].exerciseName", "landmine row").param("sets[0].block", "0").param("sets[0].weightLb", "95").param("sets[0].reps", "8"))
                .andExpect(status().is3xxRedirection());
        assertThat(exercises.findVisibleTo(alice.getId()).stream().filter(e -> e.getName().equalsIgnoreCase("landmine row")).count()).isEqualTo(1);
        assertThat(records.series(alice.getId(), created.getId())).hasSize(2); // history and trends see both sessions
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void typedNamesNeverAttachToAnotherMembersExercise() {
        Exercise form = new Exercise();
        form.setName("Zercher Carry");
        form.setMuscleGroup(MuscleGroup.CORE);
        Exercise bobs = training.createExercise(bob.getId(), form);

        SessionForm typed = new SessionForm();
        typed.setSessionDate(LocalDate.of(2026, 9, 9));
        SessionForm.SetRow row = new SessionForm.SetRow();
        row.setExerciseName("Zercher Carry");
        row.setWeightLb(135.0);
        row.setReps(1);
        typed.getSets().add(row);
        WorkoutSession saved = training.create(alice.getId(), typed);

        Exercise used = saved.getSets().get(0).getExercise();
        assertThat(used.getId()).isNotEqualTo(bobs.getId());
        assertThat(used.getMuscleGroup()).isEqualTo(MuscleGroup.OTHER); // no group chosen -> Other
    }

    @Test
    void builderOffersEveryVisibleExerciseForSearch() throws Exception {
        String html = mvc.perform(get("/train/new").with(as(alice))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<datalist id=\"exercise-options\">").contains("value=\"Nordic Curl\"").contains("value=\"Pec Deck\"");
    }

    @Test
    void recordsDetectPrsAgainstEarlierSessionsOnly() {
        training.create(alice.getId(), session(LocalDate.of(2026, 8, 1), new Object[]{squat, 275.0, 5}));
        WorkoutSession second = training.create(alice.getId(), session(LocalDate.of(2026, 8, 8), new Object[]{squat, 285.0, 5}));
        training.create(alice.getId(), session(LocalDate.of(2026, 8, 15), new Object[]{squat, 280.0, 5}));

        RecordsService.Summary summary = records.summary(alice.getId());
        assertThat(summary.events()).extracting(RecordsService.PrEvent::sessionId).containsOnly(second.getId());
        assertThat(summary.events()).extracting(RecordsService.PrEvent::kind)
                .containsExactlyInAnyOrder(RecordsService.PrKind.HEAVIEST, RecordsService.PrKind.E1RM);
        RecordsService.ExerciseRecord record = summary.forExercise(squat).orElseThrow();
        assertThat(record.bestWeight()).isEqualTo(285.0);
        assertThat(record.bestE1rm()).isCloseTo(285 * (1 + 5 / 30.0), org.assertj.core.data.Offset.offset(0.01));
        assertThat(record.sessions()).isEqualTo(3);
    }

    @Test
    void goalProgressIsReadFromLoggedData() {
        // Same-day weigh-ins: the latest entry wins, and it supersedes the sign-up weight.
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/New_York"));
        weighIn(alice, today, 200.0);
        Goal form = new Goal();
        form.setTitle("Cut to 190");
        form.setMetric(GoalMetric.BODY_WEIGHT);
        form.setTargetValue(190.0);
        form.setStartDate(today);
        Goal goal = goals.save(alice.getId(), null, form, null, null);
        assertThat(goal.getStartValue()).isEqualTo(200.0); // captured from the latest weigh-in

        weighIn(alice, today, 195.0);
        GoalService.Progress progress = goals.progress(alice.getId(), goal);
        assertThat(progress.current()).isEqualTo(195.0);
        assertThat(progress.pct()).isEqualTo(50.0);
        assertThat(progress.reached()).isFalse();
    }

    @Test
    void checkInIsOnePerDayAndWaterAccumulates() throws Exception {
        mvc.perform(post("/recover").with(as(alice)).with(csrf()).param("checkInDate", "2026-09-10").param("energy", "2"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/recover").with(as(alice)).with(csrf()).param("checkInDate", "2026-09-10").param("energy", "5").param("sleepHours", "8"))
                .andExpect(status().is3xxRedirection());
        List<DailyCheckIn> saved = checkInRepository.findAllByUserIdOrderByCheckInDateDesc(alice.getId());
        assertThat(saved).singleElement().satisfies(c -> {
            assertThat(c.getEnergy()).isEqualTo(5);
            assertThat(c.readinessScore()).isEqualTo(100);
        });

        checkIns.addWater(alice.getId(), 16);
        assertThat(checkIns.addWater(alice.getId(), 16)).isEqualTo(32.0);
    }

    @Test
    void waterRedirectOnlyReturnsToSameSitePaths() throws Exception {
        mvc.perform(post("/recover/water").with(as(alice)).with(csrf()).param("ounces", "8").param("returnTo", "https://evil.example"))
                .andExpect(redirectedUrl("/recover"));
        mvc.perform(post("/recover/water").with(as(alice)).with(csrf()).param("ounces", "8").param("returnTo", "//evil.example"))
                .andExpect(redirectedUrl("/recover"));
        mvc.perform(post("/recover/water").with(as(alice)).with(csrf()).param("ounces", "8").param("returnTo", "/fuel"))
                .andExpect(redirectedUrl("/fuel"));
    }

    @Test
    void customMetricEntriesAreScopedToTheOwner() throws Exception {
        CustomMetric form = new CustomMetric();
        form.setName("Mile time");
        form.setHigherIsBetter(false);
        CustomMetric bobs = metrics.create(bob.getId(), form);
        mvc.perform(post("/metrics/{id}/entries", bobs.getId()).with(as(alice)).with(csrf()).param("value", "7.5"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/metrics/{id}", bobs.getId()).with(as(alice))).andExpect(status().isNotFound());
        assertThat(metrics.entries(bobs.getId())).isEmpty();
    }

    @Test
    void exportsContainOnlyTheRequestingMembersRows() throws Exception {
        training.create(bob.getId(), session(LocalDate.of(2026, 9, 5), new Object[]{squat, 405.0, 1}));
        training.create(alice.getId(), session(LocalDate.of(2026, 9, 5), new Object[]{squat, 225.0, 3}));
        String csv = mvc.perform(get("/export/sets.csv").with(as(alice))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("225.0").doesNotContain("405.0");
        mvc.perform(get("/export/unknown.csv").with(as(alice))).andExpect(status().isNotFound());
    }

    @Test
    void onboardingPersonalisesProfileAndRecordsWeight() throws Exception {
        mvc.perform(post("/onboarding").with(as(alice)).with(csrf())
                        .param("name", "Test Member").param("primaryGoal", "LOSE_FAT").param("weeklyWorkoutTarget", "4")
                        .param("heightIn", "68").param("birthYear", "1990").param("sex", "FEMALE")
                        .param("activityLevel", "LIGHT").param("timeZone", "Europe/London").param("currentWeight", "160"))
                .andExpect(redirectedUrl("/fuel/targets"));
        UserInformation profile = profiles.findByUserId(alice.getId()).orElseThrow();
        assertThat(profile.getPrimaryGoal()).isEqualTo(PrimaryGoal.LOSE_FAT);
        assertThat(profile.getTimeZone()).isEqualTo("Europe/London");
        assertThat(body.latestWeight(alice.getId()).orElseThrow().getWeightLb()).isEqualTo(160.0);
        assertThat(profileService.estimate(alice.getId()).available()).isTrue();
    }

    @Autowired ProfileService profileService;

    @Test
    void energyEstimateNeedsWeightHeightAndBirthYear() {
        assertThat(profileService.estimate(bob.getId()).available()).isFalse();
        UserInformation p = profileService.profile(bob.getId());
        p.setHeightIn(70.0);
        p.setBirthYear(1990);
        p.setSex(Sex.MALE);
        p.setPrimaryGoal(PrimaryGoal.MAINTAIN);
        p.setActivityLevel(ActivityLevel.SEDENTARY);
        profiles.save(p);
        ProfileService.EnergyEstimate estimate = profileService.estimate(bob.getId());
        assertThat(estimate.available()).isTrue(); // TestUsers signs up with a 180 lb weigh-in
        assertThat(estimate.maintenance()).isBetween(2000, 2300);
        assertThat(estimate.calories()).isEqualTo((int) (Math.round(estimate.maintenance() / 10.0) * 10));
    }

    @Test
    void deletingAnAccountRemovesEverythingTheyLogged() throws Exception {
        training.create(bob.getId(), session(LocalDate.of(2026, 9, 6), new Object[]{squat, 185.0, 5}));
        checkIns.addWater(bob.getId(), 16);
        weighIn(bob, LocalDate.of(2026, 9, 6), 181.0);

        mvc.perform(post("/profile/delete-account").with(as(bob)).with(csrf()).param("confirm", "DELETE").param("password", "wrong"))
                .andExpect(redirectedUrl("/profile#account"));
        assertThat(users.existsById(bob.getId())).isTrue();

        mvc.perform(post("/profile/delete-account").with(as(bob)).with(csrf()).param("confirm", "DELETE").param("password", "correct-horse-battery"))
                .andExpect(redirectedUrl("/login?deleted"));
        assertThat(users.existsById(bob.getId())).isFalse();
        assertThat(sessions.findAllByUserIdOrderBySessionDateDescIdDesc(bob.getId())).isEmpty();
        assertThat(checkInRepository.findAllByUserIdOrderByCheckInDateDesc(bob.getId())).isEmpty();
        assertThat(sessions.findAllByUserIdOrderBySessionDateDescIdDesc(alice.getId())).isEmpty(); // alice untouched (had none)
        assertThat(users.existsById(alice.getId())).isTrue();
    }

    @Test
    void everyTrackingPageRendersForANewMember() throws Exception {
        for (String page : List.of("/home", "/train", "/train/new", "/exercises", "/records", "/fuel", "/fuel/new",
                "/fuel/ledger", "/fuel/targets", "/recover", "/body", "/goals", "/goals/new", "/metrics", "/profile",
                "/onboarding", "/export")) {
            mvc.perform(get(page).with(as(alice))).andExpect(status().isOk());
        }
    }

    private void weighIn(UserLoginDetails who, LocalDate date, double lb) {
        BodyMeasurement m = new BodyMeasurement();
        m.setMeasuredOn(date);
        m.setWeightLb(lb);
        body.save(who.getId(), null, m);
    }
}
