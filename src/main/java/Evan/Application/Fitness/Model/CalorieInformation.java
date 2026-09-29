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

    @PositiveOrZero private double calories;
    @PositiveOrZero private double proteins;
    @PositiveOrZero private double fats;
    @PositiveOrZero private double carbohydrates;
    @PositiveOrZero private double fiber;
    @PositiveOrZero private double sugars;
    @PositiveOrZero private double sodium;
    @PositiveOrZero private double cholesterol;

    private String mealType;

    /** Protein 4, carbs 4, fat 9 kcal per gram. */
    public double macroCalories() {
        return proteins * 4 + carbohydrates * 4 + fats * 9;
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
