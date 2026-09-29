package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.BodyMeasurement;
import Evan.Application.Fitness.Model.GoalMetric;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.BodyService;
import Evan.Application.Fitness.Service.GoalService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Web.ChartJson;
import Evan.Application.Fitness.Web.Flash;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@Controller
@RequestMapping("/body")
public class BodyController {
    private final BodyService body;
    private final GoalService goals;
    private final TodayService todayService;
    private final ChartJson charts;

    public BodyController(BodyService body, GoalService goals, TodayService todayService, ChartJson charts) {
        this.body = body;
        this.goals = goals;
        this.todayService = todayService;
        this.charts = charts;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("measurement")) {
            BodyMeasurement fresh = new BodyMeasurement();
            fresh.setMeasuredOn(todayService.today(me.getId()));
            model.addAttribute("measurement", fresh);
        }
        return view(me.getId(), model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("measurement") BodyMeasurement measurement,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors() && !BodyService.hasAnyValue(measurement)) {
            result.reject("empty", "Enter at least one measurement");
        }
        if (result.hasErrors()) {
            return view(me.getId(), model);
        }
        BodyMeasurement saved = body.save(me.getId(), null, measurement);
        Flash.success(redirect, saved.getWeightLb() != null ? "Weigh-in saved · " + saved.getWeightLb() + " lb" : "Measurements saved");
        return "redirect:/body";
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("measurement", body.get(me.getId(), id));
        model.addAttribute("editId", id);
        return view(me.getId(), model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                         @Valid @ModelAttribute("measurement") BodyMeasurement measurement, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors() && !BodyService.hasAnyValue(measurement)) {
            result.reject("empty", "Enter at least one measurement");
        }
        if (result.hasErrors()) {
            model.addAttribute("editId", id);
            return view(me.getId(), model);
        }
        body.save(me.getId(), id, measurement);
        Flash.success(redirect, "Measurement updated");
        return "redirect:/body";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, RedirectAttributes redirect) {
        body.delete(me.getId(), id);
        Flash.success(redirect, "Measurement deleted");
        return "redirect:/body";
    }

    private String view(Long userId, Model model) {
        LocalDate today = todayService.today(userId);
        List<BodyMeasurement> all = body.chronological(userId);
        List<BodyMeasurement> weights = all.stream().filter(m -> m.getWeightLb() != null).toList();

        List<String> labels = weights.stream().map(m -> m.getMeasuredOn().toString()).toList();
        List<Double> raw = weights.stream().map(BodyMeasurement::getWeightLb).toList();
        Optional<GoalService.Progress> weightGoal = goals.active(userId).stream()
                .filter(p -> p.goal().getMetric() == GoalMetric.BODY_WEIGHT).findFirst();
        model.addAttribute("weightChart", charts.write(ChartJson.spec("line", "labels", labels,
                "series", List.of(ChartJson.series("Weigh-ins", raw, "s1", "dots"),
                        ChartJson.series("7-entry average", ChartJson.rollingAverage(raw, 7), "s1", null)),
                "unit", "lb", "decimals", 1, "title", "Body weight trend",
                "target", weightGoal.map(p -> ChartJson.target(p.goal().getTargetValue(), "Goal")).orElse(null),
                "empty", "Weigh in on two or more days to see your trend. The average line smooths day-to-day water swings.")));

        model.addAttribute("waistChart", tapeChart(all, BodyMeasurement::getWaistIn, "Waist"));
        model.addAttribute("bodyFatChart", tapeChart(all, BodyMeasurement::getBodyFatPct, "Body fat"));

        BodyMeasurement latest = weights.isEmpty() ? null : weights.get(weights.size() - 1);
        model.addAttribute("latest", latest);
        model.addAttribute("change7", change(weights, today, 7));
        model.addAttribute("change30", change(weights, today, 30));
        model.addAttribute("weeklyRate", weeklyRate(weights, today));
        model.addAttribute("latestWaist", latestOf(all, BodyMeasurement::getWaistIn));
        model.addAttribute("latestBodyFat", latestOf(all, BodyMeasurement::getBodyFatPct));
        model.addAttribute("history", body.history(userId));
        model.addAttribute("weightGoal", weightGoal.orElse(null));
        model.addAttribute("today", today);
        return "body/index";
    }

    private String tapeChart(List<BodyMeasurement> all, Function<BodyMeasurement, Double> getter, String name) {
        List<BodyMeasurement> points = all.stream().filter(m -> getter.apply(m) != null).toList();
        return charts.write(ChartJson.spec("line", "labels", points.stream().map(m -> m.getMeasuredOn().toString()).toList(),
                "series", List.of(ChartJson.series(name, points.stream().map(getter).toList(), "s2", null)),
                "unit", name.equals("Body fat") ? "%" : "in", "decimals", 1, "height", 180, "title", name + " trend",
                "empty", "Two or more " + name.toLowerCase() + " entries draw a trend."));
    }

    private static Double latestOf(List<BodyMeasurement> all, Function<BodyMeasurement, Double> getter) {
        for (int i = all.size() - 1; i >= 0; i--) {
            if (getter.apply(all.get(i)) != null) {
                return getter.apply(all.get(i));
            }
        }
        return null;
    }

    /** Change between the latest weigh-in and the closest one at least {@code days} earlier. */
    static Double change(List<BodyMeasurement> weights, LocalDate today, int days) {
        if (weights.size() < 2) {
            return null;
        }
        BodyMeasurement latest = weights.get(weights.size() - 1);
        LocalDate cutoff = latest.getMeasuredOn().minusDays(days);
        BodyMeasurement base = null;
        for (BodyMeasurement m : weights) {
            if (!m.getMeasuredOn().isAfter(cutoff)) {
                base = m;
            }
        }
        if (base == null) {
            return null;
        }
        return Math.round((latest.getWeightLb() - base.getWeightLb()) * 10) / 10.0;
    }

    /** Least-squares slope over the last 28 days, in lb per week. */
    static Double weeklyRate(List<BodyMeasurement> weights, LocalDate today) {
        List<BodyMeasurement> recent = weights.stream().filter(m -> !m.getMeasuredOn().isBefore(today.minusDays(28))).toList();
        if (recent.size() < 3) {
            return null;
        }
        LocalDate origin = recent.get(0).getMeasuredOn();
        double n = recent.size(), sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (BodyMeasurement m : recent) {
            double x = ChronoUnit.DAYS.between(origin, m.getMeasuredOn());
            double y = m.getWeightLb();
            sx += x; sy += y; sxx += x * x; sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) {
            return null;
        }
        return Math.round((n * sxy - sx * sy) / denom * 7 * 100) / 100.0;
    }
}
