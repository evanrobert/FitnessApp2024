package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Form.PasswordForm;
import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Security.SecurityDetailsService;
import Evan.Application.Fitness.Service.AccountSecurityService;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.PasswordPolicy;
import Evan.Application.Fitness.Web.Flash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AccountService accounts;
    private final AccountSecurityService security;
    private final SecurityDetailsService userDetails;
    private final SecurityContextRepository contextRepository;
    private final SessionRegistry sessionRegistry;

    public AuthController(AccountService accounts, AccountSecurityService security, SecurityDetailsService userDetails,
                          SecurityContextRepository contextRepository, SessionRegistry sessionRegistry) {
        this.accounts = accounts;
        this.security = security;
        this.userDetails = userDetails;
        this.contextRepository = contextRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/")
    public String landing(Authentication auth) {
        return isSignedIn(auth) ? "redirect:/home" : "auth/landing";
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        return isSignedIn(auth) ? "redirect:/home" : "auth/login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupForm signupForm, BindingResult result,
                         HttpServletRequest request, HttpServletResponse response) {
        if (!result.hasFieldErrors("username") && accounts.usernameTaken(signupForm.getUsername())) {
            result.rejectValue("username", "taken", "That username is already taken");
        }
        if (!result.hasFieldErrors("email") && security.emailInUse(signupForm.getEmail(), null)) {
            result.rejectValue("email", "taken", "An account already uses that email. Log in or reset your password.");
        }
        if (!result.hasFieldErrors("password")) {
            String problem = PasswordPolicy.problem(signupForm.getPassword(), signupForm.getUsername(), signupForm.getEmail());
            if (problem != null) {
                result.rejectValue("password", "weak", problem);
            }
        }
        if (result.hasErrors()) {
            return "auth/signup";
        }
        UserLoginDetails account = accounts.register(signupForm);
        security.sendVerificationTo(account);
        signIn(account.getUsername(), request, response);
        return "redirect:/onboarding";
    }

    // ---- Forgot / reset password ----------------------------------------------

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "auth/forgot";
    }

    /** Same response whether or not the account exists, so it can't be used to find accounts. */
    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam(required = false) String login, RedirectAttributes redirect) {
        if (login != null && !login.isBlank() && login.length() <= 254) {
            security.requestPasswordReset(login);
        }
        redirect.addFlashAttribute("sent", true);
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("valid", security.resetTokenValid(token));
        PasswordForm form = new PasswordForm();
        form.setToken(token);
        model.addAttribute("passwordForm", form);
        return "auth/reset";
    }

    @PostMapping("/reset-password")
    public String reset(@ModelAttribute PasswordForm passwordForm, BindingResult result, Model model,
                        RedirectAttributes redirect) {
        UserLoginDetails owner = security.resetTokenOwner(passwordForm.getToken()).orElse(null);
        if (owner == null) {
            model.addAttribute("valid", false);
            return "auth/reset";
        }
        if (!validateNewPassword(passwordForm, owner, result)) {
            model.addAttribute("valid", true);
            return "auth/reset";
        }
        if (!security.resetPassword(passwordForm.getToken(), passwordForm.getNewPassword())) {
            model.addAttribute("valid", false);
            return "auth/reset";
        }
        Flash.success(redirect, "Password updated. Log in with your new password.");
        return "redirect:/login";
    }

    /** Shared by reset and change: policy, and the two entries must match. */
    static boolean validateNewPassword(PasswordForm form, UserLoginDetails account, BindingResult result) {
        String problem = PasswordPolicy.problem(form.getNewPassword(), account.getUsername(), account.getEmail());
        if (problem != null) {
            result.rejectValue("newPassword", "weak", problem);
        } else if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "The passwords don't match");
        }
        return !result.hasErrors();
    }

    // ---- Links from emails ------------------------------------------------------

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String token, Authentication auth,
                              RedirectAttributes redirect) {
        boolean ok = token != null && security.verifyEmail(token);
        if (ok) {
            Flash.success(redirect, "Email confirmed. You can now reset your password by email and turn on weekly summaries.");
        } else {
            Flash.error(redirect, "That confirmation link is invalid or has expired. Send a new one from Settings.");
        }
        return isSignedIn(auth) ? "redirect:/profile#account" : "redirect:/login";
    }

    /** GET only shows a confirmation button: mail scanners that prefetch links mustn't unsubscribe anyone. */
    @GetMapping("/email/unsubscribe")
    public String unsubscribeForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", security.unsubscribeTokenValid(token));
        return "auth/unsubscribe";
    }

    /** Also the target of the one-click List-Unsubscribe-Post header, so it accepts posts without a CSRF token. */
    @PostMapping("/email/unsubscribe")
    public String unsubscribe(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("done", security.unsubscribe(token));
        model.addAttribute("valid", true);
        return "auth/unsubscribe";
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
        sessionRegistry.registerNewSession(request.getSession().getId(), principal);
    }

    private static boolean isSignedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !AuthorityUtils.authorityListToSet(auth.getAuthorities()).contains("ROLE_ANONYMOUS");
    }
}
