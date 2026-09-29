package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Form.SignupForm;
import Evan.Application.Fitness.Model.Roles;
import Evan.Application.Fitness.Model.UserInformation;
import Evan.Application.Fitness.Model.UserLoginDetails;
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

    public AccountService(UserLoginDetailsRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
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
        profile.setAge(form.getAge() == null ? 0 : form.getAge());
        profile.setWeight(form.getWeight() == null ? 0 : form.getWeight());
        account.setUserInformation(profile);

        return users.save(account);
    }
}
