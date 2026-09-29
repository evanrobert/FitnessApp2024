package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.*;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.ExportService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Web.DemoDataSeeder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every page against months of (synthetic) history. Several template
 * bugs only appear once there is data to render, so empty-account checks are
 * not enough.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RichDataRenderTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired UserLoginDetailsRepository users;
    @Autowired UserInformationRepository profiles;
    @Autowired UserMacroInformationRepository targets;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutSessionRepository sessions;
    @Autowired CalorieInformationRepository meals;
    @Autowired DailyCheckInRepository checkIns;
    @Autowired BodyMeasurementRepository measurements;
    @Autowired GoalRepository goals;
    @Autowired CustomMetricRepository metrics;
    @Autowired CustomMetricEntryRepository metricEntries;
    @Autowired LimitationRepository limitations;
    @Autowired TodayService todayService;
    @Autowired TransactionTemplate tx;

    UserLoginDetails demo;
    RequestPostProcessor asDemo;

    @BeforeAll
    void seed() throws Exception {
        if (users.findByUsername("demo").isEmpty()) {
            DemoDataSeeder seeder = new DemoDataSeeder(accounts, users, profiles, targets, exercises, sessions, meals,
                    checkIns, measurements, goals, metrics, metricEntries, limitations, todayService);
            tx.executeWithoutResult(status -> {
                try {
                    seeder.run(null);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        demo = users.findByUsername("demo").orElseThrow();
        asDemo = user(new AppUserPrincipal(demo.getId(), demo.getUsername(), demo.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void everyPageRendersWithMonthsOfData() throws Exception {
        List<String> pages = new ArrayList<>(List.of("/home", "/insights", "/insights?days=30", "/insights?days=180",
                "/timeline", "/timeline?days=365", "/timeline?type=MILESTONE", "/train", "/train?q=squat&focus=STRENGTH",
                "/train/new", "/exercises", "/records", "/fuel", "/fuel/new", "/fuel/ledger", "/fuel/targets",
                "/recover", "/body", "/body?all=true", "/goals", "/goals/new", "/metrics", "/profile", "/onboarding", "/export"));
        Long sessionId = sessions.findAllByUserIdOrderBySessionDateDescIdDesc(demo.getId()).get(0).getId();
        pages.add("/train/" + sessionId);
        pages.add("/train/" + sessionId + "/edit");
        pages.add("/train/new?repeat=" + sessionId);
        exercises.findVisibleTo(demo.getId()).stream().limit(6).forEach(e -> pages.add("/exercises/" + e.getId()));
        metrics.findAllByUserIdOrderByArchivedAscNameAsc(demo.getId()).forEach(m -> pages.add("/metrics/" + m.getId()));
        goals.findAllByUserIdOrderByStatusAscTargetDateAscIdDesc(demo.getId()).forEach(g -> pages.add("/goals/" + g.getId() + "/edit"));
        pages.add("/fuel/" + meals.findAllByUserIdOrderByDateDescIdDesc(demo.getId()).get(0).getId() + "/edit");
        for (int back = 0; back < 3; back++) {
            pages.add("/fuel?date=" + todayService.today(demo.getId()).minusDays(back));
            pages.add("/recover?date=" + todayService.today(demo.getId()).minusDays(back));
        }

        for (String page : pages) {
            String html = mvc.perform(get(page).with(asDemo)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as(page).contains("</html>").doesNotContain("Exception");
        }
    }

    @Test
    void everyExportDownloadsWithRows() throws Exception {
        for (String dataset : ExportService.DATASETS.keySet()) {
            String csv = mvc.perform(get("/export/" + dataset + ".csv").with(asDemo)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(csv.lines().count()).as(dataset).isGreaterThan(1);
        }
    }

    @Test
    void emptyStatesOnlyShowWhenThereIsNothingToShow() throws Exception {
        Long sessionId = sessions.findAllByUserIdOrderBySessionDateDescIdDesc(demo.getId()).get(0).getId();
        assertThat(mvc.perform(get("/train/" + sessionId).with(asDemo)).andReturn().getResponse().getContentAsString())
                .doesNotContain("No sets or cardio recorded");
        assertThat(mvc.perform(get("/goals").with(asDemo)).andReturn().getResponse().getContentAsString())
                .doesNotContain("No goals yet");
    }
}
