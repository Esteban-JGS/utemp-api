package org.una.programmingIII.UTEMP_Project.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "grades")
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull(message = "Submission must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    @Builder.Default
    @ToString.Exclude
    private Submission submission = new Submission();

    @Column(name = "grade")
    private Double grade;

    @Size(max = 1000, message = "Comments must be at most 1000 characters long")
    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "reviewed_by_ai")
    private Boolean reviewedByAi;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    @ColumnDefault("'PENDING_REVIEW'")
    private GradeState state;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUpdate;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastUpdate = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdate = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Grade other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per entity type, so the hash does not change when the id is assigned on persist
        return Grade.class.hashCode();
    }
}