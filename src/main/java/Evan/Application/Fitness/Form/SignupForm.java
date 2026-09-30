package Evan.Application.Fitness.Form;

import Evan.Application.Fitness.Service.PasswordPolicy;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignupForm {
    @NotBlank(message = "Choose a username")
    @Size(min = 3, max = 40, message = "Usernames are 3-40 characters")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Use letters, numbers, dots, dashes or underscores")
    private String username;

    @NotBlank(message = "Add your email so you can reset your password")
    @Email(message = "Check your email address")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Choose a password")
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
            message = "Passwords need at least " + PasswordPolicy.MIN_LENGTH + " characters")
    private String password;

    @NotBlank(message = "Tell us your name")
    @Size(max = 80)
    private String name;

    /** Optional; becomes the member's first weigh-in. */
    @DecimalMin(value = "50", message = "Check your weight") @DecimalMax(value = "900", message = "Check your weight")
    private Double weight;
}
