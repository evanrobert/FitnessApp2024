package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.AccountToken;
import Evan.Application.Fitness.Model.AccountToken.Purpose;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Password reset and change, email address and verification, and email
 * preferences. Every change that affects sign-in ends other sessions and
 * notifies the member by email.
 */
@Service
public class AccountSecurityService {
    public static final Duration RESET_TTL = Duration.ofMinutes(30);
    public static final Duration VERIFY_TTL = Duration.ofHours(24);
    /** Emails of one kind per account per hour, so the form can't be used to flood an inbox. */
    private static final int MAX_EMAILS_PER_HOUR = 3;

    private final UserLoginDetailsRepository users;
    private final AccountTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final SessionRegistry sessions;
    private final Clock clock;

    public AccountSecurityService(UserLoginDetailsRepository users, AccountTokenService tokens,
                                  PasswordEncoder passwordEncoder, MailService mail, SessionRegistry sessions, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.sessions = sessions;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Finds an account by username or email address, the way the sign-in form does. */
    @Transactional(readOnly = true)
    public Optional<UserLoginDetails> findByLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String value = login.trim();
        return value.contains("@") ? users.findByEmail(normalizeEmail(value)) : users.findByUsername(value);
    }

    public boolean emailInUse(String email, Long exceptUserId) {
        String normalized = normalizeEmail(email);
        return normalized != null && (exceptUserId == null ? users.existsByEmail(normalized)
                : users.existsByEmailAndIdNot(normalized, exceptUserId));
    }

    // ---- Password reset (signed out) --------------------------------------------

    /**
     * Emails a reset link when the username or email matches an account with a
     * verified address. Callers always show the same response, whether or not
     * anything was sent, so the form can't be used to discover accounts.
     */
    @Transactional
    public void requestPasswordReset(String login) {
        UserLoginDetails user = findByLogin(login).orElse(null);
        // Unverified addresses may be mistyped (someone else's inbox), so they never receive reset links.
        if (user == null || !user.isEmailVerified()
                || tokens.issuedWithin(user.getId(), Purpose.PASSWORD_RESET, Duration.ofHours(1)) >= MAX_EMAILS_PER_HOUR) {
            return;
        }
        String raw = tokens.issue(user, Purpose.PASSWORD_RESET, user.getEmail(), RESET_TTL);
        String link = mail.link("/reset-password?token=" + raw);
        mail.send(user.getEmail(), "Reset your password", "reset",
                Map.of("name", displayName(user), "link", link, "minutes", RESET_TTL.toMinutes()),
                "Someone (hopefully you) asked to reset your password.\n\nReset it here within "
                        + RESET_TTL.toMinutes() + " minutes:\n" + link
                        + "\n\nIf you didn't ask, ignore this email; your password hasn't changed.",
                Map.of());
    }

    public boolean resetTokenValid(String raw) {
        return tokens.peek(raw, Purpose.PASSWORD_RESET).isPresent();
    }

    /** Username behind a valid reset link, for the password rules; empty when the link is invalid. */
    @Transactional(readOnly = true)
    public Optional<UserLoginDetails> resetTokenOwner(String raw) {
        return tokens.peek(raw, Purpose.PASSWORD_RESET).map(AccountToken::getUser)
                .map(u -> users.findById(u.getId()).orElseThrow());
    }

    /** Sets the new password if the link is still valid. Returns false for a used, expired or unknown link. */
    @Transactional
    public boolean resetPassword(String raw, String newPassword) {
        Optional<AccountToken> token = tokens.redeem(raw, Purpose.PASSWORD_RESET);
        if (token.isEmpty()) {
            return false;
        }
        UserLoginDetails user = users.findById(token.get().getUser().getId()).orElseThrow();
        applyNewPassword(user, newPassword, null);
        return true;
    }

    // ---- Password change (signed in) ---------------------------------------------

    @Transactional
    public void changePassword(Long userId, String newPassword, String keepSessionId) {
        applyNewPassword(users.findById(userId).orElseThrow(), newPassword, keepSessionId);
    }

