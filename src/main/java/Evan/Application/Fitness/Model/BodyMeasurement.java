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

/** A weigh-in and/or tape measurement on a given day. Every field except the date is optional. */
@Entity
@Table(name = "body_measurement")
@Getter
@Setter
@NoArgsConstructor
public class BodyMeasurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Pick a date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate measuredOn;

    @DecimalMin(value = "50", message = "Check the weight") @DecimalMax(value = "900", message = "Check the weight")
    private Double weightLb;
    @DecimalMin("2") @DecimalMax("70")
    private Double bodyFatPct;
    @DecimalMin("10") @DecimalMax("90") private Double waistIn;
    @DecimalMin("10") @DecimalMax("90") private Double hipsIn;
    @DecimalMin("10") @DecimalMax("90") private Double chestIn;
    @DecimalMin("4") @DecimalMax("40") private Double armIn;
    @DecimalMin("8") @DecimalMax("50") private Double thighIn;
    @DecimalMin("8") @DecimalMax("40") private Double neckIn;

    @Size(max = 500)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserLoginDetails user;

    public boolean hasTapeMeasurements() {
        return waistIn != null || hipsIn != null || chestIn != null || armIn != null || thighIn != null || neckIn != null;
    }
}
