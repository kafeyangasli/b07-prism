package com.github.kafeyangasli.prism.feature.blockage.model;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.user.model.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "facility_blockages", indexes = {
        @Index(name = "idx_facility_blockages_facility_status_time",
                columnList = "facility_id, status, start_at, planned_end_at")
})
public class FacilityBlockage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blockage_type_id", nullable = false)
    private BlockageType blockageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "planned_end_at")
    private LocalDateTime plannedEndAt;

    @Column(name = "actual_end_at")
    private LocalDateTime actualEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockageStatus status;

    @Column(name = "public_reason", nullable = false, length = 1000)
    private String publicReason;

    @Column(name = "internal_note", length = 2000)
    private String internalNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by")
    private User endedBy;

    @Column(name = "early_completion_reason", length = 1000)
    private String earlyCompletionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FacilityBlockage() {
    }

    public FacilityBlockage(Facility facility, BlockageType blockageType, Report report,
                            LocalDateTime startAt, LocalDateTime plannedEndAt,
                            BlockageStatus status, String publicReason, String internalNote,
                            User createdBy) {
        this.facility = facility;
        this.blockageType = blockageType;
        this.report = report;
        this.startAt = startAt;
        this.plannedEndAt = plannedEndAt;
        this.status = status;
        this.publicReason = publicReason;
        this.internalNote = internalNote;
        this.createdBy = createdBy;
    }

    @PrePersist
    void beforeInsert() {
        validateTimeRange();
        validateRepairReportReference();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        validateTimeRange();
        validateRepairReportReference();
        updatedAt = LocalDateTime.now();
    }

    private void validateTimeRange() {
        if (startAt == null || (plannedEndAt != null && !startAt.isBefore(plannedEndAt))) {
            throw new IllegalArgumentException("Facility blockage startAt must be before plannedEndAt");
        }
    }

    private void validateRepairReportReference() {
        if (blockageType != null && "REPAIR".equalsIgnoreCase(blockageType.getCode()) && report == null) {
            throw new IllegalArgumentException("A repair blockage must reference a report");
        }
    }

}
