package Evan.Application.Fitness.Model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserMacroInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private double dailyCalories;
    private double dailyProtein;
    private double dailyFat;
    private double dailyCarbohydrates;
    @OneToOne
    @JoinColumn(name = "userid")
    private UserLoginDetails userLoginDetails;
}
