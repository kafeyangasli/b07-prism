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
}
