package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.AccountToken;
import Evan.Application.Fitness.Model.AccountToken.Purpose;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.AccountTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Issues and redeems single-use emailed links. Raw tokens exist only in the email. */
@Service
public class AccountTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository tokens;
    private final Clock clock;

    public AccountTokenService(AccountTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** 256 bits of randomness, URL-safe. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public String issue(UserLoginDetails user, Purpose purpose, String email, Duration ttl) {
        String raw = randomToken();
        AccountToken token = new AccountToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setEmail(email);
        token.setTokenHash(hash(raw));
        token.setCreatedAt(now());
        token.setExpiresAt(now().plus(ttl));
        tokens.save(token);
        return raw;
    }

    /** A valid, unused token of this purpose, without redeeming it (e.g. to show the reset form). */
    @Transactional(readOnly = true)
    public Optional<AccountToken> peek(String raw, Purpose purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 100) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(raw))
                .filter(t -> t.getPurpose() == purpose && t.usableAt(now()));
    }

    /** Redeems the token: afterwards it can never be used again. */
    @Transactional
    public Optional<AccountToken> redeem(String raw, Purpose purpose) {
        Optional<AccountToken> token = peek(raw, purpose);
        token.ifPresent(t -> t.setUsedAt(now()));
        return token;
    }

    @Transactional(readOnly = true)
    public long issuedWithin(Long userId, Purpose purpose, Duration window) {
        return tokens.countIssuedSince(userId, purpose, now().minus(window));
    }

    @Transactional
    public void invalidateAll(Long userId, Purpose purpose) {
        tokens.invalidate(userId, purpose, now());
    }

    /** Housekeeping: drop links that expired more than a day ago. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(now().minusDays(1));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
