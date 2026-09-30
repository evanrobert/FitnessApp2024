package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A single-use emailed link. Only the SHA-256 hash of the token is stored. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_token")
public class AccountToken {
    public enum Purpose { PASSWORD_RESET, VERIFY_EMAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserLoginDetails user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Purpose purpose;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** For email verification: the address the link confirms. */
    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public boolean usableAt(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
