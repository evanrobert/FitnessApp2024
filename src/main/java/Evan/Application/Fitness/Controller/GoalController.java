package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.Goal;
import Evan.Application.Fitness.Model.GoalMetric;
import Evan.Application.Fitness.Model.GoalStatus;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.GoalService;
import Evan.Application.Fitness.Service.MetricService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Service.TrainingService;
import Evan.Application.Fitness.Web.Flash;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/goals")
public class GoalController {
    private final GoalService goals;
    private final TrainingService training;
    private final MetricService metrics;
    private final TodayService todayService;

    public GoalController(GoalService goals, TrainingService training, MetricService metrics, TodayService todayService) {
        this.goals = goals;
        this.training = training;
        this.metrics = metrics;
        this.todayService = todayService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        model.addAttribute("progress", goals.progressFor(me.getId()));
        model.addAttribute("today", todayService.today(me.getId()));
        return "goals/list";
    }

    @GetMapping("/new")
    public String newGoal(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) GoalMetric metric,
                          @RequestParam(required = false) Long exerciseId, @RequestParam(required = false) Long customMetricId,
                          Model model) {
        Goal goal = goals.blank(me.getId());
        if (metric != null) {
            goal.setMetric(metric);
        }
        return form(me.getId(), goal, null, exerciseId, customMetricId, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("goal") Goal goal,
                         BindingResult result, @RequestParam(required = false) Long exerciseId,
                         @RequestParam(required = false) Long customMetricId, Model model, RedirectAttributes redirect) {
        return save(me.getId(), null, goal, result, exerciseId, customMetricId, model, redirect);
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        Goal goal = goals.get(me.getId(), id);
        return form(me.getId(), goal, id, goal.getExercise() == null ? null : goal.getExercise().getId(),
                goal.getCustomMetric() == null ? null : goal.getCustomMetric().getId(), model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @Valid @ModelAttribute("goal") Goal goal,
                         BindingResult result, @RequestParam(required = false) Long exerciseId,
                         @RequestParam(required = false) Long customMetricId, Model model, RedirectAttributes redirect) {
        return save(me.getId(), id, goal, result, exerciseId, customMetricId, model, redirect);
    }

    @PostMapping("/{id}/status")
    public String status(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestParam GoalStatus status,
                         RedirectAttributes redirect) {
        goals.setStatus(me.getId(), id, status);
        Flash.success(redirect, switch (status) {
            case ACHIEVED -> "Goal achieved — logged as a milestone";
            case ARCHIVED -> "Goal archived";
            case ACTIVE -> "Goal reactivated";
        });
        return "redirect:/goals";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, RedirectAttributes redirect) {
        goals.delete(me.getId(), id);
        Flash.success(redirect, "Goal deleted");
        return "redirect:/goals";
    }

    private String save(Long userId, Long id, Goal goal, BindingResult result, Long exerciseId, Long customMetricId,
                        Model model, RedirectAttributes redirect) {
        String problem = result.hasErrors() ? null : goals.problemWith(userId, goal, exerciseId, customMetricId);
        if (problem != null) {
            result.reject("goal", problem);
        }
        if (result.hasErrors()) {
            return form(userId, goal, id, exerciseId, customMetricId, model);
        }
        goals.save(userId, id, goal, exerciseId, customMetricId);
        Flash.success(redirect, id == null ? "Goal set — progress updates as you log" : "Goal updated");
        return "redirect:/goals";
    }

    private String form(Long userId, Goal goal, Long id, Long exerciseId, Long customMetricId, Model model) {
        model.addAttribute("goal", goal);
        model.addAttribute("goalId", id);
        model.addAttribute("metricsList", GoalMetric.values());
        model.addAttribute("exercises", training.visibleExercises(userId));
        model.addAttribute("customMetrics", metrics.active(userId));
        model.addAttribute("exerciseId", exerciseId);
        model.addAttribute("customMetricId", customMetricId);
        return "goals/form";
    }
}
