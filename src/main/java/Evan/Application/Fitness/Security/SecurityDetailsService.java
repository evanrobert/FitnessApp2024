package Evan.Application.Fitness.Security;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Service.AccountSecurityService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Signs members in by username or email; a temporarily locked account can't sign in at all. */
@Service
public class SecurityDetailsService implements UserDetailsService {
    private final UserLoginDetailsRepository users;
    private final Clock clock;

    public SecurityDetailsService(UserLoginDetailsRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        return find(login).map(this::principal).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    Optional<UserLoginDetails> find(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String value = login.trim();
        return value.contains("@") ? users.findByEmail(AccountSecurityService.normalizeEmail(value))
                : users.findByUsername(value);
    }

    private AppUserPrincipal principal(UserLoginDetails user) {
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now(clock));
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPassword(), !locked,
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).toList());
    }
}
