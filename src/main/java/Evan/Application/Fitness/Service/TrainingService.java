package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Form.SessionForm;
import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.ExerciseRepository;
import Evan.Application.Fitness.Repositorys.ExerciseSetRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Repositorys.WorkoutSessionRepository;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrainingService {
    private final WorkoutSessionRepository sessions;
    private final ExerciseRepository exercises;
    private final ExerciseSetRepository sets;
    private final UserLoginDetailsRepository users;
    private final TodayService todayService;

    public TrainingService(WorkoutSessionRepository sessions, ExerciseRepository exercises, ExerciseSetRepository sets,
                           UserLoginDetailsRepository users, TodayService todayService) {
        this.sessions = sessions;
        this.exercises = exercises;
        this.sets = sets;
        this.users = users;
        this.todayService = todayService;
    }

    // ---- Sessions ---------------------------------------------------------

    public List<WorkoutSession> history(Long userId) {
        return sessions.findAllByUserIdOrderBySessionDateDescIdDesc(userId);
    }

    public List<WorkoutSession> between(Long userId, LocalDate from, LocalDate to) {
        return sessions.findAllByUserIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(userId, from, to);
    }

    public WorkoutSession get(Long userId, Long id) {
        return sessions.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Session"));
    }

    /** Filtered history for the training log (member-scale data, filtered in memory). */
    public List<WorkoutSession> search(Long userId, Filter filter) {
        return history(userId).stream()
                .filter(s -> filter.from() == null || !s.getSessionDate().isBefore(filter.from()))
                .filter(s -> filter.to() == null || !s.getSessionDate().isAfter(filter.to()))
                .filter(s -> filter.focus() == null || filter.focus() == s.getFocus())
                .filter(s -> filter.exerciseId() == null
                        || s.getSets().stream().anyMatch(st -> st.getExercise().getId().equals(filter.exerciseId())))
                .filter(s -> filter.q() == null || filter.q().isBlank() || matches(s, filter.q()))
                .toList();
    }

    private static boolean matches(WorkoutSession s, String q) {
        String needle = q.toLowerCase(Locale.ROOT).trim();
        return contains(s.displayTitle(), needle) || contains(s.getNotes(), needle)
                || s.getSets().stream().anyMatch(st -> contains(st.getExercise().getName(), needle));
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    public SessionForm blankForm(Long userId) {
        SessionForm form = new SessionForm();
        form.setSessionDate(todayService.today(userId));
        form.setLocationType(LocationType.GYM);
        return form;
    }

    public SessionForm toForm(WorkoutSession s) {
        SessionForm form = new SessionForm();
        form.setSessionDate(s.getSessionDate());
        form.setTitle(s.getTitle());
        form.setFocus(s.getFocus());
        form.setLocationType(s.getLocationType());
        form.setDurationMin(s.getDurationMin());
        form.setSessionRpe(s.getSessionRpe());
        form.setNotes(s.getNotes());
        for (ExerciseSet st : s.getSets()) {
            SessionForm.SetRow row = new SessionForm.SetRow();
            row.setExerciseId(st.getExercise().getId());
            row.setExerciseName(st.getExercise().getName());
            row.setBlock(st.getSortOrder());
            row.setReps(st.getReps());
            row.setWeightLb(st.getWeightLb());
            row.setRpe(st.getRpe());
            row.setWarmup(st.isWarmup());
            row.setNotes(st.getNotes());
            form.getSets().add(row);
        }
        for (CardioEntry c : s.getCardio()) {
            SessionForm.CardioRow row = new SessionForm.CardioRow();
            row.setActivity(c.getActivity());
            row.setDurationMin(c.getDurationMin());
            row.setDistanceMi(c.getDistanceMi());
            row.setAvgHr(c.getAvgHr());
            row.setCalories(c.getCalories());
            row.setNotes(c.getNotes());
            form.getCardio().add(row);
        }
        return form;
    }

    /** "Repeat" a past session: same exercises and loads, dated today, nothing saved yet. */
    /**
     * The workout to suggest next. Members who rotate named workouts (Upper A, Lower A...) get the
     * one they did longest ago among those done at least twice in the last 8 weeks; everyone else
     * gets their latest strength workout. {@code others} are the remaining routine workouts.
     */
    public record NextUp(WorkoutSession session, boolean rotation, List<WorkoutSession> others) {
    }

    @Transactional(readOnly = true)
    public Optional<NextUp> nextUp(Long userId) {
        LocalDate today = todayService.today(userId);
        List<WorkoutSession> strength = history(userId).stream()
                .filter(s -> !s.getSets().isEmpty() && !s.getSessionDate().isAfter(today)).toList();
        if (strength.isEmpty()) {
            return Optional.empty();
        }
        LocalDate since = today.minusWeeks(8);
        Map<String, WorkoutSession> latestByTitle = new LinkedHashMap<>(); // most recently done first
        Map<String, Integer> times = new HashMap<>();
        for (WorkoutSession s : strength) {
            if (s.getSessionDate().isBefore(since)) {
                break;
            }
            String key = s.displayTitle().trim().toLowerCase(Locale.ROOT);
            latestByTitle.putIfAbsent(key, s);
            times.merge(key, 1, Integer::sum);
        }
        List<WorkoutSession> routine = latestByTitle.entrySet().stream()
                .filter(e -> times.get(e.getKey()) >= 2).map(Map.Entry::getValue).toList();
        if (routine.size() >= 2) {
            WorkoutSession next = routine.get(routine.size() - 1);
            List<WorkoutSession> others = new ArrayList<>(routine.subList(0, routine.size() - 1));
            Collections.reverse(others); // next-most-due first
            return Optional.of(new NextUp(next, true, others.stream().limit(3).toList()));
        }
        return Optional.of(new NextUp(strength.get(0), false, List.of()));
    }

    public SessionForm repeatForm(Long userId, Long sessionId) {
        SessionForm form = toForm(get(userId, sessionId));
        form.setSessionDate(todayService.today(userId));
        form.setDurationMin(null);
        form.setSessionRpe(null);
        form.setNotes(null);
        return form;
    }

    @Transactional
    public WorkoutSession create(Long userId, SessionForm form) {
        WorkoutSession session = new WorkoutSession();
        session.setUser(users.getReferenceById(userId));
        apply(userId, session, form);
        return sessions.save(session);
    }

    @Transactional
    public WorkoutSession update(Long userId, Long id, SessionForm form) {
        WorkoutSession session = get(userId, id);
        apply(userId, session, form);
        return session;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        sessions.delete(get(userId, id));
    }

    private void apply(Long userId, WorkoutSession session, SessionForm form) {
        session.setSessionDate(form.getSessionDate());
        session.setTitle(ProfileService.blankToNull(form.getTitle()));
        session.setFocus(form.getFocus());
        session.setLocationType(form.getLocationType());
        session.setDurationMin(form.getDurationMin());
        session.setSessionRpe(form.getSessionRpe());
        session.setNotes(ProfileService.blankToNull(form.getNotes()));

        session.getSets().clear();
        session.getCardio().clear();

        // Blocks keep the order the member entered; sets are numbered within each block.
        Map<Long, Exercise> visible = new HashMap<>();
        Map<String, Exercise> typed = new HashMap<>();
        Map<Integer, Integer> blockOrder = new LinkedHashMap<>();
        Map<Integer, Integer> setCounters = new HashMap<>();
        int nextBlock = 0;
        for (SessionForm.SetRow row : form.getSets()) {
            if (row == null || row.isBlank()) {
                continue;
            }
            Exercise exercise = resolveExercise(userId, row, visible, typed);
            int blockKey = row.getBlock() == null ? -1 - blockOrder.size() : row.getBlock();
            Integer order = blockOrder.get(blockKey);
            if (order == null) {
                order = nextBlock++;
                blockOrder.put(blockKey, order);
            }
            int setNumber = setCounters.merge(order, 1, Integer::sum);

            ExerciseSet set = new ExerciseSet();
            set.setExercise(exercise);
            set.setSortOrder(order);
            set.setSetNumber(setNumber);
            set.setReps(row.getReps());
            set.setWeightLb(row.getWeightLb());
            set.setRpe(row.getRpe());
            set.setWarmup(row.isWarmup());
            set.setNotes(ProfileService.blankToNull(row.getNotes()));
            session.addSet(set);
        }
        for (SessionForm.CardioRow row : form.getCardio()) {
            if (row == null || row.isBlank()) {
                continue;
            }
            CardioEntry entry = new CardioEntry();
            entry.setActivity(row.getActivity());
            entry.setDurationMin(row.getDurationMin());
            entry.setDistanceMi(row.getDistanceMi());
            entry.setAvgHr(row.getAvgHr());
            entry.setCalories(row.getCalories());
            entry.setNotes(ProfileService.blankToNull(row.getNotes()));
            session.addCardio(entry);
        }
    }

    /**
     * A set names its exercise by id (picked from the list) or by free-form name.
     * Typed names reuse a matching library or personal exercise; anything new is
     * added to the member's library once, however many sets use it.
     */
    private Exercise resolveExercise(Long userId, SessionForm.SetRow row, Map<Long, Exercise> byId, Map<String, Exercise> byName) {
        if (row.getExerciseId() != null) {
            return byId.computeIfAbsent(row.getExerciseId(), id -> exercises.findVisible(id, userId)
                    .orElseThrow(() -> new NotFoundException("Exercise")));
        }
        String name = row.getExerciseName().trim().replaceAll("\\s+", " ");
        return byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> exercises.findVisibleByName(name, userId).stream()
                .findFirst()
                .orElseGet(() -> {
                    Exercise created = new Exercise();
                    created.setName(name);
                    created.setMuscleGroup(row.getMuscleGroup() == null ? MuscleGroup.OTHER : row.getMuscleGroup());
                    created.setOwner(users.getReferenceById(userId));
                    return exercises.save(created);
                }));
    }

    /** Exercise blocks of a session in display order. */
    public static List<Block> blocks(WorkoutSession session) {
        Map<Integer, Block> byOrder = new TreeMap<>();
        for (ExerciseSet st : session.getSets()) {
            byOrder.computeIfAbsent(st.getSortOrder(), o -> new Block(st.getExercise(), new ArrayList<>())).sets().add(st);
        }
        return new ArrayList<>(byOrder.values());
    }

    // ---- Exercises ---------------------------------------------------------

    public List<Exercise> visibleExercises(Long userId) {
        return exercises.findVisibleTo(userId);
    }

    public Exercise exercise(Long userId, Long id) {
        return exercises.findVisible(id, userId).orElseThrow(() -> new NotFoundException("Exercise"));
    }

    public boolean exerciseNameTaken(Long userId, String name) {
        return name != null && exercises.nameInUse(name.trim(), userId);
    }

    @Transactional
    public Exercise createExercise(Long userId, Exercise form) {
        Exercise exercise = new Exercise();
        exercise.setName(form.getName().trim());
        exercise.setMuscleGroup(form.getMuscleGroup());
        exercise.setEquipment(form.getEquipment());
        exercise.setOwner(users.getReferenceById(userId));
        return exercises.save(exercise);
    }

    @Transactional
    public void updateExercise(Long userId, Long id, Exercise form) {
        Exercise own = exercises.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("Exercise"));
        own.setName(form.getName().trim());
        own.setMuscleGroup(form.getMuscleGroup());
        own.setEquipment(form.getEquipment());
    }

    /** Only unused personal exercises can be deleted (history is never orphaned). */
    @Transactional
    public boolean deleteExercise(Long userId, Long id) {
        Exercise own = exercises.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("Exercise"));
        if (sets.exerciseInUse(id)) {
            return false;
        }
        exercises.delete(own);
        return true;
    }

    /**
     * "Last time" hints for the set builder: the most recent working sets per
     * exercise, e.g. "Sep 24 · 295 × 3, 295 × 3".
     */
    @Transactional(readOnly = true)
    public Map<String, String> lastPerformance(Long userId, Fmt fmt) {
        Map<Long, List<ExerciseSet>> lastSets = new HashMap<>();
        Map<Long, Long> lastSession = new HashMap<>();
        for (ExerciseSet st : sets.historyFor(userId)) {
            Long exerciseId = st.getExercise().getId();
            Long sessionId = st.getSession().getId();
            if (!sessionId.equals(lastSession.get(exerciseId))) {
                lastSession.put(exerciseId, sessionId);
                lastSets.put(exerciseId, new ArrayList<>());
            }
            if (!st.isWarmup()) {
                lastSets.get(exerciseId).add(st);
            }
        }
        Map<String, String> hints = new HashMap<>();
        lastSets.forEach((exerciseId, list) -> {
            if (!list.isEmpty()) {
                String key = list.get(0).getExercise().getName().toLowerCase(Locale.ROOT);
                String setsText = list.stream().limit(6)
                        .map(st -> (st.getWeightLb() == null ? "BW" : fmt.dec(st.getWeightLb())) + " × " + (st.getReps() == null ? "?" : st.getReps()))
                        .collect(Collectors.joining(", "));
                hints.put(key, fmt.shortDate(list.get(0).getSession().getSessionDate()) + " · " + setsText);
            }
        });
        return hints;
    }

    public record Filter(LocalDate from, LocalDate to, SessionFocus focus, Long exerciseId, String q) {
        public boolean isActive() {
            return from != null || to != null || focus != null || exerciseId != null || (q != null && !q.isBlank());
        }
    }

    public record Block(Exercise exercise, List<ExerciseSet> sets) {
        public double volume() {
            return sets.stream().filter(s -> !s.isWarmup()).mapToDouble(ExerciseSet::volume).sum();
        }

        public double bestE1rm() {
            return sets.stream().filter(s -> !s.isWarmup()).mapToDouble(ExerciseSet::estimatedOneRepMax).max().orElse(0);
        }
    }
}
