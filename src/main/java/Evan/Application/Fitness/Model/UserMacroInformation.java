package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Daily nutrition targets. One row per member. */
@Entity
@Table(name = "user_macro_information")
@Getter
@Setter
@NoArgsConstructor
public class UserMacroInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @PositiveOrZero private double dailyCalories;
    @PositiveOrZero private double dailyProtein;
    @PositiveOrZero private double dailyFat;
    @PositiveOrZero private double dailyCarbohydrates;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", unique = true)
    private UserLoginDetails user;
}
