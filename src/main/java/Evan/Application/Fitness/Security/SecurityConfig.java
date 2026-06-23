package Evan.Application.Fitness.Security;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    UserLoginDetailsRepository userLoginDetailsRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    /** Needs to be redone in order to create a authenticate permission instead of redirecting to creat account **/
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable().authorizeRequests()
                .antMatchers("/", "/login", "/signup", "/submitSignup", "/css/**", "/js/**", "/images/**").permitAll()
                .antMatchers("/home", "/add/custom/meal", "/Post/Custom/Meal", "/view/Nutrition",
                        "/edit/nutrition/information", "/download/nutrition", "/user/macro/information",
                        "/ModifyDailyIntake", "/log/custom/macro/information", "/edit/macro/information",
                        "/total", "/workouts").authenticated()
                .antMatchers("/test").hasRole("USER")
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/home")
                .permitAll()
                .and()
                .logout()
                .logoutSuccessUrl("/log-in?logout");
        return http.build();
    }
}

