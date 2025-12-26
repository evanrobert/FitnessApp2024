package Evan.Application.Fitness.Model;


import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reports {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    private String fileType;

    @Lob
    private byte[] data;

    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "userid")
    private UserLoginDetails userLoginDetails;

    @PrePersist
    public void prePersist() {
        uploadedAt = LocalDateTime.now();
    }
}
