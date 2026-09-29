package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/")
    public String landing(Authentication auth) {
        return isSignedIn(auth) ? "redirect:/home" : "index";
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        return isSignedIn(auth) ? "redirect:/home" : "login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "Signin";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupForm signupForm, BindingResult result,
                         RedirectAttributes redirect) {
        if (!result.hasFieldErrors("username") && accounts.usernameTaken(signupForm.getUsername())) {
            result.rejectValue("username", "taken", "That username is already taken");
        }
        if (result.hasErrors()) {
            return "Signin";
        }
        accounts.register(signupForm);
        redirect.addFlashAttribute("flashSuccess", "Account created. Sign in to get started.");
        return "redirect:/login";
    }

    private static boolean isSignedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !AuthorityUtils.authorityListToSet(auth.getAuthorities()).contains("ROLE_ANONYMOUS");
    }
}
