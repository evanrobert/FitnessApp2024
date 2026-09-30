package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.*;
import Evan.Application.Fitness.Web.Flash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;

@Controller
public class ProfileController {
    private final ProfileService profiles;
    private final LimitationService limitations;
    private final BodyService body;
    private final TrainingService training;
    private final AccountService accounts;
    private final TodayService todayService;

    public ProfileController(ProfileService profiles, LimitationService limitations, BodyService body,
                             TrainingService training, AccountService accounts, TodayService todayService) {
        this.profiles = profiles;
        this.limitations = limitations;
        this.body = body;
        this.training = training;
        this.accounts = accounts;
        this.todayService = todayService;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", profiles.profile(me.getId()));
        }
        if (!model.containsAttribute("limitation")) {
            Limitation fresh = new Limitation();
            fresh.setStartedOn(todayService.today(me.getId()));
            model.addAttribute("limitation", fresh);
        }
        return view(me.getId(), model);
    }

    @PostMapping("/profile")
    public String save(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("profile") UserInformation profile,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("limitation", new Limitation());
            return view(me.getId(), model);
        }
        profiles.update(me.getId(), profile);
        Flash.success(redirect, "Profile saved");
        return "redirect:/profile";
    }

    @PostMapping("/profile/limitations")
    public String addLimitation(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("limitation") Limitation limitation,
                                BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("profile", profiles.profile(me.getId()));
            model.addAttribute("openLimitation", true);
            return view(me.getId(), model);
        }
        limitations.save(me.getId(), null, limitation);
        Flash.success(redirect, "Limitation added — you'll see it when logging workouts");
        return "redirect:/profile#limitations";
    }

    @PostMapping("/profile/limitations/{id}/status")
    public String limitationStatus(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                                   @RequestParam LimitationStatus status, RedirectAttributes redirect) {
        Limitation existing = limitations.get(me.getId(), id);
        existing.setStatus(status);
        limitations.save(me.getId(), id, existing);
        Flash.success(redirect, status == LimitationStatus.RESOLVED ? "Marked resolved" : "Status updated");
        return "redirect:/profile#limitations";
    }

    @PostMapping("/profile/limitations/{id}/delete")
    public String deleteLimitation(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, RedirectAttributes redirect) {
        limitations.delete(me.getId(), id);
        Flash.success(redirect, "Limitation removed");
        return "redirect:/profile#limitations";
    }

    @PostMapping("/profile/delete-account")
    public String deleteAccount(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam String password,
                                @RequestParam(required = false) String confirm, HttpServletRequest request,
                                HttpServletResponse response, RedirectAttributes redirect) {
        if (!"DELETE".equals(confirm) || !accounts.passwordMatches(me.getId(), password)) {
            Flash.error(redirect, "Type DELETE and your current password to confirm.");
            return "redirect:/account#delete";
        }
        accounts.deleteAccount(me.getId());
        new SecurityContextLogoutHandler().logout(request, response, null);
        return "redirect:/login?deleted";
    }

    // ---- Onboarding: the few answers that make every number personal -------

    @GetMapping("/onboarding")
    public String onboarding(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        UserInformation profile = profiles.profile(me.getId());
        if (profile.getWeeklyWorkoutTarget() == null) {
            profile.setWeeklyWorkoutTarget(3);
        }
        model.addAttribute("profile", profile);
        model.addAttribute("currentWeight", body.latestWeight(me.getId()).map(BodyMeasurement::getWeightLb).orElse(null));
        addChoices(model, profile);
        return "profile/onboarding";
    }

    @PostMapping("/onboarding")
    public String finishOnboarding(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("profile") UserInformation form,
                                   BindingResult result, @RequestParam(required = false) Double currentWeight, Model model,
                                   RedirectAttributes redirect) {
        if (form.getPrimaryGoal() == null) {
            result.rejectValue("primaryGoal", "required", "Pick what you're training for");
        }
        if (currentWeight != null && (currentWeight < 50 || currentWeight > 900)) {
            result.reject("weight", "Check your weight");
        }
        if (result.hasErrors()) {
            model.addAttribute("currentWeight", currentWeight);
            addChoices(model, form);
            return "profile/onboarding";
        }
        UserInformation current = profiles.profile(me.getId());
        // Onboarding only sets its own fields; keep the rest of the profile as-is.
        form.setName(current.getName());
        form.setBio(current.getBio());
        form.setGymName(current.getGymName());
        form.setMembershipPlan(current.getMembershipPlan());
        form.setMembershipStartedOn(current.getMembershipStartedOn());
        form.setMembershipRenewsOn(current.getMembershipRenewsOn());
        form.setMembershipMonthlyCost(current.getMembershipMonthlyCost());
        profiles.update(me.getId(), form);
        LocalDate today = todayService.today(me.getId());
        if (currentWeight != null) {
            // Update today's weigh-in if one exists (e.g. from sign-up), otherwise record one.
            BodyMeasurement todays = body.latestWeight(me.getId()).filter(m -> m.getMeasuredOn().equals(today)).orElse(null);
            BodyMeasurement values = todays != null ? todays : new BodyMeasurement();
            values.setMeasuredOn(today);
            values.setWeightLb(currentWeight);
            body.save(me.getId(), todays == null ? null : todays.getId(), values);
        }
        Flash.success(redirect, "You're set up. Here are suggested daily targets based on your answers.");
        return "redirect:/fuel/targets";
    }

    private String view(Long userId, Model model) {
        UserInformation profile = (UserInformation) model.getAttribute("profile");
        LocalDate today = todayService.today(userId);
        long gymVisits30 = training.between(userId, today.minusDays(29), today).stream()
                .filter(s -> s.getLocationType() == LocationType.GYM).count();
        model.addAttribute("gymVisits30", gymVisits30);
        if (profile != null && profile.getMembershipMonthlyCost() != null && gymVisits30 > 0) {
            model.addAttribute("costPerVisit", profile.getMembershipMonthlyCost()
                    .divide(BigDecimal.valueOf(gymVisits30), 2, RoundingMode.HALF_UP));
        }
        model.addAttribute("limitations", limitations.all(userId));
        model.addAttribute("limitationStatuses", LimitationStatus.values());
        model.addAttribute("age", profile == null ? null : profile.ageIn(Year.from(today)));
        model.addAttribute("today", today);
        addChoices(model, profile);
        return "profile/index";
    }

    private void addChoices(Model model, UserInformation profile) {
        model.addAttribute("sexes", Sex.values());
        model.addAttribute("activityLevels", ActivityLevel.values());
        model.addAttribute("goals", PrimaryGoal.values());
        model.addAttribute("experienceLevels", ExperienceLevel.values());
        model.addAttribute("zones", profiles.zoneChoices(profile == null ? null : profile.getTimeZone()));
    }
}
