package Evan.Application.Fitness.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One training session: its strength sets and cardio blocks. */
@Entity
@Table(name = "workout_session")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate sessionDate;

    @Size(max = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    private SessionFocus focus;

    @Enumerated(EnumType.STRING)
    private LocationType locationType;

    @Min(0) @Max(600)
    private Integer durationMin;

    /** Whole-session effort, 1-10. */
    @Min(1) @Max(10)
    private Integer sessionRpe;

    @Size(max = 2000)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserLoginDetails user;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, setNumber ASC")
    private List<ExerciseSet> sets = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CardioEntry> cardio = new ArrayList<>();

    public void addSet(ExerciseSet set) {
        set.setSession(this);
        sets.add(set);
    }

    public void addCardio(CardioEntry entry) {
        entry.setSession(this);
        cardio.add(entry);
    }

    /** Working-set tonnage (reps x load), warm-ups excluded. */
    public double volume() {
        return sets.stream().filter(s -> !s.isWarmup()).mapToDouble(ExerciseSet::volume).sum();
    }

    public long workingSetCount() {
        return sets.stream().filter(s -> !s.isWarmup()).count();
    }

    public double cardioMinutes() {
        return cardio.stream().mapToDouble(CardioEntry::getDurationMin).sum();
    }

    public String displayTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return focus != null ? focus.getLabel() + " session" : "Training session";
    }
}
