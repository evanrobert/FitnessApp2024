package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Member profile. Owns the link to the account through user_information.userid. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_information")
public class UserInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int age;
    private int weight;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", unique = true)
    private UserLoginDetails user;
}
