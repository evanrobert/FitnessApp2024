package Evan.Application.Fitness.Security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Tracks each member's sessions so a password change can sign out other devices. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** "Keep me signed in on this device": rotating per-device tokens stored in persistent_logins. */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public RateLimitFilter rateLimitFilter(Clock clock,
                                           @Value("${app.security.rate-limit.login:10}") int logins,
                                           @Value("${app.security.rate-limit.signup:10}") int signups,
                                           @Value("${app.security.rate-limit.reset:5}") int resets) {
        return new RateLimitFilter(clock,
                new RateLimitFilter.Rule("/login", logins, Duration.ofMinutes(5)),
                new RateLimitFilter.Rule("/signup", signups, Duration.ofHours(1)),
                new RateLimitFilter.Rule("/forgot-password", resets, Duration.ofMinutes(15)),
                new RateLimitFilter.Rule("/reset-password", resets * 2, Duration.ofMinutes(15)));
    }

    /** The limiter runs inside the security chain only, not as a second servlet filter. */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                                   SessionRegistry sessionRegistry, RateLimitFilter rateLimitFilter,
                                                   PersistentTokenRepository rememberedDevices,
                                                   UserDetailsService userDetails,
                                                   @Value("${app.security.remember-me-days:30}") int rememberDays)
            throws Exception {
        // CSRF protection stays enabled (the default). Thymeleaf th:action forms carry the token.
        // Load the token eagerly: pages stream, and a lazily created session can't be started
        // once the response is committed (first visit straight to a long page with a form).
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        http
                .csrf(csrf -> csrf.csrfTokenRequestHandler(csrfHandler)
                        // Mail clients' one-click unsubscribe can't carry a CSRF token; the link's own token authorizes it.
                        .ignoringRequestMatchers("/email/unsubscribe"))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/signup", "/error",
                                "/forgot-password", "/reset-password", "/verify-email", "/email/unsubscribe",
                                "/css/**", "/js/**", "/images/**", "/fonts/**", "/icons/**", "/favicon.svg", "/apple-touch-icon.png",
                                "/manifest.json", "/sw.js", "/offline.html").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/home", false)
                        .failureUrl("/login?error")
                        .permitAll())
                .rememberMe(remember -> remember
                        .tokenRepository(rememberedDevices)
                        .userDetailsService(userDetails)
                        .rememberMeParameter("remember")
                        .rememberMeCookieName("ef_remember")
                        .tokenValiditySeconds((int) Duration.ofDays(rememberDays).toSeconds())
                        // Only identifies this app's tokens in memory; the database row is what's checked.
                        .key(UUID.randomUUID().toString()))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                // Session id changes on sign-in (fixation protection); sessions ended by a
                // password change land on the sign-in page with an explanation.
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired"))
                .headers(headers -> headers
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self'; font-src 'self'; frame-ancestors 'none'; form-action 'self'; "
                                        + "base-uri 'self'; object-src 'none'")));
        return http.build();
    }
}
