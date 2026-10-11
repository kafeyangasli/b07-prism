package com.github.kafeyangasli.prism.feature.reservation.model;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Duration;

@Getter
@Setter
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservations_facility_status_time", columnList = "facility_id, status, start_at, end_at"),
        @Index(name = "idx_reservations_user_status_end", columnList = "user_id, status, end_at"),
        @Index(name = "idx_reservations_status_expiry", columnList = "status, expires_at")
})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false, length = 1000)
    private String purpose;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "proposal_path", length = 1000)
    private String proposalPath;

    @Column(name = "validated_proposal_path", length = 1000)
    private String validatedProposalPath;

    // Only the migration grants this exception to pre-upload verification records.
    @Column(name = "proposal_validation_legacy", nullable = false)
    private boolean proposalValidationLegacy;

    @Column(name = "proposal_validated_at")
    private LocalDateTime proposalValidatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_validated_by")
    private User proposalValidatedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "reason_code", length = 80)
    private String reasonCode;

    @Column(name = "reason_detail", length = 2000)
    private String reasonDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Reservation() {
    }

    public Reservation(User user, Facility facility, LocalDateTime startAt, LocalDateTime endAt,
                       String purpose, String proposalPath, ReservationStatus status,
                       LocalDateTime expiresAt) {
        this.user = user;
        this.facility = facility;
        this.startAt = startAt;
        this.endAt = endAt;
        this.purpose = purpose;
        this.proposalPath = proposalPath;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void beforeInsert() {
        validateQuantity();
        validateTimeRange();
        validateProposalEvidence();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        validateQuantity();
        validateTimeRange();
        validateProposalEvidence();
        updatedAt = LocalDateTime.now();
    }

    private void validateTimeRange() {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("Waktu mulai reservasi harus lebih awal dari waktu selesai.");
        }
    }

    public boolean isProposalRequired() {
        return Duration.between(startAt, endAt).toMinutes() >= 360
                || (quantity != null && quantity > 5)
                || "Aula".equalsIgnoreCase(facility.getType());
    }

    private void validateQuantity() {
        if (facility.isEquipment() ? quantity == null || quantity <= 0 : quantity != null) {
            throw new IllegalArgumentException("Jumlah positif wajib untuk reservasi Alat saja.");
        }
    }

    public boolean isProposalSubmitted() {
        return proposalPath != null && !proposalPath.isBlank();
    }

    public boolean isProposalValidated() {
        return isProposalSubmitted() && proposalValidatedAt != null && proposalValidatedBy != null
                && (hasValidatedProposal() || proposalValidationLegacy);
    }

    public boolean hasValidatedProposal() {
        return validatedProposalPath != null && !validatedProposalPath.isBlank();
    }

    private void validateProposalEvidence() {
        if (hasValidatedProposal() && (!isProposalSubmitted()
                || proposalValidatedAt == null || proposalValidatedBy == null)) {
            throw new IllegalArgumentException("PDF tervalidasi harus memiliki proposal asli dan audit validasi lengkap.");
        }
        if (!proposalValidationLegacy && proposalValidatedAt != null && proposalValidatedBy != null
                && (!isProposalSubmitted() || !hasValidatedProposal())) {
            throw new IllegalArgumentException("Validasi proposal memerlukan PDF tervalidasi.");
        }
    }

}
