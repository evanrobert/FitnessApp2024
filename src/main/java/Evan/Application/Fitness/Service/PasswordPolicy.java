package Evan.Application.Fitness.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Rules for new passwords (sign-up, change, reset), following current NIST
 * guidance: length over complexity, and no well-known or personal passwords.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 100;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd",
            "123456", "1234567", "12345678", "123456789", "1234567890", "0123456789", "0987654321",
            "qwerty", "qwerty123", "qwertyuiop", "asdfghjkl", "zxcvbnm", "1q2w3e4r5t", "1qaz2wsx3edc",
            "iloveyou", "letmein", "letmein123", "welcome", "welcome123", "admin", "admin123", "administrator",
            "football", "baseball", "basketball", "superman", "batman", "trustno1", "sunshine", "princess",
            "dragon", "monkey", "starwars", "whatever", "changeme", "changeme123", "fitness", "fitness123",
            "workout", "workout123", "gympassword", "abcdefghij", "abcd1234", "aaaaaaaaaa", "1111111111");

    private PasswordPolicy() {
    }

    /** Returns a message describing the problem, or null when the password is acceptable. */
    public static String problem(String password, String username, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Use at least " + MIN_LENGTH + " characters";
        }
        if (password.length() > MAX_LENGTH) {
            return "Use at most " + MAX_LENGTH + " characters";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() < 4) {
            return "That password is too easy to guess";
        }
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            return "Don't include your username in your password";
        }
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (local.length() >= 3 && lower.contains(local)) {
                return "Don't include your email address in your password";
            }
        }
        return null;
    }
}
