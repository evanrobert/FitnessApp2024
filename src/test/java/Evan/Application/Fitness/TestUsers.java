package Evan.Application.Fitness;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.AccountService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** Synthetic test members only; no real personal data. */
public final class TestUsers {
    private TestUsers() {
    }

    public static UserLoginDetails create(AccountService accounts) {
        SignupForm form = new SignupForm();
        form.setUsername("member-" + UUID.randomUUID().toString().substring(0, 8));
        form.setPassword("correct-horse-battery");
        form.setName("Test Member");
        form.setAge(30);
        form.setWeight(180);
        return accounts.register(form);
    }

    public static RequestPostProcessor as(UserLoginDetails account) {
        return user(new AppUserPrincipal(account.getId(), account.getUsername(), account.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
