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
public class BlockageImpactPreviewRequest {

    private Long facilityId;
    private LocalDateTime startAt;
    private LocalDateTime plannedEndAt;
}
