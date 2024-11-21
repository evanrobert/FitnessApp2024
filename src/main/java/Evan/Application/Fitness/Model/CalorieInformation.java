package Evan.Application.Fitness.Model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class CalorieInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String itemName;
    private double calories;
    private double proteins;
    private double fats;
    private double carbohydrates;
    private double fiber;
    private double sugars;
    private double sodium;
    private double cholesterol;
  private String mealType;
    @Temporal(TemporalType.DATE)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date;
    @OneToOne
    @JoinColumn(name = "userid")
    private UserLoginDetails userLoginDetails;

}
