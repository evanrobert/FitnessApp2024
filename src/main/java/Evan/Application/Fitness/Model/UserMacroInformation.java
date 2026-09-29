package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Daily nutrition and hydration targets. One row per member. */
@Entity
@Table(name = "user_macro_information")
@Getter
@Setter
@NoArgsConstructor
public class UserMacroInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @PositiveOrZero @Max(10000) private double dailyCalories;
    @PositiveOrZero @Max(1000) private double dailyProtein;
    @PositiveOrZero @Max(1000) private double dailyFat;
    @PositiveOrZero @Max(2000) private double dailyCarbohydrates;
    @PositiveOrZero @Max(200) private Double dailyFiber;
    @PositiveOrZero @Max(400) private Double dailyWaterOz;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", unique = true)
    private UserLoginDetails user;
}
