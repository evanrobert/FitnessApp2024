package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** The account a member logs in with. Every tracked record belongs to one of these. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_login_details")
public class UserLoginDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    /** Lower-cased. Used to sign in and to receive reset links and (opt-in) weekly summaries. */
    @Column(unique = true, length = 254)
    private String email;

    /** Set once the member clicks the link sent to {@link #email}; cleared when the address changes. */
    private LocalDateTime emailVerifiedAt;

    private LocalDateTime passwordChangedAt;

    /** Consecutive failed sign-ins; reset on success. Enough of them sets {@link #lockedUntil}. */
    @Column(nullable = false)
    private int failedLoginCount;

    private LocalDateTime lockedUntil;

    /** Opt-in weekly summary email. */
    @Column(nullable = false)
    private boolean weeklyEmail;

    private LocalDate weeklyEmailLastSent;

    /** Random value for the one-click unsubscribe link in emails. */
    @Column(unique = true, length = 64)
    private String unsubscribeToken;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserInformation userInformation;

    // Roles are reference data (seeded by migration); never cascade writes to them.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "users_roles",
            joinColumns = @JoinColumn(name = "user_login_details_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Roles> roles = new HashSet<>();

    public boolean isEmailVerified() {
        return email != null && emailVerifiedAt != null;
    }

    public void setUserInformation(UserInformation userInformation) {
        this.userInformation = userInformation;
        if (userInformation != null) {
            userInformation.setUser(this);
        }
    }
}
