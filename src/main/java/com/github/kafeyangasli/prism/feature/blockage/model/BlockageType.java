package com.github.kafeyangasli.prism.feature.blockage.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "blockage_types", uniqueConstraints = {
        @UniqueConstraint(name = "uk_blockage_types_code", columnNames = "code")
}, indexes = {
        @Index(name = "idx_blockage_types_active", columnList = "is_active")
})
public class BlockageType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Setter
    @Column(nullable = false, length = 150)
    private String name;

    @Setter
    @Column(length = 2000)
    private String description;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "blockageType")
    private List<FacilityBlockage> facilityBlockages = new ArrayList<>();

    protected BlockageType() {
    }

    public BlockageType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    @PrePersist
    void beforeInsert() {
        normalizeCode();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        normalizeCode();
        updatedAt = LocalDateTime.now();
    }

    private void normalizeCode() {
        if (code != null) {
            code = code.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }

    public void setCode(String code) { this.code = code; normalizeCode(); }

}
