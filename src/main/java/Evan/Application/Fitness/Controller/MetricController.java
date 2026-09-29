package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Model.CustomMetric;
import Evan.Application.Fitness.Model.CustomMetricEntry;
import Evan.Application.Fitness.Model.MetricCategory;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.MetricService;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/metrics")
public class MetricController {
    private final MetricService metrics;
    private final TodayService todayService;
    private final ChartJson charts;

    public MetricController(MetricService metrics, TodayService todayService, ChartJson charts) {
        this.metrics = metrics;
        this.todayService = todayService;
        this.charts = charts;
    }

    @ModelAttribute("categories")
    public MetricCategory[] categories() {
        return MetricCategory.values();
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        if (!model.containsAttribute("metric")) {
            model.addAttribute("metric", new CustomMetric());
        }
        return listView(me.getId(), model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @ModelAttribute("metric") CustomMetric metric,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasFieldErrors("name") && metrics.nameTaken(me.getId(), metric.getName())) {
            result.rejectValue("name", "taken", "You're already tracking something with that name");
        }
        if (result.hasErrors()) {
            return listView(me.getId(), model);
        }
        CustomMetric saved = metrics.create(me.getId(), metric);
        Flash.success(redirect, saved.getName() + " added — log your first value");
        return "redirect:/metrics/" + saved.getId();
    }

    @PostMapping("/template")
    public String fromTemplate(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam String name, RedirectAttributes redirect) {
        MetricService.Template t = MetricService.TEMPLATES.stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
        if (t == null || metrics.nameTaken(me.getId(), t.name())) {
            Flash.error(redirect, "That metric is already on your list");
            return "redirect:/metrics";
        }
        CustomMetric form = new CustomMetric();
        form.setName(t.name());
        form.setUnit(t.unit());
        form.setCategory(t.category());
        form.setHigherIsBetter(t.higherIsBetter());
        form.setDescription(t.description());
        CustomMetric saved = metrics.create(me.getId(), form);
        return "redirect:/metrics/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, Model model) {
        CustomMetric metric = metrics.get(me.getId(), id);
        if (!model.containsAttribute("entry")) {
            CustomMetricEntry entry = new CustomMetricEntry();
            entry.setRecordedOn(todayService.today(me.getId()));
            model.addAttribute("entry", entry);
        }
        return detailView(me.getId(), metric, model);
    }

    @PostMapping("/{id}/entries")
    public String addEntry(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                           @Valid @ModelAttribute("entry") CustomMetricEntry entry, BindingResult result, Model model,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return detailView(me.getId(), metrics.get(me.getId(), id), model);
        }
        metrics.addEntry(me.getId(), id, entry);
        Flash.success(redirect, "Entry logged");
        return "redirect:/metrics/" + id;
    }

    @PostMapping("/entries/{entryId}/delete")
    public String deleteEntry(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long entryId, RedirectAttributes redirect) {
        Long metricId = metrics.deleteEntry(me.getId(), entryId);
        Flash.success(redirect, "Entry deleted");
        return "redirect:/metrics/" + metricId;
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                         @Valid @ModelAttribute("metric") CustomMetric metric, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            Flash.error(redirect, "Give the metric a name");
            return "redirect:/metrics/" + id;
        }
        metrics.update(me.getId(), id, metric);
        Flash.success(redirect, metric.isArchived() ? "Metric archived" : "Metric updated");
        return "redirect:/metrics/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, RedirectAttributes redirect) {
        metrics.delete(me.getId(), id);
        Flash.success(redirect, "Metric and its entries deleted");
        return "redirect:/metrics";
    }

    private String listView(Long userId, Model model) {
        model.addAttribute("summaries", metrics.summaries(userId));
        model.addAttribute("templates", metrics.unusedTemplates(userId));
        model.addAttribute("today", todayService.today(userId));
        return "metrics/list";
    }

    private String detailView(Long userId, CustomMetric metric, Model model) {
        List<CustomMetricEntry> entries = metrics.entries(metric.getId());
        model.addAttribute("metric", metric);
        model.addAttribute("summary", metrics.summaries(userId).stream().filter(s -> s.metric().getId().equals(metric.getId())).findFirst().orElse(null));
        List<CustomMetricEntry> newestFirst = new ArrayList<>(entries);
        Collections.reverse(newestFirst);
        model.addAttribute("entries", newestFirst);
        model.addAttribute("today", todayService.today(userId));
        model.addAttribute("chart", charts.write(ChartJson.spec("line", "labels", entries.stream().map(e -> e.getRecordedOn().toString()).toList(),
                "series", List.of(ChartJson.series(metric.getName(), entries.stream().map(CustomMetricEntry::getValue).toList(), "s1", null)),
                "unit", metric.getUnit(), "title", metric.getName() + " over time",
                "empty", "Log two or more entries to see the trend.")));
        return "metrics/detail";
    }
}
