package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

/**
 * One per member per day: sleep, how they feel, hydration and daily activity.
 * Ratings are 1 (low) to 5 (high); for stress and soreness a high value is worse.
 */
@Entity
@Table(name = "daily_check_in")
@Getter
@Setter
@NoArgsConstructor
public class DailyCheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Defaults to the member's today when omitted. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkInDate;

    @DecimalMin("0") @DecimalMax("24") private Double sleepHours;
    @Min(1) @Max(5) private Integer sleepQuality;
    @Min(1) @Max(5) private Integer energy;
    @Min(1) @Max(5) private Integer mood;
    @Min(1) @Max(5) private Integer stress;
    @Min(1) @Max(5) private Integer soreness;
    @DecimalMin("0") @DecimalMax("400") private Double waterOz;
    @Min(0) @Max(150000) private Integer steps;
    @Min(25) @Max(150) private Integer restingHr;

    @Size(max = 1000)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserLoginDetails user;

    /**
     * Readiness 0-100 from whichever signals were recorded: sleep (hours vs 8,
     * quality), energy, mood, and inverted stress/soreness. Null when nothing
     * relevant was logged.
     */
    public Integer readinessScore() {
        double total = 0;
        int parts = 0;
        if (sleepHours != null) {
            total += Math.min(sleepHours / 8.0, 1.0);
            parts++;
        }
        for (Integer good : new Integer[]{sleepQuality, energy, mood}) {
            if (good != null) {
                total += (good - 1) / 4.0;
                parts++;
            }
        }
        for (Integer bad : new Integer[]{stress, soreness}) {
            if (bad != null) {
                total += (5 - bad) / 4.0;
                parts++;
            }
        }
        return parts == 0 ? null : (int) Math.round(total / parts * 100);
    }

    public static int[] scale() {
        return IntStream.rangeClosed(1, 5).toArray();
    }
}
