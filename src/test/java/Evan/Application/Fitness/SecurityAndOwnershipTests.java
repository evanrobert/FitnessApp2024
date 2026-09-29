package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.CalorieInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.NutritionService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndOwnershipTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired NutritionService nutrition;
    @Autowired CalorieInformationRepository entries;
    @Autowired UserLoginDetailsRepository users;

    UserLoginDetails alice;
    UserLoginDetails bob;
    CalorieInformation bobsMeal;

    @BeforeEach
    void setUp() {
        alice = TestUsers.create(accounts);
        bob = TestUsers.create(accounts);
        CalorieInformation meal = new CalorieInformation();
        meal.setItemName("Bob's oats");
        meal.setCalories(400);
        meal.setDate(LocalDate.of(2026, 1, 5));
        bobsMeal = nutrition.logMeal(bob.getId(), meal);
    }

    @Test
    void anonymousVisitorsAreSentToLogin() throws Exception {
        mvc.perform(get("/home")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/Post/Custom/Meal").with(as(alice)).param("itemName", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submittedIdCannotOverwriteAnotherMembersMeal() throws Exception {
        mvc.perform(post("/Post/Custom/Meal").with(as(alice)).with(csrf())
                        .param("id", bobsMeal.getId().toString())
                        .param("itemName", "Hijacked").param("calories", "1"))
                .andExpect(status().is3xxRedirection());

        CalorieInformation untouched = entries.findById(bobsMeal.getId()).orElseThrow();
        assertThat(untouched.getItemName()).isEqualTo("Bob's oats");
        assertThat(entries.findAllByUserIdOrderByDateDescIdDesc(alice.getId()))
                .extracting(CalorieInformation::getItemName).containsExactly("Hijacked");
    }

    @Test
    void bulkEditIgnoresRowsOwnedBySomeoneElse() throws Exception {
        mvc.perform(post("/edit/nutrition/information").with(as(alice)).with(csrf())
                        .param("calorieInformationList[0].id", bobsMeal.getId().toString())
                        .param("calorieInformationList[0].itemName", "Hijacked"))
                .andExpect(status().is3xxRedirection());
        assertThat(entries.findById(bobsMeal.getId()).orElseThrow().getItemName()).isEqualTo("Bob's oats");
    }

    @Test
    void cannotDeleteAnotherMembersMeal() throws Exception {
        mvc.perform(post("/nutrition/{id}/delete", bobsMeal.getId()).with(as(alice)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(entries.existsById(bobsMeal.getId())).isTrue();
    }

    @Test
    void signupRejectsDuplicateUsernamesCaseInsensitively() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("username", bob.getUsername().toUpperCase())
                        .param("password", "another-password").param("name", "Copycat")
                        .param("weight", "170"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("signupForm", "username", "taken"));
    }

    @Test
    void signupCannotGrantExtraRoles() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("username", "roleseeker").param("password", "long-enough-pw")
                        .param("name", "Role Seeker").param("weight", "170")
                        .param("roles[0].name", "ROLE_ADMIN"))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findByUsername("roleseeker").orElseThrow().getRoles())
                .extracting("name").containsExactly("ROLE_USER");
    }

    @Test
    void csvExportEscapesCommasAndNeutralisesFormulas() throws Exception {
        CalorieInformation meal = new CalorieInformation();
        meal.setItemName("=HYPERLINK(\"x\"), salmon");
        meal.setDate(LocalDate.of(2026, 1, 6));
        nutrition.logMeal(alice.getId(), meal);

        String csv = mvc.perform(get("/download/nutrition").with(as(alice)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\"), salmon\"").doesNotContain("Bob's oats");
    }
}
