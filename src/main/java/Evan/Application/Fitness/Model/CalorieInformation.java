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

/** One logged food or meal entry. */
@Entity
@Table(name = "calorie_information")
@Getter
@Setter
@NoArgsConstructor
public class CalorieInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Give the meal a name")
    @Size(max = 255)
    private String itemName;

    @PositiveOrZero private Double calories;
    @PositiveOrZero private Double proteins;
    @PositiveOrZero private Double fats;
    @PositiveOrZero private Double carbohydrates;
    @PositiveOrZero private Double fiber;
    @PositiveOrZero private Double sugars;
    @PositiveOrZero private Double sodium;
    @PositiveOrZero private Double cholesterol;

    private String mealType;

    /** Protein 4, carbs 4, fat 9 kcal per gram. */
    public double macroCalories() {
        return nz(proteins) * 4 + nz(carbohydrates) * 4 + nz(fats) * 9;
    }

    /** Stored columns are NOT NULL; blank form fields mean zero. */
    @PrePersist
    @PreUpdate
    void blanksToZero() {
        calories = nz(calories);
        proteins = nz(proteins);
        fats = nz(fats);
        carbohydrates = nz(carbohydrates);
        fiber = nz(fiber);
        sugars = nz(sugars);
        sodium = nz(sodium);
        cholesterol = nz(cholesterol);
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Many entries per member (was mapped as one-to-one).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid")
    private UserLoginDetails user;
}
