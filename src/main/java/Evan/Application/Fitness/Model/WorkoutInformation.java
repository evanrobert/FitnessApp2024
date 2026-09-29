package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Entity
@Table(name = "workout_information")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 255)
    private String exerciseName;
    private String workoutType;
    @Min(1) private int sets;
    @Min(1) private int reps;
    @PositiveOrZero private double weight;
    @Size(max = 255)
    private String notes;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid")
    private UserLoginDetails user;
}
