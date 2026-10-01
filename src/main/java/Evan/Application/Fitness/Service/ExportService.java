package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Member data export: one CSV per dataset, always scoped to the requesting member. */
@Service
public class ExportService {
    private final NutritionService nutrition;
    private final TrainingService training;
    private final BodyService body;
    private final CheckInService checkIns;
    private final MetricService metrics;
    private final GoalService goals;

    public ExportService(NutritionService nutrition, TrainingService training, BodyService body,
                         CheckInService checkIns, MetricService metrics, GoalService goals) {
        this.nutrition = nutrition;
        this.training = training;
        this.body = body;
        this.checkIns = checkIns;
        this.metrics = metrics;
        this.goals = goals;
    }

    public static final Map<String, String> DATASETS = Map.of(
            "nutrition", "Meals & foods",
            "sets", "Strength sets",
            "cardio", "Cardio",
            "sessions", "Training sessions",
            "body", "Body measurements",
            "checkins", "Daily check-ins",
            "metrics", "Custom metric entries",
            "goals", "Goals");

    public record Table(List<String> header, List<List<Object>> rows) {
    }

    @Transactional(readOnly = true)
    public Table table(Long userId, String dataset) {
        return switch (dataset) {
            case "nutrition" -> table(nutrition.entriesFor(userId),
                    List.of("date", "meal_type", "item", "calories", "protein_g", "carbs_g", "fat_g", "fiber_g", "sugars_g", "sodium_mg", "cholesterol_mg"),
                    c -> List.of(str(c.getDate()), str(c.getMealType()), str(c.getItemName()), c.getCalories(), c.getProteins(),
                            c.getCarbohydrates(), c.getFats(), c.getFiber(), c.getSugars(), c.getSodium(), c.getCholesterol()));
            case "sessions" -> table(training.history(userId),
                    List.of("date", "title", "focus", "location", "duration_min", "session_rate_of_perceived_exertion", "working_sets", "volume_lb", "cardio_min", "notes"),
                    s -> List.of(str(s.getSessionDate()), str(s.getTitle()), str(s.getFocus()), str(s.getLocationType()),
                            str(s.getDurationMin()), str(s.getSessionRpe()), s.workingSetCount(), s.volume(), s.cardioMinutes(), str(s.getNotes())));
            case "sets" -> {
                List<List<Object>> rows = new ArrayList<>();
                for (WorkoutSession s : training.history(userId)) {
                    for (ExerciseSet st : s.getSets()) {
                        rows.add(List.of(str(s.getSessionDate()), st.getExercise().getName(), st.getExercise().getMuscleGroup().name(),
                                st.getSetNumber(), str(st.getReps()), str(st.getWeightLb()), str(st.getRpe()), st.isWarmup(),
                                Math.round(st.estimatedOneRepMax() * 10) / 10.0, str(st.getNotes())));
                    }
                }
                yield new Table(List.of("date", "exercise", "muscle_group", "set", "reps", "weight_lb", "rate_of_perceived_exertion", "warmup", "est_1rm_lb", "notes"), rows);
            }
            case "cardio" -> {
                List<List<Object>> rows = new ArrayList<>();
                for (WorkoutSession s : training.history(userId)) {
                    for (CardioEntry c : s.getCardio()) {
                        rows.add(List.of(str(s.getSessionDate()), c.getActivity().name(), c.getDurationMin(), str(c.getDistanceMi()),
                                str(c.getAvgHr()), str(c.getCalories()), str(c.getNotes())));
                    }
                }
                yield new Table(List.of("date", "activity", "duration_min", "distance_mi", "avg_hr", "calories", "notes"), rows);
            }
            case "body" -> table(body.history(userId),
                    List.of("date", "weight_lb", "body_fat_pct", "waist_in", "hips_in", "chest_in", "arm_in", "thigh_in", "neck_in", "notes"),
                    m -> List.of(str(m.getMeasuredOn()), str(m.getWeightLb()), str(m.getBodyFatPct()), str(m.getWaistIn()), str(m.getHipsIn()),
                            str(m.getChestIn()), str(m.getArmIn()), str(m.getThighIn()), str(m.getNeckIn()), str(m.getNotes())));
            case "checkins" -> table(checkIns.history(userId),
                    List.of("date", "sleep_hours", "sleep_quality", "energy", "mood", "stress", "soreness", "water_oz", "steps", "resting_hr", "readiness", "notes"),
                    c -> List.of(str(c.getCheckInDate()), str(c.getSleepHours()), str(c.getSleepQuality()), str(c.getEnergy()), str(c.getMood()),
                            str(c.getStress()), str(c.getSoreness()), str(c.getWaterOz()), str(c.getSteps()), str(c.getRestingHr()),
                            str(c.readinessScore()), str(c.getNotes())));
            case "metrics" -> table(metrics.allEntries(userId),
                    List.of("date", "metric", "value", "unit", "notes"),
                    e -> List.of(str(e.getRecordedOn()), e.getMetric().getName(), e.getValue(), str(e.getMetric().getUnit()), str(e.getNotes())));
            case "goals" -> table(goals.progressFor(userId),
                    List.of("title", "metric", "status", "start_value", "current_value", "target_value", "progress_pct", "start_date", "target_date", "achieved_on"),
                    p -> List.of(p.goal().getTitle(), p.goal().getMetric().name(), p.goal().getStatus().name(), str(p.start()),
                            str(p.current()), p.goal().getTargetValue(), Math.round(p.pct()), str(p.goal().getStartDate()),
                            str(p.goal().getTargetDate()), str(p.goal().getAchievedOn())));
            default -> null;
        };
    }

    private static <T> Table table(List<T> items, List<String> header, Function<T, List<Object>> row) {
        return new Table(header, items.stream().map(row).toList());
    }

    private static Object str(Object value) {
        return value == null ? "" : value.toString();
    }
}
