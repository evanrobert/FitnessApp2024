package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.NutritionService;
import Evan.Application.Fitness.Service.TodayService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One-tap food: most-eaten and starred foods, logging again, and "same as yesterday". Synthetic data only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuickFoodTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired NutritionService nutrition;
    @Autowired TodayService todayService;

    UserLoginDetails member;
    LocalDate today;

    @BeforeEach
    void setUp() {
        member = TestUsers.create(accounts);
        today = todayService.today(member.getId());
    }

    private CalorieInformation eat(String name, double calories, LocalDate date, String meal) {
        CalorieInformation c = new CalorieInformation();
        c.setItemName(name);
        c.setCalories(calories);
        c.setProteins(20.0);
        c.setDate(date);
        c.setMealType(meal);
        return nutrition.logMeal(member.getId(), c);
    }

    private List<String> names() {
        return nutrition.quickFoods(member.getId(), 12).stream().map(NutritionService.QuickFood::name).toList();
    }

    @Test
    void mostEatenFirstThenStarredFoodsJumpToTheTop() {
        eat("Protein shake", 220, today.minusDays(3), "SNACK");
        eat("protein shake ", 220, today.minusDays(2), "SNACK");
        eat("Protein shake", 230, today.minusDays(1), "SNACK");
        CalorieInformation chicken = eat("Chicken rice bowl", 650, today.minusDays(1), "LUNCH");
        eat("Apple", 95, today, "SNACK");

        assertThat(names()).containsExactly("Protein shake", "Apple", "Chicken rice bowl");
        // The latest entry's numbers are used.
        assertThat(nutrition.quickFoods(member.getId(), 12).get(0).calories()).isEqualTo(230);

        assertThat(nutrition.toggleFavorite(member.getId(), chicken.getId())).isTrue();
        assertThat(names().get(0)).isEqualTo("Chicken rice bowl");
        assertThat(nutrition.quickFoods(member.getId(), 12).get(0).favorite()).isTrue();
        assertThat(nutrition.toggleFavorite(member.getId(), chicken.getId())).isFalse();
        assertThat(names().get(0)).isEqualTo("Protein shake");
    }

    @Test
    void oneTapLogsTodayInTheChosenMealAndNeverInTheFuture() throws Exception {
        CalorieInformation shake = eat("Protein shake", 220, today.minusDays(4), "SNACK");
        mvc.perform(post("/fuel/{id}/relog", shake.getId()).with(as(member)).with(csrf())
                        .param("date", today.plusDays(3).toString()).param("type", "BREAKFAST"))
                .andExpect(status().is3xxRedirection());
        List<CalorieInformation> logged = nutrition.entriesOn(member.getId(), today);
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getMealType()).isEqualTo("BREAKFAST");
        assertThat(logged.get(0).getCalories()).isEqualTo(220);
    }

    @Test
    void sameAsYesterdayCopiesTheWholeMealOnce() throws Exception {
        eat("Oats", 300, today.minusDays(1), "BREAKFAST");
        eat("Coffee", 10, today.minusDays(1), "BREAKFAST");
        eat("Burrito", 800, today.minusDays(1), "LUNCH");
        assertThat(nutrition.sameAsYesterday(member.getId(), today, "BREAKFAST")).hasSize(2);

        mvc.perform(post("/fuel/copy-meal").with(as(member)).with(csrf())
                        .param("from", today.minusDays(1).toString()).param("to", today.toString()).param("type", "BREAKFAST"))
                .andExpect(status().is3xxRedirection());
        assertThat(nutrition.entriesOn(member.getId(), today)).extracting(CalorieInformation::getItemName)
                .containsExactly("Oats", "Coffee");
        // Breakfast is done today, so the suggestion goes away.
        assertThat(nutrition.sameAsYesterday(member.getId(), today, "BREAKFAST")).isEmpty();
    }

    @Test
    void pagesShowTheOneTapListAndOthersCantUseYourEntries() throws Exception {
        CalorieInformation shake = eat("Vanilla protein shake", 220, today.minusDays(1), "SNACK");
        for (String page : new String[]{"/fuel", "/fuel/new"}) {
            String html = mvc.perform(get(page).with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("Tap to log again").contains("Vanilla protein shake");
        }
        UserLoginDetails other = TestUsers.create(accounts);
        mvc.perform(post("/fuel/{id}/relog", shake.getId()).with(as(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/fuel/{id}/favorite", shake.getId()).with(as(other)).with(csrf())).andExpect(status().isNotFound());
        assertThat(nutrition.entriesOn(other.getId(), today)).isEmpty();
    }

    @Test
    void deletingTheAccountRemovesStars() {
        CalorieInformation shake = eat("Protein shake", 220, today, "SNACK");
        nutrition.toggleFavorite(member.getId(), shake.getId());
        accounts.deleteAccount(member.getId());
        assertThat(nutrition.favoriteKeys(member.getId())).isEmpty();
    }
}