    private void applyNewPassword(UserLoginDetails user, String newPassword, String keepSessionId) {
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        tokens.invalidateAll(user.getId(), Purpose.PASSWORD_RESET);
        endSessions(user.getUsername(), keepSessionId);
        if (user.isEmailVerified()) {
            String link = mail.link("/forgot-password");
            mail.send(user.getEmail(), "Your password was changed", "password-changed",
                    Map.of("name", displayName(user), "link", link),
                    "The password for your account was just changed and other devices were signed out.\n\n"
                            + "If this wasn't you, reset your password now: " + link,
                    Map.of());
        }
    }

    /** Signs the member out everywhere except (optionally) the session making the change. */
    public void endSessions(String username, String keepSessionId) {
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equals(username)) {
                for (SessionInformation session : sessions.getAllSessions(principal, false)) {
                    if (!session.getSessionId().equals(keepSessionId)) {
                        session.expireNow();
                    }
                }
            }
        }
    }

    // ---- Email address & verification ----------------------------------------------

    /** Stores a new address (unverified) and sends it a confirmation link. */
    @Transactional
    public void changeEmail(Long userId, String email) {
        UserLoginDetails user = users.findById(userId).orElseThrow();
        String normalized = normalizeEmail(email);
        if (normalized != null && normalized.equals(user.getEmail())) {
            return;
        }
        user.setEmail(normalized);
        user.setEmailVerifiedAt(null);
        tokens.invalidateAll(userId, Purpose.VERIFY_EMAIL);
        if (normalized != null) {
            sendVerificationTo(user);
        } else {
            user.setWeeklyEmail(false);
        }
    }

    /** Returns false when too many confirmation emails were sent recently. */
    @Transactional
    public boolean resendVerification(Long userId) {
        UserLoginDetails user = users.findById(userId).orElseThrow();
        if (user.getEmail() == null || user.isEmailVerified()) {
            return true;
        }
        if (tokens.issuedWithin(userId, Purpose.VERIFY_EMAIL, Duration.ofHours(1)) >= MAX_EMAILS_PER_HOUR) {
            return false;
        }
        sendVerificationTo(user);
        return true;
    }

    /** Sends the first confirmation email after sign-up. */
    @Transactional
    public void sendVerificationTo(UserLoginDetails user) {
        String raw = tokens.issue(user, Purpose.VERIFY_EMAIL, user.getEmail(), VERIFY_TTL);
        String link = mail.link("/verify-email?token=" + raw);
        mail.send(user.getEmail(), "Confirm your email address", "verify",
                Map.of("name", displayName(user), "link", link),
                "Confirm this address so you can reset your password and receive weekly summaries if you choose to:\n"
                        + link + "\n\nThe link works for 24 hours. If you didn't create an account, ignore this email.",
                Map.of());
    }

    /** Confirms the address the link was sent to, if it is still the account's address. */
    @Transactional
    public boolean verifyEmail(String raw) {
        Optional<AccountToken> token = tokens.redeem(raw, Purpose.VERIFY_EMAIL);
        if (token.isEmpty()) {
            return false;
        }
        UserLoginDetails user = users.findById(token.get().getUser().getId()).orElseThrow();
        if (user.getEmail() == null || !user.getEmail().equals(token.get().getEmail())) {
            return false;
        }
        user.setEmailVerifiedAt(now());
        return true;
    }

    // ---- Email preferences -----------------------------------------------------------

    /** Turns the weekly summary on or off. It can only be turned on for a verified address. */
    @Transactional
    public boolean setWeeklyEmail(Long userId, boolean enabled) {
        UserLoginDetails user = users.findById(userId).orElseThrow();
        if (enabled && !user.isEmailVerified()) {
            return false;
        }
        user.setWeeklyEmail(enabled);
        if (enabled && user.getUnsubscribeToken() == null) {
            user.setUnsubscribeToken(AccountTokenService.randomToken());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public boolean unsubscribeTokenValid(String token) {
        return token != null && !token.isBlank() && users.findByUnsubscribeToken(token).isPresent();
    }

    /** One-click unsubscribe from the link in the email; works without signing in. */
    @Transactional
    public boolean unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return users.findByUnsubscribeToken(token).map(user -> {
            user.setWeeklyEmail(false);
            return true;
        }).orElse(false);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String displayName(UserLoginDetails user) {
        return user.getUserInformation() != null && user.getUserInformation().getName() != null
                ? user.getUserInformation().getName().split("\\s+")[0] : user.getUsername();
    }
}
