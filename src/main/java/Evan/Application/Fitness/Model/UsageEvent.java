package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** That something was logged, and how; never what. See V8__usage_events.sql. */
@Entity
@Table(name = "usage_event")
@Getter
@Setter
@NoArgsConstructor
public class UsageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kept only to count distinct members and days; reports never show who. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 16)
    private String method;

    private Integer seconds;

    @Column(nullable = false)
    private LocalDate occurredOn;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
