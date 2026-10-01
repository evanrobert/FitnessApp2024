package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Form.PasswordForm;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.AccountSecurityService;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.MailService;
import Evan.Application.Fitness.Service.WeeklySummaryService;
import Evan.Application.Fitness.Web.Flash;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

/** Settings → Account & security: email, password, email summaries. */
@Controller
public class AccountController {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserLoginDetailsRepository users;
    private final AccountService accounts;
    private final AccountSecurityService security;
    private final WeeklySummaryService weekly;
    private final MailService mail;

    public AccountController(UserLoginDetailsRepository users, AccountService accounts, AccountSecurityService security,
                             WeeklySummaryService weekly, MailService mail) {
        this.users = users;
        this.accounts = accounts;
        this.security = security;
        this.weekly = weekly;
        this.mail = mail;
    }

    @GetMapping("/account")
    public String account(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        model.addAttribute("account", users.findById(me.getId()).orElseThrow());
        model.addAttribute("mailConfigured", mail.isConfigured());
        model.addAttribute("mailLogMode", mail.isLogMode());
        return "profile/account";
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal me, @ModelAttribute PasswordForm passwordForm,
                                 BindingResult result, HttpServletRequest request, Model model, RedirectAttributes redirect) {
        UserLoginDetails account = users.findById(me.getId()).orElseThrow();
        if (!accounts.passwordMatches(me.getId(), passwordForm.getCurrentPassword())) {
            result.rejectValue("currentPassword", "wrong", "That isn't your current password");
        } else {
            AuthController.validateNewPassword(passwordForm, account, result);
        }
        if (result.hasErrors()) {
            model.addAttribute("openPassword", true);
            return account(me, model);
        }
        security.changePassword(me.getId(), passwordForm.getNewPassword(), request.getSession().getId());
        Flash.success(redirect, "Password changed. Other devices were signed out.");
        return "redirect:/account";
    }

    @PostMapping("/account/email")
    public String changeEmail(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam String email,
                              @RequestParam(required = false) String currentPassword, RedirectAttributes redirect) {
        String value = email == null ? "" : email.trim();
        if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
            Flash.error(redirect, "Check the email address.");
        } else if (!accounts.passwordMatches(me.getId(), currentPassword)) {
            Flash.error(redirect, "Enter your current password to change your email.");
        } else if (security.emailInUse(value, me.getId())) {
            Flash.error(redirect, "Another account already uses that email.");
        } else {
            security.changeEmail(me.getId(), value);
            Flash.success(redirect, "Email saved. Check your inbox for a confirmation link.");
        }
        return "redirect:/account#email";
    }

    @PostMapping("/account/email/resend")
    public String resend(@AuthenticationPrincipal AppUserPrincipal me, RedirectAttributes redirect) {
        switch (security.resendVerification(me.getId())) {
            case SENT -> Flash.success(redirect, "Confirmation email sent. Check your inbox (and spam folder).");
            case ALREADY_CONFIRMED -> Flash.success(redirect, "Your email is already confirmed. To test sending, use \"Send me a preview\" below.");
            case NO_EMAIL -> Flash.error(redirect, "Add an email address first.");
            case TOO_MANY -> Flash.error(redirect, "Several confirmation emails were sent in the last hour. Check your inbox (and spam) or try again later.");
        }
        return "redirect:/account#email";
    }

    @PostMapping("/account/notifications")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal me,
                                @RequestParam(defaultValue = "false") boolean weeklyEmail, RedirectAttributes redirect) {
        if (!security.setWeeklyEmail(me.getId(), weeklyEmail)) {
            Flash.error(redirect, "Confirm your email address first, then turn on weekly summaries.");
        } else {
            Flash.success(redirect, weeklyEmail ? "Weekly summary on. It arrives Monday mornings." : "Weekly summary off.");
        }
        return "redirect:/account#notifications";
    }

    @PostMapping("/account/notifications/preview")
    public String preview(@AuthenticationPrincipal AppUserPrincipal me, RedirectAttributes redirect) {
        if (weekly.sendPreview(me.getId())) {
            Flash.success(redirect, "Preview sent. Check your inbox.");
        } else {
            Flash.error(redirect, "Confirm your email address first.");
        }
        return "redirect:/account#notifications";
    }
}
