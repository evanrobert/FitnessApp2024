package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.CalorieInformation;
import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Model.WorkoutSession;
import Evan.Application.Fitness.Repositorys.UserInformationRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * The opt-in Monday email: last week's activity and a nudge for this week.
 *
 * Email is a less protected channel than the app, so the summary carries only
 * activity counts. Weight, sleep, mood, injuries and goal names stay in the app.
 */
@Service
public class WeeklySummaryService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final UserLoginDetailsRepository users;
    private final UserInformationRepository profiles;
    private final TrainingService training;
    private final NutritionService nutrition;
    private final CheckInService checkIns;
    private final GoalService goals;
    private final RecordsService records;
    private final TodayService todayService;
    private final MailService mail;

    public WeeklySummaryService(UserLoginDetailsRepository users, UserInformationRepository profiles,
                                TrainingService training, NutritionService nutrition, CheckInService checkIns,
                                GoalService goals, RecordsService records, TodayService todayService, MailService mail) {
        this.users = users;
        this.profiles = profiles;
        this.training = training;
        this.nutrition = nutrition;
        this.checkIns = checkIns;
        this.goals = goals;
        this.records = records;
        this.todayService = todayService;
        this.mail = mail;
    }

    public record Summary(String firstName, LocalDate from, LocalDate to, int sessions, int target, long workingSets,
                          int cardioMinutes, long prs, int foodDays, int checkInDays, int activeGoals,
                          int goalsOnTrack, String headline, String nextStep) {
        public String range() {
            return DAY.format(from) + " – " + DAY.format(to);
        }
    }

    /** Monday–Sunday week before the one containing {@code today}. */
    public static LocalDate lastWeekStart(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    @Transactional(readOnly = true)
    public Summary build(Long userId, LocalDate from) {
        LocalDate to = from.plusDays(6);
        UserInformation profile = profiles.findByUserId(userId).orElse(null);
        int target = profile == null ? 3 : profile.weeklyTargetOrDefault();
        String firstName = profile == null || profile.getName() == null ? "there" : profile.getName().trim().split("\\s+")[0];

        List<WorkoutSession> sessions = training.between(userId, from, to);
        long sets = sessions.stream().mapToLong(WorkoutSession::workingSetCount).sum();
        int cardio = (int) Math.round(sessions.stream().mapToDouble(WorkoutSession::cardioMinutes).sum());
        long prs = records.summary(userId).events().stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).count();
        int foodDays = (int) nutrition.between(userId, from, to).stream().map(CalorieInformation::getDate).distinct().count();
        int checkInDays = checkIns.between(userId, from, to).size();
        List<GoalService.Progress> active = goals.active(userId);
        int onTrack = (int) active.stream().filter(p -> p.pace() == GoalService.Pace.ON_TRACK
                || p.pace() == GoalService.Pace.REACHED).count();

        return new Summary(firstName, from, to, sessions.size(), target, sets, cardio, prs, foodDays, checkInDays,
                active.size(), onTrack, headline(sessions.size(), target, prs), nextStep(sessions.size(), target, foodDays, checkInDays));
    }

    private static String headline(int sessions, int target, long prs) {
        if (sessions == 0) {
            return "A quiet week. This one's a fresh start.";
        }
        if (sessions >= target) {
            return prs > 0 ? "Target hit, with new personal records." : "You hit your training target.";
        }
        return sessions + " of " + target + " planned sessions. Solid base to build on.";
    }

    private static String nextStep(int sessions, int target, int foodDays, int checkInDays) {
        if (sessions < target) {
            return "Put " + target + " sessions on your calendar for this week, and log each one right after.";
        }
        if (foodDays < 4) {
            return "Training is on point. Try logging meals on at least 4 days this week to see how fuel lines up.";
        }
        if (checkInDays < 4) {
            return "Add the 30-second daily check-in on 4+ days to unlock readiness trends.";
        }
        return "Keep the streak going: same plan, and aim to add a little weight or a rep to one lift.";
    }

    /** Sends last week's summary. Returns false when the member has no verified address. */
    @Transactional
    public boolean send(Long userId, LocalDate weekStart, boolean preview) {
        UserLoginDetails user = users.findById(userId).orElseThrow();
        if (!user.isEmailVerified()) {
            return false;
        }
        if (user.getUnsubscribeToken() == null) {
            user.setUnsubscribeToken(AccountTokenService.randomToken());
        }
        Summary s = build(userId, weekStart);
        String unsubscribe = mail.link("/email/unsubscribe?token=" + user.getUnsubscribeToken());
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("s", s);
        vars.put("preview", preview);
        vars.put("unsubscribeLink", unsubscribe);
        vars.put("settingsLink", mail.link("/account#notifications"));
        vars.put("homeLink", mail.link("/home"));
        String text = (preview ? "(Preview)\n\n" : "") + "Hi " + s.firstName() + ",\n\nYour week, " + s.range() + ": " + s.headline()
                + "\n\n  Workouts: " + s.sessions() + " of " + s.target()
                + "\n  Working sets: " + s.workingSets()
                + "\n  Cardio: " + s.cardioMinutes() + " min"
                + "\n  New personal records: " + s.prs()
                + "\n  Days with meals logged: " + s.foodDays() + " of 7"
                + "\n  Daily check-ins: " + s.checkInDays() + " of 7"
                + (s.activeGoals() > 0 ? "\n  Goals on track: " + s.goalsOnTrack() + " of " + s.activeGoals() : "")
                + "\n\nThis week: " + s.nextStep()
                + "\n\nOpen the app for the full picture: " + mail.link("/home")
                + "\n\nStop these emails: " + unsubscribe;
        String subject = (preview ? "Preview: " : "") + "Your week in training · " + s.range();
        mail.send(user.getEmail(), subject, "weekly", vars, text,
                Map.of("List-Unsubscribe", "<" + unsubscribe + ">", "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
        return true;
    }

    /** Marks this week's email as sent; false when it already was. */
    @Transactional
    public boolean claim(Long userId, LocalDate day) {
        return users.claimWeeklyEmail(userId, day) == 1;
    }

    @Transactional
    public boolean sendPreview(Long userId) {
        return send(userId, lastWeekStart(todayService.today(userId)), true);
    }
}
