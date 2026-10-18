package org.una.programmingIII.UTEMP_Project.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "universities")
public class University {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotBlank(message = "University name must not be blank")
    @Size(max = 100, message = "University name must be at most 100 characters long")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 200, message = "Location must be at most 200 characters long")
    @Column(name = "location", length = 200)
    private String location;

    @OneToMany(mappedBy = "university")
    @Builder.Default
    @ToString.Exclude
    private List<Faculty> faculties = new ArrayList<>();

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
        if (!(o instanceof University other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per entity type, so the hash does not change when the id is assigned on persist
        return University.class.hashCode();
    }
}
