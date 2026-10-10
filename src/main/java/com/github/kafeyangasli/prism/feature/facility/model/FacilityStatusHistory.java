package com.github.kafeyangasli.prism.feature.facility.model;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

/** A status observation, valid from effectiveAt until the next observation. */
@Entity
@Getter
@Table(name = "facility_status_history", indexes = @Index(name = "idx_facility_status_time", columnList = "facility_id,effective_at"))
public class FacilityStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AdministrativeStatus status;
    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;
    @Column(name = "at_creation", nullable = false)
    private boolean atCreation;
    protected FacilityStatusHistory() {}
    public FacilityStatusHistory(Facility facility, AdministrativeStatus status, LocalDateTime effectiveAt) {
        this(facility, status, effectiveAt, false);
    }
    public FacilityStatusHistory(Facility facility, AdministrativeStatus status, LocalDateTime effectiveAt, boolean atCreation) {
        this.facility = facility;
        this.status = status;
        this.effectiveAt = effectiveAt;
        this.atCreation = atCreation;
    }
}
