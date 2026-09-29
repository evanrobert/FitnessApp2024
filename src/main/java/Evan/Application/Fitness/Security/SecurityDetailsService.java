package Evan.Application.Fitness.Security;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityDetailsService implements UserDetailsService {
    private final UserLoginDetailsRepository users;

    public SecurityDetailsService(UserLoginDetailsRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(user -> new AppUserPrincipal(user.getId(), user.getUsername(), user.getPassword(),
                        user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).toList()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
