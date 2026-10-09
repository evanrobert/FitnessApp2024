package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UsageEvent;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.UsageEventRepository;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.NutritionService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Service.UsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usage counting: what's recorded (never content), the owner-only report and retention. Synthetic data only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.admin-usernames=usage-owner")
class UsageTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired UsageEventRepository events;
    @Autowired UsageService usage;
    @Autowired NutritionService nutrition;
    @Autowired TodayService todayService;

    UserLoginDetails member;

    @BeforeEach
    void setUp() {
        member = TestUsers.create(accounts);
    }

    private List<UsageEvent> mine() {
        return events.findAll().stream().filter(e -> e.getUserId().equals(member.getId())).toList();
    }

    @Test
    void successfulLogsAreCountedWithMethodAndSecondsButNoContent() throws Exception {
        mvc.perform(post("/fuel").with(as(member)).with(csrf()).param("itemName", "Secret snack").param("calories", "100")
                .param("date", todayService.today(member.getId()).toString()).param("mealType", "SNACK").param("_elapsed", "8400"))
                .andExpect(status().is3xxRedirection());
        CalorieInformation entry = nutrition.entriesOn(member.getId(), todayService.today(member.getId())).get(0);
        mvc.perform(post("/fuel/{id}/relog", entry.getId()).with(as(member)).with(csrf()).param("_elapsed", "1900"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/train").with(as(member)).with(csrf()).param("sessionDate", LocalDate.now().toString())
                        .param("_source", "live").param("sets[0].exerciseName", "Bench Press").param("sets[0].block", "0")
                        .param("sets[0].weightLb", "100").param("sets[0].reps", "5"))
                .andExpect(status().is3xxRedirection());

        assertThat(mine()).extracting(e -> e.getKind() + ":" + e.getMethod() + ":" + e.getSeconds())
                .containsExactlyInAnyOrder("food:typed:8", "food:tap:2", "workout:live:null");
        // Only kind/method/seconds/day are stored.
        assertThat(mine().toString()).doesNotContain("Secret snack");
    }

    @Test
    void failedSavesAndUnknownSourcesAreNotTrustedBlindly() throws Exception {
        // Validation error: the form comes back (200), nothing is counted.
        mvc.perform(post("/fuel").with(as(member)).with(csrf()).param("itemName", "").param("_elapsed", "abc"))
                .andExpect(status().isOk());
        assertThat(mine()).isEmpty();
        mvc.perform(post("/train").with(as(member)).with(csrf()).param("sessionDate", LocalDate.now().toString())
                        .param("_source", "<script>").param("_elapsed", "99999999")
                        .param("sets[0].exerciseName", "Bench Press").param("sets[0].block", "0").param("sets[0].reps", "5"))
                .andExpect(status().is3xxRedirection());
        assertThat(mine()).extracting(e -> e.getMethod() + ":" + e.getSeconds()).containsExactly("typed:null");
    }

    @Test
    void reportIsForTheOwnerOnlyAndShowsTotals() throws Exception {
        usage.record(member.getId(), "food", "tap", 3);
        usage.record(member.getId(), "food", "tap", 5);
        usage.record(member.getId(), "food", "typed", 20);
        UsageService.Report report = usage.report(7);
        assertThat(report.activeMembers()).isGreaterThanOrEqualTo(1);
        assertThat(report.methods()).anySatisfy(m -> {
            assertThat(m.kind() + m.method()).isEqualTo("foodtap");
            assertThat(m.count()).isGreaterThanOrEqualTo(2);
        });

        mvc.perform(get("/admin/usage").with(as(member))).andExpect(status().isNotFound());
        String html = mvc.perform(get("/home").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("/admin/usage");

        UserLoginDetails owner = ownerAccount();
        String page = mvc.perform(get("/admin/usage").param("days", "7").with(as(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Members who logged").contains("Method and speed").doesNotContain(member.getUsername());
    }

    @Test
    void oldEventsArePurgedAndDeletingTheAccountRemovesThem() {
        usage.record(member.getId(), "weight", "form", 4);
        UsageEvent old = mine().get(0);
        old.setOccurredOn(LocalDate.now().minusDays(UsageService.KEEP_DAYS + 5));
        events.save(old);
        usage.record(member.getId(), "weight", "form", 4);
        usage.purgeOld();
        assertThat(mine()).hasSize(1);
        accounts.deleteAccount(member.getId());
        assertThat(mine()).isEmpty();
    }

    private UserLoginDetails ownerAccount() {
        Evan.Application.Fitness.Form.SignupForm form = new Evan.Application.Fitness.Form.SignupForm();
        form.setUsername("usage-owner");
        form.setEmail("owner-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com");
        form.setPassword("correct-horse-battery");
        form.setName("Owner");
        form.setWeight(170.0);
        return accounts.register(form);
    }
}
