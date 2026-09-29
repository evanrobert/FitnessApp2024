package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An injury or limitation to train around. Kept as a history (active -> resolved). */
@Entity
@Table(name = "limitation")
@Getter
@Setter
@NoArgsConstructor
public class Limitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Which area is affected?") @Size(max = 60)
    private String bodyArea;

    @NotBlank(message = "Describe it briefly") @Size(max = 120)
    private String title;

    /** 1 = minor annoyance, 5 = stops training for that area. */
    @Min(1) @Max(5)
    private Integer severity;

    @NotNull
    @Enumerated(EnumType.STRING)
    private LimitationStatus status = LimitationStatus.ACTIVE;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startedOn;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate resolvedOn;

    /** What to avoid or modify. */
    @Size(max = 1000)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserLoginDetails user;
}
