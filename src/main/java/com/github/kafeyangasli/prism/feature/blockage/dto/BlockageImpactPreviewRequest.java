package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class BlockageImpactPreviewRequest extends CreateBlockageRequest {

    public BlockageImpactPreviewRequest(Long facilityId, LocalDateTime startAt, LocalDateTime plannedEndAt) {
        setFacilityId(facilityId);
        setStartAt(startAt);
        setPlannedEndAt(plannedEndAt);
    }
}
