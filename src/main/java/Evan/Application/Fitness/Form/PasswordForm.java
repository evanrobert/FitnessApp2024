package Evan.Application.Fitness.Form;

import lombok.Data;

/** Choosing a new password: change (with the current one) or reset (with an emailed token). */
@Data
public class PasswordForm {
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
    private String token;
}
