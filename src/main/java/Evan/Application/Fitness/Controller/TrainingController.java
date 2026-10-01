package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Form.SessionForm;
import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.*;
import Evan.Application.Fitness.Web.ChartJson;
import Evan.Application.Fitness.Web.Flash;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Controller
@RequestMapping("/train")
public class TrainingController {
    private final TrainingService training;
    private final RecordsService records;
    private final LimitationService limitations;
    private final ProfileService profiles;
    private final TodayService todayService;
    private final ChartJson charts;
    private final Fmt fmt;

    public TrainingController(TrainingService training, RecordsService records, LimitationService limitations,
                              ProfileService profiles, TodayService todayService, ChartJson charts, Fmt fmt) {
        this.training = training;
        this.records = records;
        this.limitations = limitations;
        this.profiles = profiles;
        this.todayService = todayService;
        this.charts = charts;
        this.fmt = fmt;
    }

    @GetMapping
    public String log(@AuthenticationPrincipal AppUserPrincipal me,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) SessionFocus focus,
                      @RequestParam(required = false) Long exercise,
                      @RequestParam(required = false) String q,
                      Model model) {
        Long userId = me.getId();
        LocalDate today = todayService.today(userId);
        TrainingService.Filter filter = new TrainingService.Filter(from, to, focus, exercise, q);
        List<WorkoutSession> all = training.history(userId);
        List<WorkoutSession> shown = filter.isActive() ? training.search(userId, filter) : all;

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WorkoutSession> thisWeek = all.stream().filter(s -> !s.getSessionDate().isBefore(weekStart)).toList();
        List<WorkoutSession> last28 = all.stream().filter(s -> s.getSessionDate().isAfter(today.minusDays(28))).toList();

        // Weekly volume, last 12 weeks, oldest first.
        List<String> weekLabels = new ArrayList<>();
        List<Double> weekVolume = new ArrayList<>();
        for (int w = 11; w >= 0; w--) {
            LocalDate start = weekStart.minusWeeks(w);
            LocalDate end = start.plusDays(6);
            weekLabels.add(start.toString());
            weekVolume.add(all.stream().filter(s -> !s.getSessionDate().isBefore(start) && !s.getSessionDate().isAfter(end))
                    .mapToDouble(WorkoutSession::volume).sum());
        }

        model.addAttribute("sessions", shown);
        model.addAttribute("filter", filter);
        model.addAttribute("focuses", SessionFocus.values());
        model.addAttribute("exercises", training.visibleExercises(userId));
        model.addAttribute("today", today);
        model.addAttribute("weekCount", thisWeek.size());
        model.addAttribute("weekTarget", profiles.profile(userId).weeklyTargetOrDefault());
        model.addAttribute("weekVolume", thisWeek.stream().mapToDouble(WorkoutSession::volume).sum());
        model.addAttribute("avgPerWeek", last28.size() / 4.0);
        model.addAttribute("totalSessions", all.size());
        model.addAttribute("volumeChart", charts.write(ChartJson.spec("bar", "labels", weekLabels, "values", weekVolume,
                "unit", "lb", "name", "Weekly volume", "highlight", 11, "title", "Weekly training volume, last 12 weeks")));
        return "train/list";
    }

    @GetMapping("/new")
    public String newSession(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Long repeat,
                             Model model) {
        SessionForm form = repeat == null ? training.blankForm(me.getId()) : training.repeatForm(me.getId(), repeat);
        if (repeat != null) {
            model.addAttribute("repeatOf", training.get(me.getId(), repeat));
        }
        return builder(me.getId(), form, null, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("form") SessionForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return builder(me.getId(), form, null, model, result);
        }
        WorkoutSession saved = training.create(me.getId(), form);
        long prs = records.prsIn(records.summary(me.getId()), saved.getId()).size();
        Flash.success(redirect, prs > 0 ? "Great workout! You set " + prs + (prs == 1 ? " new personal best!" : " new personal bests!") : "Saved! Nice work.");
        return "redirect:/train/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        WorkoutSession session = training.get(me.getId(), id);
        RecordsService.Summary summary = records.summary(me.getId());
        List<RecordsService.PrEvent> prs = records.prsIn(summary, id);
        Set<Long> prExercises = new HashSet<>();
        prs.forEach(p -> prExercises.add(p.exercise().getId()));
        model.addAttribute("s", session);
        model.addAttribute("blocks", TrainingService.blocks(session));
        model.addAttribute("prs", prs);
        model.addAttribute("prExercises", prExercises);
        model.addAttribute("today", todayService.today(me.getId()));
        return "train/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        return builder(me.getId(), training.toForm(training.get(me.getId(), id)), id, model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                         @Valid @ModelAttribute("form") SessionForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return builder(me.getId(), form, id, model, result);
        }
        training.update(me.getId(), id, form);
        Flash.success(redirect, "Changes saved");
        return "redirect:/train/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, RedirectAttributes redirect) {
        training.delete(me.getId(), id);
        Flash.success(redirect, "Workout deleted");
        return "redirect:/train";
    }

    private String builder(Long userId, SessionForm form, Long sessionId, Model model) {
        return builder(userId, form, sessionId, model, null);
    }

    /** With validation errors, each set row gets its own messages so the page can point at the exact field. */
    private String builder(Long userId, SessionForm form, Long sessionId, Model model, BindingResult result) {
        List<Exercise> exercises = training.visibleExercises(userId);
        Map<MuscleGroup, List<Exercise>> grouped = new EnumMap<>(MuscleGroup.class);
        exercises.forEach(e -> grouped.computeIfAbsent(e.getMuscleGroup(), g -> new ArrayList<>()).add(e));

        // Server-render existing blocks (edit / repeat / validation errors) so nothing typed is lost.
        Map<Long, Exercise> byId = new HashMap<>();
        exercises.forEach(e -> byId.put(e.getId(), e));
        List<BlockView> blocks = new ArrayList<>();
        Map<String, BlockView> byKey = new LinkedHashMap<>();
        Map<SessionForm.SetRow, Map<String, String>> rowErrors = new java.util.IdentityHashMap<>();
        List<String> problems = new ArrayList<>();
        for (int idx = 0; idx < form.getSets().size(); idx++) {
            SessionForm.SetRow row = form.getSets().get(idx);
            if (result != null && row != null) {
                for (org.springframework.validation.FieldError error : result.getFieldErrors()) {
                    String prefix = "sets[" + idx + "].";
                    if (error.getField().startsWith(prefix)) {
                        rowErrors.computeIfAbsent(row, r -> new LinkedHashMap<>())
                                .putIfAbsent(error.getField().substring(prefix.length()), error.getDefaultMessage());
                    }
                }
            }
            if (row == null || (row.getExerciseId() == null && (row.getExerciseName() == null || row.getExerciseName().isBlank())
                    && row.getReps() == null && row.getWeightLb() == null)) {
                continue;
            }
            String name = row.getExerciseName() != null && !row.getExerciseName().isBlank() ? row.getExerciseName()
                    : row.getExerciseId() != null && byId.containsKey(row.getExerciseId()) ? byId.get(row.getExerciseId()).getName() : "";
            String key = row.getBlock() == null ? "x" + byKey.size() : String.valueOf(row.getBlock());
            byKey.computeIfAbsent(key, k -> {
                BlockView b = new BlockView(k, name, row.getMuscleGroup(), new ArrayList<>());
                blocks.add(b);
                return b;
            }).rows().add(row);
        }
        for (BlockView b : blocks) {
            for (int n = 0; n < b.rows().size(); n++) {
                Map<String, String> errors = rowErrors.get(b.rows().get(n));
                if (errors != null) {
                    String label = (b.exerciseName().isBlank() ? "Exercise" : b.exerciseName()) + ", set " + (n + 1);
                    errors.values().stream().distinct().forEach(m -> problems.add(label + ": " + m));
                }
            }
        }
        if (result != null) {
            for (org.springframework.validation.FieldError error : result.getFieldErrors()) {
                if (error.getField().startsWith("cardio[")) {
                    int n = Integer.parseInt(error.getField().substring(7, error.getField().indexOf(']'))) + 1;
                    problems.add("Cardio " + n + ": " + error.getDefaultMessage());
                } else if (!error.getField().startsWith("sets[")) {
                    problems.add(error.getDefaultMessage());
                }
            }
            result.getGlobalErrors().forEach(e -> problems.add(e.getDefaultMessage()));
        }
        if (blocks.isEmpty()) {
            List<SessionForm.SetRow> spare = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                spare.add(new SessionForm.SetRow());
            }
            blocks.add(new BlockView("0", "", null, spare));
        }
        form.getCardio().removeIf(java.util.Objects::isNull);
        model.addAttribute("form", form);
        model.addAttribute("blocks", blocks);
        model.addAttribute("rowErrors", rowErrors);
        // Effort and warm-up columns are optional: shown when already used or when they need fixing.
        model.addAttribute("showSetDetails", form.getSets().stream().anyMatch(r -> r != null && (r.getRpe() != null || r.isWarmup()))
                || rowErrors.values().stream().anyMatch(e -> e.containsKey("rpe")));
        model.addAttribute("problems", problems.stream().distinct().toList());
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("grouped", grouped);
        model.addAttribute("muscles", MuscleGroup.values());
        model.addAttribute("exerciseCount", exercises);
        model.addAttribute("focuses", SessionFocus.values());
        model.addAttribute("locations", LocationType.values());
        model.addAttribute("activities", CardioActivity.values());
        model.addAttribute("limitations", limitations.current(userId));
        model.addAttribute("lastTimes", charts.write(training.lastPerformance(userId, fmt)));
        return "train/form";
    }

    public record BlockView(String key, String exerciseName, MuscleGroup muscleGroup, List<SessionForm.SetRow> rows) {
    }
}
