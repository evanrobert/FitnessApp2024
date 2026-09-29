package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.BodyMeasurement;
import Evan.Application.Fitness.Model.Roles;
import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.BodyMeasurementRepository;
import Evan.Application.Fitness.Repositorys.RoleRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserLoginDetailsRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final BodyMeasurementRepository measurements;
    private final TodayService todayService;
    private final EntityManager em;

    public AccountService(UserLoginDetailsRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          BodyMeasurementRepository measurements, TodayService todayService, EntityManager em) {
        this.em = em;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.measurements = measurements;
        this.todayService = todayService;
    }

    public boolean usernameTaken(String username) {
        return users.existsByUsernameIgnoreCase(username.trim());
    }

    /** Creates the account and its profile. Callers check {@link #usernameTaken} first. */
    @Transactional
    public UserLoginDetails register(SignupForm form) {
        UserLoginDetails account = new UserLoginDetails();
        account.setUsername(form.getUsername().trim());
        account.setPassword(passwordEncoder.encode(form.getPassword()));
        account.getRoles().add(roles.findByName(Roles.USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER is missing; check migrations")));

        UserInformation profile = new UserInformation();
        profile.setName(form.getName().trim());
        account.setUserInformation(profile);
        UserLoginDetails saved = users.save(account);

        if (form.getWeight() != null) {
            BodyMeasurement first = new BodyMeasurement();
            first.setUser(saved);
            first.setMeasuredOn(todayService.today(saved.getId()));
            first.setWeightLb(form.getWeight());
            first.setNotes("Starting weight from sign-up");
            measurements.save(first);
        }
        return saved;
    }

    public boolean passwordMatches(Long userId, String rawPassword) {
        return rawPassword != null && users.findById(userId)
                .map(u -> passwordEncoder.matches(rawPassword, u.getPassword())).orElse(false);
    }

    /**
     * Permanently deletes the member and everything they logged. Order follows
     * the foreign keys; sets, cardio and metric entries cascade in the database.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        String[] statements = {
                "DELETE FROM Goal g WHERE g.user.id = :u",
                "DELETE FROM Limitation l WHERE l.user.id = :u",
                "DELETE FROM CustomMetric m WHERE m.user.id = :u",
                "DELETE FROM WorkoutSession s WHERE s.user.id = :u",
                "DELETE FROM Exercise e WHERE e.owner.id = :u",
                "DELETE FROM DailyCheckIn c WHERE c.user.id = :u",
                "DELETE FROM BodyMeasurement b WHERE b.user.id = :u",
                "DELETE FROM CalorieInformation c WHERE c.user.id = :u",
                "DELETE FROM UserMacroInformation t WHERE t.user.id = :u",
                "DELETE FROM UserInformation p WHERE p.user.id = :u"
        };
        for (String jpql : statements) {
            em.createQuery(jpql).setParameter("u", userId).executeUpdate();
        }
        em.createNativeQuery("DELETE FROM users_roles WHERE user_login_details_id = :u").setParameter("u", userId).executeUpdate();
        em.createQuery("DELETE FROM UserLoginDetails u WHERE u.id = :u").setParameter("u", userId).executeUpdate();
        em.clear();
    }
}
