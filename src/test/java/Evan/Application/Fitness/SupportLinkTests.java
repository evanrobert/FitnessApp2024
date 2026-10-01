package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Web.ViewAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The optional "Support the developer" link: shown only for a configured https page. */
@SpringBootTest(properties = "app.support.url=https://ko-fi.com/example")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportLinkTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;

    @Test
    void configuredLinkAppearsInTheMenuAndSettingsAndOpensSafely() throws Exception {
        UserLoginDetails member = TestUsers.create(accounts);
        String home = mvc.perform(get("/home").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(home).contains("href=\"https://ko-fi.com/example\"").contains("Support the developer")
                .contains("rel=\"noopener noreferrer\"");
        String account = mvc.perform(get("/account").with(as(member))).andReturn().getResponse().getContentAsString();
        assertThat(account).contains("Buy me a coffee");
    }

    @Test
    void onlySecureLinksAreAccepted() {
        assertThat(new ViewAdvice("Evan Fitness", "http://ko-fi.com/example").supportUrl()).isNull();
        assertThat(new ViewAdvice("Evan Fitness", "javascript:alert(1)").supportUrl()).isNull();
        assertThat(new ViewAdvice("Evan Fitness", "").supportUrl()).isNull();
        assertThat(new ViewAdvice("Evan Fitness", " https://buymeacoffee.com/example ").supportUrl())
                .isEqualTo("https://buymeacoffee.com/example");
    }
}
