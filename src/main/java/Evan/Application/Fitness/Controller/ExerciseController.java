package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.Equipment;
import Evan.Application.Fitness.Model.Exercise;
import Evan.Application.Fitness.Model.MuscleGroup;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.RecordsService;
import Evan.Application.Fitness.Service.StrengthLevelService;
import Evan.Application.Fitness.Service.StrengthStandards;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Service.TrainingService;
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
import java.util.*;

@Controller
public class ExerciseController {
    private final TrainingService training;
    private final RecordsService records;
    private final TodayService todayService;
    private final ChartJson charts;
    private final StrengthLevelService strength;

    public ExerciseController(TrainingService training, RecordsService records, TodayService todayService, ChartJson charts,
                              StrengthLevelService strength) {
        this.strength = strength;
        this.training = training;
        this.records = records;
        this.todayService = todayService;
        this.charts = charts;
    }

    @GetMapping("/exercises")
    public String list(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("exercise")) {
            model.addAttribute("exercise", new Exercise());
        }
        return listView(me.getId(), model);
    }

    @PostMapping("/exercises")
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("exercise") Exercise exercise,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasFieldErrors("name") && training.exerciseNameTaken(me.getId(), exercise.getName())) {
            result.rejectValue("name", "taken", "You already have an exercise with that name");
        }
        if (result.hasErrors()) {
            return listView(me.getId(), model);
        }
        Exercise saved = training.createExercise(me.getId(), exercise);
        Flash.success(redirect, saved.getName() + " added to your library");
        return "redirect:/exercises#ex-" + saved.getId();
    }

    @PostMapping("/exercises/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, RedirectAttributes redirect) {
        if (training.deleteExercise(me.getId(), id)) {
            Flash.success(redirect, "Exercise removed");
        } else {
            Flash.error(redirect, "That exercise has logged sets, so it stays to keep your history intact.");
        }
        return "redirect:/exercises";
    }

    @GetMapping("/exercises/{id}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        Exercise exercise = training.exercise(me.getId(), id);
        List<RecordsService.SessionPoint> series = records.series(me.getId(), id);
        RecordsService.Summary summary = records.summary(me.getId());

        List<String> labels = series.stream().map(p -> p.date().toString()).toList();
        List<Double> e1rm = series.stream().map(p -> round(p.topE1rm())).toList();
        List<Double> top = series.stream().map(p -> round(p.topWeight())).toList();
        List<Double> volume = series.stream().map(p -> round(p.volume())).toList();

        model.addAttribute("exercise", exercise);
        model.addAttribute("record", summary.forExercise(id).orElse(null));
        StrengthLevelService.Setup setup = strength.setup(me.getId());
        model.addAttribute("level", strength.levels(setup, summary).get(id));
        model.addAttribute("levelSetupHint", StrengthStandards.covers(exercise.getName()) ? setup.missing() : null);
        model.addAttribute("history", reversed(series));
        model.addAttribute("prs", summary.events().stream().filter(e -> e.exercise().getId().equals(id)).toList());
        model.addAttribute("today", todayService.today(me.getId()));
        model.addAttribute("strengthChart", charts.write(ChartJson.spec("line", "labels", labels,
                "series", List.of(ChartJson.series("Estimated max", e1rm, "s1", null), ChartJson.series("Best set", top, "s2", null)),
                "unit", "lb", "title", exercise.getName() + " strength trend",
                "empty", "Log this exercise in two sessions to see a strength trend.")));
        model.addAttribute("volumeChart", charts.write(ChartJson.spec("bar", "labels", labels, "values", volume, "unit", "lb",
                "name", "Total lifted", "title", exercise.getName() + " volume per session",
                "empty", "No working sets logged yet.")));
        return "train/exercise";
    }

    @GetMapping("/records")
    public String records(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        RecordsService.Summary summary = records.summary(me.getId());
        LocalDate today = todayService.today(me.getId());
        model.addAttribute("records", summary.records().stream().filter(r -> r.bestE1rm() > 0)
                .sorted(Comparator.comparingDouble(RecordsService.ExerciseRecord::bestE1rm).reversed()).toList());
        model.addAttribute("events", summary.events().stream().limit(40).toList());
        model.addAttribute("prs30", summary.prsSince(today.minusDays(30)));
        model.addAttribute("prs365", summary.prsSince(today.minusDays(365)));
        StrengthLevelService.Setup setup = strength.setup(me.getId());
        model.addAttribute("levels", strength.levels(setup, summary));
        model.addAttribute("levelSetupHint", setup.missing());
        model.addAttribute("today", today);
        return "train/records";
    }

    private String listView(Long userId, Model model) {
        List<Exercise> all = training.visibleExercises(userId);
        RecordsService.Summary summary = records.summary(userId);
        Map<Long, RecordsService.ExerciseRecord> stats = new HashMap<>();
        summary.records().forEach(r -> stats.put(r.exercise().getId(), r));
        model.addAttribute("exercises", all);
        model.addAttribute("stats", stats);
        model.addAttribute("muscles", MuscleGroup.values());
        model.addAttribute("equipment", Equipment.values());
        model.addAttribute("today", todayService.today(userId));
        return "train/exercises";
    }

    private static Double round(double v) {
        return v <= 0 ? null : Math.round(v * 10) / 10.0;
    }

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }
}
