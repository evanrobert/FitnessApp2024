package Evan.Application.Fitness.Form;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignupForm {
    @NotBlank(message = "Choose a username")
    @Size(min = 3, max = 40, message = "Usernames are 3-40 characters")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Use letters, numbers, dots, dashes or underscores")
    private String username;

    @NotBlank(message = "Choose a password")
    @Size(min = 8, max = 100, message = "Passwords need at least 8 characters")
    private String password;

    @NotBlank(message = "Tell us your name")
    @Size(max = 80)
    private String name;

    @Min(value = 13, message = "You must be at least 13") @Max(value = 110, message = "Check your age")
    private Integer age;

    @Min(value = 50, message = "Check your weight") @Max(value = 900, message = "Check your weight")
    private Integer weight;
}
