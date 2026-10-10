package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlockageImpactPreviewResponse {

    private Long facilityId;
    private String facilityName;
    private LocalDateTime startAt;
    private LocalDateTime plannedEndAt;
    private long approvedCount;
    private long pendingCount;
    private long totalAffectedCount;
    private String confirmationToken;
    private java.time.Instant expiresAt;

    public BlockageImpactPreviewResponse(Long facilityId, String facilityName, LocalDateTime startAt,
                                        LocalDateTime plannedEndAt, long approvedCount, long pendingCount, long totalAffectedCount) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.startAt = startAt;
        this.plannedEndAt = plannedEndAt;
        this.approvedCount = approvedCount;
        this.pendingCount = pendingCount;
        this.totalAffectedCount = totalAffectedCount;
    }
}
