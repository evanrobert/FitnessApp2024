package Evan.Application.Fitness.Security;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Account lockout: after {@code app.security.max-failed-logins} wrong passwords in a
 * row, the account can't sign in for {@code app.security.lockout} (even with the right
 * password). A successful sign-in or a password reset clears the counter.
 */
@Component
public class LoginAttemptListener {
    private final UserLoginDetailsRepository users;
    private final SecurityDetailsService details;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lockout;

    public LoginAttemptListener(UserLoginDetailsRepository users, SecurityDetailsService details, Clock clock,
                                @Value("${app.security.max-failed-logins:5}") int maxFailures,
                                @Value("${app.security.lockout:15m}") Duration lockout) {
        this.users = users;
        this.details = details;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockout = lockout;
    }

    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        details.find(event.getAuthentication().getName()).ifPresent(user -> {
            users.incrementFailedLogins(user.getUsername());
            users.lockIfOverThreshold(user.getUsername(), maxFailures, LocalDateTime.now(clock).plus(lockout));
        });
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        users.clearFailedLogins(event.getAuthentication().getName());
    }
}
