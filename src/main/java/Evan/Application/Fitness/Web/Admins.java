package Evan.Application.Fitness.Web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Who may see owner-only pages: usernames in app.admin-usernames (ADMIN_USERNAMES), comma-separated. */
@Component
public class Admins {
    private final Set<String> usernames;

    public Admins(@Value("${app.admin-usernames:}") String list) {
        usernames = Arrays.stream(list.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public boolean isAdmin(String username) {
        return username != null && usernames.contains(username.toLowerCase(Locale.ROOT));
    }
}
