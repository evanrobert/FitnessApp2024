package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.*;
import Evan.Application.Fitness.Repositorys.BodyMeasurementRepository;
import Evan.Application.Fitness.Repositorys.UserInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Service
public class ProfileService {
    /** Time zones offered in the picker (any valid zone id is accepted). */
    public static final List<String> COMMON_ZONES = List.of(
            "America/New_York", "America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles",
            "America/Anchorage", "Pacific/Honolulu", "America/Toronto", "America/Vancouver", "America/Mexico_City",
            "America/Sao_Paulo", "Europe/London", "Europe/Dublin", "Europe/Paris", "Europe/Berlin", "Europe/Madrid",
            "Africa/Johannesburg", "Asia/Dubai", "Asia/Kolkata", "Asia/Singapore", "Asia/Tokyo", "Australia/Sydney",
            "Pacific/Auckland", "UTC");

    private final UserInformationRepository profiles;
    private final UserLoginDetailsRepository users;
    private final BodyMeasurementRepository measurements;
    private final TodayService todayService;

    public ProfileService(UserInformationRepository profiles, UserLoginDetailsRepository users,
                          BodyMeasurementRepository measurements, TodayService todayService) {
        this.profiles = profiles;
        this.users = users;
        this.measurements = measurements;
        this.todayService = todayService;
    }

    @Transactional
    public UserInformation profile(Long userId) {
        return profiles.findByUserId(userId).orElseGet(() -> {
            UserInformation fresh = new UserInformation();
            fresh.setUser(users.getReferenceById(userId));
            return profiles.save(fresh);
        });
    }

    public boolean onboarded(Long userId) {
        return profiles.findByUserId(userId).map(p -> p.getPrimaryGoal() != null).orElse(false);
    }

    @Transactional
    public void update(Long userId, UserInformation form) {
        UserInformation p = profile(userId);
        p.setName(form.getName() == null ? null : form.getName().trim());
        p.setBirthYear(form.getBirthYear());
        p.setSex(form.getSex());
        p.setHeightIn(form.getHeightIn());
        p.setActivityLevel(form.getActivityLevel());
        p.setPrimaryGoal(form.getPrimaryGoal());
        p.setExperienceLevel(form.getExperienceLevel());
        p.setWeeklyWorkoutTarget(form.getWeeklyWorkoutTarget());
        p.setTimeZone(validZone(form.getTimeZone()));
        p.setBio(blankToNull(form.getBio()));
        p.setGymName(blankToNull(form.getGymName()));
        p.setMembershipPlan(blankToNull(form.getMembershipPlan()));
        p.setMembershipStartedOn(form.getMembershipStartedOn());
        p.setMembershipRenewsOn(form.getMembershipRenewsOn());
        p.setMembershipMonthlyCost(form.getMembershipMonthlyCost());
    }

    public List<String> zoneChoices(String current) {
        TreeSet<String> zones = new TreeSet<>(COMMON_ZONES);
        if (current != null && !current.isBlank()) {
            zones.add(current);
        }
        return new ArrayList<>(zones);
    }

    /**
     * Maintenance-calorie estimate (Mifflin-St Jeor x activity) with goal-adjusted
     * suggested targets. Returns empty with the list of missing inputs otherwise.
     */
    @Transactional(readOnly = true)
    public EnergyEstimate estimate(Long userId) {
        UserInformation p = profiles.findByUserId(userId).orElse(null);
        Double weight = measurements.findFirstByUserIdAndWeightLbNotNullOrderByMeasuredOnDescIdDesc(userId)
                .map(BodyMeasurement::getWeightLb).orElse(null);
        List<String> missing = new ArrayList<>();
        if (weight == null) missing.add("a weigh-in");
        if (p == null || p.getHeightIn() == null) missing.add("height");
        if (p == null || p.getBirthYear() == null) missing.add("birth year");
        if (!missing.isEmpty()) {
            return EnergyEstimate.missing(missing);
        }
        int age = Year.from(todayService.today(userId)).getValue() - p.getBirthYear();
        double kg = weight * 0.453592;
        double cm = p.getHeightIn() * 2.54;
        double sexOffset = p.getSex() == Sex.MALE ? 5 : p.getSex() == Sex.FEMALE ? -161 : -78;
        double bmr = 10 * kg + 6.25 * cm - 5 * age + sexOffset;
        ActivityLevel activity = p.getActivityLevel() == null ? ActivityLevel.MODERATE : p.getActivityLevel();
        double maintenance = bmr * activity.getMultiplier();

        PrimaryGoal goal = p.getPrimaryGoal() == null ? PrimaryGoal.MAINTAIN : p.getPrimaryGoal();
        double factor = switch (goal) {
            case LOSE_FAT -> 0.85;
            case BUILD_MUSCLE -> 1.10;
            case GET_STRONGER, ENDURANCE -> 1.05;
            default -> 1.0;
        };
        double proteinPerLb = switch (goal) {
            case LOSE_FAT -> 1.0;
            case BUILD_MUSCLE, GET_STRONGER -> 0.9;
            default -> 0.8;
        };
        int calories = (int) (Math.round(maintenance * factor / 10.0) * 10);
        int protein = (int) Math.round(weight * proteinPerLb);
        int fat = (int) Math.round(weight * 0.3);
        int carbs = Math.max(0, (int) Math.round((calories - protein * 4 - fat * 9) / 4.0));
        return new EnergyEstimate(true, (int) Math.round(bmr), (int) Math.round(maintenance), calories, protein, fat,
                carbs, 30, goal.getLabel(), List.of());
    }

    private static String validZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(zone.trim()).getId();
        } catch (Exception invalid) {
            return null;
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record EnergyEstimate(boolean available, int bmr, int maintenance, int calories, int protein, int fat,
                                 int carbs, int fiber, String goalLabel, List<String> missing) {
        static EnergyEstimate missing(List<String> missing) {
            return new EnergyEstimate(false, 0, 0, 0, 0, 0, 0, 0, null, missing);
        }

        public Optional<String> missingText() {
            return missing.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", missing));
        }
    }
}
