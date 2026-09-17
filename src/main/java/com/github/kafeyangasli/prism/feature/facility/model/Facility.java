package com.github.kafeyangasli.prism.feature.facility.model;

import jakarta.persistence.*;
import lombok.Setter;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "facilities", indexes = {
        @Index(name = "idx_facilities_type_location", columnList = "type, location"),
        @Index(name = "idx_facilities_administrative_status", columnList = "administrative_status")
})

public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Setter
    @Column(nullable = false, length = 150)
    private String name;

    @Setter
    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Setter
    @Column(nullable = false, length = 150)
    private String location;

    @Setter
    @Column(nullable = false)
    private Integer capacity;

    @Setter
    @Column(length = 2000)
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "administrative_status", nullable = false, length = 20)
    private AdministrativeStatus administrativeStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Facility() {
    }

    public Facility(String code, String name, String type, String location, Integer capacity,
                    String description, AdministrativeStatus administrativeStatus) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.location = location;
        this.capacity = capacity;
        this.description = description;
        this.administrativeStatus = administrativeStatus;
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
