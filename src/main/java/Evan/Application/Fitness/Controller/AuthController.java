package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Security.SecurityDetailsService;
import Evan.Application.Fitness.Service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {
    private final AccountService accounts;
    private final SecurityDetailsService userDetails;
    private final SecurityContextRepository contextRepository;

    public AuthController(AccountService accounts, SecurityDetailsService userDetails,
                          SecurityContextRepository contextRepository) {
        this.accounts = accounts;
        this.userDetails = userDetails;
        this.contextRepository = contextRepository;
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
                         HttpServletRequest request, HttpServletResponse response) {
        if (!result.hasFieldErrors("username") && accounts.usernameTaken(signupForm.getUsername())) {
            result.rejectValue("username", "taken", "That username is already taken");
        }
        if (result.hasErrors()) {
            return "Signin";
        }
        UserLoginDetails account = accounts.register(signupForm);
        signIn(account.getUsername(), request, response);
        return "redirect:/onboarding";
    }

    /** New members go straight to onboarding instead of re-typing their password. */
    private void signIn(String username, HttpServletRequest request, HttpServletResponse response) {
        UserDetails principal = userDetails.loadUserByUsername(username);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId();
        contextRepository.saveContext(context, request, response);
    }

    private static boolean isSignedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !AuthorityUtils.authorityListToSet(auth.getAuthorities()).contains("ROLE_ANONYMOUS");
    }
}
