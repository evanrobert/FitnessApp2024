package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.DailyCheckIn;
import Evan.Application.Fitness.Model.UserMacroInformation;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.CheckInService;
import Evan.Application.Fitness.Service.NutritionService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Web.ChartJson;
import Evan.Application.Fitness.Web.Flash;
import Evan.Application.Fitness.Web.ViewAdvice;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/recover")
public class RecoveryController {
    private final CheckInService checkIns;
    private final NutritionService nutrition;
    private final TodayService todayService;
    private final ChartJson charts;

    public RecoveryController(CheckInService checkIns, NutritionService nutrition, TodayService todayService, ChartJson charts) {
        this.checkIns = checkIns;
        this.nutrition = nutrition;
        this.todayService = todayService;
        this.charts = charts;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal me,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        Long userId = me.getId();
        LocalDate today = todayService.today(userId);
        LocalDate day = date == null || date.isAfter(today) ? today : date;
        if (!model.containsAttribute("checkIn")) {
            model.addAttribute("checkIn", checkIns.formFor(userId, day));
        }
        return view(userId, day, today, model);
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("checkIn") DailyCheckIn checkIn,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        LocalDate today = todayService.today(me.getId());
        if (checkIn.getCheckInDate() != null && checkIn.getCheckInDate().isAfter(today)) {
            result.rejectValue("checkInDate", "future", "Check-ins can't be in the future");
        }
        if (result.hasErrors()) {
            return view(me.getId(), checkIn.getCheckInDate() == null ? today : checkIn.getCheckInDate(), today, model);
        }
        DailyCheckIn saved = checkIns.save(me.getId(), checkIn);
        Integer readiness = saved.readinessScore();
        Flash.success(redirect, readiness == null ? "Check-in saved" : "Check-in saved · readiness " + readiness);
        return "redirect:/recover?date=" + saved.getCheckInDate();
    }

    @PostMapping("/water")
    public String water(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "16") double ounces,
                        @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        double clamped = Math.max(-64, Math.min(64, ounces));
        double total = checkIns.addWater(me.getId(), clamped);
        Flash.success(redirect, "Water logged · " + Math.round(total) + " oz today");
        return "redirect:" + ViewAdvice.safeReturn(returnTo, "/recover");
    }

    private String view(Long userId, LocalDate day, LocalDate today, Model model) {
        LocalDate from = today.minusDays(29);
        List<DailyCheckIn> month = checkIns.between(userId, from, today);
        Map<LocalDate, DailyCheckIn> byDay = new HashMap<>();
        month.forEach(c -> byDay.put(c.getCheckInDate(), c));
        List<String> labels = new ArrayList<>();
        List<Double> readiness = new ArrayList<>(), sleep = new ArrayList<>(), energy = new ArrayList<>(), water = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            DailyCheckIn c = byDay.get(d);
            labels.add(d.toString());
            readiness.add(c == null || c.readinessScore() == null ? null : c.readinessScore().doubleValue());
            sleep.add(c == null ? null : c.getSleepHours());
            energy.add(c == null || c.getEnergy() == null ? null : c.getEnergy().doubleValue());
            water.add(c == null || c.getWaterOz() == null ? 0.0 : c.getWaterOz());
        }
        Double waterTarget = nutrition.targetsFor(userId).map(UserMacroInformation::getDailyWaterOz).orElse(null);

        model.addAttribute("day", day);
        model.addAttribute("today", today);
        model.addAttribute("isToday", day.equals(today));
        model.addAttribute("todayCheckIn", byDay.get(today));
        model.addAttribute("waterTarget", waterTarget);
        model.addAttribute("history", checkIns.history(userId).stream().limit(30).toList());
        model.addAttribute("avgSleep", month.stream().filter(c -> c.getSleepHours() != null).mapToDouble(DailyCheckIn::getSleepHours).average().orElse(Double.NaN));
        model.addAttribute("avgReadiness", month.stream().map(DailyCheckIn::readinessScore).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(Double.NaN));
        model.addAttribute("checkInDays", month.size());
        model.addAttribute("readinessChart", charts.write(ChartJson.spec("line", "labels", labels,
                "series", List.of(ChartJson.series("Readiness", readiness, "s1", null)), "unit", "", "decimals", 0,
                "title", "Readiness, last 30 days", "empty", "Check in on two or more days to see your readiness trend.")));
        model.addAttribute("sleepChart", charts.write(ChartJson.spec("line", "labels", labels,
                "series", List.of(ChartJson.series("Sleep", sleep, "s3", null)), "unit", "h", "decimals", 1,
                "target", ChartJson.target(8, "8 h"), "title", "Sleep, last 30 days", "empty", "Log sleep in your check-ins to see the trend.")));
        model.addAttribute("waterChart", charts.write(ChartJson.spec("bar", "labels", labels, "values", water, "unit", "oz",
                "name", "Water", "title", "Water, last 30 days", "highlight", labels.size() - 1,
                "target", waterTarget != null && waterTarget > 0 ? ChartJson.target(waterTarget, "Target") : null,
                "empty", "Tap +16 oz from the quick log to start tracking hydration.")));
        model.addAttribute("energyChart", charts.write(ChartJson.spec("line", "labels", labels,
                "series", List.of(ChartJson.series("Energy", energy, "s2", null)), "decimals", 0,
                "title", "Energy (1-5), last 30 days", "empty", "Rate your energy in check-ins to see it here.")));
        return "recover/index";
    }
}
