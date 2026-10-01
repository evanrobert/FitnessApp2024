package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.Sex;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Strength levels (Beginner to Elite) for the main barbell lifts.
 *
 * Each level is a multiple of body weight for the estimated one-rep max, in line
 * with widely published adult strength standards. They are a rough, motivating
 * guide: not an official national ranking, and not adjusted for age.
 */
public final class StrengthStandards {
    public static final List<String> LEVELS = List.of("Beginner", "Novice", "Intermediate", "Advanced", "Elite");

    /** Body-weight multiples for Beginner, Novice, Intermediate, Advanced, Elite. */
    private record Ratios(double[] male, double[] female) {
    }

    private static final Map<String, Ratios> LIFTS = Map.of(
            "bench press", new Ratios(new double[]{0.50, 0.75, 1.00, 1.50, 2.00}, new double[]{0.25, 0.50, 0.75, 1.00, 1.50}),
            "back squat", new Ratios(new double[]{0.75, 1.25, 1.50, 2.25, 2.75}, new double[]{0.50, 0.75, 1.25, 1.50, 2.00}),
            "deadlift", new Ratios(new double[]{1.00, 1.50, 2.00, 2.50, 3.00}, new double[]{0.50, 1.00, 1.25, 1.75, 2.50}),
            "overhead press", new Ratios(new double[]{0.35, 0.55, 0.80, 1.05, 1.35}, new double[]{0.20, 0.35, 0.50, 0.75, 1.00}),
            "barbell row", new Ratios(new double[]{0.50, 0.75, 1.00, 1.50, 1.75}, new double[]{0.25, 0.40, 0.65, 0.90, 1.20}),
            "front squat", new Ratios(new double[]{0.55, 0.85, 1.25, 1.75, 2.25}, new double[]{0.35, 0.60, 0.90, 1.25, 1.60}),
            "incline bench press", new Ratios(new double[]{0.40, 0.65, 0.85, 1.20, 1.55}, new double[]{0.20, 0.40, 0.60, 0.85, 1.20}));

    private StrengthStandards() {
    }

    /** One rung of the ladder, with the weight it takes at the member's body weight. */
    public record Rung(String name, double weightLb, boolean reached) {
    }

    /**
     * Where a lift stands. {@code name} is null below Beginner. {@code next} is null at Elite.
     * {@code pctToNext} is progress from the current level's weight to the next one.
     */
    public record Level(String name, int rank, String next, Double nextWeightLb, double lbToNext, double pctToNext,
                        List<Rung> ladder) {
        public String label() {
            return name == null ? "Getting started" : name;
        }

        /** Badge style for this level: each rung has its own color. */
        public String css() {
            return "level-" + rank;
        }
    }

    public static boolean covers(String exerciseName) {
        return exerciseName != null && LIFTS.containsKey(exerciseName.trim().toLowerCase(Locale.ROOT));
    }

    /** Empty when the lift has no standard, or sex or body weight aren't known. */
    public static Optional<Level> level(String exerciseName, Sex sex, Double bodyWeightLb, double estimatedMaxLb) {
        if (!covers(exerciseName) || bodyWeightLb == null || bodyWeightLb <= 0 || estimatedMaxLb <= 0
                || (sex != Sex.MALE && sex != Sex.FEMALE)) {
            return Optional.empty();
        }
        Ratios ratios = LIFTS.get(exerciseName.trim().toLowerCase(Locale.ROOT));
        double[] r = sex == Sex.FEMALE ? ratios.female() : ratios.male();
        int rank = 0;
        Rung[] ladder = new Rung[r.length];
        for (int i = 0; i < r.length; i++) {
            double weight = round5(r[i] * bodyWeightLb);
            boolean reached = estimatedMaxLb >= weight;
            if (reached) {
                rank = i + 1;
            }
            ladder[i] = new Rung(LEVELS.get(i), weight, reached);
        }
        String name = rank == 0 ? null : LEVELS.get(rank - 1);
        if (rank == r.length) {
            return Optional.of(new Level(name, rank, null, null, 0, 100, List.of(ladder)));
        }
        double from = rank == 0 ? 0 : ladder[rank - 1].weightLb();
        double to = ladder[rank].weightLb();
        double pct = Math.max(0, Math.min(100, (estimatedMaxLb - from) / (to - from) * 100));
        return Optional.of(new Level(name, rank, LEVELS.get(rank), to, Math.max(0, to - estimatedMaxLb), pct, List.of(ladder)));
    }

    /** Weights rounded to the nearest 5 lb, as plates are loaded. */
    private static double round5(double lb) {
        return Math.round(lb / 5.0) * 5.0;
    }
}
