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
public class CreateBlockageRequest {

    private Long facilityId;
    private Long blockageTypeId;
    private Long reportId;
    private LocalDateTime startAt;
    private LocalDateTime plannedEndAt;
    private String publicReason;
    private String internalNote;
}
