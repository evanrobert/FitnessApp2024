package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CustomMetric;
import Evan.Application.Fitness.Model.CustomMetricEntry;
import Evan.Application.Fitness.Model.MetricCategory;
import Evan.Application.Fitness.Repositorys.CustomMetricEntryRepository;
import Evan.Application.Fitness.Repositorys.CustomMetricRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Member-defined tracking. Covers fitness assessments, habits and anything numeric the app doesn't model. */
@Service
public class MetricService {
    /** One-click starting points; members can define anything else. */
    public static final List<Template> TEMPLATES = List.of(
            new Template("Push-ups (max reps)", "reps", MetricCategory.ASSESSMENT, true, "Unbroken push-ups, good form"),
            new Template("1-mile run", "min", MetricCategory.ASSESSMENT, false, "Timed mile, minutes"),
            new Template("Plank hold", "sec", MetricCategory.ASSESSMENT, true, "Max hold in seconds"),
            new Template("Vertical jump", "in", MetricCategory.PERFORMANCE, true, "Standing vertical"),
            new Template("Sit and reach", "in", MetricCategory.ASSESSMENT, true, "Flexibility test"),
            new Template("500 m row", "sec", MetricCategory.PERFORMANCE, false, "Erg time trial"),
            new Template("Grip strength", "lb", MetricCategory.ASSESSMENT, true, "Dynamometer, best hand"),
            new Template("Meditation", "min", MetricCategory.HABIT, true, "Minutes per day"));

    private final CustomMetricRepository metrics;
    private final CustomMetricEntryRepository entries;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public MetricService(CustomMetricRepository metrics, CustomMetricEntryRepository entries,
                         UserLoginDetailsRepository users, TodayService todayService) {
        this.metrics = metrics;
        this.entries = entries;
        this.users = users;
        this.todayService = todayService;
    }

    @Transactional(readOnly = true)
    public List<Summary> summaries(Long userId) {
        return metrics.findAllByUserIdOrderByArchivedAscNameAsc(userId).stream().map(this::summarize).toList();
    }

    public List<CustomMetric> active(Long userId) {
        return metrics.findAllByUserIdOrderByArchivedAscNameAsc(userId).stream().filter(m -> !m.isArchived()).toList();
    }

    public CustomMetric get(Long userId, Long id) {
        return metrics.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Metric"));
    }

    public List<CustomMetricEntry> entries(Long metricId) {
        return entries.findAllByMetricIdOrderByRecordedOnAscIdAsc(metricId);
    }

    public List<CustomMetricEntry> allEntries(Long userId) {
        return entries.allFor(userId);
    }

    public boolean nameTaken(Long userId, String name) {
        return name != null && metrics.existsByUserIdAndNameIgnoreCase(userId, name.trim());
    }

    public List<Template> unusedTemplates(Long userId) {
        List<String> existing = metrics.findAllByUserIdOrderByArchivedAscNameAsc(userId).stream()
                .map(m -> m.getName().toLowerCase(Locale.ROOT)).toList();
        return TEMPLATES.stream().filter(t -> !existing.contains(t.name().toLowerCase(Locale.ROOT))).toList();
    }

    @Transactional
    public CustomMetric create(Long userId, CustomMetric form) {
        CustomMetric metric = new CustomMetric();
        metric.setUser(users.getReferenceById(userId));
        copy(form, metric);
        return metrics.save(metric);
    }

    @Transactional
    public void update(Long userId, Long id, CustomMetric form) {
        CustomMetric metric = get(userId, id);
        copy(form, metric);
        metric.setArchived(form.isArchived());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        metrics.delete(get(userId, id));
    }

    @Transactional
    public void addEntry(Long userId, Long metricId, CustomMetricEntry form) {
        CustomMetricEntry entry = new CustomMetricEntry();
        entry.setMetric(get(userId, metricId));
        entry.setRecordedOn(form.getRecordedOn() == null ? todayService.today(userId) : form.getRecordedOn());
        entry.setValue(form.getValue());
        entry.setNotes(ProfileService.blankToNull(form.getNotes()));
        entries.save(entry);
    }

    @Transactional
    public Long deleteEntry(Long userId, Long entryId) {
        CustomMetricEntry entry = entries.findOwned(entryId, userId).orElseThrow(() -> new NotFoundException("Entry"));
        Long metricId = entry.getMetric().getId();
        entries.delete(entry);
        return metricId;
    }

    private static void copy(CustomMetric from, CustomMetric to) {
        to.setName(from.getName().trim());
        to.setUnit(ProfileService.blankToNull(from.getUnit()));
        to.setCategory(from.getCategory() == null ? MetricCategory.OTHER : from.getCategory());
        to.setHigherIsBetter(from.isHigherIsBetter());
        to.setDescription(ProfileService.blankToNull(from.getDescription()));
    }

    private Summary summarize(CustomMetric metric) {
        List<CustomMetricEntry> list = entries.findAllByMetricIdOrderByRecordedOnAscIdAsc(metric.getId());
        if (list.isEmpty()) {
            return new Summary(metric, null, null, null, 0, null);
        }
        CustomMetricEntry latest = list.get(list.size() - 1);
        Comparator<CustomMetricEntry> byValue = Comparator.comparing(CustomMetricEntry::getValue);
        CustomMetricEntry best = metric.isHigherIsBetter() ? list.stream().max(byValue).orElseThrow() : list.stream().min(byValue).orElseThrow();
        Double change = list.size() > 1 ? latest.getValue() - list.get(0).getValue() : null;
        return new Summary(metric, latest.getValue(), latest.getRecordedOn(), best.getValue(), list.size(), change);
    }

    public record Template(String name, String unit, MetricCategory category, boolean higherIsBetter, String description) {
    }

    public record Summary(CustomMetric metric, Double latest, LocalDate latestOn, Double best, int count, Double change) {
        /** Whether the change since the first entry is an improvement for this metric's direction. */
        public Boolean improving() {
            if (change == null || change == 0) {
                return null;
            }
            return metric.isHigherIsBetter() == change > 0;
        }
    }
}
