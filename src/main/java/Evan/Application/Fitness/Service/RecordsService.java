package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.Exercise;
import Evan.Application.Fitness.Model.ExerciseSet;
import Evan.Application.Fitness.Model.WorkoutSession;
import Evan.Application.Fitness.Repositorys.ExerciseSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Personal records are derived from set history rather than stored, so editing
 * or deleting a session always leaves records consistent.
 * A PR is logged when a session beats every earlier session for that exercise
 * on heaviest load or estimated 1RM (the first session sets the baseline).
 */
@Service
public class RecordsService {
    private final ExerciseSetRepository sets;

    public RecordsService(ExerciseSetRepository sets) {
        this.sets = sets;
    }

    @Transactional(readOnly = true)
    public Summary summary(Long userId) {
        Map<Long, Builder> byExercise = new LinkedHashMap<>();
        List<PrEvent> events = new ArrayList<>();

        for (SessionExercise group : groups(sets.historyFor(userId))) {
            Builder b = byExercise.computeIfAbsent(group.exercise().getId(), id -> new Builder(group.exercise()));
            b.sessions++;
            b.sets += group.workingSets();
            b.lastDone = group.date();
            b.totalVolume += group.volume();
            if (group.topWeight() > 0) {
                if (b.bestWeight > 0 && group.topWeight() > b.bestWeight) {
                    events.add(new PrEvent(group.date(), group.sessionId(), group.exercise(), PrKind.HEAVIEST,
                            group.topWeight(), b.bestWeight, group.topWeightReps()));
                }
                if (group.topWeight() > b.bestWeight) {
                    b.bestWeight = group.topWeight();
                    b.bestWeightReps = group.topWeightReps();
                    b.bestWeightOn = group.date();
                }
            }
            if (group.topE1rm() > 0) {
                if (b.bestE1rm > 0 && group.topE1rm() > b.bestE1rm + 0.05) {
                    events.add(new PrEvent(group.date(), group.sessionId(), group.exercise(), PrKind.E1RM,
                            group.topE1rm(), b.bestE1rm, null));
                }
                if (group.topE1rm() > b.bestE1rm) {
                    b.bestE1rm = group.topE1rm();
                    b.bestE1rmOn = group.date();
                }
            }
            b.bestSetVolume = Math.max(b.bestSetVolume, group.bestSetVolume());
        }

        List<ExerciseRecord> records = byExercise.values().stream().map(Builder::build)
                .sorted(Comparator.comparing(ExerciseRecord::lastDone).reversed()).toList();
        events.sort(Comparator.comparing(PrEvent::date).reversed());
        return new Summary(records, events);
    }

    /** Per-session series for one exercise: top estimated 1RM, top load and volume. */
    @Transactional(readOnly = true)
    public List<SessionPoint> series(Long userId, Long exerciseId) {
        return groups(sets.historyFor(userId, exerciseId)).stream()
                .map(g -> new SessionPoint(g.date(), g.sessionId(), g.topE1rm(), g.topWeight(), g.volume(), g.workingSets(),
                        g.sets()))
                .toList();
    }

    public List<PrEvent> prsIn(Summary summary, Long sessionId) {
        return summary.events().stream().filter(e -> e.sessionId().equals(sessionId)).toList();
    }

    private static List<SessionExercise> groups(List<ExerciseSet> chronological) {
        List<SessionExercise> out = new ArrayList<>();
        Map<String, SessionExercise> index = new HashMap<>();
        for (ExerciseSet st : chronological) {
            WorkoutSession s = st.getSession();
            String key = s.getId() + ":" + st.getExercise().getId();
            SessionExercise group = index.get(key);
            if (group == null) {
                group = new SessionExercise(s.getId(), s.getSessionDate(), st.getExercise(), new ArrayList<>());
                index.put(key, group);
                out.add(group);
            }
            group.sets().add(st);
        }
        return out;
    }

    record SessionExercise(Long sessionId, LocalDate date, Exercise exercise, List<ExerciseSet> sets) {
        List<ExerciseSet> working() {
            return sets.stream().filter(s -> !s.isWarmup()).toList();
        }

        int workingSets() {
            return working().size();
        }

        double topWeight() {
            return working().stream().filter(s -> s.getReps() != null && s.getReps() > 0 && s.getWeightLb() != null)
                    .mapToDouble(ExerciseSet::getWeightLb).max().orElse(0);
        }

        Integer topWeightReps() {
            double top = topWeight();
            return working().stream().filter(s -> s.getWeightLb() != null && s.getWeightLb() == top && s.getReps() != null)
                    .map(ExerciseSet::getReps).max(Integer::compare).orElse(null);
        }

        double topE1rm() {
            return working().stream().mapToDouble(ExerciseSet::estimatedOneRepMax).max().orElse(0);
        }

        double volume() {
            return working().stream().mapToDouble(ExerciseSet::volume).sum();
        }

        double bestSetVolume() {
            return working().stream().mapToDouble(ExerciseSet::volume).max().orElse(0);
        }
    }

    private static final class Builder {
        final Exercise exercise;
        double bestWeight;
        Integer bestWeightReps;
        LocalDate bestWeightOn;
        double bestE1rm;
        LocalDate bestE1rmOn;
        double bestSetVolume;
        double totalVolume;
        int sessions;
        int sets;
        LocalDate lastDone;

        Builder(Exercise exercise) {
            this.exercise = exercise;
        }

        ExerciseRecord build() {
            return new ExerciseRecord(exercise, bestWeight, bestWeightReps, bestWeightOn, bestE1rm, bestE1rmOn,
                    bestSetVolume, totalVolume, sessions, sets, lastDone);
        }
    }

    public enum PrKind {
        HEAVIEST("Heaviest set"), E1RM("Estimated 1RM");

        private final String label;

        PrKind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Summary(List<ExerciseRecord> records, List<PrEvent> events) {
        public Optional<ExerciseRecord> forExercise(Long exerciseId) {
            return records.stream().filter(r -> r.exercise().getId().equals(exerciseId)).findFirst();
        }

        public long prsSince(LocalDate from) {
            return events.stream().filter(e -> !e.date().isBefore(from)).count();
        }
    }

    public record ExerciseRecord(Exercise exercise, double bestWeight, Integer bestWeightReps, LocalDate bestWeightOn,
                                 double bestE1rm, LocalDate bestE1rmOn, double bestSetVolume, double totalVolume,
                                 int sessions, int sets, LocalDate lastDone) {
    }

    public record PrEvent(LocalDate date, Long sessionId, Exercise exercise, PrKind kind, double value,
                          double previous, Integer reps) {
        public double gain() {
            return value - previous;
        }
    }

    public record SessionPoint(LocalDate date, Long sessionId, double topE1rm, double topWeight, double volume,
                               int workingSets, List<ExerciseSet> sets) {
    }
}
