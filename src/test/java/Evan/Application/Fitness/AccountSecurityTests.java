package Evan.Application.Fitness;

import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Service.AccountSecurityService;
import Evan.Application.Fitness.Service.AccountService;
import Evan.Application.Fitness.Service.MailService;
import Evan.Application.Fitness.Service.PasswordPolicy;
import Evan.Application.Fitness.Service.TrainingService;
import Evan.Application.Fitness.Service.WeeklySummaryService;
import Evan.Application.Fitness.Form.SessionForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Evan.Application.Fitness.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sign-in hardening, password reset/change, email verification and the weekly summary. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityTests {
    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired AccountSecurityService security;
    @Autowired UserLoginDetailsRepository users;
    @Autowired MailService mail;
    @Autowired SessionRegistry sessionRegistry;
    @Autowired WeeklySummaryService weekly;
    @Autowired TrainingService training;

    UserLoginDetails member;

    @BeforeEach
    void setUp() {
        member = TestUsers.create(accounts);
    }

    /** Each test gets its own client address so per-IP limits don't leak between tests. */
    private static RequestPostProcessor fromNewIp() {
        String ip = "10.20." + (IP.get() / 250) + "." + (IP.getAndIncrement() % 250 + 1);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void verify(UserLoginDetails account) {
        UserLoginDetails fresh = users.findById(account.getId()).orElseThrow();
        fresh.setEmailVerifiedAt(LocalDateTime.now());
        users.save(fresh);
    }

    private Optional<MailService.Email> lastMailTo(String address) {
        return mail.recent().stream().filter(e -> e.to().equals(address)).findFirst();
    }

    private static String token(MailService.Email email) {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(email.text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    // ---- Password rules ------------------------------------------------------

    @Test
    void passwordPolicyRejectsShortCommonAndPersonalPasswords() {
        assertThat(PasswordPolicy.problem("short", "sam", null)).contains("10");
        assertThat(PasswordPolicy.problem("Password123", "sam", null)).contains("guess");
        assertThat(PasswordPolicy.problem("aaaaaaaaaaaa", "sam", null)).contains("guess");
        assertThat(PasswordPolicy.problem("samantha-lifts-heavy", "samantha", null)).contains("username");
        assertThat(PasswordPolicy.problem("jordan.k-squats-daily", "user1", "jordan.k@example.com")).contains("email");
        assertThat(PasswordPolicy.problem("tall-green-kettlebell", "sam", "sam@example.com")).isNull();
    }

    @Test
    void signupRequiresAnEmailAndAStrongPassword() throws Exception {
        mvc.perform(post("/signup").with(csrf()).with(fromNewIp())
                        .param("username", "noemail").param("password", "tall-green-kettlebell").param("name", "No Email"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"));
        mvc.perform(post("/signup").with(csrf()).with(fromNewIp())
                        .param("username", "weakpw").param("email", "weakpw@example.com")
                        .param("password", "password123").param("name", "Weak"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("signupForm", "password", "weak"));
        mvc.perform(post("/signup").with(csrf()).with(fromNewIp())
                        .param("username", "dupemail").param("email", member.getEmail().toUpperCase())
                        .param("password", "tall-green-kettlebell").param("name", "Dupe"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("signupForm", "email", "taken"));
    }

    @Test
    void signupSendsAConfirmationLinkThatVerifiesTheAddress() throws Exception {
        String name = "new-" + UUID.randomUUID().toString().substring(0, 6);
        String email = name + "@Example.com";
        mvc.perform(post("/signup").with(csrf()).with(fromNewIp())
                        .param("username", name).param("email", email)
                        .param("password", "tall-green-kettlebell").param("name", "New Member"))
                .andExpect(redirectedUrl("/onboarding"));
        UserLoginDetails created = users.findByUsername(name).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(created.isEmailVerified()).isFalse();

        MailService.Email confirmation = lastMailTo(created.getEmail()).orElseThrow();
        assertThat(confirmation.subject()).contains("Confirm");
        mvc.perform(get("/verify-email").param("token", token(confirmation))).andExpect(redirectedUrl("/login"));
        assertThat(users.findById(created.getId()).orElseThrow().isEmailVerified()).isTrue();
        // Single use.
        mvc.perform(get("/verify-email").param("token", token(confirmation)))
                .andExpect(flash().attributeExists("flashError"));
    }

    // ---- Sign-in: email, lockout, rate limit --------------------------------------

    @Test
    void membersCanSignInWithUsernameOrEmail() throws Exception {
        RequestPostProcessor ip = fromNewIp();
        mvc.perform(post("/login").with(csrf()).with(ip).param("username", member.getUsername()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/home"));
        mvc.perform(post("/login").with(csrf()).with(ip).param("username", member.getEmail().toUpperCase()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/home"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        RequestPostProcessor ip = fromNewIp();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login").with(csrf()).with(ip).param("username", member.getUsername()).param("password", "wrong-" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        UserLoginDetails locked = users.findById(member.getId()).orElseThrow();
        assertThat(locked.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(10));
        mvc.perform(post("/login").with(csrf()).with(fromNewIp()).param("username", member.getUsername()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void successfulSignInResetsTheFailureCount() throws Exception {
        RequestPostProcessor ip = fromNewIp();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/login").with(csrf()).with(ip).param("username", member.getUsername()).param("password", "nope"));
        }
        assertThat(users.findById(member.getId()).orElseThrow().getFailedLoginCount()).isEqualTo(3);
        mvc.perform(post("/login").with(csrf()).with(ip).param("username", member.getUsername()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/home"));
        assertThat(users.findById(member.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    void oneAddressIsThrottledAfterTooManySignInAttempts() throws Exception {
        RequestPostProcessor ip = fromNewIp();
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/login").with(csrf()).with(ip).param("username", "nobody-" + i).param("password", "x"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").with(csrf()).with(ip).param("username", "nobody").param("password", "x"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // A different client isn't affected.
        mvc.perform(post("/login").with(csrf()).with(fromNewIp()).param("username", member.getUsername()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/home"));
    }

    // ---- Password reset ------------------------------------------------------------

    @Test
    void forgotPasswordLooksTheSameWhetherOrNotTheAccountExists() throws Exception {
        verify(member);
        int before = mail.recent().size();
        mvc.perform(post("/forgot-password").with(csrf()).with(fromNewIp()).param("login", "no-such-member"))
                .andExpect(redirectedUrl("/forgot-password")).andExpect(flash().attribute("sent", true));
        assertThat(mail.recent()).hasSize(before);
        mvc.perform(post("/forgot-password").with(csrf()).with(fromNewIp()).param("login", member.getUsername()))
                .andExpect(redirectedUrl("/forgot-password")).andExpect(flash().attribute("sent", true));
        assertThat(lastMailTo(member.getEmail())).get().extracting(MailService.Email::subject).isEqualTo("Reset your password");
    }

    @Test
    void unconfirmedAddressesNeverReceiveResetLinks() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).with(fromNewIp()).param("login", member.getEmail()));
        assertThat(mail.recent()).noneMatch(e -> e.to().equals(member.getEmail()) && e.subject().contains("Reset"));
    }

    @Test
    void resetLinkSetsANewPasswordOnceAndClearsALockout() throws Exception {
        verify(member);
        UserLoginDetails locked = users.findById(member.getId()).orElseThrow();
        locked.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        users.save(locked);
        security.requestPasswordReset(member.getEmail());
        String token = token(lastMailTo(member.getEmail()).orElseThrow());
        assertThat(lastMailTo(member.getEmail()).orElseThrow().html()).contains("/reset-password?token=" + token);

        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk()).andExpect(model().attribute("valid", true));
        mvc.perform(post("/reset-password").with(csrf()).with(fromNewIp()).param("token", token)
                        .param("newPassword", "brand-new-barbell").param("confirmPassword", "different-barbell"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("passwordForm", "confirmPassword", "mismatch"));
        mvc.perform(post("/reset-password").with(csrf()).with(fromNewIp()).param("token", token)
                        .param("newPassword", "brand-new-barbell").param("confirmPassword", "brand-new-barbell"))
                .andExpect(redirectedUrl("/login"));

        UserLoginDetails after = users.findById(member.getId()).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(accounts.passwordMatches(member.getId(), "brand-new-barbell")).isTrue();
        assertThat(accounts.passwordMatches(member.getId(), PASSWORD)).isFalse();
        assertThat(lastMailTo(member.getEmail()).orElseThrow().subject()).isEqualTo("Your password was changed");

        mvc.perform(get("/reset-password").param("token", token)).andExpect(model().attribute("valid", false));
        mvc.perform(post("/reset-password").with(csrf()).with(fromNewIp()).param("token", token)
                        .param("newPassword", "another-new-plate").param("confirmPassword", "another-new-plate"))
                .andExpect(model().attribute("valid", false));
        assertThat(accounts.passwordMatches(member.getId(), "another-new-plate")).isFalse();
    }

    @Test
    void madeUpResetTokensAreRejected() throws Exception {
        mvc.perform(get("/reset-password").param("token", "not-a-real-token")).andExpect(model().attribute("valid", false));
        mvc.perform(get("/reset-password")).andExpect(model().attribute("valid", false));
    }

    // ---- Signed-in account settings ------------------------------------------------

    @Test
    void changingThePasswordNeedsTheCurrentOneAndEndsOtherSessions() throws Exception {
        sessionRegistry.registerNewSession("other-device", new User(member.getUsername(), "x", List.of()));
        mvc.perform(post("/account/password").with(as(member)).with(csrf())
                        .param("currentPassword", "wrong").param("newPassword", "brand-new-barbell").param("confirmPassword", "brand-new-barbell"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("passwordForm", "currentPassword", "wrong"));
        assertThat(sessionRegistry.getSessionInformation("other-device").isExpired()).isFalse();

        mvc.perform(post("/account/password").with(as(member)).with(csrf())
                        .param("currentPassword", PASSWORD).param("newPassword", "brand-new-barbell").param("confirmPassword", "brand-new-barbell"))
                .andExpect(redirectedUrl("/account"));
        assertThat(accounts.passwordMatches(member.getId(), "brand-new-barbell")).isTrue();
        SessionInformation other = sessionRegistry.getSessionInformation("other-device");
        assertThat(other.isExpired()).isTrue();
        sessionRegistry.removeSessionInformation("other-device");
    }

    @Test
    void changingEmailNeedsThePasswordAndRequiresNewConfirmation() throws Exception {
        verify(member);
        String next = "moved-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
        mvc.perform(post("/account/email").with(as(member)).with(csrf()).param("email", next).param("currentPassword", "wrong"))
                .andExpect(flash().attributeExists("flashError"));
        assertThat(users.findById(member.getId()).orElseThrow().getEmail()).isEqualTo(member.getEmail());

        mvc.perform(post("/account/email").with(as(member)).with(csrf()).param("email", next).param("currentPassword", PASSWORD))
                .andExpect(flash().attributeExists("flashSuccess"));
        UserLoginDetails updated = users.findById(member.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo(next);
        assertThat(updated.isEmailVerified()).isFalse();
        assertThat(lastMailTo(next)).isPresent();
    }

    @Test
    void accountPageRendersForMembersWithAndWithoutEmail() throws Exception {
        mvc.perform(get("/account").with(as(member))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Not confirmed")));
        UserLoginDetails noEmail = users.findById(member.getId()).orElseThrow();
        noEmail.setEmail(null);
        users.save(noEmail);
        mvc.perform(get("/account").with(as(member))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add an email")));
    }

    // ---- Weekly summary ----------------------------------------------------------

    @Test
    void weeklySummaryIsOptInAndNeedsAConfirmedAddress() throws Exception {
        mvc.perform(post("/account/notifications").with(as(member)).with(csrf()).param("weeklyEmail", "true"))
                .andExpect(flash().attributeExists("flashError"));
        assertThat(users.findById(member.getId()).orElseThrow().isWeeklyEmail()).isFalse();

        verify(member);
        mvc.perform(post("/account/notifications").with(as(member)).with(csrf()).param("weeklyEmail", "true"))
                .andExpect(flash().attributeExists("flashSuccess"));
        assertThat(users.findById(member.getId()).orElseThrow().isWeeklyEmail()).isTrue();
        assertThat(users.weeklyEmailRecipients()).contains(member.getId());
    }

    @Test
    void weeklySummaryCountsLastWeekAndLeavesHealthDetailsOut() {
        verify(member);
        LocalDate today = LocalDate.now();
        LocalDate lastMonday = WeeklySummaryService.lastWeekStart(today);
        SessionForm form = new SessionForm();
        form.setSessionDate(lastMonday.plusDays(2));
        SessionForm.SetRow set = new SessionForm.SetRow();
        set.setExerciseName("Back Squat");
        set.setReps(5);
        set.setWeightLb(225.0);
        form.getSets().add(set);
        training.create(member.getId(), form);

        WeeklySummaryService.Summary s = weekly.build(member.getId(), lastMonday);
        assertThat(s.sessions()).isEqualTo(1);
        assertThat(s.workingSets()).isEqualTo(1);

        assertThat(weekly.sendPreview(member.getId())).isTrue();
        MailService.Email email = lastMailTo(member.getEmail()).orElseThrow();
        assertThat(email.subject()).startsWith("Preview: Your week in training");
        assertThat(email.headers()).containsKey("List-Unsubscribe").containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        // Starting weight (180 lb) from sign-up must not appear.
        assertThat(email.text()).doesNotContain("180").doesNotContain("Weight");
        assertThat(email.html()).doesNotContain("180");
    }

    @Test
    void eachWeekIsClaimedOnlyOnce() {
        LocalDate monday = LocalDate.of(2026, 9, 28);
        assertThat(weekly.claim(member.getId(), monday)).isTrue();
        assertThat(weekly.claim(member.getId(), monday)).isFalse();
        assertThat(weekly.claim(member.getId(), monday.plusWeeks(1))).isTrue();
    }

    @Test
    void unsubscribeLinkConfirmsBeforeTurningTheEmailOff() throws Exception {
        verify(member);
        security.setWeeklyEmail(member.getId(), true);
        String token = users.findById(member.getId()).orElseThrow().getUnsubscribeToken();

        // Link scanners fetch with GET: that must not unsubscribe.
        mvc.perform(get("/email/unsubscribe").param("token", token)).andExpect(status().isOk()).andExpect(model().attribute("valid", true));
        assertThat(users.findById(member.getId()).orElseThrow().isWeeklyEmail()).isTrue();

        // One-click POST from a mail client carries no CSRF token.
        mvc.perform(post("/email/unsubscribe").param("token", token)).andExpect(status().isOk()).andExpect(model().attribute("done", true));
        assertThat(users.findById(member.getId()).orElseThrow().isWeeklyEmail()).isFalse();

        mvc.perform(post("/email/unsubscribe").param("token", "made-up")).andExpect(model().attribute("done", false));
    }

    // ---- Headers & navigation -------------------------------------------------------

    @Test
    void permissionsPolicyHeaderIsSent() throws Exception {
        mvc.perform(get("/login")).andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")));
    }

    @Test
    void navigationHasFiveSectionsWithTabsInside() throws Exception {
        String home = mvc.perform(get("/home").with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(home).contains(">Progress<").doesNotContain("Custom metrics").doesNotContain("class=\"subnav\"");
        String goals = mvc.perform(get("/goals").with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(goals).contains("class=\"subnav\"").contains("href=\"/insights\"").contains("href=\"/timeline\"");
        String records = mvc.perform(get("/records").with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(records).contains(">Sessions</a>").contains(">Exercises</a>");
    }
}
