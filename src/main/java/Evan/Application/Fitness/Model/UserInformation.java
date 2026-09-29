package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

/** Member profile: who they are, what they're training for, and their gym membership. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_information")
public class UserInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Add your name") @Size(max = 80)
    private String name;

    @Min(value = 1900, message = "Check the year") @Max(value = 2100, message = "Check the year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @DecimalMin(value = "36", message = "Height is in inches") @DecimalMax(value = "96", message = "Height is in inches")
    private Double heightIn;

    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    private PrimaryGoal primaryGoal;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    @Min(0) @Max(14)
    private Integer weeklyWorkoutTarget;

    @Size(max = 64)
    private String timeZone;

    @Size(max = 1000)
    private String bio;

    @Size(max = 120)
    private String gymName;

    @Size(max = 80)
    private String membershipPlan;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate membershipStartedOn;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate membershipRenewsOn;

    @PositiveOrZero @Digits(integer = 8, fraction = 2)
    private BigDecimal membershipMonthlyCost;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", unique = true)
    private UserLoginDetails user;

    public Integer ageIn(Year year) {
        return birthYear == null ? null : year.getValue() - birthYear;
    }

    public int weeklyTargetOrDefault() {
        return weeklyWorkoutTarget == null || weeklyWorkoutTarget == 0 ? 3 : weeklyWorkoutTarget;
    }
}
