package com.github.kafeyangasli.prism.feature.facility.model;

import jakarta.persistence.*;
import lombok.Setter;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "facilities", indexes = {
        @Index(name = "idx_facilities_type_location", columnList = "facility_type_id, location"),
        @Index(name = "idx_facilities_administrative_status", columnList = "administrative_status")
})

public class Facility {

    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveAt ASC, id ASC")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<FacilityStatusHistory> statusHistory = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "facility", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private java.util.List<FacilityImage> images = new java.util.ArrayList<>();

    @Transient
    public FacilityImage getThumbnail() {
        return images.stream().filter(FacilityImage::isThumbnail).findFirst().orElse(null);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Setter
    @Column(nullable = false, length = 150)
    private String name;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_type_id", nullable = false)
    private FacilityType facilityType;

    @Setter
    @Column(nullable = false, length = 150)
    private String location;

    @Setter
    @Column(nullable = false)
    private Integer capacity;

    @Setter
    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "administrative_status", nullable = false, length = 20)
    private AdministrativeStatus administrativeStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Facility() {
    }

    public Facility(String code, String name, FacilityType facilityType, String location, Integer capacity,
                    String description, AdministrativeStatus administrativeStatus) {
        this.code = code;
        this.name = name;
        this.facilityType = facilityType;
        this.location = location;
        this.capacity = capacity;
        this.description = description;
        this.administrativeStatus = administrativeStatus;
    }

    /**
     * Source-compatibility constructor for callers being migrated to FacilityType.
     * Persisted facilities must use a managed FacilityType instance.
     */
    @Deprecated(forRemoval = false)
    public Facility(String code, String name, String type, String location, Integer capacity,
                    String description, AdministrativeStatus administrativeStatus) {
        this(code, name, new FacilityType(type, type, null), location, capacity, description, administrativeStatus);
    }

    @PrePersist
    void beforeInsert() {
        normalizeCode();
        LocalDateTime now = LocalDateTime.now(java.time.Clock.system(java.time.ZoneId.of("Asia/Jakarta")));
        createdAt = now;
        updatedAt = now;
        if (statusHistory.isEmpty()) statusHistory.add(new FacilityStatusHistory(this, administrativeStatus, now, true));
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

    public void setAdministrativeStatus(AdministrativeStatus status) {
        changeAdministrativeStatus(status, LocalDateTime.now(java.time.Clock.system(java.time.ZoneId.of("Asia/Jakarta"))));
    }

    public void changeAdministrativeStatus(AdministrativeStatus status, LocalDateTime effectiveAt) {
        java.util.Objects.requireNonNull(status);
        if (administrativeStatus != status) {
            if (!statusHistory.isEmpty() && effectiveAt.isBefore(statusHistory.getLast().getEffectiveAt())) {
                throw new IllegalArgumentException("Status history must be chronological");
            }
            administrativeStatus = status;
            statusHistory.add(new FacilityStatusHistory(this, status, effectiveAt));
        }
    }

    /** Non-persistent compatibility accessor retained for other feature domains. */
    @Transient
    public String getType() {
        return facilityType == null ? null : facilityType.getName();
    }

}
