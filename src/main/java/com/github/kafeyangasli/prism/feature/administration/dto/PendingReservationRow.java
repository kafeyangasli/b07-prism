package com.github.kafeyangasli.prism.feature.administration.dto;

import java.time.LocalDateTime;

public record PendingReservationRow(long id, String requesterName, String facilityName,
                                    LocalDateTime startAt, LocalDateTime endAt,
                                    LocalDateTime createdAt, LocalDateTime expiresAt,
                                    boolean proposalRequired, boolean proposalSubmitted, boolean proposalValidated,
                                    boolean validatedProposalAvailable, boolean proposalValidationLegacy,
                                    int conflictingPendingCount, String purpose, Integer quantity) {
    public PendingReservationRow(long id, String requesterName, String facilityName,
                                 LocalDateTime startAt, LocalDateTime endAt, LocalDateTime createdAt,
                                 LocalDateTime expiresAt, boolean proposalRequired, boolean proposalValidated,
                                 int conflictingPendingCount) {
        this(id, requesterName, facilityName, startAt, endAt, createdAt, expiresAt,
                proposalRequired, proposalRequired, proposalValidated, false, false, conflictingPendingCount, null, null);
    }

    public PendingReservationRow(long id, String requesterName, String facilityName,
            LocalDateTime startAt, LocalDateTime endAt, LocalDateTime createdAt, LocalDateTime expiresAt,
            boolean proposalRequired, boolean proposalSubmitted, boolean proposalValidated,
            boolean validatedProposalAvailable, boolean proposalValidationLegacy, int conflictingPendingCount, String purpose) {
        this(id, requesterName, facilityName, startAt, endAt, createdAt, expiresAt, proposalRequired,
                proposalSubmitted, proposalValidated, validatedProposalAvailable, proposalValidationLegacy,
                conflictingPendingCount, purpose, null);
    }

    public boolean isApprovalBlockedByProposal() {
        return (proposalRequired || proposalSubmitted) && !proposalValidated;
    }
}
