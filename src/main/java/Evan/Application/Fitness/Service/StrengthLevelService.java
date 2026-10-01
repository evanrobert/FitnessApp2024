package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.BodyMeasurement;
import Evan.Application.Fitness.Model.Sex;
import Evan.Application.Fitness.Model.UserInformation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strength levels for a member's lifts, from their best estimated max, sex and latest weight. */
@Service
public class StrengthLevelService {
    private final RecordsService records;
    private final ProfileService profiles;
    private final BodyService body;

    public StrengthLevelService(RecordsService records, ProfileService profiles, BodyService body) {
        this.records = records;
        this.profiles = profiles;
        this.body = body;
    }

    /** What's needed before levels can be shown, or null when everything is there. */
    public record Setup(Sex sex, Double bodyWeightLb) {
        public boolean ready() {
            return (sex == Sex.MALE || sex == Sex.FEMALE) && bodyWeightLb != null;
        }

        public String missing() {
            boolean noSex = sex != Sex.MALE && sex != Sex.FEMALE;
            if (noSex && bodyWeightLb == null) {
                return "Add your sex in Profile and today's weight to see your strength level.";
            }
            if (noSex) {
                return "Add your sex in Profile to see your strength level. Strength standards differ for men and women.";
            }
            return bodyWeightLb == null ? "Add your weight to see your strength level." : null;
        }
    }

    @Transactional(readOnly = true)
    public Setup setup(Long userId) {
        UserInformation profile = profiles.profile(userId);
        Double weight = body.latestWeight(userId).map(BodyMeasurement::getWeightLb).orElse(null);
        return new Setup(profile == null ? null : profile.getSex(), weight);
    }

    /** Level per exercise id, only for lifts that have a standard. */
    @Transactional(readOnly = true)
    public Map<Long, StrengthStandards.Level> levels(Long userId) {
        return levels(setup(userId), records.summary(userId));
    }

    /** One lift's level, for lists. */
    public record LiftLevel(Long exerciseId, String name, StrengthStandards.Level level) {
    }

    /** Lifts with a level, strongest level first, then closest to the next one. */
    @Transactional(readOnly = true)
    public java.util.List<LiftLevel> ranked(Long userId) {
        Setup setup = setup(userId);
        RecordsService.Summary summary = records.summary(userId);
        Map<Long, StrengthStandards.Level> levels = levels(setup, summary);
        return summary.records().stream().filter(r -> levels.containsKey(r.exercise().getId()))
                .map(r -> new LiftLevel(r.exercise().getId(), r.exercise().getName(), levels.get(r.exercise().getId())))
                .sorted(java.util.Comparator.comparingInt((LiftLevel l) -> l.level().rank()).reversed()
                        .thenComparing(java.util.Comparator.comparingDouble((LiftLevel l) -> l.level().pctToNext()).reversed()))
                .toList();
    }

    /** True when the member has logged a lift that has levels but is missing sex or weight. */
    @Transactional(readOnly = true)
    public boolean needsSetupFor(Long userId) {
        return !setup(userId).ready() && records.summary(userId).records().stream()
                .anyMatch(r -> StrengthStandards.covers(r.exercise().getName()) && r.bestE1rm() > 0);
    }

    public Map<Long, StrengthStandards.Level> levels(Setup setup, RecordsService.Summary summary) {
        Map<Long, StrengthStandards.Level> out = new LinkedHashMap<>();
        for (RecordsService.ExerciseRecord r : summary.records()) {
            StrengthStandards.level(r.exercise().getName(), setup.sex(), setup.bodyWeightLb(), r.bestE1rm())
                    .ifPresent(level -> out.put(r.exercise().getId(), level));
        }
        return out;
    }
}
