package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.BodyMeasurement;
import Evan.Application.Fitness.Model.Roles;
import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.BodyMeasurementRepository;
import Evan.Application.Fitness.Repositorys.RoleRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
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

    public AccountService(UserLoginDetailsRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          BodyMeasurementRepository measurements, TodayService todayService) {
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
}
