package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Something a member chose to track that the app doesn't model directly (mile time, push-ups, steps...). */
@Entity
@Table(name = "custom_metric")
@Getter
@Setter
@NoArgsConstructor
public class CustomMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name what you're tracking") @Size(max = 80)
    private String name;

    @Size(max = 24)
    private String unit;

    @Enumerated(EnumType.STRING)
    private MetricCategory category;

    private boolean higherIsBetter = true;

    private boolean archived;

    @Size(max = 255)
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserLoginDetails user;
}
