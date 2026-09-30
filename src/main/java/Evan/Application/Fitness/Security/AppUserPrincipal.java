package Evan.Application.Fitness.Security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** Authenticated member. Carries the account id so controllers never re-query by username. */
public class AppUserPrincipal extends User {
    private final Long id;

    public AppUserPrincipal(Long id, String username, String password,
                            Collection<? extends GrantedAuthority> authorities) {
        this(id, username, password, true, authorities);
    }

    public AppUserPrincipal(Long id, String username, String password, boolean accountNonLocked,
                            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, true, true, true, accountNonLocked, authorities);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
