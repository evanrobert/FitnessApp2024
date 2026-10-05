package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Form.SessionForm;
import Evan.Application.Fitness.Model.Exercise;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a pasted workout into a filled-in workout form. Nothing is saved here: the member
 * sees the normal workout form, checks it and presses Save.
 */
@Service
public class WorkoutImportService {
    private final TrainingService training;

    public WorkoutImportService(TrainingService training) {
        this.training = training;
    }

    public record Import(SessionForm form, int exerciseCount, int setCount, int matchedCount, List<String> skipped) {
    }

    public Import read(Long userId, String text) {
        WorkoutTextParser.Result parsed = WorkoutTextParser.parse(text);
        SessionForm form = training.blankForm(userId);
        form.setTitle(parsed.title());
        LocalDate date = dateFor(parsed.date(), form.getSessionDate());
        if (date != null) {
            form.setSessionDate(date);
        }

        // Library and the member's own exercises, keyed so small spelling differences still match.
        Map<String, Exercise> known = new HashMap<>();
        for (Exercise e : training.visibleExercises(userId)) {
            known.putIfAbsent(WorkoutTextParser.matchKey(e.getName()), e);
        }
        int matched = 0;
        for (int b = 0; b < parsed.blocks().size(); b++) {
            WorkoutTextParser.Block block = parsed.blocks().get(b);
            Exercise existing = known.get(WorkoutTextParser.matchKey(block.name()));
            if (existing != null) {
                matched++;
            }
            for (WorkoutTextParser.SetLine set : block.sets()) {
                SessionForm.SetRow row = new SessionForm.SetRow();
                row.setBlock(b);
                row.setExerciseName(existing != null ? existing.getName() : block.name());
                row.setMuscleGroup(existing != null ? existing.getMuscleGroup() : WorkoutTextParser.guessMuscle(block.name()));
                row.setReps(set.reps());
                row.setWeightLb(set.weightLb());
                form.getSets().add(row);
            }
        }
        return new Import(form, parsed.blocks().size(), parsed.setCount(), matched, parsed.skipped());
    }

    /** A date written in the notes, if it's a real date that isn't in the future. */
    static LocalDate dateFor(WorkoutTextParser.WrittenDate written, LocalDate today) {
        if (written == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.of(written.year() != null ? written.year() : today.getYear(), written.month(), written.day());
            if (written.year() == null && date.isAfter(today)) {
                date = date.minusYears(1);
            }
            return date.isAfter(today) || date.isBefore(today.minusYears(5)) ? null : date;
        } catch (DateTimeException e) {
            return null;
        }
    }
}
